# Personal Finance System -- Android

<div align="center">

**A full-featured, privacy-first personal finance manager for Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Material3](https://img.shields.io/badge/Material-3-2D2D2D?style=flat-square&logo=materialdesign)](https://m3.material.io/)
[![Room/SQLite](https://img.shields.io/badge/Database-SQLite-003B57?style=flat-square)](https://developer.android.com/training/data-storage/sqlite)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-green?style=flat-square)](https://developer.android.com/training/articles/keystore)
[![Drive Sync](https://img.shields.io/badge/Cloud%20Sync-Google%20Drive-4285F4?style=flat-square&logo=google)](https://developers.google.com/drive)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](../LICENSE)

</div>

---

## Overview

Personal Finance System is the Android companion to the [Python desktop application](../Python/). It is a native Kotlin/Jetpack Compose app that provides **complete financial management** on your mobile device -- with the same encryption, sync, and multi-currency features as the desktop version.

Your data stays on your device. When encryption is enabled, the database is protected with AES-256-GCM at rest and is only decrypted when you unlock it with your password or biometrics.

> **Min SDK:** 24 (Android 7.0) &nbsp;|&nbsp; **Target SDK:** 36 (Android 16) &nbsp;|&nbsp; **Language:** Kotlin

---

## Features at a Glance

| Module | Description |
|--------|-------------|
| **Dashboard** | Period-based financial trend charts (assets/liabilities, income vs. expense, balance), upcoming debt/credit due-date alerts, sortable period summary table |
| **Budget** | Select budget-eligible accounts, manage earmarked funds linked to expense categories, 12-month forward projections with editable forecast table and charts |
| **Accounts** | Multi-currency accounts (Cash / Invest / Fixed), asset allocation pie charts, currency distribution visualization, inter-account transfers, year-over-year comparison |
| **Balance** | Annual balance review with monthly income/expense/balance composite chart, category breakdown pie charts, monthly detail matrix with sparkline trends |
| **Transactions** | Full CRUD for income/expense records, multi-condition filter system (field + operator + value), sortable list with category icons and account badges |
| **Debts** | Track loans, credit cards, mortgages, and receivables with progress bars, due-date tracking, overdue warnings, composition pie charts |
| **Notes** | Personal financial note pad with full-screen editor |
| **SQL Console** | Direct SQL query interface for power users with quick-access snippet chips |
| **Settings** | Base currency, multi-currency management with live rate refresh, category management, AES-256 encryption toggle, data wipe |

---

## Security Architecture

### Database Encryption (AES-256-GCM)

| Property | Value |
|----------|-------|
| Algorithm | AES-256-GCM |
| Key derivation | PBKDF2-HMAC-SHA256 |
| PBKDF2 iterations | 600,000 |
| Salt | 16-byte cryptographically secure random |
| Nonce | 12-byte random per encryption |
| Verifier | Encrypted known-plaintext for password validation |
| Secure delete | 3-pass random overwrite before file deletion |

The encryption engine is implemented in `crypto/CryptoManager.kt` and mirrors the Python desktop implementation exactly, enabling cross-platform encrypted database sync.

### Biometric Authentication

- Uses Android's `BiometricPrompt` API (fingerprint / face unlock)
- Encrypted master password stored in Android Keystore (AES-GCM)
- Key alias: `pfm_biometric_key_v1`
- Auto-prompts biometric on app launch when available

---

## Cloud Sync (Google Drive)

| Feature | Details |
|---------|---------|
| Scope | `drive.file` (app only accesses its own files) |
| Files synced | `finance.db.enc` (ciphertext) + `finance.db.meta` (metadata) |
| Auth flow | Google Sign-In via `GoogleAuthUtil` OAuth2 |
| Security | **Only ciphertext is ever uploaded** -- Google never sees plaintext |
| Conflict detection | Remote `updated_at` vs. local last-sync comparison |
| Device tracking | Hostname recorded in sync metadata |

The sync module (`sync/GoogleDriveSync.kt`) communicates directly with the Google Drive REST API v3 via OkHttp.

---

## Multi-Currency Support

**11 built-in currencies:** CNY, HKD, USD, EUR, GBP, JPY, TWD, KRW, SGD, AUD, CAD

- Live exchange rates fetched from `open.er-api.com` (free, no API key required)
- Hardcoded fallback rate table for offline use
- Configurable base currency -- all values auto-convert
- Currency symbols: `¥` `HK$` `$` `€` `£` `¥` `NT$` `₩` `S$` `A$` `C$`

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.2 |
| **UI** | Jetpack Compose (Material3) |
| **Charts** | Custom Compose Canvas (no third-party chart library) |
| **Database** | SQLite via `SQLiteOpenHelper` |
| **Encryption** | AndroidKeyStore + AES-256-GCM + PBKDF2 |
| **Biometrics** | `androidx.biometric` (BiometricPrompt) |
| **Cloud Sync** | Google Drive REST API v3 via OkHttp |
| **Auth** | Google Play Services Auth (Sign-In + OAuth2) |
| **AI** | Firebase AI (Gemini API) -- declared, not actively used |
| **JSON** | Moshi (with KSP codegen) |
| **Build** | Gradle 9.3, AGP 9.1, KSP |

---

## Project Structure

```
Android/
├── app/
│   ├── build.gradle.kts              # Module build config (dependencies, signing, SDK)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt          # Entry point, tab navigation, Google Sign-In
│       │   │   ├── crypto/
│       │   │   │   ├── CryptoManager.kt     # AES-256-GCM encryption engine
│       │   │   │   └── BiometricHelper.kt   # Biometric + Android Keystore integration
│       │   │   ├── data/
│       │   │   │   ├── db/AppDatabase.kt    # SQLite data access layer (9 tables)
│       │   │   │   └── model/Models.kt      # Data classes for all entities
│       │   │   ├── sync/
│       │   │   │   ├── GoogleDriveSync.kt   # Google Drive upload/download
│       │   │   │   └── CurrencyConverter.kt # Live exchange rates + fallback
│       │   │   └── ui/
│       │   │       ├── MainViewModel.kt     # Central state + business logic
│       │   │       ├── components/
│       │   │       │   └── FinancialCharts.kt # Canvas-based charts (pie, line, bar, combo, sparkline)
│       │   │       ├── screens/
│       │   │       │   ├── DashboardScreen.kt
│       │   │       │   ├── BudgetScreen.kt
│       │   │       │   ├── AccountsScreen.kt
│       │   │       │   ├── BalanceScreen.kt
│       │   │       │   ├── TransactionsScreen.kt
│       │   │       │   ├── DebtsScreen.kt
│       │   │       │   ├── NotesScreen.kt
│       │   │       │   ├── SqlScreen.kt
│       │   │       │   ├── SettingsScreen.kt
│       │   │       │   └── LoginScreen.kt
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/
│       │       ├── drawable/         # App icon, launcher assets
│       │       ├── mipmap-anydpi-v26/ # Adaptive icons
│       │       ├── values/           # Strings, colors, themes
│       │       └── xml/              # Backup rules, data extraction rules
│       ├── test/                     # Unit tests (JUnit, Robolectric, Roborazzi)
│       └── androidTest/              # Instrumented tests
├── build.gradle.kts                  # Root build file (plugin declarations)
├── settings.gradle.kts               # Project settings, repository config
├── gradle/
│   ├── libs.versions.toml            # Version catalog
│   └── wrapper/                      # Gradle wrapper (9.3.1)
├── gradle.properties                 # JVM args, build flags
├── .env.example                      # API key template (GEMINI_API_KEY)
└── metadata.json                     # AI Studio app metadata
```

---

## Database Schema

The app uses **9 SQLite tables**:

| Table | Purpose |
|-------|---------|
| `accounts` | Financial accounts (name, currency, balance, type) |
| `categories` | Income/expense categories with icons and colors |
| `transactions` | Income/expense records with multi-currency support |
| `debts` | Loans, credit cards, mortgages, and receivables |
| `settings` | Key-value configuration store |
| `net_worth_history` | Historical net worth snapshots (date, assets, liabilities) |
| `earmarked_funds` | Budget earmarked funds with linked categories |
| `budget_forecast` | 12-month forecast overrides |
| `notes` | Free-text financial notes |

---

## Setup & Installation

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable recommended)
- JDK 11+
- Android SDK 36
- A Gemini API key (optional, for AI features)

### Steps

1. **Clone the repository:**

   ```bash
   git clone <repo-url>
   cd PersonalFinanceSystem/Android
   ```

2. **Open in Android Studio:**

   Select **Open** and choose the `Android/` directory. Let Android Studio resolve any incompatibilities.

3. **Configure API keys:**

   Create a `.env` file in the project root:

   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

4. **Remove the debug signing config (for release builds):**

   In `app/build.gradle.kts`, remove:
   ```kotlin
   signingConfig = signingConfigs.getByName("debugConfig")
   ```

5. **Run the app** on an emulator or physical device (min SDK 24).

---

## Tests

```bash
# Unit tests
./gradlew test

# Robolectric tests (JVM, no device needed)
./gradlew testDebugUnitTest

# Screenshot tests (Roborazzi)
./gradlew verifyRoborazziDebug

# Instrumented tests (device required)
./gradlew connectedAndroidTest
```

---

## Key Design Decisions

- **No third-party chart library** -- All financial charts (pie, line, bar, combo, sparkline) are drawn using Compose Canvas for full control and zero external dependencies.
- **Raw SQLite over Room** -- Despite Room being in dependencies, the app uses `SQLiteOpenHelper` directly for maximum control over schema migrations and encryption integration.
- **Offline-first exchange rates** -- 26 hardcoded fallback rate pairs ensure the app works without network connectivity.
- **Cross-platform encryption** -- AES-256-GCM with identical PBKDF2 parameters (600K iterations) ensures encrypted databases are compatible between Android and Python.

---

## License

This project is licensed under the [MIT License](../LICENSE).
