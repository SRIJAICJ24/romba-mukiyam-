package com.rombamukiyam.vault.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.rombamukiyam.vault.core.common.SafeLog

enum class IntegrityLevel { QUICK, FULL }

data class IntegrityResult(
    val ok: Boolean,
    val level: IntegrityLevel,
    val checkedAt: Long,
    /** Table -> row count, useful for backup manifests and health screen. Never contains data. */
    val tableCounts: Map<String, Int>,
    val problemCount: Int,
)

/** Runs SQLite integrity pragmas. QUICK on every open (fast); FULL for backup/restore/migration/periodic. */
class IntegrityChecker {

    fun check(db: SupportSQLiteDatabase, level: IntegrityLevel, now: Long): IntegrityResult {
        val pragma = if (level == IntegrityLevel.QUICK) "PRAGMA quick_check;" else "PRAGMA integrity_check;"
        val problems = mutableListOf<String>()
        db.query(pragma).use { c ->
            while (c.moveToNext()) {
                val line = c.getString(0)
                if (line != "ok") problems += line
            }
        }
        val fkProblems = db.query("PRAGMA foreign_key_check;").use { it.count }
        val counts = TABLES.associateWith { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
        }
        val ok = problems.isEmpty() && fkProblems == 0 && counts.values.none { it < 0 }
        if (!ok) SafeLog.e(TAG, "integrity check failed: problems=${problems.size} fk=$fkProblems")
        return IntegrityResult(ok, level, now, counts, problems.size + fkProblems)
    }

    companion object {
        private const val TAG = "Integrity"
        val TABLES = listOf("family_profiles", "vault_items", "credential_fields", "credential_history", "tags", "vault_item_tags", "password_history", "password_policies")
    }
}
