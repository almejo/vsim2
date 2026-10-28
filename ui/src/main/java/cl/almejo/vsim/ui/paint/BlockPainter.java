package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The look shared by every «Bloques» block (M7): one configurable painter, so each block only says its symbol, its body
 * and, when it has one, the live text it shows. It draws, in this order:
 * <ol>
 *     <li>the body ({@link Body}) with {@link PainterSupport#body}, inset half a grid unit on every side that has pins
 *     (room for the leads) and one pixel on the others;</li>
 *     <li>a lead from each pin on the box's edge to the body, thick for a bus pin (wider than one bit);</li>
 *     <li>the clock triangle on the body's edge at every pin named {@value #CLOCK_PIN} (instead of its name);</li>
 *     <li>the symbol in the middle and, under it on screen, the live text (if the state gives one); with
 *     {@link #withCornerSymbol}, the symbol small in a corner and the live text in lines in the middle;</li>
 *     <li>each pin's short name inside the body next to its pin (left pins left-aligned, right pins right-aligned,
 *     top and bottom pins centred under or over them), shrunk to fit and never outside the body;</li>
 *     <li>the pin dots, last.</li>
 * </ol>
 * The pins come from the type as the parameters shape it ({@link ComponentType#specializedFor}), so the painter works
 * for any width and for blocks that grow with their inputs. A pin is on the left edge when x = 0, on the right when
 * x = the type's width, else on the top when y = 0 and on the bottom when y = the type's height; a pin elsewhere gets
 * no lead and no name. Texts are drawn only through {@link PainterSupport}, upright in every rotation; colours come from
 * the scheme at paint time. With {@link VisibleState#NONE} (the palette icon) the live text function decides what to
 * show, usually nothing or the block's state at power on.
 */
public final class BlockPainter implements ComponentPainter {

    /** The outline of a block's body. */
    public enum Body {
        /** A rounded rectangle (decoder, comparator, register, counter). */
        RECTANGLE,
        /**
         * The ALU chevron: the left edge is full height with a notch in its middle (keep left pins off the middle
         * eighth), the right edge spans the middle half of the height and the top and bottom edges slant between them.
         */
        CHEVRON,
        /** Like {@link #CHEVRON} without the notch: narrow at the output side (the mux). */
        TRAPEZOID
    }

    /** The name of a clock pin: drawn as the clock triangle instead of a name. */
    public static final String CLOCK_PIN = "CLK";

    private static final double LEAD = UNIT / 2;
    private static final double MARGIN = 1;
    private static final double INSET = 2;
    private static final float SYMBOL_SIZE = 9f;
    private static final float LIVE_TEXT_SIZE = 7f;
    /** The thin register's value: larger than the usual live text, the symbol is out of its way. */
    private static final float CORNER_LIVE_TEXT_SIZE = 9f;
    private static final float CORNER_SYMBOL_SIZE = 5f;
    private static final float PIN_NAME_SIZE = 6f;
    private static final float MINIMUM_SIZE = 4f;
    /**
     * Smaller than the other texts' minimum so a register's 32 binary digits fit its body, upright and also sideways,
     * where the body is only a few units across (readable with zoom).
     */
    private static final float LIVE_TEXT_MINIMUM_SIZE = 1.5f;
    private static final double TEXT_GAP = 1;
    private static final double CLOCK_HALF_BASE = 4;
    private static final double CLOCK_DEPTH = 5;
    private static final BasicStroke BUS_STROKE = new BasicStroke(3.5f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final BiFunction<VisibleState, Map<String, Object>, Optional<String>> NO_LIVE_TEXT =
            (state, parameters) -> Optional.empty();

    private enum Side { LEFT, RIGHT, TOP, BOTTOM, NONE }

    private final String symbol;
    private final Body body;
    private final BiFunction<VisibleState, Map<String, Object>, Optional<String>> liveText;
    private final boolean cornerSymbol;

    /** A block drawn with {@code body}, showing {@code symbol} (such as {@code Σ} or {@code ALU}) in its middle. */
    public BlockPainter(String symbol, Body body) {
        this(symbol, body, NO_LIVE_TEXT, false);
    }

    private BlockPainter(String symbol, Body body,
                         BiFunction<VisibleState, Map<String, Object>, Optional<String>> liveText,
                         boolean cornerSymbol) {
        this.symbol = symbol;
        this.body = body;
        this.liveText = liveText;
        this.cornerSymbol = cornerSymbol;
    }

    /** This painter, also writing under the symbol the text {@code text} gives for the state (nothing when empty). */
    public BlockPainter withLiveText(Function<VisibleState, Optional<String>> text) {
        return withLiveText((state, parameters) -> text.apply(state));
    }

    /**
     * Like {@link #withLiveText(Function)} for a text that also depends on the block's parameters, resolved (every
     * parameter present, normalized), such as the «Mostrar como» format of a register.
     */
    public BlockPainter withLiveText(BiFunction<VisibleState, Map<String, Object>, Optional<String>> text) {
        return new BlockPainter(symbol, body, text, cornerSymbol);
    }

    /**
     * This painter with the symbol small in the body's top left corner and the live text large in the middle, one line
     * per {@code \n} (the thin register: «REG» in the corner, its value in one or two lines).
     */
    public BlockPainter withCornerSymbol() {
        return new BlockPainter(symbol, body, liveText, true);
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        ComponentType shaped = type.specializedFor(parameters);
        double width = shaped.size().width() * UNIT;
        double height = shaped.size().height() * UNIT;
        Rectangle2D box = bodyBox(shaped);
        Shape outline = outline(box);
        PainterSupport.body(graphics, outline, scheme);
        paintLeads(graphics, shaped, box, outline, width, height, scheme);
        boolean sideways = PainterSupport.turnedSideways(graphics);
        for (PinSpec pin : shaped.pins()) {
            if (pin.name().equals(CLOCK_PIN)) {
                paintClockMark(graphics, pin, side(shaped, pin), box, scheme);
            }
        }
        Optional<String> live = liveText.apply(state, resolved);
        if (cornerSymbol) {
            paintCornerSymbolAndLines(graphics, shaped, outline, box, live.orElse(""), sideways, scheme);
        } else {
            paintSymbolAndLiveText(graphics, outline, box, live, sideways, scheme);
        }
        for (PinSpec pin : shaped.pins()) {
            if (!pin.name().isBlank() && !pin.name().equals(CLOCK_PIN)) {
                paintPinName(graphics, shaped, pin, box, outline, sideways, scheme);
            }
        }
        PainterSupport.pinDots(graphics, shaped, scheme);
    }

    /** The rectangle the body fits in: half a grid unit in from each side that has pins, one pixel from the others. */
    private static Rectangle2D bodyBox(ComponentType type) {
        boolean[] hasPins = new boolean[Side.values().length];
        for (PinSpec pin : type.pins()) {
            hasPins[side(type, pin).ordinal()] = true;
        }
        double left = hasPins[Side.LEFT.ordinal()] ? LEAD : MARGIN;
        double right = hasPins[Side.RIGHT.ordinal()] ? LEAD : MARGIN;
        double top = hasPins[Side.TOP.ordinal()] ? LEAD : MARGIN;
        double bottom = hasPins[Side.BOTTOM.ordinal()] ? LEAD : MARGIN;
        return new Rectangle2D.Double(left, top, type.size().width() * UNIT - left - right,
                type.size().height() * UNIT - top - bottom);
    }

    private static Side side(ComponentType type, PinSpec pin) {
        int x = pin.position().x();
        int y = pin.position().y();
        if (x <= 0) {
            return Side.LEFT;
        }
        if (x >= type.size().width()) {
            return Side.RIGHT;
        }
        if (y <= 0) {
            return Side.TOP;
        }
        if (y >= type.size().height()) {
            return Side.BOTTOM;
        }
        return Side.NONE;
    }

    private Shape outline(Rectangle2D box) {
        if (body == Body.RECTANGLE) {
            return new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 4, 4);
        }
        double left = box.getX();
        double right = box.getMaxX();
        double top = box.getY();
        double bottom = box.getMaxY();
        double quarter = box.getHeight() / 4;
        Path2D path = new Path2D.Double();
        path.moveTo(left, top);
        path.lineTo(right, top + quarter);
        path.lineTo(right, bottom - quarter);
        path.lineTo(left, bottom);
        if (body == Body.CHEVRON) {
            double notchHalfHeight = box.getHeight() / 16;
            double notchDepth = Math.min(box.getWidth() / 4, 2 * notchHalfHeight);
            double middle = box.getCenterY();
            path.lineTo(left, middle + notchHalfHeight);
            path.lineTo(left + notchDepth, middle);
            path.lineTo(left, middle - notchHalfHeight);
        }
        path.closePath();
        return path;
    }

    /** The y of the body's top edge at local x (the top of the box for a rectangle, the slant otherwise). */
    private double topEdgeAt(Rectangle2D box, double x) {
        if (body == Body.RECTANGLE) {
            return box.getY();
        }
        double fraction = Math.clamp((x - box.getX()) / box.getWidth(), 0, 1);
        return box.getY() + fraction * box.getHeight() / 4;
    }

    private double bottomEdgeAt(Rectangle2D box, double x) {
        return box.getMaxY() - (topEdgeAt(box, x) - box.getY());
    }

    /** A lead from each pin straight towards the middle of the box, drawn only outside the body. */
    private static void paintLeads(Graphics2D graphics, ComponentType type, Rectangle2D box, Shape outline,
                                   double width, double height, ColorScheme scheme) {
        Area outside = new Area(new Rectangle2D.Double(-UNIT, -UNIT, width + 2 * UNIT, height + 2 * UNIT));
        outside.subtract(new Area(outline));
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outside);
            for (PinSpec pin : type.pins()) {
                double pinX = pin.position().x() * UNIT;
                double pinY = pin.position().y() * UNIT;
                Line2D lead = switch (side(type, pin)) {
                    case LEFT, RIGHT -> new Line2D.Double(pinX, pinY, box.getCenterX(), pinY);
                    case TOP, BOTTOM -> new Line2D.Double(pinX, pinY, pinX, box.getCenterY());
                    case NONE -> null;
                };
                if (lead == null) {
                    continue;
                }
                if (pin.width() > 1) {
                    clipped.setColor(scheme.componentStroke());
                    clipped.setStroke(BUS_STROKE);
                    clipped.draw(lead);
                } else {
                    PainterSupport.lead(clipped, lead.getX1(), lead.getY1(), lead.getX2(), lead.getY2(), scheme);
                }
            }
        } finally {
            clipped.dispose();
        }
    }

    /** The clock triangle: its base on the body's edge across the pin, its tip pointing into the body. */
    private void paintClockMark(Graphics2D graphics, PinSpec pin, Side side, Rectangle2D box, ColorScheme scheme) {
        double pinX = pin.position().x() * UNIT;
        double pinY = pin.position().y() * UNIT;
        Point2D base;
        Point2D inward;
        switch (side) {
            case LEFT -> {
                base = new Point2D.Double(box.getX(), pinY);
                inward = new Point2D.Double(1, 0);
            }
            case RIGHT -> {
                base = new Point2D.Double(box.getMaxX(), pinY);
                inward = new Point2D.Double(-1, 0);
            }
            case TOP -> {
                base = new Point2D.Double(pinX, topEdgeAt(box, pinX));
                inward = new Point2D.Double(0, 1);
            }
            case BOTTOM -> {
                base = new Point2D.Double(pinX, bottomEdgeAt(box, pinX));
                inward = new Point2D.Double(0, -1);
            }
            default -> {
                return;
            }
        }
        // Along the edge: the inward direction turned a quarter.
        double alongX = -inward.getY();
        double alongY = inward.getX();
        Path2D mark = new Path2D.Double();
        mark.moveTo(base.getX() - alongX * CLOCK_HALF_BASE, base.getY() - alongY * CLOCK_HALF_BASE);
        mark.lineTo(base.getX() + inward.getX() * CLOCK_DEPTH, base.getY() + inward.getY() * CLOCK_DEPTH);
        mark.lineTo(base.getX() + alongX * CLOCK_HALF_BASE, base.getY() + alongY * CLOCK_HALF_BASE);
        PainterSupport.outline(graphics, mark, scheme);
    }

    /** The symbol in the middle of the box; with a live text, the two stacked one over the other on screen. */
    private void paintSymbolAndLiveText(Graphics2D graphics, Shape outline, Rectangle2D box, Optional<String> live,
                                        boolean sideways, ColorScheme scheme) {
        double across = (sideways ? box.getHeight() : box.getWidth()) - 2 * INSET;
        float symbolSize = fitted(graphics, symbol, SYMBOL_SIZE, across);
        double centerX = box.getCenterX();
        double centerY = box.getCenterY();
        if (live.isEmpty() || live.get().isEmpty()) {
            PainterSupport.centeredText(graphics, outline, symbol, centerX, centerY, symbolSize, scheme.text());
            return;
        }
        String text = live.get();
        float liveSize = fitted(graphics, text, LIVE_TEXT_SIZE, across, LIVE_TEXT_MINIMUM_SIZE);
        double symbolHeight = textHeight(graphics, symbolSize);
        double liveHeight = textHeight(graphics, liveSize);
        double total = symbolHeight + TEXT_GAP * 2 + liveHeight;
        Point2D down = screenDown(graphics);
        double symbolOffset = -total / 2 + symbolHeight / 2;
        double liveOffset = total / 2 - liveHeight / 2;
        PainterSupport.centeredText(graphics, outline, symbol, centerX + down.getX() * symbolOffset,
                centerY + down.getY() * symbolOffset, symbolSize, scheme.text());
        PainterSupport.centeredText(graphics, outline, text, centerX + down.getX() * liveOffset,
                centerY + down.getY() * liveOffset, liveSize, scheme.text());
    }

    /**
     * The symbol small in the body's top left corner (local; another corner on screen when turned) and the lines of
     * {@code live} stacked in the middle of what the pin names and the symbol leave free, all at one size that fits
     * the widest line across that area and every line along it.
     */
    private void paintCornerSymbolAndLines(Graphics2D graphics, ComponentType type, Shape outline, Rectangle2D box,
                                           String live, boolean sideways, ColorScheme scheme) {
        float symbolSize = fitted(graphics, symbol, CORNER_SYMBOL_SIZE, box.getWidth() / 2);
        double symbolWidth = textWidth(graphics, symbol, symbolSize);
        double symbolHeight = textHeight(graphics, symbolSize);
        double localSymbolWidth = sideways ? symbolHeight : symbolWidth;
        double localSymbolHeight = sideways ? symbolWidth : symbolHeight;
        // Upright the symbol takes a band over the value; turned sideways it sits after the left pins' names (on
        // screen: under the top names) and takes a band along the value instead.
        Rectangle2D area = valueArea(graphics, type, box, sideways ? 0 : INSET + symbolHeight + TEXT_GAP, sideways);
        double symbolLeft = sideways ? area.getX() : box.getX() + INSET;
        PainterSupport.centeredText(graphics, outline, symbol, symbolLeft + localSymbolWidth / 2,
                box.getY() + INSET + localSymbolHeight / 2, symbolSize, scheme.text());
        if (live.isEmpty()) {
            return;
        }
        if (sideways) {
            double symbolBand = localSymbolWidth + TEXT_GAP;
            area = new Rectangle2D.Double(area.getX() + symbolBand, area.getY(),
                    Math.max(1, area.getWidth() - symbolBand), area.getHeight());
        }
        double across = sideways ? area.getHeight() : area.getWidth();
        double along = sideways ? area.getWidth() : area.getHeight();
        String[] lines = live.split("\\n");
        float size = CORNER_LIVE_TEXT_SIZE;
        for (String line : lines) {
            size = Math.min(size, fitted(graphics, line, CORNER_LIVE_TEXT_SIZE, across, LIVE_TEXT_MINIMUM_SIZE));
        }
        double lineHeight = textHeight(graphics, size) + TEXT_GAP * 2;
        if (lines.length * lineHeight > along && lineHeight > 0) {
            size = (float) Math.max(LIVE_TEXT_MINIMUM_SIZE, size * along / (lines.length * lineHeight));
            lineHeight = textHeight(graphics, size) + TEXT_GAP * 2;
        }
        Point2D down = screenDown(graphics);
        for (int index = 0; index < lines.length; index++) {
            double offset = (index - (lines.length - 1) / 2.0) * lineHeight;
            PainterSupport.centeredText(graphics, outline, lines[index], area.getCenterX() + down.getX() * offset,
                    area.getCenterY() + down.getY() * offset, size, scheme.text());
        }
    }

    /**
     * The part of the body no pin name and no clock triangle uses: on each side with pins, a band as deep as its
     * deepest name (measured as {@link #paintPinName} draws it); the top band at least {@code topBand} deep, and
     * every band at least the usual inset.
     */
    private static Rectangle2D valueArea(Graphics2D graphics, ComponentType type, Rectangle2D box, double topBand,
                                         boolean sideways) {
        double[] bands = new double[Side.values().length];
        Arrays.fill(bands, INSET);
        bands[Side.TOP.ordinal()] = Math.max(INSET, topBand);
        for (PinSpec pin : type.pins()) {
            Side side = side(type, pin);
            if (side == Side.NONE || pin.name().isBlank()) {
                continue;
            }
            double depth = CLOCK_DEPTH;
            if (!pin.name().equals(CLOCK_PIN)) {
                boolean runsAcrossTheEdge = (side == Side.LEFT || side == Side.RIGHT) != sideways;
                depth = runsAcrossTheEdge ? textWidth(graphics, pin.name(), PIN_NAME_SIZE)
                        : textHeight(graphics, PIN_NAME_SIZE);
            }
            bands[side.ordinal()] = Math.max(bands[side.ordinal()], INSET + depth + TEXT_GAP);
        }
        double left = box.getX() + bands[Side.LEFT.ordinal()];
        double top = box.getY() + bands[Side.TOP.ordinal()];
        double right = box.getMaxX() - bands[Side.RIGHT.ordinal()];
        double bottom = box.getMaxY() - bands[Side.BOTTOM.ordinal()];
        return new Rectangle2D.Double(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    /** A pin's name inside the body, next to its pin. */
    private void paintPinName(Graphics2D graphics, ComponentType type, PinSpec pin, Rectangle2D box, Shape outline,
                              boolean sideways, ColorScheme scheme) {
        Side side = side(type, pin);
        if (side == Side.NONE) {
            return;
        }
        String name = pin.name();
        double pinX = pin.position().x() * UNIT;
        double pinY = pin.position().y() * UNIT;
        boolean horizontalSide = side == Side.LEFT || side == Side.RIGHT;
        double gap = gapToNeighbours(type, pin, side, horizontalSide ? box.getHeight() : box.getWidth());
        // The text runs along local x unless the component is turned sideways.
        boolean runsAcrossTheEdge = horizontalSide != sideways;
        double available = runsAcrossTheEdge
                ? (horizontalSide ? box.getWidth() : box.getHeight()) / 2 - INSET
                : gap - 2 * TEXT_GAP;
        float size = fitted(graphics, name, PIN_NAME_SIZE, available);
        double extent = runsAcrossTheEdge ? textWidth(graphics, name, size) : textHeight(graphics, size);
        // On a slanted top or bottom edge, the text clears the edge along its whole span, not only under the pin.
        double halfSpan = (runsAcrossTheEdge ? textHeight(graphics, size) : textWidth(graphics, name, size)) / 2;
        double centerX = pinX;
        double centerY = pinY;
        switch (side) {
            case LEFT -> centerX = box.getX() + INSET + extent / 2;
            case RIGHT -> centerX = box.getMaxX() - INSET - extent / 2;
            case TOP -> centerY = Math.max(topEdgeAt(box, pinX - halfSpan), topEdgeAt(box, pinX + halfSpan))
                    + INSET + extent / 2;
            case BOTTOM -> centerY = Math.min(bottomEdgeAt(box, pinX - halfSpan), bottomEdgeAt(box, pinX + halfSpan))
                    - INSET - extent / 2;
            default -> {
                return;
            }
        }
        PainterSupport.centeredText(graphics, outline, name, centerX, centerY, size, scheme.text());
    }

    /** The distance to the nearest other pin on the same side ({@code fallback} when there is none). */
    private static double gapToNeighbours(ComponentType type, PinSpec pin, Side side, double fallback) {
        boolean horizontalSide = side == Side.LEFT || side == Side.RIGHT;
        double gap = fallback;
        for (PinSpec other : type.pins()) {
            if (other == pin || side(type, other) != side) {
                continue;
            }
            double distance = horizontalSide
                    ? Math.abs(other.position().y() - pin.position().y()) * UNIT
                    : Math.abs(other.position().x() - pin.position().x()) * UNIT;
            if (distance > 0) {
                gap = Math.min(gap, distance);
            }
        }
        return gap;
    }

    /** The local unit vector that points down on screen (the texts stack along it). */
    private static Point2D screenDown(Graphics2D graphics) {
        return switch (PainterSupport.quarterTurns(graphics)) {
            case 1 -> new Point2D.Double(1, 0);
            case -1 -> new Point2D.Double(-1, 0);
            case 2, -2 -> new Point2D.Double(0, -1);
            default -> new Point2D.Double(0, 1);
        };
    }

    private static float fitted(Graphics2D graphics, String text, float preferred, double available) {
        return fitted(graphics, text, preferred, available, MINIMUM_SIZE);
    }

    private static float fitted(Graphics2D graphics, String text, float preferred, double available, float minimum) {
        double width = textWidth(graphics, text, preferred);
        if (width <= available || width == 0) {
            return preferred;
        }
        // Hinting makes widths grow in steps, not in proportion to the size: shrink until the text really fits.
        float size = (float) Math.max(minimum, preferred * available / width);
        while (size > minimum && textWidth(graphics, text, size) > available) {
            size = Math.max(minimum, size - 0.25f);
        }
        return size;
    }

    private static double textHeight(Graphics2D graphics, float size) {
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size));
        return metrics.getAscent() - metrics.getDescent();
    }

    private static double textWidth(Graphics2D graphics, String text, float size) {
        // Measured with the graphics' transform, as PainterSupport draws it: integer advances of a small font at 100 %
        // can be a third shorter than the text drawn at a zoom.
        return graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size)).getStringBounds(text, graphics)
                .getWidth();
    }
}
