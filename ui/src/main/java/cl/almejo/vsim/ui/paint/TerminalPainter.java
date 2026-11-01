package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.core.terminal.TerminalGeometry;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * «Terminal» (spec §4, §8.1–§8.5): a title bar with the part's label (its name without one) and a chip with its mode
 * and size («VT100 · 24×80»), the screen below, and the pin stubs with their names. The screen keeps the part's own
 * colours («Colores de pantalla», «Paleta ANSI») in every theme but «Del tema de VSim», read from the scheme at paint
 * time like the border and the title bar. Without a simulation (palette, ghost) the screen is empty with the cursor at
 * home.
 *
 * <p>The canvas paints through {@link #paintLive} with the {@link TerminalMoment} of its animation (blink, Automan's
 * star, last character's glow, persistence, screen effect); the plain {@link #paint} of the painter interface, used by
 * the palette and the exports, shows a still screen without effects.
 */
public final class TerminalPainter implements ComponentPainter {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double CORNER = 4;
    private static final double TITLE_HEIGHT = TerminalGeometry.TITLE_BAR_UNITS * UNIT;
    private static final double MARGIN = TerminalGeometry.MARGIN_UNITS * UNIT;
    private static final double TITLE_PADDING = 6;
    /** Room kept between the label and the chip. */
    private static final double TITLE_GAP = 8;
    private static final float TITLE_TEXT_SIZE = 8f;
    /** The chip is quieter than the label: the mockup's muted colour. */
    private static final int CHIP_ALPHA = 170;
    private static final float PIN_NAME_SIZE = 5f;
    private static final double PIN_NAME_INSET = 4.5;
    /** A right-edge name ends this far inside the edge, where a centred D name starts on the left. */
    private static final double RIGHT_PIN_NAME_INSET = 2.5;
    private static final BasicStroke DIVIDER = new BasicStroke(1f);
    private static final String CHIP_KEY_PREFIX = "terminal.chip.";
    private static final int OPAQUE = 255;
    /** How far the body's shadow and outline reach out of its shape (and its rounded corners in), rounded up. */
    private static final int BODY_REACH = 4;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        paintLive(graphics, type, parameters, state, scheme, Optional.empty(), TerminalMoment.EXPORTED);
    }

    /**
     * Draws the Terminal with {@code label} in its title bar (empty: the part's name) at {@code moment} of the view's
     * animation, its screen drawn directly.
     */
    public void paintLive(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                          ColorScheme scheme, Optional<String> label, TerminalMoment moment) {
        paintLive(graphics, type, parameters, state, scheme, label, moment, Optional.empty());
    }

    /**
     * {@link #paintLive(Graphics2D, ComponentType, Map, VisibleState, ColorScheme, Optional, TerminalMoment)} with the
     * screen kept as a picture in {@code raster}, the view's for this part: a repaint that only moves the cursor, the
     * glow or the fading old picture blits it. An export and black and white still draw the screen directly.
     */
    public void paintLive(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                          ColorScheme scheme, Optional<String> label, TerminalMoment moment,
                          TerminalScreenRaster raster) {
        paintLive(graphics, type, parameters, state, scheme, label, moment, Optional.of(raster));
    }

    private void paintLive(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                           VisibleState state, ColorScheme scheme, Optional<String> label, TerminalMoment moment,
                           Optional<TerminalScreenRaster> raster) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        Shape outline = new RoundRectangle2D.Double(0, 0, width, height, CORNER, CORNER);
        TerminalLook look = TerminalLook.of(resolved);
        TerminalScreenPainter.Setting setting = new TerminalScreenPainter.Setting(screen(width, height),
                romGlyphsOf(state, resolved), rows(resolved), columns(resolved), TerminalTypes.fontSize(resolved), look,
                colorsOf(look, scheme));
        boolean cached = raster.isPresent() && moment.effects() && !setting.colors().monochrome();
        if (cached && setting.colors().background().getAlpha() == OPAQUE) {
            paintBodyAround(graphics, outline, scheme, inside(setting.screen()));
        } else {
            PainterSupport.body(graphics, outline, scheme);
        }
        if (shows(graphics, new Rectangle2D.Double(0, 0, width, TITLE_HEIGHT))) {
            paintTitleBar(graphics, outline, width, resolved, label.orElseGet(() -> Messages.componentName(type.id())),
                    scheme);
        }
        if (!cached || !raster.get().paint(graphics, setting, frameOf(state), moment)) {
            TerminalScreenPainter.paint(graphics, setting, frameOf(state), moment);
        }
        PainterSupport.pinDots(graphics, type, scheme);
        paintPinNames(graphics, type, scheme);
    }

    /**
     * The screen alone, as the Terminal window shows it (spec §9): {@code frame}'s cells in the part's look (screen
     * colours, palette, font, cursor shape, screen effect) filling {@code screen}, whose size may differ from the
     * canvas's (the cells grow with the window). Still: no blink, no Automan spin, no glow, no persistence. The theme's
     * colours come from {@code scheme}, read at paint time.
     */
    public static void paintScreen(Graphics2D graphics, Rectangle2D screen, TerminalFrame frame,
                                   Map<String, Object> resolved, ColorScheme scheme) {
        paintScreen(graphics, screen, frame, GlyphCache.EMPTY, resolved, scheme);
    }

    /**
     * {@link #paintScreen(Graphics2D, Rectangle2D, TerminalFrame, Map, ColorScheme)} with the glyphs its character ROM
     * gave ({@code glyphs}), drawn as the canvas draws them when the part takes its glyphs from a ROM.
     */
    public static void paintScreen(Graphics2D graphics, Rectangle2D screen, TerminalFrame frame, GlyphCache glyphs,
                                   Map<String, Object> resolved, ColorScheme scheme) {
        TerminalFontSize size = TerminalTypes.fontSize(resolved);
        Rectangle2D natural = new Rectangle2D.Double(0, 0, frame.columns() * size.cellWidth(),
                frame.rows() * size.cellHeight());
        Graphics2D scaled = (Graphics2D) graphics.create();
        try {
            scaled.translate(screen.getX(), screen.getY());
            scaled.scale(screen.getWidth() / natural.getWidth(), screen.getHeight() / natural.getHeight());
            TerminalLook look = TerminalLook.of(resolved);
            GlyphCache romGlyphs = TerminalTypes.externalGlyphs(resolved) ? glyphs : GlyphCache.EMPTY;
            TerminalScreenPainter.paint(scaled, natural, Optional.of(frame), romGlyphs, frame.rows(), frame.columns(),
                    size, look, colorsOf(look, scheme), TerminalMoment.still(TerminalAnimation.STILL));
        } finally {
            scaled.dispose();
        }
    }

    /**
     * The part of a Terminal painted at {@code moment} that will look different later without a new frame, in the
     * part's coordinates: the cursor's reach and the last character's cell, or the whole screen while the old picture
     * fades; empty when nothing moves.
     */
    public Optional<Rectangle2D> movingArea(ComponentType type, Map<String, Object> parameters, VisibleState state,
                                            TerminalMoment moment) {
        if (!moment.moving()) {
            return Optional.empty();
        }
        Rectangle2D screen = screen(type.size().width() * UNIT, type.size().height() * UNIT);
        Optional<TerminalFrame> frame = frameOf(state);
        if (moment.ghost().isPresent() || frame.isEmpty()) {
            return Optional.of(screen);
        }
        TerminalFontSize size = TerminalTypes.fontSize(type.resolveParameters(parameters));
        Rectangle2D area = TerminalCursorPainter.reach(TerminalScreenPainter.cellBounds(screen, size,
                frame.get().cursorRow(), frame.get().cursorColumn()));
        if (moment.lastGlowAlpha() > 0) {
            frame.get().lastWritten().ifPresent(last -> area.add(TerminalScreenPainter.cellBounds(screen, size,
                    last.row(), last.column())));
        }
        return Optional.of(area);
    }

    /**
     * The part's body only where the clip reaches outside {@code hidden}, the inside of an opaque screen painted over
     * it next: its gradient is the costliest thing a canvas repaint of a moving screen would draw, all of it hidden.
     * A band of the clip well inside the body (away from its rounded edge, outline and shadow) is just the gradient.
     */
    private static void paintBodyAround(Graphics2D graphics, Shape outline, ColorScheme scheme, Rectangle2D hidden) {
        Rectangle clip = Optional.ofNullable(graphics.getClipBounds()).orElseGet(() -> {
            Rectangle whole = outline.getBounds();
            whole.grow(BODY_REACH, BODY_REACH);
            return whole;
        });
        if (!clip.intersects(hidden)) {
            PainterSupport.body(graphics, outline, scheme);
            return;
        }
        Rectangle2D inside = clip.createIntersection(hidden);
        Rectangle2D outlineBox = outline.getBounds2D();
        Rectangle2D flat = new Rectangle2D.Double(outlineBox.getX() + BODY_REACH, outlineBox.getY() + BODY_REACH,
                outlineBox.getWidth() - 2 * BODY_REACH, outlineBox.getHeight() - 2 * BODY_REACH);
        List<Rectangle2D> bands = List.of(
                new Rectangle2D.Double(clip.getMinX(), clip.getMinY(), clip.getWidth(),
                        inside.getMinY() - clip.getMinY()),
                new Rectangle2D.Double(clip.getMinX(), inside.getMaxY(), clip.getWidth(),
                        clip.getMaxY() - inside.getMaxY()),
                new Rectangle2D.Double(clip.getMinX(), inside.getMinY(), inside.getMinX() - clip.getMinX(),
                        inside.getHeight()),
                new Rectangle2D.Double(inside.getMaxX(), inside.getMinY(), clip.getMaxX() - inside.getMaxX(),
                        inside.getHeight()));
        for (Rectangle2D band : bands) {
            if (band.getWidth() <= 0 || band.getHeight() <= 0) {
                continue;
            }
            Graphics2D around = (Graphics2D) graphics.create();
            try {
                around.clip(band);
                if (flat.contains(band)) {
                    // a plain rectangle of the gradient: its edges are the clip's or under the screen
                    around.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    around.setPaint(PainterSupport.bodyGradient(around, outline, scheme));
                    around.fill(band);
                } else {
                    PainterSupport.body(around, outline, scheme);
                }
            } finally {
                around.dispose();
            }
        }
    }

    /**
     * Whether the clip reaches {@code area} or near it (its outline and antialiased edges): a repaint of the screen
     * alone skips the title bar, whose clip to the rounded outline is costly.
     */
    private static boolean shows(Graphics2D graphics, Rectangle2D area) {
        Rectangle clip = graphics.getClipBounds();
        return clip == null || clip.intersects(area.getX() - BODY_REACH, area.getY() - BODY_REACH,
                area.getWidth() + 2 * BODY_REACH, area.getHeight() + 2 * BODY_REACH);
    }

    /** {@code screen} less the pixel its antialiased edge blends with the body. */
    private static Rectangle2D inside(Rectangle2D screen) {
        return new Rectangle2D.Double(screen.getX() + 1, screen.getY() + 1, screen.getWidth() - 2,
                screen.getHeight() - 2);
    }

    /** The screen's colours in {@code scheme}: the part's own, or the scheme's text on its background in monochrome. */
    static TerminalScreenPainter.Colors colorsOf(TerminalLook look, ColorScheme scheme) {
        return MonochromeScheme.isMonochrome(scheme)
                ? new TerminalScreenPainter.Colors(scheme.text(), scheme.background(), look.palette(), true)
                : new TerminalScreenPainter.Colors(look.screenColors().text(scheme),
                look.screenColors().background(scheme), look.palette(), false);
    }

    /** The chip's text, «VT100 · 24×80»: the mode and the rows × columns. */
    static String chip(Map<String, Object> resolved) {
        return Messages.format(CHIP_KEY_PREFIX + resolved.get(TerminalTypes.MODE), rows(resolved), columns(resolved));
    }

    private static Optional<TerminalFrame> frameOf(VisibleState state) {
        return state instanceof VisibleState.TerminalState terminal ? Optional.of(terminal.frame()) : Optional.empty();
    }

    /** The glyphs of the character ROM to draw: none unless the part takes its glyphs from one. */
    private static GlyphCache romGlyphsOf(VisibleState state, Map<String, Object> resolved) {
        return state instanceof VisibleState.TerminalState terminal && TerminalTypes.externalGlyphs(resolved)
                ? terminal.glyphs() : GlyphCache.EMPTY;
    }

    private static Rectangle2D screen(double width, double height) {
        return new Rectangle2D.Double(MARGIN, TITLE_HEIGHT + MARGIN, width - 2 * MARGIN,
                height - TITLE_HEIGHT - 2 * MARGIN);
    }

    private static int rows(Map<String, Object> resolved) {
        return (Integer) resolved.get(TerminalTypes.ROWS);
    }

    private static int columns(Map<String, Object> resolved) {
        return (Integer) resolved.get(TerminalTypes.COLUMNS);
    }

    private static void paintTitleBar(Graphics2D graphics, Shape outline, double width, Map<String, Object> resolved,
                                      String name, ColorScheme scheme) {
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            clipped.setColor(scheme.componentShade());
            clipped.fill(new Rectangle2D.Double(0, 0, width, TITLE_HEIGHT));
            clipped.setColor(scheme.componentStroke());
            clipped.setStroke(DIVIDER);
            clipped.draw(new Line2D.Double(0, TITLE_HEIGHT, width, TITLE_HEIGHT));
        } finally {
            clipped.dispose();
        }
        if (PainterSupport.turnedSideways(graphics)) {
            // upright text cannot fit the title bar turned into a narrow vertical strip
            return;
        }
        double middle = TITLE_HEIGHT / 2;
        // turned upside down the part's left is the screen's right: the texts keep their side of the bar
        boolean upsideDown = Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
        String chip = chip(resolved);
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(TITLE_TEXT_SIZE));
        double nameRoom = width - 2 * TITLE_PADDING - metrics.stringWidth(chip) - TITLE_GAP;
        PainterSupport.uprightText(graphics, PainterSupport.ellipsized(name, metrics, nameRoom), TITLE_PADDING, middle,
                TITLE_TEXT_SIZE, scheme.text(), upsideDown ? PainterSupport.ALIGN_RIGHT : PainterSupport.ALIGN_LEFT);
        PainterSupport.uprightText(graphics, chip, width - TITLE_PADDING, middle, TITLE_TEXT_SIZE,
                PainterSupport.withAlpha(scheme.text(), CHIP_ALPHA),
                upsideDown ? PainterSupport.ALIGN_LEFT : PainterSupport.ALIGN_RIGHT);
    }

    /**
     * Each pin's name inside the part, next to its stub: D beside the left edge, CODE, ROW and PIX (an external
     * character ROM) beside the right edge, the others above the bottom edge.
     */
    private static void paintPinNames(Graphics2D graphics, ComponentType type, ColorScheme scheme) {
        int rightEdge = type.size().width();
        for (var pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            boolean onLeftEdge = pin.position().x() == 0;
            boolean onRightEdge = pin.position().x() == rightEdge && pin.position().y() < type.size().height();
            if (onLeftEdge) {
                PainterSupport.uprightText(graphics, pin.name(), pinX + PIN_NAME_INSET, pinY, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_CENTER);
            } else if (onRightEdge) {
                paintRightPinName(graphics, pin.name(), pinX, pinY, scheme);
            } else {
                PainterSupport.uprightText(graphics, pin.name(), pinX, pinY - PIN_NAME_INSET, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_CENTER);
            }
        }
    }

    /**
     * A right-edge pin's name, ending just inside the edge; turned upside down it starts there instead (the text stays
     * upright, so its end would leave the part), and turned sideways it is centred a little inside the edge.
     */
    private static void paintRightPinName(Graphics2D graphics, String name, double pinX, double pinY,
                                          ColorScheme scheme) {
        if (PainterSupport.turnedSideways(graphics)) {
            PainterSupport.uprightText(graphics, name, pinX - PIN_NAME_INSET, pinY, PIN_NAME_SIZE, scheme.text(),
                    PainterSupport.ALIGN_CENTER);
            return;
        }
        boolean upsideDown = Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
        PainterSupport.uprightText(graphics, name, pinX - RIGHT_PIN_NAME_INSET, pinY, PIN_NAME_SIZE, scheme.text(),
                upsideDown ? PainterSupport.ALIGN_LEFT : PainterSupport.ALIGN_RIGHT);
    }
}
