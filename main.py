import sys
import os
import gc
import sqlite3

os.environ["QT_AUTO_SCREEN_SCALE_FACTOR"] = "1"

import matplotlib
matplotlib.use('QtAgg')
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties, fontManager

matplotlib.rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'Microsoft YaHei', 'Segoe UI', 'DejaVu Sans']
matplotlib.rcParams['font.family'] = 'sans-serif'
matplotlib.rcParams['axes.unicode_minus'] = False


from PySide6.QtWidgets import QApplication
from PySide6.QtCore import Qt, QLocale
from PySide6.QtGui import QFont

from database import Database
from main_window import MainWindow
from login_window import LoginWindow
from db_crypto import load_meta, enc_path, encrypt_file, secure_delete

FONT_FAMILY = '"Segoe UI", "Microsoft JhengHei", "Microsoft YaHei", sans-serif'

FONT_STYLE = f"font-family: {FONT_FAMILY};"


def main():
    app = QApplication(sys.argv)

    db_path = "finance.db"

    def _attempt_db(path):
        try:
            return Database(path)
        except sqlite3.DatabaseError:
            return None

    def open_plaintext(path):
        db = _attempt_db(path)
        if db is not None:
            return db
        gc.collect()
        if os.path.exists(enc_path(path)) and os.path.exists(path):
            try:
                os.remove(path)
            except Exception:
                pass
        return _attempt_db(path)

    db = open_plaintext(db_path)

    QLocale.setDefault(QLocale(QLocale.English, QLocale.UnitedStates))

    app.setStyle("Fusion")

    font = QFont()
    font.setFamilies(["Segoe UI", "Microsoft JhengHei", "Microsoft YaHei"])
    font.setPointSize(10)
    app.setFont(font)

    app.setStyleSheet(f"""
        QMainWindow {{
            background-color: #ecf0f1;
        }}
        QWidget {{
            font-family: {FONT_FAMILY};
            color: #2c3e50;
        }}
        QLabel {{
            font-family: {FONT_FAMILY};
            color: #2c3e50;
        }}
        QPushButton {{
            font-family: {FONT_FAMILY};
        }}
        QTableWidget {{
            font-family: {FONT_FAMILY};
            color: #2c3e50;
        }}
        QHeaderView::section {{
            font-family: {FONT_FAMILY};
            color: #2c3e50;
        }}
        QScrollBar:vertical {{
            border: none;
            background-color: #f0f0f0;
            width: 10px;
            margin: 0;
        }}
        QScrollBar::handle:vertical {{
            background-color: #c0c0c0;
            min-height: 30px;
            border-radius: 5px;
        }}
        QScrollBar::handle:vertical:hover {{
            background-color: #a0a0a0;
        }}
        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
            height: 0;
        }}
        QScrollBar:horizontal {{
            border: none;
            background-color: #f0f0f0;
            height: 10px;
            margin: 0;
        }}
        QScrollBar::handle:horizontal {{
            background-color: #c0c0c0;
            min-width: 30px;
            border-radius: 5px;
        }}
        QScrollBar::handle:horizontal:hover {{
            background-color: #a0a0a0;
        }}
        QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {{
            width: 0;
        }}
    """)

    login = LoginWindow(db_path)
    if not login.exec():
        sys.exit(0)

    db = login.db
    window = MainWindow(db)
    window.show()

    rc = app.exec()

    meta = load_meta(db.db_path)
    if meta.get("encryption") and db.crypto_key:
        encrypt_file(db.db_path, enc_path(db.db_path), db.crypto_key)
        secure_delete(db.db_path)

    sys.exit(rc)


if __name__ == "__main__":
    main()
