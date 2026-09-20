package com.rombamukiyam.vault.core.security

import android.os.Build
import java.io.File

/**
 * Heuristic, best-effort detection of obviously reduced platform security. Used ONLY to show a dismissible
 * warning. It never locks, wipes or otherwise alters the vault.
 */
class RootDetector {
    fun looksCompromised(): Boolean = hasTestKeys() || hasSuBinary() || hasMagiskPaths()

    private fun hasTestKeys(): Boolean = Build.TAGS?.contains("test-keys") == true

    private fun hasSuBinary(): Boolean = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su",
        "/system/bin/failsafe/su", "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su", "/su/bin/su",
    ).any { runCatching { File(it).exists() }.getOrDefault(false) }

    private fun hasMagiskPaths(): Boolean = listOf("/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules")
        .any { runCatching { File(it).exists() }.getOrDefault(false) }
}
