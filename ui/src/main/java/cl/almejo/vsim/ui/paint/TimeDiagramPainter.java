package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.TimeDiagramSample;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * A small scope screen that draws its own traces: one per input, its name at the left, the newest sample at the right
 * edge, one sample per pixel and a faint grid line every {@link #GRID_EVERY} samples. Z is a dashed middle line and a
 * conflict a red band. Sampling is periodic ({@code sample-interval}), so pulses shorter than the interval never show.
 */
public final class TimeDiagramPainter implements ComponentPainter {

    static final double SCREEN_LEFT = 4;
    static final double SCREEN_OVERHANG = 5;
    static final double NAME_COLUMN = 9;
    static final double RIGHT_PADDING = 2;
    static final double PITCH = 1;
    static final double SWING = 3;
    static final int GRID_EVERY = 10;
    private static final double GRID_INSET = 2;
    private static final float NAME_SIZE = 4f;
    private static final int NAME_ALPHA = 190;
    private static final int DEFAULT_SAMPLE_INTERVAL = 150;
    private static final BasicStroke TRACE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    private static final BasicStroke HIGH_IMPEDANCE = new BasicStroke(1f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[]{1.5f, 1.5f}, 0f);
    private static final BasicStroke EDGE = new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
    private static final BasicStroke GRID = new BasicStroke(0.5f);

    /** What a channel shows during one sample. */
    enum Level { LOW, HIGH, HIGH_IMPEDANCE, CONFLICT }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        for (PinSpec pin : type.pins()) {
            double pinY = pin.position().y() * UNIT;
            PainterSupport.lead(graphics, pin.position().x() * UNIT, pinY, SCREEN_LEFT, pinY, scheme);
        }
        PainterSupport.body(graphics, new RoundRectangle2D.Double(SCREEN_LEFT, -SCREEN_OVERHANG, width - SCREEN_LEFT,
                height + 2 * SCREEN_OVERHANG, 4, 4), scheme, scheme.diagramBackground());
        List<TimeDiagramSample> visible = visibleSamples(type, state);
        paintGrid(graphics, type, visible, sampleInterval(type, parameters), scheme);
        for (int channel = 0; channel < type.pinCount(); channel++) {
            paintChannel(graphics, type, channel, visible, scheme);
            PainterSupport.centeredText(graphics, type.pin(channel).name(), SCREEN_LEFT + NAME_COLUMN / 2,
                    channelCenter(type, channel), NAME_SIZE, PainterSupport.withAlpha(scheme.diagramTrace(), NAME_ALPHA));
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    static double plotLeft() {
        return SCREEN_LEFT + NAME_COLUMN;
    }

    static double plotRight(ComponentType type) {
        return type.size().width() * UNIT - RIGHT_PADDING;
    }

    /** How many samples fit between the names and the right edge. */
    static int capacity(ComponentType type) {
        return (int) Math.floor((plotRight(type) - plotLeft()) / PITCH);
    }

    /** The newest samples that fit, oldest first; none without a diagram state. */
    static List<TimeDiagramSample> visibleSamples(ComponentType type, VisibleState state) {
        if (!(state instanceof VisibleState.TimeDiagramState diagram)) {
            return List.of();
        }
        List<TimeDiagramSample> samples = diagram.samples();
        return samples.subList(Math.max(0, samples.size() - capacity(type)), samples.size());
    }

    /** Left edge of visible sample {@code index} out of {@code count}; the newest one ends at the right edge. */
    static double sampleLeft(ComponentType type, int index, int count) {
        return plotRight(type) - (count - index) * PITCH;
    }

    /** The trace of {@code channel} swings around the height of its pin. */
    static double channelCenter(ComponentType type, int channel) {
        return type.pin(channel).position().y() * UNIT;
    }

    /** The level of {@code channel} in {@code values}; a missing value reads as Z. */
    static Level levelOf(List<Value> values, int channel) {
        if (channel >= values.size()) {
            return Level.HIGH_IMPEDANCE;
        }
        Value value = values.get(channel);
        if (value.conflict()) {
            return Level.CONFLICT;
        }
        return switch (value.bit(0)) {
            case ONE -> Level.HIGH;
            case ZERO -> Level.LOW;
            case HIGH_IMPEDANCE -> Level.HIGH_IMPEDANCE;
        };
    }

    private static double heightOf(Level level, double center) {
        return switch (level) {
            case HIGH -> center - SWING;
            case LOW -> center + SWING;
            case HIGH_IMPEDANCE, CONFLICT -> center;
        };
    }

    private static int sampleInterval(ComponentType type, Map<String, Object> parameters) {
        if (parameters.get("sample-interval") instanceof Integer interval && interval > 0) {
            return interval;
        }
        return type.parameters().stream()
                .filter(parameter -> parameter.name().equals("sample-interval"))
                .map(ParameterSpec::defaultValue)
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .findFirst()
                .orElse(DEFAULT_SAMPLE_INTERVAL);
    }

    /** A vertical line at every sample whose number (time / interval) is a multiple of {@link #GRID_EVERY}. */
    private static void paintGrid(Graphics2D graphics, ComponentType type, List<TimeDiagramSample> visible,
                                  int interval, ColorScheme scheme) {
        double top = -SCREEN_OVERHANG + GRID_INSET;
        double bottom = type.size().height() * UNIT + SCREEN_OVERHANG - GRID_INSET;
        graphics.setColor(scheme.diagramGrid());
        graphics.setStroke(GRID);
        for (int index = 0; index < visible.size(); index++) {
            long sampleNumber = Math.floorDiv(visible.get(index).time(), (long) interval);
            if (Math.floorMod(sampleNumber, (long) GRID_EVERY) == 0) {
                double x = sampleLeft(type, index, visible.size());
                graphics.draw(new Line2D.Double(x, top, x, bottom));
            }
        }
    }

    /** Draws runs of equal levels, joined by vertical edges. */
    private static void paintChannel(Graphics2D graphics, ComponentType type, int channel,
                                     List<TimeDiagramSample> visible, ColorScheme scheme) {
        int count = visible.size();
        double center = channelCenter(type, channel);
        int runStart = 0;
        for (int index = 1; index <= count; index++) {
            Level runLevel = levelOf(visible.get(runStart).values(), channel);
            if (index < count && levelOf(visible.get(index).values(), channel) == runLevel) {
                continue;
            }
            double runLeft = sampleLeft(type, runStart, count);
            double runRight = sampleLeft(type, index, count);
            paintRun(graphics, runLevel, runLeft, runRight, center, scheme);
            if (index < count) {
                Level next = levelOf(visible.get(index).values(), channel);
                if (runLevel != Level.CONFLICT && next != Level.CONFLICT) {
                    graphics.setColor(scheme.diagramTrace());
                    graphics.setStroke(EDGE);
                    graphics.draw(new Line2D.Double(runRight, heightOf(runLevel, center), runRight,
                            heightOf(next, center)));
                }
            }
            runStart = index;
        }
    }

    private static void paintRun(Graphics2D graphics, Level level, double left, double right, double center,
                                 ColorScheme scheme) {
        switch (level) {
            case CONFLICT -> {
                graphics.setColor(scheme.wireConflict());
                graphics.fill(new Rectangle2D.Double(left, center - SWING, right - left, 2 * SWING));
            }
            case HIGH_IMPEDANCE -> {
                graphics.setColor(scheme.diagramTrace());
                graphics.setStroke(HIGH_IMPEDANCE);
                graphics.draw(new Line2D.Double(left, center, right, center));
            }
            case HIGH, LOW -> {
                graphics.setColor(scheme.diagramTrace());
                graphics.setStroke(TRACE);
                double y = heightOf(level, center);
                graphics.draw(new Line2D.Double(left, y, right, y));
            }
        }
    }
}
