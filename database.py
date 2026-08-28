import sqlite3
from datetime import datetime, date
from typing import List, Optional, Dict, Any
from dataclasses import dataclass
from enum import Enum

from currency_converter import CurrencyConverter


class TransactionType(Enum):
    INCOME = "income"
    EXPENSE = "expense"


class DebtType(Enum):
    LOAN = "loan"
    CREDIT_CARD = "credit_card"
    MORTGAGE = "mortgage"
    OTHER = "other"


@dataclass
class Account:
    id: Optional[int] = None
    name: str = ""
    currency: str = "CNY"
    balance: float = 0.0
    account_type: str = "cash"
    notes: str = ""
    created_at: str = ""
    updated_at: str = ""


@dataclass
class Category:
    id: Optional[int] = None
    name: str = ""
    type: str = "expense"
    icon: str = ""
    color: str = "#4CAF50"


@dataclass
class Transaction:
    id: Optional[int] = None
    account_id: int = 0
    category_id: int = 0
    amount: float = 0.0
    type: str = "expense"
    description: str = ""
    date: str = ""
    currency: str = ""
    exchange_rate: float = 0.0
    created_at: str = ""


@dataclass
class Debt:
    id: Optional[int] = None
    name: str = ""
    total_amount: float = 0.0
    paid_amount: float = 0.0
    interest_rate: float = 0.0
    currency: str = "CNY"
    start_date: str = ""
    due_date: str = ""
    completed: bool = False
    debtor: str = "me"
    note: str = ""
    created_at: str = ""


class Database:
    def __init__(self, db_path: str = "finance.db"):
        self.db_path = db_path
        self.crypto_key = None
        self.converter = CurrencyConverter()
        self.init_db()
        self._settings_cache = {}

    def get_setting(self, key: str, default=None) -> str:
        if key in self._settings_cache:
            return self._settings_cache[key]
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM settings WHERE key=?", (key,))
            row = cursor.fetchone()
            value = row[0] if row else default
            self._settings_cache[key] = value
            return value

    def set_setting(self, key: str, value: str):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)",
                (key, value)
            )
            conn.commit()
            self._settings_cache[key] = value

    def get_base_currency(self) -> str:
        return self.get_setting("base_currency", "HKD")

    def set_base_currency(self, currency: str):
        self.set_setting("base_currency", currency)

    def get_currencies(self) -> List[str]:
        raw = self.get_setting("currencies", "")
        if raw:
            curs = [c.strip().upper() for c in raw.split(",") if c.strip()]
            if curs:
                return curs
        defaults = self.converter.get_supported_currencies()
        self.set_setting("currencies", ",".join(defaults))
        return list(defaults)

    def add_currency(self, code: str):
        cur = (code or "").strip().upper()
        if not cur:
            return
        curs = self.get_currencies()
        if cur not in curs:
            curs.append(cur)
            self.set_setting("currencies", ",".join(curs))

    def remove_currency(self, code: str):
        cur = (code or "").strip().upper()
        curs = [c for c in self.get_currencies() if c != cur]
        self.set_setting("currencies", ",".join(curs))

    def clear_all(self):
        """Remove all user data while keeping app settings
        (base currency and the custom currency list)."""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            for tbl in ("transactions", "accounts", "debts", "net_worth_history",
                        "earmarked_funds", "budget_forecast", "notes"):
                cursor.execute(f"DELETE FROM {tbl}")
            cursor.execute("DELETE FROM categories")
            conn.commit()
            self._init_default_categories(conn)

    # ----- Budget: available-asset account selection -----
    def get_budget_account_ids(self):
        raw = self.get_setting("budget_account_ids")
        if raw is None:
            return None  # uninitialized
        try:
            return set(int(x) for x in raw.split(",") if x.strip())
        except Exception:
            return set()

    def set_budget_account_ids(self, ids):
        self.set_setting("budget_account_ids", ",".join(str(i) for i in ids))

    # ----- Budget: earmarked funds (專款) -----
    def get_funds(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute("SELECT * FROM earmarked_funds ORDER BY id")
            out = []
            for r in cur.fetchall():
                cats = [int(x) for x in (r["categories"] or "").split(",") if x.strip()]
                out.append({
                    "id": r["id"], "name": r["name"],
                    "amount": r["amount"], "categories": cats,
                })
            return out

    def add_fund(self, name, amount, category_ids):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute(
                "INSERT INTO earmarked_funds (name, amount, categories) VALUES (?, ?, ?)",
                (name, amount, ",".join(str(c) for c in category_ids))
            )
            conn.commit()
            return cur.lastrowid

    def update_fund(self, fid, name, amount, category_ids):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute(
                "UPDATE earmarked_funds SET name=?, amount=?, categories=? WHERE id=?",
                (name, amount, ",".join(str(c) for c in category_ids), fid)
            )
            conn.commit()

    def delete_fund(self, fid):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute("DELETE FROM earmarked_funds WHERE id=?", (fid,))
            conn.commit()

    # ----- Budget: manual forecast overrides -----
    def get_forecast(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute("SELECT * FROM budget_forecast")
            out = {}
            for r in cur.fetchall():
                out[r["month"]] = {
                    "assets": r["assets"], "liabilities": r["liabilities"],
                    "income": r["income"], "expense": r["expense"],
                }
            return out

    def set_forecast_cell(self, month, field, value):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute(
                "SELECT assets, liabilities, income, expense FROM budget_forecast WHERE month=?",
                (month,)
            )
            row = cur.fetchone()
            if row is None:
                data = {"assets": None, "liabilities": None, "income": None, "expense": None}
            else:
                data = {"assets": row[0], "liabilities": row[1],
                        "income": row[2], "expense": row[3]}
            data[field] = value
            cur.execute(
                "INSERT OR REPLACE INTO budget_forecast "
                "(month, assets, liabilities, income, expense) VALUES (?, ?, ?, ?, ?)",
                (month, data["assets"], data["liabilities"],
                 data["income"], data["expense"])
            )
            conn.commit()

    def delete_forecast(self, month):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute("DELETE FROM budget_forecast WHERE month=?", (month,))
            conn.commit()

    # ----- Notes (one-by-one) -----
    def get_notes(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute("SELECT * FROM notes ORDER BY updated_at DESC")
            return [dict(row) for row in cur.fetchall()]

    def add_note(self, content: str) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute("INSERT INTO notes (content) VALUES (?)", (content,))
            conn.commit()
            return cur.lastrowid

    def update_note(self, note_id: int, content: str):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute(
                "UPDATE notes SET content=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (content, note_id)
            )
            conn.commit()

    def delete_note(self, note_id: int):
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute("DELETE FROM notes WHERE id=?", (note_id,))
            conn.commit()

    def upsert_net_worth(self, date_str: str, assets: float, liabilities: float):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "INSERT OR REPLACE INTO net_worth_history (date, assets, liabilities) VALUES (?, ?, ?)",
                (date_str, assets, liabilities)
            )
            conn.commit()

    def upsert_net_worth_if_missing(self, date_str: str, assets: float, liabilities: float):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT 1 FROM net_worth_history WHERE date=?", (date_str,))
            if cursor.fetchone() is None:
                cursor.execute(
                    "INSERT INTO net_worth_history (date, assets, liabilities) VALUES (?, ?, ?)",
                    (date_str, assets, liabilities)
                )
                conn.commit()

    def get_net_worth_history(self) -> List[Dict]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM net_worth_history ORDER BY date")
            return [dict(row) for row in cursor.fetchall()]

    def get_latest_net_worth_before(self, date_str: str) -> Optional[Dict]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM net_worth_history WHERE date < ? ORDER BY date DESC LIMIT 1",
                (date_str,)
            )
            row = cursor.fetchone()
            return dict(row) if row else None

    def init_db(self):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()

            # Remove legacy / unused investment tables (investments, platforms).
            for _t in ("investments", "investment_platforms", "investment_monthly"):
                try:
                    cursor.execute(f"DROP TABLE IF EXISTS {_t}")
                except sqlite3.OperationalError:
                    pass

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    currency TEXT DEFAULT 'CNY',
                    balance REAL DEFAULT 0,
                    account_type TEXT DEFAULT 'cash',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            try:
                cursor.execute("ALTER TABLE accounts ADD COLUMN notes TEXT DEFAULT ''")
            except sqlite3.OperationalError:
                pass

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    type TEXT DEFAULT 'expense',
                    icon TEXT DEFAULT '',
                    color TEXT DEFAULT '#4CAF50'
                )
            """)

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id INTEGER,
                    category_id INTEGER,
                    amount REAL DEFAULT 0,
                    type TEXT DEFAULT 'expense',
                    description TEXT DEFAULT '',
                    date DATE DEFAULT CURRENT_DATE,
                    currency TEXT DEFAULT '',
                    exchange_rate REAL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (account_id) REFERENCES accounts(id),
                    FOREIGN KEY (category_id) REFERENCES categories(id)
                )
            """)
            try:
                cursor.execute("ALTER TABLE transactions ADD COLUMN currency TEXT DEFAULT ''")
            except sqlite3.OperationalError:
                pass
            try:
                cursor.execute("ALTER TABLE transactions ADD COLUMN exchange_rate REAL DEFAULT 0")
            except sqlite3.OperationalError:
                pass

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS debts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    type TEXT DEFAULT 'loan',
                    total_amount REAL DEFAULT 0,
                    paid_amount REAL DEFAULT 0,
                    interest_rate REAL DEFAULT 0,
                    currency TEXT DEFAULT 'CNY',
                    start_date DATE DEFAULT CURRENT_DATE,
                    due_date DATE DEFAULT CURRENT_DATE,
                    completed INTEGER DEFAULT 0,
                    debtor TEXT DEFAULT 'me',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            try:
                cursor.execute("ALTER TABLE debts ADD COLUMN completed INTEGER DEFAULT 0")
            except sqlite3.OperationalError:
                pass
            try:
                cursor.execute("ALTER TABLE debts ADD COLUMN debtor TEXT DEFAULT 'me'")
            except sqlite3.OperationalError:
                pass
            try:
                cursor.execute("ALTER TABLE debts ADD COLUMN note TEXT DEFAULT ''")
            except sqlite3.OperationalError:
                pass

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS net_worth_history (
                    date TEXT PRIMARY KEY,
                    assets REAL DEFAULT 0,
                    liabilities REAL DEFAULT 0
                )
            """)

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS earmarked_funds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    amount REAL DEFAULT 0,
                    categories TEXT DEFAULT ''
                )
            """)

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS budget_forecast (
                    month TEXT PRIMARY KEY,
                    assets REAL,
                    liabilities REAL,
                    income REAL,
                    expense REAL
                )
            """)

            cursor.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content TEXT DEFAULT '',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)

            conn.commit()
            self._init_default_categories(conn)
            self._rename_legacy_categories(conn)

    def _rename_legacy_categories(self, conn):
        rename_map = {
            "工资": "Salary",
            "奖金": "Bonus",
            "投资收益": "Investment Income",
            "其他收入": "Other Income",
            "餐饮": "Food & Dining",
            "交通": "Transportation",
            "购物": "Shopping",
            "住房": "Housing",
            "娱乐": "Entertainment",
            "医疗": "Healthcare",
            "教育": "Education",
            "其他支出": "Other Expense",
        }
        cursor = conn.cursor()
        for old_name, new_name in rename_map.items():
            cursor.execute(
                "UPDATE categories SET name = ? WHERE name = ?",
                (new_name, old_name)
            )
        conn.commit()

    def _init_default_categories(self, conn):
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM categories")
        count = cursor.fetchone()[0]
        if count > 0:
            cursor.execute(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('categories_seeded', '1')"
            )
            conn.commit()
            return
        cursor.execute("SELECT value FROM settings WHERE key='categories_seeded'")
        row = cursor.fetchone()
        if row and row[0] == "1":
            return
        default_categories = [
            ("Salary", "income", "💰", "#4CAF50"),
            ("Bonus", "income", "🎁", "#8BC34A"),
            ("Investment Income", "income", "📈", "#00BCD4"),
            ("Other Income", "income", "💵", "#009688"),
            ("Food & Dining", "expense", "🍜", "#FF5722"),
            ("Transportation", "expense", "🚗", "#FF9800"),
            ("Shopping", "expense", "🛒", "#E91E63"),
            ("Housing", "expense", "🏠", "#9C27B0"),
            ("Entertainment", "expense", "🎮", "#673AB7"),
            ("Healthcare", "expense", "🏥", "#F44336"),
            ("Education", "expense", "📚", "#3F51B5"),
            ("Other Expense", "expense", "💸", "#795548"),
        ]
        cursor.executemany(
            "INSERT INTO categories (name, type, icon, color) VALUES (?, ?, ?, ?)",
            default_categories
        )
        cursor.execute(
            "INSERT OR REPLACE INTO settings (key, value) VALUES ('categories_seeded', '1')"
        )
        conn.commit()

    def get_accounts(self) -> List[Account]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM accounts ORDER BY created_at DESC")
            return [Account(**dict(row)) for row in cursor.fetchall()]

    def add_account(self, account: Account) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "INSERT INTO accounts (name, currency, balance, account_type, notes) VALUES (?, ?, ?, ?, ?)",
                (account.name, account.currency, account.balance, account.account_type, account.notes)
            )
            conn.commit()
            return cursor.lastrowid

    def update_account(self, account: Account):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE accounts SET name=?, currency=?, balance=?, account_type=?, notes=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (account.name, account.currency, account.balance, account.account_type, account.notes, account.id)
            )
            conn.commit()

    def transfer_between_accounts(self, from_id: int, to_id: int, amount: float):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT id, balance, currency FROM accounts WHERE id IN (?, ?)",
                (from_id, to_id)
            )
            rows = {r[0]: r for r in cursor.fetchall()}
            if from_id not in rows or to_id not in rows:
                raise ValueError("Account not found")
            if rows[from_id][2] != rows[to_id][2]:
                raise ValueError("Currency mismatch")
            if rows[from_id][1] < amount:
                raise ValueError("Insufficient funds")
            cursor.execute(
                "UPDATE accounts SET balance = balance - ?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (amount, from_id)
            )
            cursor.execute(
                "UPDATE accounts SET balance = balance + ?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (amount, to_id)
            )
            conn.commit()

    def delete_account(self, account_id: int):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM accounts WHERE id=?", (account_id,))
            conn.commit()

    def get_categories(self, type_filter: str = None) -> List[Category]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            if type_filter:
                cursor.execute("SELECT * FROM categories WHERE type=? ORDER BY id", (type_filter,))
            else:
                cursor.execute("SELECT * FROM categories ORDER BY type, id")
            return [Category(**dict(row)) for row in cursor.fetchall()]

    def get_category_by_id(self, category_id: int) -> Optional[Category]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM categories WHERE id=?", (category_id,))
            row = cursor.fetchone()
            return Category(**dict(row)) if row else None

    def add_category(self, category: Category) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "INSERT INTO categories (name, type, icon, color) VALUES (?, ?, ?, ?)",
                (category.name, category.type, category.icon, category.color)
            )
            conn.commit()
            return cursor.lastrowid

    def delete_category(self, category_id: int):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM categories WHERE id=?", (category_id,))
            conn.commit()

    def update_category(self, category: Category):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE categories SET name=?, type=?, icon=?, color=? WHERE id=?",
                (category.name, category.type, category.icon, category.color, category.id)
            )
            conn.commit()

    def get_transaction_years(self) -> List[int]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT DISTINCT substr(date, 1, 4) FROM transactions WHERE date IS NOT NULL AND date != ''")
            years = [int(r[0]) for r in cursor.fetchall() if r[0] and r[0].isdigit()]
            if not years:
                years = [datetime.now().year]
            return sorted(years)

    def get_transactions(self, account_id: int = None, start_date: str = None, end_date: str = None) -> List[Dict]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            query = """
                SELECT t.*, c.name as category_name, c.icon as category_icon, c.color as category_color,
                       a.name as account_name, a.currency as account_currency
                FROM transactions t
                LEFT JOIN categories c ON t.category_id = c.id
                LEFT JOIN accounts a ON t.account_id = a.id
                WHERE 1=1
            """
            params = []
            if account_id:
                query += " AND t.account_id=?"
                params.append(account_id)
            if start_date:
                query += " AND t.date>=?"
                params.append(start_date)
            if end_date:
                query += " AND t.date<=?"
                params.append(end_date)
            query += " ORDER BY t.date DESC, t.created_at DESC"
            cursor.execute(query, params)
            base = self.get_base_currency()
            rows = []
            for row in cursor.fetchall():
                d = dict(row)
                acc_cur = d.get("account_currency") or base
                cur = d.get("currency") or acc_cur
                d["currency"] = cur
                rate = d.get("exchange_rate")
                if not rate:
                    rate = self.converter.get_rate(cur, base)
                d["exchange_rate"] = rate
                d["base_amount"] = float(d.get("amount") or 0) * float(rate or 1)
                rows.append(d)
            return rows

    def add_transaction(self, transaction: Transaction) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            acc_cur = self.get_base_currency()
            if transaction.account_id:
                cursor.execute("SELECT currency FROM accounts WHERE id=?", (transaction.account_id,))
                r = cursor.fetchone()
                if r:
                    acc_cur = r[0]
            cur = transaction.currency or acc_cur
            rate = transaction.exchange_rate or self.converter.get_rate(cur, self.get_base_currency())
            delta = self.converter.convert(transaction.amount, cur, acc_cur)
            cursor.execute(
                """INSERT INTO transactions (account_id, category_id, amount, type, description, date, currency, exchange_rate)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (transaction.account_id, transaction.category_id, transaction.amount,
                 transaction.type, transaction.description, transaction.date, cur, rate)
            )
            account_cursor = conn.cursor()
            if transaction.account_id:
                if transaction.type == "income":
                    account_cursor.execute(
                        "UPDATE accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        (delta, transaction.account_id)
                    )
                else:
                    account_cursor.execute(
                        "UPDATE accounts SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        (delta, transaction.account_id)
                    )
            conn.commit()
            return cursor.lastrowid

    def update_transaction(self, transaction: Transaction):
        # Editing never adjusts account balances; only a fresh add (with an
        # account selected) does. The balance was already moved at add time.
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cur = transaction.currency or self.get_base_currency()
            rate = transaction.exchange_rate or self.converter.get_rate(cur, self.get_base_currency())
            cursor.execute(
                """UPDATE transactions SET account_id=?, category_id=?, amount=?, type=?, description=?, date=?, currency=?, exchange_rate=?
                   WHERE id=?""",
                (transaction.account_id, transaction.category_id, transaction.amount,
                 transaction.type, transaction.description, transaction.date, cur, rate, transaction.id)
            )
            conn.commit()

    def delete_transaction(self, transaction_id: int):
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM transactions WHERE id=?", (transaction_id,))
            row = cursor.fetchone()
            if row:
                transaction = dict(row)
                acc_cur = self.get_base_currency()
                if transaction.get("account_id"):
                    cursor.execute("SELECT currency FROM accounts WHERE id=?", (transaction["account_id"],))
                    r = cursor.fetchone()
                    if r:
                        acc_cur = r[0]
                cur = transaction.get("currency") or acc_cur
                delta = self.converter.convert(transaction["amount"], cur, acc_cur)
                if transaction["type"] == "income":
                    cursor.execute(
                        "UPDATE accounts SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        (delta, transaction["account_id"])
                    )
                else:
                    cursor.execute(
                        "UPDATE accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        (delta, transaction["account_id"])
                    )
                cursor.execute("DELETE FROM transactions WHERE id=?", (transaction_id,))
                conn.commit()

    def get_debts(self) -> List[Debt]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM debts ORDER BY completed ASC, due_date ASC")
            debts = []
            for row in cursor.fetchall():
                data = dict(row)
                data.pop("type", None)
                debts.append(Debt(**data))
            return debts

    def add_debt(self, debt: Debt) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                """INSERT INTO debts (name, total_amount, paid_amount, interest_rate, currency, start_date, due_date, completed, debtor, note)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (debt.name, debt.total_amount, debt.paid_amount,
                 debt.interest_rate, debt.currency, debt.start_date, debt.due_date,
                 1 if debt.completed else 0, debt.debtor or "me", debt.note or "")
            )
            conn.commit()
            return cursor.lastrowid

    def update_debt(self, debt: Debt):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute(
                """UPDATE debts SET name=?, total_amount=?, paid_amount=?,
                   interest_rate=?, currency=?, start_date=?, due_date=?, completed=?, debtor=?, note=? WHERE id=?""",
                (debt.name, debt.total_amount, debt.paid_amount,
                 debt.interest_rate, debt.currency, debt.start_date, debt.due_date,
                 1 if debt.completed else 0, debt.debtor or "me", debt.note or "", debt.id)
            )
            conn.commit()

    def delete_debt(self, debt_id: int):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM debts WHERE id=?", (debt_id,))
            conn.commit()

    def get_monthly_summary(self, year: int, month: int) -> Dict[str, float]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            date_pattern = f"{year}-{month:02d}%"
            cursor.execute(
                "SELECT COALESCE(SUM(amount * COALESCE(exchange_rate, 1.0)), 0) FROM transactions WHERE type='income' AND date LIKE ?",
                (date_pattern,)
            )
            income = cursor.fetchone()[0]
            cursor.execute(
                "SELECT COALESCE(SUM(amount * COALESCE(exchange_rate, 1.0)), 0) FROM transactions WHERE type='expense' AND date LIKE ?",
                (date_pattern,)
            )
            expense = cursor.fetchone()[0]
            return {"income": income, "expense": expense, "net": income - expense}

    def get_month_transaction_count(self, year: int, month: int) -> int:
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.cursor()
            cur.execute(
                "SELECT COUNT(*) FROM transactions WHERE date LIKE ?",
                (f"{year}-{month:02d}%",)
            )
            return int(cur.fetchone()[0])

    def get_category_stats(self, start_date: str, end_date: str, type_filter: str = "expense") -> List[Dict]:
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """SELECT c.name, c.color, c.icon, SUM(t.amount * COALESCE(t.exchange_rate, 1.0)) as total
                   FROM transactions t
                   JOIN categories c ON t.category_id = c.id
                   WHERE t.date BETWEEN ? AND ? AND t.type = ?
                   GROUP BY c.id
                   ORDER BY total DESC""",
                (start_date, end_date, type_filter)
            )
            return [dict(row) for row in cursor.fetchall()]

    def get_total_balance(self) -> Dict[str, float]:
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COALESCE(SUM(balance), 0) FROM accounts")
            total = cursor.fetchone()[0]
            return {"total": total}
