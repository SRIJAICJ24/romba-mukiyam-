package com.rombamukiyam.vault.core.common

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import java.io.IOException
import javax.crypto.AEADBadTagException

/** Maps low-level exceptions to typed [VaultError]s. Never leaks the exception message to the UI. */
object ErrorMapper {
    fun map(e: Throwable): VaultError = when {
        e is VaultException -> e.error
        e is SQLiteFullException -> VaultError.InsufficientStorage(e)
        e is SQLiteDatabaseCorruptException -> VaultError.DatabaseIntegrityFailure(e)
        // SQLCipher's own exception hierarchy (net.zetetic.database.sqlcipher.*) mirrors the framework names.
        e.javaClass.simpleName == "SQLiteFullException" -> VaultError.InsufficientStorage(e)
        e.javaClass.simpleName == "SQLiteDatabaseCorruptException" -> VaultError.DatabaseIntegrityFailure(e)
        e.javaClass.simpleName == "SQLiteDiskIOException" -> VaultError.DatabaseUnavailable(e)
        e is AEADBadTagException -> VaultError.AuthenticationFailed()
        e is IOException && (e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true) ->
            VaultError.InsufficientStorage(e)
        e is IOException -> VaultError.DatabaseUnavailable(e)
        else -> VaultError.UnknownSafeFailure(e)
    }
}
