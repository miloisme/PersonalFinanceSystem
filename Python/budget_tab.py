from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QFrame,
    QTableWidget, QTableWidgetItem, QHeaderView, QListWidget, QListWidgetItem,
    QDialog, QFormLayout, QLineEdit, QDoubleSpinBox, QScrollArea, QMessageBox,
    QSizePolicy, QAbstractItemView
)
from PySide6.QtCore import Qt
from PySide6.QtGui import QColor

from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
import matplotlib
matplotlib.use('QtAgg')
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter
from matplotlib.patches import Patch
from matplotlib.font_manager import FontProperties

from datetime import datetime
from calendar import monthrange

from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_sort_filter
from chart_tooltips import enable_line_hover

_cjk_font = FontProperties(family=["Microsoft JhengHei", "Microsoft YaHei", "Segoe UI"])
matplotlib.rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Microsoft YaHei', 'Segoe UI', 'DejaVu Sans']
matplotlib.rcParams['font.family'] = 'sans-serif'
matplotlib.rcParams['axes.unicode_minus'] = False

COLOR_ASSETS = "#1f3a93"
COLOR_LIABILITIES = "#d35400"
COLOR_BALANCE = "#8e44ad"
COLOR_BALANCE_FC = "#bb8fce"
COLOR_BAL_POS = "#a9dfbf"
COLOR_BAL_NEG = "#f5b7b1"

_FC_FIELDS = {1: "assets", 2: "liabilities", 3: "income", 4: "expense"}
_ROW_H = 30
RECEIVABLE_ID = -1


class MplCanvas(FigureCanvas):
    def resizeEvent(self, event):
        w = event.size().width()
        h = event.size().height()
        if w > 0 and h > 0 and self.figure is not None:
            self.figure.set_size_inches(w / self.figure.dpi, h / self.figure.dpi)
        super().resizeEvent(event)


def _add_months(y, m, delta):
    idx = (m - 1) + delta
    y += idx // 12
    m = idx % 12 + 1
    if y < 0:
        y = 0
    return y, m


def _currency_symbol(currency):
    symbols = {
        "CNY": "¥", "HKD": "HK$", "USD": "$", "EUR": "€",
        "GBP": "£", "JPY": "¥", "TWD": "NT$", "KRW": "₩",
        "SGD": "S$", "AUD": "A$", "CAD": "C$"
    }
    return symbols.get(currency, currency + " ")


class BudgetTab(QWidget):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.converter = CurrencyConverter()
        self._filling = False
        self._fund_sort_col = None
        self._fund_sort_order = Qt.SortOrder.AscendingOrder
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
        layout.setSpacing(10)

        header = QLabel("Budget")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        # Top row: Select Accounts + Earmarked side by side
        top = QHBoxLayout()
        top.setSpacing(10)
        top.addWidget(self._card(self._build_select_accounts()), 1)
        top.addWidget(self._card(self._build_earmarked()), 1)
        layout.addLayout(top)

        layout.addWidget(self._build_summary_section())
        layout.addWidget(self._card(self._build_forecast_section()))

        scroll.setWidget(content)
        outer.addWidget(scroll)
        self.refresh()

    # ---------- helpers ----------
    def _card(self, widget):
        card = QFrame()
        card.setStyleSheet("QFrame { background-color: white; border-radius: 10px; border: none; }")
        v = QVBoxLayout(card)
        v.setContentsMargins(12, 10, 12, 10)
        v.setSpacing(8)
        v.addWidget(widget)
        return card

    def _section_title(self, text):
        lbl = QLabel(text)
        _set_cjk_font(lbl)
        lbl.setStyleSheet("font-size: 14px; font-weight: bold; color: #2c3e50;")
        return lbl

    def _chart_title(self, text):
        lbl = QLabel(text)
        _set_cjk_font(lbl)
        lbl.setStyleSheet("font-size: 12px; font-weight: bold; color: #2c3e50;")
        return lbl

    # ---------- select accounts ----------
    def _build_select_accounts(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(6)
        v.addWidget(self._section_title("Select Accounts"))
        self.available_list = QListWidget()
        self.available_list.setMinimumHeight(160)
        self.available_list.setStyleSheet("""
            QListWidget { border: 1px solid #ecf0f1; border-radius: 6px; padding: 4px; }
            QListWidget::item { padding: 4px; }
        """)
        self.available_list.itemChanged.connect(self._on_account_toggled)
        v.addWidget(self.available_list)
        return w

    def _on_account_toggled(self, item):
        if self._filling:
            return
        ids = self.db.get_budget_account_ids() or set()
        aid = item.data(Qt.ItemDataRole.UserRole)
        if item.checkState() == Qt.CheckState.Checked:
            ids.add(aid)
        else:
            ids.discard(aid)
        self.db.set_budget_account_ids(ids)
        self._update_summary()

    def _populate_available(self):
        accounts = self.db.get_accounts()
        selected = self.db.get_budget_account_ids()
        if selected is None:
            selected = {a.id for a in accounts}
            self.db.set_budget_account_ids(selected)
        base = self.db.get_base_currency()
        self._filling = True
        self.available_list.clear()
        for a in accounts:
            converted = self.converter.convert(a.balance, a.currency, base)
            sym = _currency_symbol(a.currency)
            item = QListWidgetItem(
                f"{a.name}  [{a.account_type}]  {sym}{a.balance:,.2f}  →  {_currency_symbol(base)}{converted:,.2f}")
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked if a.id in selected else Qt.CheckState.Unchecked)
            item.setData(Qt.ItemDataRole.UserRole, a.id)
            self.available_list.addItem(item)
        recv = self._total_receivable(base)
        recv_item = QListWidgetItem(
            f"Receivable (owed to me)   {_currency_symbol(base)}{recv:,.2f}")
        recv_item.setFlags(recv_item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
        recv_item.setCheckState(Qt.CheckState.Checked if RECEIVABLE_ID in selected else Qt.CheckState.Unchecked)
        recv_item.setData(Qt.ItemDataRole.UserRole, RECEIVABLE_ID)
        self.available_list.addItem(recv_item)
        self._filling = False

    def _total_receivable(self, base):
        total = 0.0
        for d in self.db.get_debts():
            if getattr(d, "debtor", "me") == "other" and not getattr(d, "completed", False):
                total += self.converter.convert(d.total_amount - d.paid_amount, d.currency, base)
        return total

    # ---------- earmarked funds ----------
    def _build_earmarked(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(6)
        row = QHBoxLayout()
        row.addWidget(self._section_title("Earmarked"))
        row.addStretch()
        add_btn = QPushButton("+ Add Fund")
        add_btn.setStyleSheet("QPushButton { background-color: #27ae60; color: white; border: none; padding: 5px 14px; border-radius: 5px; font-size: 12px; } QPushButton:hover { background-color: #1e8449; }")
        add_btn.clicked.connect(self._add_fund)
        row.addWidget(add_btn)
        v.addLayout(row)

        self.funds_table = QTableWidget()
        self.funds_table.setColumnCount(5)
        self.funds_table.setHorizontalHeaderLabels(["Name", "Amount", "Linked Categories", "Coverage (months)", "Actions"])
        self.funds_table.verticalHeader().setVisible(False)
        self.funds_table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.funds_table.setStyleSheet("""
            QTableWidget { border: 1px solid #ecf0f1; border-radius: 6px; gridline-color: #ecf0f1; }
            QHeaderView::section { background-color: #f8f9fa; padding: 6px; border: none; font-weight: bold; }
        """)
        v.addWidget(self.funds_table)
        enable_sort_filter(self.funds_table, "funds_table",
                          action_col=4, enable_filter=False,
                          on_sort_change=self._apply_fund_sort)
        return w

    def _avg_monthly_expense(self, cat_ids):
        if not cat_ids:
            return 0.0
        today = datetime.now()
        sy, sm = _add_months(today.year, today.month, -11)
        start = f"{sy}-{sm:02d}-01"
        last_day = monthrange(today.year, today.month)[1]
        end = f"{today.year}-{today.month:02d}-{last_day}"
        txs = self.db.get_transactions(start_date=start, end_date=end)
        total = 0.0
        for t in txs:
            if t.get("type") == "expense" and t.get("category_id") in cat_ids:
                total += float(t.get("base_amount") or 0.0)
        return total / 12.0

    def _populate_funds(self):
        self._funds = self.db.get_funds()
        funds = self._sort_funds(self._funds)
        cats_by_id = {c.id: c for c in self.db.get_categories("expense")}
        self.funds_table.setSortingEnabled(False)
        self.funds_table.setRowCount(len(funds))
        base = self.db.get_base_currency()
        sym = _currency_symbol(base)
        for i, f in enumerate(funds):
            self.funds_table.setItem(i, 0, QTableWidgetItem(f["name"]))
            self.funds_table.setItem(i, 1, QTableWidgetItem(f"{sym}{f['amount']:,.2f}"))
            names = [cats_by_id[c].name for c in f["categories"] if c in cats_by_id]
            self.funds_table.setItem(i, 2, QTableWidgetItem(", ".join(names) if names else "—"))
            avg = self._avg_monthly_expense(f["categories"])
            if avg > 0:
                cov = f["amount"] / avg
                self.funds_table.setItem(i, 3, QTableWidgetItem(f"{cov:,.1f}"))
            else:
                self.funds_table.setItem(i, 3, QTableWidgetItem("—"))

            widget = QWidget()
            h = QHBoxLayout(widget)
            h.setContentsMargins(4, 2, 4, 2)
            h.setSpacing(4)
            edit_btn = QPushButton("Edit")
            edit_btn.setStyleSheet("QPushButton { background-color: #f39c12; color: white; border: none; padding: 3px 10px; border-radius: 3px; font-size: 11px; } QPushButton:hover { background-color: #e67e22; }")
            edit_btn.clicked.connect(lambda _, fid=f["id"]: self._edit_fund(fid))
            del_btn = QPushButton("Delete")
            del_btn.setStyleSheet("QPushButton { background-color: #e74c3c; color: white; border: none; padding: 3px 10px; border-radius: 3px; font-size: 11px; } QPushButton:hover { background-color: #c0392b; }")
            del_btn.clicked.connect(lambda _, fid=f["id"]: self._delete_fund(fid))
            h.addWidget(edit_btn)
            h.addWidget(del_btn)
            self.funds_table.setCellWidget(i, 4, widget)
            self.funds_table.setRowHeight(i, _ROW_H)
        self.funds_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        header_h = self.funds_table.horizontalHeader().height()
        self.funds_table.setMinimumHeight(header_h + max(1, len(funds)) * _ROW_H + 8)

    def _sort_funds(self, funds):
        col = self._fund_sort_col
        if col is None or not funds:
            return funds
        if col == 0:
            key = lambda f: f["name"].lower()
        elif col == 1:
            key = lambda f: f["amount"]
        elif col == 2:
            cats_by_id = {c.id: c.name for c in self.db.get_categories("expense")}
            key = lambda f: ", ".join(cats_by_id.get(c, "") for c in f["categories"])
        elif col == 3:
            key = lambda f: (f["amount"] / self._avg_monthly_expense(f["categories"])
                             if self._avg_monthly_expense(f["categories"]) > 0 else 0.0)
        else:
            return funds
        return sorted(funds, key=key,
                      reverse=(self._fund_sort_order == Qt.SortOrder.DescendingOrder))

    def _apply_fund_sort(self, col, order):
        self._fund_sort_col = col
        self._fund_sort_order = order
        self._populate_funds()

    def _fund_dialog(self, fund=None):
        dlg = QDialog(self)
        dlg.setWindowTitle("Earmarked Fund" if not fund else "Edit Fund")
        dlg.setMinimumWidth(360)
        try:
            from main_window import DIALOG_STYLE
            dlg.setStyleSheet(DIALOG_STYLE)
        except Exception:
            pass
        layout = QFormLayout(dlg)
        layout.setLabelAlignment(Qt.AlignmentFlag.AlignRight)

        name_edit = QLineEdit()
        amount_spin = QDoubleSpinBox()
        amount_spin.setRange(0, 1e15)
        amount_spin.setDecimals(2)
        amount_spin.setSingleStep(100)
        amount_spin.setMaximumWidth(220)

        cat_list = QListWidget()
        cat_list.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        cats = self.db.get_categories("expense")
        checked = set(fund["categories"]) if fund else set()
        for c in cats:
            it = QListWidgetItem(c.name)
            it.setFlags(it.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            it.setCheckState(Qt.CheckState.Checked if c.id in checked else Qt.CheckState.Unchecked)
            it.setData(Qt.ItemDataRole.UserRole, c.id)
            cat_list.addItem(it)

        if fund:
            name_edit.setText(fund["name"])
            amount_spin.setValue(fund["amount"])

        layout.addRow("Name:", name_edit)
        layout.addRow("Amount:", amount_spin)
        layout.addRow("Linked Categories:", cat_list)

        btns = QHBoxLayout()
        ok = QPushButton("Save")
        ok.clicked.connect(dlg.accept)
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(dlg.reject)
        btns.addWidget(ok)
        btns.addWidget(cancel)
        layout.addRow(btns)

        if dlg.exec():
            selected = [cat_list.item(r).data(Qt.ItemDataRole.UserRole)
                        for r in range(cat_list.count())
                        if cat_list.item(r).checkState() == Qt.CheckState.Checked]
            name = name_edit.text().strip()
            if not name:
                QMessageBox.warning(self, "Invalid", "Name is required.")
                return None
            return name, amount_spin.value(), selected
        return None

    def _add_fund(self):
        res = self._fund_dialog()
        if res:
            self.db.add_fund(*res)
            self.refresh()

    def _edit_fund(self, fid):
        fund = next((f for f in self.db.get_funds() if f["id"] == fid), None)
        if not fund:
            return
        res = self._fund_dialog(fund)
        if res:
            self.db.update_fund(fid, *res)
            self.refresh()

    def _delete_fund(self, fid):
        reply = QMessageBox.question(self, "Confirm", "Delete this earmarked fund?",
                                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
        if reply == QMessageBox.StandardButton.Yes:
            self.db.delete_fund(fid)
            self.refresh()

    # ---------- summary cards ----------
    def _build_summary_section(self):
        self.total_assets_card = self._summary_card("available assets", "#27ae60")
        self.total_earmarked_card = self._summary_card("earmarked", "#e67e22")
        self.available_card = self._summary_card("available", "#2980b9")
        row = QHBoxLayout()
        row.setSpacing(10)
        row.addWidget(self.total_assets_card)
        row.addWidget(self.total_earmarked_card)
        row.addWidget(self.available_card)
        container = QWidget()
        container.setLayout(row)
        return container

    def _summary_card(self, title, color):
        card = QFrame()
        card.setStyleSheet("QFrame { background-color: white; border-radius: 10px; }")
        v = QVBoxLayout(card)
        v.setContentsMargins(14, 10, 14, 10)
        v.setSpacing(2)
        t = QLabel(title)
        _set_cjk_font(t)
        t.setStyleSheet("font-size: 11px; color: #7f8c8d;")
        val = QLabel("—")
        _set_cjk_font(val)
        val.setObjectName("value")
        val.setStyleSheet(f"font-size: 15px; font-weight: bold; color: {color};")
        v.addWidget(t)
        v.addWidget(val)
        card._value_label = val
        return card

    def _update_summary(self):
        base = self.db.get_base_currency()
        sym = _currency_symbol(base)
        ids = self.db.get_budget_account_ids() or set()
        total = 0.0
        for a in self.db.get_accounts():
            if a.id in ids:
                total += self.converter.convert(a.balance, a.currency, base)
        if RECEIVABLE_ID in ids:
            total += self._total_receivable(base)
        earmarked = sum(f["amount"] for f in self.db.get_funds())
        available = total - earmarked
        self.total_assets_card._value_label.setText(f"{sym}{total:,.2f}")
        self.total_earmarked_card._value_label.setText(f"{sym}{earmarked:,.2f}")
        self.available_card._value_label.setText(f"{sym}{available:,.2f}")

    # ---------- forecast ----------
    def _build_forecast_section(self):
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(6)
        v.addWidget(self._section_title("Future Forecast"))

        charts_row = QHBoxLayout()
        charts_row.setSpacing(10)

        self.line_fig = Figure(figsize=(6, 2.8), dpi=100)
        self.line_fig.set_facecolor('white')
        self.line_canvas = MplCanvas(self.line_fig)
        self.line_canvas.setFixedHeight(260)
        charts_row.addWidget(self._chart_card("Total Assets & Liabilities", self.line_canvas), 1)

        self.bar_fig = Figure(figsize=(6, 2.8), dpi=100)
        self.bar_fig.set_facecolor('white')
        self.bar_canvas = MplCanvas(self.bar_fig)
        self.bar_canvas.setFixedHeight(260)
        charts_row.addWidget(self._chart_card("Balance = Income − Expense", self.bar_canvas), 1)

        v.addLayout(charts_row)

        self.forecast_table = QTableWidget()
        self.forecast_table.setColumnCount(5)
        self.forecast_table.setHorizontalHeaderLabels(["Month", "Assets", "Liabilities", "Income", "Expense"])
        self.forecast_table.verticalHeader().setVisible(False)
        self.forecast_table.setStyleSheet("""
            QTableWidget { border: 1px solid #ecf0f1; border-radius: 6px; gridline-color: #ecf0f1; }
            QHeaderView::section { background-color: #f8f9fa; padding: 6px; border: none; font-weight: bold; }
        """)
        self._filling = False
        self.forecast_table.cellChanged.connect(self._on_forecast_cell_changed)
        v.addWidget(self.forecast_table)
        return w

    def _chart_card(self, title, canvas):
        card = QFrame()
        card.setStyleSheet("QFrame { background-color: white; border-radius: 8px; border: none; }")
        v = QVBoxLayout(card)
        v.setContentsMargins(10, 6, 10, 6)
        v.setSpacing(2)
        v.addWidget(self._chart_title(title))
        v.addWidget(canvas)
        return card

    def _month_series(self):
        today = datetime.now()
        cur = (today.year, today.month)
        months = [_add_months(cur[0], cur[1], i) for i in range(13)]
        return months, 0

    def _historical_net_worth(self):
        by_month = {}
        for h in self.db.get_net_worth_history():
            by_month[h['date'][:7]] = (h['assets'], h['liabilities'])
        return by_month

    def _compute_series(self):
        all_months, cur_idx = self._month_series()
        hnw = self._historical_net_worth()
        fc = self.db.get_forecast()
        assets, liabilities, incomes, expenses = [], [], [], []
        prev_a = prev_l = None
        for idx, (y, m) in enumerate(all_months):
            label = f"{y}-{m:02d}"
            ov = fc.get(label, {})
            s = self.db.get_monthly_summary(y, m)
            if idx == 0 and label in hnw:
                a_def, l_def = hnw[label]
            else:
                a_def, l_def = prev_a, prev_l
            if idx == 0:
                inc = ov.get("income") if ov.get("income") is not None else s['income']
                exp = ov.get("expense") if ov.get("expense") is not None else s['expense']
            else:
                inc = ov.get("income") if ov.get("income") is not None else 0.0
                exp = ov.get("expense") if ov.get("expense") is not None else 0.0
            # Current month uses the real net worth; future months roll the
            # prior month's assets forward by this month's net (income - expense)
            # unless an explicit override was entered.
            a_ov = ov.get("assets")
            if a_ov is not None:
                a = a_ov
            elif idx == 0:
                a = a_def
            else:
                a = (prev_a + (inc - exp)) if prev_a is not None else (inc - exp)
            l = ov.get("liabilities") if ov.get("liabilities") is not None else l_def
            prev_a, prev_l = a, l
            assets.append(a)
            liabilities.append(l)
            incomes.append(inc)
            expenses.append(exp)
        return all_months, cur_idx, assets, liabilities, incomes, expenses

    def _populate_forecast_table(self, series):
        all_months, cur_idx, assets, liabilities, incomes, expenses = series
        base = self.db.get_base_currency()
        sym = _currency_symbol(base)
        self._filling = True
        self.forecast_table.setRowCount(len(all_months))
        for i, (y, m) in enumerate(all_months):
            label = f"{y}-{m:02d}"
            month_item = QTableWidgetItem(label)
            month_item.setFlags(Qt.ItemFlag.ItemIsEnabled)
            self.forecast_table.setItem(i, 0, month_item)
            a = assets[i]
            l = liabilities[i]
            inc = incomes[i]
            exp = expenses[i]
            cells = [
                "" if a is None else f"{sym}{a:,.2f}",
                "" if l is None else f"{sym}{l:,.2f}",
                f"{sym}{inc:,.2f}",
                f"{sym}{exp:,.2f}",
            ]
            for col, txt in enumerate(cells, start=1):
                item = QTableWidgetItem(txt)
                # The current month (index 0) is read-only; only future months are editable.
                if i == 0:
                    item.setFlags(Qt.ItemFlag.ItemIsEnabled)
                self.forecast_table.setItem(i, col, item)
            self.forecast_table.setRowHeight(i, _ROW_H)
        self._filling = False
        self.forecast_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        header_h = self.forecast_table.horizontalHeader().height()
        self.forecast_table.setMinimumHeight(header_h + len(all_months) * _ROW_H + 8)

    def _on_forecast_cell_changed(self, row, col):
        if self._filling or col not in _FC_FIELDS:
            return
        item = self.forecast_table.item(row, col)
        if item is None:
            return
        all_months, _ = self._month_series()
        label = f"{all_months[row][0]}-{all_months[row][1]:02d}"
        text = item.text().strip()
        field = _FC_FIELDS[col]
        if text == "":
            value = None
        else:
            try:
                sym = _currency_symbol(self.db.get_base_currency())
                value = float(text.replace(",", "").replace(sym, ""))
            except Exception:
                self._recompute_and_draw()
                return
        self.db.set_forecast_cell(label, field, value)
        self._recompute_and_draw()

    def _recompute_and_draw(self):
        series = self._compute_series()
        self._populate_forecast_table(series)
        self._draw_line(series)
        self._draw_bars(series)

    def _draw_line(self, series):
        all_months, _, assets, liabilities, _, _ = series
        self.line_fig.clear()
        ax = self.line_fig.add_subplot(111)
        x = list(range(len(all_months)))
        if not any(v is not None for v in assets + liabilities):
            ax.text(0.5, 0.5, 'No Data', ha='center', va='center', fontsize=11, color='gray', transform=ax.transAxes)
            ax.set_xticks([]); ax.set_yticks([])
            self.line_fig.tight_layout(); self.line_canvas.draw(); return

        ax.plot(x, assets, color=COLOR_ASSETS, linewidth=1.6, linestyle='--', label='Assets')
        ax.plot(x, liabilities, color=COLOR_LIABILITIES, linewidth=1.6, linestyle='--', label='Liabilities')

        labels = [f"{y}-{m:02d}" for y, m in all_months]
        n = len(labels)
        step = 1 if n <= 12 else (n + 11) // 12
        ticks = list(range(0, n, step))
        if (n - 1) not in ticks:
            ticks.append(n - 1)
        ax.set_xticks(ticks)
        ax.set_xticklabels([labels[i] for i in ticks], rotation=30, ha='right', fontsize=8)
        ax.yaxis.set_major_formatter(FuncFormatter(lambda v, p: f'{v:,.0f}'))
        ax.set_ylabel(self.db.get_base_currency(), fontsize=9)
        ax.legend(fontsize=9, loc='upper left')
        ax.grid(axis='y', alpha=0.3)
        ax.tick_params(labelsize=8)
        sym = _currency_symbol(self.db.get_base_currency())
        enable_line_hover(self.line_canvas, self.line_fig, ax, labels, [
            {"label": "Assets", "values": assets, "color": COLOR_ASSETS},
            {"label": "Liabilities", "values": liabilities, "color": COLOR_LIABILITIES},
        ], sym)
        self.line_fig.tight_layout()
        self.line_canvas.draw()

    def _draw_bars(self, series):
        all_months, _, _, _, incomes, expenses = series
        self.bar_fig.clear()
        ax = self.bar_fig.add_subplot(111)
        balances = [inc - exp for inc, exp in zip(incomes, expenses)]
        x = list(range(len(all_months)))
        pos_x = [x[i] for i, b in enumerate(balances) if b >= 0]
        pos_y = [b for b in balances if b >= 0]
        neg_x = [x[i] for i, b in enumerate(balances) if b < 0]
        neg_y = [b for b in balances if b < 0]
        ax.bar(pos_x, pos_y, color=COLOR_BAL_POS)
        ax.bar(neg_x, neg_y, color=COLOR_BAL_NEG)
        ax.axhline(y=0, color='#7f8c8d', linewidth=0.8, linestyle='--')

        labels = [f"{y}-{m:02d}" for y, m in all_months]
        n = len(labels)
        step = 1 if n <= 12 else (n + 11) // 12
        ticks = list(range(0, n, step))
        if (n - 1) not in ticks:
            ticks.append(n - 1)
        ax.set_xticks(ticks)
        ax.set_xticklabels([labels[i] for i in ticks], rotation=30, ha='right', fontsize=8)
        ax.yaxis.set_major_formatter(FuncFormatter(lambda v, p: f'{v:,.0f}'))
        ax.set_ylabel(self.db.get_base_currency(), fontsize=9)
        ax.legend(handles=[
            Patch(facecolor=COLOR_BAL_POS, label='Surplus'),
            Patch(facecolor=COLOR_BAL_NEG, label='Deficit'),
        ], fontsize=9, loc='upper left')
        ax.grid(axis='y', alpha=0.3)
        ax.tick_params(labelsize=8)
        sym = _currency_symbol(self.db.get_base_currency())
        enable_line_hover(self.bar_canvas, self.bar_fig, ax, labels, [
            {"label": "Balance", "values": balances, "color": COLOR_BAL_POS},
        ], sym)
        self.bar_fig.tight_layout()
        self.bar_canvas.draw()

    # ---------- totals / refresh ----------
    def _totals(self):
        base = self.db.get_base_currency()
        assets = sum(self.converter.convert(a.balance, a.currency, base) for a in self.db.get_accounts())
        liabilities = 0.0
        for d in self.db.get_debts():
            if getattr(d, "completed", False):
                continue
            amount = self.converter.convert(d.total_amount - d.paid_amount, d.currency, base)
            if getattr(d, "debtor", "me") == "other":
                assets += amount
            else:
                liabilities += amount
        return assets, liabilities

    def _prune_stale_forecasts(self):
        # Months that have already arrived are driven by actuals; any leftover
        # forecast override for them is dropped (no need to retain it).
        cur = datetime.now().strftime("%Y-%m")
        for month in list(self.db.get_forecast().keys()):
            if month <= cur:
                self.db.delete_forecast(month)

    def refresh(self):
        assets, liabilities = self._totals()
        today = datetime.now().strftime("%Y-%m-%d")
        self.db.upsert_net_worth_if_missing(today, assets, liabilities)

        self._prune_stale_forecasts()
        self._populate_available()
        self._populate_funds()
        self._update_summary()
        self._recompute_and_draw()
