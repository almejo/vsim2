package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongFunction;

/**
 * Draws the rows of a waveform panel with the time diagram's conventions: one bit as high and low lines joined by
 * edges, Z as a dashed middle line, a conflict as a red band; a bus as a hexagon-ended band per stable value with its
 * hex text when it fits; a gap (the net disappeared) and the time before a truncated history hatched. Nothing is
 * drawn after the current time or before a channel's first event. Lines sit on pixel centers, so an edge at time t
 * covers pixel column {@code floor(axis.xOf(t))}.
 */
public final class WaveformPainter {

    public static final int ROW_HEIGHT = 28;
    /** From the row top to the high level, and from the low level to the row bottom. */
    static final int LEVEL_MARGIN = 6;
    static final double BUS_SLANT = 3;
    private static final float TEXT_SIZE = 11f;
    private static final double HATCH_SPACING = 6;
    private static final BasicStroke LINE = new BasicStroke(1f);
    private static final BasicStroke HIGH_IMPEDANCE = new BasicStroke(1f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f);

    /** What a stretch of a row shows. */
    enum Level { LOW, HIGH, HIGH_IMPEDANCE, CONFLICT, BUS, GAP }

    private WaveformPainter() {
    }

    public static void paint(Graphics2D graphics, WaveformSnapshot snapshot, List<String> channelIds, TimeAxis axis,
                             int width, ColorScheme scheme) {
        paint(graphics, snapshot, channelIds, axis, width, scheme, Map.of());
    }

    /** As above; {@code formats} gives a channel id its bus format ({@link Probe#FORMAT_HEX} when absent). */
    public static void paint(Graphics2D graphics, WaveformSnapshot snapshot, List<String> channelIds, TimeAxis axis,
                             int width, ColorScheme scheme, Map<String, String> formats) {
        paint(graphics, snapshot, channelIds, axis, width, scheme, formats, Map.of());
    }

    /**
     * As above; {@code valueNames} gives a channel the names its values stand for (a machine's state row): such a row
     * is a band per value with its name, 1-bit too, and «—» while it is Z.
     */
    public static void paint(Graphics2D graphics, WaveformSnapshot snapshot, List<String> channelIds, TimeAxis axis,
                             int width, ColorScheme scheme, Map<String, String> formats,
                             Map<String, LongFunction<String>> valueNames) {
        paint(graphics, snapshot, channelIds, axis, width, scheme, formats, valueNames, Map.of());
    }

    /**
     * As above; {@code valueColors} gives a named row the colours the user gave its values (a machine's states, hito
     * 3): a band of a value with a colour is filled with the scheme's {@link ColorScheme#waveStateFill} shade of it,
     * read now, and its name written in {@link ColorScheme#waveStateFillText}.
     */
    public static void paint(Graphics2D graphics, WaveformSnapshot snapshot, List<String> channelIds, TimeAxis axis,
                             int width, ColorScheme scheme, Map<String, String> formats,
                             Map<String, LongFunction<String>> valueNames,
                             Map<String, LongFunction<Optional<StateColor>>> valueColors) {
        // Pure strokes: lines and the bus outlines land on the same pixel rows (normalization shifts paths by a row).
        Object strokeControl = graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        try {
            paintRows(graphics, snapshot, channelIds, axis, width, scheme, formats, valueNames, valueColors);
        } finally {
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    strokeControl == null ? RenderingHints.VALUE_STROKE_DEFAULT : strokeControl);
        }
    }

    private static void paintRows(Graphics2D graphics, WaveformSnapshot snapshot, List<String> channelIds,
                                  TimeAxis axis, int width, ColorScheme scheme, Map<String, String> formats,
                                  Map<String, LongFunction<String>> valueNames,
                                  Map<String, LongFunction<Optional<StateColor>>> valueColors) {
        int height = channelIds.size() * ROW_HEIGHT;
        graphics.setColor(scheme.diagramBackground());
        graphics.fill(new Rectangle2D.Double(0, 0, width, height));
        paintGrid(graphics, axis, height, scheme);
        for (int row = 0; row < channelIds.size(); row++) {
            Optional<Trace> trace = snapshot.trace(channelIds.get(row));
            if (trace.isPresent()) {
                paintRow(graphics, trace.get(), row * ROW_HEIGHT, axis, width, snapshot.time(), scheme,
                        formats.getOrDefault(channelIds.get(row), Probe.FORMAT_HEX),
                        valueNames.get(channelIds.get(row)), valueColors.get(channelIds.get(row)));
            }
        }
        OptionalLong cursor = axis.cursor();
        if (cursor.isPresent()) {
            double x = pixel(axis.xOf(cursor.getAsLong()));
            graphics.setColor(scheme.selection());
            graphics.setStroke(LINE);
            graphics.draw(new Line2D.Double(x, 0, x, height));
        }
    }

    static double highY(double top) {
        return top + LEVEL_MARGIN + 0.5;
    }

    static double lowY(double top) {
        return top + ROW_HEIGHT - LEVEL_MARGIN - 0.5;
    }

    static double middleY(double top) {
        return top + ROW_HEIGHT / 2 + 0.5;
    }

    /** The center of the pixel column {@code x} falls in, so a one-pixel line covers exactly that column. */
    static double pixel(double x) {
        return Math.floor(x) + 0.5;
    }

    static Level levelOf(Optional<Value> value) {
        if (value.isEmpty()) {
            return Level.GAP;
        }
        Value present = value.get();
        if (present.conflict()) {
            return Level.CONFLICT;
        }
        if (present.isFullyHighImpedance()) {
            return Level.HIGH_IMPEDANCE;
        }
        if (present.width() > 1) {
            return Level.BUS;
        }
        return present.bits() != 0 ? Level.HIGH : Level.LOW;
    }

    /** As {@link #levelOf(Optional)}; in a named row every value but a gap or a conflict is a band. */
    static Level levelOf(Optional<Value> value, LongFunction<String> names) {
        Level level = levelOf(value);
        return names != null && level != Level.GAP && level != Level.CONFLICT ? Level.BUS : level;
    }

    private static void paintGrid(Graphics2D graphics, TimeAxis axis, int height, ColorScheme scheme) {
        graphics.setColor(scheme.diagramGrid());
        graphics.setStroke(LINE);
        if (axis.showsUnitGrid()) {
            for (long unit = (long) Math.ceil(axis.startTime()); unit <= axis.endTime(); unit++) {
                double x = pixel(axis.xOf(unit));
                graphics.draw(new Line2D.Double(x, 0, x, height));
            }
        }
        for (long tick : axis.ticks()) {
            double x = pixel(axis.xOf(tick));
            graphics.draw(new Line2D.Double(x, 0, x, height));
        }
    }

    /**
     * Draws one row and returns how many positions it visited (for tests: at most about two per pixel column). Walks
     * the events by index; when the next event falls in the same pixel column, the column becomes a full-height bar
     * and the walk jumps to the first event of the next column, so the cost scales with the width of the plot.
     */
    static int paintRow(Graphics2D graphics, Trace trace, double top, TimeAxis axis, int width, long now,
                        ColorScheme scheme) {
        return paintRow(graphics, trace, top, axis, width, now, scheme, Probe.FORMAT_HEX);
    }

    static int paintRow(Graphics2D graphics, Trace trace, double top, TimeAxis axis, int width, long now,
                        ColorScheme scheme, String format) {
        return paintRow(graphics, trace, top, axis, width, now, scheme, format, null);
    }

    /** As above; a non-null {@code names} draws every value as a named band ({@link #paint} with value names). */
    static int paintRow(Graphics2D graphics, Trace trace, double top, TimeAxis axis, int width, long now,
                        ColorScheme scheme, String format, LongFunction<String> names) {
        return paintRow(graphics, trace, top, axis, width, now, scheme, format, names, null);
    }

    /** As above; a non-null {@code colours} fills the band of each value that has a colour with it. */
    static int paintRow(Graphics2D graphics, Trace trace, double top, TimeAxis axis, int width, long now,
                        ColorScheme scheme, String format, LongFunction<String> names,
                        LongFunction<Optional<StateColor>> colours) {
        OptionalLong first = trace.firstTime();
        if (first.isEmpty()) {
            return 0;
        }
        if (trace.truncated()) {
            double right = Math.min(width, axis.xOf(first.getAsLong()));
            if (right > 0) {
                hatch(graphics, 0, right, top, scheme);
            }
        }
        long from = Math.max(first.getAsLong(), (long) Math.floor(axis.startTime()));
        long to = Math.min(now, (long) Math.ceil(axis.endTime()));
        if (to < from) {
            return 0;
        }
        int eventCount = trace.eventCount();
        Optional<Value> value = trace.valueAt(from);
        long segmentStart = from;
        int visited = 0;
        int index = trace.firstIndexAtOrAfter(from + 1);
        while (index < eventCount && trace.eventTime(index) <= to) {
            visited++;
            long time = trace.eventTime(index);
            double right = axis.xOf(time);
            double column = Math.floor(right);
            long nextColumnTime = Math.max(time + 1, (long) Math.ceil(axis.timeAt(column + 1)));
            int next = index + 1;
            paintSegment(graphics, value, axis.xOf(segmentStart), right, top, scheme, format, names, colours);
            if (next < eventCount && trace.eventTime(next) < nextColumnTime && trace.eventTime(next) <= to) {
                graphics.setColor(scheme.diagramTrace());
                graphics.setStroke(LINE);
                graphics.draw(new Line2D.Double(pixel(right), highY(top), pixel(right), lowY(top)));
                int last = trace.firstIndexAtOrAfter(Math.min(nextColumnTime, to + 1)) - 1;
                value = trace.eventValue(last);
                segmentStart = trace.eventTime(last);
                index = last + 1;
            } else {
                Optional<Value> after = trace.eventValue(index);
                paintEdge(graphics, levelOf(value, names), levelOf(after, names), right, top, scheme);
                value = after;
                segmentStart = time;
                index = next;
            }
        }
        paintSegment(graphics, value, axis.xOf(segmentStart), axis.xOf(to), top, scheme, format, names,
                colours);
        return visited;
    }

    private static void paintSegment(Graphics2D graphics, Optional<Value> value, double left, double right,
                                     double top, ColorScheme scheme, String format,
                                     LongFunction<String> names, LongFunction<Optional<StateColor>> colours) {
        if (right <= left) {
            return;
        }
        graphics.setStroke(LINE);
        switch (levelOf(value, names)) {
            case HIGH, LOW -> {
                double y = levelOf(value) == Level.HIGH ? highY(top) : lowY(top);
                graphics.setColor(scheme.diagramTrace());
                graphics.draw(new Line2D.Double(left, y, right, y));
            }
            case HIGH_IMPEDANCE -> {
                graphics.setColor(scheme.diagramTrace());
                graphics.setStroke(HIGH_IMPEDANCE);
                graphics.draw(new Line2D.Double(left, middleY(top), right, middleY(top)));
            }
            case CONFLICT -> {
                graphics.setColor(scheme.wireConflict());
                graphics.fill(new Rectangle2D.Double(left, highY(top), right - left, lowY(top) - highY(top)));
                if (value.orElseThrow().width() > 1 || names != null) {
                    paintText(graphics, BusValueText.CONFLICT, left, right, top, scheme.diagramTrace());
                }
            }
            case BUS -> {
                Optional<StateColor> colour = colourOf(value, names, colours);
                Shape band = hexagon(left, right, top);
                colour.ifPresent(found -> {
                    graphics.setColor(scheme.waveStateFill(found));
                    graphics.fill(band);
                });
                graphics.setColor(scheme.diagramTrace());
                graphics.draw(band);
                String text = names != null ? ChannelRows.valueText(value, format, names)
                        : busText(value.orElseThrow(), format);
                paintText(graphics, text, left, right, top,
                        colour.map(scheme::waveStateFillText).orElseGet(scheme::diagramTrace));
            }
            case GAP -> hatch(graphics, left, right, top, scheme);
        }
    }

    /** The colour of a named band's value, when the row has colours and the value is a whole code. */
    private static Optional<StateColor> colourOf(Optional<Value> value, LongFunction<String> names,
                                                 LongFunction<Optional<StateColor>> colours) {
        if (names == null || colours == null || value.isEmpty() || !value.get().isFullyDefined()) {
            return Optional.empty();
        }
        return colours.apply(value.get().bits());
    }

    /** The text of a bus segment in a channel's {@code format}: {@code 0x66} or {@code 'f'}. */
    static String busText(Value value, String format) {
        return BusValueText.of(value, format);
    }

    /** A vertical line where one bit changes between levels (Z counts as the middle); buses pinch in their outline. */
    private static void paintEdge(Graphics2D graphics, Level before, Level after, double x, double top,
                                  ColorScheme scheme) {
        if (before == after || !isLine(before) || !isLine(after)) {
            return;
        }
        graphics.setColor(scheme.diagramTrace());
        graphics.setStroke(LINE);
        graphics.draw(new Line2D.Double(pixel(x), heightOf(before, top), pixel(x), heightOf(after, top)));
    }

    private static boolean isLine(Level level) {
        return level == Level.HIGH || level == Level.LOW || level == Level.HIGH_IMPEDANCE;
    }

    private static double heightOf(Level level, double top) {
        return switch (level) {
            case HIGH -> highY(top);
            case LOW -> lowY(top);
            default -> middleY(top);
        };
    }

    private static Shape hexagon(double left, double right, double top) {
        double slant = Math.min(BUS_SLANT, (right - left) / 2);
        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(left, middleY(top));
        shape.lineTo(left + slant, highY(top));
        shape.lineTo(right - slant, highY(top));
        shape.lineTo(right, middleY(top));
        shape.lineTo(right - slant, lowY(top));
        shape.lineTo(left + slant, lowY(top));
        shape.closePath();
        return shape;
    }

    /** {@code text} centered in the band, only when it fits between the slants. */
    private static void paintText(Graphics2D graphics, String text, double left, double right, double top,
                                  Color color) {
        Font font = graphics.getFont().deriveFont(TEXT_SIZE);
        FontMetrics metrics = graphics.getFontMetrics(font);
        int textWidth = metrics.stringWidth(text);
        if (textWidth + 2 * BUS_SLANT + 2 > right - left) {
            return;
        }
        graphics.setFont(font);
        graphics.setColor(color);
        float x = (float) ((left + right - textWidth) / 2);
        float baseline = (float) (middleY(top) + (metrics.getAscent() - metrics.getDescent()) / 2.0);
        graphics.drawString(text, x, baseline);
    }

    private static void hatch(Graphics2D graphics, double left, double right, double top, ColorScheme scheme) {
        Shape previousClip = graphics.getClip();
        double bandTop = highY(top);
        double bandBottom = lowY(top);
        double height = bandBottom - bandTop;
        graphics.clip(new Rectangle2D.Double(left, bandTop - 0.5, right - left, height + 1));
        graphics.setColor(scheme.diagramGrid());
        graphics.setStroke(LINE);
        double start = Math.floor((left - height) / HATCH_SPACING) * HATCH_SPACING;
        for (double x = start; x < right; x += HATCH_SPACING) {
            graphics.draw(new Line2D.Double(x, bandBottom, x + height, bandTop));
        }
        graphics.setClip(previousClip);
    }
}
