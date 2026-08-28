# 💰 Personal Finance System

<div align="center">

**A local-first, privacy-oriented personal finance desktop application**

[![Python](https://img.shields.io/badge/Python-3.8%2B-blue?style=flat-square)](https://www.python.org/)
[![PySide6](https://img.shields.io/badge/GUI-PySide6%206.5%2B-2c3e50?style=flat-square)](https://doc.qt.io/qtforpython/)
[![SQLite](https://img.shields.io/badge/Database-SQLite-003B57?style=flat-square)](https://www.sqlite.org/)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-green?style=flat-square)](https://cryptography.io/)
[![Exchange Rates](https://img.shields.io/badge/Exchange%20Rates-yfinance-ff9900?style=flat-square)](https://github.com/ranaroussi/yfinance)
[![Charts](https://img.shields.io/badge/Charts-Matplotlib-11557c?style=flat-square)](https://matplotlib.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

</div>

---

## 📌 Introduction

**Personal Finance System** is a desktop finance application built with **Python + PySide6 (Qt)**.
All data is stored in a local **SQLite** database and can optionally be protected with
**AES-256-GCM** encryption. It offers multi-currency management, income/expense tracking,
debt and receivable tracking, budgeting, net-worth charts, and a direct SQL console — a
complete toolkit for users who want full control over their personal finances.

> Your data stays on this device only. When encryption is enabled, the database is encrypted
> at rest using a password-derived key (PBKDF2) and is only decrypted at login time.

## ✨ Features

| Module             | Description                                                                                                                                                                                                       |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 📊 **Dashboard**    | Asset/liability trend charts, income vs. expense comparison, balance charts, and an editable period summary (monthly or yearly). Shows upcoming debt and credit-card payment reminders.                           |
| 💼 **Budget**       | Choose which accounts to include, set aside "earmarked" funds linked to categories, and automatically compute how many months they cover. 12-month forward projections for assets/liabilities and income/expense. |
| 🏦 **Accounts**     | Manage multi-currency accounts (cash, bank, investment, etc.), support same-currency transfers between accounts, and visualize balances and allocation.                                                           |
| ⚖️ **Balance**      | View per-account and overall net worth in a base currency, with automatic cross-currency conversion.                                                                                                              |
| 🧾 **Transactions** | Add / edit / delete income and expense records, with category, account, currency, exchange rate, plus date-range filtering and sorting.                                                                           |
| 💳 **Debts**        | Manage loans, credit cards, mortgages, and "money owed to me" receivables, including repayment progress, interest rates, and due dates.                                                                           |
| 📝 **Notes**        | A simple personal note list for capturing financial thoughts anytime.                                                                                                                                             |
| 💻 **SQL Console**  | Run arbitrary SQL directly against the local database — `SELECT` shows results, other statements show affected row counts.                                                                                        |
| ⚙️ **Settings**     | Base-currency configuration, currency management and rate refresh, income/expense category management, database encryption toggle, CSV export, and data reset.                                                    |

### Additional Highlights

- **Multi-currency with live rates**: 11 built-in currencies (CNY, HKD, USD, EUR, GBP, JPY, TWD, KRW, SGD, AUD, CAD), fetched via `yfinance`, with an offline fallback rate table.
- **Local encryption**: Enable AES-256-GCM encryption; the key is derived from your password via PBKDF2 (600,000 iterations). On exit the plaintext database is securely wiped (`secure_delete`).
- **CSV export**: Export any table to UTF-8 (with BOM) CSV for easy opening in Excel.

## 🖥️ Requirements

- **Operating System**: Windows (UI fonts use Microsoft JhengHei / Microsoft YaHei; running on Windows is recommended)
- **Python**: 3.8 or newer
- **Dependencies**:

  ```
  PySide6>=6.5.0
  matplotlib>=3.7.0
  yfinance>=0.2.36
  ```

## 🚀 Installation & Usage

1. Clone (or download) the repository to your machine:

   ```bash
   git clone <repo-url>
   cd PersonalFinanceSystem
   ```

2. Create and activate a virtual environment (recommended):

   ```bash
   python -m venv venv
   venv\Scripts\activate
   ```

3. Install the dependencies:

   ```bash
   pip install -r requirements.txt
   ```

4. Launch the application:

   ```bash
   python main.py
   ```

   On first launch, `finance.db` (unencrypted) is created and the login screen appears — click **Open** to start using it. If you later enable encryption in **Settings**, a password will be required on subsequent logins.

## 🗂️ Project Structure

```
PersonalFinanceSystem/
├── main.py               # Entry point: initializes the Qt app, login flow, and main window
├── main_window.py        # Main window with side navigation, integrating all feature tabs
├── login_window.py       # Login / unlock window (shows password input based on encryption state)
├── database.py           # SQLite data-access layer (accounts, categories, transactions, debts, settings, …)
├── db_crypto.py          # AES-256-GCM encryption, PBKDF2 key derivation, secure delete
├── currency_converter.py # Multi-currency rates (yfinance + fallback table), singleton cache
├── accounts_tab.py       # Accounts tab
├── balance_tab.py        # Balance tab
├── transactions_tab.py   # Transactions tab
├── debts_tab.py          # Debts / receivables tab
├── charts_tab.py         # Dashboard (charts and period summary)
├── budget_tab.py         # Budget and forward-projection tab
├── notes_tab.py          # Notes tab
├── sql_tab.py            # SQL console tab
├── settings_tab.py       # Settings tab (currencies, categories, encryption, export)
├── table_utils.py        # Table sorting / filtering helpers
├── font_utils.py         # CJK font configuration helpers
├── requirements.txt      # Python dependencies
├── finance.db           # Plaintext database (when encryption is disabled)
├── finance.db.enc       # Encrypted database (when encryption is enabled)
└── finance.db.meta      # Encryption metadata (salt, verifier)
```

## 🔐 Database Encryption

In **Settings → Database Encryption (AES-256)**, check "Encrypt this database with a password",
enter and confirm a password, then click **Apply Encryption Settings** to enable it.

- The key is derived via `PBKDF2HMAC(SHA256, 600000 iterations)` from your password and a random salt.
- The database is encrypted with `AESGCM` using a 12-byte nonce, and a verifier confirms password correctness.
- Once enabled, `finance.db` is encrypted into `finance.db.enc`, and the plaintext file is overwritten with random data and securely deleted.
- On application exit (`before main.py terminates`) the plaintext is automatically re-encrypted and wiped.

> ⚠️ Remember your password. A lost password makes the encrypted database unrecoverable.

## 💱 Exchange Rates

- Rates are sourced from `yfinance` live currency pairs (e.g. `USDHKD=X`).
- If there is no network access or a lookup fails, the app falls back to the built-in fallback rate table (see `fallback_rates` in `currency_converter.py`).
- Click **Refresh Rates** in **Settings** to force a refresh, or add/remove currencies (new codes are validated via `yfinance`).

## 🛠️ Tech Stack

- **Language**: Python 3
- **GUI Framework**: PySide6 (Qt 6)
- **Charts**: Matplotlib (embedded via Qt `FigureCanvasQTAgg`)
- **Database**: SQLite (via the standard `sqlite3` module)
- **Encryption**: `cryptography` package's AES-GCM and PBKDF2
- **Exchange Rates**: `yfinance`
- **Design**: Fusion style + custom stylesheet (dark sidebar, card-based layout)

## ✅ Usage Tips

- Periodically back up your data via **Export to CSV** in **Settings**.
- Enable encryption for better security, but always remember your password.
- When recording across currencies, make sure the relevant currency is added and rates are refreshed for accurate base-currency conversion.

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<p align="center">Made with Python & Qt 💙</p>
