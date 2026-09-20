package com.rombamukiyam.vault.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.core.crypto.toHex
import com.rombamukiyam.vault.core.crypto.wipe
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Opens the Room database on top of SQLCipher.
 *
 *  - The key is the 32-byte database sub-key derived from the Vault Key (HKDF). It is passed to SQLCipher in
 *    raw-key form (x'hex') so SQLCipher does NOT run its own PBKDF2 on it — the entropy is already 256 bits.
 *  - The passphrase buffer is cleared by SQLCipher after keying (clearPassphrase = true).
 *  - Destructive migration fallbacks are intentionally absent. A failing migration throws and the file is preserved.
 */
class DatabaseFactory(private val context: Context) {

    fun databaseFile(): File = context.getDatabasePath(RombaDatabase.FILE_NAME)

    fun exists(): Boolean = databaseFile().exists()

    fun open(databaseKey: ByteArray, file: File = databaseFile()): RombaDatabase {
        require(databaseKey.size == 32) { "database key must be 32 bytes" }
        val rawKey = "x'${databaseKey.toHex()}'".toByteArray(Charsets.US_ASCII)
        val factory = SupportOpenHelperFactory(rawKey, hook, /* clearPassphrase = */ true)
        val db = Room.databaseBuilder(context, RombaDatabase::class.java, file.absolutePath)
            .openHelperFactory(factory)
            .addMigrations(*MigrationRegistry.ALL)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(callback)
            .build()
        // Force the connection open now so a wrong key / corrupt file fails here, not lazily inside a screen.
        db.openHelper.writableDatabase
        rawKey.wipe()
        return db
    }

    private val hook = object : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection) {
            // Wipe sensitive memory (keys/page buffers) as SQLCipher frees it.
            connection.execute("PRAGMA cipher_memory_security = ON;", null, null)
        }

        override fun postKey(connection: SQLiteConnection) {
            // Nothing: defaults (4.x: AES-256-CBC pages, HMAC-SHA512) are what we want.
        }
    }

    private val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL("PRAGMA secure_delete = ON;")
            SafeLog.d(TAG, "database opened")
        }
    }

    companion object {
        private const val TAG = "DatabaseFactory"
    }
}
