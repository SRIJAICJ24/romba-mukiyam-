package com.rombamukiyam.vault.core.common

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import javax.crypto.AEADBadTagException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class ErrorMapperTest {

    @Test
    fun `storage full maps to InsufficientStorage`() {
        assertThat(ErrorMapper.map(SQLiteFullException("full"))).isInstanceOf(VaultError.InsufficientStorage::class.java)
        assertThat(ErrorMapper.map(IOException("write failed: ENOSPC (No space left on device)"))).isInstanceOf(VaultError.InsufficientStorage::class.java)
    }

    @Test
    fun `corruption maps to DatabaseIntegrityFailure`() {
        assertThat(ErrorMapper.map(SQLiteDatabaseCorruptException("bad"))).isInstanceOf(VaultError.DatabaseIntegrityFailure::class.java)
    }

    @Test
    fun `bad tag maps to authentication failure and unknown errors are safe`() {
        assertThat(ErrorMapper.map(AEADBadTagException())).isInstanceOf(VaultError.AuthenticationFailed::class.java)
        assertThat(ErrorMapper.map(IllegalStateException("boom"))).isInstanceOf(VaultError.UnknownSafeFailure::class.java)
    }

    @Test
    fun `codes never include messages and diagnostics bus skips expected outcomes`() {
        val recorded = mutableListOf<String>()
        DiagnosticsBus.sink = { code, _ -> recorded += code }
        safeOutcome<Unit> { throw VaultException(VaultError.AuthenticationFailed()) }
        safeOutcome<Unit> { throw VaultException(VaultError.ValidationFailed(listOf("x"))) }
        safeOutcome<Unit> { throw IllegalStateException("secret-looking message 1234") }
        DiagnosticsBus.sink = null
        assertThat(recorded).containsExactly("E_UNKNOWN")
        assertThat(VaultError.MigrationFailure(1, 2).code).isEqualTo("E_MIGRATION_1_2")
        assertThat(VaultError.UnknownSafeFailure(IllegalStateException("do not leak")).code).doesNotContain("leak")
    }

    @Test
    fun `outcome helpers`() {
        val ok: Outcome<Int> = safeOutcome { 21 }
        assertThat(ok.map { it * 2 }.getOrNull()).isEqualTo(42)
        val bad: Outcome<Int> = safeOutcome { throw VaultException(VaultError.VaultLocked) }
        assertThat(bad.isSuccess).isFalse()
        assertThat((bad as Outcome.Failure).error).isEqualTo(VaultError.VaultLocked)
    }
}
