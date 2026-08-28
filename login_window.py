import os

from PySide6.QtWidgets import (
    QDialog, QVBoxLayout, QLabel, QLineEdit, QPushButton, QFrame
)
from PySide6.QtCore import Qt

from database import Database
from db_crypto import (
    load_meta, derive_key, check_verifier, decrypt_file, enc_path
)
from font_utils import set_cjk_font as _set_cjk_font

CARD_STYLE = """
    QFrame {
        background-color: white;
        border-radius: 10px;
        border: none;
    }
"""

BTN_STYLE = """
    QPushButton {
        color: white;
        background-color: #3498db;
        border: none;
        padding: 10px 20px;
        border-radius: 6px;
        font-size: 14px;
    }
    QPushButton:hover { background-color: #2980b9; }
"""


class LoginWindow(QDialog):
    def __init__(self, db_path: str, parent=None):
        super().__init__(parent)
        self.db_path = db_path
        self.key = None
        self.db = None

        self.meta = load_meta(db_path)
        self.encrypted = bool(self.meta.get("encryption"))
        self.salt = None
        self.verifier = None
        if self.encrypted:
            try:
                self.salt = bytes.fromhex(self.meta.get("salt", ""))
                self.verifier = self.meta.get("verifier", "")
            except Exception:
                self.encrypted = False

        self.setWindowTitle("Personal Finance System")
        self.setMinimumWidth(380)
        self.setMinimumHeight(260)
        self.setStyleSheet("QDialog { background-color: #ecf0f1; }")
        self.setup_ui()

    def setup_ui(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(28, 28, 28, 28)
        root.setSpacing(14)

        card = QFrame()
        card.setStyleSheet(CARD_STYLE)
        card_layout = QVBoxLayout(card)
        card_layout.setContentsMargins(22, 22, 22, 22)
        card_layout.setSpacing(12)

        title = QLabel("Personal Finance System")
        title.setStyleSheet("font-size: 18px; font-weight: bold; color: #2c3e50;")
        title.setAlignment(Qt.AlignCenter)
        _set_cjk_font(title)
        card_layout.addWidget(title)

        if self.encrypted:
            subtitle = QLabel("This database is encrypted (AES-256).\nEnter your password to unlock.")
            self.pw_edit = QLineEdit()
            self.pw_edit.setEchoMode(QLineEdit.EchoMode.Password)
            self.pw_edit.setPlaceholderText("Password")
            self.pw_edit.returnPressed.connect(self._try_open)
            self.pw_edit.setMinimumHeight(34)
        else:
            subtitle = QLabel("Encryption is disabled.\nClick Open to continue.")
            self.pw_edit = QLineEdit()
            self.pw_edit.setVisible(False)

        subtitle.setStyleSheet("font-size: 12px; color: #7f8c8d;")
        subtitle.setAlignment(Qt.AlignCenter)
        _set_cjk_font(subtitle)
        card_layout.addWidget(subtitle)
        card_layout.addWidget(self.pw_edit)

        self.error_label = QLabel("")
        self.error_label.setStyleSheet("font-size: 12px; color: #e74c3c;")
        self.error_label.setAlignment(Qt.AlignCenter)
        self.error_label.setWordWrap(True)
        _set_cjk_font(self.error_label)
        card_layout.addWidget(self.error_label)

        open_btn = QPushButton("Unlock" if self.encrypted else "Open")
        open_btn.setStyleSheet(BTN_STYLE)
        open_btn.setMinimumHeight(38)
        open_btn.clicked.connect(self._try_open)
        card_layout.addWidget(open_btn)

        root.addWidget(card)
        root.addStretch()

        note = QLabel("Your data stays on this device. Encryption uses AES-256 with a "
                      "password-derived key (PBKDF2).")
        note.setStyleSheet("font-size: 10px; color: #95a5a6;")
        note.setAlignment(Qt.AlignCenter)
        note.setWordWrap(True)
        _set_cjk_font(note)
        root.addWidget(note)

        if not self.encrypted:
            self.pw_edit.setFocusPolicy(Qt.FocusPolicy.NoFocus)

    def _try_open(self):
        self.error_label.setText("")
        if self.encrypted:
            password = self.pw_edit.text()
            if not password:
                self.error_label.setText("Please enter your password.")
                return
            if not self.salt or not self.verifier:
                self.error_label.setText("Encryption metadata is missing or corrupt.")
                return
            key = derive_key(password, self.salt)
            if not check_verifier(key, self.verifier):
                self.error_label.setText("Incorrect password. Please try again.")
                return
            src = enc_path(self.db_path)
            if not os.path.exists(src):
                self.error_label.setText(
                    "Encrypted data file not found. Cannot unlock the database."
                )
                return
            try:
                decrypt_file(src, self.db_path, key)
            except Exception:
                self.error_label.setText("Failed to decrypt the database.")
                return
            self.key = key

        try:
            self.db = Database(self.db_path)
            self.db.crypto_key = self.key
        except Exception as e:
            self.error_label.setText(f"Failed to open database: {e}")
            return
        self.accept()
