import os
import csv
import sqlite3

from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QComboBox, QFrame, QGroupBox, QFormLayout, QMessageBox,
    QScrollArea, QTableWidget, QTableWidgetItem, QHeaderView,
    QDialog, QLineEdit, QColorDialog, QInputDialog, QListWidget, QListWidgetItem,
    QFileDialog, QAbstractItemView, QCheckBox
)
from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QColor

from database import Category
from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_sort_filter
from db_crypto import (
    derive_key, gen_salt, make_verifier, save_meta,
    encrypt_file, enc_path, load_meta, secure_delete
)

CARD_STYLE = """
    QFrame {
        background-color: white;
        border-radius: 8px;
        border: none;
    }
"""

BTN_STYLE = """
    QPushButton {
        color: white;
        border: none;
        padding: 8px 16px;
        border-radius: 5px;
        font-size: 13px;
    }
"""


class CategoryDialog(QDialog):
    def __init__(self, parent=None, category: Category = None):
        super().__init__(parent)
        self.category = category
        self.setWindowTitle("Edit Category" if category else "Add Category")
        self.setMinimumWidth(360)
        try:
            from main_window import DIALOG_STYLE
            self.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        self.setup_ui()

    def setup_ui(self):
        from PySide6.QtWidgets import QFormLayout
        layout = QFormLayout(self)

        self.name_edit = QLineEdit()
        layout.addRow("Name:", self.name_edit)

        self.type_combo = QComboBox()
        self.type_combo.addItem("Expense", "expense")
        self.type_combo.addItem("Income", "income")
        layout.addRow("Type:", self.type_combo)

        self.icon_edit = QLineEdit()
        self.icon_edit.setPlaceholderText("Emoji e.g. 🍜")
        self.icon_edit.setMaxLength(4)
        layout.addRow("Icon:", self.icon_edit)

        if self.category:
            self.name_edit.setText(self.category.name)
            idx = self.type_combo.findData(self.category.type)
            if idx >= 0:
                self.type_combo.setCurrentIndex(idx)
            self.icon_edit.setText(self.category.icon)

        btn_row = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #3498db; }")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #95a5a6; }")
        cancel_btn.clicked.connect(self.reject)
        btn_row.addWidget(save_btn)
        btn_row.addWidget(cancel_btn)
        layout.addRow(btn_row)

    def get_category(self) -> Category:
        cat_type = self.type_combo.currentData()
        color = "#27ae60" if cat_type == "income" else "#e74c3c"
        return Category(
            id=self.category.id if self.category else None,
            name=self.name_edit.text().strip(),
            type=cat_type,
            icon=self.icon_edit.text().strip(),
            color=color,
        )


class ExportDialog(QDialog):
    def __init__(self, db, parent=None):
        super().__init__(parent)
        self.db = db
        self.setWindowTitle("Export Database to CSV")
        self.setMinimumWidth(440)
        try:
            from main_window import DIALOG_STYLE
            self.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(8)

        hint = QLabel("Choose which tables to export. Each selected table is written "
                      "to <table>.csv in the chosen folder (UTF-8, Excel-friendly).")
        _set_cjk_font(hint)
        hint.setWordWrap(True)
        hint.setStyleSheet("font-size: 12px; color: #7f8c8d;")
        layout.addWidget(hint)

        self.table_list = QListWidget()
        self.table_list.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.table_list.setStyleSheet("""
            QListWidget { border: 1px solid #ecf0f1; border-radius: 6px; padding: 4px; }
            QListWidget::item { padding: 4px; }
        """)
        for name in self._tables():
            it = QListWidgetItem(name)
            it.setFlags(it.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            it.setCheckState(Qt.CheckState.Checked)
            self.table_list.addItem(it)
        layout.addWidget(self.table_list)

        dir_row = QHBoxLayout()
        self.dir_edit = QLineEdit()
        self.dir_edit.setPlaceholderText("Output folder…")
        _set_cjk_font(self.dir_edit)
        dir_row.addWidget(self.dir_edit)
        browse_btn = QPushButton("Browse")
        browse_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #3498db; }")
        browse_btn.clicked.connect(self._browse)
        dir_row.addWidget(browse_btn)
        layout.addLayout(dir_row)

        self.status_label = QLabel("")
        _set_cjk_font(self.status_label)
        self.status_label.setStyleSheet("font-size: 12px; color: #7f8c8d;")
        layout.addWidget(self.status_label)

        btns = QHBoxLayout()
        export_btn = QPushButton("Export")
        export_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #27ae60; }")
        export_btn.clicked.connect(self._export)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #95a5a6; }")
        cancel_btn.clicked.connect(self.reject)
        btns.addStretch()
        btns.addWidget(export_btn)
        btns.addWidget(cancel_btn)
        layout.addLayout(btns)

    def _tables(self):
        conn = sqlite3.connect(self.db.db_path)
        cur = conn.cursor()
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        tables = [r[0] for r in cur.fetchall()]
        conn.close()
        return tables

    def _browse(self):
        d = QFileDialog.getExistingDirectory(self, "Select Output Folder")
        if d:
            self.dir_edit.setText(d)

    def _selected_tables(self):
        return [self.table_list.item(r).text() for r in range(self.table_list.count())
                if self.table_list.item(r).checkState() == Qt.CheckState.Checked]

    def _export(self):
        tables = self._selected_tables()
        out_dir = self.dir_edit.text().strip()
        if not tables:
            self.status_label.setText("Please select at least one table.")
            return
        if not out_dir or not os.path.isdir(out_dir):
            self.status_label.setText("Please choose a valid output folder.")
            return
        conn = sqlite3.connect(self.db.db_path)
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        written = 0
        for t in tables:
            try:
                cur.execute(f"SELECT * FROM {t}")
                rows = cur.fetchall()
                cols = [d[0] for d in cur.description]
                path = os.path.join(out_dir, f"{t}.csv")
                with open(path, "w", newline="", encoding="utf-8-sig") as f:
                    writer = csv.writer(f)
                    writer.writerow(cols)
                    for row in rows:
                        writer.writerow([row[c] for c in cols])
                written += 1
            except Exception as e:
                self.status_label.setText(f"Error exporting '{t}': {e}")
                conn.close()
                return
        conn.close()
        self.status_label.setText(f"Exported {written} table(s) to {out_dir}")
        self.accept()


class SettingsTab(QWidget):
    base_currency_changed = Signal(str)

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
        layout.setSpacing(8)

        header = QLabel("Settings")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        currency_group = QGroupBox("Base Currency")
        _set_cjk_font(currency_group)
        currency_group.setStyleSheet("""
            QGroupBox {
                font-size: 13px; font-weight: bold; color: #2c3e50;
                background-color: white;
                border-radius: 8px; margin-top: 8px; padding-top: 18px;
            }
            QGroupBox::title { subcontrol-origin: margin; left: 15px; padding: 0 5px; }
        """)
        currency_layout = QFormLayout(currency_group)

        self.base_currency_combo = QComboBox()
        _set_cjk_font(self.base_currency_combo)
        self.base_currency_combo.addItems(self.db.get_currencies())
        current_base = self.db.get_base_currency()
        index = self.base_currency_combo.findText(current_base)
        if index >= 0:
            self.base_currency_combo.setCurrentIndex(index)
        currency_layout.addRow("Currency:", self.base_currency_combo)

        layout.addWidget(currency_group)

        cur_mgmt = QGroupBox("Currency Management")
        _set_cjk_font(cur_mgmt)
        cur_mgmt.setStyleSheet("""
            QGroupBox {
                font-size: 13px; font-weight: bold; color: #2c3e50;
                background-color: white;
                border-radius: 8px; margin-top: 8px; padding-top: 18px;
            }
            QGroupBox::title { subcontrol-origin: margin; left: 15px; padding: 0 5px; }
        """)
        cm_layout = QVBoxLayout(cur_mgmt)

        self.currency_table = QTableWidget()
        _set_cjk_font(self.currency_table)
        self.currency_table.setColumnCount(3)
        self.currency_table.setHorizontalHeaderLabels(["Currency", "Rate (1 base =)", "Fetched"])
        self.currency_table.verticalHeader().setVisible(False)
        self.currency_table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.currency_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.currency_table.setMaximumHeight(200)
        self.currency_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.currency_table.setStyleSheet("""
            QTableWidget { border: 1px solid #eee; border-radius: 5px; gridline-color: #ecf0f1; }
            QHeaderView::section { background-color: #f8f9fa; padding: 4px; border: none; font-weight: bold; font-size: 12px; }
        """)
        enable_sort_filter(self.currency_table, "currency_table", enable_filter=False)
        cm_layout.addWidget(self.currency_table)

        cm_row = QHBoxLayout()
        self.new_currency_edit = QLineEdit()
        self.new_currency_edit.setPlaceholderText("e.g. HKD")
        self.new_currency_edit.setMaximumWidth(140)
        _set_cjk_font(self.new_currency_edit)
        cm_row.addWidget(self.new_currency_edit)
        add_cur_btn = QPushButton("+ Add")
        add_cur_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #27ae60; }")
        add_cur_btn.clicked.connect(self.add_currency)
        cm_row.addWidget(add_cur_btn)
        remove_cur_btn = QPushButton("Remove")
        remove_cur_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #e74c3c; }")
        remove_cur_btn.clicked.connect(self.remove_currency)
        cm_row.addWidget(remove_cur_btn)
        refresh_rates_btn = QPushButton("Refresh Rates")
        refresh_rates_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #3498db; }")
        refresh_rates_btn.clicked.connect(self.refresh_rates)
        cm_row.addWidget(refresh_rates_btn)
        cm_row.addStretch()
        cm_layout.addLayout(cm_row)
        layout.addWidget(cur_mgmt)

        cat_group = QGroupBox("Categories (Income / Expense)")
        _set_cjk_font(cat_group)
        cat_group.setStyleSheet("""
            QGroupBox {
                font-size: 13px; font-weight: bold; color: #2c3e50;
                background-color: white;
                border-radius: 8px; margin-top: 8px; padding-top: 18px;
            }
            QGroupBox::title { subcontrol-origin: margin; left: 15px; padding: 0 5px; }
        """)
        cat_layout = QVBoxLayout(cat_group)

        btn_row = QHBoxLayout()
        add_cat_btn = QPushButton("+ Add")
        add_cat_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #27ae60; }")
        add_cat_btn.clicked.connect(self.add_category)
        btn_row.addWidget(add_cat_btn)
        btn_row.addStretch()
        cat_layout.addLayout(btn_row)

        self.cat_table = QTableWidget()
        _set_cjk_font(self.cat_table)
        self.cat_table.setColumnCount(4)
        self.cat_table.setHorizontalHeaderLabels(["Icon", "Name", "Type", "Action"])
        self.cat_table.verticalHeader().setVisible(False)
        self.cat_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.cat_table.setSelectionMode(QTableWidget.SelectionMode.SingleSelection)
        self.cat_table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.cat_table.setMaximumHeight(260)
        self.cat_table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.ResizeToContents)
        self.cat_table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.cat_table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.cat_table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.cat_table.setStyleSheet("""
            QTableWidget { border: 1px solid #eee; border-radius: 5px; gridline-color: #ecf0f1; }
            QHeaderView::section { background-color: #f8f9fa; padding: 4px; border: none; font-weight: bold; font-size: 12px; }
        """)
        cat_layout.addWidget(self.cat_table)
        self._sort_col = None
        self._sort_order = Qt.SortOrder.AscendingOrder
        enable_sort_filter(self.cat_table, "categories_table",
                           action_col=self.cat_table.columnCount() - 1,
                           enable_filter=False, on_sort_change=self._apply_cat_sort)

        layout.addWidget(cat_group)

        enc_group = QGroupBox("Database Encryption (AES-256)")
        _set_cjk_font(enc_group)
        enc_group.setStyleSheet("""
            QGroupBox {
                font-size: 13px; font-weight: bold; color: #2c3e50;
                background-color: white;
                border-radius: 8px; margin-top: 8px; padding-top: 18px;
            }
            QGroupBox::title { subcontrol-origin: margin; left: 15px; padding: 0 5px; }
        """)
        enc_layout = QVBoxLayout(enc_group)

        self.enc_check = QCheckBox("Encrypt this database with a password")
        _set_cjk_font(self.enc_check)
        self.enc_check.setStyleSheet("font-size: 12px;")
        enc_layout.addWidget(self.enc_check)

        enc_form = QFormLayout()
        self.enc_pw = QLineEdit()
        self.enc_pw.setEchoMode(QLineEdit.EchoMode.Password)
        self.enc_pw.setPlaceholderText("Password")
        _set_cjk_font(self.enc_pw)
        self.enc_pw2 = QLineEdit()
        self.enc_pw2.setEchoMode(QLineEdit.EchoMode.Password)
        self.enc_pw2.setPlaceholderText("Confirm password")
        _set_cjk_font(self.enc_pw2)
        enc_form.addRow("Password:", self.enc_pw)
        enc_form.addRow("Confirm:", self.enc_pw2)
        enc_layout.addLayout(enc_form)

        self.enc_status = QLabel("")
        _set_cjk_font(self.enc_status)
        self.enc_status.setStyleSheet("font-size: 12px; color: #7f8c8d;")
        self.enc_status.setWordWrap(True)
        enc_layout.addWidget(self.enc_status)

        apply_enc_btn = QPushButton("Apply Encryption Settings")
        apply_enc_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #8e44ad; }")
        apply_enc_btn.clicked.connect(self.apply_encryption)
        enc_layout.addWidget(apply_enc_btn)

        enc_hint = QLabel(
            "When enabled, the database file is encrypted at rest with AES-256 "
            "(key derived from your password via PBKDF2). You will be asked for the "
            "password at the login screen. Changing the password re-encrypts the file."
        )
        _set_cjk_font(enc_hint)
        enc_hint.setWordWrap(True)
        enc_hint.setStyleSheet("font-size: 11px; color: #95a5a6;")
        enc_layout.addWidget(enc_hint)

        layout.addWidget(enc_group)

        data_group = QGroupBox("Data Management")
        _set_cjk_font(data_group)
        data_group.setStyleSheet("""
            QGroupBox {
                font-size: 13px; font-weight: bold; color: #2c3e50;
                background-color: white;
                border-radius: 8px; margin-top: 8px; padding-top: 18px;
            }
            QGroupBox::title { subcontrol-origin: margin; left: 15px; padding: 0 5px; }
        """)
        data_layout = QVBoxLayout(data_group)
        export_btn = QPushButton("Export to CSV")
        export_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #16a085; }")
        export_btn.clicked.connect(self.export_data)
        data_layout.addWidget(export_btn)
        clear_btn = QPushButton("Clear All Data")
        clear_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #c0392b; }")
        clear_btn.clicked.connect(self.clear_all_data)
        data_layout.addWidget(clear_btn)
        layout.addWidget(data_group)

        layout.addStretch()
        scroll.setWidget(content)
        outer.addWidget(scroll)

        self.base_currency_combo.currentTextChanged.connect(self.on_base_currency_changed)
        self.refresh_categories()
        self.refresh_currencies()
        self._refresh_enc_ui()

    def on_base_currency_changed(self, currency: str):
        self.db.set_base_currency(currency)
        self.base_currency_changed.emit(currency)
        self.refresh_currencies()

    def _refresh_enc_ui(self):
        meta = load_meta(self.db.db_path)
        enabled = bool(meta.get("encryption"))
        self.enc_check.setChecked(enabled)
        if enabled:
            self.enc_status.setText("Encryption is currently ENABLED.")
        else:
            self.enc_status.setText("Encryption is currently disabled.")

    def apply_encryption(self):
        enabled = self.enc_check.isChecked()
        if enabled:
            pw = self.enc_pw.text()
            if not pw:
                QMessageBox.warning(self, "Error", "Please enter a password.")
                return
            if pw != self.enc_pw2.text():
                QMessageBox.warning(self, "Error", "Passwords do not match.")
                return
            salt = gen_salt()
            key = derive_key(pw, salt)
            verifier = make_verifier(key)
            meta = {"encryption": True, "salt": salt.hex(), "verifier": verifier}
            save_meta(self.db.db_path, meta)
            encrypt_file(self.db.db_path, enc_path(self.db.db_path), key)
            self.db.crypto_key = key
            self.enc_status.setText("Encryption enabled. The database is now encrypted with AES-256.")
            QMessageBox.information(
                self, "Encryption",
                "Encryption enabled. From the next login you will need this password "
                "to open the database."
            )
        else:
            meta = load_meta(self.db.db_path)
            meta["encryption"] = False
            meta.pop("salt", None)
            meta.pop("verifier", None)
            save_meta(self.db.db_path, meta)
            ep = enc_path(self.db.db_path)
            if os.path.exists(ep):
                secure_delete(ep)
            self.db.crypto_key = None
            self.enc_status.setText("Encryption disabled. The database is stored unencrypted.")
            QMessageBox.information(
                self, "Encryption",
                "Encryption disabled. Data is now stored unencrypted."
            )
        self.enc_pw.clear()
        self.enc_pw2.clear()
        self._refresh_enc_ui()

    def refresh_currencies(self):
        curs = self.db.get_currencies()
        base = self.db.get_base_currency()
        self.currency_table.setSortingEnabled(False)
        self.currency_table.setRowCount(len(curs))
        try:
            rate_rows = self.converter.get_all_rates(base, curs)
            rates = {code: (rate, t) for code, rate, t in rate_rows}
        except Exception:
            rates = {}
        for r, code in enumerate(curs):
            self.currency_table.setItem(r, 0, QTableWidgetItem(code))
            if code == base:
                self.currency_table.setItem(r, 1, QTableWidgetItem("Base"))
                self.currency_table.setItem(r, 2, QTableWidgetItem("\u2014"))
            else:
                rate, t = rates.get(code, (1.0, None))
                self.currency_table.setItem(r, 1, QTableWidgetItem(f"{rate:.4f} {code}"))
                fetched = t.strftime("%Y-%m-%d %H:%M") if t else "\u2014"
                self.currency_table.setItem(r, 2, QTableWidgetItem(fetched))
        self.currency_table.setSortingEnabled(True)

        self.base_currency_combo.blockSignals(True)
        self.base_currency_combo.clear()
        self.base_currency_combo.addItems(curs)
        cur = self.db.get_base_currency()
        idx = self.base_currency_combo.findText(cur)
        if idx >= 0:
            self.base_currency_combo.setCurrentIndex(idx)
        self.base_currency_combo.blockSignals(False)

    def add_currency(self):
        code = self.new_currency_edit.text().strip().upper()
        if not code:
            return
        if code in self.db.get_currencies():
            QMessageBox.information(self, "Info", f"'{code}' is already in the list.")
            return
        if not self.converter.validate_currency(code):
            QMessageBox.warning(
                self, "Invalid Currency",
                f"'{code}' is not a recognized currency (could not verify it via yfinance)."
            )
            return
        self.db.add_currency(code)
        self.new_currency_edit.clear()
        self.refresh_currencies()

    def remove_currency(self):
        row = self.currency_table.currentRow()
        if row < 0:
            QMessageBox.information(self, "Info", "Please select a currency to remove.")
            return
        code_item = self.currency_table.item(row, 0)
        if not code_item:
            return
        code = code_item.text()
        if code == self.db.get_base_currency():
            QMessageBox.warning(self, "Error", "Cannot remove the base currency.")
            return
        self.db.remove_currency(code)
        self.refresh_currencies()

    def refresh_rates(self):
        self.converter.refresh_rates()
        self.refresh_currencies()

    def export_data(self):
        dlg = ExportDialog(self.db, self)
        dlg.exec()

    def clear_all_data(self):
        reply = QMessageBox.warning(
            self, "Confirm Clear All",
            "This will permanently delete ALL data (accounts, transactions, "
            "investments, debts and history).\nSettings (base currency and "
            "currency list) are kept.\n\nContinue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply != QMessageBox.StandardButton.Yes:
            return
        self.db.clear_all()
        self.refresh_currencies()
        self.refresh_categories()
        win = self.window()
        if hasattr(win, "refresh_all_tabs"):
            win.refresh_all_tabs()

    def _sort_categories(self, categories):
        col = self._sort_col
        if col is None or not categories:
            return categories
        if col == 0:
            key = lambda c: (c.icon or "")
        elif col == 1:
            key = lambda c: c.name.lower()
        elif col == 2:
            key = lambda c: c.type
        else:
            return categories
        return sorted(categories, key=key, reverse=(self._sort_order == Qt.SortOrder.DescendingOrder))

    def _apply_cat_sort(self, col, order):
        self._sort_col = col
        self._sort_order = order
        self.refresh_categories()

    def refresh_categories(self):
        categories = self._sort_categories(self.db.get_categories())
        self.cat_table.setRowCount(len(categories))
        for i, cat in enumerate(categories):
            icon_item = QTableWidgetItem(cat.icon)
            icon_item.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            if cat.id is not None:
                icon_item.setData(Qt.ItemDataRole.UserRole, cat.id)
            self.cat_table.setItem(i, 0, icon_item)
            self.cat_table.setItem(i, 1, QTableWidgetItem(cat.name))
            type_item = QTableWidgetItem("Income" if cat.type == "income" else "Expense")
            type_item.setForeground(QColor("#27ae60") if cat.type == "income" else QColor("#e74c3c"))
            self.cat_table.setItem(i, 2, type_item)

            action_widget = QWidget()
            action_layout = QHBoxLayout(action_widget)
            action_layout.setContentsMargins(2, 2, 2, 2)
            action_layout.setSpacing(4)
            cid = cat.id
            edit_btn = QPushButton("Edit")
            edit_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #f39c12; padding: 3px 8px; font-size: 11px; }")
            del_btn = QPushButton("Delete")
            del_btn.setStyleSheet(BTN_STYLE + "QPushButton { background-color: #e74c3c; padding: 3px 8px; font-size: 11px; }")
            edit_btn.clicked.connect(lambda checked=False, cid=cid: self._edit_category_by_id(cid))
            del_btn.clicked.connect(lambda checked=False, cid=cid: self._delete_category_by_id(cid))
            action_layout.addWidget(edit_btn)
            action_layout.addWidget(del_btn)
            self.cat_table.setCellWidget(i, 3, action_widget)

    def add_category(self):
        dialog = CategoryDialog(self)
        if dialog.exec():
            cat = dialog.get_category()
            if not cat.name:
                QMessageBox.warning(self, "Error", "Name cannot be empty")
                return
            self.db.add_category(cat)
            self.refresh_categories()

    def _edit_category_by_id(self, category_id):
        cat = self.db.get_category_by_id(category_id)
        if not cat:
            return
        dialog = CategoryDialog(self, cat)
        if dialog.exec():
            updated = dialog.get_category()
            if not updated.name:
                QMessageBox.warning(self, "Error", "Name cannot be empty")
                return
            self.db.update_category(updated)
            self.refresh_categories()

    def _delete_category_by_id(self, category_id):
        cat = self.db.get_category_by_id(category_id)
        if not cat:
            return
        reply = QMessageBox.question(
            self, "Confirm Delete",
            f"Delete category '{cat.name}'?\nExisting transactions will keep a blank category.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.db.delete_category(category_id)
            self.refresh_categories()
