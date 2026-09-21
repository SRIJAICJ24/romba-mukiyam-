package com.rombamukiyam.vault.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.dao.VaultItemDao
import com.rombamukiyam.vault.data.entity.CredentialFieldEntity
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.PasswordHistoryEntity
import com.rombamukiyam.vault.data.entity.PasswordPolicyEntity
import com.rombamukiyam.vault.data.entity.TagEntity
import com.rombamukiyam.vault.data.entity.VaultItemEntity
import com.rombamukiyam.vault.data.entity.VaultItemTagCrossRef
import com.rombamukiyam.vault.data.mapper.domainOf
import com.rombamukiyam.vault.data.mapper.toDomain
import com.rombamukiyam.vault.data.mapper.toSummary
import com.rombamukiyam.vault.core.crypto.toHex
import com.rombamukiyam.vault.domain.model.FieldDraft
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.PasswordDigest
import com.rombamukiyam.vault.domain.model.PasswordPolicy
import com.rombamukiyam.vault.domain.model.HistoryEvent
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.domain.model.VaultItemDraft
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.CategoryCountSummary
import com.rombamukiyam.vault.domain.repository.ReminderRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class VaultRepositoryImpl(
    private val session: VaultSession,
    private val clock: Clock,
    private val reminders: ReminderRepository? = null,
) : VaultRepository {

    private val dao: VaultItemDao get() = session.database().vaultItemDao()

    /** Re-subscribes when the session changes so a locked vault yields empty lists instead of crashing. */
    private fun <T> whenUnlocked(empty: T, source: () -> Flow<T>): Flow<T> =
        session.state.flatMapLatest { if (session.isUnlocked) source() else flowOf(empty) }

    private fun withOwnerNames(items: Flow<List<VaultItemEntity>>): Flow<List<VaultItemSummary>> =
        combine(items, session.database().familyProfileDao().observeAll()) { list, profiles ->
            val names = profiles.associate { it.id to it.displayName }
            list.map { it.toSummary(names[it.ownerId]) }
        }

    override fun observeActive() = whenUnlocked(emptyList()) { withOwnerNames(dao.observeActive()) }
    override fun observeFavorites() = whenUnlocked(emptyList()) { withOwnerNames(dao.observeFavorites()) }
    override fun observeByCategory(category: VaultCategory) = whenUnlocked(emptyList()) { withOwnerNames(dao.observeByCategory(category.name)) }
    override fun observeByOwner(ownerId: String) = whenUnlocked(emptyList()) { withOwnerNames(dao.observeByOwner(ownerId)) }
    override fun observeTrash() = whenUnlocked(emptyList()) { withOwnerNames(dao.observeTrash()) }
    override fun observeActiveCount(): Flow<Int> = whenUnlocked(0) { dao.observeActiveCount() }

    override fun observeCategoryCounts(): Flow<List<CategoryCountSummary>> = whenUnlocked(emptyList()) {
        dao.observeCategoryCounts().map { l -> l.map { CategoryCountSummary(VaultCategory.fromName(it.category), it.count) } }
    }

    override fun observeItem(id: String): Flow<VaultItem?> = whenUnlocked(null) {
        dao.observeWithFields(id).map { rel -> rel?.let { toDomainItem(it.item, it.fields) } }
    }

    override fun observeHistory(itemId: String): Flow<List<HistoryEvent>> = whenUnlocked(emptyList()) {
        dao.observeHistory(itemId).map { l -> l.map { it.toDomain() } }
    }

    override suspend fun getItem(id: String): VaultItem? = dao.getWithFields(id)?.let { toDomainItem(it.item, it.fields) }

    private suspend fun toDomainItem(item: VaultItemEntity, fields: List<CredentialFieldEntity>): VaultItem {
        val tags = session.database().tagDao().getTagsForItem(item.id).map { it.name }
        return item.toDomain(fields.map { it.toDomain(session) }, tags)
    }

    override suspend fun search(query: String): List<VaultItemSummary> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val escaped = q.replace("%", "").replace("_", "")
        val names = session.database().familyProfileDao().getAll().associate { it.id to it.displayName }
        return dao.searchMetadata("%$escaped%").map { it.toSummary(names[it.ownerId]) }
    }

    // ------------------------------------------------------------ writes

    override suspend fun create(draft: VaultItemDraft): String {
        session.assertWritable()
        val now = clock.nowMillis()
        val id = draft.id ?: UUID.randomUUID().toString()
        val entity = draft.toEntity(id, createdAt = now, now = now)
        val fields = draft.fields.mapIndexed { i, f -> f.toEntity(id, i, now, now) }
        session.database().runInTransactionSuspend {
            dao.insertItemWithFields(entity, fields, event(id, HistoryEventType.CREDENTIAL_CREATED, null, now))
            writeTags(id, draft.tags, now)
            recordDigests(id, draft.fields, fields, now)
            reminders?.syncForItem(id, draft.category, draft.passwordLastChangedAt, draft.reviewIntervalDays, draft.fields)
        }
        reminders?.republish()
        return id
    }

    override suspend fun update(draft: VaultItemDraft) {
        session.assertWritable()
        val id = requireNotNull(draft.id) { "update requires id" }
        val existing = dao.getById(id) ?: error("item missing")
        val now = clock.nowMillis()
        val entity = draft.toEntity(id, createdAt = existing.createdAt, now = now).copy(deletedAt = existing.deletedAt)
        val fields = draft.fields.mapIndexed { i, f -> f.toEntity(id, i, existing.createdAt, now) }
        session.database().runInTransactionSuspend {
            dao.updateItemWithFields(entity, fields, event(id, HistoryEventType.CREDENTIAL_UPDATED, null, now))
            writeTags(id, draft.tags, now)
            recordDigests(id, draft.fields, fields, now)
            reminders?.syncForItem(id, draft.category, draft.passwordLastChangedAt, draft.reviewIntervalDays, draft.fields)
        }
        reminders?.republish()
    }

    /**
     * Keeps the keyed digest of every password-type field current. Only writes a new history row when the
     * value actually changed (same digest => nothing to record), so reuse detection stays meaningful.
     */
    private suspend fun recordDigests(itemId: String, drafts: List<FieldDraft>, entities: List<CredentialFieldEntity>, now: Long) {
        val pw = session.database().passwordDao()
        drafts.zip(entities).forEach { (draft, entity) ->
            if (draft.type !in PASSWORD_TYPES || draft.value.isBlank()) return@forEach
            val digest = session.historyDigest(draft.value).toHex()
            if (pw.countPreviousUses(itemId, entity.id, digest) > 0) {
                // Same password as before (edit that did not touch it): make sure it is the current row only.
                pw.retireCurrent(entity.id)
                pw.insertHistory(PasswordHistoryEntity(UUID.randomUUID().toString(), itemId, entity.id, digest, current = true, changedAt = now))
                return@forEach
            }
            pw.retireCurrent(entity.id)
            pw.insertHistory(PasswordHistoryEntity(UUID.randomUUID().toString(), itemId, entity.id, digest, current = true, changedAt = now))
        }
    }

    private suspend fun writeTags(itemId: String, tags: List<String>, now: Long) {
        val tagDao = session.database().tagDao()
        tagDao.clearTagsForItem(itemId)
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (clean.isEmpty()) return
        val existing = tagDao.getAll().associateBy { it.name.lowercase() }
        val refs = clean.map { name ->
            val tag = existing[name.lowercase()] ?: TagEntity(UUID.randomUUID().toString(), name, now).also { tagDao.insert(it) }
            VaultItemTagCrossRef(itemId, tag.id)
        }
        tagDao.insertCrossRefs(refs)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        session.assertWritable()
        dao.setFavorite(id, favorite, clock.nowMillis())
    }

    override suspend fun moveToTrash(id: String) {
        session.assertWritable()
        val now = clock.nowMillis()
        dao.moveToTrash(id, now, event(id, HistoryEventType.MOVED_TO_TRASH, null, now))
        reminders?.republish() // trashed items drop out of the schedule
    }

    override suspend fun restore(id: String) {
        session.assertWritable()
        val now = clock.nowMillis()
        dao.restoreFromTrash(id, now, event(id, HistoryEventType.RESTORED, null, now))
        reminders?.republish()
    }

    override suspend fun deletePermanently(id: String) {
        session.assertWritable()
        // Cascades remove fields, history, digests, policy, reminders and tag links for this item.
        dao.deletePermanently(id)
        session.database().tagDao().deleteUnusedTags()
        reminders?.republish()
    }

    override suspend fun purgeTrashOlderThan(days: Int): Int {
        if (session.isSafeMode) return 0
        val cutoff = clock.nowMillis() - days * 86_400_000L
        val old = dao.getTrashOlderThan(cutoff)
        old.forEach { dao.deletePermanently(it.id) }
        if (old.isNotEmpty()) session.database().tagDao().deleteUnusedTags()
        return old.size
    }

    override suspend fun changePassword(itemId: String, fieldId: String, newPassword: String, changedAt: Long) {
        session.assertWritable()
        val now = clock.nowMillis()
        val fields = dao.getFields(itemId)
        val field = fields.firstOrNull { it.id == fieldId } ?: error("field missing")
        val updated = field.copy(encryptedValue = session.encryptField(newPassword, field.id), updatedAt = now)
        val digest = session.historyDigest(newPassword).toHex()
        val pw = session.database().passwordDao()
        session.database().runInTransactionSuspend {
            dao.updateField(updated)
            dao.setPasswordLastChanged(itemId, changedAt, now)
            dao.insertHistory(event(itemId, HistoryEventType.PASSWORD_CHANGED, null, now))
            pw.retireCurrent(fieldId)
            pw.insertHistory(PasswordHistoryEntity(UUID.randomUUID().toString(), itemId, fieldId, digest, current = true, changedAt = now))
            val item = dao.getById(itemId)
            if (item?.reviewIntervalDays != null) {
                val fieldDrafts = fields.map { f ->
                    val type = FieldType.fromName(f.fieldType)
                    FieldDraft(f.id, type, f.label, if (type == FieldType.EXPIRY_DATE) session.decryptField(f.encryptedValue, f.id) else "")
                }
                reminders?.syncForItem(itemId, VaultCategory.fromName(item.category), changedAt, item.reviewIntervalDays, fieldDrafts)
            }
        }
        reminders?.republish()
    }

    // ------------------------------------------------------------ password tools

    override fun observeCurrentDigests(): Flow<List<PasswordDigest>> = whenUnlocked(emptyList()) {
        session.database().passwordDao().observeAllCurrent().map { l -> l.map { PasswordDigest(it.vaultItemId, it.fieldId, it.digestHex, it.changedAt) } }
    }

    override suspend fun wasUsedBefore(itemId: String, fieldId: String, password: String): Boolean =
        session.database().passwordDao().countPreviousUses(itemId, fieldId, session.historyDigest(password).toHex()) > 0

    override fun observePolicy(itemId: String): Flow<PasswordPolicy?> = whenUnlocked(null) {
        session.database().passwordDao().observePolicy(itemId).map { it?.toDomain() }
    }

    override suspend fun getPolicy(itemId: String): PasswordPolicy? = session.database().passwordDao().getPolicy(itemId)?.toDomain()

    override suspend fun savePolicy(policy: PasswordPolicy) {
        session.assertWritable()
        session.database().passwordDao().upsertPolicy(
            PasswordPolicyEntity(
                vaultItemId = policy.itemId, minLength = policy.minLength, maxLength = policy.maxLength,
                requireUppercase = policy.requireUppercase, requireLowercase = policy.requireLowercase,
                requireDigit = policy.requireDigit, requireSymbol = policy.requireSymbol,
                allowedSymbols = policy.allowedSymbols?.ifBlank { null }, forbiddenCharacters = policy.forbiddenCharacters?.ifEmpty { null },
                notes = policy.notes?.ifBlank { null }, policySource = policy.policySource?.ifBlank { null },
                lastVerifiedAt = policy.lastVerifiedAt, updatedAt = clock.nowMillis(),
            ),
        )
    }

    override suspend fun deletePolicy(itemId: String) {
        session.assertWritable()
        session.database().passwordDao().deletePolicy(itemId)
    }

    override suspend fun primaryPasswordsForHealth(): List<Triple<String, String, String>> {
        val active = dao.getAllActive().associateBy { it.id }
        return dao.getAllFields()
            .filter { it.vaultItemId in active && FieldType.fromName(it.fieldType) in PASSWORD_TYPES }
            .map { Triple(it.vaultItemId, it.id, session.decryptField(it.encryptedValue, it.id)) }
    }

    private fun PasswordPolicyEntity.toDomain() = PasswordPolicy(
        itemId = vaultItemId, minLength = minLength, maxLength = maxLength, requireUppercase = requireUppercase,
        requireLowercase = requireLowercase, requireDigit = requireDigit, requireSymbol = requireSymbol,
        allowedSymbols = allowedSymbols, forbiddenCharacters = forbiddenCharacters, notes = notes,
        policySource = policySource, lastVerifiedAt = lastVerifiedAt,
    )

    override suspend fun markReviewed(itemId: String, reviewedAt: Long) {
        session.assertWritable()
        val now = clock.nowMillis()
        session.database().runInTransactionSuspend {
            dao.setPasswordLastChanged(itemId, reviewedAt, now)
            dao.insertHistory(event(itemId, HistoryEventType.CREDENTIAL_REVIEWED, null, now))
            val item = dao.getById(itemId)
            if (item?.reviewIntervalDays != null) {
                // Keep the expiry reminder untouched: pass the stored expiry field back through the sync.
                val fieldDrafts = dao.getFields(itemId).map { f ->
                    val type = FieldType.fromName(f.fieldType)
                    FieldDraft(f.id, type, f.label, if (type == FieldType.EXPIRY_DATE) session.decryptField(f.encryptedValue, f.id) else "")
                }
                reminders?.syncForItem(itemId, VaultCategory.fromName(item.category), reviewedAt, item.reviewIntervalDays, fieldDrafts)
            }
        }
        reminders?.republish()
    }

    // ------------------------------------------------------------ helpers

    private val PASSWORD_TYPES = setOf(
        FieldType.PASSWORD, FieldType.PROFILE_PASSWORD, FieldType.TRANSACTION_PASSWORD, FieldType.WIFI_PASSWORD, FieldType.CUSTOM_SECRET,
    )

    private fun event(itemId: String?, type: HistoryEventType, detail: String?, now: Long) =
        CredentialHistoryEntity(UUID.randomUUID().toString(), itemId, type.name, detail, now)

    private fun VaultItemDraft.toEntity(id: String, createdAt: Long, now: Long) = VaultItemEntity(
        id = id,
        ownerId = ownerId,
        category = category.name,
        title = title.trim(),
        subtitle = subtitle?.trim()?.ifBlank { null },
        institution = institution?.trim()?.ifBlank { null },
        officialDomain = domainOf(officialWebsite),
        officialWebsite = officialWebsite?.trim()?.ifBlank { null },
        favorite = favorite,
        highSecurity = highSecurity,
        notes = notes?.trim()?.ifBlank { null },
        passwordLastChangedAt = passwordLastChangedAt,
        reviewIntervalDays = reviewIntervalDays,
        twoFactorEnabled = twoFactorEnabled,
        createdAt = createdAt,
        updatedAt = now,
    )

    private fun FieldDraft.toEntity(itemId: String, order: Int, createdAt: Long, now: Long): CredentialFieldEntity {
        val fieldId = id ?: UUID.randomUUID().toString()
        return CredentialFieldEntity(
            id = fieldId,
            vaultItemId = itemId,
            fieldType = type.name,
            label = label.trim(),
            encryptedValue = session.encryptField(value, fieldId),
            isSecret = isSecret,
            isSensitive = isSensitive,
            displayOrder = order,
            createdAt = createdAt,
            updatedAt = now,
        )
    }
}

/** Suspending transaction helper. */
suspend fun <T> RoomDatabase.runInTransactionSuspend(block: suspend () -> T): T = withTransaction { block() }
