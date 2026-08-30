from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QListWidget, QListWidgetItem, QDialog, QTextEdit, QFrame
)
from PySide6.QtCore import Qt
from font_utils import set_cjk_font as _set_cjk_font


class NotesTab(QWidget):
    def __init__(self, db):
        super().__init__()
        self.db = db
        self.setup_ui()
        self.refresh()

    def setup_ui(self):
        outer = QVBoxLayout(self)
        outer.setContentsMargins(12, 10, 12, 10)
        outer.setSpacing(8)

        header_row = QHBoxLayout()
        header = QLabel("Notes")
        _set_cjk_font(header)
        header.setStyleSheet("font-size: 20px; font-weight: bold; color: #2c3e50;")
        header_row.addWidget(header)
        header_row.addStretch()
        add_btn = QPushButton("+ Add Note")
        add_btn.setStyleSheet("""
            QPushButton {
                background-color: #27ae60; color: white; border: none;
                padding: 7px 18px; border-radius: 5px; font-size: 13px;
            }
            QPushButton:hover { background-color: #1e8449; }
        """)
        add_btn.clicked.connect(self._add_note)
        header_row.addWidget(add_btn)
        outer.addLayout(header_row)

        self.note_list = QListWidget()
        self.note_list.setStyleSheet("""
            QListWidget { background-color: white; border: 1px solid #ecf0f1; border-radius: 8px; padding: 4px; }
            QListWidget::item { padding: 8px; border-bottom: 1px solid #f1f2f6; }
            QListWidget::item:selected { background-color: #eaf4fc; color: #2c3e50; }
        """)
        self.note_list.itemDoubleClicked.connect(lambda _: self._edit_note())
        outer.addWidget(self.note_list, 1)

        btn_row = QHBoxLayout()
        edit_btn = QPushButton("Edit")
        edit_btn.setStyleSheet("QPushButton { background-color: #f39c12; color: white; border: none; padding: 6px 20px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #e67e22; }")
        edit_btn.clicked.connect(self._edit_note)
        del_btn = QPushButton("Delete")
        del_btn.setStyleSheet("QPushButton { background-color: #e74c3c; color: white; border: none; padding: 6px 20px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #c0392b; }")
        del_btn.clicked.connect(self._delete_note)
        btn_row.addStretch()
        btn_row.addWidget(edit_btn)
        btn_row.addWidget(del_btn)
        outer.addLayout(btn_row)

    def _preview(self, content):
        first = (content or "").strip().splitlines()
        if not first:
            return "(empty note)"
        line = first[0]
        return line if len(line) <= 60 else line[:60] + "…"

    def refresh(self):
        self.note_list.clear()
        for n in self.db.get_notes():
            item = QListWidgetItem(self._preview(n["content"]))
            item.setData(Qt.ItemDataRole.UserRole, n["id"])
            self.note_list.addItem(item)

    def _selected_id(self):
        item = self.note_list.currentItem()
        if item is None:
            return None
        return item.data(Qt.ItemDataRole.UserRole)

    def _note_dialog(self, content=""):
        dlg = QDialog(self)
        dlg.setWindowTitle("Note" if content else "New Note")
        dlg.setMinimumWidth(420)
        dlg.setMinimumHeight(260)
        layout = QVBoxLayout(dlg)
        editor = QTextEdit()
        editor.setAcceptRichText(False)
        editor.setPlainText(content)
        _set_cjk_font(editor)
        editor.setStyleSheet("""
            QTextEdit { background-color: white; border: 1px solid #ecf0f1;
                       border-radius: 8px; font-size: 13px; padding: 10px; }
        """)
        layout.addWidget(editor)
        btns = QHBoxLayout()
        save = QPushButton("Save")
        save.setStyleSheet("QPushButton { background-color: #3498db; color: white; border: none; padding: 7px 20px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #2980b9; }")
        save.clicked.connect(dlg.accept)
        cancel = QPushButton("Cancel")
        cancel.setStyleSheet("QPushButton { background-color: #95a5a6; color: white; border: none; padding: 7px 20px; border-radius: 5px; font-size: 13px; } QPushButton:hover { background-color: #7f8c8d; }")
        cancel.clicked.connect(dlg.reject)
        btns.addStretch()
        btns.addWidget(save)
        btns.addWidget(cancel)
        layout.addLayout(btns)
        if dlg.exec():
            return editor.toPlainText()
        return None

    def _add_note(self):
        text = self._note_dialog("")
        if text is not None and text.strip():
            self.db.add_note(text)
            self.refresh()

    def _edit_note(self):
        nid = self._selected_id()
        if nid is None:
            return
        notes = [n for n in self.db.get_notes() if n["id"] == nid]
        if not notes:
            return
        text = self._note_dialog(notes[0]["content"])
        if text is not None:
            self.db.update_note(nid, text)
            self.refresh()

    def _delete_note(self):
        nid = self._selected_id()
        if nid is None:
            return
        from PySide6.QtWidgets import QMessageBox
        if QMessageBox.question(self, "Confirm", "Delete this note?",
                               QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No) == QMessageBox.StandardButton.Yes:
            self.db.delete_note(nid)
            self.refresh()
