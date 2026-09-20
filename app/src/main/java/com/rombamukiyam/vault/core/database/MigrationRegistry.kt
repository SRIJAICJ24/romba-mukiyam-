package com.rombamukiyam.vault.core.database

import androidx.room.migration.Migration

/**
 * Every schema step ever shipped, in order. Migrations are additive and explicit; there is NO destructive
 * fallback anywhere in this project (a unit test enforces that the string never appears in source).
 *
 * Adding a version:
 *   1. bump [RombaDatabase.SCHEMA_VERSION]
 *   2. add `MIGRATION_n_m` here with hand-written SQL
 *   3. export the schema JSON and add a MigrationTest case (n -> m and 1 -> m)
 *   4. document it in DATABASE.md
 */
object MigrationRegistry {
    val ALL: Array<Migration> = arrayOf()
}
