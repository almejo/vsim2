package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * {@code bus-splitter} and {@code bus-joiner}: a thick spine one unit in from the bus pin's side, the bus lead along
 * the top, and a lead per pin (the first one at the top) labelled with its bits. The joiner is the same drawing with
 * the bus on the right.
 *
 * <p>With one bit per pin (the part of before groups) each lead is thin, one unit long and carries its bit number
 * centred over it. When some pin carries a group of bits, core spaces the pins two units apart and makes the leads
 * long enough for the longest label: each label ({@code 0-3}, {@code 7-11,25-31}) is written whole above its lead,
 * clear of the spine and of the pin, along the lead (bottom to top on a part turned sideways), and a group's lead is
 * drawn thick like a bus.
 */
public final class BusSplitterPainter implements ComponentPainter {

    private static final BasicStroke BUS_STROKE = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    /** The font size of the pin labels, small enough for two digits over a one-unit lead. */
    static final float LABEL_SIZE = 5f;
    private static final double LABEL_RISE = 2.5;
    /** A grouped label's middle above its lead: clear of a thick lead below and of the previous pin's lead above. */
    private static final double GROUP_LABEL_RISE = 5.5;
    /** The air between the spine (half its stroke and a bit) and a grouped label. */
    private static final double SPINE_GAP = 3;
    /** The air between a grouped label and its pin's square. */
    private static final double PIN_GAP = 2;
    /** The room of a one-bit label on a part turned sideways: the distance to the next pin's label, less a pixel. */
    static final double SIDEWAYS_LABEL_ROOM = UNIT - 1;
    /** How many groupings the labels are remembered for before starting over: far more than a circuit has. */
    private static final int MOST_REMEMBERED_GROUPINGS = 256;
    /** Painting may run off the EDT (exporting an image), so the remembered labels are shared safely. */
    private static final Map<LabelsKey, List<String>> REMEMBERED_LABELS = new ConcurrentHashMap<>();

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        PinSpec bus = type.pin(0);
        double spineX = spineX(type);
        double bottom = type.pin(type.pinCount() - 1).position().y() * UNIT;
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(BUS_STROKE);
        graphics.draw(new Line2D.Double(bus.position().x() * UNIT, 0, spineX, 0));
        graphics.draw(new Line2D.Double(spineX, 0, spineX, bottom));
        List<String> labels = labels(parameters);
        boolean grouped = grouped(type);
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(LABEL_SIZE));
        boolean sideways = PainterSupport.turnedSideways(graphics);
        for (int index = 1; index < type.pinCount(); index++) {
            PinSpec pin = type.pin(index);
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            PainterSupport.pinLead(graphics, pin, spineX, pinY, pinX, pinY, scheme);
            String label = index - 1 < labels.size() ? labels.get(index - 1) : String.valueOf(index - 1);
            if (grouped) {
                PainterSupport.textAlong(graphics, label, labelBox(spineX, pinX, pinY), LABEL_SIZE, scheme.text());
            } else {
                String shown = sideways ? PainterSupport.ellipsized(label, metrics, SIDEWAYS_LABEL_ROOM) : label;
                PainterSupport.uprightText(graphics, shown, (spineX + pinX) / 2, pinY - LABEL_RISE, LABEL_SIZE,
                        scheme.text(), PainterSupport.ALIGN_CENTER);
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The spine's x: one unit in from the bus pin (at the left of a splitter, at the right of a joiner). */
    private static double spineX(ComponentType type) {
        int busX = type.pin(0).position().x();
        return (busX == 0 ? 1 : busX - 1) * UNIT;
    }

    /** True when some pin after the bus carries several bits: the part is laid out for whole labels. */
    private static boolean grouped(ComponentType type) {
        return IntStream.range(1, type.pinCount()).anyMatch(index -> type.pin(index).width() > 1);
    }

    /** The band above a grouped pin's lead where its label goes, clear of the spine and of the pin. */
    private static Rectangle2D labelBox(double spineX, double pinX, double pinY) {
        double left = Math.min(spineX, pinX) + (pinX > spineX ? SPINE_GAP : PIN_GAP);
        double right = Math.max(spineX, pinX) - (pinX > spineX ? PIN_GAP : SPINE_GAP);
        return new Rectangle2D.Double(left, pinY - GROUP_LABEL_RISE - LABEL_SIZE / 2, right - left, LABEL_SIZE);
    }

    /** The length along the lead a grouped label has without shrinking: the lead less the air at both ends. */
    static double labelRoom(ComponentType type) {
        double spineX = spineX(type);
        PinSpec first = type.pin(1);
        return labelBox(spineX, first.position().x() * UNIT, first.position().y() * UNIT).getWidth();
    }

    /**
     * The label of each pin after the bus, in pin order: its bit, or its group's ranges. They are worked out once per
     * width and groups and remembered (every part on the board is painted on every frame, and a CPU has dozens of
     * splitters with the same few groupings).
     */
    static List<String> labels(Map<String, Object> parameters) {
        Object groups = parameters.get(StandardComponentTypes.GROUPS);
        if (!(parameters.get(StandardComponentTypes.WIDTH) instanceof Integer width) || groups == null
                || width < 1 || width > BitGroups.MAXIMUM_WIDTH) {
            return List.of();
        }
        LabelsKey key = new LabelsKey(width, groups.toString());
        List<String> remembered = REMEMBERED_LABELS.get(key);
        if (remembered != null) {
            return remembered;
        }
        if (REMEMBERED_LABELS.size() >= MOST_REMEMBERED_GROUPINGS) {
            REMEMBERED_LABELS.clear();
        }
        List<String> labels = labelsOf(key);
        List<String> earlier = REMEMBERED_LABELS.putIfAbsent(key, labels);
        return earlier == null ? labels : earlier;
    }

    private static List<String> labelsOf(LabelsKey key) {
        if (BitGroups.problem(key.groups(), key.width()).isPresent()) {
            return List.of();
        }
        BitGroups bitGroups = BitGroups.parse(key.groups(), key.width());
        return IntStream.range(0, bitGroups.pinCount()).mapToObj(bitGroups::label).toList();
    }

    /** A bus part's width and groups text, the key of its remembered labels. */
    private record LabelsKey(int width, String groups) {
    }
}
