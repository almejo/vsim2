package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The RAM and ROM (memory spec §3, mockup §2–3). The title bar has «RAM · 256 × 8» small above the name, and on the
 * right the activity light and the ▾ / ▸ square that folds the content window ({@code show-contents}); while the
 * memory reads or writes, an {@code RD} / {@code WR} pill sits left of the light, and «CS = 0» when it is not selected.
 * Shown, a bordered window lists five words around the last address as «address  word» in monospace; the accessed word
 * is outlined with a light tint, blue for a read and orange for a write (the written value in orange), from the
 * scheme's {@code memoryRead} / {@code memoryWrite} read at paint time; a word written undefined shows «Z»; a memory
 * whose {@code CS} is 0 has its light off and its window dimmed. A ROM has a band along its top edge. A dashed outline
 * in the conflict colour warns that the contents do not fit the memory.
 *
 * <p>The title bar and the window are drawn upright in their own place, so turned upside down the part keeps its title
 * along the bottom edge and its words reading downwards; the square's click area follows ({@link #foldToggleArea}).
 * Turned a quarter, the part is drawn folded with its texts centred and has no square (a window would not read
 * sideways). Every text is clipped to the body. Pins, leads and the clock mark follow the pins of the type.
 */
public final class MemoryPainter implements ComponentPainter {

    private static final double LEAD = UNIT / 2;
    private static final double MARGIN = 1;
    /** The height of the title bar: the subtitle and the name, one above the other. */
    static final double TITLE_HEIGHT = 21;
    private static final double TITLE_MIDDLE = TITLE_HEIGHT / 2;
    private static final double BAND_HEIGHT = 4;
    /** Room on each side of the window for the pin names. */
    private static final double SIDE_ROOM = 16;
    private static final double WINDOW_GAP = 2;
    private static final double WINDOW_PADDING = 2;
    private static final double WINDOW_ARC = 4;
    private static final double ROW_HEIGHT = 10;
    private static final double ROW_TEXT_INSET = 4;
    /** Room under the window for the bottom pins' names (WE, CS). */
    private static final double BOTTOM_ROOM = 12;
    private static final int WINDOW_ROWS = 5;
    private static final double TEXT_INSET = 6;
    private static final double TEXT_GAP = 3;
    private static final double TOGGLE_SIZE = 8;
    private static final double TOGGLE_FROM_RIGHT = 4;
    /** How far around the square a click still folds (about a pixel and a half at 100 %). */
    private static final double TOGGLE_HIT_MARGIN = 1.5;
    private static final double TOGGLE_ARC = 2;
    private static final double GLYPH_HALF = 2;
    private static final double LIGHT_RADIUS = 3;
    private static final double PILL_HEIGHT = 8;
    private static final double PILL_PADDING = 3;
    /**
     * How much a light text on the pill is preferred over a dark one, as a contrast factor: the mockup has white on
     * both activity colours in Claro; on the brighter colours of Oscuro the dark text still wins.
     */
    private static final double LIGHT_TEXT_PREFERENCE = 1.6;
    /** What the name keeps at least before the RD / WR pill or «CS = 0» is left out. */
    private static final double MINIMUM_NAME_ROOM = 24;
    /** The gap between the two hex columns of a row: the mockup's two spaces. */
    private static final String COLUMN_GAP = "  ";
    private static final String UNDEFINED_WORD = "Z";
    private static final float NAME_SIZE = 8.5f;
    private static final float SUBTITLE_SIZE = 5f;
    private static final float ROW_SIZE = 6.5f;
    private static final float PIN_NAME_SIZE = 6f;
    private static final float BADGE_SIZE = 5f;
    private static final float MINIMUM_SIZE = 3f;
    /** The text of the rows other than the accessed one (the mockup's 75 % opacity). */
    private static final int QUIET_ROW_ALPHA = 190;
    /** The tint of the accessed word's box (the mockup's {@code --read-bg}, about 15 %). */
    private static final int ACCESS_TINT_ALPHA = 40;
    private static final float DIMMED_ALPHA = 0.55f;
    /** The band at a quarter of the stroke, as in the mockup. */
    private static final int BAND_ALPHA = 64;
    private static final Font MONOSPACE = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    private static final Font MONOSPACE_BOLD = MONOSPACE.deriveFont(Font.BOLD);
    private static final BasicStroke THIN_STROKE = new BasicStroke(0.8f);
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

    /** What a memory shows now: whether it is selected and its last access (none in the palette and the ghost). */
    private record Activity(boolean enabled, VisibleState.MemoryAccess access) {

        static Activity of(VisibleState.MemoryState memory) {
            return memory == null ? new Activity(true, VisibleState.MemoryAccess.NONE)
                    : new Activity(memory.enabled(), memory.lastAccess());
        }

        /** The colour of the last access while selected, empty when idle or deselected. */
        Optional<Color> color(ColorScheme scheme) {
            if (!enabled) {
                return Optional.empty();
            }
            return switch (access) {
                case READ -> Optional.of(scheme.memoryRead());
                case WRITE -> Optional.of(scheme.memoryWrite());
                case NONE -> Optional.empty();
            };
        }
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        paintWithLabel(graphics, type, parameters, state, scheme, Messages.componentName(type.id()));
    }

    /** Draws the memory with {@code name} (see {@code MemoryNames}) in its title bar. */
    public void paintWithLabel(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                               VisibleState state, ColorScheme scheme, String name) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        ComponentType shaped = type.specializedFor(parameters);
        MemoryKind kind = MemoryTypes.kindOf(type.id()).orElse(MemoryKind.RAM);
        VisibleState.MemoryState memory = state instanceof VisibleState.MemoryState known ? known : null;
        Activity activity = Activity.of(memory);
        boolean sideways = PainterSupport.turnedSideways(graphics);
        boolean folded = sideways || MemoryTypes.SHOW_CONTENTS_NO.equals(resolved.get(MemoryTypes.SHOW_CONTENTS));
        Decoded decoded = decoded((String) resolved.get(MemoryTypes.CONTENTS));

        Rectangle2D box = bodyBox(shaped);
        Shape outline = new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 4, 4);
        PainterSupport.body(graphics, outline, scheme);
        paintLeads(graphics, shaped, box, scheme);
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            if (kind == MemoryKind.ROM) {
                paintBand(clipped, box, scheme);
            }
            String subtitle = subtitle(type, resolved);
            if (sideways) {
                paintSidewaysTitle(clipped, box, subtitle, name, activity, scheme);
            } else {
                paintTitle(clipped, titleBar(box), subtitle, name, memory != null, activity, folded, scheme);
            }
            if (!folded) {
                paintWindow(clipped, windowBox(box, resolved), resolved, memory, decoded, activity, scheme);
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

    /**
     * Where a click on the ▾ / ▸ square folds or unfolds the window, in the part's local coordinates (before its
     * rotation and place): the square and a little around it. Empty turned a quarter, where the part is always folded.
     */
    public static Optional<Rectangle2D> foldToggleArea(ComponentType specialized, Rotation rotation) {
        if (rotation.quarterTurns() % 2 != 0) {
            return Optional.empty();
        }
        Rectangle2D titleBar = titleBar(bodyBox(specialized));
        Rectangle2D square = toggleSquare(titleBar);
        Rectangle2D area = new Rectangle2D.Double(square.getX() - TOGGLE_HIT_MARGIN, square.getY() - TOGGLE_HIT_MARGIN,
                square.getWidth() + 2 * TOGGLE_HIT_MARGIN, square.getHeight() + 2 * TOGGLE_HIT_MARGIN);
        if (rotation == Rotation.R0) {
            return Optional.of(area);
        }
        // Upside down the title bar is drawn turned about its centre: the square sits at its other end.
        return Optional.of(new Rectangle2D.Double(2 * titleBar.getCenterX() - area.getMaxX(),
                2 * titleBar.getCenterY() - area.getMaxY(), area.getWidth(), area.getHeight()));
    }

    /**
     * Whether {@code gridPosition} (grid units) is on the fold square of the memory {@code component} of {@code type}
     * as it is drawn on the canvas (the inverse of {@link ComponentDrawing#paintPlaced}).
     */
    public static boolean isOnFoldToggle(ComponentType type, Component component, Point2D gridPosition) {
        Optional<Rectangle2D> area = foldToggleArea(type.specializedFor(component.parameters()),
                component.rotation());
        if (area.isEmpty()) {
            return false;
        }
        AffineTransform placed = new AffineTransform();
        placed.translate(component.position().x() * UNIT, component.position().y() * UNIT);
        placed.rotate(component.rotation().quarterTurns() * Math.PI / 2);
        try {
            Point2D local = placed.inverseTransform(new Point2D.Double(gridPosition.getX() * UNIT,
                    gridPosition.getY() * UNIT), null);
            return area.get().contains(local);
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalStateException("a rotation and a translation are always invertible", exception);
        }
    }

    /** The title bar of a memory of the (specialized) type, in local coordinates. */
    static Rectangle2D titleBar(ComponentType specialized) {
        return titleBar(bodyBox(specialized));
    }

    private static Rectangle2D titleBar(Rectangle2D box) {
        return new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), TITLE_HEIGHT);
    }

    /** The ▾ / ▸ square at the right end of an upright title bar. */
    private static Rectangle2D toggleSquare(Rectangle2D titleBar) {
        return new Rectangle2D.Double(titleBar.getMaxX() - TOGGLE_FROM_RIGHT - TOGGLE_SIZE,
                titleBar.getY() + TITLE_MIDDLE - TOGGLE_SIZE / 2, TOGGLE_SIZE, TOGGLE_SIZE);
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

    private static void paintLeads(Graphics2D graphics, ComponentType type, Rectangle2D box, ColorScheme scheme) {
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
                graphics.draw(new Line2D.Double(pinX, pinY, endX, endY));
            } else {
                PainterSupport.lead(graphics, pinX, pinY, endX, endY, scheme);
            }
        }
    }

    /** The ROM's band along the top edge, so it never reads as a RAM at a glance. */
    private static void paintBand(Graphics2D graphics, Rectangle2D box, ColorScheme scheme) {
        graphics.setColor(PainterSupport.withAlpha(scheme.componentStroke(), BAND_ALPHA));
        graphics.fill(new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), BAND_HEIGHT));
    }

    /** «RAM · 256 × 8». */
    private static String subtitle(ComponentType type, Map<String, Object> resolved) {
        return Messages.format("memory.subtitle", Messages.componentName(type.id()),
                Messages.choiceName(MemoryTypes.WORDS, String.valueOf(resolved.get(MemoryTypes.WORDS))),
                String.valueOf(resolved.get(MemoryTypes.WIDTH)));
    }

    /**
     * The title bar, upright about its centre: subtitle and name on the left; on the right the square, the light and,
     * left of the light, the RD / WR pill or «CS = 0» ({@code simulated}: a state from the simulation, not the palette).
     */
    private static void paintTitle(Graphics2D graphics, Rectangle2D titleBar, String subtitle, String name,
                                   boolean simulated, Activity activity, boolean folded, ColorScheme scheme) {
        Graphics2D upright = uprightAbout(graphics, titleBar.getCenterX(), titleBar.getCenterY());
        try {
            Rectangle2D square = toggleSquare(titleBar);
            paintToggle(upright, square, folded, scheme);
            double lightX = square.getX() - TEXT_GAP - LIGHT_RADIUS;
            double middleY = titleBar.getY() + TITLE_MIDDLE;
            paintLight(upright, lightX, middleY, activity, scheme);
            double textLeft = titleBar.getX() + TEXT_INSET;
            double statusRight = lightX - LIGHT_RADIUS - TEXT_GAP;
            double statusLeft = paintStatus(upright, statusRight, middleY, statusRight - textLeft - MINIMUM_NAME_ROOM,
                    simulated, activity, scheme);
            double available = statusLeft - TEXT_GAP - textLeft;
            drawText(upright, subtitle, textLeft, titleBar.getY() + 7.5, SUBTITLE_SIZE, available,
                    PainterSupport.ALIGN_LEFT, scheme.text());
            drawText(upright, name, textLeft, titleBar.getY() + 15, NAME_SIZE, available, PainterSupport.ALIGN_LEFT,
                    scheme.text());
        } finally {
            upright.dispose();
        }
    }

    /** Turned a quarter: subtitle, name and the pill or «CS = 0» centred in the body; the light in its corner. */
    private static void paintSidewaysTitle(Graphics2D graphics, Rectangle2D box, String subtitle, String name,
                                           Activity activity, ColorScheme scheme) {
        paintLight(graphics, box.getMaxX() - TOGGLE_FROM_RIGHT - LIGHT_RADIUS, box.getY() + TITLE_MIDDLE, activity,
                scheme);
        double centerX = box.getCenterX();
        double centerY = box.getCenterY();
        // upright about the centre the body is as wide on screen as it is tall here
        double across = box.getHeight() - 2 * TEXT_INSET;
        Graphics2D upright = uprightAbout(graphics, centerX, centerY);
        try {
            drawText(upright, subtitle, centerX, centerY - 9, SUBTITLE_SIZE, across, PainterSupport.ALIGN_CENTER,
                    scheme.text());
            drawText(upright, name, centerX, centerY + 1, NAME_SIZE, across, PainterSupport.ALIGN_CENTER,
                    scheme.text());
            statusText(true, activity).ifPresent(text -> {
                double width = textWidth(upright, text, BADGE_SIZE, Font.BOLD)
                        + (activity.color(scheme).isPresent() ? 2 * PILL_PADDING : 0);
                paintStatus(upright, centerX + width / 2, centerY + 11, width, true, activity, scheme);
            });
        } finally {
            upright.dispose();
        }
    }

    /** The square with ▾ (shown: a click folds) or ▸ (folded: a click shows the words). */
    private static void paintToggle(Graphics2D graphics, Rectangle2D square, boolean folded, ColorScheme scheme) {
        Shape frame = new RoundRectangle2D.Double(square.getX(), square.getY(), square.getWidth(), square.getHeight(),
                TOGGLE_ARC, TOGGLE_ARC);
        graphics.setColor(scheme.componentFill());
        graphics.fill(frame);
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(THIN_STROKE);
        graphics.draw(frame);
        double centerX = square.getCenterX();
        double centerY = square.getCenterY();
        Path2D glyph = new Path2D.Double();
        if (folded) {
            glyph.moveTo(centerX - GLYPH_HALF * 0.75, centerY - GLYPH_HALF);
            glyph.lineTo(centerX - GLYPH_HALF * 0.75, centerY + GLYPH_HALF);
            glyph.lineTo(centerX + GLYPH_HALF, centerY);
        } else {
            glyph.moveTo(centerX - GLYPH_HALF, centerY - GLYPH_HALF * 0.75);
            glyph.lineTo(centerX + GLYPH_HALF, centerY - GLYPH_HALF * 0.75);
            glyph.lineTo(centerX, centerY + GLYPH_HALF);
        }
        glyph.closePath();
        graphics.setColor(scheme.text());
        graphics.fill(glyph);
    }

    private static void paintLight(Graphics2D graphics, double centerX, double centerY, Activity activity,
                                   ColorScheme scheme) {
        Ellipse2D light = new Ellipse2D.Double(centerX - LIGHT_RADIUS, centerY - LIGHT_RADIUS, 2 * LIGHT_RADIUS,
                2 * LIGHT_RADIUS);
        Optional<Color> lit = activity.color(scheme);
        graphics.setColor(lit.orElse(scheme.componentShade()));
        graphics.fill(light);
        if (lit.isEmpty()) {
            // an unlit light is only its outline, as a lit one is only its colour (mockup)
            graphics.setColor(scheme.componentStroke());
            graphics.setStroke(THIN_STROKE);
            graphics.draw(light);
        }
    }

    /** «RD» / «WR» while selected and active, «CS = 0» when deselected, nothing otherwise or in the palette. */
    private static Optional<String> statusText(boolean simulated, Activity activity) {
        if (!simulated) {
            return Optional.empty();
        }
        if (!activity.enabled()) {
            return Optional.of(Messages.text("memory.badge.deselected"));
        }
        return switch (activity.access()) {
            case READ -> Optional.of(Messages.text("memory.badge.read"));
            case WRITE -> Optional.of(Messages.text("memory.badge.write"));
            case NONE -> Optional.empty();
        };
    }

    /**
     * Draws the pill (or «CS = 0») ending at {@code right} when it fits in {@code room}; answers where it starts
     * ({@code right} when nothing was drawn), so the name keeps the rest.
     */
    private static double paintStatus(Graphics2D graphics, double right, double centerY, double room,
                                      boolean simulated, Activity activity, ColorScheme scheme) {
        Optional<String> text = statusText(simulated, activity);
        if (text.isEmpty()) {
            return right;
        }
        Optional<Color> pill = activity.color(scheme);
        double width = textWidth(graphics, text.get(), BADGE_SIZE, Font.BOLD) + (pill.isPresent() ? 2 * PILL_PADDING : 0);
        if (width > room) {
            return right;
        }
        if (pill.isEmpty()) {
            drawText(graphics, text.get(), right, centerY, BADGE_SIZE, width, PainterSupport.ALIGN_RIGHT,
                    PainterSupport.withAlpha(scheme.text(), QUIET_ROW_ALPHA));
            return right - width;
        }
        Shape badge = new RoundRectangle2D.Double(right - width, centerY - PILL_HEIGHT / 2, width, PILL_HEIGHT,
                PILL_HEIGHT, PILL_HEIGHT);
        graphics.setColor(pill.get());
        graphics.fill(badge);
        PainterSupport.uprightText(graphics, text.get(), right - width / 2, centerY, BADGE_SIZE,
                contrasting(pill.get(), scheme), PainterSupport.ALIGN_CENTER,
                PainterSupport.BASE_FONT.deriveFont(Font.BOLD));
        return right - width;
    }

    /** The scheme's text or fill colour, whichever reads better on {@code background} (white on blue in Claro). */
    private static Color contrasting(Color background, ColorScheme scheme) {
        double backgroundLuminance = luminance(background);
        Color light = scheme.componentFill();
        Color dark = scheme.text();
        if (luminance(light) < luminance(dark)) {
            Color swapped = light;
            light = dark;
            dark = swapped;
        }
        double onLight = (luminance(light) + 0.05) / (backgroundLuminance + 0.05);
        double onDark = (backgroundLuminance + 0.05) / (luminance(dark) + 0.05);
        return onLight * LIGHT_TEXT_PREFERENCE >= onDark ? light : dark;
    }

    /** The relative luminance of {@code color} (WCAG). */
    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** The bordered window under the title bar, between the pin names, as tall as its rows. */
    private static Rectangle2D windowBox(Rectangle2D box, Map<String, Object> resolved) {
        double top = box.getY() + TITLE_HEIGHT + WINDOW_GAP;
        double room = box.getMaxY() - BOTTOM_ROOM - top - 2 * WINDOW_PADDING;
        long rows = Math.min(Math.min(WINDOW_ROWS, MemoryTypes.words(resolved)),
                Math.max(0, (long) Math.floor(room / ROW_HEIGHT)));
        return new Rectangle2D.Double(box.getX() + SIDE_ROOM, top, box.getWidth() - 2 * SIDE_ROOM,
                rows * ROW_HEIGHT + 2 * WINDOW_PADDING);
    }

    private static void paintWindow(Graphics2D graphics, Rectangle2D window, Map<String, Object> resolved,
                                     VisibleState.MemoryState memory, Decoded decoded, Activity activity,
                                     ColorScheme scheme) {
        int rows = (int) Math.round((window.getHeight() - 2 * WINDOW_PADDING) / ROW_HEIGHT);
        if (rows <= 0) {
            return;
        }
        Graphics2D upright = uprightAbout(graphics, window.getCenterX(), window.getCenterY());
        try {
            if (!activity.enabled()) {
                upright.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIMMED_ALPHA));
            }
            Shape frame = new RoundRectangle2D.Double(window.getX(), window.getY(), window.getWidth(),
                    window.getHeight(), WINDOW_ARC, WINDOW_ARC);
            upright.setColor(scheme.background());
            upright.fill(frame);
            upright.setColor(scheme.componentStroke());
            upright.setStroke(THIN_STROKE);
            upright.draw(frame);
            paintRows(upright, window, rows, resolved, memory, decoded, activity, scheme);
        } finally {
            upright.dispose();
        }
    }

    /** One «address  word» row per window row; the accessed one outlined and tinted. */
    private static void paintRows(Graphics2D graphics, Rectangle2D window, int rows, Map<String, Object> resolved,
                                  VisibleState.MemoryState memory, Decoded decoded, Activity activity,
                                  ColorScheme scheme) {
        int width = (Integer) resolved.get(MemoryTypes.WIDTH);
        int addressBits = MemoryTypes.addressBits(resolved);
        int valueDigits = Math.max(1, (width + 3) / 4);
        long first = memory == null ? 0 : memory.firstWindowAddress();
        long mask = width >= Long.SIZE ? -1L : (1L << width) - 1;
        String widest = addressText(0, addressBits) + COLUMN_GAP + "0".repeat(valueDigits);
        float size = fittedSize(graphics, widest, ROW_SIZE, window.getWidth() - 2 * ROW_TEXT_INSET, MONOSPACE);
        double addressLeft = window.getX() + ROW_TEXT_INSET;
        double valueLeft = addressLeft + textWidth(graphics, addressText(0, addressBits) + COLUMN_GAP, size,
                Font.PLAIN, MONOSPACE);
        Optional<Color> accessColor = activity.color(scheme);
        for (int row = 0; row < rows; row++) {
            long address = first + row;
            double centerY = window.getY() + WINDOW_PADDING + row * ROW_HEIGHT + ROW_HEIGHT / 2;
            boolean accessed = memory != null && accessColor.isPresent() && address == memory.lastAddress();
            if (accessed) {
                Shape box = new RoundRectangle2D.Double(window.getX() + WINDOW_PADDING, centerY - ROW_HEIGHT / 2,
                        window.getWidth() - 2 * WINDOW_PADDING, ROW_HEIGHT, WINDOW_ARC, WINDOW_ARC);
                graphics.setColor(PainterSupport.withAlpha(accessColor.get(), ACCESS_TINT_ALPHA));
                graphics.fill(box);
                graphics.setColor(accessColor.get());
                graphics.setStroke(THIN_STROKE);
                graphics.draw(box);
            }
            Color text = accessed ? scheme.text() : PainterSupport.withAlpha(scheme.text(), QUIET_ROW_ALPHA);
            boolean written = accessed && activity.access() == VisibleState.MemoryAccess.WRITE;
            PainterSupport.uprightText(graphics, addressText(address, addressBits), addressLeft, centerY, size, text,
                    PainterSupport.ALIGN_LEFT, MONOSPACE);
            PainterSupport.uprightText(graphics, wordAt(memory, decoded, row, address, mask, valueDigits), valueLeft,
                    centerY, size, written ? scheme.memoryWrite() : text, PainterSupport.ALIGN_LEFT,
                    written ? MONOSPACE_BOLD : MONOSPACE);
        }
    }

    /** The text of the word of window row {@code row}: from the simulation when there is one, else the contents. */
    private static String wordAt(VisibleState.MemoryState memory, Decoded decoded, int row, long address, long mask,
                                 int digits) {
        if (memory == null) {
            return wordText(decoded.contents().word(address) & mask, digits, false);
        }
        long value = row < memory.window().size() ? memory.window().get(row) : 0;
        return wordText(value, digits, memory.undefinedAt(row));
    }

    /** A word of the window in hex with {@code digits} digits, or «Z» for a word written undefined. */
    static String wordText(long value, int digits, boolean undefined) {
        return undefined ? UNDEFINED_WORD : hex(value, digits);
    }

    private static String hex(long value, int digits) {
        String text = Long.toHexString(value).toUpperCase(Locale.ROOT);
        return text.length() >= digits ? text : "0".repeat(digits - text.length()) + text;
    }

    private static void paintPinNames(Graphics2D graphics, ComponentType type, Rectangle2D box, ColorScheme scheme) {
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

    /**
     * A copy of {@code graphics} turned back about (centerX, centerY) so what is drawn there reads upright: upside down
     * a region is drawn as at R0 in the same place, its far ends swapped.
     */
    private static Graphics2D uprightAbout(Graphics2D graphics, double centerX, double centerY) {
        Graphics2D upright = (Graphics2D) graphics.create();
        upright.rotate(-PainterSupport.quarterTurns(graphics) * Math.PI / 2, centerX, centerY);
        return upright;
    }

    /** Upright text anchored at the point, shrunk to fit {@code available}. */
    private static void drawText(Graphics2D graphics, String text, double anchorX, double anchorY, float size,
                                 double available, double alignment, Color color) {
        PainterSupport.uprightText(graphics, text, anchorX, anchorY,
                fittedSize(graphics, text, size, available, PainterSupport.BASE_FONT), color, alignment);
    }

    /** {@code size}, or smaller (down to a minimum) so {@code text} fits {@code available}. */
    private static float fittedSize(Graphics2D graphics, String text, float size, double available, Font font) {
        double width = graphics.getFontMetrics(font.deriveFont(size)).getStringBounds(text, graphics).getWidth();
        if (width <= available || width == 0) {
            return size;
        }
        float fitted = (float) Math.max(MINIMUM_SIZE, size * available / width);
        while (fitted > MINIMUM_SIZE && graphics.getFontMetrics(font.deriveFont(fitted))
                .getStringBounds(text, graphics).getWidth() > available) {
            fitted = Math.max(MINIMUM_SIZE, fitted - 0.25f);
        }
        return fitted;
    }

    private static double textWidth(Graphics2D graphics, String text, float size, int style) {
        return textWidth(graphics, text, size, style, PainterSupport.BASE_FONT);
    }

    private static double textWidth(Graphics2D graphics, String text, float size, int style, Font font) {
        FontMetrics metrics = graphics.getFontMetrics(font.deriveFont(style, size));
        return metrics.getStringBounds(text, graphics).getWidth();
    }

    /** The address text of a window row: hex, as many digits as the address bits need («0F» for 256 words). */
    static String addressText(long address, int addressBits) {
        return hex(address, Math.max(1, (addressBits + 3) / 4));
    }
}
