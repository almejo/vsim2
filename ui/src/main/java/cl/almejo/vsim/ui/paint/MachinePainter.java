package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineState;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The simple face of a state machine instance (spec §2.4, hito 1): the default box (name on top, the pins' names) with
 * the current state's name in the middle, in the scheme's orange, and «S0 → S1» (current → next) below. A drawn
 * appearance still wins ({@link SubcircuitPainter} decides). As a {@link ComponentPainter} it draws the palette's
 * icon: a box with two states and an arrow.
 */
public final class MachinePainter implements ComponentPainter {

    private static final float STATE_SIZE = 8f;
    private static final float NEXT_SIZE = 6f;
    private static final float MINIMUM_SIZE = 4f;
    private static final String ARROW = " → ";

    /** The palette icon: the type has no definition, so it is a box with two circles joined by an arrow. */
    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        PainterSupport.body(graphics, new RoundRectangle2D.Double(1, 1, width - 2, height - 2, 4, 4), scheme);
        double radius = height * 0.2;
        double centerY = height / 2;
        Ellipse2D first = new Ellipse2D.Double(width * 0.2 - radius, centerY - radius, 2 * radius, 2 * radius);
        Ellipse2D second = new Ellipse2D.Double(width * 0.8 - radius, centerY - radius, 2 * radius, 2 * radius);
        Graphics2D lines = (Graphics2D) graphics.create();
        try {
            lines.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            lines.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            lines.setColor(scheme.componentStroke());
            lines.draw(first);
            lines.draw(second);
            lines.draw(new Line2D.Double(first.getMaxX(), centerY, second.getMinX(), centerY));
            lines.draw(new Line2D.Double(second.getMinX() - 3, centerY - 3, second.getMinX(), centerY));
            lines.draw(new Line2D.Double(second.getMinX() - 3, centerY + 3, second.getMinX(), centerY));
            lines.setColor(scheme.memoryWrite());
            lines.fill(new Ellipse2D.Double(first.getCenterX() - radius / 2, centerY - radius / 2, radius, radius));
        } finally {
            lines.dispose();
        }
    }

    /**
     * The face of an instance of {@code definition}'s machine: the default box and, when the machine holds a state
     * ({@code state} is a machine's), its name in orange in the middle and «current → next» below.
     */
    static void paintFace(Graphics2D graphics, ComponentType type, ComponentDefinition definition,
                          VisibleState state, ColorScheme scheme) {
        SubcircuitPainter.paintDefaultBox(graphics, type, scheme, PainterSupport.STROKE.getLineWidth(), 1);
        if (!(state instanceof VisibleState.MachineVisibleState current)) {
            return;
        }
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        Rectangle2D box = new Rectangle2D.Double(1, 1, width - 2, height - 2);
        double available = width / 2;
        float stateSize = fitted(graphics, current.stateName(), STATE_SIZE, available);
        PainterSupport.centeredText(graphics, box, current.stateName(), width / 2, height / 2, stateSize,
                scheme.memoryWrite());
        Optional<String> nextName = current.nextStateId().map(nextId -> nameOf(definition, nextId));
        if (nextName.isPresent()) {
            String text = current.stateName() + ARROW + nextName.get();
            float nextSize = fitted(graphics, text, NEXT_SIZE, available);
            PainterSupport.centeredText(graphics, box, text, width / 2, height / 2 + stateSize + 1, nextSize,
                    scheme.text());
        }
    }

    private static String nameOf(ComponentDefinition definition, String stateId) {
        return definition.machine().flatMap(machine -> machine.state(stateId)).map(MachineState::name)
                .orElse(stateId);
    }

    private static float fitted(Graphics2D graphics, String text, float preferred, double available) {
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(preferred));
        double textWidth = metrics.stringWidth(text);
        return textWidth <= available ? preferred
                : (float) Math.max(MINIMUM_SIZE, preferred * available / textWidth);
    }
}
