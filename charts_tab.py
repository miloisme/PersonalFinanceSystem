from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QGridLayout, QLabel, QFrame,
    QComboBox, QDateEdit, QPushButton, QScrollArea, QTableWidget,
    QTableWidgetItem, QHeaderView
)
from PySide6.QtCore import Qt, QDate
from PySide6.QtGui import QColor

from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
import matplotlib
matplotlib.use('QtAgg')

import matplotlib.pyplot as plt
from matplotlib.ticker import MaxNLocator, FuncFormatter
from matplotlib.font_manager import FontProperties
from PySide6.QtWidgets import QSizePolicy

_cjk_font = FontProperties(family=["Microsoft JhengHei", "Microsoft YaHei", "Segoe UI"])

matplotlib.rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Microsoft YaHei', 'Segoe UI', 'DejaVu Sans']
matplotlib.rcParams['font.family'] = 'sans-serif'
matplotlib.rcParams['axes.unicode_minus'] = False



class MplCanvas(FigureCanvas):
    def resizeEvent(self, event):
        w = event.size().width()
        h = event.size().height()
        if w > 0 and h > 0 and self.figure is not None:
            self.figure.set_size_inches(w / self.figure.dpi, h / self.figure.dpi)
        super().resizeEvent(event)


def _plain_axis(ax):
    ax.yaxis.set_major_formatter(FuncFormatter(lambda x, pos: f'{x:,.0f}'))


def _simplify_xticks(ax, labels, rotation=30):
    """Show a readable subset of x-axis tick labels when there are many periods."""
    n = len(labels)
    if n <= 1:
        ax.set_xticks([0])
        ax.set_xticklabels(labels, fontsize=8)
        return
    if n <= 12:
        step = 1
    else:
        step = (n + 11) // 12
    ticks = list(range(0, n, step))
    if (n - 1) not in ticks:
        ticks.append(n - 1)
    ax.set_xticks(ticks)
    ax.set_xticklabels([labels[i] for i in ticks], rotation=rotation, ha='right', fontsize=8)

from datetime import datetime, date
from calendar import monthrange
from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_sort_filter

COLOR_ASSETS = "#1f3a93"
COLOR_LIABILITIES = "#d35400"
COLOR_INCOME = "#27ae60"
COLOR_EXPENSE = "#e74c3c"
COLOR_BALANCE = "#8e44ad"


class ChartsTab(QWidget):
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

        header = QLabel("Dashboard")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        filter_frame = QFrame()
        filter_frame.setStyleSheet("QFrame { background-color: white; border-radius: 8px; border: none; }")
        filter_row = QHBoxLayout(filter_frame)
        filter_row.setContentsMargins(12, 6, 12, 6)
        filter_row.setSpacing(8)

        lbl1 = QLabel("Start:")
        _set_cjk_font(lbl1)
        filter_row.addWidget(lbl1)
        self.start_date = QDateEdit()
        _set_cjk_font(self.start_date)
        self.start_date.setCalendarPopup(True)
        self.start_date.setDisplayFormat("yyyy-MM-dd")
        filter_row.addWidget(self.start_date)

        lbl2 = QLabel("End:")
        _set_cjk_font(lbl2)
        filter_row.addWidget(lbl2)
        self.end_date = QDateEdit()
        _set_cjk_font(self.end_date)
        self.end_date.setCalendarPopup(True)
        self.end_date.setDisplayFormat("yyyy-MM-dd")
        filter_row.addWidget(self.end_date)

        lbl3 = QLabel("Period:")
        _set_cjk_font(lbl3)
        filter_row.addWidget(lbl3)
        self.period_combo = QComboBox()
        _set_cjk_font(self.period_combo)
        self.period_combo.addItem("Month", "month")
        self.period_combo.addItem("Year", "year")
        filter_row.addWidget(self.period_combo)

        filter_row.addStretch()
        layout.addWidget(filter_frame)

        self._load_dates()
        self.start_date.dateChanged.connect(self.on_range_changed)
        self.end_date.dateChanged.connect(self.on_range_changed)
        self.period_combo.currentIndexChanged.connect(self.refresh)

        self.net_fig = Figure(figsize=(8, 3.5), dpi=100)
        self.net_fig.set_facecolor('white')
        self.net_canvas = MplCanvas(self.net_fig)
        self.net_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.net_canvas.setFixedHeight(350)

        self.ie_fig = Figure(figsize=(8, 3.5), dpi=100)
        self.ie_fig.set_facecolor('white')
        self.ie_canvas = MplCanvas(self.ie_fig)
        self.ie_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.ie_canvas.setFixedHeight(350)

        self.bal_fig = Figure(figsize=(8, 3.5), dpi=100)
        self.bal_fig.set_facecolor('white')
        self.bal_canvas = MplCanvas(self.bal_fig)
        self.bal_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.bal_canvas.setFixedHeight(350)

        self._build_debt_info_card()

        grid = QGridLayout()
        grid.setSpacing(8)
        grid.addWidget(self._chart_card("Assets & Liabilities Trend", self.net_canvas), 0, 0)
        grid.addWidget(self.debt_info_card, 0, 1)
        grid.addWidget(self._chart_card("Income vs Expense", self.ie_canvas), 1, 0)
        grid.addWidget(self._chart_card("Balance (Income - Expense)", self.bal_canvas), 1, 1)
        layout.addLayout(grid)

        table_card = QFrame()
        table_card.setStyleSheet("QFrame { background-color: white; border-radius: 8px; border: none; }")
        tc_layout = QVBoxLayout(table_card)
        tc_layout.setContentsMargins(10, 6, 10, 6)
        tc_layout.setSpacing(2)
        tc_title = QLabel("Period Summary")
        _set_cjk_font(tc_title)
        tc_title.setStyleSheet("font-size: 12px; font-weight: bold; color: #2c3e50;")
        tc_layout.addWidget(tc_title)
        self.summary_table = QTableWidget()
        self.summary_table.setColumnCount(5)
        self.summary_table.setHorizontalHeaderLabels(["Period", "Income", "Expense", "Balance", "Assets / Liabilities"])
        self.summary_table.verticalHeader().setVisible(False)
        self.summary_table.setEditTriggers(QTableWidget.EditTrigger.DoubleClicked)
        self.summary_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.summary_table.setStyleSheet("""
            QTableWidget { border: 1px solid #ecf0f1; border-radius: 5px; gridline-color: #ecf0f1; font-size: 11px; }
            QHeaderView::section { background-color: #f8f9fa; padding: 4px; border: none; font-weight: bold; font-size: 11px; }
        """)
        self._filling = False
        self._manual_dates = {h['date'] for h in self.db.get_net_worth_history()}
        self.summary_table.cellChanged.connect(self._on_summary_cell_changed)
        tc_layout.addWidget(self.summary_table)
        enable_sort_filter(self.summary_table, "summary_table", layout=tc_layout,
                          enable_filter=False, sortable=False)
        layout.addWidget(table_card)

        scroll.setWidget(content)
        outer.addWidget(scroll)

        self.refresh()

    def _chart_card(self, title, canvas):
        card = QFrame()
        card.setStyleSheet("QFrame { background-color: white; border-radius: 8px; border: none; }")
        v = QVBoxLayout(card)
        v.setContentsMargins(10, 6, 10, 6)
        v.setSpacing(2)
        lbl = QLabel(title)
        _set_cjk_font(lbl)
        lbl.setStyleSheet("font-size: 12px; font-weight: bold; color: #2c3e50;")
        v.addWidget(lbl)
        v.addWidget(canvas)
        return card

    def _build_debt_info_card(self):
        self.debt_info_card = QFrame()
        self.debt_info_card.setStyleSheet("QFrame { background-color: white; border-radius: 8px; border: none; }")
        di = QVBoxLayout(self.debt_info_card)
        di.setContentsMargins(12, 10, 12, 10)
        di.setSpacing(4)
        di_title = QLabel("Upcoming Debt & Credit Due Dates")
        _set_cjk_font(di_title)
        di_title.setStyleSheet("font-size: 12px; font-weight: bold; color: #7f8c8d;")
        di.addWidget(di_title)
        self.debt_info_list = QLabel("No upcoming due dates")
        _set_cjk_font(self.debt_info_list)
        self.debt_info_list.setStyleSheet("font-size: 12px; color: #2c3e50;")
        self.debt_info_list.setWordWrap(True)
        self.debt_info_list.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignTop)
        di.addWidget(self.debt_info_list)

    def _refresh_info_cards(self, assets, liabilities):
        base = self.db.get_base_currency()
        symbol = self._get_currency_symbol(base)

        today = datetime.now().strftime("%Y-%m-%d")
        debts = [d for d in self.db.get_debts()
                 if not getattr(d, "completed", False) and d.due_date]
        debts.sort(key=lambda d: d.due_date)
        if not debts:
            self.debt_info_list.setText("No upcoming due dates")
            return
        lines = []
        for d in debts[:6]:
            party = getattr(d, "debtor", "me")
            overdue = d.due_date < today and party == "me"
            if party == "other":
                color = "#27ae60"
                tag = " (owed)"
            elif overdue:
                color = "#e74c3c"
                tag = ""
            else:
                color = "#2c3e50"
                tag = ""
            remaining = d.total_amount - d.paid_amount
            lines.append(
                f'<font color="{color}">• {d.name}{tag}: {d.due_date}</font>'
                f'  <font color="#95a5a6">({symbol}{remaining:,.0f} left)</font>'
            )
        self.debt_info_list.setText("<br>".join(lines))

    def _load_dates(self):
        saved_start = self.db.get_setting("dash_start_date")
        saved_end = self.db.get_setting("dash_end_date")
        if saved_start:
            d = QDate.fromString(saved_start, "yyyy-MM-dd")
            if d.isValid():
                self.start_date.setDate(d)
        else:
            self.start_date.setDate(QDate.currentDate())
        if saved_end:
            d = QDate.fromString(saved_end, "yyyy-MM-dd")
            if d.isValid():
                self.end_date.setDate(d)
        else:
            d = QDate.currentDate()
            self.end_date.setDate(QDate(d.year(), d.month(), d.daysInMonth()))

    def on_range_changed(self):
        self.db.set_setting("dash_start_date", self.start_date.date().toString("yyyy-MM-dd"))
        self.db.set_setting("dash_end_date", self.end_date.date().toString("yyyy-MM-dd"))
        self.refresh()

    def _qdate_to_date(self, qd):
        return date(qd.year(), qd.month(), qd.day())

    def _get_currency_symbol(self, currency):
        symbols = {
            "CNY": "\u00A5", "HKD": "HK$", "USD": "$", "EUR": "\u20AC",
            "GBP": "\u00A3", "JPY": "\u00A5", "TWD": "NT$", "KRW": "\u20A9",
            "SGD": "S$", "AUD": "A$", "CAD": "C$"
        }
        return symbols.get(currency, currency + " ")

    def refresh(self):
        self.db.set_setting("dash_start_date", self.start_date.date().toString("yyyy-MM-dd"))
        self.db.set_setting("dash_end_date", self.end_date.date().toString("yyyy-MM-dd"))

        start = self._qdate_to_date(self.start_date.date())
        end = self._qdate_to_date(self.end_date.date())
        period = self.period_combo.currentData() or "month"

        assets, liabilities = self._totals()
        self._refresh_info_cards(assets, liabilities)
        today = datetime.now().strftime("%Y-%m-%d")
        self.db.upsert_net_worth(today, assets, liabilities)

        self.draw_assets_liabilities(start, end, period)
        self.draw_income_expense(start, end, period)
        self.draw_balance(start, end, period)
        self._fill_summary_table(start, end, period)

    def _fill_summary_table(self, start: date, end: date, period: str):
        labels, keys = self._iter_periods(start, end, period)
        incomes, expenses = self._income_expense_for_periods(keys)
        balances = [(inc - exp) if (inc is not None and exp is not None) else None
                    for inc, exp in zip(incomes, expenses)]

        start_s = start.strftime("%Y-%m-%d")
        end_s = end.strftime("%Y-%m-%d")
        history = [h for h in self.db.get_net_worth_history()
                   if start_s <= h['date'] <= end_s]

        last_by_period = {}
        for h in history:
            if period == "year":
                key = h['date'][:4]
            else:
                key = h['date'][:7]
            last_by_period[key] = h

        symbol = self._get_currency_symbol(self.db.get_base_currency())
        now = datetime.now()
        current_label = str(now.year) if period == "year" else now.strftime("%Y-%m")
        live_assets, live_liabilities = self._totals()
        self._filling = True
        row_height = 32
        self.summary_table.setRowCount(len(labels))

        def _fmt(v):
            return "" if v is None else f"{symbol}{v:,.2f}"

        for i, label in enumerate(labels):
            texts = [
                label,
                _fmt(incomes[i]),
                _fmt(expenses[i]),
                _fmt(balances[i]),
            ]
            for c in range(4):
                it = self.summary_table.item(i, c)
                if it is None:
                    it = QTableWidgetItem()
                    self.summary_table.setItem(i, c, it)
                it.setText(texts[c])
                it.setFlags(it.flags() & ~Qt.ItemIsEditable)
                if c == 3 and balances[i] is not None:
                    it.setForeground(QColor("#27ae60") if balances[i] >= 0 else QColor("#e74c3c"))

            if label == current_label:
                # Current period: auto-fetched from live accounts/debts and read-only.
                a_l = f"{symbol}{live_assets:,.2f} / {symbol}{live_liabilities:,.2f}"
                al_item = QTableWidgetItem(a_l)
                al_item.setFlags(al_item.flags() & ~Qt.ItemIsEditable)
            else:
                h = last_by_period.get(label)
                if h:
                    a_l = f"{symbol}{h['assets']:,.2f} / {symbol}{h['liabilities']:,.2f}"
                else:
                    a_l = "--"
                al_item = QTableWidgetItem(a_l)
                al_item.setFlags(al_item.flags() | Qt.ItemIsEditable)
            self.summary_table.setItem(i, 4, al_item)

        self._filling = False

        for i in range(len(labels)):
            self.summary_table.setRowHeight(i, row_height)
        header_h = self.summary_table.horizontalHeader().height()
        self.summary_table.setMinimumHeight(header_h + len(labels) * row_height + 8)

    def _period_to_date(self, label: str) -> str:
        label = label.strip()
        now = datetime.now()
        if len(label) == 4 and label.isdigit():
            if label == str(now.year):
                return now.strftime("%Y-%m-%d")
            return f"{label}-01-01"
        parts = label.split("-")
        if len(parts) >= 2:
            if parts[0] == str(now.year) and parts[1] == f"{now.month:02d}":
                return now.strftime("%Y-%m-%d")
            return f"{parts[0]}-{parts[1]}-01"
        return label

    def _on_summary_cell_changed(self, row: int, col: int):
        if self._filling or col != 4:
            return
        item = self.summary_table.item(row, col)
        if item is None:
            return
        period_label = self.summary_table.item(row, 0).text().strip()
        now = datetime.now()
        current_label = str(now.year) if (self.period_combo.currentData() or "month") == "year" else now.strftime("%Y-%m")
        if period_label == current_label:
            self.refresh()
            return
        text = item.text().strip()
        if text == "--" or "/" not in text:
            self.refresh()
            return
        symbol = self._get_currency_symbol(self.db.get_base_currency())
        try:
            def parse_money(s):
                s = s.strip()
                if symbol and s.startswith(symbol):
                    s = s[len(symbol):]
                s = s.replace(",", "").replace(" ", "")
                return float(s)
            a_part, l_part = text.split("/")
            assets = parse_money(a_part)
            liab = parse_money(l_part)
        except Exception:
            self.refresh()
            return
        period_label = self.summary_table.item(row, 0).text().strip()
        date_str = self._period_to_date(period_label)
        self.db.upsert_net_worth(date_str, assets, liab)
        self._manual_dates.add(date_str)
        self.refresh()

    def _totals(self):
        base = self.db.get_base_currency()
        assets = sum(
            self.converter.convert(a.balance, a.currency, base)
            for a in self.db.get_accounts()
        )
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

    def draw_assets_liabilities(self, start: date, end: date, period: str):
        self.net_fig.clear()
        ax = self.net_fig.add_subplot(111)

        labels, _ = self._iter_periods(start, end, period)
        full_history = self.db.get_net_worth_history()
        if period == "year":
            by_period = {h['date'][:4]: h for h in full_history}
        else:
            by_period = {h['date'][:7]: h for h in full_history}
        asset_vals = [by_period[lab]['assets'] if lab in by_period else None for lab in labels]
        liab_vals = [by_period[lab]['liabilities'] if lab in by_period else None for lab in labels]

        if not any(v is not None for v in asset_vals + liab_vals):
            ax.text(0.5, 0.5, 'No Data', ha='center', va='center',
                    fontsize=11, color='gray', transform=ax.transAxes)
            ax.set_xticks([])
            ax.set_yticks([])
            self.net_fig.tight_layout()
            self.net_canvas.draw()
            return

        x = range(len(labels))
        ax.plot(x, asset_vals, linewidth=1.4,
                label='Assets', color=COLOR_ASSETS)
        ax.plot(x, liab_vals, linewidth=1.4,
                label='Liabilities', color=COLOR_LIABILITIES)

        _simplify_xticks(ax, labels, rotation=30)
        ax.set_ylabel(self.db.get_base_currency(), fontsize=9)
        ax.legend(fontsize=9, loc='upper left')
        ax.grid(axis='y', alpha=0.3)
        ax.tick_params(labelsize=8)
        _plain_axis(ax)
        self.net_fig.tight_layout()
        self.net_canvas.draw()

    def _iter_periods(self, start: date, end: date, period: str):
        if period == "year":
            years = list(range(start.year, end.year + 1))
            return [str(y) for y in years], [("year", y) for y in years]
        else:
            labels = []
            keys = []
            y, m = start.year, start.month
            while (y, m) <= (end.year, end.month):
                labels.append(f"{y}-{m:02d}")
                keys.append(("month", y, m))
                m += 1
                if m > 12:
                    m = 1
                    y += 1
            return labels, keys

    def _income_expense_for_periods(self, keys):
        incomes = []
        expenses = []
        for key in keys:
            if key[0] == "year":
                _, y = key
                total_in = 0.0
                total_ex = 0.0
                any_data = False
                for m in range(1, 13):
                    if self.db.get_month_transaction_count(y, m) > 0:
                        any_data = True
                    s = self.db.get_monthly_summary(y, m)
                    total_in += s['income']
                    total_ex += s['expense']
                if not any_data:
                    incomes.append(None)
                    expenses.append(None)
                else:
                    incomes.append(total_in)
                    expenses.append(total_ex)
            else:
                _, y, m = key
                if self.db.get_month_transaction_count(y, m) > 0:
                    s = self.db.get_monthly_summary(y, m)
                    incomes.append(s['income'])
                    expenses.append(s['expense'])
                else:
                    incomes.append(None)
                    expenses.append(None)
        return incomes, expenses

    def draw_income_expense(self, start: date, end: date, period: str):
        self.ie_fig.clear()
        ax = self.ie_fig.add_subplot(111)

        labels, keys = self._iter_periods(start, end, period)
        incomes, expenses = self._income_expense_for_periods(keys)

        if not any(v is not None for v in incomes + expenses):
            ax.text(0.5, 0.5, 'No Data', ha='center', va='center',
                    fontsize=11, color='gray', transform=ax.transAxes)
            ax.set_xticks([])
            ax.set_yticks([])
            self.ie_fig.tight_layout()
            self.ie_canvas.draw()
            return

        x = range(len(labels))

        ax.plot(x, incomes, linewidth=1.4,
                label='Income', color=COLOR_INCOME)
        ax.plot(x, expenses, linewidth=1.4,
                label='Expense', color=COLOR_EXPENSE)
        if all(v is not None for v in incomes) and all(v is not None for v in expenses):
            ax.fill_between(x, incomes, alpha=0.08, color=COLOR_INCOME)
            ax.fill_between(x, expenses, alpha=0.08, color=COLOR_EXPENSE)

        _simplify_xticks(ax, labels, rotation=30)

        ax.set_ylabel(self.db.get_base_currency(), fontsize=9)
        ax.legend(fontsize=9, loc='upper left')
        ax.grid(axis='y', alpha=0.3)
        ax.tick_params(labelsize=8)
        ax.yaxis.set_major_locator(MaxNLocator(6))
        _plain_axis(ax)
        self.ie_fig.tight_layout()
        self.ie_canvas.draw()

    def draw_balance(self, start: date, end: date, period: str):
        self.bal_fig.clear()
        ax = self.bal_fig.add_subplot(111)

        labels, keys = self._iter_periods(start, end, period)
        incomes, expenses = self._income_expense_for_periods(keys)
        balances = [(inc - exp) if (inc is not None and exp is not None) else None
                    for inc, exp in zip(incomes, expenses)]

        if not any(v is not None for v in balances):
            ax.text(0.5, 0.5, 'No Data', ha='center', va='center',
                    fontsize=11, color='gray', transform=ax.transAxes)
            ax.set_xticks([])
            ax.set_yticks([])
            self.bal_fig.tight_layout()
            self.bal_canvas.draw()
            return

        x = range(len(labels))

        ax.plot(x, balances, color=COLOR_BALANCE, linewidth=1.6,
                label="Balance")
        ax.axhline(y=0, color='#7f8c8d', linewidth=0.8, linestyle='--')

        _simplify_xticks(ax, labels, rotation=30)

        ax.set_ylabel(self.db.get_base_currency(), fontsize=9)
        ax.grid(axis='y', alpha=0.3)
        ax.tick_params(labelsize=8)
        ax.yaxis.set_major_locator(MaxNLocator(6))
        ax.legend(fontsize=9, loc='upper left')
        _plain_axis(ax)
        self.bal_fig.tight_layout()
        self.bal_canvas.draw()
