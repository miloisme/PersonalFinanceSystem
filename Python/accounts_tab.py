from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QComboBox, QDoubleSpinBox, QMessageBox,
    QFrame, QCheckBox, QDateEdit, QSizePolicy
)
from PySide6.QtWidgets import QScrollArea
from PySide6.QtCore import Qt, QDate
from PySide6.QtGui import QColor

from database import Account, Transaction
from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_sort_filter

from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
import matplotlib
import matplotlib.colors as mcolors
matplotlib.use('QtAgg')
matplotlib.rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Microsoft YaHei', 'Segoe UI', 'DejaVu Sans']
matplotlib.rcParams['font.family'] = 'sans-serif'
matplotlib.rcParams['axes.unicode_minus'] = False

from chart_tooltips import enable_pie_hover
from datetime import datetime


def _currency_symbol(currency):
    return {
        "CNY": "¥", "HKD": "HK$", "USD": "$", "EUR": "€",
        "GBP": "£", "JPY": "¥", "TWD": "NT$", "KRW": "₩",
        "SGD": "S$", "AUD": "A$", "CAD": "C$"
    }.get(currency, currency + " ")


CARD_STYLE = "QFrame { background-color: white; border-radius: 8px; border: none; }"

COLOR_ASSETS = "#1f3a93"
COLOR_DEBT = "#8b0000"
COLOR_NET = "#16a0de"

BUCKETS = [
    ("Cash", "cash", "#1f4e79", ["#1f4e79", "#2e75b6", "#5b9bd5", "#9dc3e6", "#bdd7ee", "#deeaf6", "#0b3d62", "#3a86c8"]),
    ("Invest", "invest", "#5f2a7a", ["#5f2a7a", "#8064a2", "#a587c9", "#c8b7e0", "#e4d9f0", "#f3ebf7", "#4a1f61", "#704a91"]),
    ("Fixed", "fixed", "#9c5600", ["#9c5600", "#c55a11", "#ed7d31", "#f4b183", "#fbe5d6", "#fff2e6", "#7a4300", "#d97a2c"]),
]

BUCKET_BASE_COLOR = {key: color for _, key, color, _ in BUCKETS}

CURRENCY_PALETTE = ["#1f4e79", "#c0392b", "#27ae60", "#8e44ad",
                    "#d35400", "#16a085", "#2c3e50", "#e67e22",
                    "#34495e", "#2980b9", "#7f8c8d"]

LABEL_MIN_PCT = 5.0


def _text_color_for(hex_color):
    """Pick black or white text for best contrast against the given background color."""
    rgb = mcolors.to_rgb(hex_color)
    lum = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
    return "white" if lum < 0.55 else "black"


def _gradient_colors(base, n):
    """Return n colors going dark -> light, for slices ordered largest -> smallest."""
    if n <= 0:
        return []
    base_rgb = mcolors.to_rgb(base)
    dark = tuple(c * 0.55 for c in base_rgb)
    light = tuple(min(1.0, c + (1.0 - c) * 0.78) for c in base_rgb)
    if n == 1:
        return [mcolors.to_hex(base_rgb)]
    return [mcolors.to_hex(tuple(dark[i] + (light[i] - dark[i]) * (k / (n - 1)) for i in range(3)))
            for k in range(n)]

TYPE_TO_BUCKET = {
    "cash": "cash",
    "invest": "invest",
    "fixed": "fixed",
}


class AccountDialog(QDialog):
    def __init__(self, parent=None, account: Account = None, db=None):
        super().__init__(parent)
        self.account = account
        self.db = db
        self.converter = CurrencyConverter()
        self.setWindowTitle("Edit Account" if account else "Add Account")
        self.setMinimumWidth(420)
        try:
            from main_window import DIALOG_STYLE
            self.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        self.setup_ui()

    def setup_ui(self):
        layout = QFormLayout(self)
        layout.setSpacing(6)
        layout.setContentsMargins(10, 8, 10, 8)

        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("Enter account name")
        layout.addRow("Name:", self.name_edit)

        self.currency_combo = QComboBox()
        currencies = self.db.get_currencies()
        self.currency_combo.addItems(currencies)
        base_cur = self.db.get_base_currency()
        bi = self.currency_combo.findText(base_cur)
        if bi >= 0:
            self.currency_combo.setCurrentIndex(bi)
        layout.addRow("Currency:", self.currency_combo)

        self.balance_spin = QDoubleSpinBox()
        self.balance_spin.setRange(-999999999, 999999999)
        self.balance_spin.setDecimals(2)
        layout.addRow("Balance:", self.balance_spin)

        self.type_combo = QComboBox()
        self.type_combo.addItem("Cash", "cash")
        self.type_combo.addItem("Invest", "invest")
        self.type_combo.addItem("Fixed", "fixed")
        layout.addRow("Type:", self.type_combo)

        self.notes_edit = QLineEdit()
        self.notes_edit.setPlaceholderText("Optional notes")
        layout.addRow("Notes:", self.notes_edit)

        self.keep_book_cb = QCheckBox("Keep Book (record transactions)")
        self.keep_book_cb.stateChanged.connect(self._toggle_book_fields)
        layout.addRow("", self.keep_book_cb)

        self._book_widget = QWidget()
        book_layout = QFormLayout(self._book_widget)
        book_layout.setContentsMargins(0, 0, 0, 0)
        book_layout.setSpacing(4)
        self._book_date = QDateEdit()
        self._book_date.setCalendarPopup(True)
        self._book_date.setDate(QDate.currentDate())
        self._book_date.setDisplayFormat("yyyy-MM-dd")
        book_layout.addRow("Date:", self._book_date)
        self._book_amount = QDoubleSpinBox()
        self._book_amount.setRange(-999999999, 999999999)
        self._book_amount.setDecimals(2)
        book_layout.addRow("Amount:", self._book_amount)
        self._book_type = QComboBox()
        self._book_type.addItem("Income", "income")
        self._book_type.addItem("Expense", "expense")
        book_layout.addRow("Type:", self._book_type)
        self._book_desc = QLineEdit()
        self._book_desc.setPlaceholderText("Optional description")
        book_layout.addRow("Desc:", self._book_desc)
        self._book_widget.setVisible(False)
        layout.addRow(self._book_widget)

        if self.account:
            self.name_edit.setText(self.account.name)
            idx = self.currency_combo.findText(self.account.currency)
            if idx >= 0:
                self.currency_combo.setCurrentIndex(idx)
            self.balance_spin.setValue(self.account.balance)
            for i in range(self.type_combo.count()):
                if self.type_combo.itemData(i) == self.account.account_type:
                    self.type_combo.setCurrentIndex(i)
                    break
            self.notes_edit.setText(getattr(self.account, "notes", "") or "")

        btn_row = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        btn_row.addWidget(save_btn)
        btn_row.addWidget(cancel_btn)
        layout.addRow(btn_row)

    def _toggle_book_fields(self, state):
        self._book_widget.setVisible(state == Qt.CheckState.Checked.value)

    def get_account(self) -> Account:
        return Account(
            id=self.account.id if self.account else None,
            name=self.name_edit.text(),
            currency=self.currency_combo.currentText(),
            balance=self.balance_spin.value(),
            account_type=self.type_combo.currentData(),
            notes=self.notes_edit.text().strip()
        )

    def get_book_transaction(self):
        if not self.keep_book_cb.isChecked():
            return None
        return {
            "date": self._book_date.date().toString("yyyy-MM-dd"),
            "amount": self._book_amount.value(),
            "type": self._book_type.currentData(),
            "description": self._book_desc.text().strip(),
        }


class TransferDialog(QDialog):
    def __init__(self, parent=None, db=None, accounts=None):
        super().__init__(parent)
        self.db = db
        self.accounts = accounts or []
        self.setWindowTitle("Transfer Between Accounts")
        self.setMinimumWidth(400)
        try:
            from main_window import DIALOG_STYLE
            self.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        self.setup_ui()

    def setup_ui(self):
        layout = QFormLayout(self)
        layout.setLabelAlignment(Qt.AlignmentFlag.AlignRight)

        self.from_combo = QComboBox()
        self.from_combo.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.from_combo.setMinimumWidth(220)
        layout.addRow("From:", self.from_combo)

        self.to_combo = QComboBox()
        self.to_combo.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.to_combo.setMinimumWidth(220)
        layout.addRow("To:", self.to_combo)

        self.currency_label = QLabel("")
        self.currency_label.setStyleSheet("color: #8e44ad; font-weight: bold;")
        layout.addRow("Currency:", self.currency_label)

        self.amount_spin = QDoubleSpinBox()
        self.amount_spin.setRange(0, 1e15)
        self.amount_spin.setDecimals(2)
        self.amount_spin.setSingleStep(100)
        self.amount_spin.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.amount_spin.setMinimumWidth(220)
        layout.addRow("Amount:", self.amount_spin)

        self.from_combo.currentIndexChanged.connect(self._on_from_changed)
        self._populate_from()

        btn_row = QHBoxLayout()
        ok_btn = QPushButton("Transfer")
        ok_btn.setStyleSheet("QPushButton { background-color: #8e44ad; color: white; border: none; padding: 8px 16px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #732d91; }")
        ok_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.setStyleSheet("QPushButton { background-color: #95a5a6; color: white; border: none; padding: 8px 16px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #7f8c8d; }")
        cancel_btn.clicked.connect(self.reject)
        btn_row.addWidget(ok_btn)
        btn_row.addWidget(cancel_btn)
        layout.addRow(btn_row)

    def _populate_from(self):
        self.from_combo.blockSignals(True)
        self.from_combo.clear()
        for a in self.accounts:
            if a.get("is_investment"):
                continue
            self.from_combo.addItem(f"{a['name']} ({a['currency']})", a["id"])
        self.from_combo.blockSignals(False)
        self._on_from_changed()

    def _on_from_changed(self):
        from_id = self.from_combo.currentData()
        from_acc = next((a for a in self.accounts if a["id"] == from_id), None)
        cur = from_acc["currency"] if from_acc else ""
        self.currency_label.setText(cur)
        self.to_combo.blockSignals(True)
        self.to_combo.clear()
        for a in self.accounts:
            if a.get("is_investment"):
                continue
            if a["id"] == from_id:
                continue
            if a["currency"] != cur:
                continue
            self.to_combo.addItem(f"{a['name']} ({a['currency']})", a["id"])
        self.to_combo.blockSignals(False)
        self.amount_spin.setPrefix(self._symbol(cur))

    def _symbol(self, currency):
        symbols = {
            "CNY": "¥", "HKD": "HK$", "USD": "$", "EUR": "€",
            "GBP": "£", "JPY": "¥", "TWD": "NT$", "KRW": "₩",
            "SGD": "S$", "AUD": "A$", "CAD": "C$"
        }
        return symbols.get(currency, currency + " ")

    def get_transfer(self):
        return {
            "from_id": self.from_combo.currentData(),
            "to_id": self.to_combo.currentData(),
            "amount": self.amount_spin.value(),
        }


class AccountsTab(QWidget):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.converter = CurrencyConverter()
        self.setup_ui()

    def setup_ui(self):
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        content = QWidget()
        layout = QVBoxLayout(content)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(6)

        header = QLabel("Account Management")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        top_row = QHBoxLayout()
        top_row.setSpacing(6)

        self.info_card = QFrame()
        self.info_card.setStyleSheet(CARD_STYLE)
        self._build_info_card(self.info_card)
        top_row.addWidget(self.info_card, 1)

        self.alloc_card = self._make_pie_card("Asset Allocation")
        top_row.addWidget(self.alloc_card, 1)

        self.curr_card = self._make_pie_card("Currency Distribution")
        top_row.addWidget(self.curr_card, 1)

        layout.addLayout(top_row)

        sub_row = QHBoxLayout()
        sub_row.setSpacing(6)
        self.sub_figs = {}
        self.sub_canvases = {}
        for label, key, color, _palette in BUCKETS:
            card = QFrame()
            card.setStyleSheet(CARD_STYLE)
            v = QVBoxLayout(card)
            v.setContentsMargins(8, 4, 8, 4)
            v.setSpacing(1)
            t = QLabel(label)
            _set_cjk_font(t)
            t.setStyleSheet(f"font-size: 12px; font-weight: bold; color: {color};")
            t.setAlignment(Qt.AlignmentFlag.AlignCenter)
            v.addWidget(t)
            fig = Figure(figsize=(3.2, 2.0), dpi=100)
            fig.set_facecolor('white')
            canvas = FigureCanvas(fig)
            v.addWidget(canvas)
            sub_row.addWidget(card)
            self.sub_figs[key] = fig
            self.sub_canvases[key] = canvas
        layout.addLayout(sub_row)

        btn_layout = QHBoxLayout()
        add_btn = QPushButton("+ Add Account")
        add_btn.setStyleSheet("""
            QPushButton {
                background-color: #3498db; color: white; border: none;
                padding: 7px 16px; border-radius: 5px; font-size: 13px;
            }
            QPushButton:hover { background-color: #2980b9; }
        """)
        add_btn.clicked.connect(self.add_account)
        btn_layout.addWidget(add_btn)

        transfer_btn = QPushButton("⇄ Transfer")
        transfer_btn.setStyleSheet("""
            QPushButton {
                background-color: #8e44ad; color: white; border: none;
                padding: 7px 16px; border-radius: 5px; font-size: 13px;
            }
            QPushButton:hover { background-color: #732d91; }
        """)
        transfer_btn.clicked.connect(self.transfer)
        btn_layout.addWidget(transfer_btn)

        btn_layout.addStretch()
        layout.addLayout(btn_layout)

        self.table = QTableWidget()
        self.table.setColumnCount(7)
        self.table.setHorizontalHeaderLabels(["Name", "Currency", "Balance", "Converted", "Type", "Notes", "Actions"])
        self.table.verticalHeader().setVisible(False)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setStyleSheet("""
            QTableWidget {
                background-color: white; border-radius: 8px;
                gridline-color: #ecf0f1;
            }
            QHeaderView::section {
                background-color: #f8f9fa; padding: 6px; border: none; font-weight: bold; font-size: 12px;
            }
        """)
        self._sort_col = None
        self._sort_order = Qt.SortOrder.AscendingOrder
        layout.addWidget(self.table)
        enable_sort_filter(self.table, "accounts_table",
                           action_col=self.table.columnCount() - 1, layout=layout,
                           enable_filter=False, on_sort_change=self._apply_sort)

        scroll.setWidget(content)
        outer.addWidget(scroll)

        self.refresh()

    def _make_pie_card(self, title):
        card = QFrame()
        card.setStyleSheet(CARD_STYLE)
        v = QVBoxLayout(card)
        v.setContentsMargins(8, 4, 8, 4)
        v.setSpacing(1)
        t = QLabel(title)
        _set_cjk_font(t)
        t.setStyleSheet("font-size: 12px; font-weight: bold; color: #2c3e50;")
        v.addWidget(t)
        fig = Figure(figsize=(3.2, 2.2), dpi=100)
        fig.set_facecolor('white')
        canvas = FigureCanvas(fig)
        v.addWidget(canvas)
        if title.startswith("Asset"):
            self.alloc_fig = fig
            self.alloc_canvas = canvas
        else:
            self.curr_fig = fig
            self.curr_canvas = canvas
        return card

    def _build_info_card(self, card):
        v = QVBoxLayout(card)
        v.setContentsMargins(10, 6, 10, 6)
        v.setSpacing(2)

        assets_title = QLabel("Assets")
        _set_cjk_font(assets_title)
        assets_title.setStyleSheet(f"font-size: 11px; font-weight: bold; color: {COLOR_ASSETS};")
        v.addWidget(assets_title)

        assets_val_row = QHBoxLayout()
        assets_val_row.setSpacing(8)
        self.assets_value = QLabel("--")
        _set_cjk_font(self.assets_value)
        self.assets_value.setStyleSheet(f"font-size: 16px; font-weight: bold; color: {COLOR_ASSETS};")
        assets_val_row.addWidget(self.assets_value)
        self.yoy_label = QLabel("")
        _set_cjk_font(self.yoy_label)
        self.yoy_label.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.yoy_label.setStyleSheet("font-size: 10px; font-weight: bold; padding: 1px 5px; border-radius: 6px;")
        assets_val_row.addWidget(self.yoy_label)
        assets_val_row.addStretch()
        v.addLayout(assets_val_row)

        self.last_year_label = QLabel("Last Year: --")
        _set_cjk_font(self.last_year_label)
        self.last_year_label.setStyleSheet("font-size: 10px; color: #95a5a6;")
        v.addWidget(self.last_year_label)

        v.addWidget(self._hline())

        debt_title = QLabel("Debt")
        _set_cjk_font(debt_title)
        debt_title.setStyleSheet(f"font-size: 11px; font-weight: bold; color: {COLOR_DEBT};")
        v.addWidget(debt_title)

        debt_val_row = QHBoxLayout()
        debt_val_row.setSpacing(8)
        self.debt_value = QLabel("--")
        _set_cjk_font(self.debt_value)
        self.debt_value.setStyleSheet(f"font-size: 16px; font-weight: bold; color: {COLOR_DEBT};")
        debt_val_row.addWidget(self.debt_value)
        self.debt_ratio_label = QLabel("")
        _set_cjk_font(self.debt_ratio_label)
        self.debt_ratio_label.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.debt_ratio_label.setStyleSheet(
            "font-size: 10px; font-weight: bold; color: #c0392b; "
            "background: #fdecea; padding: 1px 5px; border-radius: 6px;"
        )
        debt_val_row.addWidget(self.debt_ratio_label)
        debt_val_row.addStretch()
        v.addLayout(debt_val_row)

        v.addWidget(self._hline())

        net_title = QLabel("Net Assets")
        _set_cjk_font(net_title)
        net_title.setStyleSheet(f"font-size: 11px; font-weight: bold; color: {COLOR_NET};")
        v.addWidget(net_title)

        self.net_value = QLabel("--")
        _set_cjk_font(self.net_value)
        self.net_value.setStyleSheet(f"font-size: 16px; font-weight: bold; color: {COLOR_NET};")
        v.addWidget(self.net_value)

    @staticmethod
    def _hline():
        line = QFrame()
        line.setFrameShape(QFrame.Shape.HLine)
        line.setStyleSheet("color: #ecf0f1;")
        line.setFixedHeight(1)
        return line

    def _get_currency_symbol(self, currency):
        symbols = {
            "CNY": "\u00A5", "HKD": "HK$", "USD": "$", "EUR": "\u20AC",
            "GBP": "\u00A3", "JPY": "\u00A5", "TWD": "NT$", "KRW": "\u20A9",
            "SGD": "S$", "AUD": "A$", "CAD": "C$"
        }
        return symbols.get(currency, currency + " ")

    def _compute(self):
        base = self.db.get_base_currency()
        accounts = self.db.get_accounts()
        bucket_sums = {"cash": 0.0, "invest": 0.0, "fixed": 0.0}
        bucket_accounts = {"cash": [], "invest": [], "fixed": []}
        currency_sums = {}
        total = 0.0
        display_accounts = []
        for acc in accounts:
            converted = self.converter.convert(acc.balance, acc.currency, base)
            total += converted
            b = TYPE_TO_BUCKET.get(acc.account_type, "cash")
            bucket_sums[b] += converted
            if converted > 0:
                bucket_accounts[b].append((acc.name, converted, acc.currency))
                currency_sums[acc.currency] = currency_sums.get(acc.currency, 0.0) + converted
            display_accounts.append({
                "id": acc.id, "name": acc.name, "currency": acc.currency,
                "balance": acc.balance, "type": acc.account_type,
                "notes": getattr(acc, "notes", "") or "",
                "is_investment": False,
            })

        debt = 0.0
        receivable = 0.0
        for d in self.db.get_debts():
            if getattr(d, "completed", False):
                continue
            party = getattr(d, "debtor", "me")
            if party == "other":
                receivable += self.converter.convert(d.total_amount - d.paid_amount, d.currency, base)
            else:
                debt += self.converter.convert(d.total_amount - d.paid_amount, d.currency, base)

        if receivable > 0:
            total += receivable
            bucket_sums["cash"] += receivable
            bucket_accounts["cash"].append(("Receivable", receivable, base))
            currency_sums[base] = currency_sums.get(base, 0.0) + receivable
            display_accounts.append({
                "id": None, "name": "Receivable", "currency": base,
                "balance": receivable, "type": "cash",
                "is_investment": True, "is_receivable": True,
            })

        this_year = datetime.now().year
        last = self.db.get_latest_net_worth_before(f"{this_year}-01-01")
        return {
            "base": base, "total": total, "debt": debt, "net": total - debt,
            "bucket_sums": bucket_sums, "bucket_accounts": bucket_accounts,
            "currency_sums": currency_sums,
            "last_year_assets": last["assets"] if last else None,
            "accounts": display_accounts,
        }

    def refresh(self):
        data = self._compute()
        base = data["base"]
        symbol = self._get_currency_symbol(base)
        total = data["total"]
        debt = data["debt"]

        self.assets_value.setText(f"{symbol}{total:,.2f}")
        self.debt_value.setText(f"{symbol}{debt:,.2f}")
        self.net_value.setText(f"{symbol}{data['net']:,.2f}")

        last = data["last_year_assets"]
        if last is not None and last != 0:
            pct = (total - last) / abs(last) * 100
            if pct > 0:
                sign, color, bg = "+", "#27ae60", "#eafaf1"
            elif pct < 0:
                sign, color, bg = "", "#c0392b", "#fdecea"
            else:
                sign, color, bg = "", "#95a5a6", "#f4f6f7"
            self.yoy_label.setText(f"{sign}{pct:.1f}% YoY")
            self.yoy_label.setStyleSheet(
                f"font-size: 10px; font-weight: bold; color: {color}; "
                f"background: {bg}; padding: 1px 5px; border-radius: 6px;"
            )
            self.last_year_label.setText(f"Last Year: {symbol}{last:,.2f}")
        else:
            self.yoy_label.setText("N/A")
            self.yoy_label.setStyleSheet(
                "font-size: 10px; font-weight: bold; color: #95a5a6; "
                "background: #f4f6f7; padding: 1px 5px; border-radius: 6px;"
            )
            self.last_year_label.setText("Last Year: --")

        if debt == 0:
            self.debt_ratio_label.hide()
        elif total > 0:
            self.debt_ratio_label.show()
            ratio = debt / total * 100
            self.debt_ratio_label.setText(f"{ratio:.1f}%")
        else:
            self.debt_ratio_label.setText("N/A")
            self.debt_ratio_label.show()

        self._draw_allocation_pie(data)
        self._draw_currency_pie(data)
        for label, key, color, palette in BUCKETS:
            self._draw_sub_pie(key, data["bucket_accounts"].get(key, []))
        self._fill_table(data)

    def _draw_pie(self, fig, canvas, items, base_color, empty_label="No Data",
                  label_fontsize=7, pct_fontsize=6):
        fig.clear()
        ax = fig.add_subplot(111)
        if not items:
            ax.text(0.5, 0.5, empty_label, ha='center', va='center',
                    fontsize=9, color='gray', transform=ax.transAxes)
            ax.set_xticks([]); ax.set_yticks([])
        else:
            total = sum(v for _, v in items)
            ordered = sorted(items, key=lambda x: -x[1])
            names = [n for n, _ in ordered]
            values = [v for _, v in ordered]
            colors = _gradient_colors(base_color, len(values))
            labels = []
            sym = _currency_symbol(self.db.get_base_currency())
            for name, v in zip(names, values):
                pct = (v / total * 100) if total else 0
                labels.append(f"{name}\n{sym}{v:,.2f}" if pct >= LABEL_MIN_PCT else "")
            def autopct_func(pct):
                return f"{pct:.1f}%" if pct >= LABEL_MIN_PCT else ""
            wedges, texts, autotexts = ax.pie(
                values, labels=labels, colors=colors, autopct=autopct_func,
                startangle=90, counterclock=False, pctdistance=0.62,
                wedgeprops=dict(edgecolor='white', linewidth=1.5)
            )
            for t in texts:
                t.set_fontsize(label_fontsize)
                t.set_color('black')
            for at, c in zip(autotexts, colors):
                at.set_fontsize(pct_fontsize)
                at.set_color(_text_color_for(c))
            ax.axis('equal')
            enable_pie_hover(fig, ax, wedges, names, values, total, sym)
        fig.tight_layout()
        canvas.draw()
        canvas.flush_events()

    def _draw_allocation_pie(self, data):
        items = []
        for label, key, color, _ in BUCKETS:
            amt = data["bucket_sums"].get(key, 0)
            if amt > 0:
                items.append((label, amt))
        self._draw_pie(self.alloc_fig, self.alloc_canvas, items, "#1f3a93",
                       empty_label="No Assets")

    def _draw_currency_pie(self, data):
        currency_sums = data["currency_sums"]
        items = [(code, amt) for code, amt in currency_sums.items() if amt > 0]
        self._draw_pie(self.curr_fig, self.curr_canvas, items, "#16a085",
                       empty_label="No Currencies")

    def _draw_sub_pie(self, key, accounts):
        items = [(a[0], a[1]) for a in accounts]
        self._draw_pie(self.sub_figs[key], self.sub_canvases[key], items,
                       BUCKET_BASE_COLOR.get(key, "#34495e"), empty_label="No data")

    def _sort_accounts(self, accounts, base):
        col = self._sort_col
        if col is None or not accounts:
            return accounts
        if col == 0:
            key = lambda a: a["name"]
        elif col == 1:
            key = lambda a: a["currency"]
        elif col == 2:
            key = lambda a: a["balance"]
        elif col == 3:
            key = lambda a: self.converter.convert(a["balance"], a["currency"], base)
        elif col == 4:
            key = lambda a: a["type"]
        elif col == 5:
            key = lambda a: (a.get("notes") or "").lower()
        else:
            return accounts
        return sorted(accounts, key=key, reverse=(self._sort_order == Qt.SortOrder.DescendingOrder))

    def _apply_sort(self, col, order):
        self._sort_col = col
        self._sort_order = order
        self.refresh()

    def _fill_table(self, data):
        accounts = self._sort_accounts(data["accounts"], data["base"])
        base = data["base"]
        self.table.setSortingEnabled(False)
        self.table.setRowCount(len(accounts))

        type_map = {"cash": "Cash", "invest": "Invest", "fixed": "Fixed"}
        for i, account in enumerate(accounts):
            self.table.setItem(i, 0, QTableWidgetItem(account["name"]))
            self.table.setItem(i, 1, QTableWidgetItem(account["currency"]))

            symbol = self._get_currency_symbol(account["currency"])
            balance_item = QTableWidgetItem(f"{symbol}{account['balance']:,.2f}")
            balance_item.setForeground(QColor("#27ae60") if account["balance"] >= 0 else QColor("#e74c3c"))
            self.table.setItem(i, 2, balance_item)

            converted = self.converter.convert(account["balance"], account["currency"], base)
            base_symbol = self._get_currency_symbol(base)
            converted_item = QTableWidgetItem(f"{base_symbol}{converted:,.2f}")
            converted_item.setForeground(QColor("#3498db"))
            self.table.setItem(i, 3, converted_item)

            self.table.setItem(i, 4, QTableWidgetItem(type_map.get(account["type"], account["type"])))

            notes_item = QTableWidgetItem(account.get("notes") or "")
            notes_item.setToolTip(account.get("notes") or "")
            self.table.setItem(i, 5, notes_item)

            btn_widget = QWidget()
            btn_layout = QHBoxLayout(btn_widget)
            btn_layout.setContentsMargins(4, 2, 4, 2)
            btn_layout.setSpacing(4)
            if account.get("is_receivable"):
                tag = QLabel("Auto")
                tag.setStyleSheet("color: #95a5a6; font-size: 11px; padding: 3px 8px;")
                btn_layout.addWidget(tag)
            elif account.get("is_investment"):
                tag = QLabel("Auto")
                tag.setStyleSheet("color: #95a5a6; font-size: 11px; padding: 3px 8px;")
                btn_layout.addWidget(tag)
            else:
                edit_btn = QPushButton("Edit")
                edit_btn.setStyleSheet("QPushButton { background-color: #f39c12; color: white; border: none; padding: 3px 8px; border-radius: 3px; font-size: 11px; } QPushButton:hover { background-color: #e67e22; }")
                edit_btn.clicked.connect(lambda _, acc=account: self.edit_account(acc))
                btn_layout.addWidget(edit_btn)
                delete_btn = QPushButton("Delete")
                delete_btn.setStyleSheet("QPushButton { background-color: #e74c3c; color: white; border: none; padding: 3px 8px; border-radius: 3px; font-size: 11px; } QPushButton:hover { background-color: #c0392b; }")
                delete_btn.clicked.connect(lambda _, acc_id=account["id"]: self.delete_account(acc_id))
                btn_layout.addWidget(delete_btn)
            self.table.setCellWidget(i, 6, btn_widget)
            self.table.setRowHeight(i, 36)

    def add_account(self):
        dialog = AccountDialog(self, db=self.db)
        if dialog.exec():
            account = dialog.get_account()
            acc_id = self.db.add_account(account)
            book = dialog.get_book_transaction()
            if book and book["amount"] != 0:
                tx = Transaction(
                    account_id=acc_id,
                    category_id=0,
                    amount=abs(book["amount"]),
                    type=book["type"],
                    description=book["description"],
                    date=book["date"],
                )
                self.db.add_transaction(tx)
            self.refresh()

    def get_account_list(self):
        return self._compute()["accounts"]

    def transfer(self):
        accounts = self.get_account_list()
        dialog = TransferDialog(self, db=self.db, accounts=accounts)
        if dialog.exec():
            data = dialog.get_transfer()
            if data["from_id"] is None or data["to_id"] is None:
                QMessageBox.warning(self, "Transfer Failed", "Please select both accounts.")
                return
            try:
                self.db.transfer_between_accounts(data["from_id"], data["to_id"], data["amount"])
            except ValueError as e:
                QMessageBox.warning(self, "Transfer Failed", str(e))
                return
            self.refresh()

    def edit_account(self, account):
        if isinstance(account, dict):
            account = Account(
                id=account["id"], name=account["name"], currency=account["currency"],
                balance=account["balance"], account_type=account["type"],
                notes=account.get("notes", "")
            )
        dialog = AccountDialog(self, account, db=self.db)
        if dialog.exec():
            updated_account = dialog.get_account()
            self.db.update_account(updated_account)
            self.refresh()

    def delete_account(self, account_id: int):
        reply = QMessageBox.question(
            self, "Confirm Delete", "Are you sure you want to delete this account?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.db.delete_account(account_id)
            self.refresh()
