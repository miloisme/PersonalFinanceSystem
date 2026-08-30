<div align="center">

# 💰 Personal Finance System

**A cross-platform, privacy-first personal finance manager with end-to-end encryption** 🔐

[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![Python](https://img.shields.io/badge/Python-3.8%2B-3776AB?style=flat-square&logo=python)](Python/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin)](Android/)
[![PySide6](https://img.shields.io/badge/Gui-PySide6%206.5%2B-41CD52?style=flat-square)](Python/)
[![Compose](https://img.shields.io/badge/Ui-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose)](Android/)
[![SQLite](https://img.shields.io/badge/Database-SQLite-003B57?style=flat-square)](https://www.sqlite.org/)
[![AES-256](https://img.shields.io/badge/Encryption-AES--256--GCM-green?style=flat-square)](https://cryptography.io/)
[![Drive](https://img.shields.io/badge/Cloud%20Sync-Google%20Drive-4285F4?style=flat-square&logo=google)](https://developers.google.com/drive)

---

**Personal Finance System** is a local-first, privacy-oriented finance manager available as both a **Python/Qt desktop app** 🖥️ and a **native Android (Kotlin/Compose) app** 📱. Your data never leaves your device unencrypted -- all databases are protected with AES-256-GCM and can be synced to Google Drive as ciphertext only.

</div>

---

## 🤔 Why Personal Finance System?

| Feature | Details |
|---------|---------|
| 🔒 **True Ownership** | All data is stored in a local SQLite database on your device. No cloud account required. No data leaves your device unless you choose to sync. |
| 🛡️ **Bank-Grade Encryption** | AES-256-GCM with PBKDF2 key derivation (600,000 iterations). Even the cloud sync only transmits ciphertext. |
| 💱 **Multi-Currency** | 11 built-in currencies with live exchange rates from a free API. Automatic conversion across all views. |
| 🚀 **Full Feature Set** | Accounts, transactions, debts, budgets, earmarked funds, 12-month forecasts, net worth tracking, charts, notes, SQL console -- everything in one app. |
| 🌐 **Cross-Platform** | Desktop (Python/Qt) and Mobile (Android/Compose) with compatible encrypted databases. |
| 📂 **No Lock-In** | Open-source (MIT license), standard SQLite, exportable to CSV. |

---

## 📊 Platform Comparison

| Feature | 🐍 Python Desktop | 🤖 Android |
|---------|---------------|---------|
| **Language** | Python 3.8+ | Kotlin 2.2 |
| **UI Framework** | PySide6 (Qt 6) | Jetpack Compose (Material3) |
| **Charts** | Matplotlib (embedded) | Custom Compose Canvas |
| **Database** | SQLite (`sqlite3` module) | SQLite (`SQLiteOpenHelper`) |
| **Encryption** | `cryptography` (AES-GCM) | AndroidKeyStore + AES-GCM |
| **Biometric Auth** | -- | 🔐 Fingerprint / Face Unlock |
| **Cloud Sync** | Google Drive (OAuth browser flow) | Google Drive (Google Sign-In) |
| **Exchange Rates** | `open.er-api.com` | `open.er-api.com` |
| **CSV Export** | ✅ | -- |
| **SQL Console** | ✅ | ✅ |
| **Min OS** | Windows (recommended) | Android 7.0 (API 24) |

---

## 🎯 Feature Overview

### 💼 Financial Management

- 🏦 **Account Management** -- Create accounts in any currency (Cash, Investment, Fixed types), transfer between same-currency accounts, asset allocation visualization
- 📝 **Transaction Tracking** -- Income/expense records with category, account, multi-currency support, automatic balance updates
- 💳 **Debt & Credit Tracking** -- Loans, credit cards, mortgages, receivables with progress bars, interest rates, due-date alerts, overdue warnings
- 📊 **Budget Planning** -- Select budget-eligible accounts, earmarked funds with category linkage, 12-month forward projections with editable forecasts
- 📈 **Net Worth Tracking** -- Historical snapshots with manual override, year-over-year comparison

### 📉 Analytics & Visualization

- 📊 **Dashboard** -- Assets/liabilities trend, income vs. expense, balance charts, period summary table
- ⚖️ **Balance Review** -- Annual monthly matrix with category breakdown, sparkline trends, saving rate
- 🔮 **Budget Forecast** -- Line and bar charts for 12-month projections
- 🎨 **Custom Charts** -- Pie charts, line charts, bar charts, combo charts, sparklines (all hand-drawn)

### 🔐 Data & Security

- 🔒 **AES-256-GCM Encryption** -- Full database encryption with PBKDF2-derived keys
- 🗑️ **Secure Delete** -- 3-pass file shredding before deletion
- 👆 **Biometric Unlock** -- Android fingerprint/face authentication (Android only)
- ☁️ **Google Drive Sync** -- Encrypted database sync with conflict detection
- 📤 **CSV Export** -- Export any table to Excel-compatible CSV
- 💻 **SQL Console** -- Direct database access for power users

---

## 🚀 Getting Started

### 🐍 Python Desktop

```bash
cd Python
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

See [Python/README.md](Python/README.md) for full documentation.

### 🤖 Android

1. 📂 Open `Android/` in Android Studio
2. 🔑 Create `.env` with `GEMINI_API_KEY` (optional)
3. ▶️ Run on emulator or device (min SDK 24)

See [Android/README.md](Android/README.md) for full documentation.

---

## 📁 Project Structure

```
PersonalFinanceSystem/
├── README.md               # 📄 This file
├── LICENSE                  # ⚖️ MIT License
├── Python/                 # 🖥️ Desktop application
│   ├── main.py             # 🚀 Entry point
│   ├── main_window.py      # 🪟 Main window + sidebar navigation
│   ├── login_window.py     # 🔑 Login / encryption unlock
│   ├── database.py         # 🗄️ SQLite data access layer
│   ├── db_crypto.py        # 🔐 AES-256-GCM encryption engine
│   ├── currency_converter.py # 💱 Live exchange rates
│   ├── drive_sync.py       # ☁️ Google Drive sync
│   ├── *_tab.py            # 📑 Feature tabs (9 tabs)
│   ├── table_utils.py      # 📋 Table helpers
│   ├── font_utils.py       # 🔤 CJK font support
│   └── requirements.txt    # 📦 Python dependencies
├── Android/                # 📱 Mobile application
│   ├── app/src/main/java/com/example/
│   │   ├── MainActivity.kt # 🚀 Entry point + navigation
│   │   ├── crypto/         # 🔐 Encryption + biometric
│   │   ├── data/           # 🗄️ Database + models
│   │   ├── sync/           # ☁️ Drive sync + currency
│   │   └── ui/             # 🎨 Screens + charts + theme
│   ├── build.gradle.kts    # 🔧 Build configuration
│   └── gradle/             # 📦 Gradle wrapper + version catalog
└── .git/                   # 📂 Git repository
```

---

## 🛡️ Security at a Glance

| Property | Value |
|----------|-------|
| 🔒 Encryption | AES-256-GCM |
| 🔑 Key derivation | PBKDF2-HMAC-SHA256 |
| 🔢 Iterations | 600,000 |
| 🧂 Salt | 16-byte random |
| 🎲 Nonce | 12-byte random |
| ☁️ Cloud sync | Ciphertext only |
| 👆 Biometric | Android Keystore (Android app only) |

---

## 💱 Supported Currencies

🇨🇳 CNY (¥) | 🇭🇰 HKD (HK$) | 🇺🇸 USD ($) | 🇪🇺 EUR (€) | 🇬🇧 GBP (£) | 🇯🇵 JPY (¥) | 🇹🇼 TWD (NT$) | 🇰🇷 KRW (₩) | 🇸🇬 SGD (S$) | 🇦🇺 AUD (A$) | 🇨🇦 CAD (C$)

Live rates from [open.er-api.com](https://open.er-api.com/) with offline fallback table.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**Made with 🐍 Python, ⚡ Qt, 🟣 Kotlin, and 🎨 Jetpack Compose**

</div>
