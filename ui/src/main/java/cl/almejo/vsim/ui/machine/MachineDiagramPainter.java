package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineConditions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Draws a machine's diagram in diagram units (the caller has applied the view's zoom and pan): the arrows with their
 * conditions and Mealy outputs, the states with their names, Moore outputs and colours, the initial mark (an arrow
 * from a filled dot), the active state and the next arrow in the active colour, the selection dashed (the agreed
 * mockup's look), a condition that cannot be read in the problem colour, the problems' glows ({@link MachineGlows}: a
 * wide translucent stroke under an arrow, a halo around a state, red for an error and gold for a warning, each with
 * an «!» badge) and what a
 * gesture shows while it lasts ({@link Overlay}). Colours come from {@link MachineColors} at each paint.
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
    private static final double INITIAL_LABEL_GAP = 8;

    /** How wide a lit border (the pointer near it: a drag from there makes an arrow) and a drawn arrow are. */
    private static final float LIT_BORDER_WIDTH = 4f;
    /** How opaque the rubber band's inside is (0..255). */
    private static final int RUBBER_BAND_ALPHA = 40;
    /** How wide the glow under a problem arrow is (the mockup's 7 around a 2 wide arrow, at its state size). */
    private static final float ARROW_GLOW_WIDTH = 10f;
    /**
     * How opaque the glow under an arrow is: stronger than the mockup's 0.25, so an amber warning reads as amber on
     * the dark board too (hand test of hito 2: it looked brown).
     */
    private static final int ARROW_GLOW_ALPHA = 110;
    /** How far a problem state's halo reaches past its circle (the mockup's 6 at radius 28). */
    private static final double STATE_HALO = 8;
    /** How opaque a state's halo is: stronger than the mockup's 0.18, for the same reason as the arrows'. */
    private static final int STATE_HALO_ALPHA = 115;
    /**
     * How far the selection's dashes reach on each side of the next arrow's orange when that arrow is selected too,
     * so the selection stays visible over it (hand test of hito 2).
     */
    private static final float SELECTION_BESIDE = 3f;

    /**
     * The radius of the «!» badge a problem state wears on its rim and a problem arrow beside its label, so a warning
     * or an error never reads by colour alone (hand test of hito 2, round 2).
     */
    static final double BADGE_RADIUS = 8;
    /** The room between an arrow's label and its badge. */
    private static final double BADGE_GAP = 4;
    /** The size of the badge's «!». */
    private static final int BADGE_MARK_SIZE = 12;

    /** How much of its strength a glow keeps while another problem is being shown, so that one stands out. */
    private static final int FADED_GLOW_SHARE = 4;
    /** The ring a revealed problem's elements blink with: wide, in its glow colour, the selection on top. */
    private static final float FLASH_RING_WIDTH = 9f;
    /** The see-through of a blink ring drawn in the selection's own colour. */
    private static final int ELEMENT_FLASH_ALPHA = 110;

    /**
     * What to light and mark on top of the model; {@code revealed} is the problem being shown (from Problemas, «Ver»,
     * F8): while it is, the other problems' glows fade.
     */
    record Marks(Optional<ActiveMachineState> active, MachineSelection selection, MachineGlows glows,
                 Optional<MachineFinding> revealed) {

        Marks(Optional<ActiveMachineState> active, MachineSelection selection, MachineGlows glows) {
            this(active, selection, glows, Optional.empty());
        }

        Marks(Optional<ActiveMachineState> active, MachineSelection selection) {
            this(active, selection, MachineGlows.NONE);
        }

        /** Whether the glow of the state {@code stateId} fades now: another problem is being shown. */
        boolean fadesState(String stateId) {
            return revealed.filter(finding -> !MachineGlows.statesOf(finding).contains(stateId)).isPresent();
        }

        /** Whether the glow of the arrow {@code transitionId} fades now: another problem is being shown. */
        boolean fadesTransition(String transitionId) {
            return revealed.filter(finding -> !MachineGlows.transitionsOf(finding).contains(transitionId))
                    .isPresent();
        }
    }

    /**
     * What a gesture shows while it lasts, on top of the diagram: the state whose border is lit, the arrow being drawn
     * (from a state's border to the pointer, or the loop a release on its own state would make) and the rubber band
     * (red when it takes away from the selection), all in diagram units.
     */
    record Overlay(Optional<String> litStateId, Optional<MachineDiagramGeometry.Arrow> pendingArrow,
                   Optional<Rectangle2D> rubberBand, boolean bandRemoves) {

        static final Overlay NONE = new Overlay(Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    /** The font of a state's name, at 100 %. */
    static Font nameFont() {
        return new Font(Font.SANS_SERIF, Font.BOLD, NAME_SIZE);
    }

    /** The font of a state's Moore outputs, at 100 %. */
    static Font outputsFont() {
        return new Font(Font.MONOSPACED, Font.PLAIN, OUTPUT_SIZE);
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
        overlay.pendingArrow().ifPresent(arrow -> {
            graphics.setStroke(new BasicStroke(NEXT_ARROW_WIDTH));
            graphics.draw(arrow.path());
            paintHead(graphics, arrow.headFrom(), arrow.end());
        });
        overlay.rubberBand().ifPresent(band -> {
            // a band that takes away is red, as on the circuit
            Color colour = overlay.bandRemoves() ? MachineColors.problem(scheme) : highlight;
            graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), RUBBER_BAND_ALPHA));
            graphics.fill(band);
            graphics.setColor(colour);
            graphics.setStroke(dashed(1f));
            graphics.draw(band);
        });
    }

    /**
     * The lit half of a revealed problem's blink: its states' borders and its arrows ringed wide in the problem's
     * colour, with the selection colour on top, so the eye finds them after the view moved (hand test of hito 2: the
     * selection colour alone was lost among the glows).
     */
    static void paintFlash(Graphics2D graphics, StateMachine machine, MachineDiagramGeometry geometry,
                           MachineFinding finding, ColorScheme scheme) {
        paintFlash(graphics, machine, geometry, MachineGlows.statesOf(finding), MachineGlows.transitionsOf(finding),
                finding.isWarning() ? MachineColors.warning(scheme) : MachineColors.problem(scheme), scheme);
    }

    /**
     * The lit half of a blink of the states {@code stateIds} and the arrows {@code transitionIds} (those still in
     * {@code machine}): ringed wide in {@code ring} (see-through when it is the selection's own colour, so the thin
     * selection line on top still reads), with the selection colour on top.
     */
    static void paintFlash(Graphics2D graphics, StateMachine machine, MachineDiagramGeometry geometry,
                           Collection<String> stateIds, Collection<String> transitionIds, Color ring,
                           ColorScheme scheme) {
        List<Shape> shapes = new ArrayList<>();
        stateIds.stream().filter(stateId -> machine.state(stateId).isPresent())
                .forEach(stateId -> shapes.add(geometry.circle(stateId)));
        transitionIds.stream()
                .filter(transitionId -> machine.transition(transitionId).isPresent())
                .forEach(transitionId -> shapes.add(geometry.arrow(transitionId).path()));
        Color selection = MachineColors.selection(scheme);
        graphics.setColor(ring.equals(selection)
                ? new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), ELEMENT_FLASH_ALPHA) : ring);
        graphics.setStroke(new BasicStroke(FLASH_RING_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        shapes.forEach(graphics::draw);
        graphics.setColor(selection);
        graphics.setStroke(new BasicStroke(LIT_BORDER_WIDTH));
        shapes.forEach(graphics::draw);
    }

    private MachineDiagramPainter() {
    }

    /**
     * Where the badge of a problem state goes: on its rim, up and to the right, where an arrow's head seldom lands.
     */
    static Point2D stateBadgeCentre(MachineDiagramGeometry geometry, String stateId) {
        Point2D centre = geometry.centre(stateId);
        double along = geometry.radius(stateId) * Math.sqrt(0.5);
        return new Point2D.Double(centre.getX() + along, centre.getY() - along);
    }

    /**
     * Where the badge of a problem arrow goes: just right of its label ({@code metrics} of {@link #labelFont()}), or
     * on the label's place when it has none.
     */
    static Point2D arrowBadgeCentre(MachineTransition transition, MachineDiagramGeometry.Arrow arrow,
                                    FontMetrics metrics) {
        String label = labelOf(transition);
        if (label.isEmpty()) {
            return arrow.labelAnchor();
        }
        Rectangle2D bounds = labelBounds(transition, arrow, metrics);
        return new Point2D.Double(bounds.getMaxX() + BADGE_GAP + BADGE_RADIUS, bounds.getCenterY());
    }

    static void paint(Graphics2D graphics, StateMachine machine, MachineDiagramGeometry geometry, Marks marks,
                      ColorScheme scheme) {
        paintGlows(graphics, machine, geometry, marks, scheme);
        Optional<String> nextTransition = marks.active().flatMap(ActiveMachineState::nextTransitionId);
        for (MachineTransition transition : machine.transitions()) {
            boolean next = nextTransition.filter(transition.id()::equals).isPresent();
            paintArrow(graphics, transition, geometry.arrow(transition.id()), next,
                    marks.selection().containsTransition(transition.id()),
                    marks.glows().ofTransition(transition.id()).filter(glow -> !marks.fadesTransition(transition.id())),
                    scheme);
        }
        geometry.initialMark().ifPresent(mark -> paintInitialMark(graphics, mark, scheme));
        geometry.initialDot().ifPresent(dot -> {
            graphics.setColor(MachineColors.muted(scheme));
            graphics.fill(dot);
        });
        Optional<String> activeState = marks.active().map(ActiveMachineState::stateId);
        for (MachineState state : drawingOrder(machine, marks)) {
            boolean faded = marks.fadesState(state.id());
            marks.glows().ofState(state.id()).ifPresent(glow -> paintHalo(graphics, state.id(), geometry, glow,
                    faded, scheme));
            paintState(graphics, state, geometry, activeState.filter(state.id()::equals).isPresent(),
                    marks.selection().containsState(state.id()),
                    marks.glows().ofState(state.id()).filter(glow -> !faded), scheme);
        }
        paintArrowBadges(graphics, machine, geometry, marks, scheme);
    }

    /**
     * The «!» of the problem arrows, over the states: beside a label it can land on a state's opaque inside, and it
     * is the cue that does not depend on telling colours apart (review of the hand fixes, M2).
     */
    private static void paintArrowBadges(Graphics2D graphics, StateMachine machine, MachineDiagramGeometry geometry,
                                         Marks marks, ColorScheme scheme) {
        FontMetrics metrics = graphics.getFontMetrics(labelFont());
        for (MachineTransition transition : machine.transitions()) {
            marks.glows().ofTransition(transition.id()).filter(glow -> !marks.fadesTransition(transition.id()))
                    .ifPresent(glow -> paintBadge(graphics, arrowBadgeCentre(transition,
                            geometry.arrow(transition.id()), metrics), glowColour(glow, scheme), scheme));
        }
    }

    /**
     * The states in the order they are drawn, each one whole (halo, inside, ring, name, outputs), so overlapping
     * states stack the same way all through: the plain ones in model order, then the selected ones, the active one
     * last (hand test of hito 2, round 2: one state's ring went over another's halo but under its inside).
     */
    static List<MachineState> drawingOrder(StateMachine machine, Marks marks) {
        return drawingOrder(machine, marks.active(), marks.selection());
    }

    /**
     * The ids of the states in the order they are drawn ({@link #drawingOrder(StateMachine, Marks)}), for hit testing
     * to pick the state on top.
     */
    static List<String> drawingOrderIds(StateMachine machine, Optional<ActiveMachineState> active,
                                        MachineSelection selection) {
        return drawingOrder(machine, active, selection).stream().map(MachineState::id).toList();
    }

    private static List<MachineState> drawingOrder(StateMachine machine, Optional<ActiveMachineState> active,
                                                   MachineSelection selection) {
        Optional<String> activeState = active.map(ActiveMachineState::stateId);
        List<MachineState> ordered = new ArrayList<>(machine.states());
        ordered.sort(Comparator.comparingInt(state -> activeState.filter(state.id()::equals).isPresent() ? 2
                : selection.containsState(state.id()) ? 1 : 0));
        return ordered;
    }

    /** The halo of a problem state, around its circle; faded while another problem is being shown. */
    private static void paintHalo(Graphics2D graphics, String stateId, MachineDiagramGeometry geometry,
                                  MachineGlows.Glow glow, boolean faded, ColorScheme scheme) {
        Point2D centre = geometry.centre(stateId);
        double radius = geometry.radius(stateId) + STATE_HALO;
        graphics.setColor(withAlpha(glowColour(glow, scheme), faded ? STATE_HALO_ALPHA / FADED_GLOW_SHARE
                : STATE_HALO_ALPHA));
        graphics.fill(new Ellipse2D.Double(centre.getX() - radius, centre.getY() - radius, 2 * radius, 2 * radius));
    }

    /**
     * The wide strokes under the problem arrows, under everything else; faded while another problem is being shown
     * (a problem state's halo is drawn with its state).
     */
    private static void paintGlows(Graphics2D graphics, StateMachine machine, MachineDiagramGeometry geometry,
                                   Marks marks, ColorScheme scheme) {
        MachineGlows glows = marks.glows();
        graphics.setStroke(new BasicStroke(ARROW_GLOW_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (MachineTransition transition : machine.transitions()) {
            glows.ofTransition(transition.id()).ifPresent(glow -> {
                graphics.setColor(withAlpha(glowColour(glow, scheme), marks.fadesTransition(transition.id())
                        ? ARROW_GLOW_ALPHA / FADED_GLOW_SHARE : ARROW_GLOW_ALPHA));
                graphics.draw(geometry.arrow(transition.id()).path());
            });
        }
    }

    private static Color glowColour(MachineGlows.Glow glow, ColorScheme scheme) {
        return glow == MachineGlows.Glow.ERROR ? MachineColors.problem(scheme) : MachineColors.warning(scheme);
    }

    private static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    private static void paintArrow(Graphics2D graphics, MachineTransition transition,
                                   MachineDiagramGeometry.Arrow arrow, boolean next, boolean selected,
                                   Optional<MachineGlows.Glow> glow, ColorScheme scheme) {
        Color plain = glow.map(found -> glowColour(found, scheme)).orElseGet(() -> MachineColors.muted(scheme));
        Color colour = next ? MachineColors.active(scheme)
                : selected ? MachineColors.selection(scheme) : plain;
        float width = next ? NEXT_ARROW_WIDTH : ARROW_WIDTH;
        if (next && selected) {
            // the selection's dashes on each side of the orange, which is drawn over them
            graphics.setColor(MachineColors.selection(scheme));
            graphics.setStroke(dashed(width + 2 * SELECTION_BESIDE));
            graphics.draw(arrow.path());
        }
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
                    : next ? MachineColors.active(scheme) : plain);
            graphics.setFont(labelFont());
            drawCentred(graphics, label, arrow.labelAnchor());
        }
    }

    /**
     * A small disc in the problem's colour with an «!» on it, ringed in the board's colour so it stands apart from
     * the line or the circle it sits on: the cue that does not depend on telling colours apart.
     */
    private static void paintBadge(Graphics2D graphics, Point2D centre, Color colour, ColorScheme scheme) {
        Ellipse2D disc = new Ellipse2D.Double(centre.getX() - BADGE_RADIUS, centre.getY() - BADGE_RADIUS,
                2 * BADGE_RADIUS, 2 * BADGE_RADIUS);
        graphics.setColor(colour);
        graphics.fill(disc);
        graphics.setColor(MachineColors.background(scheme));
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.draw(disc);
        graphics.setColor(ReadableColors.contrast(colour, Color.BLACK) >= ReadableColors.contrast(colour, Color.WHITE)
                ? Color.BLACK : Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, BADGE_MARK_SIZE));
        drawCentred(graphics, "!", centre);
    }

    /** «x», «x / Z=1», «/ Z=1» (otherwise, with a Mealy output) or nothing (otherwise alone). */
    static String labelOf(MachineTransition transition) {
        return MachineLabelText.of(transition);
    }

    /** A state's Moore outputs under it, written as Propiedades and the arrow labels write them. */
    static String mooreLabelOf(List<OutputAssignment> outputs) {
        return MachineOutputsText.write(outputs);
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

    /**
     * A state: its circle, filled with the colour the user gave it (hito 3), its outline in the problem colour when it
     * glows (like a problem arrow, so a warning reads as gold and not only as a faint halo), its name and its Moore
     * outputs where the geometry puts them, in a colour that reads on its inside.
     */
    private static void paintState(Graphics2D graphics, MachineState state, MachineDiagramGeometry geometry,
                                   boolean active, boolean selected, Optional<MachineGlows.Glow> glow,
                                   ColorScheme scheme) {
        graphics.setColor(MachineColors.stateFill(scheme, state, active));
        graphics.fill(geometry.circle(state.id()));
        float width = active ? ACTIVE_STATE_WIDTH : STATE_WIDTH;
        Color outline = glow.map(found -> glowColour(found, scheme))
                .orElseGet(() -> MachineColors.stateOutline(scheme));
        graphics.setColor(active ? MachineColors.active(scheme)
                : selected ? MachineColors.selection(scheme) : outline);
        graphics.setStroke(selected ? dashed(width) : new BasicStroke(width));
        graphics.draw(geometry.circle(state.id()));
        if (selected && active) {
            graphics.setColor(MachineColors.selection(scheme));
            graphics.setStroke(dashed(STATE_WIDTH));
            graphics.draw(geometry.circle(state.id()));
        }
        graphics.setColor(MachineColors.stateText(scheme, state));
        Font nameFont = geometry.nameFont(state.id());
        graphics.setFont(nameFont);
        List<String> lines = geometry.nameLines(state.id());
        double lineHeight = MachineDiagramGeometry.lineHeight(nameFont);
        Point2D centre = geometry.centre(state.id());
        double top = centre.getY() - lines.size() * lineHeight / 2;
        for (int index = 0; index < lines.size(); index++) {
            drawCentred(graphics, lines.get(index),
                    new Point2D.Double(centre.getX(), top + (index + 0.5) * lineHeight));
        }
        geometry.outputsBounds(state.id()).ifPresent(outputs -> {
            graphics.setColor(MachineColors.stateOutputs(scheme, state));
            graphics.setFont(outputsFont());
            drawCentred(graphics, mooreLabelOf(state.outputs()),
                    new Point2D.Double(outputs.getCenterX(), outputs.getCenterY()));
        });
        glow.ifPresent(found -> paintBadge(graphics, stateBadgeCentre(geometry, state.id()),
                glowColour(found, scheme), scheme));
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
