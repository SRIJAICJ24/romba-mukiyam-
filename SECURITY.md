# Romba Mukiyam — Security Model & Threat Model

This document describes what Romba Mukiyam protects, how, and — just as importantly — what it does **not**
protect against. Read the *Remaining limitations* section before storing real family credentials.

## 1. Design principles

1. **Offline by construction.** The app declares no `INTERNET` permission and explicitly removes it from the
   merged manifest (`tools:node="remove"`), so no library can add it back. There is no account, cloud, analytics,
   telemetry or crash upload.
2. **Fail closed.** If the authentication or key state cannot be confirmed, the vault stays locked.
3. **Never destroy data to recover.** No destructive migrations, no "reset vault" shortcuts, safety snapshots
   before restore, rollback-only transactions.
4. **No back door.** There is no developer password, hidden key, remote unlock or debug bypass. If the master
   passphrase and the recovery key are both lost, the vault cannot be opened by anyone.

## 2. Key hierarchy

```
Master passphrase ──Argon2id(salt, m≥64 MiB, t≥3, p≥1)──► Master Derived Key (32 B)
                                                             │ AES-256-GCM wrap
Recovery key (256-bit random, shown once) ──HKDF-SHA256──► Recovery wrap key ──┐ AES-256-GCM wrap
Android Keystore AES-256-GCM key (biometric-bound, non-exportable) ─────────────┤ AES-256-GCM wrap
                                                                                ▼
                                                        Vault Key (256-bit random, RAM only while unlocked)
                                                                                │ HKDF-SHA256
                              ┌──────────────────────────────┬──────────────────┴─────────────────┐
                    SQLCipher database key           Field key (AES-256-GCM)        History HMAC key
                    (raw key, x'hex' form)           per-field ciphertext            keyed digests for
                                                     inside the encrypted DB         reuse detection
```

* The three wrapped copies of the Vault Key live in `files/keys/keyring.json` (with an atomic `.tmp` → rename
  write and a `.bak` copy). Every slot's metadata (type, salt, KDF cost, Keystore alias, vault id) is bound as
  GCM associated data, so slot metadata cannot be swapped or downgraded without detection.
* Argon2id parameters are calibrated per device at onboarding (target ≈ 700 ms) and never go below the floor
  (64 MiB / 3 iterations / p=1). They are stored with the slot and enforced again on read.
* Changing the master passphrase re-wraps the same Vault Key with a fresh salt (atomic keyring replacement); the
  database is not re-encrypted. Existing backups still require the passphrase that was current when they were made.
* The biometric slot uses `setUserAuthenticationRequired(true)`, per-use authentication
  (`AUTH_BIOMETRIC_STRONG`, timeout 0) and `setInvalidatedByBiometricEnrollment(true)`. StrongBox is attempted
  and falls back to the TEE keystore. If enrolment changes, the slot is dropped and the user is asked for the
  master passphrase; the vault itself is untouched.

## 3. Data at rest

| Data | Location | Protection |
|---|---|---|
| Vault database | `databases/romba_vault.db` (private storage) | SQLCipher 4 (AES-256-CBC pages, HMAC-SHA512), raw 256-bit key |
| Secret and non-secret field values | `credential_fields.encryptedValue` | AES-256-GCM under the field key, AAD = field id (defence in depth inside SQLCipher) |
| Keyring | `files/keys/keyring.json` | Only wrapped keys; see §2 |
| Password history | `password_history.digestHex` | HMAC-SHA256 keyed from the Vault Key — never a plain hash |
| Reminder schedule for notifications | `files/schedule/reminder_schedule.json` | **Plaintext by design**: contains only due dates and reminder types; item names only if the user opts in |
| Preferences | Jetpack DataStore | Non-secret settings only (theme, timeouts, toggles) |
| Backups | User-chosen location via Storage Access Framework | `.rmvault`: AES-256-GCM under Argon2id(master passphrase); see BACKUP_FORMAT.md |
| Safety snapshots | `files/safety/<timestamp>/` | Copies of the encrypted DB + keyring taken before each restore |

Android Auto Backup and device-to-device transfer are disabled (`allowBackup=false`, full exclusion rules for
both `fullBackupContent` and `dataExtractionRules`).

## 4. Data in use

* The Vault Key and derived keys live only in `VaultSession` memory and are zeroed on lock. Locking happens on
  auto-lock timeout after backgrounding (default 30 s), on `ACTION_SCREEN_OFF`, on tapping Lock, and implicitly
  on process death (the key is never persisted or saved to instance state).
* `FLAG_SECURE` is set on the Activity window from the stored preference (on by default) before content is
  drawn: screenshots, screen recording and the Recent Apps thumbnail are blocked.
* Secret fields are masked; Reveal auto-hides after 15 s (configurable) and on `ON_PAUSE`. High Security items
  (banks by default) require biometric/device-credential confirmation (or the passphrase) before reveal/copy,
  with a 30 s grace period.
* Clipboard copies are marked `EXTRA_IS_SENSITIVE` and cleared after 30 s (configurable) by an in-process timer
  plus a WorkManager fallback; only clips we recognise (label or content digest) are cleared.
* No `rememberSaveable` is used for secrets; typed passphrases are cleared from ViewModel state on `ON_STOP`.
* Logging goes through `SafeLog` only (codes and exception class names); release builds strip `android.util.Log`
  via R8. Entities that could contain secrets override `toString()`.
* Notifications are generic by default ("1 item needs attention"); the public (lock-screen) version is always
  generic. Never usernames, IDs or passwords.

## 5. Threat analysis

| Threat | Protection | Residual risk |
|---|---|---|
| Lost / stolen phone (locked) | Vault Key not on disk unwrapped; Argon2id on passphrase; Keystore-bound biometric key; auto-lock | A weak passphrase can be brute-forced offline if the attacker extracts `keyring.json` + DB. Use a long passphrase. |
| Database extraction (root, forensic tools) | SQLCipher + per-field AES-GCM; key not stored | Same as above: offline guessing against Argon2id. |
| Stolen backup file | AES-256-GCM, Argon2id-derived key, authenticated header | Offline guessing against the passphrase used for that backup. Keep backups physically safe. |
| Brute force on the device | Argon2id cost, UI cooldown after repeated failures (5/20/60 s) | UI cooldown is not a security boundary against extracted data. |
| Malicious app | No exported components beyond the launcher; no content providers; no deep links; secrets never in Intents | A device-level compromise (root malware) can read RAM while unlocked. |
| Clipboard exposure | Sensitive flag, auto-clear, generic snackbars | Other apps may read the clipboard while it is set (Android ≥ 10 limits this to the focused app / IME). |
| Screenshots / screen recording | `FLAG_SECURE` | Can be disabled by the user in Settings; physical cameras. |
| Shoulder surfing | Masked by default, short reveal timeout, `PasswordVisualTransformation` | Revealed values are visible while shown. |
| Rooted device | Heuristic warning only | A rooted or compromised OS can defeat any app-level protection. |
| Accidental deletion | Trash with retention (30 d default), permanent deletion needs re-auth, RESTRICT FK on profiles | Emptying Trash is final unless a backup exists. |
| Migration failure | Explicit additive migrations run in a transaction; no destructive fallback; Safe Mode | A failed migration leaves the file untouched at the old version. |
| Corrupted database | `quick_check` on open, full check for backup/restore/health; Safe Mode (read-only) offered | Recovery relies on having a verified backup. |
| Forgotten master passphrase | Recovery key (shown once, regenerable while unlocked) | Losing both = permanent loss. This is deliberate. |
| Interrupted save / crash / power loss | Room transactions over SQLCipher (WAL); keyring atomic write with `.bak` | — |
| Low storage | `SQLiteFullException` mapped to a safe message; transaction rolls back | — |

## 6. Permissions actually requested

`USE_BIOMETRIC`, `POST_NOTIFICATIONS`. Libraries add `USE_FINGERPRINT` (Biometric compat), `WAKE_LOCK`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (WorkManager runtime). **No `INTERNET`, no storage permissions**
(files are accessed only through the Storage Access Framework document picker).

## 7. Remaining limitations (be honest with yourself before storing real data)

1. Security is bounded by the **master passphrase**. Choose a long, unique passphrase (four unrelated words or more).
2. The emulator/CI runs could not verify **hardware-backed Keystore or StrongBox** behaviour; on a real phone
   Settings → Security shows whether the fingerprint key is in secure hardware.
3. The **reminder schedule file** is plaintext by design; keep "Show item names in notifications" off if the
   phone is shared.
4. Password strength scoring is a local heuristic, not a breach check (no network by design).
5. The clipboard is cleared best-effort; some OEM keyboards keep their own clipboard history.
6. A rooted or malware-infected device can capture secrets while the vault is unlocked.
7. Backups are protected by the passphrase in use when they were created — if you change the passphrase,
   remember the old one for old backups, or create a fresh backup.
8. This is a personal project reviewed by its author and automated tests, not by an independent security audit.
