package cl.almejo.vsim.ui.karnaugh;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * The theme colours the wizard paints with, read from the FlatLaf theme each time (never cached: the theme changes
 * live), with plain fallbacks for other look and feels.
 */
final class WizardColors {

    private WizardColors() {
    }

    static Color foreground() {
        return color("Label.foreground", Color.BLACK);
    }

    static Color background() {
        return color("Panel.background", Color.WHITE);
    }

    static Color cellBackground() {
        return color("Table.background", background());
    }

    static Color muted() {
        return color("Label.disabledForeground", Color.GRAY);
    }

    static Color gridLine() {
        return color("Component.borderColor", Color.GRAY);
    }

    static Color highlight() {
        return color("Component.focusColor", new Color(0x1E88E5));
    }

    static Color error() {
        return color("Component.error.focusedBorderColor", new Color(0xE53935));
    }

    /**
     * The error colour for text: {@link #error()} on a light theme; on a dark theme the theme's error colour is a dim
     * border red, too dark to read as text, so a brighter red is used.
     */
    static Color errorText() {
        return dark() ? new Color(0xFF7A7A) : error();
    }

    /**
     * Amber for a warning that does not block anything (letters written together): a dark amber on a light theme and
     * a bright one on a dark theme, both readable as text (the theme's own warning colour is a border colour, too
     * pale for text on white).
     */
    static Color warning() {
        return dark() ? new Color(0xE8B04A) : new Color(0x8A5A00);
    }

    /**
     * The dotted ring of a Karnaugh cell the practice still has to group: between the text and the cell, soft enough
     * not to compete with the groups.
     */
    static Color uncoveredRing() {
        return blend(foreground(), cellBackground(), dark() ? 0.55 : 0.45);
    }

    /** The ring of the cell a practice hint points at and the outline of the group it shows: the readable amber. */
    static Color hintRing() {
        return warning();
    }

    /**
     * The notation legend's side panel: a soft tint of the dialog's background, a touch darker than it (towards the
     * text on a light theme, towards black on a dark one, where a lighter tint would eat the text's contrast).
     */
    static Color sidePanel() {
        return dark() ? blend(Color.BLACK, background(), 0.12) : blend(foreground(), background(), 0.04);
    }

    /**
     * The faint outline of the legend's operator buttons, so they read as buttons before the mouse is over them:
     * between the panel and the text, nearer the panel.
     */
    static Color legendButtonEdge() {
        return blend(foreground(), sidePanel(), 0.28);
    }

    /** The import preview's input columns: a soft tint of the theme's accent over the table's background. */
    static Color previewInput() {
        return readable(highlight(), dark() ? 0.32 : 0.15);
    }

    /** The import preview's output columns: a soft amber tint, apart from the inputs' one. */
    static Color previewOutput() {
        return readable(warning(), dark() ? 0.26 : 0.16);
    }

    /**
     * The soft background of a suggestion (the import's «Esta tabla cabe en el asistente…»): a tint of the accent over
     * the dialog's background, faded until the text keeps a contrast of at least 4.5:1.
     */
    static Color hint() {
        Color text = foreground();
        Color base = background();
        for (double share = dark() ? 0.22 : 0.12; share > 0; share -= 0.02) {
            Color candidate = blend(highlight(), base, share);
            if (contrast(text, candidate) >= 4.6) {
                return candidate;
            }
        }
        return base;
    }

    /** The thin edge of {@link #hint()}: the accent, halfway to the background. */
    static Color hintEdge() {
        return blend(highlight(), background(), dark() ? 0.45 : 0.35);
    }

    /** A cell, row or name of the import preview with a problem: the theme's error colour as a tint. */
    static Color previewProblem() {
        return readable(error(), dark() ? 0.50 : 0.26);
    }

    /**
     * {@code share} of {@code tint} over the table's background, less when needed for the text to keep a contrast of
     * at least 4.5:1 (WCAG AA). On a dark theme the tint goes over a darker shade of the background: the light text
     * keeps its contrast there, where a tint over the background itself would have to fade to nothing.
     */
    private static Color readable(Color tint, double share) {
        Color text = foreground();
        Color base = dark() ? blend(Color.BLACK, cellBackground(), 0.35) : cellBackground();
        Color candidate = blend(tint, base, share);
        for (double less = share; less > 0 && contrast(text, candidate) < 4.6; less -= 0.02) {
            candidate = blend(tint, base, less);
        }
        return candidate;
    }

    /** The thin line on the side panel's edge. */
    static Color sidePanelEdge() {
        return gridLine();
    }

    /**
     * Secondary text on the side panel (the legend's examples): the text colour faded towards the panel, but only as
     * far as it stays readable (contrast of at least 4.5:1, WCAG AA).
     */
    static Color sidePanelMuted() {
        Color text = foreground();
        Color panel = sidePanel();
        for (double share = 0.6; share < 1; share += 0.05) {
            Color candidate = blend(text, panel, share);
            if (contrast(candidate, panel) >= 4.6) {
                return candidate;
            }
        }
        return text;
    }

    /** The WCAG contrast ratio between two colours (1 to 21). */
    static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** True on a dark theme: picks the bright variant of the group colours. */
    static boolean dark() {
        Color background = background();
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255;
        return luminance < 0.5;
    }

    /** {@code color} with {@code alpha}. */
    static Color translucent(Color color, int alpha) {
        return new Color((color.getRGB() & 0xFFFFFF) | alpha << 24, true);
    }

    /** An opaque mix: {@code share} of {@code top} over {@code bottom} (renderers cannot be translucent). */
    static Color blend(Color top, Color bottom, double share) {
        return new Color(mix(top.getRed(), bottom.getRed(), share), mix(top.getGreen(), bottom.getGreen(), share),
                mix(top.getBlue(), bottom.getBlue(), share));
    }

    private static int mix(int top, int bottom, double share) {
        return (int) Math.round(top * share + bottom * (1 - share));
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
