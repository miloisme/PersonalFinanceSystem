import sqlite3
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTextEdit, QTableWidget, QTableWidgetItem, QHeaderView,
    QFrame, QMessageBox
)
from PySide6.QtCore import Qt
from PySide6.QtGui import QColor

from font_utils import set_cjk_font


class SQLTab(QWidget):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(8)

        header = QLabel("SQL Console")
        set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        layout.addWidget(header)

        hint = QLabel("Run arbitrary SQL against the local database. SELECT queries show results; "
                      "other statements show the affected row count.")
        set_cjk_font(hint)
        hint.setStyleSheet("font-size: 12px; color: #7f8c8d;")
        hint.setWordWrap(True)
        layout.addWidget(hint)

        self.editor = QTextEdit()
        set_cjk_font(self.editor)
        self.editor.setPlaceholderText("SELECT * FROM accounts;")
        self.editor.setStyleSheet("""
            QTextEdit {
                background-color: white; border: 1px solid #ddd; border-radius: 8px;
                padding: 8px; font-family: Consolas, monospace; font-size: 13px; color: #2c3e50;
            }
        """)
        self.editor.setMinimumHeight(120)
        layout.addWidget(self.editor)

        btn_row = QHBoxLayout()
        run_btn = QPushButton("Execute")
        run_btn.setStyleSheet("QPushButton { background-color: #3498db; color: white; border: none; padding: 8px 20px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #2980b9; }")
        run_btn.clicked.connect(self.run_sql)
        clear_btn = QPushButton("Clear")
        clear_btn.setStyleSheet("QPushButton { background-color: #95a5a6; color: white; border: none; padding: 8px 20px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #7f8c8d; }")
        clear_btn.clicked.connect(lambda: self.editor.clear())
        btn_row.addWidget(run_btn)
        btn_row.addWidget(clear_btn)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        self.status_label = QLabel("")
        set_cjk_font(self.status_label)
        self.status_label.setStyleSheet("font-size: 12px; color: #7f8c8d;")
        layout.addWidget(self.status_label)

        self.result_table = QTableWidget()
        self.result_table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.result_table.verticalHeader().setVisible(False)
        self.result_table.setStyleSheet("""
            QTableWidget { background-color: white; border-radius: 8px; gridline-color: #ecf0f1; }
            QHeaderView::section { background-color: #f8f9fa; padding: 6px; border: none; font-weight: bold; }
        """)
        layout.addWidget(self.result_table, 1)

    def run_sql(self):
        sql = self.editor.toPlainText().strip()
        if not sql:
            return
        try:
            conn = sqlite3.connect(self.db.db_path)
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(sql)
            lowered = sql.lower()
            is_select = lowered.startswith("select") or lowered.startswith("pragma") or lowered.startswith("explain")
            if is_select:
                rows = cur.fetchall()
                self._show_rows(rows)
                self.status_label.setText(f"{len(rows)} row(s) returned.")
            else:
                conn.commit()
                self.status_label.setText(f"Executed. {cur.rowcount} row(s) affected.")
                self.result_table.setRowCount(0)
                self.result_table.setColumnCount(0)
                self._refresh_other_tabs()
            conn.close()
        except Exception as e:
            self.result_table.setRowCount(0)
            self.result_table.setColumnCount(0)
            self.status_label.setText("Error: " + str(e))
            QMessageBox.warning(self, "SQL Error", str(e))

    def _show_rows(self, rows):
        if not rows:
            self.result_table.setRowCount(0)
            self.result_table.setColumnCount(0)
            return
        cols = rows[0].keys()
        self.result_table.setColumnCount(len(cols))
        self.result_table.setHorizontalHeaderLabels(cols)
        self.result_table.setRowCount(len(rows))
        for i, row in enumerate(rows):
            for j, col in enumerate(cols):
                val = row[col]
                item = QTableWidgetItem("" if val is None else str(val))
                item.setForeground(QColor("#2c3e50"))
                self.result_table.setItem(i, j, item)
        self.result_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.ResizeToContents)

    def _refresh_other_tabs(self):
        win = self.window()
        if hasattr(win, "refresh_all_tabs"):
            win.refresh_all_tabs()

    def refresh(self):
        pass
