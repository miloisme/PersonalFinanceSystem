from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QComboBox, QDoubleSpinBox, QDateEdit,
    QMessageBox, QFrame, QProgressBar, QCheckBox, QSizePolicy
)
from PySide6.QtWidgets import QScrollArea
from PySide6.QtCore import Qt, QDate
from PySide6.QtGui import QColor
import matplotlib
matplotlib.use('QtAgg')
matplotlib.rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Microsoft YaHei', 'Segoe UI', 'DejaVu Sans']
matplotlib.rcParams['font.family'] = 'sans-serif'
matplotlib.rcParams['axes.unicode_minus'] = False
from matplotlib.backends.backend_qt5agg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
import matplotlib.patheffects as pe

from database import Debt
from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_sort_filter
from chart_tooltips import enable_pie_hover


class DebtDialog(QDialog):
    def __init__(self, parent=None, debt: Debt = None, party: str = None):
        super().__init__(parent)
        self.debt = debt
        self.db = parent.db if parent is not None else None
        if debt is not None:
            self._party = debt.debtor or "me"
        elif party in ("me", "other"):
            self._party = party
        else:
            self._party = "me"
        self.converter = CurrencyConverter()
        if debt is not None:
            title = "Edit " + ("Credit" if self._party == "other" else "Debt")
        else:
            title = "Add " + ("Credit" if self._party == "other" else "Debt")
        self.setWindowTitle(title)
        self.setMinimumWidth(450)
        try:
            from main_window import DIALOG_STYLE
            self.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        self.setup_ui()

    def setup_ui(self):
        layout = QFormLayout(self)

        name_label = "Creditor:" if self._party == "me" else "Debtor:"
        self.name_label = name_label
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText(
            "e.g. Bank, Friend" if self._party == "me" else "e.g. John, Company"
        )
        layout.addRow(name_label, self.name_edit)

        if self._party == "me":
            party_note = "You are the debtor (I owe this)."
        else:
            party_note = "You are the creditor (owed to you)."
        party_label = QLabel(party_note)
        party_label.setStyleSheet("font-size: 11px; color: #7f8c8d;")
        layout.addRow(party_label)

        self.total_amount_spin = QDoubleSpinBox()
        self.total_amount_spin.setRange(0, 999999999)
        self.total_amount_spin.setDecimals(2)
        layout.addRow("Total Amount:", self.total_amount_spin)

        self.paid_amount_spin = QDoubleSpinBox()
        self.paid_amount_spin.setRange(0, 999999999)
        self.paid_amount_spin.setDecimals(2)
        layout.addRow("Paid Amount:", self.paid_amount_spin)

        self.interest_rate_spin = QDoubleSpinBox()
        self.interest_rate_spin.setRange(0, 100)
        self.interest_rate_spin.setDecimals(2)
        self.interest_rate_spin.setSuffix("%")
        layout.addRow("Interest Rate:", self.interest_rate_spin)

        self.currency_combo = QComboBox()
        currencies = self.db.get_currencies()
        self.currency_combo.addItems(currencies)
        base_cur = self.db.get_base_currency()
        bi = self.currency_combo.findText(base_cur)
        if bi >= 0:
            self.currency_combo.setCurrentIndex(bi)
        layout.addRow("Currency:", self.currency_combo)

        self.start_date_edit = QDateEdit()
        self.start_date_edit.setCalendarPopup(True)
        self.start_date_edit.setDate(QDate.currentDate())
        layout.addRow("Start Date:", self.start_date_edit)

        self.due_check = QCheckBox("Set due date")
        self.due_check.setStyleSheet("font-size: 12px;")
        layout.addRow(self.due_check)
        self.due_date_edit = QDateEdit()
        self.due_date_edit.setCalendarPopup(True)
        self.due_date_edit.setDate(QDate.currentDate().addYears(1))
        self.due_date_edit.setEnabled(False)
        self.due_check.toggled.connect(lambda on: self.due_date_edit.setEnabled(on))
        layout.addRow("Due Date:", self.due_date_edit)

        self.completed_check = QCheckBox("Completed (excluded from totals)")
        layout.addRow(self.completed_check)

        self.note_edit = QLineEdit()
        self.note_edit.setPlaceholderText("optional note...")
        layout.addRow("Note:", self.note_edit)

        if self.debt:
            self.name_edit.setText(self.debt.name)
            self.total_amount_spin.setValue(self.debt.total_amount)
            self.paid_amount_spin.setValue(self.debt.paid_amount)
            self.interest_rate_spin.setValue(self.debt.interest_rate)
            curr_index = self.currency_combo.findText(self.debt.currency)
            if curr_index >= 0:
                self.currency_combo.setCurrentIndex(curr_index)
            if self.debt.start_date:
                self.start_date_edit.setDate(QDate.fromString(self.debt.start_date, "yyyy-MM-dd"))
            if self.debt.due_date:
                self.due_check.setChecked(True)
                self.due_date_edit.setEnabled(True)
                self.due_date_edit.setDate(QDate.fromString(self.debt.due_date, "yyyy-MM-dd"))
            else:
                self.due_check.setChecked(False)
                self.due_date_edit.setEnabled(False)
            self.completed_check.setChecked(bool(self.debt.completed))
            self.note_edit.setText(self.debt.note or "")

        button_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(save_btn)
        button_layout.addWidget(cancel_btn)
        layout.addRow(button_layout)

    def get_debt(self) -> Debt:
        due_date = self.due_date_edit.date().toString("yyyy-MM-dd") if self.due_check.isChecked() else ""
        return Debt(
            id=self.debt.id if self.debt else None,
            name=self.name_edit.text(),
            total_amount=self.total_amount_spin.value(),
            paid_amount=self.paid_amount_spin.value(),
            interest_rate=self.interest_rate_spin.value(),
            currency=self.currency_combo.currentText(),
            start_date=self.start_date_edit.date().toString("yyyy-MM-dd"),
            due_date=due_date,
            completed=self.completed_check.isChecked(),
            debtor=self._party,
            note=self.note_edit.text().strip()
        )


class DebtsTab(QWidget):
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

        header = QLabel("Debts & Credits")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        top_row = QHBoxLayout()
        top_row.setSpacing(8)

        card_style = """
            QFrame {
                background-color: white;
                border-radius: 10px;
                padding: 15px;
            }
        """

        # Card A (left): summary stats (stacked vertically)
        stats_frame = QFrame()
        stats_frame.setStyleSheet("""
            QFrame {
                background-color: white;
                border-radius: 10px;
                padding: 8px 12px;
            }
        """)
        stats_frame.setMinimumHeight(230)
        stats_layout = QVBoxLayout(stats_frame)
        stats_layout.setContentsMargins(0, 0, 0, 0)
        stats_layout.setSpacing(16)

        self.total_debt_label = QLabel("Debt (I owe)")
        _set_cjk_font(self.total_debt_label)
        self.total_debt_label.setStyleSheet("font-size: 11px; color: #7f8c8d;")
        self.total_debt_value = QLabel("HK$ 0.00")
        _set_cjk_font(self.total_debt_value)
        self.total_debt_value.setStyleSheet("font-size: 17px; font-weight: bold; color: #e74c3c;")
        self.total_debt_value.setMinimumHeight(26)
        self.total_debt_value.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        total_debt_container = QVBoxLayout()
        total_debt_container.setSpacing(0)
        total_debt_container.addWidget(self.total_debt_label)
        total_debt_container.addWidget(self.total_debt_value)
        stats_layout.addLayout(total_debt_container)

        remaining_container = QVBoxLayout()
        remaining_container.setSpacing(0)
        remaining_label = QLabel("Remaining (I owe)")
        _set_cjk_font(remaining_label)
        remaining_label.setStyleSheet("font-size: 11px; color: #7f8c8d;")
        self.remaining_value = QLabel("HK$ 0.00")
        _set_cjk_font(self.remaining_value)
        self.remaining_value.setStyleSheet("font-size: 17px; font-weight: bold; color: #f39c12;")
        self.remaining_value.setMinimumHeight(26)
        self.remaining_value.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        remaining_container.addWidget(remaining_label)
        remaining_container.addWidget(self.remaining_value)
        stats_layout.addLayout(remaining_container)

        receivable_container = QVBoxLayout()
        receivable_container.setSpacing(0)
        receivable_label = QLabel("Receivable (owed to me)")
        _set_cjk_font(receivable_label)
        receivable_label.setStyleSheet("font-size: 11px; color: #7f8c8d;")
        self.receivable_value = QLabel("HK$ 0.00")
        _set_cjk_font(self.receivable_value)
        self.receivable_value.setStyleSheet("font-size: 17px; font-weight: bold; color: #27ae60;")
        self.receivable_value.setMinimumHeight(26)
        self.receivable_value.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        receivable_container.addWidget(receivable_label)
        receivable_container.addWidget(self.receivable_value)
        stats_layout.addLayout(receivable_container)

        top_row.addWidget(stats_frame, 1)

        # Card B (middle): debt composition pie
        owed_card = QFrame()
        owed_card.setStyleSheet("""
            QFrame { background-color: white; border-radius: 10px; padding: 4px; }
        """)
        owed_card.setFixedHeight(230)
        owed_layout = QVBoxLayout(owed_card)
        owed_layout.setContentsMargins(0, 0, 0, 0)
        owed_layout.setSpacing(2)
        owed_title = QLabel("Debt Composition")
        _set_cjk_font(owed_title)
        owed_title.setStyleSheet("font-size: 12px; font-weight: bold; color: #2c3e50;")
        owed_layout.addWidget(owed_title)
        self.owed_canvas = FigureCanvas(Figure(figsize=(3.2, 2.2)))
        self.owed_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        owed_layout.addWidget(self.owed_canvas, 1)
        top_row.addWidget(owed_card, 1)

        # Card C (right): credit composition pie
        owing_card = QFrame()
        owing_card.setStyleSheet("""
            QFrame { background-color: white; border-radius: 10px; padding: 4px; }
        """)
        owing_card.setFixedHeight(230)
        owing_layout = QVBoxLayout(owing_card)
        owing_layout.setContentsMargins(0, 0, 0, 0)
        owing_layout.setSpacing(2)
        owing_title = QLabel("Credit Composition")
        _set_cjk_font(owing_title)
        owing_title.setStyleSheet("font-size: 12px; font-weight: bold; color: #2c3e50;")
        owing_layout.addWidget(owing_title)
        self.owing_canvas = FigureCanvas(Figure(figsize=(3.2, 2.2)))
        self.owing_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        owing_layout.addWidget(self.owing_canvas, 1)
        top_row.addWidget(owing_card, 1)

        layout.addLayout(top_row)

        btn_layout = QHBoxLayout()
        add_debt_btn = QPushButton("+ Add Debt")
        add_debt_btn.setStyleSheet("""
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
        add_debt_btn.clicked.connect(lambda: self.add_debt("me"))
        btn_layout.addWidget(add_debt_btn)

        add_credit_btn = QPushButton("+ Add Credit")
        add_credit_btn.setStyleSheet("""
            QPushButton {
                background-color: #27ae60;
                color: white;
                border: none;
                padding: 7px 16px;
                border-radius: 5px;
                font-size: 13px;
            }
            QPushButton:hover {
                background-color: #1e8449;
            }
        """)
        add_credit_btn.clicked.connect(lambda: self.add_debt("other"))
        btn_layout.addWidget(add_credit_btn)

        btn_layout.addStretch()
        layout.addLayout(btn_layout)

        self.table = QTableWidget()
        self.table.setColumnCount(10)
        self.table.setHorizontalHeaderLabels([
            "Name", "Total", "Paid", "Remaining", "Remaining (Base)", "Rate", "Due Date", "Note", "Progress", "Actions"
        ])
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
        enable_sort_filter(self.table, "debts_table",
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

    def _sort_debts(self, debts, base_currency):
        col = self._sort_col
        if col is None or not debts:
            return debts
        if col == 0:
            key = lambda d: d.name
        elif col == 1:
            key = lambda d: d.total_amount
        elif col == 2:
            key = lambda d: d.paid_amount
        elif col == 3:
            key = lambda d: d.total_amount - d.paid_amount
        elif col == 4:
            key = lambda d: self.converter.convert(d.total_amount - d.paid_amount, d.currency, base_currency)
        elif col == 5:
            key = lambda d: d.interest_rate
        elif col == 6:
            key = lambda d: d.due_date or ""
        elif col == 7:
            key = lambda d: d.note or ""
        else:
            return debts
        return sorted(debts, key=key, reverse=(self._sort_order == Qt.SortOrder.DescendingOrder))

    def _apply_sort(self, col, order):
        self._sort_col = col
        self._sort_order = order
        self.refresh()

    def refresh(self):
        base_currency = self.db.get_base_currency()
        base_symbol = self._get_currency_symbol(base_currency)

        debts = self._sort_debts(self.db.get_debts(), base_currency)
        self.table.setSortingEnabled(False)
        self.table.setRowCount(len(debts))

        me_debt = 0.0
        me_paid = 0.0
        other_receivable = 0.0
        me_owed = []
        other_owed = []
        today = QDate.currentDate()

        for i, debt in enumerate(debts):
            done = bool(getattr(debt, "completed", False))
            gray = QColor("#bdc3c7") if done else None
            party = getattr(debt, "debtor", "me")

            name_item = QTableWidgetItem(("? " + debt.name) if done else debt.name)
            self.table.setItem(i, 0, name_item)

            debt_symbol = self._get_currency_symbol(debt.currency)
            self.table.setItem(i, 1, QTableWidgetItem(f"{debt_symbol}{debt.total_amount:,.2f}"))
            self.table.setItem(i, 2, QTableWidgetItem(f"{debt_symbol}{debt.paid_amount:,.2f}"))

            remaining = debt.total_amount - debt.paid_amount
            remaining_item = QTableWidgetItem(f"{debt_symbol}{remaining:,.2f}")
            if not done:
                if party == "me":
                    remaining_item.setForeground(QColor("#e74c3c"))
                else:
                    remaining_item.setForeground(QColor("#27ae60"))
            self.table.setItem(i, 3, remaining_item)

            converted_remaining = self.converter.convert(remaining, debt.currency, base_currency)
            conv_item = QTableWidgetItem(f"{base_symbol}{converted_remaining:,.2f}")
            if not done:
                if party == "me":
                    conv_item.setForeground(QColor("#e74c3c"))
                else:
                    conv_item.setForeground(QColor("#27ae60"))
            self.table.setItem(i, 4, conv_item)

            self.table.setItem(i, 5, QTableWidgetItem(f"{debt.interest_rate:.2f}%"))

            due_item = QTableWidgetItem(debt.due_date or "--")
            if not done and debt.due_date:
                try:
                    if QDate.fromString(debt.due_date, "yyyy-MM-dd") < today:
                        due_item.setForeground(QColor("#e74c3c"))
                except Exception:
                    pass
            self.table.setItem(i, 6, due_item)

            note_item = QTableWidgetItem(debt.note or "")
            note_item.setToolTip(debt.note or "")
            self.table.setItem(i, 7, note_item)

            progress_widget = QWidget()
            progress_layout = QVBoxLayout(progress_widget)
            progress_layout.setContentsMargins(10, 5, 10, 5)
            progress_layout.setAlignment(Qt.AlignmentFlag.AlignVCenter)

            progress_bar = QProgressBar()
            progress_bar.setRange(0, 100)
            if done:
                progress = 100
            elif debt.total_amount > 0:
                progress = int((debt.paid_amount / debt.total_amount) * 100)
            else:
                progress = 0
            progress_bar.setValue(progress)
            progress_bar.setTextVisible(True)
            progress_bar.setFormat(f"{progress}%")
            if done:
                chunk = "#bdc3c7"
            elif progress >= 100:
                chunk = "#27ae60"
            else:
                chunk = "#3498db"
            progress_bar.setStyleSheet(f"""
                QProgressBar {{
                    border: none;
                    background-color: #ecf0f1;
                    border-radius: 8px;
                    height: 16px;
                    text-align: center;
                }}
                QProgressBar::chunk {{
                    background-color: {chunk};
                    border-radius: 8px;
                }}
            """)
            progress_layout.addWidget(progress_bar)

            self.table.setCellWidget(i, 8, progress_widget)

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
            edit_btn.clicked.connect(lambda _, d=debt: self.edit_debt(d))
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
            delete_btn.clicked.connect(lambda _, d_id=debt.id: self.delete_debt(d_id))
            btn_layout.addWidget(delete_btn)

            self.table.setCellWidget(i, 9, btn_widget)
            self.table.setRowHeight(i, 44)

            if gray is not None:
                for c in range(self.table.columnCount()):
                    item = self.table.item(i, c)
                    if item is not None:
                        item.setForeground(gray)

            if not done:
                remaining = debt.total_amount - debt.paid_amount
                converted = self.converter.convert(remaining, debt.currency, base_currency)
                if party == "other":
                    other_receivable += converted
                    other_owed.append((debt.name, converted))
                else:
                    me_debt += self.converter.convert(debt.total_amount, debt.currency, base_currency)
                    me_paid += self.converter.convert(debt.paid_amount, debt.currency, base_currency)
                    me_owed.append((debt.name, converted))

        self.total_debt_value.setText(f"{base_symbol}{me_debt:,.2f}")
        self.receivable_value.setText(f"{base_symbol}{other_receivable:,.2f}")
        self.remaining_value.setText(f"{base_symbol}{me_debt - me_paid:,.2f}")

        # Merge entries that share the same name (e.g. several debts to one party)
        def _agg(entries):
            merged = {}
            for nm, amt in entries:
                merged[nm] = merged.get(nm, 0.0) + amt
            return list(merged.items())
        me_owed = _agg(me_owed)
        other_owed = _agg(other_owed)

        me_total = sum(a for _, a in me_owed)
        other_total = sum(a for _, a in other_owed)
        self._draw_composition_pie(self.owed_canvas, me_owed, me_total,
                                   ["#e74c3c", "#f39c12", "#3498db", "#9b59b6", "#1abc9c", "#95a5a6"],
                                   unit=base_symbol)
        self._draw_composition_pie(self.owing_canvas, other_owed, other_total,
                                   ["#27ae60", "#16a085", "#2980b9", "#8e44ad", "#d35400", "#7f8c8d"],
                                   unit=base_symbol)

    def _draw_composition_pie(self, canvas, entries, total, palette, unit=""):
        canvas.figure.clear()
        ax = canvas.figure.add_subplot(111)
        ax.set_axis_off()
        if not entries or total <= 0:
            ax.text(0.5, 0.5, "No data", ha="center", va="center", fontsize=11, color="#bdc3c7")
            canvas.draw()
            return
        ranked = sorted(entries, key=lambda x: -x[1])
        top = ranked[:5]
        rest = sum(a for _, a in ranked[5:])
        names = [n for n, _ in top]
        sizes = [a for _, a in top]
        if rest > 0:
            names.append("Other")
            sizes.append(rest)
        # Outside labels: name + amount; inside: percentage (only for slices >= 5%)
        wedge_labels = []
        for nm, a in zip(names, sizes):
            pct = (a / total * 100) if total else 0
            wedge_labels.append(f"{nm}  {unit}{a:,.0f}" if pct >= 5.0 else "")
        colors = palette[:len(sizes)]
        wedges, texts, autotexts = ax.pie(
            sizes, colors=colors, startangle=90, radius=0.82,
            labels=wedge_labels, labeldistance=1.04,
            autopct=lambda p: f"{p:.1f}%" if p >= 5.0 else "", pctdistance=0.52,
            textprops={"fontsize": 8, "color": "#2c3e50"},
            wedgeprops={"linewidth": 0.5, "edgecolor": "white"},
        )
        # percentage text inside, drawn with a dark outline so it stays
        # readable on any wedge color
        for t in autotexts:
            t.set_color("white")
            t.set_fontsize(8)
            t.set_path_effects([pe.withStroke(linewidth=2, foreground="#2c3e50")])
        ax.set_aspect("equal")
        ax.set_position([0.0, 0.0, 1.0, 1.0])
        enable_pie_hover(canvas.figure, ax, wedges, names, sizes, total, unit)
        canvas.draw()

    def add_debt(self, party="me"):
        dialog = DebtDialog(self, party=party)
        if dialog.exec():
            debt = dialog.get_debt()
            self.db.add_debt(debt)
            self.refresh()

    def edit_debt(self, debt: Debt):
        dialog = DebtDialog(self, debt)
        if dialog.exec():
            updated_debt = dialog.get_debt()
            self.db.update_debt(updated_debt)
            self.refresh()

    def delete_debt(self, debt_id: int):
        reply = QMessageBox.question(
            self, "Confirm Delete", "Are you sure you want to delete this debt?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.db.delete_debt(debt_id)
            self.refresh()



