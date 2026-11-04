package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.ai.AiColors;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The line under the preview that says something about the sheets or the printer: a plain dimmed hint, an info
 * (soft green, info icon) or a warning (amber, warning icon). Every colour is asked each time it paints, so a theme
 * change recolours it.
 */
final class NoticeLabel extends JLabel {

    /** How a notice looks: what kind of thing it says. */
    enum Kind {
        /** A dimmed remark; no icon. */
        HINT,
        /** Something the program did on the user's behalf, or good news; soft green. */
        INFO,
        /** Something that may go wrong on the printer; amber. */
        WARNING
    }

    private static final int WRAP_PIXELS = 320;

    private Kind kind = Kind.HINT;
    private String plainText = "";

    NoticeLabel() {
        setVerticalTextPosition(TOP);
        setIconTextGap(6);
    }

    /** Shows {@code text} in the look of {@code noticeKind}. */
    void notice(Kind noticeKind, String text) {
        kind = noticeKind;
        plainText = text;
        setIcon(switch (noticeKind) {
            case HINT -> null;
            case INFO -> Icons.get(PrintIcons.NOTICE_INFO, 16);
            case WARNING -> Icons.get(PrintIcons.NOTICE_WARNING, 16);
        });
        setBorder(noticeKind == Kind.HINT ? BorderFactory.createEmptyBorder()
                : BorderFactory.createEmptyBorder(4, 6, 4, 6));
        setOpaque(false);
        setText(text.isEmpty() ? "" : "<html><body style='width: " + WRAP_PIXELS + "px'>"
                + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</body></html>");
    }

    Kind kind() {
        return kind;
    }

    /** The text as given, without the markup that wraps it. */
    String plainText() {
        return plainText;
    }

    @Override
    public Color getForeground() {
        // the superclass constructor asks before the field is set
        return switch (kind == null ? Kind.HINT : kind) {
            case HINT -> UIManager.getColor("Label.disabledForeground");
            case INFO -> AiColors.success();
            case WARNING -> ReadableColors.warningText();
        };
    }

    /** The soft tint behind an info or a warning: its colour over the panel, light enough to keep the text readable. */
    Color tint() {
        Color base = switch (kind) {
            case HINT -> null;
            case INFO -> AiColors.success();
            case WARNING -> ReadableColors.warningText();
        };
        if (base == null) {
            return null;
        }
        Color panel = ReadableColors.panelBackground();
        double share = ReadableColors.dark() ? 0.16 : 0.10;
        return new Color(
                (int) Math.round(base.getRed() * share + panel.getRed() * (1 - share)),
                (int) Math.round(base.getGreen() * share + panel.getGreen() * (1 - share)),
                (int) Math.round(base.getBlue() * share + panel.getBlue() * (1 - share)));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Color tint = tint();
        if (tint != null) {
            Graphics2D painter = (Graphics2D) graphics.create();
            try {
                painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.setColor(tint);
                painter.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            } finally {
                painter.dispose();
            }
        }
        super.paintComponent(graphics);
    }
}
