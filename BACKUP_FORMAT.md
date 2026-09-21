# `.rmvault` — Romba Mukiyam encrypted backup format (version 1)

A backup is a single file, e.g. `RombaMukiyam_Backup_2026-09-21.rmvault`, written through the Android
Storage Access Framework to a location the user chooses (USB, SD card, Downloads, computer). It is never
uploaded by the app.

## Container layout (all integers big-endian)

```
offset  size  field
0       8     magic            "RMVAULT\0"
8       2     formatVersion    u16 = 1
10      2     schemaVersion    u16 (Room schema the payload was exported from; 3 for this release)
12      1     kdfId            u8 = 1 (Argon2id)
13      1     saltLen          u8 (16)
14      n     salt             Argon2id salt (fresh random per backup)
+0      4     memoryKib        u32 (≥ 65536 enforced on read)
+4      4     iterations       u32 (≥ 3 enforced on read)
+8      1     parallelism      u8  (≥ 1)
+9      1     argonVersion     u8  (0x13)
+10     12    nonce            AES-GCM nonce (fresh random per backup)
+22     8     createdAt        i64 epoch millis
+30     4     ciphertextLen    u32 (= plaintext length + 16)
        …     ciphertext ‖ tag AES-256-GCM output (128-bit tag)
```

**The entire header is the GCM associated data.** Any modification of versions, KDF parameters, timestamp or
length is detected as an authentication failure, and a header advertising KDF parameters below the floor is
refused before any key derivation happens (no downgrade of the KDF cost through a tampered file).

## Key derivation

`key = Argon2id(masterPassphrase, salt, memoryKib, iterations, parallelism, 32 bytes)`

The parameters copied into the header are the device's calibrated parameters for the passphrase slot, with a
**fresh salt** per backup. Restoring requires the master passphrase that was in use when the backup was created.

## Payload (before encryption)

UTF-8 JSON, `BackupPayload`:

```
payloadVersion, schemaVersion, createdAt, vaultId, appVersion,
profiles[], items[], fields[] (values in clear inside the encrypted payload), history[],
tags[], itemTags[], policies[], reminders[],
manifest { counts of every list }
```

Field values are exported decrypted (they are re-encrypted with the destination device's field key on import),
which is what makes a restore onto a new phone possible. Password-history digests are **not** exported: they are
keyed to the source device's Vault Key, so the importer re-derives current digests from the restored values.

## Verification (after every backup and on demand)

1. read the file back from the provider (not from memory)
2. parse header → check magic, format version, KDF floor, length consistency
3. derive key → GCM decrypt (tag verifies header + payload)
4. parse JSON → manifest counts must match list sizes; referential sanity (fields → items, items → profiles)
5. compare item/field counts with what was exported

Only then is "Backup verified" shown and `lastBackupAt` recorded.

## Restore

1. steps 1–4 above (nothing written yet)
2. `schemaVersion` newer than the app → refused ("update the app first"); older payloads are accepted by the
   versioned importer (v1 payload today)
3. safety snapshot: copy `romba_vault.db` (+ WAL) and `keys/` to `files/safety/<timestamp>/`
4. ONE transaction: delete all rows → insert profiles, items, fields (re-encrypted), history, tags, policies,
   reminders → re-derive digests → `integrity_check` + count verification → record `RESTORED`
5. any exception → rollback → live vault untouched; the snapshot stays until the user deletes it in Vault Health

## Limits & compatibility

* Maximum file size 64 MiB (a family vault is a few hundred KB).
* `formatVersion` is bumped only for incompatible container changes; payload evolution uses `schemaVersion`
  plus importer logic, so older backups remain restorable.
* Filenames never contain owner names or secrets.
* Never: ZIP passwords, Base64-as-encryption, XOR, custom ciphers, plaintext exports.
