# Romba Mukiyam — Database

Room 2.8 over **SQLCipher for Android 4.19** (`net.zetetic:sqlcipher-android`). One database file,
`databases/romba_vault.db`, WAL journal mode, `PRAGMA cipher_memory_security = ON`, `secure_delete = ON`,
foreign keys enforced. The key is the 32-byte HKDF sub-key of the Vault Key passed as a raw key (`x'hex'`), so
SQLCipher's own PBKDF2 is bypassed (the key already has 256 bits of entropy).

Current schema version: **3**. Exported schemas live in `app/schemas/…/1.json`, `2.json`, `3.json`.

## Entities

| Table | Purpose | Notes |
|---|---|---|
| `family_profiles` | Owners (Dad, Mom, Joint…) | Archive, never delete |
| `vault_items` | Aggregate root per credential/document/note | Non-secret metadata; `deletedAt` = in Trash |
| `credential_fields` | Dynamic fields | `encryptedValue` BLOB = AES-GCM(nonce‖ct‖tag), AAD = field id; `isSecret`, `isSensitive` |
| `credential_history` | Event log (created, password changed, reviewed, trashed, restored, backup…) | Events only, never values; `vaultItemId` nullable for vault-wide events |
| `tags`, `vault_item_tags` | Free tags | unique tag names |
| `password_history` (v2) | Keyed digests for reuse detection | `current` flag; digests HMAC-SHA256 keyed from the Vault Key |
| `password_policies` (v2) | Per-item password rules | min/max length, class requirements, allowed/forbidden symbols, source, last verified |
| `reminders` (v3) | Review / expiry reminders | unique (`vaultItemId`, `type`); `nextDueDate`, `notifyBeforeDays` |
| `backup_metadata` (v3) | Backup created/restored records | no vault content |
| `diagnostic_events` (v3) | Safe error codes | user-clearable |

## Relationships & constraints

```
family_profiles 1 ──< vault_items          ownerId, ON DELETE RESTRICT  (a profile owning items cannot be deleted)
vault_items     1 ──< credential_fields    ON DELETE CASCADE
vault_items     1 ──< credential_history   ON DELETE CASCADE (nullable for vault events)
vault_items     1 ──< password_history     ON DELETE CASCADE
vault_items     1 ──1 password_policies    ON DELETE CASCADE
vault_items     1 ──< reminders            ON DELETE CASCADE (nullable for vault-wide reminders)
vault_items    >──< tags via vault_item_tags (CASCADE both sides)
```

Items are the aggregate root: cascading from an item is intentional because items only reach permanent deletion
through Trash + re-authentication. Profiles use RESTRICT so no credential can ever vanish through a profile.

## Indexes

`vault_items(ownerId, category, title, favorite, updatedAt, deletedAt, institution)`,
`credential_fields(vaultItemId, (vaultItemId, displayOrder))`, `credential_history(vaultItemId, occurredAt, eventType)`,
`password_history(vaultItemId, digestHex, changedAt)`, `reminders(vaultItemId, nextDueDate, type, unique(vaultItemId,type))`,
`backup_metadata(createdAt)`, `diagnostic_events(occurredAt)`, `tags(name unique)`. **No index touches a secret value.**

## Transactions

Multi-step writes are atomic (`@Transaction` DAO methods or `withTransaction`):

* create/update item: item + fields + history + tag links + password digests + derived reminders
* change password: field value + `passwordLastChangedAt` + history event + retire/insert digest + reminder sync
* mark reviewed, move to trash, restore
* backup restore: delete all tables → import → full integrity check → count verification (rollback on any failure)

## Search

`VaultItemDao.searchMetadata` joins title, subtitle, institution, domain, category, owner name and tags with
`LIKE`. Field values (including usernames and customer IDs) are never searched.

## Schema history & migration rules

| Version | Change | Migration |
|---|---|---|
| 1 | initial tables | — |
| 2 | + `password_history`, `password_policies` | `MIGRATION_1_2` (additive `CREATE TABLE`/`CREATE INDEX`) |
| 3 | + `reminders`, `backup_metadata`, `diagnostic_events` | `MIGRATION_2_3` (additive) |

Rules:

1. **Never** `fallbackToDestructiveMigration*()` — a unit test scans the source tree and fails if it appears.
2. Every step is hand-written SQL copied from the exported schema JSON, registered in `MigrationRegistry.ALL`.
3. `MigrationTest` (instrumented, runs over SQLCipher) seeds realistic rows and validates 1→2, 2→3 and 1→3, checking
   row counts, byte-identical encrypted blobs and cascade behaviour.
4. Room runs each migration in a transaction; on failure the file stays at the previous version and the app offers
   read-only Safe Mode + backup instead of touching data.
5. Integrity: `quick_check` on every open; `integrity_check` + `foreign_key_check` + per-table counts for backup,
   restore, Vault Health and on demand.
