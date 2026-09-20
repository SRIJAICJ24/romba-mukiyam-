package com.rombamukiyam.vault.core.security

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.VaultException
import com.rombamukiyam.vault.core.crypto.Aead
import com.rombamukiyam.vault.core.crypto.AeadCiphertext
import com.rombamukiyam.vault.core.crypto.Hkdf
import com.rombamukiyam.vault.core.crypto.KeyPurpose
import com.rombamukiyam.vault.core.crypto.wipe
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.database.IntegrityChecker
import com.rombamukiyam.vault.core.database.IntegrityLevel
import com.rombamukiyam.vault.core.database.RombaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SessionState {
    data object Locked : SessionState()
    data class Unlocked(val unlockedAt: Long, val safeMode: Boolean) : SessionState()
}

/**
 * The single in-memory holder of key material for the current unlocked session.
 *
 *  - Lives only as long as the process; never written to SavedState, Bundle, disk or logs.
 *  - [lock] closes the database and zeroes every key. Process death implies locked by construction.
 *  - Repositories obtain the database through [database]; it throws [VaultError.VaultLocked] when locked
 *    so no code path can accidentally operate on a closed/absent database.
 */
class VaultSession(
    private val databaseFactory: DatabaseFactory,
    private val integrityChecker: IntegrityChecker,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Locked)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile private var vaultKey: ByteArray? = null
    @Volatile private var fieldKey: ByteArray? = null
    @Volatile private var historyKey: ByteArray? = null
    @Volatile private var db: RombaDatabase? = null

    val isUnlocked: Boolean get() = _state.value is SessionState.Unlocked

    /**
     * Derives sub-keys, opens the database and performs a quick integrity check.
     * Takes ownership of [key] (it is copied; caller should wipe its own copy).
     * @throws VaultException DatabaseUnavailable / DatabaseIntegrityFailure — in which case nothing is retained.
     */
    @Synchronized
    fun unlock(key: ByteArray, safeMode: Boolean = false) {
        if (isUnlocked) return
        val vk = key.copyOf()
        val dbKey = Hkdf.derive(vk, KeyPurpose.DATABASE)
        try {
            val database = try {
                databaseFactory.open(dbKey)
            } catch (e: Exception) {
                SafeLog.e(TAG, "database open failed", e)
                throw VaultException(VaultError.DatabaseUnavailable(e))
            }
            val integrity = integrityChecker.check(database.openHelper.writableDatabase, IntegrityLevel.QUICK, clock.nowMillis())
            if (!integrity.ok && !safeMode) {
                databaseFactory.closeAndWipe(database)
                throw VaultException(VaultError.DatabaseIntegrityFailure())
            }
            vaultKey = vk
            fieldKey = Hkdf.derive(vk, KeyPurpose.FIELD)
            historyKey = Hkdf.derive(vk, KeyPurpose.HISTORY_HMAC)
            db = database
            _state.value = SessionState.Unlocked(clock.nowMillis(), safeMode)
            SafeLog.i(TAG, "vault unlocked")
        } catch (e: Exception) {
            vk.wipe()
            throw e
        } finally {
            dbKey.wipe()
        }
    }

    @Synchronized
    fun lock() {
        if (!isUnlocked && db == null) return
        try {
            db?.let { databaseFactory.closeAndWipe(it) }
        } catch (e: Exception) {
            SafeLog.w(TAG, "database close failed", e)
        }
        db = null
        vaultKey?.wipe(); vaultKey = null
        fieldKey?.wipe(); fieldKey = null
        historyKey?.wipe(); historyKey = null
        _state.value = SessionState.Locked
        SafeLog.i(TAG, "vault locked")
    }

    fun database(): RombaDatabase = db ?: throw VaultException(VaultError.VaultLocked)

    /** Copy of the Vault Key for key-management operations (change passphrase, enable biometric). Wipe after use. */
    fun vaultKeyCopy(): ByteArray = vaultKey?.copyOf() ?: throw VaultException(VaultError.VaultLocked)

    // ------------------------------------------------------------ field-level crypto

    fun encryptField(plaintext: String, fieldId: String): ByteArray {
        val key = fieldKey ?: throw VaultException(VaultError.VaultLocked)
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        try {
            return Aead.encrypt(key, bytes, fieldAad(fieldId)).toBytes()
        } finally {
            bytes.wipe()
        }
    }

    fun decryptField(ciphertext: ByteArray, fieldId: String): String {
        val key = fieldKey ?: throw VaultException(VaultError.VaultLocked)
        val plain = Aead.decrypt(key, AeadCiphertext.fromBytes(ciphertext), fieldAad(fieldId))
        try {
            return String(plain, Charsets.UTF_8)
        } finally {
            plain.wipe()
        }
    }

    /** Keyed digest used for password-history reuse detection. Never a plain hash. */
    fun historyDigest(password: String): ByteArray {
        val key = historyKey ?: throw VaultException(VaultError.VaultLocked)
        val bytes = password.toByteArray(Charsets.UTF_8)
        try {
            return Hkdf.hmac(key, bytes)
        } finally {
            bytes.wipe()
        }
    }

    private fun fieldAad(fieldId: String) = "rombamukiyam/field/v1|$fieldId".toByteArray(Charsets.UTF_8)

    companion object {
        private const val TAG = "VaultSession"
    }
}
