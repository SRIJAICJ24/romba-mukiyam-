# Romba Mukiyam — Architecture

Single Gradle module (`app`), Kotlin, Jetpack Compose (Material 3), Clean Architecture + MVVM, Hilt.
Package root: `com.rombamukiyam.vault`.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ui / feature (Compose)                                                       │
│  RombaAppRoot ─ routes purely from SessionState + settings                   │
│    Onboarding · Lock · MainShell(Home, Family, Vault, Alerts, Settings)      │
│    Credential detail / form / change-password / policy · Backup · Health     │
│  ViewModels: StateFlow<UiState>, Channel<Effect>; no secrets in SavedState   │
├──────────────────────────────────────────────────────────────────────────────┤
│ domain                                                                       │
│  models (VaultItem, FieldDraft, Reminder, PasswordPolicy …)                  │
│  repository interfaces (VaultRepository, FamilyRepository, ReminderRepository│
│  SettingsRepository, DiagnosticsRepository, VaultEventLogger)                │
│  use cases (Unlock/Lock, Create/Update/Delete/Restore item, Search,          │
│  GeneratePassword, CheckPasswordHealth, UpdatePassword, Create/Verify/Restore │
│  Backup, GetUpcomingAlerts, OnUnlockMaintenance …) → Outcome<T>              │
├──────────────────────────────────────────────────────────────────────────────┤
│ data                                                                         │
│  Room entities + DAOs (schema v3) · repository implementations · mappers     │
├──────────────────────────────────────────────────────────────────────────────┤
│ core                                                                         │
│  crypto:   Aead (AES-GCM) · Hkdf · Argon2Kdf · RecoveryKeyCodec ·            │
│            PasswordGenerator · PasswordStrength · SecureRandomProvider        │
│  security: VaultKeyManager · Keyring/KeyringStore · KeystoreManager ·         │
│            BiometricAuthenticator · VaultSession · AutoLockController ·       │
│            SecureClipboard · ReminderSchedulePublisher · RootDetector          │
│  database: RombaDatabase (Room) · DatabaseFactory (SQLCipher) ·              │
│            MigrationRegistry · IntegrityChecker                              │
│  backup:   RmvaultFormat · BackupPayload · BackupManager · RecoveryKitPrinter │
│  common:   VaultError/Outcome/safeOutcome · ErrorMapper · SafeLog ·          │
│            DiagnosticsBus · Clock · DispatcherProvider                        │
├──────────────────────────────────────────────────────────────────────────────┤
│ worker: ReminderCheckWorker (12 h periodic) · ClipboardClearWorker           │
│ di: CoreModule · DataModule (Hilt singletons)                                │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Layering rules

* Composables only talk to ViewModels; ViewModels only call use cases (and a few read-only repositories for flows).
* Use cases return `Outcome<T>` (`Success | Failure(VaultError)`); exceptions are mapped by `ErrorMapper`, never
  shown raw. `safeOutcome {}` also reports the error *code* to `DiagnosticsBus`.
* Repositories obtain the database through `VaultSession.database()`, which throws `VaultLocked` when locked and
  `assertWritable()` refuses writes in Safe Mode. Flows are wrapped with `whenUnlocked` so a lock never crashes a
  collecting screen — it just yields empty data.
* All crypto, KDF, database, backup and integrity work runs on `Dispatchers.Default/IO` via `DispatcherProvider`.

## Session & lock lifecycle

```
process start ──► SessionState.Locked (key only exists in RAM)
   unlock(passphrase | biometric | recovery) ──► VaultKeyManager unwraps VK
        ──► VaultSession.unlock: HKDF sub-keys, DatabaseFactory.open (SQLCipher raw key),
            quick_check ──► Unlocked(safeMode?) ──► OnUnlockMaintenance (trash purge, schedule republish)
   lock(): AutoLockController (ON_STOP + timeout | SCREEN_OFF | manual) ──► close DB, wipe keys ──► Locked
```

## Reminders & notifications

Reminders are rows in the encrypted DB. Because a background worker cannot read the encrypted DB, the app
publishes a **non-secret schedule file** (due dates + types, names only with opt-in) every time reminders or
notification settings change. `ReminderCheckWorker` reads that file and posts a privacy-preserving notification.

## Backup & restore

See BACKUP_FORMAT.md. Restore = validate & decrypt in memory → safety snapshot → single transaction (delete all,
import, verify) → commit. Any failure rolls back, leaving the live vault byte-identical.

## Build & release

* AGP 9.4 (built-in Kotlin), Gradle 9.6, compileSdk 37, minSdk 26, targetSdk 36.
* Release: R8 + resource shrinking, `debuggable=false`, `Log.*` stripped, signed from
  `%USERPROFILE%\.rombamukiyam\keystore.properties` (outside the repo).
* Static guard tests (`SourceSecurityRegressionTest`) fail the build if `fallbackToDestructiveMigration`,
  `rememberSaveable`, direct `android.util.Log`, or an un-removed `INTERNET` permission ever appears.

## Adding a feature safely

1. Entities: add columns/tables, bump `RombaDatabase.SCHEMA_VERSION`, write `MIGRATION_n_m`, export schema,
   add a `MigrationTest` case (n→m and 1→m).
2. Repository → use case → ViewModel → Composable. Return `Outcome`, never throw into the UI.
3. Anything secret: keep it in `remember`, ViewModel state cleared on `ON_STOP`, never in routes/Bundles/logs.
