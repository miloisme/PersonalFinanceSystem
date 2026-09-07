from PySide6.QtWidgets import (
    QApplication,
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QLabel, QStackedWidget, QFrame,
    QSizePolicy, QSpacerItem, QMessageBox
)
from PySide6.QtCore import Qt, QSize
from PySide6.QtGui import QFont, QIcon
import os
import datetime

from drive_sync import (
    is_client_configured, is_authenticated, is_available, authenticate,
    upload as drive_upload, download as drive_download,
    remote_meta, get_last_synced_at, _device_name,
)

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
        root_layout = QVBoxLayout(central_widget)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        self._build_sync_toolbar()
        root_layout.addWidget(self.sync_bar)

        main_layout = QHBoxLayout()
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)
        root_layout.addLayout(main_layout)

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

    # ----- Google Drive sync -----
    def _build_sync_toolbar(self):
        self.sync_bar = QFrame()
        self.sync_bar.setFixedHeight(44)
        self.sync_bar.setStyleSheet("QFrame { background-color: #1a252f; }")
        bar = QHBoxLayout(self.sync_bar)
        bar.setContentsMargins(12, 4, 12, 4)
        bar.setSpacing(8)

        title = QLabel("\u2601 Drive Sync")
        title.setStyleSheet("color: #ecf0f1; font-weight: bold; font-size: 13px;")
        bar.addWidget(title)

        self.btn_login = QPushButton("Login Google")
        self.btn_upload = QPushButton("\u2191 Upload")
        self.btn_download = QPushButton("\u2193 Download")
        for b in (self.btn_login, self.btn_upload, self.btn_download):
            b.setStyleSheet("""
                QPushButton { background-color: #3498db; color: white; border: none;
                              padding: 6px 14px; border-radius: 5px; font-size: 12px; }
                QPushButton:hover { background-color: #2980b9; }
                QPushButton:disabled { background-color: #566573; color: #aaa; }
            """)
            b.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_login.clicked.connect(self.on_login)
        self.btn_upload.clicked.connect(self.on_upload)
        self.btn_download.clicked.connect(self.on_download)
        bar.addWidget(self.btn_login)
        bar.addWidget(self.btn_upload)
        bar.addWidget(self.btn_download)

        bar.addSpacerItem(QSpacerItem(
            20, 10, QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum))

        self.sync_status = QLabel("")
        self.sync_status.setStyleSheet("color: #bdc3c7; font-size: 12px;")
        bar.addWidget(self.sync_status)

        self.refresh_sync_status()

    def refresh_sync_status(self):
        if not is_available():
            self.sync_status.setText("缺少 Google 库 (pip install -r requirements.txt)")
            self.btn_upload.setEnabled(False)
            self.btn_download.setEnabled(False)
            return
        if not is_client_configured():
            self.sync_status.setText("未配置 client_secret.json")
            self.btn_upload.setEnabled(False)
            self.btn_download.setEnabled(False)
            return
        if not is_authenticated():
            self.sync_status.setText("未登录 Google")
            self.btn_upload.setEnabled(False)
            self.btn_download.setEnabled(False)
            return
        last = get_last_synced_at()
        if last:
            self.sync_status.setText(f"已登录 · 上次同步: {last}")
        else:
            self.sync_status.setText("已登录 · 尚未同步")
        self.btn_upload.setEnabled(True)
        self.btn_download.setEnabled(True)

    @staticmethod
    def _iso_to_epoch(iso):
        try:
            return datetime.datetime.strptime(
                iso, "%Y-%m-%dT%H:%M:%SZ"
            ).replace(tzinfo=datetime.timezone.utc).timestamp()
        except Exception:
            return 0

    def on_login(self):
        try:
            authenticate()
            QMessageBox.information(self, "Google Drive", "登录成功，可开始同步。")
        except Exception as e:
            QMessageBox.warning(
                self, "登录失败",
                f"{e}\n\n如需重新授权，请重试登录（将弹出浏览器重新完成认证）。")
        self.refresh_sync_status()

    def on_upload(self):
        try:
            remote = remote_meta()
            last = get_last_synced_at()
            if remote and remote.get("updated_at") and last and remote["updated_at"] > last:
                resp = QMessageBox.warning(
                    self, "云端数据较新",
                    f"云端数据更新于 {remote['updated_at']}，本地上次同步于 {last}。\n"
                    "上传将覆盖云端数据，是否继续？",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
                if resp == QMessageBox.StandardButton.No:
                    return
            updated = drive_upload(self.db, device=_device_name())
            self.refresh_sync_status()
            QMessageBox.information(
                self, "上传成功", f"已上传至 Google Drive\n更新时间: {updated}")
        except Exception as e:
            QMessageBox.warning(self, "上传失败", str(e))

    def on_download(self):
        try:
            remote = remote_meta()
            last = get_last_synced_at()
            if remote and remote.get("updated_at") and last and remote["updated_at"] <= last:
                local_mtime = (
                    os.path.getmtime(self.db.db_path)
                    if os.path.exists(self.db.db_path) else 0
                )
                if local_mtime > self._iso_to_epoch(last):
                    resp = QMessageBox.warning(
                        self, "本地有未同步改动",
                        "本地数据库自上次同步后有改动，下载将覆盖本地数据，是否继续？",
                        QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
                    if resp == QMessageBox.StandardButton.No:
                        return
            drive_download(self.db)
            self.refresh_all_tabs()
            self.refresh_sync_status()
            QMessageBox.information(
                self, "下载成功", "已从 Google Drive 同步并刷新界面。")
        except Exception as e:
            QMessageBox.warning(self, "下载失败", str(e))
