package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineKind;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.UIManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * The two pills «Moore» and «Mealy» at the top-left of a machine's diagram, in screen space (not zoomed or panned):
 * each is lit when the machine assigns outputs that way, both when it is mixed (then a short line says how many of
 * each). They are not buttons: clicks pass through to the diagram. Colours are read from the scheme at paint time, and
 * the font and sizes follow the interface size (100–200 %) at paint time too.
 */
final class MachineKindChips {

    /** Sizes at 100 %, scaled by the interface size ({@link UIScale}) when used. */
    private static final int MARGIN = 10;
    private static final int GAP = 6;
    private static final int PADDING_X = 12;
    private static final int PADDING_Y = 4;
    /** The font when the look and feel has none (never under FlatLaf). */
    private static final int FALLBACK_FONT_SIZE = 12;
    /** How much of the lit colour tints a lit pill's inside. */
    private static final double LIT_FILL_TINT = 0.18;
    /** How far the unlit pill's outline and text go from the text colour towards the background. */
    private static final double UNLIT_MIX = 0.6;

    private MachineKindChips() {
    }

    /**
     * The text of the pill that is not lit: faded like the outline, but never under
     * {@link ReadableColors#COMFORTABLE_TEXT_CONTRAST}:1.
     */
    static Color unlitText(ColorScheme scheme) {
        return ReadableColors.readableOn(mix(scheme.text(), scheme.background(), UNLIT_MIX), scheme.background(),
                ReadableColors.COMFORTABLE_TEXT_CONTRAST);
    }

    /** The chips' font: the interface's label font, read now, so it follows the interface size and the theme. */
    static Font defaultFont() {
        Font label = UIManager.getFont("Label.font");
        return label != null ? label.deriveFont(Font.PLAIN) : new Font(Font.SANS_SERIF, Font.PLAIN,
                UIScale.scale(FALLBACK_FONT_SIZE));
    }

    /** «2 salidas en estados, 1 en flechas» for a mixed machine; empty otherwise. */
    static String summary(MachineKind kind) {
        if (!kind.mixed()) {
            return "";
        }
        int inStates = kind.outputsInStates().size();
        int onArrows = kind.outputsOnArrows().size();
        return Messages.format("machine.kind.summary",
                Messages.format(inStates == 1 ? "machine.kind.inStates.one" : "machine.kind.inStates.many", inStates),
                Messages.format(onArrows == 1 ? "machine.kind.onArrows.one" : "machine.kind.onArrows.many", onArrows));
    }

    static Rectangle2D mooreBounds(Font font) {
        FontMetrics metrics = metrics(font);
        double width = pillWidth(metrics, Messages.text("machine.kind.moore"));
        return new Rectangle2D.Double(UIScale.scale(MARGIN), UIScale.scale(MARGIN), width, pillHeight(metrics));
    }

    static Rectangle2D mealyBounds(Font font) {
        FontMetrics metrics = metrics(font);
        Rectangle2D moore = mooreBounds(font);
        double width = pillWidth(metrics, Messages.text("machine.kind.mealy"));
        return new Rectangle2D.Double(moore.getMaxX() + UIScale.scale(GAP), UIScale.scale(MARGIN), width,
                pillHeight(metrics));
    }

    /** What the pill under {@code screenPoint} explains; nothing elsewhere. */
    static Optional<String> tooltipAt(Point2D screenPoint, MachineKind kind, Font font) {
        boolean onMoore = mooreBounds(font).contains(screenPoint);
        boolean onMealy = mealyBounds(font).contains(screenPoint);
        if (!onMoore && !onMealy) {
            return Optional.empty();
        }
        if (!kind.hasOutputs()) {
            return Optional.of(Messages.text("machine.kind.none"));
        }
        return Optional.of(Messages.text(onMoore ? "machine.kind.moore.tooltip" : "machine.kind.mealy.tooltip"));
    }

    static void paint(Graphics2D canvas, MachineKind kind, ColorScheme scheme) {
        Graphics2D graphics = (Graphics2D) canvas.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = defaultFont();
            graphics.setFont(font);
            paintPill(graphics, mooreBounds(font), Messages.text("machine.kind.moore"), kind.moore(), scheme);
            paintPill(graphics, mealyBounds(font), Messages.text("machine.kind.mealy"), kind.mealy(), scheme);
            String summary = summary(kind);
            if (!summary.isEmpty()) {
                graphics.setColor(MachineColors.muted(scheme));
                FontMetrics metrics = graphics.getFontMetrics();
                graphics.drawString(summary, UIScale.scale(MARGIN),
                        (float) (mooreBounds(font).getMaxY() + UIScale.scale(GAP) + metrics.getAscent()));
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void paintPill(Graphics2D graphics, Rectangle2D bounds, String text, boolean lit,
                                  ColorScheme scheme) {
        RoundRectangle2D pill = new RoundRectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(),
                bounds.getHeight(), bounds.getHeight(), bounds.getHeight());
        Color unlit = mix(scheme.text(), scheme.background(), UNLIT_MIX);
        if (lit) {
            Color accent = scheme.selection();
            graphics.setColor(mix(scheme.background(), accent, LIT_FILL_TINT));
            graphics.fill(pill);
            graphics.setColor(accent);
            graphics.setStroke(new java.awt.BasicStroke(UIScale.scale(1.5f)));
            graphics.draw(pill);
            graphics.setColor(scheme.text());
        } else {
            graphics.setColor(unlit);
            graphics.setStroke(new java.awt.BasicStroke(UIScale.scale(1f)));
            graphics.draw(pill);
            graphics.setColor(unlitText(scheme));
        }
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, (float) (bounds.getX() + UIScale.scale(PADDING_X)),
                (float) (bounds.getY() + UIScale.scale(PADDING_Y) + metrics.getAscent()));
    }

    private static double pillWidth(FontMetrics metrics, String text) {
        return metrics.stringWidth(text) + 2.0 * UIScale.scale(PADDING_X);
    }

    private static double pillHeight(FontMetrics metrics) {
        return metrics.getHeight() + 2.0 * UIScale.scale(PADDING_Y);
    }

    private static FontMetrics metrics(Font font) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scratch.createGraphics();
        try {
            return graphics.getFontMetrics(font);
        } finally {
            graphics.dispose();
        }
    }

    // CPD-OFF state-machine code on its own branch: MachineColors has the same mix (ThemeColors.mix after the merge)
    private static Color mix(Color base, Color towards, double amount) {
        return new Color(channel(base.getRed(), towards.getRed(), amount),
                channel(base.getGreen(), towards.getGreen(), amount),
                channel(base.getBlue(), towards.getBlue(), amount));
    }

    private static int channel(int base, int towards, double amount) {
        return (int) Math.round(base + (towards - base) * amount);
    }
    // CPD-ON
}
