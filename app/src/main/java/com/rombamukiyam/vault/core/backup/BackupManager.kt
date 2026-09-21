package com.rombamukiyam.vault.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.rombamukiyam.vault.BuildConfig
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.InvalidBackupReason
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.VaultException
import com.rombamukiyam.vault.core.crypto.KdfParams
import com.rombamukiyam.vault.core.crypto.PassphraseKdf
import com.rombamukiyam.vault.core.crypto.toHex
import com.rombamukiyam.vault.core.crypto.wipe
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.database.IntegrityChecker
import com.rombamukiyam.vault.core.database.IntegrityLevel
import com.rombamukiyam.vault.core.database.RombaDatabase
import com.rombamukiyam.vault.core.security.KeyringStore
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.entity.BackupMetadataEntity
import com.rombamukiyam.vault.data.entity.CredentialFieldEntity
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import com.rombamukiyam.vault.data.entity.PasswordHistoryEntity
import com.rombamukiyam.vault.data.entity.PasswordPolicyEntity
import com.rombamukiyam.vault.data.entity.ReminderEntity
import com.rombamukiyam.vault.data.entity.TagEntity
import com.rombamukiyam.vault.data.entity.VaultItemEntity
import com.rombamukiyam.vault.data.entity.VaultItemTagCrossRef
import com.rombamukiyam.vault.domain.model.FieldType
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json

data class BackupResult(val itemCount: Int, val sizeBytes: Long, val createdAt: Long, val verified: Boolean)

data class BackupInfo(val schemaVersion: Int, val createdAt: Long, val itemCount: Int, val profileCount: Int, val fieldCount: Int)

/**
 * Creates, verifies and restores `.rmvault` backups. All operations run off the main thread (callers use IO).
 *
 * Restore is designed so that ANY failure — bad file, wrong passphrase, schema too new, storage error,
 * process death — leaves the live vault exactly as it was:
 *   1. read + parse + decrypt + validate the backup into memory (no writes yet)
 *   2. copy the live database files and keyring to a safety folder
 *   3. replace all rows inside ONE Room transaction (rollback on error)
 *   4. verify counts and integrity; only then record success
 */
class BackupManager(
    private val context: Context,
    private val session: VaultSession,
    private val keys: VaultKeyManager,
    private val kdf: PassphraseKdf,
    private val databaseFactory: DatabaseFactory,
    private val keyringStore: KeyringStore,
    private val integrity: IntegrityChecker,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------ create

    suspend fun createBackup(destination: Uri, passphrase: ByteArray, displayName: String?): BackupResult {
        if (!keys.verifyPassphrase(passphrase)) throw VaultException(VaultError.AuthenticationFailed())
        val db = session.database()
        val now = clock.nowMillis()
        val full = integrity.check(db.openHelper.writableDatabase, IntegrityLevel.FULL, now)
        if (!full.ok) throw VaultException(VaultError.DatabaseIntegrityFailure())

        val payload = export(db, now)
        val plaintext = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val params = KdfParams.withFreshSalt(keys.currentKdfParams())
        val key = kdf.derive(passphrase, params)
        val bytes = try {
            RmvaultFormat.encode(RombaDatabase.SCHEMA_VERSION, params, now, key, plaintext)
        } finally {
            key.wipe(); plaintext.wipe()
        }
        if (bytes.size > RmvaultFormat.MAX_FILE_BYTES) throw VaultException(VaultError.BackupFailure())

        try {
            context.contentResolver.openOutputStream(destination, "wt")?.use { out -> out.write(bytes); out.flush() }
                ?: throw VaultException(VaultError.BackupFailure())
        } catch (e: VaultException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "backup write failed", e)
            throw VaultException(VaultError.BackupFailure(e))
        }

        // Verify by reading the file back from the provider, not from memory.
        val verified = try {
            val info = verify(destination, passphrase)
            info.itemCount == payload.items.size && info.fieldCount == payload.fields.size
        } catch (e: Exception) {
            SafeLog.e(TAG, "backup verification failed", e)
            false
        }
        if (!verified) throw VaultException(VaultError.BackupFailure())

        db.reminderDao().insertBackup(
            BackupMetadataEntity(
                id = UUID.randomUUID().toString(), createdAt = now, displayName = displayName?.take(120), sizeBytes = bytes.size.toLong(),
                itemCount = payload.items.size, formatVersion = RmvaultFormat.FORMAT_VERSION, schemaVersion = RombaDatabase.SCHEMA_VERSION,
                verified = true, event = "CREATED",
            ),
        )
        return BackupResult(payload.items.size, bytes.size.toLong(), now, verified = true)
    }

    // ------------------------------------------------------------------ verify

    /** Full verification: header, authentication tag, decryptability, JSON shape and manifest counts. */
    suspend fun verify(source: Uri, passphrase: ByteArray): BackupInfo {
        val payload = readAndDecrypt(source, passphrase).second
        return BackupInfo(payload.schemaVersion, payload.createdAt, payload.items.size, payload.profiles.size, payload.fields.size)
    }

    /** Header-only inspection (no passphrase). */
    fun inspect(source: Uri): RmvaultFormat.Header = RmvaultFormat.parse(readBytes(source)).header

    private fun readBytes(source: Uri): ByteArray {
        val bytes = try {
            context.contentResolver.openInputStream(source)?.use { input -> input.readBytesCapped(RmvaultFormat.MAX_FILE_BYTES) }
        } catch (e: Exception) {
            throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.TRUNCATED, e))
        } ?: throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.TRUNCATED))
        return bytes
    }

    private fun readAndDecrypt(source: Uri, passphrase: ByteArray): Pair<RmvaultFormat.Header, BackupPayload> {
        val parsed = RmvaultFormat.parse(readBytes(source))
        if (parsed.header.schemaVersion > RombaDatabase.SCHEMA_VERSION) throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.NEWER_SCHEMA))
        val key = kdf.derive(passphrase, parsed.header.kdf)
        val plaintext = try {
            RmvaultFormat.decrypt(parsed, key)
        } finally {
            key.wipe()
        }
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.CORRUPT_PAYLOAD, e))
        } finally {
            plaintext.wipe()
        }
        if (!payload.manifest.matches(payload)) throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.COUNT_MISMATCH))
        if (payload.schemaVersion != parsed.header.schemaVersion) throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.CORRUPT_PAYLOAD))
        // Referential sanity: every field/tag link must point at an item in the payload.
        val itemIds = payload.items.map { it.id }.toSet()
        val profileIds = payload.profiles.map { it.id }.toSet()
        if (payload.fields.any { it.vaultItemId !in itemIds } || payload.items.any { it.ownerId !in profileIds }) {
            throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.CORRUPT_PAYLOAD))
        }
        return parsed.header to payload
    }

    // ------------------------------------------------------------------ restore

    suspend fun restore(source: Uri, backupPassphrase: ByteArray, displayName: String?): BackupResult {
        val (header, payload) = readAndDecrypt(source, backupPassphrase)
        val db = session.database()
        val now = clock.nowMillis()

        // Safety snapshot of the live vault. Kept until the user deletes it from Vault Health.
        val snapshotDir = snapshot()

        try {
            db.withTransaction {
                val w = db.openHelper.writableDatabase
                w.execSQL("PRAGMA defer_foreign_keys = ON")
                listOf("vault_item_tags", "tags", "reminders", "password_policies", "password_history", "credential_history", "credential_fields", "vault_items", "family_profiles")
                    .forEach { w.execSQL("DELETE FROM $it") }
                import(db, payload, now)
                // Verify inside the transaction so a mismatch rolls everything back.
                val counts = integrity.check(w, IntegrityLevel.FULL, now)
                if (!counts.ok) throw VaultException(VaultError.DatabaseIntegrityFailure())
                if (counts.tableCounts["vault_items"] != payload.items.size || counts.tableCounts["credential_fields"] != payload.fields.size) {
                    throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.COUNT_MISMATCH))
                }
                db.reminderDao().insertBackup(
                    BackupMetadataEntity(
                        id = UUID.randomUUID().toString(), createdAt = now, displayName = displayName?.take(120), sizeBytes = 0,
                        itemCount = payload.items.size, formatVersion = header.formatVersion, schemaVersion = header.schemaVersion, verified = true, event = "RESTORED",
                    ),
                )
                db.vaultItemDao().insertHistory(CredentialHistoryEntity(UUID.randomUUID().toString(), null, "BACKUP_RESTORED", "schema ${header.schemaVersion}", now))
            }
        } catch (e: VaultException) {
            SafeLog.e(TAG, "restore rolled back (${e.error.code}); snapshot at ${snapshotDir.name}")
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "restore rolled back; snapshot at ${snapshotDir.name}", e)
            throw VaultException(VaultError.BackupFailure(e))
        }
        return BackupResult(payload.items.size, 0, now, verified = true)
    }

    /** Copies live database files + keyring into filesDir/safety/<timestamp>. Never deletes anything. */
    fun snapshot(): File {
        val dir = File(File(context.filesDir, SAFETY_DIR), clock.nowMillis().toString()).apply { mkdirs() }
        val dbFile = databaseFactory.databaseFile()
        // Checkpoint WAL into the main file so the copy is complete.
        runCatching { session.database().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).filter { it.exists() }.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
        val keyDir = File(context.filesDir, KeyringStore.DIR_NAME)
        if (keyDir.exists()) keyDir.copyRecursively(File(dir, KeyringStore.DIR_NAME), overwrite = true)
        SafeLog.i(TAG, "safety snapshot created")
        return dir
    }

    fun listSnapshots(): List<File> = File(context.filesDir, SAFETY_DIR).listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()

    fun deleteSnapshot(dir: File) {
        if (dir.parentFile?.name == SAFETY_DIR) dir.deleteRecursively()
    }

    // ------------------------------------------------------------------ mapping

    private suspend fun export(db: RombaDatabase, now: Long): BackupPayload {
        val profiles = db.familyProfileDao().getAll().map { BackupPayload.ProfileDto(it.id, it.displayName, it.relationship, it.avatarType, it.avatarColor, it.createdAt, it.updatedAt, it.archived, it.sortOrder) }
        val items = db.vaultItemDao().getAllIncludingTrash().map {
            BackupPayload.ItemDto(it.id, it.ownerId, it.category, it.title, it.subtitle, it.institution, it.officialDomain, it.officialWebsite, it.favorite, it.highSecurity, it.notes, it.passwordLastChangedAt, it.reviewIntervalDays, it.twoFactorEnabled, it.createdAt, it.updatedAt, it.deletedAt)
        }
        val fields = db.vaultItemDao().getAllFields().map {
            BackupPayload.FieldDto(it.id, it.vaultItemId, it.fieldType, it.label, session.decryptField(it.encryptedValue, it.id), it.isSecret, it.isSensitive, it.displayOrder, it.createdAt, it.updatedAt)
        }
        val history = db.vaultItemDao().getAllHistory().map { BackupPayload.HistoryDto(it.id, it.vaultItemId, it.eventType, it.detail, it.occurredAt) }
        val tags = db.tagDao().getAll().map { BackupPayload.TagDto(it.id, it.name, it.createdAt) }
        val itemTags = db.tagDao().getAllCrossRefs().map { BackupPayload.ItemTagDto(it.vaultItemId, it.tagId) }
        val policies = db.passwordDao().getAllPolicies().map {
            BackupPayload.PolicyDto(it.vaultItemId, it.minLength, it.maxLength, it.requireUppercase, it.requireLowercase, it.requireDigit, it.requireSymbol, it.allowedSymbols, it.forbiddenCharacters, it.notes, it.policySource, it.lastVerifiedAt, it.updatedAt)
        }
        val reminders = db.reminderDao().getAll().map { BackupPayload.ReminderDto(it.id, it.vaultItemId, it.type, it.enabled, it.lastActionDate, it.nextDueDate, it.intervalDays, it.notifyBeforeDays, it.customLabel, it.createdAt, it.updatedAt) }
        return BackupPayload(
            schemaVersion = RombaDatabase.SCHEMA_VERSION, createdAt = now, vaultId = keys.vaultId(), appVersion = BuildConfig.VERSION_NAME,
            profiles = profiles, items = items, fields = fields, history = history, tags = tags, itemTags = itemTags, policies = policies, reminders = reminders,
            manifest = BackupPayload.Manifest(profiles.size, items.size, fields.size, history.size, tags.size, itemTags.size, policies.size, reminders.size),
        )
    }

    private suspend fun import(db: RombaDatabase, p: BackupPayload, now: Long) {
        db.familyProfileDao().insertAll(p.profiles.map { FamilyProfileEntity(it.id, it.displayName, it.relationship, it.avatarType, it.avatarColor, it.createdAt, it.updatedAt, it.archived, it.sortOrder) })
        p.items.forEach {
            db.vaultItemDao().insert(
                VaultItemEntity(it.id, it.ownerId, it.category, it.title, it.subtitle, it.institution, it.officialDomain, it.officialWebsite, it.favorite, it.highSecurity, it.notes, it.passwordLastChangedAt, it.reviewIntervalDays, it.twoFactorEnabled, it.createdAt, it.updatedAt, it.deletedAt),
            )
        }
        db.vaultItemDao().insertFields(
            p.fields.map { CredentialFieldEntity(it.id, it.vaultItemId, it.fieldType, it.label, session.encryptField(it.value, it.id), it.isSecret, it.isSensitive, it.displayOrder, it.createdAt, it.updatedAt) },
        )
        p.history.forEach { db.vaultItemDao().insertHistory(CredentialHistoryEntity(it.id, it.vaultItemId, it.eventType, it.detail, it.occurredAt)) }
        p.tags.forEach { db.tagDao().insert(TagEntity(it.id, it.name, it.createdAt)) }
        db.tagDao().insertCrossRefs(p.itemTags.map { VaultItemTagCrossRef(it.vaultItemId, it.tagId) })
        p.policies.forEach {
            db.passwordDao().upsertPolicy(PasswordPolicyEntity(it.vaultItemId, it.minLength, it.maxLength, it.requireUppercase, it.requireLowercase, it.requireDigit, it.requireSymbol, it.allowedSymbols, it.forbiddenCharacters, it.notes, it.policySource, it.lastVerifiedAt, it.updatedAt))
        }
        p.reminders.forEach { db.reminderDao().upsert(ReminderEntity(it.id, it.vaultItemId, it.type, it.enabled, it.lastActionDate, it.nextDueDate, it.intervalDays, it.notifyBeforeDays, it.customLabel, it.createdAt, it.updatedAt)) }
        // Re-derive current password digests under THIS device's history key (old digests are not portable).
        val passwordTypes = setOf(FieldType.PASSWORD, FieldType.PROFILE_PASSWORD, FieldType.TRANSACTION_PASSWORD, FieldType.WIFI_PASSWORD, FieldType.CUSTOM_SECRET)
        p.fields.filter { FieldType.fromName(it.fieldType) in passwordTypes && it.value.isNotBlank() }.forEach { f ->
            db.passwordDao().insertHistory(PasswordHistoryEntity(UUID.randomUUID().toString(), f.vaultItemId, f.id, session.historyDigest(f.value).toHex(), current = true, changedAt = f.updatedAt))
        }
    }

    private fun java.io.InputStream.readBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw VaultException(VaultError.InvalidBackup(InvalidBackupReason.UNSUPPORTED_FORMAT))
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "Backup"
        const val SAFETY_DIR = "safety"

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }
}
