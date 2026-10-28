package cl.almejo.vsim.ui.memory;

import javax.swing.Icon;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.Map;

/**
 * A label that never asks for more room than {@code preferredWidth}: when its text does not fit it paints it with the
 * middle cut out ({@code para-enl…sprites.hex}) so both ends stay readable, and offers the full text (or an explicit
 * detail, such as a full path) as its tooltip. {@link #getText()} always returns the full text.
 */
final class ElidedLabel extends JLabel {

    private static final String ELLIPSIS = "…";
    private static final int ICON_GAP = 4;

    private final int preferredWidth;
    private String detail;

    ElidedLabel(int preferredWidth) {
        this.preferredWidth = preferredWidth;
        setToolTipText("");
    }

    /** The tooltip shown instead of the full text (a full path, say); null for the full text when it is cut. */
    void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public String getToolTipText() {
        if (detail != null) {
            return detail;
        }
        String text = getText();
        return text == null || text.isBlank() || fits(text) ? null : text;
    }

    private boolean fits(String text) {
        return getFontMetrics(getFont()).stringWidth(text) <= availableWidth();
    }

    private int availableWidth() {
        Insets insets = getInsets();
        Icon icon = getIcon();
        int iconSpace = icon == null ? 0 : icon.getIconWidth() + ICON_GAP;
        return Math.max(0, getWidth() - insets.left - insets.right - iconSpace);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.min(size.width, preferredWidth), size.height);
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        return new Dimension(Math.min(size.width, 40), size.height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (hints instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<RenderingHints.Key, ?> typed = (Map<RenderingHints.Key, ?>) map;
                painter.addRenderingHints(typed);
            } else {
                painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            Insets insets = getInsets();
            int x = insets.left;
            Icon icon = getIcon();
            if (icon != null) {
                icon.paintIcon(this, painter, x, (getHeight() - icon.getIconHeight()) / 2);
                x += icon.getIconWidth() + ICON_GAP;
            }
            String text = getText();
            if (text != null && !text.isEmpty()) {
                painter.setFont(getFont());
                painter.setColor(getForeground());
                FontMetrics metrics = painter.getFontMetrics();
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                painter.drawString(elideMiddle(text, metrics, availableWidth()), x, baseline);
            }
        } finally {
            painter.dispose();
        }
    }

    /** {@code text}, or its start and end around an ellipsis so that it fits {@code width} pixels. */
    static String elideMiddle(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        int ellipsisWidth = metrics.stringWidth(ELLIPSIS);
        int budget = width - ellipsisWidth;
        if (budget <= 0) {
            return ELLIPSIS;
        }
        int head = 0;
        int tail = 0;
        // grow alternately: the end (extension) matters as much as the start
        while (head + tail < text.length()) {
            int nextHead = head + (head <= tail ? 1 : 0);
            int nextTail = tail + (head > tail ? 1 : 0);
            String candidate = text.substring(0, nextHead) + text.substring(text.length() - nextTail);
            if (metrics.stringWidth(candidate) > budget) {
                break;
            }
            head = nextHead;
            tail = nextTail;
        }
        return text.substring(0, head) + ELLIPSIS + text.substring(text.length() - tail);
    }
}
