package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineConditions;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Draws a machine's diagram in diagram units (the caller has applied the view's zoom and pan): the arrows with their
 * conditions and Mealy outputs, the states with their names and Moore outputs, the initial mark, the active state and
 * the next arrow in the active colour, the selection dashed (the agreed mockup's look), a condition that cannot be
 * read in the problem colour, and what a gesture shows while it lasts ({@link Overlay}). Colours come from
 * {@link MachineColors} at each paint.
 */
final class MachineDiagramPainter {

    private static final float ARROW_WIDTH = 1.8f;
    private static final float NEXT_ARROW_WIDTH = 3.2f;
    private static final float STATE_WIDTH = 2f;
    private static final float ACTIVE_STATE_WIDTH = 3.5f;
    private static final float[] SELECTION_DASH = {6f, 4f};
    private static final double HEAD_LENGTH = 11;
    private static final double HEAD_HALF_WIDTH = 5;
    private static final int NAME_SIZE = 19;
    private static final int OUTPUT_SIZE = 12;
    private static final int LABEL_SIZE = 13;
    /** How far below the centre a state's Moore outputs go. */
    private static final double OUTPUTS_BELOW = 20;
    private static final double INITIAL_LABEL_GAP = 8;

    /** How wide a lit border (the pointer near it: a drag from there makes an arrow) and a drawn arrow are. */
    private static final float LIT_BORDER_WIDTH = 4f;
    /** How opaque the rubber band's inside is (0..255). */
    private static final int RUBBER_BAND_ALPHA = 40;

    /** What to light and mark on top of the model. */
    record Marks(Optional<ActiveMachineState> active, MachineSelection selection) {
    }

    /**
     * What a gesture shows while it lasts, on top of the diagram: the state whose border is lit, the arrow being drawn
     * (from a state's border to the pointer) and the rubber band, all in diagram units.
     */
    record Overlay(Optional<String> litStateId, Optional<Line2D> pendingArrow, Optional<Rectangle2D> rubberBand) {

        static final Overlay NONE = new Overlay(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** The font of a state's name, at 100 %. */
    static Font nameFont() {
        return new Font(Font.SANS_SERIF, Font.BOLD, NAME_SIZE);
    }

    /** The font of an arrow's label, at 100 %. */
    static Font labelFont() {
        return new Font(Font.MONOSPACED, Font.BOLD, LABEL_SIZE);
    }

    /** Where the label of {@code transition} is drawn, measured with {@code metrics} of {@link #labelFont()}. */
    static Rectangle2D labelBounds(MachineTransition transition, MachineDiagramGeometry.Arrow arrow,
                                   FontMetrics metrics) {
        return textBounds(labelOf(transition), arrow.labelAnchor(), metrics);
    }

    /** Where {@code text} centred on {@code centre} goes (at least a few characters wide, so an empty one is found). */
    static Rectangle2D textBounds(String text, Point2D centre, FontMetrics metrics) {
        double width = Math.max(metrics.stringWidth(text), metrics.stringWidth("xxx"));
        double height = metrics.getHeight();
        return new Rectangle2D.Double(centre.getX() - width / 2, centre.getY() - height / 2, width, height);
    }

    /** Draws {@code overlay} over a diagram painted with {@code geometry}. */
    static void paintOverlay(Graphics2D graphics, MachineDiagramGeometry geometry, Overlay overlay,
                             ColorScheme scheme) {
        Color highlight = MachineColors.selection(scheme);
        graphics.setColor(highlight);
        graphics.setStroke(new BasicStroke(LIT_BORDER_WIDTH));
        overlay.litStateId().ifPresent(stateId -> graphics.draw(geometry.circle(stateId)));
        overlay.pendingArrow().ifPresent(line -> {
            graphics.setStroke(new BasicStroke(NEXT_ARROW_WIDTH));
            graphics.draw(line);
            paintHead(graphics, line.getP1(), line.getP2());
        });
        overlay.rubberBand().ifPresent(band -> {
            graphics.setColor(new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(),
                    RUBBER_BAND_ALPHA));
            graphics.fill(band);
            graphics.setColor(highlight);
            graphics.setStroke(dashed(1f));
            graphics.draw(band);
        });
    }

    private MachineDiagramPainter() {
    }

    static void paint(Graphics2D graphics, StateMachine machine, MachineDiagramGeometry geometry, Marks marks,
                      ColorScheme scheme) {
        Optional<String> nextTransition = marks.active().flatMap(ActiveMachineState::nextTransitionId);
        for (MachineTransition transition : machine.transitions()) {
            boolean next = nextTransition.filter(transition.id()::equals).isPresent();
            paintArrow(graphics, transition, geometry.arrow(transition.id()), next,
                    marks.selection().containsTransition(transition.id()), scheme);
        }
        geometry.initialMark().ifPresent(mark -> paintInitialMark(graphics, mark, scheme));
        Optional<String> activeState = marks.active().map(ActiveMachineState::stateId);
        for (MachineState state : machine.states()) {
            paintState(graphics, state, geometry, activeState.filter(state.id()::equals).isPresent(),
                    marks.selection().containsState(state.id()), scheme);
        }
    }

    private static void paintArrow(Graphics2D graphics, MachineTransition transition,
                                   MachineDiagramGeometry.Arrow arrow, boolean next, boolean selected,
                                   ColorScheme scheme) {
        Color colour = next ? MachineColors.active(scheme)
                : selected ? MachineColors.selection(scheme) : MachineColors.muted(scheme);
        float width = next ? NEXT_ARROW_WIDTH : ARROW_WIDTH;
        graphics.setColor(colour);
        graphics.setStroke(selected && !next ? dashed(width) : new BasicStroke(width));
        graphics.draw(arrow.path());
        graphics.setStroke(new BasicStroke(width));
        paintHead(graphics, arrow.headFrom(), arrow.end());
        String label = labelOf(transition);
        if (!label.isEmpty()) {
            // a condition that cannot be read stays as written, in the problem colour
            boolean unreadable = MachineConditions.parse(transition.condition()).problem().isPresent();
            graphics.setColor(unreadable ? MachineColors.problem(scheme)
                    : next ? MachineColors.active(scheme) : MachineColors.muted(scheme));
            graphics.setFont(labelFont());
            drawCentred(graphics, label, arrow.labelAnchor());
        }
    }

    /** «x», «x / Z=1», «/ Z=1» (otherwise, with a Mealy output) or nothing (otherwise alone). */
    static String labelOf(MachineTransition transition) {
        if (transition.outputs().isEmpty()) {
            return transition.condition();
        }
        String outputs = "/ " + assignments(transition.outputs());
        return transition.isOtherwise() ? outputs : transition.condition() + " " + outputs;
    }

    private static String assignments(List<OutputAssignment> outputs) {
        return outputs.stream().map(output -> output.output() + "=" + (output.value() ? 1 : 0))
                .collect(Collectors.joining(" "));
    }

    private static void paintHead(Graphics2D graphics, Point2D from, Point2D tip) {
        double length = from.distance(tip);
        if (length == 0) {
            return;
        }
        double unitX = (tip.getX() - from.getX()) / length;
        double unitY = (tip.getY() - from.getY()) / length;
        double baseX = tip.getX() - unitX * HEAD_LENGTH;
        double baseY = tip.getY() - unitY * HEAD_LENGTH;
        Path2D head = new Path2D.Double();
        head.moveTo(tip.getX(), tip.getY());
        head.lineTo(baseX - unitY * HEAD_HALF_WIDTH, baseY + unitX * HEAD_HALF_WIDTH);
        head.lineTo(baseX + unitY * HEAD_HALF_WIDTH, baseY - unitX * HEAD_HALF_WIDTH);
        head.closePath();
        graphics.fill(head);
    }

    private static void paintInitialMark(Graphics2D graphics, Line2D mark, ColorScheme scheme) {
        graphics.setColor(MachineColors.muted(scheme));
        graphics.setStroke(new BasicStroke(ARROW_WIDTH));
        graphics.draw(mark);
        paintHead(graphics, mark.getP1(), mark.getP2());
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, LABEL_SIZE));
        Point2D start = mark.getP1();
        graphics.drawString(Messages.text("machine.initialMark"), (float) start.getX(),
                (float) (start.getY() - INITIAL_LABEL_GAP));
    }

    private static void paintState(Graphics2D graphics, MachineState state, MachineDiagramGeometry geometry,
                                   boolean active, boolean selected, ColorScheme scheme) {
        graphics.setColor(active ? MachineColors.activeFill(scheme) : MachineColors.stateFill(scheme));
        graphics.fill(geometry.circle(state.id()));
        float width = active ? ACTIVE_STATE_WIDTH : STATE_WIDTH;
        graphics.setColor(active ? MachineColors.active(scheme)
                : selected ? MachineColors.selection(scheme) : MachineColors.stateOutline(scheme));
        graphics.setStroke(selected ? dashed(width) : new BasicStroke(width));
        graphics.draw(geometry.circle(state.id()));
        if (selected && active) {
            graphics.setColor(MachineColors.selection(scheme));
            graphics.setStroke(dashed(STATE_WIDTH));
            graphics.draw(geometry.circle(state.id()));
        }
        Point2D centre = geometry.centre(state.id());
        graphics.setColor(MachineColors.text(scheme));
        graphics.setFont(nameFont());
        drawCentred(graphics, state.name(), centre);
        if (!state.outputs().isEmpty()) {
            graphics.setColor(MachineColors.muted(scheme));
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, OUTPUT_SIZE));
            drawCentred(graphics, assignments(state.outputs()),
                    new Point2D.Double(centre.getX(), centre.getY() + OUTPUTS_BELOW));
        }
    }

    private static Stroke dashed(float width) {
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, SELECTION_DASH, 0f);
    }

    /** Draws {@code text} centred on {@code centre}, both ways. */
    private static void drawCentred(Graphics2D graphics, String text, Point2D centre) {
        FontMetrics metrics = graphics.getFontMetrics();
        float x = (float) (centre.getX() - metrics.stringWidth(text) / 2.0);
        float y = (float) (centre.getY() + (metrics.getAscent() - metrics.getDescent()) / 2.0);
        graphics.drawString(text, x, y);
    }
}
