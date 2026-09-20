package com.rombamukiyam.vault.security

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Static regression checks over the source tree. These guard project-wide security rules that no runtime
 * test can see: no destructive migrations, no INTERNET permission, backups disabled, no forbidden crypto.
 */
class SourceSecurityRegressionTest {

    private val moduleDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/AndroidManifest.xml").exists() }

    private val mainSources: List<File> = File(moduleDir, "src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `no destructive migration fallbacks anywhere`() {
        val offenders = mainSources.filter { it.readText().contains("fallbackToDestructiveMigration") }
        assertWithMessage("destructive migration found in $offenders").that(offenders).isEmpty()
    }

    @Test
    fun `manifest declares no INTERNET permission and removes it from merged libraries`() {
        val manifest = File(moduleDir, "src/main/AndroidManifest.xml").readText()
        val internetLines = manifest.lines().filter { it.contains("android.permission.INTERNET") }
        assertThat(internetLines).isNotEmpty()
        internetLines.forEach { assertThat(it).contains("tools:node=\"remove\"") }
        assertThat(manifest).contains("android:allowBackup=\"false\"")
        assertThat(manifest).contains("android:usesCleartextTraffic=\"false\"")
        assertThat(manifest).doesNotContain("android:debuggable=\"true\"")
    }

    @Test
    fun `no forbidden primitives in production code`() {
        val forbidden = listOf("AES/ECB", "\"MD5\"", "AES/CBC/PKCS5Padding", "DESede", "\"SHA-1\"", "rememberSaveable")
        mainSources.forEach { file ->
            val text = file.readText()
            forbidden.forEach { token ->
                assertWithMessage("${file.name} uses forbidden token $token").that(text).doesNotContain(token)
            }
        }
    }

    @Test
    fun `only SafeLog talks to android util Log`() {
        val offenders = mainSources.filter { it.name != "SafeLog.kt" && it.readText().contains("android.util.Log") }
        assertWithMessage("direct Log usage in $offenders").that(offenders).isEmpty()
    }

    @Test
    fun `secret-bearing entities redact toString`() {
        val entityDir = File(moduleDir, "src/main/java/com/rombamukiyam/vault/data/entity")
        listOf("CredentialFieldEntity.kt", "VaultItemEntity.kt").forEach { name ->
            assertThat(File(entityDir, name).readText()).contains("override fun toString()")
        }
    }
}
