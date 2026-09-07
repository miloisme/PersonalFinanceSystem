"""Reusable hover-tooltip helpers for pie and line/bar charts.

Tooltips are rendered as a single, shared floating Qt panel so they can appear
above neighboring charts/cards (instead of being clipped by their containers)
without piling up multiple stray windows.

Design choices to avoid common tooltip pitfalls:
* One global (module-level) tooltip is reused by every chart, so at most one
  panel can ever be visible at a time.
* The panel uses the ``Qt.Tool`` window flag (not ``WindowStaysOnTopHint``),
  which ties it to the app's active top-level window. It therefore disappears
  automatically once the user switches to another application, and never
  lingers on top of unrelated windows.
* The panel is mouse-transparent so it never blocks chart interaction.
"""
from PySide6.QtCore import Qt, QPoint
from PySide6.QtWidgets import QFrame, QLabel, QVBoxLayout


class _FloatingTooltip(QFrame):
    """Single, always-in-app, mouse-transparent floating value label."""

    def __init__(self):
        super().__init__(None, Qt.WindowType.Tool | Qt.WindowType.FramelessWindowHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self.setAttribute(Qt.WidgetAttribute.WA_ShowWithoutActivating)
        self.setStyleSheet(
            "_FloatingTooltip { background-color: white; border: 1px solid #888888; "
            "border-radius: 5px; }"
        )
        self._label = QLabel(self)
        self._label.setStyleSheet(
            "QLabel { background: transparent; color: #222222; padding: 4px 8px; "
            "font-size: 11px; font-weight: bold; }"
        )
        lay = QVBoxLayout(self)
        lay.setContentsMargins(6, 4, 6, 4)
        lay.addWidget(self._label)
        self.hide()

    def show_at(self, text, global_pos):
        self._label.setText(text)
        self.adjustSize()
        self.show()
        self.raise_()
        # Offset a little from the cursor so it doesn't cover the point.
        self.move(global_pos.x() + 14, global_pos.y() + 14)

    def hide_tip(self):
        if self.isVisible():
            self.hide()


_TOP_LEVEL_TIP = None


def _get_global_tip():
    """Return the single shared tooltip instance."""
    global _TOP_LEVEL_TIP
    if _TOP_LEVEL_TIP is None:
        _TOP_LEVEL_TIP = _FloatingTooltip()
    return _TOP_LEVEL_TIP


class _HoverBinder:
    """Tracks one motion handler per canvas, sharing the single tooltip.

    Re-binding disconnects any previously attached handler for the same canvas
    so handlers never accumulate across redraws. When the pointer physically
    leaves the canvas, the shared tooltip is hidden too (via matplotlib's
    ``figure_leave_event``), so stray panels never linger.
    """

    def __init__(self, canvas):
        self.canvas = canvas
        self._cid = None
        self._leave_cid = None
        self._on_leave_cb = None

    def _handle_leave(self, event=None):
        _get_global_tip().hide_tip()
        if self._on_leave_cb is not None:
            self._on_leave_cb()

    def rebind(self, handler, on_leave=None):
        if self._cid is not None:
            try:
                self.canvas.mpl_disconnect(self._cid)
            except Exception:
                pass
            self._cid = None
        if self._leave_cid is not None:
            try:
                self.canvas.mpl_disconnect(self._leave_cid)
            except Exception:
                pass
            self._leave_cid = None
        self._on_leave_cb = on_leave
        self._cid = self.canvas.mpl_connect("motion_notify_event", handler)
        self._leave_cid = self.canvas.mpl_connect("figure_leave_event", self._handle_leave)


_BINDERS = {}


def _get_binder(canvas):
    binder = _BINDERS.get(id(canvas))
    if binder is None:
        binder = _HoverBinder(canvas)
        _BINDERS[id(canvas)] = binder
        try:
            canvas.destroyed.connect(lambda *_: _BINDERS.pop(id(canvas), None))
        except Exception:
            pass
    return binder


def _canvas_global_pos(canvas, event):
    """Convert a matplotlib mouse event to a global screen position.

    Matplotlib's ``event.x / event.y`` are pixels in the figure with an origin
    at the bottom-left; Qt widgets use a top-left origin.
    """
    x = getattr(event, "x", None)
    y = getattr(event, "y", None)
    if x is None or y is None:
        return None
    qx = int(x)
    qy = max(0, int(canvas.height()) - int(y))
    return canvas.mapToGlobal(QPoint(qx, qy))


def enable_pie_hover(fig, ax, wedges, names, values, total, symbol=""):
    """Attach hover tooltip to pie-chart wedges (floating Qt panel)."""
    import math

    canvas = fig.canvas
    binder = _get_binder(canvas)

    def _motion(event):
        tip = _get_global_tip()
        if event.inaxes != ax:
            tip.hide_tip()
            return
        for i, w in enumerate(wedges):
            contains, _ = w.contains(event)
            if contains:
                pct = (values[i] / total * 100) if total else 0
                label = (f"{names[i]}\n"
                         f"{symbol}{values[i]:,.2f}\n"
                         f"{pct:.1f}%")
                pos = _canvas_global_pos(canvas, event)
                if pos is not None:
                    tip.show_at(label, pos)
                return
        tip.hide_tip()

    binder.rebind(_motion)


def enable_line_hover(canvas, fig, ax, labels, series, symbol=""):
    """Attach hover tooltip to line / bar charts (floating Qt panel)."""
    binder = _get_binder(canvas)

    vline = ax.axvline(x=0, color="#888888", linewidth=0.8,
                       linestyle="--", visible=False, zorder=99)

    n = len(labels)

    def _motion(event):
        tip = _get_global_tip()
        if event.inaxes != ax:
            tip.hide_tip()
            if vline.get_visible():
                vline.set_visible(False)
                fig.canvas.draw_idle()
            return
        xdata = event.xdata
        if xdata is None:
            return
        idx = max(0, min(n - 1, round(xdata)))
        lines = []
        for s in series:
            v = s["values"][idx] if idx < len(s["values"]) else None
            if v is not None:
                lines.append(f'{s["label"]}: {symbol}{v:,.2f}')
        if not lines:
            tip.hide_tip()
            if vline.get_visible():
                vline.set_visible(False)
                fig.canvas.draw_idle()
            return
        period = labels[idx] if idx < len(labels) else ""
        text = period + "\n" + "\n".join(lines)
        pos = _canvas_global_pos(canvas, event)
        if pos is not None:
            tip.show_at(text, pos)
        vline.set_xdata([idx, idx])
        vline.set_visible(True)
        fig.canvas.draw_idle()

    def _on_leave():
        if vline.get_visible():
            vline.set_visible(False)
            fig.canvas.draw_idle()

    binder.rebind(_motion, on_leave=_on_leave)
