from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QTableWidget, QTableWidgetItem, QHeaderView, QScrollArea,
    QSizePolicy, QComboBox, QSpinBox
)
from PySide6.QtCore import Qt, QPointF
from PySide6.QtGui import (
    QPainter, QPen, QColor, QPixmap, QBrush, QFont, QCursor
)

from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
import matplotlib
matplotlib.use('QtAgg')
import re
from matplotlib.ticker import FuncFormatter
from matplotlib.font_manager import FontProperties
from matplotlib.patches import Patch

from datetime import datetime, date
from currency_converter import CurrencyConverter
from font_utils import set_cjk_font as _set_cjk_font
from table_utils import enable_column_memory
from chart_tooltips import enable_pie_hover, enable_line_hover

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


C_INCOME = "#27ae60"
C_EXPENSE = "#e74c3c"
C_BAL_POS = "#aed6f1"
C_BAL_NEG = "#f5b7b1"
C_BALANCE = "#8e44ad"
C_SAVE = "#16a085"

MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

_EMOJI_RE = re.compile(
    "[\U0001F000-\U0001FAFF\U00002600-\U000027BF\U0001F1E6-\U0001F1FF\uFE0F]"
)


def _strip_emoji(s: str) -> str:
    return _EMOJI_RE.sub("", s).strip()


class BalanceTab(QWidget):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.converter = CurrencyConverter()
        self._cache = None
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

        header = QLabel("Balance Review")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        ctrl = QHBoxLayout()
        ctrl.setSpacing(8)
        ylbl = QLabel("Year:")
        _set_cjk_font(ylbl)
        ctrl.addWidget(ylbl)
        self.year_combo = QComboBox()
        _set_cjk_font(self.year_combo)
        years = self.db.get_transaction_years()
        for y in years:
            self.year_combo.addItem(str(y), y)
        now_y = datetime.now().year
        idx = self.year_combo.findData(now_y)
        if idx >= 0:
            self.year_combo.setCurrentIndex(idx)
        elif self.year_combo.count() > 0:
            self.year_combo.setCurrentIndex(self.year_combo.count() - 1)
        self.year_combo.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Fixed)
        self.year_combo.setMinimumWidth(90)
        self.year_combo.currentIndexChanged.connect(self.refresh)
        ctrl.addWidget(self.year_combo)
        ctrl.addStretch()
        layout.addLayout(ctrl)

        charts_row = QHBoxLayout()
        charts_row.setSpacing(8)

        self.comp_fig = Figure(figsize=(6, 3), dpi=100)
        self.comp_fig.set_facecolor('white')
        self.comp_canvas = MplCanvas(self.comp_fig)
        self.comp_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.comp_canvas.setFixedHeight(280)
        charts_row.addWidget(self._chart_card("Monthly Income / Expense / Balance", self.comp_canvas), 2)

        self.inc_fig = Figure(figsize=(3.2, 3), dpi=100)
        self.inc_fig.set_facecolor('white')
        self.inc_canvas = MplCanvas(self.inc_fig)
        self.inc_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.inc_canvas.setFixedHeight(280)
        charts_row.addWidget(self._chart_card("Income Breakdown", self.inc_canvas), 1)

        self.exp_fig = Figure(figsize=(3.2, 3), dpi=100)
        self.exp_fig.set_facecolor('white')
        self.exp_canvas = MplCanvas(self.exp_fig)
        self.exp_canvas.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.exp_canvas.setFixedHeight(280)
        charts_row.addWidget(self._chart_card("Expense Breakdown", self.exp_canvas), 1)

        layout.addLayout(charts_row)

        matrix_card = QFrame()
        matrix_card.setStyleSheet("QFrame { background-color: white; border-radius: 8px; border: none; }")
        mc = QVBoxLayout(matrix_card)
        mc.setContentsMargins(10, 8, 10, 8)
        mc.setSpacing(4)
        mt = QLabel("Monthly Detail by Category")
        _set_cjk_font(mt)
        mt.setStyleSheet("font-size: 13px; font-weight: bold; color: #2c3e50;")
        mc.addWidget(mt)

        self.matrix = QTableWidget()
        self.matrix.setColumnCount(16)
        self.matrix.setHorizontalHeaderLabels(
            ["Category"] + MONTHS + ["Year Total", "Monthly Avg", "Trend"]
        )
        self.matrix.verticalHeader().setVisible(False)
        self.matrix.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.matrix.setSelectionMode(QTableWidget.SelectionMode.NoSelection)
        self.matrix.setStyleSheet("""
            QTableWidget { border: 1px solid #ecf0f1; border-radius: 5px; gridline-color: #ecf0f1; font-size: 11px; }
            QHeaderView::section { background-color: #f8f9fa; padding: 4px; border: none; font-weight: bold; font-size: 10px; }
        """)
        enable_column_memory(
            self.matrix, "balance_matrix", stretch_column=None,
            default_widths=[150] + [72] * 12 + [95, 95, 130]
        )
        mc.addWidget(self.matrix)
        layout.addWidget(matrix_card)

        scroll.setWidget(content)
        outer.addWidget(scroll)

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

    def _symbol(self):
        cur = self.db.get_base_currency()
        symbols = {
            "CNY": "¥", "HKD": "HK$", "USD": "$", "EUR": "€",
            "GBP": "£", "JPY": "¥", "TWD": "NT$", "KRW": "₩",
            "SGD": "S$", "AUD": "A$", "CAD": "C$"
        }
        return symbols.get(cur, cur + " ")

    def refresh(self):
        self._cache = self._compute(int(self.year_combo.currentData()))
        self._draw_composite()
        self._draw_pies()
        self._fill_matrix()

    def _compute(self, year):
        now = datetime.now()
        latest = now.month if year == now.year else 12

        cats = self.db.get_categories()
        cat_info = {c.id: c for c in cats}

        txns = self.db.get_transactions(
            start_date=f"{year}-01-01", end_date=f"{year}-12-31"
        )

        inc_month = [0.0] * 13
        exp_month = [0.0] * 13
        txn_month = [0] * 13
        inc_cat = {}
        exp_cat = {}

        for t in txns:
            m = int(t["date"][5:7])
            txn_month[m] += 1
            amt = float(t.get("base_amount", t["amount"]))
            cid = t["category_id"]
            c = cat_info.get(cid)
            name = c.name if c else "Unknown"
            color = c.color if c else "#999999"
            icon = (c.icon + " ") if c and c.icon else ""
            if t["type"] == "income":
                inc_month[m] += amt
                d = inc_cat.setdefault(cid, {"name": icon + name, "color": color, "by": [0.0] * 13})
                d["by"][m] += amt
            else:
                exp_month[m] += amt
                d = exp_cat.setdefault(cid, {"name": icon + name, "color": color, "by": [0.0] * 13})
                d["by"][m] += amt

        def finalize(store):
            rows = []
            for cid, d in store.items():
                total = sum(d["by"][1:13])
                avg = total / latest if latest else 0
                rows.append({
                    "name": d["name"], "color": d["color"],
                    "by": d["by"], "total": total, "avg": avg
                })
            rows.sort(key=lambda r: r["total"], reverse=True)
            return rows

        inc_rows = finalize(inc_cat)
        exp_rows = finalize(exp_cat)

        # Display arrays: a month with no transactions is left empty (None)
        # instead of forced to 0, so charts/table show gaps.
        inc_disp = [(inc_month[m] if txn_month[m] > 0 else None) for m in range(13)]
        exp_disp = [(exp_month[m] if txn_month[m] > 0 else None) for m in range(13)]
        balance_month = [
            (inc_disp[m] - exp_disp[m])
            if (inc_disp[m] is not None and exp_disp[m] is not None) else None
            for m in range(13)
        ]

        inc_total_year = sum(inc_month[1:latest + 1])
        exp_total_year = sum(exp_month[1:latest + 1])
        balance_total = inc_total_year - exp_total_year
        saving_month = []
        for m in range(1, 13):
            if txn_month[m] == 0:
                saving_month.append(None)
            else:
                saving_month.append(
                    (balance_month[m] / inc_disp[m] * 100.0)
                    if (inc_disp[m] is not None and inc_disp[m] > 0) else 0.0
                )
        inc_avg = inc_total_year / latest if latest else 0
        exp_avg = exp_total_year / latest if latest else 0
        balance_avg = balance_total / latest if latest else 0
        annual_sr = (balance_total / inc_total_year * 100.0) if inc_total_year > 0 else 0.0

        return {
            "year": year, "latest": latest,
            "txn_month": txn_month,
            "inc_month": inc_disp, "exp_month": exp_disp,
            "inc_rows": inc_rows, "exp_rows": exp_rows,
            "balance_month": balance_month, "saving_month": saving_month,
            "inc_total_year": inc_total_year, "exp_total_year": exp_total_year,
            "balance_total": balance_total, "inc_avg": inc_avg, "exp_avg": exp_avg,
            "balance_avg": balance_avg, "annual_sr": annual_sr,
        }

    def _draw_composite(self):
        d = self._cache
        self.comp_fig.clear()
        ax = self.comp_fig.add_subplot(111)
        x = list(range(12))
        balance = d["balance_month"][1:13]
        income = d["inc_month"][1:13]
        expense = d["exp_month"][1:13]
        ax2 = ax.twinx()
        bal_bars = [0.0 if b is None else b for b in balance]
        bar_colors = [C_BAL_POS if b >= 0 else C_BAL_NEG for b in bal_bars]

        ax2.bar(x, bal_bars, color=bar_colors, alpha=0.55, width=0.62, label="Balance", zorder=1)
        l1, = ax.plot(x, income, color=C_INCOME, linewidth=1.6, marker="o", markersize=3, label="Income", zorder=3)
        l2, = ax.plot(x, expense, color=C_EXPENSE, linewidth=1.6, marker="o", markersize=3, label="Expense", zorder=3)
        ax.axhline(0, color="#999999", linewidth=0.8, zorder=2)
        ax.set_xticks(x)
        ax.set_xticklabels(MONTHS, fontsize=8)
        ax.set_ylabel(f"{self.db.get_base_currency()} (Income / Expense)", fontsize=9)
        ax2.set_ylabel("Balance", fontsize=9)
        ax.grid(axis="y", alpha=0.3)
        ax.tick_params(labelsize=8)
        ax2.tick_params(labelsize=8)
        ax.yaxis.set_major_formatter(FuncFormatter(lambda v, p: f"{v:,.0f}"))
        ax2.yaxis.set_major_formatter(FuncFormatter(lambda v, p: f"{v:,.0f}"))
        ax.legend([l1, l2], ["Income", "Expense"], fontsize=8, loc="upper left")
        ax2.legend(handles=[Patch(color=C_BAL_POS, label="Balance (+)"),
                            Patch(color=C_BAL_NEG, label="Balance (-)")],
                   fontsize=7, loc="upper right")
        enable_line_hover(self.comp_canvas, self.comp_fig, ax, MONTHS, [
            {"label": "Income", "values": income, "color": C_INCOME},
            {"label": "Expense", "values": expense, "color": C_EXPENSE},
        ], self._symbol())
        self.comp_fig.tight_layout()
        self.comp_canvas.draw()

    def _draw_pies(self):
        self._draw_pie(self.inc_fig, self.inc_canvas, self._cache["inc_rows"], C_INCOME)
        self._draw_pie(self.exp_fig, self.exp_canvas, self._cache["exp_rows"], C_EXPENSE)

    def _draw_pie(self, fig, canvas, rows, accent):
        fig.clear()
        ax = fig.add_subplot(111)
        total = sum(r["total"] for r in rows)
        if total <= 0:
            ax.text(0.5, 0.5, "No Data", ha="center", va="center", fontsize=11,
                    color="gray", transform=ax.transAxes)
            ax.set_xticks([])
            ax.set_yticks([])
            fig.tight_layout()
            canvas.draw()
            return
        values = [r["total"] for r in rows]
        names = [_strip_emoji(r["name"]) for r in rows]
        labels = []
        for name, v in zip(names, values):
            pct = (v / total * 100) if total else 0
            labels.append(f"{name}\n{v:,.2f}" if pct >= 5.0 else "")
        colors = [r["color"] for r in rows] or None
        wedges, _, _ = ax.pie(values, labels=labels, colors=colors,
                              autopct=lambda p: f"{p:.1f}%" if p >= 5.0 else "",
                              textprops={"fontsize": 7},
                              startangle=90, pctdistance=0.75)
        for w in wedges:
            w.set_linewidth(0.5)
            w.set_edgecolor("white")
        ax.axis("equal")
        enable_pie_hover(fig, ax, wedges, names, values, total, self._symbol())
        fig.tight_layout()
        canvas.draw()

    def _fmt(self, v):
        return f"{v:,.2f}"

    def _fmt_sr(self, v):
        return f"{v:.1f}%"

    def _sparkline(self, values, color):
        w, h = 120, 30
        pix = QPixmap(w, h)
        pix.fill(Qt.GlobalColor.transparent)
        lbl = QLabel()
        values = [v for v in values if v is not None]
        if not values or all(v == 0 for v in values):
            lbl.setText("-")
            lbl.setStyleSheet("color: #bbb; font-size: 11px; qproperty-alignment: AlignCenter;")
            return lbl
        p = QPainter(pix)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        pen = QPen(QColor(color))
        pen.setWidth(1.5)
        p.setPen(pen)
        mn = min(values)
        mx = max(values)
        rng = (mx - mn) or 1.0
        n = len(values)
        pad = 3
        pts = []
        for i, v in enumerate(values):
            x = pad + (w - 2 * pad) * (i / (n - 1) if n > 1 else 0.5)
            y = h - pad - (h - 2 * pad) * ((v - mn) / rng)
            pts.append(QPointF(x, y))
        p.drawPolyline(pts)
        p.setBrush(QColor(color))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawEllipse(pts[-1], 2.2, 2.2)
        p.end()
        lbl.setPixmap(pix)
        lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        return lbl

    def _cell(self, text, color=None, bold=False, bg=None, align=Qt.AlignmentFlag.AlignRight):
        item = QTableWidgetItem(text)
        item.setTextAlignment(align | Qt.AlignmentFlag.AlignVCenter)
        if color:
            item.setForeground(QColor(color))
        if bold:
            f = QFont()
            f.setBold(True)
            item.setFont(f)
        if bg:
            item.setBackground(QColor(bg))
        return item

    def _fill_matrix(self):
        d = self._cache
        latest = d["latest"]
        m = self.matrix
        m.setUpdatesEnabled(False)

        n_inc = len(d["inc_rows"])
        n_exp = len(d["exp_rows"])
        total_rows = n_inc + 1 + n_exp + 1 + 2
        m.setRowCount(total_rows)

        r = 0

        def set_row_bg(row, color):
            for c in range(15):
                it = m.item(row, c)
                if it is not None:
                    it.setBackground(QColor(color))
            wgt = m.cellWidget(row, 15)
            if wgt is not None:
                wgt.setStyleSheet(f"background-color: {color};")

        def cat_rows(store, color):
            nonlocal r
            for row in store:
                m.setItem(r, 0, self._cell(row["name"], color=color, align=Qt.AlignmentFlag.AlignLeft))
                for mi in range(12):
                    has = d["txn_month"][mi + 1] > 0
                    m.setItem(r, mi + 1, self._cell("" if not has else self._fmt(row["by"][mi + 1]), color=color))
                m.setItem(r, 13, self._cell(self._fmt(row["total"]), color=color, bold=True))
                m.setItem(r, 14, self._cell(self._fmt(row["avg"]), color=color))
                m.setCellWidget(r, 15, self._sparkline(row["by"][1:latest + 1], color))
                r += 1

        cat_rows(d["inc_rows"], C_INCOME)
        # Total income
        m.setItem(r, 0, self._cell("Total Income", color=C_INCOME, bold=True, align=Qt.AlignmentFlag.AlignLeft))
        inc_trend = d["inc_month"][1:latest + 1]
        for mi in range(12):
            has = d["txn_month"][mi + 1] > 0
            val = d["inc_month"][mi + 1]
            m.setItem(r, mi + 1, self._cell("" if not has else self._fmt(val), color=C_INCOME, bold=True))
        m.setItem(r, 13, self._cell(self._fmt(d["inc_total_year"]), color=C_INCOME, bold=True))
        m.setItem(r, 14, self._cell(self._fmt(d["inc_avg"]), color=C_INCOME, bold=True))
        m.setCellWidget(r, 15, self._sparkline(inc_trend, C_INCOME))
        set_row_bg(r, "#d5f5e3")
        r += 1

        cat_rows(d["exp_rows"], C_EXPENSE)
        # Total expense
        m.setItem(r, 0, self._cell("Total Expense", color=C_EXPENSE, bold=True, align=Qt.AlignmentFlag.AlignLeft))
        exp_trend = d["exp_month"][1:latest + 1]
        for mi in range(12):
            has = d["txn_month"][mi + 1] > 0
            val = d["exp_month"][mi + 1]
            m.setItem(r, mi + 1, self._cell("" if not has else self._fmt(val), color=C_EXPENSE, bold=True))
        m.setItem(r, 13, self._cell(self._fmt(d["exp_total_year"]), color=C_EXPENSE, bold=True))
        m.setItem(r, 14, self._cell(self._fmt(d["exp_avg"]), color=C_EXPENSE, bold=True))
        m.setCellWidget(r, 15, self._sparkline(exp_trend, C_EXPENSE))
        set_row_bg(r, "#fadbd8")
        r += 1

        # Balance row
        m.setItem(r, 0, self._cell("Balance", color=C_BALANCE, bold=True, align=Qt.AlignmentFlag.AlignLeft))
        bal_trend = d["balance_month"][1:latest + 1]
        for mi in range(12):
            has = d["txn_month"][mi + 1] > 0
            bv = d["balance_month"][mi + 1]
            m.setItem(r, mi + 1, self._cell("" if not has else self._fmt(bv), color=C_BALANCE))
        m.setItem(r, 13, self._cell(self._fmt(d["balance_total"]), color=C_BALANCE, bold=True))
        m.setItem(r, 14, self._cell(self._fmt(d["balance_avg"]), color=C_BALANCE, bold=True))
        m.setCellWidget(r, 15, self._sparkline(bal_trend, C_BALANCE))
        set_row_bg(r, "#e8daef")
        r += 1

        # Saving rate row
        m.setItem(r, 0, self._cell("Saving Rate", color=C_SAVE, bold=True, align=Qt.AlignmentFlag.AlignLeft))
        for mi in range(12):
            sr = d["saving_month"][mi]
            m.setItem(r, mi + 1, self._cell("" if sr is None else self._fmt_sr(sr), color=C_SAVE))
        m.setItem(r, 13, self._cell(self._fmt_sr(d["annual_sr"]), color=C_SAVE, bold=True))
        m.setItem(r, 14, self._cell("-", color="#bbb"))
        m.setItem(r, 15, self._cell("-", color="#bbb"))
        set_row_bg(r, "#d1f2eb")
        r += 1

        m.setRowCount(r)
        m.setUpdatesEnabled(True)
        self._size_matrix()

    def _size_matrix(self):
        m = self.matrix
        m.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Interactive)
        for i in range(1, 16):
            m.horizontalHeader().setSectionResizeMode(i, QHeaderView.ResizeMode.Interactive)
        rh = 26
        for i in range(m.rowCount()):
            m.setRowHeight(i, rh)
        m.setMinimumHeight(m.rowCount() * rh + 40)
