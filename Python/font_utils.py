from PySide6.QtGui import QFont

CJK_FONT = QFont(["Segoe UI", "Microsoft JhengHei", "Microsoft YaHei"], 10)


def set_cjk_font(widget):
    widget.setFont(CJK_FONT)
