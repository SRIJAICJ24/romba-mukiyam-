package com.rombamukiyam.vault.core.security

import com.rombamukiyam.vault.core.common.SafeLog
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Persists the [Keyring] as JSON in private app storage with crash-safe writes:
 *   write temp -> fsync -> rename over previous (atomic on the same filesystem)
 * A `.bak` copy of the previous keyring is kept so an interrupted write can never leave zero valid copies.
 */
class KeyringStore(private val dir: File) {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file get() = File(dir, FILE_NAME)
    private val tmp get() = File(dir, "$FILE_NAME.tmp")
    private val bak get() = File(dir, "$FILE_NAME.bak")

    fun exists(): Boolean = file.exists() || bak.exists()

    @Synchronized
    fun read(): Keyring? {
        readFrom(file)?.let { return it }
        // Primary missing/corrupt: fall back to the backup copy, but never delete the primary.
        val fallback = readFrom(bak)
        if (fallback != null) SafeLog.w(TAG, "primary keyring unreadable; using .bak copy")
        return fallback
    }

    @Synchronized
    @Throws(IOException::class)
    fun write(keyring: Keyring) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create key directory")
        val bytes = json.encodeToString(Keyring.serializer(), keyring).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        // Verify what we wrote before replacing anything.
        val verify = readFrom(tmp) ?: throw IOException("keyring verification failed after write")
        if (verify != keyring) throw IOException("keyring verification mismatch")
        if (file.exists()) {
            bak.delete()
            if (!file.renameTo(bak)) {
                // Fallback: copy then continue; primary still intact.
                file.copyTo(bak, overwrite = true)
            }
        }
        if (!tmp.renameTo(file)) throw IOException("keyring rename failed")
        fsyncDirectory()
    }

    private fun readFrom(f: File): Keyring? {
        if (!f.exists()) return null
        return try {
            json.decodeFromString(Keyring.serializer(), f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            SafeLog.w(TAG, "keyring parse failed for ${f.name}", e)
            null
        }
    }

    private fun fsyncDirectory() {
        // Best effort: directory fsync is not exposed on Android's java.io; the file fsync + rename is the guarantee.
    }

    companion object {
        private const val TAG = "KeyringStore"
        const val FILE_NAME = "keyring.json"
        const val DIR_NAME = "keys"
    }
}
