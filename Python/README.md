# Personal Finance System -- Python Desktop

<div align="center">

**A local-first, privacy-oriented personal finance desktop application**

[![Python](https://img.shields.io/badge/Python-3.8%2B-3776AB?style=flat-square&logo=python)](https://www.python.org/)
[![PySide6](https://img.shields.io/badge/GUI-PySide6%206.5%2B-41CD52?style=flat-square)](https://doc.qt.io/qtforpython/)
[![SQLite](https://img.shields.io/badge/Database-SQLite-003B57?style=flat-square)](https://www.sqlite.org/)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-green?style=flat-square)](https://cryptography.io/)
[![Exchange Rates](https://img.shields.io/badge/Exchange%20Rates-open.er--api.com-ff9900?style=flat-square)](https://open.er-api.com/)
[![Charts](https://img.shields.io/badge/Charts-Matplotlib-11557c?style=flat-square)](https://matplotlib.org/)
[![Drive Sync](https://img.shields.io/badge/Cloud%20Sync-Google%20Drive-4285F4?style=flat-square&logo=google)](https://developers.google.com/drive)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](../LICENSE)

</div>

---

## Overview

Personal Finance System is a desktop finance application built with **Python + PySide6 (Qt)**. All data is stored in a local **SQLite** database and can optionally be protected with **AES-256-GCM** encryption. It offers multi-currency management, income/expense tracking, debt and receivable tracking, budgeting, net-worth charts, Google Drive sync, and a direct SQL console -- a complete toolkit for users who want full control over their personal finances.

> Your data stays on this device only. When encryption is enabled, the database is encrypted at rest using a password-derived key (PBKDF2) and is only decrypted at login time.

---

## Features at a Glance

| Module | Description |
|--------|-------------|
| **Dashboard** | Asset/liability trend charts, income vs. expense comparison, balance charts, and an editable period summary (monthly or yearly). Shows upcoming debt and credit-card payment reminders. |
| **Budget** | Choose which accounts to include, set aside "earmarked" funds linked to categories, and automatically compute how many months they cover. 12-month forward projections for assets/liabilities and income/expense. |
| **Accounts** | Manage multi-currency accounts (cash, bank, investment, etc.), support same-currency transfers between accounts, and visualize balances and allocation. |
| **Balance** | View per-account and overall net worth in a base currency, with automatic cross-currency conversion. Yearly review with monthly category matrix and sparkline trends. |
| **Transactions** | Add / edit / delete income and expense records, with category, account, currency, exchange rate, plus multi-condition filtering (chip-based UI) and sorting. |
| **Debts** | Manage loans, credit cards, mortgages, and "money owed to me" receivables, including repayment progress, interest rates, due dates, and composition pie charts. |
| **Notes** | A simple personal note list for capturing financial thoughts anytime. |
| **SQL Console** | Run arbitrary SQL directly against the local database -- `SELECT` shows results in a table; other statements show affected row counts. |
| **Settings** | Base-currency configuration, currency management and rate refresh, income/expense category management, database encryption toggle, CSV export, and data reset. |

### Additional Highlights

- **Multi-currency with live rates**: 11 built-in currencies (CNY, HKD, USD, EUR, GBP, JPY, TWD, KRW, SGD, AUD, CAD), fetched via `open.er-api.com` (free, no API key), with an offline fallback rate table.
- **Local encryption**: Enable AES-256-GCM encryption; the key is derived from your password via PBKDF2 (600,000 iterations). On exit the plaintext database is securely wiped (`secure_delete`).
- **Google Drive sync**: Upload/download your encrypted database to Google Drive. Only ciphertext ever leaves your device.
- **CSV export**: Export any table to UTF-8 (with BOM) CSV for easy opening in Excel.
- **Sparklines**: Inline mini-charts in table cells for at-a-glance trend visualization.
- **Column memory**: Persistent column widths and sort states via QSettings.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Python 3.8+ |
| **GUI Framework** | PySide6 (Qt 6) with Fusion style |
| **Charts** | Matplotlib (embedded via `FigureCanvasQTAgg`) |
| **Database** | SQLite (via the standard `sqlite3` module) |
| **Encryption** | `cryptography` package (AES-GCM + PBKDF2) |
| **Exchange Rates** | `open.er-api.com` (free REST API) |
| **Cloud Sync** | Google Drive API v3 via `google-api-python-client` |
| **Design** | Fusion style + custom stylesheet (dark sidebar, card-based layout) |

---

## Project Structure

```
Python/
├── main.py               # Entry point: initializes the Qt app, login flow, and main window
├── main_window.py        # Main window with sidebar navigation, sync toolbar, all tabs
├── login_window.py       # Login / unlock window (shows password input based on encryption state)
├── database.py           # SQLite data-access layer (accounts, categories, transactions, debts, settings, ...)
├── db_crypto.py          # AES-256-GCM encryption, PBKDF2 key derivation, secure delete
├── currency_converter.py # Multi-currency rates (open.er-api.com + fallback table), thread-safe singleton
├── accounts_tab.py       # Accounts tab (pie charts, allocation, transfers)
├── balance_tab.py        # Balance tab (yearly review, monthly matrix, sparklines)
├── transactions_tab.py   # Transactions tab (multi-condition filtering, chip UI)
├── debts_tab.py          # Debts / receivables tab (progress bars, composition pies)
├── charts_tab.py         # Dashboard (trend charts, period summary, editable net worth)
├── budget_tab.py         # Budget and forward-projection tab (earmarked funds, forecasts)
├── notes_tab.py          # Notes tab
├── sql_tab.py            # SQL console tab
├── settings_tab.py       # Settings tab (currencies, categories, encryption, export)
├── table_utils.py        # Table sorting / filtering / column-width persistence helpers
├── font_utils.py         # CJK font configuration helpers
├── drive_sync.py         # Google Drive upload/download (encrypted sync only)
├── requirements.txt      # Python dependencies
├── .gitignore            # Git ignore rules
├── finance.db            # Plaintext database (created on first run)
├── finance.db.enc        # Encrypted database (when encryption is enabled)
├── finance.db.meta       # Encryption metadata (JSON: salt, verifier, sync info)
├── client_secret.json    # Google OAuth client secrets (for Drive sync)
├── drive_token.json      # Google OAuth token (persisted after login)
└── drive_state.json      # Drive sync state (file IDs, last sync time)
```

---

## Database Schema

The application uses **9 SQLite tables**:

| Table | Key Columns | Purpose |
|-------|-------------|---------|
| `accounts` | `id`, `name`, `currency`, `balance`, `account_type` (cash/invest/fixed), `notes` | Financial accounts |
| `categories` | `id`, `name`, `type` (expense/income), `icon` (emoji), `color` (hex) | Income/expense categories |
| `transactions` | `id`, `account_id`, `category_id`, `amount`, `type`, `description`, `date`, `currency`, `exchange_rate` | Financial transactions |
| `debts` | `id`, `name`, `total_amount`, `paid_amount`, `interest_rate`, `currency`, `due_date`, `debtor` (me/other) | Debts and credits |
| `settings` | `key`, `value` | Key-value configuration store |
| `net_worth_history` | `date`, `assets`, `liabilities` | Historical net worth snapshots |
| `earmarked_funds` | `id`, `name`, `amount`, `categories` (comma-separated IDs) | Budget earmarked funds |
| `budget_forecast` | `month`, `assets`, `liabilities`, `income`, `expense` | 12-month forecast overrides |
| `notes` | `id`, `content`, `created_at`, `updated_at` | Free-text notes |

### Default Categories (seeded on first run)

| Income | Expense |
|--------|---------|
| Salary | Food & Dining |
| Bonus | Transportation |
| Investment Income | Shopping |
| Other Income | Housing |
| | Entertainment |
| | Healthcare |
| | Education |
| | Other Expense |

---

## Security Architecture

### Database Encryption (AES-256-GCM)

| Property | Value |
|----------|-------|
| Algorithm | AES-256-GCM |
| Key derivation | PBKDF2-HMAC-SHA256 |
| Key length | 32 bytes (256 bits) |
| PBKDF2 iterations | 600,000 |
| Salt | 16 random bytes (`os.urandom`) |
| Nonce | 12 random bytes per encryption |
| Verifier | Encrypted known-plaintext (`pfm-verifier-ok`) for password validation |
| Secure delete | 3-pass random overwrite with retry logic for Windows file locks |

### How Encryption Works

1. **Enable**: Go to Settings > Database Encryption, enter and confirm a password, click "Apply Encryption Settings"
2. **Key derivation**: Your password + random salt -> PBKDF2 (600K iterations) -> 256-bit key
3. **Encryption**: The plaintext `finance.db` is encrypted into `finance.db.enc` using AES-256-GCM with a random 12-byte nonce
4. **Secure wipe**: The plaintext file is overwritten with random data and deleted
5. **On exit**: The app re-encrypts the database and securely wipes the plaintext
6. **On login**: Enter your password to decrypt and open the database

> **Warning:** A lost password makes the encrypted database unrecoverable.

---

## Google Drive Sync

| Feature | Details |
|---------|---------|
| Scope | `drive.file` (app only accesses files it creates) |
| Files synced | `finance.db.enc` (ciphertext) + `finance.db.meta` (metadata JSON) |
| Auth flow | OAuth 2.0 via local browser (`InstalledAppFlow`) |
| Client config | `client_secret.json` (Desktop OAuth client) |
| Token storage | `drive_token.json` (local) |
| State tracking | `drive_state.json` (file IDs, last sync timestamp) |
| Upload | Exports clean snapshot via `VACUUM INTO`, encrypts, uploads both files, records MD5 and device name |
| Download | Downloads `.enc` and `.meta`, verifies password matches via verifier, decrypts over local DB |
| Conflict detection | Compares remote `updated_at` with local last sync time |
| Security | **Only ciphertext is ever sent to Google** |

### Setup

1. Create a Google Cloud project and enable the Drive API
2. Create OAuth 2.0 credentials (Desktop App) and download as `client_secret.json`
3. Place `client_secret.json` in the `Python/` directory
4. Click "Login" in the sync toolbar to authenticate via browser

---

## Multi-Currency Support

**11 built-in currencies:** CNY, HKD, USD, EUR, GBP, JPY, TWD, KRW, SGD, AUD, CAD

- Live exchange rates fetched from `open.er-api.com` (free, no API key required)
- Thread-safe singleton with in-memory cache
- Hardcoded fallback rates for offline use (26 pairs)
- Configurable base currency (default: HKD) -- all views auto-convert
- Add/remove currencies in Settings; new codes are validated against the live API

### Currency Symbols

| Currency | Symbol | Currency | Symbol |
|----------|--------|----------|--------|
| CNY | `¥` | TWD | `NT$` |
| HKD | `HK$` | KRW | `₩` |
| USD | `$` | SGD | `S$` |
| EUR | `€` | AUD | `A$` |
| GBP | `£` | CAD | `C$` |
| JPY | `¥` | | |

---

## Setup & Installation

### Prerequisites

- **Operating System**: Windows (recommended -- UI fonts use Microsoft JhengHei / Microsoft YaHei)
- **Python**: 3.8 or newer
- **pip**: Latest version

### Steps

1. **Clone the repository:**

   ```bash
   git clone <repo-url>
   cd PersonalFinanceSystem/Python
   ```

2. **Create and activate a virtual environment (recommended):**

   ```bash
   python -m venv venv
   venv\Scripts\activate    # Windows
   # source venv/bin/activate  # macOS/Linux
   ```

3. **Install dependencies:**

   ```bash
   pip install -r requirements.txt
   ```

4. **Launch the application:**

   ```bash
   python main.py
   ```

   On first launch, `finance.db` (unencrypted) is created and the login screen appears -- click **Open** to start using it. If you later enable encryption in **Settings**, a password will be required on subsequent logins.

---

## Usage Tips

- **Back up regularly**: Use **Export to CSV** in Settings to create periodic backups.
- **Enable encryption** for better security, but always remember your password.
- **Sync to Google Drive**: Keep your encrypted database in the cloud for cross-device access.
- **Refresh rates**: Click "Refresh Rates" in Settings when base currency or rates seem stale.
- **SQL Console**: Use the SQL tab for advanced queries -- all SELECT, PRAGMA, and EXPLAIN queries show results; other statements trigger a full tab refresh.
- **Earmarked funds**: Link them to expense categories to auto-calculate months of coverage based on 12-month average spending.

---

## Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| `PySide6` | >= 6.5.0 | Qt GUI framework |
| `matplotlib` | >= 3.7.0 | Charts and visualization |
| `cryptography` | >= 41.0.0 | AES-256 encryption (PBKDF2 + AES-GCM) |
| `google-api-python-client` | >= 2.100.0 | Google Drive API (optional, for cloud sync) |
| `google-auth-httplib2` | >= 0.1.0 | Google auth (optional) |
| `google-auth-oauthlib` | >= 1.0.0 | Google OAuth (optional) |

---

## License

This project is licensed under the [MIT License](../LICENSE).

---

<p align="center">Made with Python & Qt</p>
