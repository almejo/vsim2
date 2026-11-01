package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.terminal.TerminalFrame;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * One view's picture of one Terminal's screen (spec §8.2): the cells, glyphs and screen effect (glow, scanlines,
 * vignette) of a frame, rendered once at the device's resolution, so a paint that only moves the cursor, the last
 * character's glow or the fading old picture costs a blit instead of every glyph again. The picture is drawn again
 * when anything it shows changes: the frame, the look, the colours resolved for this paint (a new theme), the character
 * ROM's glyphs, the size, or the device's scale, rotation or sub-pixel position. The old picture after a scroll or a
 * clear is a picture of its own, drawn once and faded over the cells that changed.
 *
 * <p>Only the canvas uses it: an export, a printed page and black and white keep drawing the screen as vectors. Above
 * {@link #MAXIMUM_PIXELS} (a huge zoom) it keeps nothing and the screen is drawn directly, as before. Used on the EDT.
 */
public final class TerminalScreenRaster {

    /** The largest picture kept, in device pixels: 16 MB. */
    static final long MAXIMUM_PIXELS = 4_000_000;
    /** Sub-pixel positions closer than this share a picture: a pan by whole pixels keeps it. */
    private static final double SUBPIXEL_STEPS = 256;

    private Picture screen;
    private Picture ghost;
    /** The cells the old picture fades over, for {@link #changedFrame}. */
    private List<Rectangle2D> changedRuns = List.of();
    private TerminalFrame changedFrame;
    private int renders;
    private int ghostRenders;

    /**
     * Where the device puts a picture: the linear part of the paint's transform (zoom, screen scale, rotation) and
     * the sub-pixel position of the part's origin, in {@link #SUBPIXEL_STEPS}.
     */
    private record Placement(double scaleX, double shearY, double shearX, double scaleY, long subpixelX,
                             long subpixelY) {
    }

    /** A picture rendered once and what it shows. */
    private record Picture(BufferedImage image, TerminalScreenPainter.Setting setting, Optional<TerminalFrame> frame,
                           Placement placement) {

        boolean shows(TerminalScreenPainter.Setting otherSetting, Optional<TerminalFrame> otherFrame,
                      Placement otherPlacement) {
            return placement.equals(otherPlacement) && setting.equals(otherSetting) && sameFrame(frame, otherFrame);
        }
    }

    /** The device pixels a paint covers with the screen: the corner and the size of the picture. */
    private record Footprint(int left, int top, int width, int height, Placement placement) {
    }

    /** How many times the screen's picture was rendered: tests check a repaint does not render it again. */
    int renders() {
        return renders;
    }

    /** How many times the old picture's layer was rendered. */
    int ghostRenders() {
        return ghostRenders;
    }

    /**
     * Paints the screen of {@code setting} with {@code frame} at {@code moment} from the kept pictures, rendering them
     * first when what they show changed; false (nothing painted) when the picture would be too large, so the caller
     * draws the screen directly.
     */
    boolean paint(Graphics2D graphics, TerminalScreenPainter.Setting setting, Optional<TerminalFrame> frame,
                  TerminalMoment moment) {
        AffineTransform transform = graphics.getTransform();
        Footprint footprint = footprint(transform, setting.screen());
        if ((long) footprint.width() * footprint.height() > MAXIMUM_PIXELS || footprint.width() <= 0
                || footprint.height() <= 0) {
            screen = null;
            ghost = null;
            return false;
        }
        if (screen == null || !screen.shows(setting, frame, footprint.placement())) {
            screen = new Picture(render(graphics, transform, footprint, layer -> TerminalScreenPainter.paint(layer,
                    setting, frame, stillScreen(moment))), setting, frame, footprint.placement());
            renders++;
        }
        Graphics2D device = (Graphics2D) graphics.create();
        try {
            device.setTransform(new AffineTransform());
            device.drawImage(screen.image(), footprint.left(), footprint.top(), null);
            if (moment.ghost().isPresent() && frame.isPresent()) {
                paintGhost(device, transform, footprint, setting, frame.get(), moment.ghost().get(),
                        moment.ghostOpacity());
            } else {
                ghost = null;
                changedFrame = null;
            }
        } finally {
            device.dispose();
        }
        for (Rectangle2D area : TerminalScreenPainter.overlayAreas(setting, frame, moment)) {
            paintOverlay(graphics, transform, setting, frame, moment, area);
        }
        return true;
    }

    /**
     * The old picture's characters faded to {@code opacity} over the cells where the screen changed, from a layer
     * rendered once for the whole fading; the cells that did not change stay as they are.
     */
    private void paintGhost(Graphics2D device, AffineTransform transform, Footprint footprint,
                            TerminalScreenPainter.Setting setting, TerminalFrame frame, TerminalFrame old,
                            double opacity) {
        Optional<TerminalFrame> oldFrame = Optional.of(old);
        if (ghost == null || !ghost.shows(setting, oldFrame, footprint.placement())) {
            ghost = new Picture(render(device, transform, footprint, layer -> TerminalScreenPainter.paintGhostLayer(
                    layer, setting, frame, old)), setting, oldFrame, footprint.placement());
            ghostRenders++;
            changedFrame = null;
        }
        if (changedFrame != frame) {
            changedRuns = TerminalScreenPainter.changedRuns(setting, frame, old);
            changedFrame = frame;
        }
        device.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
        for (Rectangle2D run : changedRuns) {
            Rectangle2D onDevice = transform.createTransformedShape(run).getBounds2D();
            int left = (int) Math.round(onDevice.getMinX());
            int top = (int) Math.round(onDevice.getMinY());
            int right = (int) Math.round(onDevice.getMaxX());
            int bottom = (int) Math.round(onDevice.getMaxY());
            device.drawImage(ghost.image(), left, top, right, bottom, left - footprint.left(),
                    top - footprint.top(), right - footprint.left(), bottom - footprint.top(), null);
        }
    }

    /**
     * The cursor or the last character's glow drawn directly over the picture: the cells of {@code area} painted
     * again at {@code moment}, inside the device pixels the screen covers whole (its antialiased edge is the
     * picture's already).
     */
    private static void paintOverlay(Graphics2D graphics, AffineTransform transform,
                                     TerminalScreenPainter.Setting setting, Optional<TerminalFrame> frame,
                                     TerminalMoment moment, Rectangle2D area) {
        Rectangle2D onDevice = transform.createTransformedShape(setting.screen()).getBounds2D();
        int left = (int) Math.ceil(onDevice.getMinX());
        int top = (int) Math.ceil(onDevice.getMinY());
        int right = (int) Math.floor(onDevice.getMaxX());
        int bottom = (int) Math.floor(onDevice.getMaxY());
        Graphics2D overlay = (Graphics2D) graphics.create();
        try {
            overlay.setTransform(new AffineTransform());
            overlay.clipRect(left, top, right - left, bottom - top);
            overlay.setTransform(transform);
            overlay.clip(area);
            TerminalScreenPainter.paint(overlay, setting, frame, moment);
        } finally {
            overlay.dispose();
        }
    }

    /** The screen without what moves: no cursor, no glow on the last character, no old picture. */
    private static TerminalMoment stillScreen(TerminalMoment moment) {
        return new TerminalMoment(moment.animation(), Double.NaN, false, 0, Optional.empty(), 0,
                TerminalMoment.NEVER);
    }

    /**
     * The device pixels of {@code screen}'s picture. Its corner is a whole number of pixels before the part's origin,
     * so the picture is drawn with the same sub-pixel translation as the part, never a negative one: Java 2D places
     * the glyphs a pixel apart under a negative translation, and the picture would not match the direct drawing.
     */
    private static Footprint footprint(AffineTransform transform, Rectangle2D screen) {
        Rectangle2D onDevice = transform.createTransformedShape(screen).getBounds2D();
        int originX = (int) Math.floor(transform.getTranslateX());
        int originY = (int) Math.floor(transform.getTranslateY());
        int left = originX - Math.max(0, (int) Math.ceil(originX - onDevice.getMinX()));
        int top = originY - Math.max(0, (int) Math.ceil(originY - onDevice.getMinY()));
        int width = (int) Math.ceil(onDevice.getMaxX()) - left;
        int height = (int) Math.ceil(onDevice.getMaxY()) - top;
        Placement placement = new Placement(transform.getScaleX(), transform.getShearY(), transform.getShearX(),
                transform.getScaleY(), Math.round((transform.getTranslateX() - originX) * SUBPIXEL_STEPS),
                Math.round((transform.getTranslateY() - originY) * SUBPIXEL_STEPS));
        return new Footprint(left, top, width, height, placement);
    }

    /** A drawing on a layer of the device's pixels. */
    private interface LayerDrawing {
        void draw(Graphics2D layer);
    }

    /**
     * A transparent picture of {@code footprint} drawn by {@code drawing} with the paint's {@code transform} and the
     * rendering hints of {@code graphics}, so its pixels are the ones a direct drawing would make.
     */
    private static BufferedImage render(Graphics2D graphics, AffineTransform transform, Footprint footprint,
                                        LayerDrawing drawing) {
        GraphicsConfiguration configuration = graphics.getDeviceConfiguration();
        BufferedImage image = configuration == null
                ? new BufferedImage(footprint.width(), footprint.height(), BufferedImage.TYPE_INT_ARGB_PRE)
                : configuration.createCompatibleImage(footprint.width(), footprint.height(),
                Transparency.TRANSLUCENT);
        Graphics2D layer = image.createGraphics();
        try {
            layer.setRenderingHints(graphics.getRenderingHints());
            layer.translate(-footprint.left(), -footprint.top());
            layer.transform(transform);
            drawing.draw(layer);
        } finally {
            layer.dispose();
        }
        return image;
    }

    private static boolean sameFrame(Optional<TerminalFrame> first, Optional<TerminalFrame> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.get() == second.get() || first.get().equals(second.get());
    }
}
