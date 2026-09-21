package com.rombamukiyam.vault.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema step ever shipped, in order. Migrations are additive and explicit; there is NO destructive
 * fallback anywhere in this project (a unit test enforces that the string never appears in source).
 *
 * Adding a version:
 *   1. bump [RombaDatabase.SCHEMA_VERSION]
 *   2. add `MIGRATION_n_m` here with hand-written SQL copied from the exported schema JSON
 *   3. add a MigrationTest case (n -> m and 1 -> m) with seeded rows
 *   4. document it in DATABASE.md
 *
 * Room runs each migration inside a transaction; a failure leaves the file at the previous version.
 */
object MigrationRegistry {

    /** v2: password history digests + per-account password policies. Purely additive. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `password_history` (`id` TEXT NOT NULL, `vaultItemId` TEXT NOT NULL, `fieldId` TEXT NOT NULL, " +
                    "`digestHex` TEXT NOT NULL, `current` INTEGER NOT NULL DEFAULT 1, `changedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`vaultItemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_vaultItemId` ON `password_history` (`vaultItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_digestHex` ON `password_history` (`digestHex`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_changedAt` ON `password_history` (`changedAt`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `password_policies` (`vaultItemId` TEXT NOT NULL, `minLength` INTEGER, `maxLength` INTEGER, " +
                    "`requireUppercase` INTEGER NOT NULL DEFAULT 0, `requireLowercase` INTEGER NOT NULL DEFAULT 0, " +
                    "`requireDigit` INTEGER NOT NULL DEFAULT 0, `requireSymbol` INTEGER NOT NULL DEFAULT 0, `allowedSymbols` TEXT, " +
                    "`forbiddenCharacters` TEXT, `notes` TEXT, `policySource` TEXT, `lastVerifiedAt` INTEGER, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`vaultItemId`), FOREIGN KEY(`vaultItemId`) REFERENCES `vault_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
