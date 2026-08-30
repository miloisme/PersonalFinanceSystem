from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QComboBox, QDoubleSpinBox, QTextEdit,
    QDateEdit, QMessageBox, QFrame, QGroupBox, QSizePolicy
)
from PySide6.QtWidgets import QScrollArea
from PySide6.QtCore import Qt, QDate
from PySide6.QtGui import QFont, QColor

from database import Transaction
from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_sort_filter


class TransactionDialog(QDialog):
    def __init__(self, parent=None, db=None, transaction=None):
        super().__init__(parent)
        self.db = db
        self.transaction = transaction
        self.converter = CurrencyConverter()
        self.setWindowTitle("Edit Record" if transaction else "Add Record")
        self.setMinimumWidth(480)
        try:
            from main_window import DIALOG_STYLE
            self.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        self.setup_ui()

    def setup_ui(self):
        layout = QFormLayout(self)
        layout.setLabelAlignment(Qt.AlignmentFlag.AlignRight)

        self.type_combo = QComboBox()
        self.type_combo.addItem("Expense", "expense")
        self.type_combo.addItem("Income", "income")
        self.type_combo.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.type_combo.setMinimumWidth(220)
        self.type_combo.currentIndexChanged.connect(self.on_type_changed)
        layout.addRow("Type:", self.type_combo)

        self.account_combo = QComboBox()
        self.account_combo.addItem("None", None)
        accounts = self.db.get_accounts()
        for account in accounts:
            self.account_combo.addItem(f"{account.name} ({account.currency})", account.id)
        self.account_combo.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.account_combo.setMinimumWidth(220)
        self.account_combo.currentIndexChanged.connect(self._on_account_changed)
        layout.addRow("Account:", self.account_combo)

        self.currency_combo = QComboBox()
        self.currency_combo.addItems(self.db.get_currencies())
        self.currency_combo.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.currency_combo.setMinimumWidth(220)
        self.currency_combo.currentIndexChanged.connect(self._update_rate)
        layout.addRow("Currency:", self.currency_combo)

        rate_widget = QWidget()
        rate_h = QHBoxLayout(rate_widget)
        rate_h.setContentsMargins(0, 0, 0, 0)
        self.rate_prefix = QLabel("1 " + self.db.get_base_currency() + " =")
        self.rate_prefix.setStyleSheet("font-size: 11px; color: #7f8c8d;")
        self.rate_spin = QDoubleSpinBox()
        self.rate_spin.setRange(0.0001, 999999)
        self.rate_spin.setDecimals(4)
        self.rate_spin.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.rate_spin.setSuffix(" " + self.db.get_base_currency())
        rate_h.addWidget(self.rate_prefix)
        rate_h.addWidget(self.rate_spin)
        rate_h.addStretch()
        layout.addRow("Rate (at entry):", rate_widget)

        self.category_combo = QComboBox()
        self.category_combo.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.category_combo.setMinimumWidth(220)
        self.load_categories("expense")
        layout.addRow("Category:", self.category_combo)

        self.amount_spin = QDoubleSpinBox()
        self.amount_spin.setRange(0, 999999999)
        self.amount_spin.setDecimals(2)
        layout.addRow("Amount:", self.amount_spin)

        self.date_edit = QDateEdit()
        self.date_edit.setCalendarPopup(True)
        self.date_edit.setDate(QDate.currentDate())
        layout.addRow("Date:", self.date_edit)

        self.desc_edit = QTextEdit()
        self.desc_edit.setPlaceholderText("Add notes...")
        self.desc_edit.setMaximumHeight(100)
        layout.addRow("Notes:", self.desc_edit)

        if self.transaction:
            index = self.type_combo.findData(self.transaction.type)
            if index >= 0:
                self.type_combo.setCurrentIndex(index)
            acc_id = self.transaction.account_id if self.transaction.account_id else None
            acc_index = self.account_combo.findData(acc_id)
            if acc_index >= 0:
                self.account_combo.setCurrentIndex(acc_index)
            self.load_categories(self.transaction.type)
            cat_index = self.category_combo.findData(self.transaction.category_id)
            if cat_index >= 0:
                self.category_combo.setCurrentIndex(cat_index)
            self.amount_spin.setValue(self.transaction.amount)
            if self.transaction.date:
                self.date_edit.setDate(QDate.fromString(self.transaction.date, "yyyy-MM-dd"))
            self.desc_edit.setText(self.transaction.description)
            if self.transaction.currency:
                ci = self.currency_combo.findText(self.transaction.currency)
                if ci >= 0:
                    self.currency_combo.setCurrentIndex(ci)

        if self.transaction is None:
            base_cur = self.db.get_base_currency()
            bi = self.currency_combo.findText(base_cur)
            if bi >= 0:
                self.currency_combo.setCurrentIndex(bi)

        self._update_rate()
        if self.transaction is not None and self.transaction.exchange_rate:
            self.rate_spin.setValue(self.transaction.exchange_rate)
        button_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(save_btn)
        button_layout.addWidget(cancel_btn)
        layout.addRow(button_layout)

    def _on_account_changed(self, _index):
        acc_id = self.account_combo.currentData()
        if acc_id is not None:
            for a in self.db.get_accounts():
                if a.id == acc_id:
                    ci = self.currency_combo.findText(a.currency)
                    if ci >= 0:
                        self.currency_combo.setCurrentIndex(ci)
                    break
        self._update_rate()

    def _update_rate(self):
        cur = self.currency_combo.currentText()
        base = self.db.get_base_currency()
        try:
            rate = self.converter.get_rate(cur, base)
        except Exception:
            rate = 1.0
        if rate <= 0:
            rate = 1.0
        self.rate_prefix.setText(f"1 {base} =")
        self.rate_spin.setSuffix(" " + cur)
        self.rate_spin.setValue(rate)

    def on_type_changed(self, index):
        type_value = self.type_combo.currentData()
        self.load_categories(type_value)

    def load_categories(self, type_filter):
        self.category_combo.clear()
        categories = self.db.get_categories(type_filter)
        for cat in categories:
            self.category_combo.addItem(f"{cat.icon} {cat.name}", cat.id)

    def get_transaction(self) -> Transaction:
        acc_id = self.account_combo.currentData()
        cur = self.currency_combo.currentText()
        return Transaction(
            id=self.transaction.id if self.transaction else None,
            account_id=acc_id if acc_id is not None else 0,
            category_id=self.category_combo.currentData(),
            amount=self.amount_spin.value(),
            type=self.type_combo.currentData(),
            description=self.desc_edit.toPlainText(),
            date=self.date_edit.date().toString("yyyy-MM-dd"),
            currency=cur,
            exchange_rate=self.rate_spin.value(),
        )


class TransactionsTab(QWidget):
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

        header = QLabel("Transactions")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        # ---- Filter section: dynamic conditions ----
        filter_frame = QFrame()
        filter_frame.setStyleSheet("""
            QFrame {
                background-color: white;
                border-radius: 10px;
                padding: 10px 12px;
            }
        """)
        filter_layout = QVBoxLayout(filter_frame)
        filter_layout.setContentsMargins(0, 0, 0, 0)
        filter_layout.setSpacing(8)

        filter_top = QHBoxLayout()
        filter_top.setSpacing(8)
        filter_lbl = QLabel("Filters")
        _set_cjk_font(filter_lbl)
        filter_lbl.setStyleSheet("font-size: 13px; font-weight: bold; color: #2c3e50;")
        filter_top.addWidget(filter_lbl)
        filter_top.addStretch()

        add_cond_btn = QPushButton("+ Add Condition")
        add_cond_btn.setStyleSheet("""
            QPushButton {
                background-color: #3498db; color: white; border: none;
                padding: 5px 12px; border-radius: 4px; font-size: 12px;
            }
            QPushButton:hover { background-color: #2980b9; }
        """)
        add_cond_btn.clicked.connect(self.add_condition)
        filter_top.addWidget(add_cond_btn)

        self.clear_cond_btn = QPushButton("Clear")
        self.clear_cond_btn.setStyleSheet("""
            QPushButton {
                background-color: #95a5a6; color: white; border: none;
                padding: 5px 12px; border-radius: 4px; font-size: 12px;
            }
            QPushButton:hover { background-color: #7f8c8d; }
        """)
        self.clear_cond_btn.clicked.connect(self.clear_conditions)
        filter_top.addWidget(self.clear_cond_btn)
        filter_layout.addLayout(filter_top)

        self.chips_layout = QHBoxLayout()
        self.chips_layout.setSpacing(6)
        self.chips_layout.setContentsMargins(0, 0, 0, 0)
        self.chips_layout.addStretch()
        filter_layout.addLayout(self.chips_layout)
        self.conditions = []
        self._render_chips()

        layout.addWidget(filter_frame)

        stats_frame = QFrame()
        stats_frame.setStyleSheet("""
            QFrame {
                background-color: white;
                border-radius: 10px;
                padding: 8px 12px;
            }
        """)
        stats_layout = QHBoxLayout(stats_frame)
        stats_layout.setContentsMargins(0, 0, 0, 0)
        stats_layout.setSpacing(6)

        def make_stat(label_text, value_color):
            container = QVBoxLayout()
            container.setSpacing(1)
            lbl = QLabel(label_text)
            _set_cjk_font(lbl)
            lbl.setStyleSheet("font-size: 12px; color: #7f8c8d;")
            val = QLabel("0")
            _set_cjk_font(val)
            val.setStyleSheet(f"font-size: 18px; font-weight: bold; color: {value_color};")
            container.addWidget(lbl)
            container.addWidget(val)
            stats_layout.addLayout(container)
            return val

        self.records_value = make_stat("Records", "#2c3e50")
        self.income_value = make_stat("Income", "#27ae60")
        self.expense_value = make_stat("Expense", "#e74c3c")
        self.net_value = make_stat("Net", "#3498db")

        layout.addWidget(stats_frame)

        btn_layout = QHBoxLayout()
        add_btn = QPushButton("+ Add Transaction")
        add_btn.setStyleSheet("""
            QPushButton {
                background-color: #3498db;
                color: white;
                border: none;
                padding: 7px 16px;
                border-radius: 5px;
                font-size: 13px;
            }
            QPushButton:hover {
                background-color: #2980b9;
            }
        """)
        add_btn.clicked.connect(self.add_transaction)
        btn_layout.addWidget(add_btn)
        btn_layout.addStretch()
        layout.addLayout(btn_layout)

        self.table = QTableWidget()
        self.table.setColumnCount(8)
        self.table.setHorizontalHeaderLabels(
            ["Date", "Type", "Category", "Amount", "Converted", "Account", "Notes", "Actions"])
        self.table.verticalHeader().setVisible(False)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setStyleSheet("""
            QTableWidget {
                background-color: white;
                border-radius: 10px;
                gridline-color: #ecf0f1;
            }
            QHeaderView::section {
                background-color: #f8f9fa;
                padding: 10px;
                border: none;
                font-weight: bold;
            }
        """)
        self._sort_col = None
        self._sort_order = Qt.SortOrder.AscendingOrder
        layout.addWidget(self.table)
        enable_sort_filter(self.table, "transactions_table",
                           action_col=self.table.columnCount() - 1, layout=layout,
                           enable_filter=False, on_sort_change=self._apply_sort)

        scroll.setWidget(content)
        outer.addWidget(scroll)

        self.refresh()

    def _get_currency_symbol(self, currency):
        symbols = {
            "CNY": "\u00A5", "HKD": "HK$", "USD": "$", "EUR": "\u20AC",
            "GBP": "\u00A3", "JPY": "\u00A5", "TWD": "NT$", "KRW": "\u20A9",
            "SGD": "S$", "AUD": "A$", "CAD": "C$"
        }
        return symbols.get(currency, currency + " ")

    def _sort_key(self, item):
        trans, converted = item
        c = self._sort_col
        if c == 0:
            return trans.get("date", "")
        if c == 1:
            return trans.get("type", "")
        if c == 2:
            return (trans.get("category_name") or "").lower()
        if c == 3 or c == 4:
            return converted
        if c == 5:
            return (trans.get("account_name") or "").lower()
        if c == 6:
            return (trans.get("description") or "").lower()
        return trans.get("date", "")

    def _apply_sort(self, col, order):
        self._sort_col = col
        self._sort_order = order
        self.refresh()

    def refresh(self):
        raw = self.db.get_transactions()
        raw.sort(key=lambda t: (t.get("date", ""), t.get("id", 0)))

        base_currency = self.db.get_base_currency()
        base_symbol = self._get_currency_symbol(base_currency)

        filtered = []
        for trans in raw:
            converted = trans.get("base_amount", trans["amount"])
            ok = True
            for cond in self.conditions:
                f = cond["field"]
                v = cond["value"]
                if f == "account":
                    if trans.get("account_id") != v:
                        ok = False
                        break
                elif f == "type":
                    if trans["type"] != v:
                        ok = False
                        break
                elif f == "category":
                    if trans.get("category_id") != v:
                        ok = False
                        break
                elif f == "note":
                    if v.lower() not in (trans.get("description") or "").lower():
                        ok = False
                        break
                elif f == "date_from":
                    if trans.get("date", "") < v:
                        ok = False
                        break
                elif f == "date_to":
                    if trans.get("date", "") > v:
                        ok = False
                        break
                elif f == "amount_min":
                    if converted < v:
                        ok = False
                        break
                elif f == "amount_max":
                    if converted > v:
                        ok = False
                        break
            if ok:
                filtered.append((trans, converted))

        if self._sort_col is not None:
            filtered.sort(key=self._sort_key,
                          reverse=(self._sort_order == Qt.SortOrder.DescendingOrder))
        else:
            filtered.sort(key=lambda it: (it[0].get("date", ""), it[0].get("id", 0)))

        total_income = 0.0
        total_expense = 0.0
        self.table.setSortingEnabled(False)
        self.table.setRowCount(len(filtered))

        for i, (trans, converted_amount) in enumerate(filtered):
            self.table.setItem(i, 0, QTableWidgetItem(trans.get("date", "")))

            type_item = QTableWidgetItem("Income" if trans["type"] == "income" else "Expense")
            if trans["type"] == "income":
                type_item.setForeground(QColor("#27ae60"))
            else:
                type_item.setForeground(QColor("#e74c3c"))
            self.table.setItem(i, 1, type_item)

            category_text = f"{trans.get('category_icon', '')} {trans.get('category_name', '')}"
            self.table.setItem(i, 2, QTableWidgetItem(category_text))

            amount_text = f"{trans['amount']:,.2f} {trans['currency']}"
            amount_item = QTableWidgetItem(amount_text)
            if trans["type"] == "income":
                amount_item.setForeground(QColor("#27ae60"))
                total_income += converted_amount
            else:
                amount_item.setForeground(QColor("#e74c3c"))
                total_expense += converted_amount
            self.table.setItem(i, 3, amount_item)

            converted_item = QTableWidgetItem(f"{base_symbol}{converted_amount:,.2f}")
            converted_item.setToolTip(f"= {trans['amount']:,.2f} {trans['currency']} × rate-at-entry")
            if trans["type"] == "income":
                converted_item.setForeground(QColor("#27ae60"))
            else:
                converted_item.setForeground(QColor("#e74c3c"))
            self.table.setItem(i, 4, converted_item)

            account_name = trans.get("account_name", "") or ""
            self.table.setItem(i, 5, QTableWidgetItem(account_name if account_name else "--"))

            desc_item = QTableWidgetItem(trans.get("description", ""))
            desc_item.setToolTip(trans.get("description", ""))
            self.table.setItem(i, 6, desc_item)

            btn_widget = QWidget()
            btn_layout = QHBoxLayout(btn_widget)
            btn_layout.setContentsMargins(5, 5, 5, 5)

            edit_btn = QPushButton("Edit")
            edit_btn.setStyleSheet("""
                QPushButton {
                    background-color: #f39c12;
                    color: white;
                    border: none;
                    padding: 5px 10px;
                    border-radius: 3px;
                }
                QPushButton:hover {
                    background-color: #e67e22;
                }
            """)
            edit_btn.clicked.connect(lambda _, t=trans: self.edit_transaction(t))
            btn_layout.addWidget(edit_btn)

            delete_btn = QPushButton("Delete")
            delete_btn.setStyleSheet("""
                QPushButton {
                    background-color: #e74c3c;
                    color: white;
                    border: none;
                    padding: 5px 10px;
                    border-radius: 3px;
                }
                QPushButton:hover {
                    background-color: #c0392b;
                }
            """)
            delete_btn.clicked.connect(lambda _, t_id=trans["id"]: self.delete_transaction(t_id))
            btn_layout.addWidget(delete_btn)

            self.table.setCellWidget(i, 7, btn_widget)
            self.table.setRowHeight(i, 40)

        self.records_value.setText(str(len(filtered)))
        self.income_value.setText(f"{base_symbol}{total_income:,.2f}")
        self.expense_value.setText(f"{base_symbol}{total_expense:,.2f}")
        self.net_value.setText(f"{base_symbol}{total_income - total_expense:,.2f}")

    def add_condition(self):
        dlg = QDialog(self)
        dlg.setWindowTitle("Add Filter Condition")
        dlg.setMinimumWidth(340)
        try:
            from main_window import DIALOG_STYLE
            dlg.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        layout = QFormLayout(dlg)
        layout.setLabelAlignment(Qt.AlignmentFlag.AlignRight)

        field_combo = QComboBox()
        field_combo.addItem("Account", "account")
        field_combo.addItem("Type", "type")
        field_combo.addItem("Category", "category")
        field_combo.addItem("Note contains", "note")
        field_combo.addItem("Date from", "date_from")
        field_combo.addItem("Date to", "date_to")
        field_combo.addItem("Amount min", "amount_min")
        field_combo.addItem("Amount max", "amount_max")
        layout.addRow("Field:", field_combo)

        holder = QWidget()
        hlayout = QHBoxLayout(holder)
        hlayout.setContentsMargins(0, 0, 0, 0)
        value_ref = {}

        def build_value(field):
            while hlayout.count():
                w = hlayout.takeAt(0).widget()
                if w is not None:
                    w.deleteLater()
            if field == "account":
                w = QComboBox()
                for a in self.db.get_accounts():
                    w.addItem(f"{a.name} ({a.currency})", a.id)
            elif field == "type":
                w = QComboBox()
                w.addItem("Income", "income")
                w.addItem("Expense", "expense")
            elif field == "category":
                w = QComboBox()
                for c in self.db.get_categories("expense") + self.db.get_categories("income"):
                    w.addItem(f"{c.icon} {c.name}", c.id)
            elif field == "note":
                w = QLineEdit()
                w.setPlaceholderText("text to match...")
            elif field in ("date_from", "date_to"):
                w = QDateEdit()
                w.setCalendarPopup(True)
                w.setDate(QDate.currentDate())
            else:
                w = QDoubleSpinBox()
                w.setRange(0, 999999999)
                w.setDecimals(2)
            w.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
            hlayout.addWidget(w)
            value_ref["widget"] = w
            value_ref["field"] = field

        field_combo.currentIndexChanged.connect(lambda _i: build_value(field_combo.currentData()))
        build_value(field_combo.currentData())
        layout.addRow("Value:", holder)

        btns = QHBoxLayout()
        ok = QPushButton("Add")
        ok.clicked.connect(dlg.accept)
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(dlg.reject)
        btns.addWidget(ok)
        btns.addWidget(cancel)
        layout.addRow(btns)

        if not dlg.exec():
            return
        f = field_combo.currentData()
        w = value_ref["widget"]
        if f == "account":
            val = w.currentData()
            if val is None:
                return
            display = f"Account = {w.currentText()}"
        elif f == "type":
            val = w.currentData()
            display = f"Type = {w.currentText()}"
        elif f == "category":
            val = w.currentData()
            if val is None:
                return
            display = f"Category = {w.currentText()}"
        elif f == "note":
            val = w.text().strip()
            if not val:
                return
            display = f"Note ~ {val}"
        elif f in ("date_from", "date_to"):
            val = w.date().toString("yyyy-MM-dd")
            display = f"Date {'≥' if f == 'date_from' else '≤'} {val}"
        else:
            val = w.value()
            display = f"Amount {'≥' if f == 'amount_min' else '≤'} {val:,.2f}"

        self.conditions.append({"field": f, "value": val, "display": display})
        self._render_chips()
        self.refresh()

    def _render_chips(self):
        while self.chips_layout.count():
            item = self.chips_layout.takeAt(0)
            w = item.widget()
            if w is not None:
                w.deleteLater()
        if not self.conditions:
            empty = QLabel("No filters applied")
            _set_cjk_font(empty)
            empty.setStyleSheet("font-size: 11px; color: #bdc3c7;")
            self.chips_layout.addWidget(empty)
            return
        for idx, cond in enumerate(self.conditions):
            chip = QWidget()
            cl = QHBoxLayout(chip)
            cl.setContentsMargins(8, 3, 8, 3)
            cl.setSpacing(4)
            chip.setStyleSheet("background-color: #ecf0f1; border-radius: 10px;")
            lbl = QLabel(cond["display"])
            _set_cjk_font(lbl)
            lbl.setStyleSheet("font-size: 11px; color: #2c3e50;")
            cl.addWidget(lbl)
            x = QPushButton("✕")
            x.setFixedSize(16, 16)
            x.setStyleSheet(
                "QPushButton { background-color: #bdc3c7; color: white; border: none;"
                " border-radius: 8px; font-size: 10px; }"
                " QPushButton:hover { background-color: #e74c3c; }"
            )
            x.clicked.connect(lambda _, i=idx: self.remove_condition(i))
            cl.addWidget(x)
            self.chips_layout.addWidget(chip)
        self.chips_layout.addStretch()

    def remove_condition(self, index):
        if 0 <= index < len(self.conditions):
            self.conditions.pop(index)
        self._render_chips()
        self.refresh()

    def clear_conditions(self):
        self.conditions = []
        self._render_chips()
        self.refresh()

    def add_transaction(self):
        dialog = TransactionDialog(self, self.db)
        if dialog.exec():
            transaction = dialog.get_transaction()
            self.db.add_transaction(transaction)
            self.refresh()

    def edit_transaction(self, transaction):
        if isinstance(transaction, dict):
            transaction = Transaction(
                id=transaction.get("id"),
                account_id=transaction.get("account_id", 0),
                category_id=transaction.get("category_id", 0),
                amount=transaction.get("amount", 0),
                type=transaction.get("type", "expense"),
                description=transaction.get("description", ""),
                date=transaction.get("date", ""),
                currency=transaction.get("currency", ""),
                exchange_rate=transaction.get("exchange_rate"),
            )
        dialog = TransactionDialog(self, self.db, transaction)
        if dialog.exec():
            updated = dialog.get_transaction()
            self.db.update_transaction(updated)
            self.refresh()

    def delete_transaction(self, transaction_id):
        reply = QMessageBox.question(
            self, "Confirm Delete", "Are you sure you want to delete this record?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.db.delete_transaction(transaction_id)
            self.refresh()



