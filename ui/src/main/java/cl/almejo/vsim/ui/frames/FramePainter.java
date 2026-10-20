package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFonts;

import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.MonochromeScheme;

import javax.swing.Icon;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * Paints a frame: the tint over its rectangle, a 1 px border and the title in a small band on the top edge. Only the
 * frame itself; selection, handles and controls belong to other layers. Works in document coordinates (8 px per grid
 * unit) and reads every color from the scheme it is given.
 */
public final class FramePainter {

    /** Height of the title band, in document pixels. */
    public static final double TITLE_BAR_HEIGHT = 2 * GridPoint.PIXELS_PER_UNIT;

    private static final BasicStroke BORDER = new BasicStroke(1f);
    private static final double TITLE_PADDING = 5;
    private static final int BAND_ALPHA = 36;
    /** Below this many screen pixels per grid unit the title would be an unreadable smear and is left out. */
    private static final double MINIMUM_PIXELS_PER_GRID_FOR_TITLE = 3;
    private static final String ELLIPSIS = "…";
    /** Side of a control icon, in document pixels. */
    static final double CONTROL_SIZE = 12;
    private static final double CONTROL_GAP = 2;
    private static final double CONTROL_MARGIN = 2;
    /** Room kept at the right end of the title bar for the controls, so the title never runs under them. */
    private static final double CONTROLS_WIDTH = 2 * CONTROL_SIZE + 2 * CONTROL_GAP + CONTROL_MARGIN;
    private static final float FAINT_ALPHA = 0.35f;

    private FramePainter() {
    }

    /** The rectangle of the frame in document pixels. */
    public static Rectangle2D bounds(Frame frame) {
        return new Rectangle2D.Double(frame.topLeft().x() * GridPoint.PIXELS_PER_UNIT,
                frame.topLeft().y() * GridPoint.PIXELS_PER_UNIT, frame.width() * GridPoint.PIXELS_PER_UNIT,
                frame.height() * GridPoint.PIXELS_PER_UNIT);
    }

    /** The title band along the top edge, in document pixels; what the user grabs to move the frame. */
    public static Rectangle2D titleBarBounds(Frame frame) {
        Rectangle2D bounds = bounds(frame);
        return new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), TITLE_BAR_HEIGHT);
    }

    /** The font the title is painted with, at 100 %: the current text font of Preferencias (M6.8), bold. */
    public static Font titleFont() {
        return TextFonts.titleFont();
    }

    /**
     * Where the title text is written: from the left padding of the title band to the controls, as high as the title
     * font, centred in the band. In document pixels.
     */
    public static Rectangle2D titleTextBounds(Frame frame) {
        Rectangle2D bar = titleBarBounds(frame);
        LineMetrics metrics = titleFont().getLineMetrics("Ag", new FontRenderContext(null, true, true));
        double height = metrics.getAscent() + metrics.getDescent();
        return new Rectangle2D.Double(bar.getX() + TITLE_PADDING, bar.getY() + (bar.getHeight() - height) / 2,
                Math.max(0, bar.getWidth() - 2 * TITLE_PADDING - CONTROLS_WIDTH), height);
    }

    /** The ⋯ button, at the right end of the title bar, in document pixels. */
    public static Rectangle2D menuBounds(Frame frame) {
        Rectangle2D bar = titleBarBounds(frame);
        return new Rectangle2D.Double(bar.getMaxX() - CONTROL_MARGIN - CONTROL_SIZE,
                bar.getY() + (bar.getHeight() - CONTROL_SIZE) / 2, CONTROL_SIZE, CONTROL_SIZE);
    }

    /** The padlock, left of the ⋯ button, in document pixels. */
    public static Rectangle2D lockBounds(Frame frame) {
        Rectangle2D menu = menuBounds(frame);
        return new Rectangle2D.Double(menu.getX() - CONTROL_GAP - CONTROL_SIZE, menu.getY(), CONTROL_SIZE,
                CONTROL_SIZE);
    }

    /** Below this many screen pixels per grid unit the title and the controls are left out. */
    public static boolean showsDetails(double pixelsPerGrid) {
        return pixelsPerGrid >= MINIMUM_PIXELS_PER_GRID_FOR_TITLE;
    }

    /**
     * The controls: padlock and ⋯ button when {@code active} (the frame is hovered or selected); otherwise only a
     * faint closed padlock, and only when the frame is locked. Never part of an image export.
     */
    public static void paintControls(Graphics2D graphics, Frame frame, boolean active, double pixelsPerGrid) {
        if (!showsDetails(pixelsPerGrid) || !(active || frame.locked())) {
            return;
        }
        Rectangle2D lock = lockBounds(frame);
        Icon lockIcon = Icons.get(frame.locked() ? FrameIcons.LOCK_CLOSED : FrameIcons.LOCK_OPEN,
                (int) CONTROL_SIZE);
        Composite previous = graphics.getComposite();
        if (!active) {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FAINT_ALPHA));
        }
        lockIcon.paintIcon(null, graphics, (int) Math.round(lock.getX()), (int) Math.round(lock.getY()));
        if (active) {
            Rectangle2D menu = menuBounds(frame);
            Icons.get(FrameIcons.MENU, (int) CONTROL_SIZE)
                    .paintIcon(null, graphics, (int) Math.round(menu.getX()), (int) Math.round(menu.getY()));
        }
        graphics.setComposite(previous);
    }

    /** {@code pixelsPerGrid} is the screen size of one grid unit (8 at 100 %). As the canvas paints it. */
    public static void paint(Graphics2D graphics, Frame frame, ColorScheme scheme, double pixelsPerGrid) {
        paint(graphics, frame, scheme, pixelsPerGrid, PaintPolicy.CANVAS);
    }

    /**
     * The frame under {@code policy}: on paper the title is painted however small a grid unit is and may use the room
     * of the buttons; in black and white the frame has no fill at all (neither tint nor title band), only its border
     * in dark grey and its title.
     */
    public static void paint(Graphics2D graphics, Frame frame, ColorScheme scheme, double pixelsPerGrid,
                             PaintPolicy policy) {
        Rectangle2D bounds = bounds(frame);
        Rectangle2D bar = titleBarBounds(frame);
        Color border = policy.monochrome() ? MonochromeScheme.FRAME_BORDER : scheme.frameBorder();
        if (!policy.monochrome()) {
            Color tint = scheme.frameTint(frame.tint());
            if (tint.getAlpha() > 0) {
                graphics.setColor(tint);
                graphics.fill(bounds);
            }
            graphics.setColor(new Color((border.getRGB() & 0xFFFFFF) | (BAND_ALPHA << 24), true));
            graphics.fill(bar);
        }
        graphics.setStroke(policy.widened(BORDER));
        graphics.setColor(border);
        graphics.draw(bounds);
        graphics.draw(new Line2D.Double(bar.getMinX(), bar.getMaxY(), bar.getMaxX(), bar.getMaxY()));
        if (!frame.title().isEmpty() && (policy.alwaysShowFrameTitles() || showsDetails(pixelsPerGrid))) {
            paintTitle(graphics, frame.title(), bar, scheme, policy.paintFrameButtons() ? CONTROLS_WIDTH : 0);
        }
    }

    private static void paintTitle(Graphics2D graphics, String title, Rectangle2D bar, ColorScheme scheme,
                                   double controlsWidth) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(titleFont());
        FontMetrics metrics = graphics.getFontMetrics();
        String shown = fit(title, metrics, bar.getWidth() - 2 * TITLE_PADDING - controlsWidth);
        if (shown.isEmpty()) {
            return;
        }
        double baseline = bar.getY() + (bar.getHeight() + metrics.getAscent() - metrics.getDescent()) / 2.0;
        Shape previousClip = graphics.getClip();
        graphics.clipRect((int) Math.floor(bar.getX()), (int) Math.floor(bar.getY()),
                (int) Math.ceil(bar.getWidth()), (int) Math.ceil(bar.getHeight()));
        graphics.setColor(scheme.frameTitle());
        graphics.drawString(shown, (float) (bar.getX() + TITLE_PADDING), (float) baseline);
        graphics.setClip(previousClip);
    }

    private static String fit(String title, FontMetrics metrics, double available) {
        if (metrics.stringWidth(title) <= available) {
            return title;
        }
        for (int length = title.length() - 1; length > 0; length--) {
            String candidate = title.substring(0, length) + ELLIPSIS;
            if (metrics.stringWidth(candidate) <= available) {
                return candidate;
            }
        }
        return "";
    }
}
