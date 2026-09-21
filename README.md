# Romba Mukiyam

**Your family's private vault.** *Romba Mukiyam* ("very important") is a fully offline Android password and
credential lifecycle manager built for one family: banking logins, customer IDs, email credentials, cards,
documents, recovery information and secure notes — plus review reminders, expiry alerts, password health and
encrypted backups.

* 100% offline — **no `INTERNET` permission**, no account, no cloud, no analytics, no telemetry.
* Room + **SQLCipher** database, per-field **AES-256-GCM**, **Argon2id** master passphrase, **Android Keystore**
  fingerprint unlock, one-time **recovery key**, encrypted **`.rmvault`** backups.
* Designed for a parent aged 50+: large text, obvious buttons, plain words, confirmations before anything risky.

Read [SECURITY.md](SECURITY.md) before storing real credentials. Also see [ARCHITECTURE.md](ARCHITECTURE.md),
[DATABASE.md](DATABASE.md) and [BACKUP_FORMAT.md](BACKUP_FORMAT.md).

## Features (V1)

| Area | What you get |
|---|---|
| Family profiles | Dad / Mom / Me / Sibling / Joint / Other, archive & restore (never deletes their credentials) |
| Vault | Bank, Website, App, Email, Card, Identity, Document, Insurance, Investment, Utility, Secure Note, Wi‑Fi, Other — with per-category templates and **dynamic custom fields** (customer ID, CIF/CRN, profile & transaction passwords, recovery codes…) |
| Secrets | Masked by default, reveal with auto-hide, High-Security items re-check fingerprint/passphrase, secure clipboard auto-clear, no screenshots |
| Search & favourites | Fast local search over titles, institutions, owners, categories, tags — never over secrets |
| Password tools | Generator (random / memorable / EFF passphrase), strength meter, per-account **password rules**, reuse detection via keyed digests, guided **manual** password-change workflow, health report |
| Alerts | Review policies (30–365 days), card & document expiry, backup reminders; privacy-preserving notifications via WorkManager |
| Trash | 30-day retention (configurable), restore, permanent delete behind re-authentication |
| Backup & recovery | Verified encrypted `.rmvault` backups to USB/SD/computer via the system file picker, safe restore with automatic safety snapshot, printable Emergency Recovery Kit, change master passphrase, regenerate recovery key |
| Hardening | Safe Mode (read-only) when the database reports problems, Vault Health integrity checks, local diagnostics (codes only), root warning |

Deliberately **not** stored: OTPs, UPI/ATM PINs, CVVs (the form refuses such labels).
Deliberately **not** included in V1: cloud sync, autofill, browser extension, AI, TOTP, bank integrations.

## Security model in one paragraph

A random 256-bit Vault Key encrypts everything. It exists unwrapped only in RAM while the vault is open and is
stored on disk only wrapped three ways: under Argon2id(master passphrase), under the recovery key, and under a
biometric-bound Android Keystore key. Losing the passphrase **and** the recovery key makes the vault
unrecoverable — there is no back door. Details and residual risks: [SECURITY.md](SECURITY.md).

## Requirements

* Android 8.0 (API 26) or newer; fingerprint sensor optional (master passphrase always works).
* Build: JDK 17, Android SDK platform 37 + build-tools 36, Gradle 9.6 (wrapper), AGP 9.4.

## Build

```bash
# Windows (PowerShell): point Gradle at a JDK 17
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"   # or any JDK 17 path
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest          # JVM tests
.\gradlew.bat lintDebug                  # lint (errors fail the build)
.\gradlew.bat connectedDebugAndroidTest  # instrumentation tests (device/emulator with an enrolled fingerprint)
.\gradlew.bat assembleDebug assembleRelease
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release.apk`.

### Release signing

`app/build.gradle.kts` reads `%USERPROFILE%\.rombamukiyam\keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it the release APK is built **unsigned**.
Back up the keystore and its password — future updates must be signed with the same key to install over the
existing app.

## Tests

* **JVM**: AES-GCM (tamper/nonce/wrong key), HKDF RFC 5869 vectors, recovery key codec, key hierarchy
  (create/unlock/change passphrase/recovery/AAD binding/atomic keyring), password generator & policies, expiry
  parsing, error mapping, and static security regressions (no destructive migrations, no `INTERNET`, no
  `rememberSaveable`, only `SafeLog` logs).
* **Instrumented** (SQLCipher + Keystore + WorkManager on device): Argon2id, encrypted DB open/wrong key/plaintext
  absence/multi-connection, FK RESTRICT, Keystore auth-bound key refused without biometric, migrations 1→2, 2→3,
  1→3 with seeded data, repository atomicity/rollback, trash, search, 500 items, backup round trip, tamper/
  truncation/wrong passphrase/newer schema/weakened KDF rejection with the live vault byte-identical, reminder
  worker notification privacy, Compose onboarding flow.

## First-time setup

1. Install the APK, open **Romba Mukiyam**.
2. Read the offline promise, create a **long master passphrase** (a sentence of 4+ unrelated words) and confirm it.
3. **Write down the recovery key** shown once, and tick the acknowledgement.
4. Enable fingerprint unlock (optional; passphrase remains the fallback).
5. Create the first family profile and choose a backup reminder interval.

## Backup

Settings → Backup & Recovery → **Create encrypted backup** → choose USB drive / SD card / folder → enter your
master passphrase. The app writes the `.rmvault` file, reads it back and verifies it before reporting success.
Keep two copies in two places. Use **Verify a backup file** any time to check a copy.

## Restore / recovery

* Fingerprint stopped working (new fingerprints enrolled): unlock with the master passphrase, re-enable
  fingerprint in Settings.
* Forgot the master passphrase: Lock screen → **Recovery** → enter the recovery key → set a new passphrase.
* New or wiped phone: install the app, complete onboarding (any new passphrase), then Settings → Backup &
  Recovery → **Restore from backup** → pick the `.rmvault` → enter the passphrase that was in use **when that
  backup was made**. A safety copy of the current vault is taken first; a failed restore leaves it untouched.
* Database problem reported at unlock: choose **Open in Safe Mode (read-only)**, create a backup, then restore a
  verified backup.

## Known limitations

See [SECURITY.md → Remaining limitations](SECURITY.md#7-remaining-limitations-be-honest-with-yourself-before-storing-real-data).
Additionally: English only in V1 (all strings are resources; Tamil can be added), no widgets, no autofill,
notifications require granting the permission on Android 13+.

## Licence notes

Third-party components: AndroidX / Kotlin / Hilt (Apache 2.0), SQLCipher for Android (BSD-style),
Argon2Kt (MIT) over the Argon2 reference implementation (CC0/Apache 2.0), EFF short wordlist (CC BY 3.0).
