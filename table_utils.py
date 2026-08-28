from PySide6.QtWidgets import QHeaderView, QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLineEdit
from PySide6.QtCore import QSettings, QTimer, Qt


def enable_column_memory(table, key, stretch_column=None, default_widths=None):
    """Enable manual column resizing and persist widths across sessions.

    Every column is user-resizable (Interactive) and the last column stretches
    to fill any remaining space, so the whole table width stays adjustable.
    Widths are remembered via QSettings. When no saved widths exist,
    ``default_widths`` (list of ints, same length as column count) is applied.
    """
    header = table.horizontalHeader()
    n = table.columnCount()
    for i in range(n):
        header.setSectionResizeMode(i, QHeaderView.ResizeMode.Interactive)
    header.setStretchLastSection(True)

    settings = QSettings("FinanceManager", "ColumnWidths")
    raw = settings.value(key)
    restored = False
    if raw:
        try:
            if isinstance(raw, str):
                widths = [int(x) for x in raw.split(",") if x.strip()]
            else:
                widths = [int(x) for x in raw]
            for i, w in enumerate(widths):
                if 0 <= i < n and i != n - 1 and w > 0:
                    header.resizeSection(i, w)
            restored = any(widths)
        except Exception:
            restored = False
    if not restored and default_widths:
        for i, w in enumerate(default_widths):
            if 0 <= i < n and i != n - 1 and w > 0:
                header.resizeSection(i, w)

    header.sectionResized.connect(lambda i, o, nw: _save_columns(table, key))


def _save_columns(table, key):
    settings = QSettings("FinanceManager", "ColumnWidths")
    widths = [table.horizontalHeader().sectionSize(i) for i in range(table.columnCount())]
    settings.setValue(key, widths)


def enable_sort_filter(table, key, action_col=None, layout=None, enable_filter=True,
                       sortable=True, on_sort_change=None):
    """Enable column sorting (header click) and remember column widths / sort
    state via QSettings. When `enable_filter` is True, also adds a per-column
    filter bar (remembered via QSettings) inserted above the table.

    `action_col` (usually the last column) is excluded from filtering.

    Two sorting modes:

    * Built-in (default, `on_sort_change is None`): Qt reorders rows itself on
      header click. This is fine for tables WITHOUT interactive cell widgets,
      but Qt does NOT move cell widgets when sorting, which misplaces /
      duplicates per-row Edit/Delete buttons. So do NOT use this for tables that
      put interactive widgets in cells.

    * Data-driven (`on_sort_change` provided): the caller owns sorting. A header
      click only records the column/order (and shows the sort indicator) then
      calls `on_sort_change(col, order)`. The caller must re-sort its data model
      and repopulate the table -- which recreates cell widgets in the new order,
      keeping them aligned. This is the correct mode for tables with action
      buttons. The `sortable` flag is ignored in this mode.
    """
    enable_column_memory(table, key)
    header = table.horizontalHeader()
    n = table.columnCount()
    if action_col is None:
        action_col = -1
    settings = QSettings("FinanceManager", "ColumnWidths")
    data_driven = on_sort_change is not None

    # Capture the original (plain) header labels so we can append the ↑/↓ state
    # symbol to the active column without losing the base text.
    base_labels = [table.horizontalHeaderItem(c).text() if table.horizontalHeaderItem(c) else ""
                   for c in range(n)]

    def set_sort_indicator(col, order):
        if col is None or col < 0 or col >= n:
            return
        symbol = " ↑" if order == Qt.SortOrder.AscendingOrder else " ↓"
        for c in range(n):
            item = table.horizontalHeaderItem(c)
            if item is None:
                continue
            base = base_labels[c] if c < len(base_labels) else ""
            if c == col:
                item.setText(base + symbol)
            else:
                item.setText(base)

    # Built-in sort must stay OFF whenever cell widgets are involved.
    table.setSortingEnabled((not data_driven) and sortable)

    edits = []
    container = None

    if enable_filter:
        container = QWidget()
        cv = QVBoxLayout(container)
        cv.setContentsMargins(0, 0, 0, 0)
        cv.setSpacing(2)

        toggle = QPushButton("\u26B2 Filter")
        toggle.setCheckable(True)
        toggle.setStyleSheet(
            "QPushButton { background: #ecf0f1; border: none; padding: 3px 8px; "
            "border-radius: 3px; font-size: 11px; }"
            "QPushButton:checked { background: #3498db; color: white; }"
        )
        cv.addWidget(toggle)

        edits_row = QWidget()
        eh = QHBoxLayout(edits_row)
        eh.setContentsMargins(0, 0, 0, 0)
        eh.setSpacing(2)
        for c in range(n):
            if c == action_col:
                le = QLineEdit()
                le.setEnabled(False)
                le.setPlaceholderText("-")
            else:
                le = QLineEdit()
                le.setPlaceholderText("filter")
                le.setStyleSheet("QLineEdit { font-size: 11px; padding: 2px 4px; }")
            eh.addWidget(le)
            edits.append(le)
        edits_row.setVisible(False)
        cv.addWidget(edits_row)
        toggle.toggled.connect(lambda on: edits_row.setVisible(on))

        raw = settings.value(key + "_filter")
        if raw:
            try:
                texts = raw.split("@@") if isinstance(raw, str) else [str(x) for x in raw]
                for i, t in enumerate(texts):
                    if 0 <= i < n:
                        edits[i].setText(t)
            except Exception:
                pass

    timer = QTimer()
    timer.setSingleShot(True)
    timer.setInterval(150)

    def apply_view():
        if (not data_driven) and sortable:
            sc = settings.value(key + "_sort_col")
            so = settings.value(key + "_sort_order")
            if sc is not None and so is not None:
                sc, so = int(sc), int(so)
                if header.sortIndicatorSection() != sc or int(header.sortIndicatorOrder().value) != so:
                    table.sortByColumn(sc, Qt.SortOrder(so))
        if not enable_filter:
            return
        filters = [edits[c].text().strip().lower() for c in range(n)]
        any_filter = any(filters)
        for r in range(table.rowCount()):
            show = True
            if any_filter:
                for c in range(n):
                    f = filters[c]
                    if not f or c == action_col:
                        continue
                    item = table.item(r, c)
                    if item is None or f not in item.text().lower():
                        show = False
                        break
            table.setRowHidden(r, not show)

    def schedule():
        timer.start()

    for le in edits:
        le.textChanged.connect(schedule)

    timer.timeout.connect(apply_view)
    table.cellChanged.connect(lambda r, c: schedule())

    if data_driven:
        sort_state = {"col": None, "order": None}

        def on_section(col):
            if col == action_col:
                return
            if sort_state["col"] == col and sort_state["order"] == Qt.SortOrder.AscendingOrder:
                order = Qt.SortOrder.DescendingOrder
            else:
                order = Qt.SortOrder.AscendingOrder
            sort_state["col"] = col
            sort_state["order"] = order
            header.setSortIndicator(col, order)
            settings.setValue(key + "_sort_col", col)
            settings.setValue(key + "_sort_order", int(order.value))
            set_sort_indicator(col, order)
            on_sort_change(col, order)
        header.sectionClicked.connect(on_section)
    else:
        def on_sort(col, order):
            settings.setValue(key + "_sort_col", col)
            settings.setValue(key + "_sort_order", int(order.value))
            set_sort_indicator(col, order)
        if sortable:
            header.sortIndicatorChanged.connect(on_sort)
        QTimer.singleShot(0, apply_view)

    if data_driven:
        sc = settings.value(key + "_sort_col")
        so = settings.value(key + "_sort_order")
        if sc is not None and so is not None:
            sc, so = int(sc), int(so)
            sort_state["col"] = sc
            sort_state["order"] = Qt.SortOrder(so)
            header.setSortIndicator(sc, Qt.SortOrder(so))
            set_sort_indicator(sc, Qt.SortOrder(so))
            # Defer so the caller finishes its own initial population first.
            QTimer.singleShot(0, lambda: on_sort_change(sc, Qt.SortOrder(so)))

    if enable_filter and layout is not None and container is not None:
        idx = layout.indexOf(table)
        if idx >= 0:
            layout.insertWidget(idx, container)
        else:
            layout.addWidget(container)

    return container

