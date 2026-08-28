from PySide6.QtWidgets import (
    QApplication,
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QStackedWidget, QFrame,
    QSizePolicy, QSpacerItem
)
from PySide6.QtCore import Qt, QSize
from PySide6.QtGui import QFont, QIcon

from accounts_tab import AccountsTab
from balance_tab import BalanceTab
from transactions_tab import TransactionsTab
from debts_tab import DebtsTab
from charts_tab import ChartsTab
from budget_tab import BudgetTab
from sql_tab import SQLTab
from notes_tab import NotesTab
from settings_tab import SettingsTab
from font_utils import set_cjk_font

DIALOG_STYLE = """
    QDialog {
        background-color: #f5f6fa;
    }
    QLabel {
        color: #2c3e50;
    }
    QLineEdit, QComboBox, QDoubleSpinBox, QTextEdit, QDateEdit {
        background-color: white;
        color: #2c3e50;
        border: 1px solid #ddd;
        border-radius: 5px;
        padding: 8px;
        font-size: 13px;
    }
    QLineEdit:focus, QComboBox:focus, QDoubleSpinBox:focus, QTextEdit:focus, QDateEdit:focus {
        border: 1px solid #3498db;
    }
    QComboBox QAbstractItemView {
        background-color: white;
        color: #2c3e50;
        selection-background-color: #3498db;
        selection-color: white;
    }
    QPushButton {
        background-color: #3498db;
        color: white;
        border: none;
        padding: 8px 16px;
        border-radius: 5px;
        font-size: 13px;
    }
    QPushButton:hover {
        background-color: #2980b9;
    }
    QFormLayout QLabel {
        color: #2c3e50;
        font-size: 13px;
    }
"""


class MainWindow(QMainWindow):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.setWindowTitle("Personal Finance Manager")
        screen = QApplication.primaryScreen().availableGeometry()
        w = int(screen.width() * 0.80)
        h = int(screen.height() * 0.90)
        self.resize(w, h)
        self.move((screen.width() - w) // 2, (screen.height() - h) // 2)
        self.setMinimumSize(1024, 680)
        from currency_converter import CurrencyConverter, get_used_currencies
        CurrencyConverter().prefetch_rates(
            get_used_currencies(self.db), self.db.get_base_currency()
        )
        self.setup_ui()

    def setup_ui(self):
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QHBoxLayout(central_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        sidebar = QFrame()
        sidebar.setFixedWidth(190)
        sidebar.setStyleSheet("""
            QFrame {
                background-color: #2c3e50;
                border-right: 1px solid #1a252f;
            }
        """)
        sidebar_layout = QVBoxLayout(sidebar)
        sidebar_layout.setContentsMargins(0, 0, 0, 0)
        sidebar_layout.setSpacing(0)

        logo_label = QLabel("\U0001F4B0 Finance Manager")
        logo_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        set_cjk_font(logo_label)
        logo_label.setStyleSheet("""
            QLabel {
                color: white;
                font-size: 17px;
                font-weight: bold;
                padding: 14px;
                background-color: #1a252f;
            }
        """)
        sidebar_layout.addWidget(logo_label)

        nav_buttons = [
            ("\U0001F4CA Dashboard", 0),
            ("\U0001F4C8 Budget", 1),
            ("\U0001F4B3 Accounts", 2),
            ("\u2696\uFE0F Balance", 3),
            ("\U0001F4DD Transactions", 4),
            ("\U0001F3E6 Debts", 5),
            ("\U0001F4DD Notes", 6),
            ("\U0001F4BB SQL", 7),
            ("\u2699\uFE0F Settings", 8),
        ]

        self.nav_buttons = []
        for text, index in nav_buttons:
            btn = QPushButton(text)
            btn.setCheckable(True)
            btn.setMinimumHeight(42)
            set_cjk_font(btn)
            btn.setStyleSheet("""
                QPushButton {
                    color: white;
                    text-align: left;
                    padding: 8px 16px;
                    border: none;
                    font-size: 13px;
                    background-color: transparent;
                }
                QPushButton:hover {
                    background-color: #34495e;
                }
                QPushButton:checked {
                    background-color: #3498db;
                    border-left: 3px solid #2980b9;
                }
            """)
            btn.clicked.connect(lambda checked, idx=index: self.switch_tab(idx))
            sidebar_layout.addWidget(btn)
            self.nav_buttons.append(btn)

        sidebar_layout.addSpacerItem(QSpacerItem(
            20, 40, QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Expanding
        ))

        main_layout.addWidget(sidebar)

        self.stack = QStackedWidget()
        self.stack.setStyleSheet("background-color: #ecf0f1;")

        self.accounts_tab = AccountsTab(self.db)
        self.balance_tab = BalanceTab(self.db)
        self.transactions_tab = TransactionsTab(self.db)
        self.debts_tab = DebtsTab(self.db)
        self.charts_tab = ChartsTab(self.db)
        self.budget_tab = BudgetTab(self.db)
        self.sql_tab = SQLTab(self.db)
        self.notes_tab = NotesTab(self.db)
        self.settings_tab = SettingsTab(self.db)
        self.settings_tab.base_currency_changed.connect(self.on_base_currency_changed)

        self.stack.addWidget(self.charts_tab)
        self.stack.addWidget(self.budget_tab)
        self.stack.addWidget(self.accounts_tab)
        self.stack.addWidget(self.balance_tab)
        self.stack.addWidget(self.transactions_tab)
        self.stack.addWidget(self.debts_tab)
        self.stack.addWidget(self.notes_tab)
        self.stack.addWidget(self.sql_tab)
        self.stack.addWidget(self.settings_tab)

        main_layout.addWidget(self.stack)

        self.nav_buttons[0].setChecked(True)
        self.switch_tab(0)
        self.update_total_balance()

    @staticmethod
    def get_dialog_style():
        return DIALOG_STYLE

    def on_base_currency_changed(self, currency: str):
        from currency_converter import CurrencyConverter, get_used_currencies
        CurrencyConverter().prefetch_rates(
            get_used_currencies(self.db), currency
        )
        self.refresh_all_tabs()

    def refresh_all_tabs(self):
        self.charts_tab.refresh()
        self.budget_tab.refresh()
        self.accounts_tab.refresh()
        self.balance_tab.refresh()
        self.transactions_tab.refresh()
        self.debts_tab.refresh()
        self.notes_tab.refresh()
        self.update_total_balance()

    def switch_tab(self, index: int):
        self.stack.setCurrentIndex(index)
        for i, btn in enumerate(self.nav_buttons):
            btn.setChecked(i == index)
        if index == 0:
            self.charts_tab.refresh()
        elif index == 1:
            self.budget_tab.refresh()
        elif index == 2:
            self.accounts_tab.refresh()
        elif index == 3:
            self.balance_tab.refresh()
        elif index == 4:
            self.transactions_tab.refresh()
        elif index == 5:
            self.debts_tab.refresh()
        elif index == 6:
            self.notes_tab.refresh()
        elif index == 7:
            self.sql_tab.refresh()
        self.update_total_balance()

    def update_total_balance(self):
        pass
