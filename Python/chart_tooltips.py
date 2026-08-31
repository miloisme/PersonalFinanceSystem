"""Reusable Matplotlib hover-tooltip helpers for pie and line/bar charts."""
from matplotlib.text import Annotation


def enable_pie_hover(fig, ax, wedges, names, values, total, symbol=""):
    """Attach hover tooltip to pie-chart wedges.

    Parameters
    ----------
    fig : Figure
    ax : Axes
    wedges : list of matplotlib.patches.Wedge
    names : list[str]
    values : list[float]
    total : float
    symbol : str  currency symbol prefix
    """
    annot = ax.annotate("", xy=(0, 0), xytext=(0, 0),
                        textcoords="offset points",
                        fontsize=9, fontweight="bold",
                        bbox=dict(boxstyle="round,pad=0.4", fc="white",
                                  ec="#888888", alpha=0.92),
                        arrowprops=dict(arrowstyle="-", color="#888888",
                                        lw=0.8),
                        zorder=100)
    annot.set_visible(False)

    def _motion(event):
        if event.inaxes != ax:
            if annot.get_visible():
                annot.set_visible(False)
                fig.canvas.draw_idle()
            return
        for i, w in enumerate(wedges):
            contains, _ = w.contains(event)
            if contains:
                ang = (w.theta2 + w.theta1) / 2.0
                import math
                x = 1.25 * math.cos(math.radians(ang))
                y = 1.25 * math.sin(math.radians(ang))
                pct = (values[i] / total * 100) if total else 0
                label = (f"{names[i]}\n"
                         f"{symbol}{values[i]:,.2f}\n"
                         f"{pct:.1f}%")
                annot.set_text(label)
                annot.xy = (x, y)
                annot.set_visible(True)
                fig.canvas.draw_idle()
                return
        if annot.get_visible():
            annot.set_visible(False)
            fig.canvas.draw_idle()

    fig.canvas.mpl_connect("motion_notify_event", _motion)


def enable_line_hover(canvas, fig, ax, labels, series, symbol=""):
    """Attach hover tooltip to line / bar charts.

    Parameters
    ----------
    canvas : FigureCanvas
    fig : Figure
    ax : Axes
    labels : list[str]  – period labels for x-axis
    series : list[dict] – each {"label": str, "values": list[float|None], "color": str}
    symbol : str
    """
    annot = ax.annotate("", xy=(0, 0), xytext=(0, 0),
                        textcoords="offset points",
                        fontsize=8, fontweight="bold",
                        bbox=dict(boxstyle="round,pad=0.3", fc="white",
                                  ec="#888888", alpha=0.92),
                        arrowprops=dict(arrowstyle="-", color="#888888",
                                        lw=0.8),
                        zorder=100)
    annot.set_visible(False)

    vline = ax.axvline(x=0, color="#888888", linewidth=0.8,
                       linestyle="--", visible=False, zorder=99)

    n = len(labels)

    def _motion(event):
        if event.inaxes != ax:
            if annot.get_visible():
                annot.set_visible(False)
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
            if annot.get_visible():
                annot.set_visible(False)
                vline.set_visible(False)
                fig.canvas.draw_idle()
            return
        period = labels[idx] if idx < len(labels) else ""
        text = period + "\n" + "\n".join(lines)
        annot.set_text(text)
        annot.xy = (idx, event.ydata or 0)
        annot.set_visible(True)
        vline.set_xdata([idx, idx])
        vline.set_visible(True)
        fig.canvas.draw_idle()

    canvas.mpl_connect("motion_notify_event", _motion)
