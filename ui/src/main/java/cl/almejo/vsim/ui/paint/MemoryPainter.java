package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The RAM and ROM (memory spec §3): a block with its name large in a title bar, «RAM · 256 × 8» small above it, an
 * activity light in the top right and a ▾ / ▸ glyph next to it. Shown ({@code show-contents = yes}) it has a content
 * window of five words around the last address (hex, the row of the last access highlighted); folded it is a compact
 * block with the name and the light. A ROM has a thin band along its top edge. A memory whose {@code CS} is 0 has its
 * light off and its window dimmed; a read lights blue and a write orange ({@code memoryRead} / {@code memoryWrite} of
 * the scheme, read at paint time), with an {@code RD} / {@code WR} badge when there is room. A dashed outline in the
 * conflict colour warns that the contents do not fit the memory (a word beyond its size or wider than its width).
 * Turned a quarter, the part is drawn folded (a window would not read sideways). Every text is upright and clipped to
 * the body. Pins, leads and the clock mark follow the pins of the type as its parameters shape it.
 */
public final class MemoryPainter implements ComponentPainter {

    private static final double LEAD = UNIT / 2;
    private static final double MARGIN = 1;
    private static final double TITLE_HEIGHT = 21;
    private static final double BAND_HEIGHT = 3;
    private static final double SIDE_ROOM = 16;
    private static final double ROW_HEIGHT = 10;
    private static final double BOTTOM_ROOM = 12;
    private static final int WINDOW_ROWS = 5;
    private static final double TEXT_INSET = 6;
    private static final double GLYPH_FROM_RIGHT = 21;
    private static final double LIGHT_FROM_RIGHT = 9;
    private static final double LIGHT_RADIUS = 3;
    private static final double GLYPH_HALF = 2.5;
    private static final float NAME_SIZE = 8.5f;
    private static final float FOLDED_NAME_SIZE = 10f;
    private static final float SUBTITLE_SIZE = 5f;
    private static final float ROW_SIZE = 6.5f;
    private static final float PIN_NAME_SIZE = 6f;
    private static final float BADGE_SIZE = 6f;
    private static final float MINIMUM_SIZE = 3f;
    private static final int ROW_HIGHLIGHT_ALPHA = 80;
    private static final float DIMMED_ALPHA = 0.4f;
    private static final int BAND_ALPHA = 170;
    private static final double TEXT_GAP = 2;
    private static final BasicStroke BUS_STROKE = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke WARNING_STROKE = new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 3f}, 0f);
    private static final String CLOCK_PIN = "CLK";

    /** The decoded contents of the last few contents texts seen, so a paint never decodes (the text rarely changes). */
    private record Decoded(String text, MemoryContents contents, long highestAddress, int widestBits) {
    }

    /** How many memories' decoded contents are kept: a circuit with a few memories decodes none per frame. */
    private static final int DECODED_CACHE_SIZE = 8;

    private final Map<String, Decoded> decodedByText = new LinkedHashMap<>(DECODED_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Decoded> eldest) {
            return size() > DECODED_CACHE_SIZE;
        }
    };
    private int decodeCount;

    /** What a memory is called on the canvas: its label, or {@code RAM1} / {@code ROM2} by its place among the memories. */
    public static String displayName(String label, MemoryKind kind, int ordinal) {
        if (!label.isBlank()) {
            return label;
        }
        String base = kind.id().toUpperCase(Locale.ROOT);
        return ordinal > 0 ? base + ordinal : base;
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        MemoryKind kind = MemoryTypes.kindOf(type.id()).orElse(MemoryKind.RAM);
        paintWithLabel(graphics, type, parameters, state, scheme, displayName("", kind, 0));
    }

    /** Draws the memory with {@code name} (already {@link #displayName}d) in its title bar. */
    public void paintWithLabel(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                               VisibleState state, ColorScheme scheme, String name) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        ComponentType shaped = type.specializedFor(parameters);
        MemoryKind kind = MemoryTypes.kindOf(type.id()).orElse(MemoryKind.RAM);
        VisibleState.MemoryState memory = state instanceof VisibleState.MemoryState known ? known : null;
        boolean enabled = memory == null || memory.enabled();
        VisibleState.MemoryAccess access = memory == null ? VisibleState.MemoryAccess.NONE : memory.lastAccess();
        boolean sideways = PainterSupport.turnedSideways(graphics);
        boolean folded = sideways || MemoryTypes.SHOW_CONTENTS_NO.equals(resolved.get(MemoryTypes.SHOW_CONTENTS));
        Decoded decoded = decoded((String) resolved.get(MemoryTypes.CONTENTS));

        Rectangle2D box = bodyBox(shaped);
        Shape outline = new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 4, 4);
        PainterSupport.body(graphics, outline, scheme);
        paintLeads(graphics, shaped, box, outline, scheme);
        if (kind == MemoryKind.ROM) {
            paintBand(graphics, outline, box, scheme);
        }
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            paintTitle(clipped, box, resolved, shaped, name, folded, sideways, enabled, access, scheme);
            if (!folded) {
                paintWindow(clipped, shaped, box, resolved, memory, decoded, enabled, access, scheme);
            }
            paintPinNames(clipped, shaped, box, scheme);
        } finally {
            clipped.dispose();
        }
        paintClockMark(graphics, shaped, box, scheme);
        if (doesNotFit(resolved, decoded)) {
            graphics.setColor(scheme.wireConflict());
            graphics.setStroke(WARNING_STROKE);
            graphics.draw(new RoundRectangle2D.Double(box.getX() + 2, box.getY() + 2, box.getWidth() - 4,
                    box.getHeight() - 4, 3, 3));
        }
        PainterSupport.pinDots(graphics, shaped, scheme);
    }

    private Decoded decoded(String contents) {
        synchronized (decodedByText) {
            Decoded known = decodedByText.get(contents);
            if (known != null) {
                return known;
            }
        }
        MemoryContents parsed = MemoryContents.decode(contents);
        Decoded fresh = new Decoded(contents, parsed, parsed.highestAddress(), parsed.widestBits());
        synchronized (decodedByText) {
            decodeCount++;
            decodedByText.put(contents, fresh);
        }
        return fresh;
    }

    /** How many times a contents text was decoded (tests). */
    int decodeCount() {
        synchronized (decodedByText) {
            return decodeCount;
        }
    }

    private static boolean doesNotFit(Map<String, Object> resolved, Decoded decoded) {
        long words = MemoryTypes.words(resolved);
        int width = (Integer) resolved.get(MemoryTypes.WIDTH);
        return decoded.highestAddress() >= words || decoded.widestBits() > width;
    }

    private static Rectangle2D bodyBox(ComponentType type) {
        boolean left = false;
        boolean right = false;
        boolean bottom = false;
        for (PinSpec pin : type.pins()) {
            switch (side(type, pin)) {
                case LEFT -> left = true;
                case RIGHT -> right = true;
                case BOTTOM -> bottom = true;
                default -> {
                }
            }
        }
        double leftEdge = left ? LEAD : MARGIN;
        double rightEdge = right ? LEAD : MARGIN;
        double bottomEdge = bottom ? LEAD : MARGIN;
        return new Rectangle2D.Double(leftEdge, MARGIN, type.size().width() * UNIT - leftEdge - rightEdge,
                type.size().height() * UNIT - MARGIN - bottomEdge);
    }

    private enum Side { LEFT, RIGHT, TOP, BOTTOM, NONE }

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

    private static void paintLeads(Graphics2D graphics, ComponentType type, Rectangle2D box, Shape outline,
                                   ColorScheme scheme) {
        for (PinSpec pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            double endX = pinX;
            double endY = pinY;
            switch (side(type, pin)) {
                case LEFT -> endX = box.getX();
                case RIGHT -> endX = box.getMaxX();
                case TOP -> endY = box.getY();
                case BOTTOM -> endY = box.getMaxY();
                default -> {
                    continue;
                }
            }
            if (pin.width() > 1) {
                graphics.setColor(scheme.componentStroke());
                graphics.setStroke(BUS_STROKE);
                graphics.draw(new java.awt.geom.Line2D.Double(pinX, pinY, endX, endY));
            } else {
                PainterSupport.lead(graphics, pinX, pinY, endX, endY, scheme);
            }
        }
    }

    /** The ROM's thin band along the top edge, so it never reads as a RAM at a glance. */
    private static void paintBand(Graphics2D graphics, Shape outline, Rectangle2D box, ColorScheme scheme) {
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            clipped.setColor(PainterSupport.withAlpha(scheme.componentStroke(), BAND_ALPHA));
            clipped.fill(new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), BAND_HEIGHT));
        } finally {
            clipped.dispose();
        }
    }

    private void paintTitle(Graphics2D graphics, Rectangle2D box, Map<String, Object> resolved, ComponentType type,
                            String name, boolean folded, boolean sideways, boolean enabled,
                            VisibleState.MemoryAccess access, ColorScheme scheme) {
        String subtitle = Messages.format("memory.subtitle", Messages.componentName(type.id()),
                Messages.choiceName(MemoryTypes.WORDS, String.valueOf(resolved.get(MemoryTypes.WORDS))),
                String.valueOf(resolved.get(MemoryTypes.WIDTH)));
        double across = sideways ? box.getHeight() : box.getWidth();
        if (folded) {
            double centerX = box.getCenterX();
            double centerY = box.getCenterY();
            drawText(graphics, subtitle, centerX, centerY - 9, SUBTITLE_SIZE, across - 2 * TEXT_INSET,
                    PainterSupport.ALIGN_CENTER, scheme.text());
            drawText(graphics, name, centerX, centerY + 1, FOLDED_NAME_SIZE, across - 2 * TEXT_INSET,
                    PainterSupport.ALIGN_CENTER, scheme.text());
            if (!sideways) {
                paintBadge(graphics, centerX, centerY + 11, enabled, access, PainterSupport.ALIGN_CENTER, scheme);
            }
        } else {
            double available = box.getWidth() - TEXT_INSET - GLYPH_FROM_RIGHT - GLYPH_HALF - TEXT_GAP;
            graphics.setColor(scheme.componentShade());
            graphics.fill(new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), TITLE_HEIGHT));
            graphics.setColor(PainterSupport.withAlpha(scheme.componentStroke(), BAND_ALPHA / 2));
            graphics.setStroke(PainterSupport.STROKE);
            graphics.draw(new java.awt.geom.Line2D.Double(box.getX(), box.getY() + TITLE_HEIGHT, box.getMaxX(),
                    box.getY() + TITLE_HEIGHT));
            drawText(graphics, subtitle, box.getX() + TEXT_INSET, box.getY() + 8, SUBTITLE_SIZE, available,
                    PainterSupport.ALIGN_LEFT, scheme.text());
            drawText(graphics, name, box.getX() + TEXT_INSET, box.getY() + 14.5, NAME_SIZE, available,
                    PainterSupport.ALIGN_LEFT, scheme.text());
        }
        paintLightAndGlyph(graphics, box, enabled, access, !folded, scheme);
    }

    private void paintLightAndGlyph(Graphics2D graphics, Rectangle2D box, boolean enabled,
                                    VisibleState.MemoryAccess access, boolean shown, ColorScheme scheme) {
        double lightX = box.getMaxX() - LIGHT_FROM_RIGHT;
        double lightY = box.getY() + 9;
        Color active = enabled ? activityColor(access, scheme) : null;
        Ellipse2D light = new Ellipse2D.Double(lightX - LIGHT_RADIUS, lightY - LIGHT_RADIUS, 2 * LIGHT_RADIUS,
                2 * LIGHT_RADIUS);
        graphics.setColor(active != null ? active : scheme.componentShade());
        graphics.fill(light);
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(PainterSupport.STROKE);
        graphics.draw(light);
        double glyphX = box.getMaxX() - GLYPH_FROM_RIGHT;
        Path2D glyph = new Path2D.Double();
        if (shown) {
            glyph.moveTo(glyphX - GLYPH_HALF, lightY - 1.5);
            glyph.lineTo(glyphX + GLYPH_HALF, lightY - 1.5);
            glyph.lineTo(glyphX, lightY + 2);
        } else {
            glyph.moveTo(glyphX - 1.5, lightY - GLYPH_HALF);
            glyph.lineTo(glyphX - 1.5, lightY + GLYPH_HALF);
            glyph.lineTo(glyphX + 2, lightY);
        }
        glyph.closePath();
        graphics.setColor(scheme.text());
        graphics.fill(glyph);
    }

    private static Color activityColor(VisibleState.MemoryAccess access, ColorScheme scheme) {
        return switch (access) {
            case READ -> scheme.memoryRead();
            case WRITE -> scheme.memoryWrite();
            case NONE -> null;
        };
    }

    private void paintBadge(Graphics2D graphics, double anchorX, double anchorY, boolean enabled,
                            VisibleState.MemoryAccess access, double alignment, ColorScheme scheme) {
        Color color = enabled ? activityColor(access, scheme) : null;
        if (color == null) {
            return;
        }
        String text = Messages.text(access == VisibleState.MemoryAccess.READ ? "memory.badge.read"
                : "memory.badge.write");
        drawText(graphics, text, anchorX, anchorY, BADGE_SIZE, Double.MAX_VALUE, alignment, color);
    }

    private void paintWindow(Graphics2D graphics, ComponentType type, Rectangle2D box, Map<String, Object> resolved,
                             VisibleState.MemoryState memory, Decoded decoded, boolean enabled,
                             VisibleState.MemoryAccess access, ColorScheme scheme) {
        long words = MemoryTypes.words(resolved);
        int width = (Integer) resolved.get(MemoryTypes.WIDTH);
        int addressBits = MemoryTypes.addressBits(resolved);
        int valueDigits = Math.max(1, (width + 3) / 4);
        long first = memory == null ? 0 : memory.firstWindowAddress();
        long mask = width >= Long.SIZE ? -1L : (1L << width) - 1;
        double left = box.getX() + SIDE_ROOM;
        double right = box.getMaxX() - SIDE_ROOM;
        double top = box.getY() + TITLE_HEIGHT + 3;
        int rows = (int) Math.min(Math.min(WINDOW_ROWS, words),
                Math.max(0, Math.floor((box.getMaxY() - BOTTOM_ROOM - top) / ROW_HEIGHT)));
        Graphics2D window = (Graphics2D) graphics.create();
        try {
            if (!enabled) {
                window.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIMMED_ALPHA));
            }
            for (int row = 0; row < rows; row++) {
                long address = first + row;
                long value = memory == null ? decoded.contents().word(address) & mask
                        : row < memory.window().size() ? memory.window().get(row) : 0;
                double centerY = top + row * ROW_HEIGHT + ROW_HEIGHT / 2;
                boolean current = memory != null && address == memory.lastAddress();
                Color activity = activityColor(access, scheme);
                if (current && activity != null) {
                    window.setColor(PainterSupport.withAlpha(activity, ROW_HIGHLIGHT_ALPHA));
                    window.fill(new Rectangle2D.Double(left, centerY - ROW_HEIGHT / 2, right - left, ROW_HEIGHT));
                }
                boolean written = current && access == VisibleState.MemoryAccess.WRITE;
                drawText(window, addressText(address, addressBits), left + 2, centerY, ROW_SIZE, (right - left) / 2,
                        PainterSupport.ALIGN_LEFT, scheme.text());
                drawText(window, hex(value, valueDigits), right - 2, centerY, ROW_SIZE, (right - left) / 2,
                        PainterSupport.ALIGN_RIGHT, written ? scheme.memoryWrite() : scheme.text());
            }
            if (memory != null && enabled && rows > 0) {
                paintBadge(window, box.getMaxX() - SIDE_ROOM, box.getMaxY() - BOTTOM_ROOM + 5, true, access,
                        PainterSupport.ALIGN_RIGHT, scheme);
            }
        } finally {
            window.dispose();
        }
    }

    private static String hex(long value, int digits) {
        String text = Long.toHexString(value).toUpperCase(Locale.ROOT);
        return text.length() >= digits ? text : "0".repeat(digits - text.length()) + text;
    }

    private void paintPinNames(Graphics2D graphics, ComponentType type, Rectangle2D box, ColorScheme scheme) {
        for (PinSpec pin : type.pins()) {
            if (pin.name().isBlank() || pin.name().equals(CLOCK_PIN)) {
                continue;
            }
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            switch (side(type, pin)) {
                case LEFT -> drawText(graphics, pin.name(), box.getX() + 2, pinY, PIN_NAME_SIZE, SIDE_ROOM - 2,
                        PainterSupport.ALIGN_LEFT, scheme.text());
                case RIGHT -> drawText(graphics, pin.name(), box.getMaxX() - 2, pinY, PIN_NAME_SIZE, SIDE_ROOM - 2,
                        PainterSupport.ALIGN_RIGHT, scheme.text());
                case BOTTOM -> drawText(graphics, pin.name(), pinX, box.getMaxY() - 5, PIN_NAME_SIZE, UNIT * 2 - 2,
                        PainterSupport.ALIGN_CENTER, scheme.text());
                default -> {
                }
            }
        }
    }

    /** The clock triangle on the bottom edge, at the CLK pin. */
    private static void paintClockMark(Graphics2D graphics, ComponentType type, Rectangle2D box, ColorScheme scheme) {
        for (PinSpec pin : type.pins()) {
            if (pin.name().equals(CLOCK_PIN) && side(type, pin) == Side.BOTTOM) {
                double pinX = pin.position().x() * UNIT;
                Path2D mark = new Path2D.Double();
                mark.moveTo(pinX - 4, box.getMaxY());
                mark.lineTo(pinX, box.getMaxY() - 5);
                mark.lineTo(pinX + 4, box.getMaxY());
                PainterSupport.outline(graphics, mark, scheme);
            }
        }
    }

    /** Upright text anchored at the point, shrunk to fit {@code available}. */
    private static void drawText(Graphics2D graphics, String text, double anchorX, double anchorY, float size,
                                 double available, double alignment, Color color) {
        float fitted = size;
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(fitted));
        double width = metrics.getStringBounds(text, graphics).getWidth();
        if (width > available && width > 0) {
            fitted = (float) Math.max(MINIMUM_SIZE, size * available / width);
            while (fitted > MINIMUM_SIZE && graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(fitted))
                    .getStringBounds(text, graphics).getWidth() > available) {
                fitted = Math.max(MINIMUM_SIZE, fitted - 0.25f);
            }
        }
        PainterSupport.uprightText(graphics, text, anchorX, anchorY, fitted, color, alignment);
    }

    /** The address text of a window row: hex, as many digits as the address bits need (\u00ab0F\u00bb for 256 words). */
    static String addressText(long address, int addressBits) {
        return hex(address, Math.max(1, (addressBits + 3) / 4));
    }
}
