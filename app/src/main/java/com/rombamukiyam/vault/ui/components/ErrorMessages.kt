package com.rombamukiyam.vault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.BiometricUnavailableReason
import com.rombamukiyam.vault.core.common.InvalidBackupReason
import com.rombamukiyam.vault.core.common.VaultError

/** Plain-language, secret-free messages. Never shows exception text. */
@Composable
fun VaultError.message(): String = when (this) {
    VaultError.VaultLocked -> stringResource(R.string.err_locked)
    is VaultError.AuthenticationFailed -> stringResource(R.string.err_auth_failed)
    is VaultError.DatabaseUnavailable -> stringResource(R.string.err_db_unavailable)
    is VaultError.DatabaseIntegrityFailure -> stringResource(R.string.err_db_integrity)
    is VaultError.InsufficientStorage -> stringResource(R.string.err_storage)
    is VaultError.BackupFailure -> stringResource(R.string.err_backup)
    is VaultError.InvalidBackup -> when (reason) {
        InvalidBackupReason.BAD_MAGIC -> stringResource(R.string.err_backup_not_rmvault)
        InvalidBackupReason.UNSUPPORTED_FORMAT -> stringResource(R.string.err_backup_unsupported)
        InvalidBackupReason.NEWER_SCHEMA -> stringResource(R.string.err_backup_newer)
        InvalidBackupReason.TRUNCATED, InvalidBackupReason.CORRUPT_PAYLOAD, InvalidBackupReason.COUNT_MISMATCH ->
            stringResource(R.string.err_backup_corrupt)
        InvalidBackupReason.AUTH_FAILED -> stringResource(R.string.err_backup_auth)
    }
    VaultError.WrongRecoveryCredential -> stringResource(R.string.err_recovery_wrong)
    is VaultError.MigrationFailure -> stringResource(R.string.err_migration)
    VaultError.KeystoreInvalidated -> stringResource(R.string.err_keystore_invalidated)
    is VaultError.BiometricUnavailable -> when (reason) {
        BiometricUnavailableReason.NONE_ENROLLED -> stringResource(R.string.err_bio_none_enrolled)
        BiometricUnavailableReason.NO_HARDWARE -> stringResource(R.string.err_bio_no_hardware)
        BiometricUnavailableReason.NOT_ENABLED -> stringResource(R.string.err_bio_not_enabled)
        else -> stringResource(R.string.err_bio_unavailable)
    }
    VaultError.BiometricCancelled -> stringResource(R.string.err_bio_cancelled)
    VaultError.PermissionUnavailable -> stringResource(R.string.err_permission)
    is VaultError.ValidationFailed -> messages.firstOrNull() ?: stringResource(R.string.err_validation)
    is VaultError.UnknownSafeFailure -> stringResource(R.string.err_unknown)
}
