package com.rombamukiyam.vault.core.common

/**
 * Typed, user-safe failure categories. These never carry secrets and are what the UI maps to
 * plain-language messages. [cause] is kept only for diagnostics (exception class name), never shown.
 */
sealed class VaultError(open val cause: Throwable? = null) {
    data object VaultLocked : VaultError()
    data class AuthenticationFailed(val attemptsRemainingHint: Int? = null) : VaultError()
    data class DatabaseUnavailable(override val cause: Throwable? = null) : VaultError(cause)
    data class DatabaseIntegrityFailure(override val cause: Throwable? = null) : VaultError(cause)
    data class InsufficientStorage(override val cause: Throwable? = null) : VaultError(cause)
    data class BackupFailure(override val cause: Throwable? = null) : VaultError(cause)
    data class InvalidBackup(val reason: InvalidBackupReason, override val cause: Throwable? = null) : VaultError(cause)
    data object WrongRecoveryCredential : VaultError()
    data class MigrationFailure(val fromVersion: Int, val toVersion: Int, override val cause: Throwable? = null) :
        VaultError(cause)
    data object KeystoreInvalidated : VaultError()
    data class BiometricUnavailable(val reason: BiometricUnavailableReason) : VaultError()
    data object BiometricCancelled : VaultError()
    data object PermissionUnavailable : VaultError()
    data class ValidationFailed(val messages: List<String>) : VaultError()
    data class UnknownSafeFailure(override val cause: Throwable? = null) : VaultError(cause)

    /** Stable code for diagnostics. Never includes user data. */
    val code: String
        get() = when (this) {
            VaultLocked -> "E_LOCKED"
            is AuthenticationFailed -> "E_AUTH"
            is DatabaseUnavailable -> "E_DB_UNAVAILABLE"
            is DatabaseIntegrityFailure -> "E_DB_INTEGRITY"
            is InsufficientStorage -> "E_STORAGE_FULL"
            is BackupFailure -> "E_BACKUP"
            is InvalidBackup -> "E_BACKUP_INVALID_${reason.name}"
            WrongRecoveryCredential -> "E_RECOVERY_WRONG"
            is MigrationFailure -> "E_MIGRATION_${fromVersion}_$toVersion"
            KeystoreInvalidated -> "E_KEYSTORE_INVALIDATED"
            is BiometricUnavailable -> "E_BIOMETRIC_${reason.name}"
            BiometricCancelled -> "E_BIOMETRIC_CANCELLED"
            PermissionUnavailable -> "E_PERMISSION"
            is ValidationFailed -> "E_VALIDATION"
            is UnknownSafeFailure -> "E_UNKNOWN"
        }
}

enum class InvalidBackupReason { BAD_MAGIC, UNSUPPORTED_FORMAT, NEWER_SCHEMA, TRUNCATED, AUTH_FAILED, CORRUPT_PAYLOAD, COUNT_MISMATCH }

enum class BiometricUnavailableReason { NO_HARDWARE, HW_UNAVAILABLE, NONE_ENROLLED, SECURITY_UPDATE_REQUIRED, UNSUPPORTED, NOT_ENABLED }

/** Thrown internally to carry a [VaultError] across layers. Message is the code only. */
class VaultException(val error: VaultError) : Exception(error.code, error.cause)

/**
 * Result type used from use cases upwards. Prefer this over Kotlin's [Result] so failures are always typed.
 */
sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: VaultError) : Outcome<Nothing>()

    inline fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun onSuccess(block: (T) -> Unit): Outcome<T> {
        if (this is Success) block(value)
        return this
    }

    inline fun onFailure(block: (VaultError) -> Unit): Outcome<T> {
        if (this is Failure) block(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value
    val isSuccess: Boolean get() = this is Success
}

/** Runs [block] and converts any exception into a typed failure. */
inline fun <T> safeOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: VaultException) {
    Outcome.Failure(e.error)
} catch (e: Exception) {
    Outcome.Failure(ErrorMapper.map(e))
}
