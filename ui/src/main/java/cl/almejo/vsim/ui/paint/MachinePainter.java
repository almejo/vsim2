package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.MachineVisibleState;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineColors;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.FaceArrow;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.FaceLayout;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.FaceState;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The face of a state machine instance (spec §1, mockup D, hito 2): the default box with its pins' names and the
 * machine's name on top. With 1 to 4 states, its miniature in fixed places ({@link MachineFaceLayout}) with the
 * active state lit and the arrow the next edge takes in the active colour, and «S0 → S1» (current → next) below; from 5
 * states the current state's name, big, in the active colour, «próximo → S8» below and a small machine icon top-right.
 * With no state (stopped, or «Sin valores») nothing is lit and «—» stands for the state. The face is always upright
 * on screen, also when the piece is turned; the piece's size never depends on which face it shows. A drawn appearance
 * still wins ({@link SubcircuitPainter} decides). Colours are read at each paint. As a {@link ComponentPainter} it
 * draws the palette's icon: a box with two states and an arrow.
 */
public final class MachinePainter implements ComponentPainter {

    private static final float NAME_SIZE = 8f;
    private static final float TEXT_SIZE = 6f;
    private static final float BIG_NAME_SIZE = 16f;
    private static final float PIN_NAME_SIZE = 6f;
    private static final float MINIMUM_SIZE = 4f;
    private static final String ARROW = " → ";
    /** What the face writes for the state when there is none. */
    private static final String NO_STATE = "—";

    /** The air between a text and the box's border or a pin name, in local pixels. */
    private static final double INSET = 4;
    private static final double GAP = 2;
    /** The rows of the name on top and of the text below, in local pixels. */
    private static final double TITLE_ROW = 1.5 * UNIT;
    private static final double TEXT_ROW = 9;
    /** The pin names take at most this share of the box's width on each side, so the miniature keeps its room. */
    private static final double MAXIMUM_PIN_COLUMN_SHARE = 0.3;

    private static final float ARROW_WIDTH = 0.8f;
    private static final float NEXT_ARROW_WIDTH = 1.4f;
    private static final float STATE_WIDTH = 1f;
    private static final double HEAD_LENGTH = 3.2;
    private static final double HEAD_HALF_WIDTH = 1.6;
    /** The machine icon of the big-name face: three small states in a row, the middle one lit. */
    private static final double ICON_DOT_RADIUS = 1.8;
    private static final double ICON_DOT_SPACING = 5;
    private static final int ICON_DOTS = 3;
    private static final int ICON_LIT_DOT = 1;

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
            lines.setColor(MachineColors.active(scheme));
            lines.fill(new Ellipse2D.Double(first.getCenterX() - radius / 2, centerY - radius / 2, radius, radius));
        } finally {
            lines.dispose();
        }
    }

    /** The face of an instance of {@code definition}'s machine, showing {@code state} when it is a machine's. */
    static void paintFace(Graphics2D graphics, ComponentType type, ComponentDefinition definition,
                          VisibleState state, ColorScheme scheme) {
        SubcircuitPainter.paintBoxAndPins(graphics, type, scheme);
        StateMachine machine = definition.machine().orElseGet(StateMachine::initial);
        Optional<MachineVisibleState> current = state instanceof MachineVisibleState machineState
                ? Optional.of(machineState) : Optional.empty();
        Graphics2D upright = upright(graphics, type);
        try {
            FaceFrame frame = FaceFrame.of(upright, type, PainterSupport.turnedSideways(graphics));
            upright.clip(frame.box());
            FaceLayout layout = MachineFaceLayout.of(machine, frame.area());
            Face face = new Face(upright, frame, machine, current, scheme);
            switch (layout.mode()) {
                case MINIATURE -> face.paintMiniature(definition.name(), layout);
                case BIG_NAME -> face.paintBigName(definition.name());
            }
        } finally {
            upright.dispose();
        }
    }

    /**
     * A copy of {@code graphics} turned back to upright on screen, with its origin at the top-left of the box as seen
     * there: turned a quarter, the box is as wide as the piece is high.
     */
    private static Graphics2D upright(Graphics2D graphics, ComponentType type) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        boolean sideways = PainterSupport.turnedSideways(graphics);
        double uprightWidth = sideways ? height : width;
        double uprightHeight = sideways ? width : height;
        Graphics2D upright = (Graphics2D) graphics.create();
        upright.rotate(-PainterSupport.quarterTurns(graphics) * Math.PI / 2, width / 2, height / 2);
        upright.translate(width / 2 - uprightWidth / 2, height / 2 - uprightHeight / 2);
        upright.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return upright;
    }

    /**
     * Where the face's parts go on the upright box: the name's row on top, the text's row at the bottom and the area
     * between them and the pin names (at the sides, or at the top and bottom when the piece is turned a quarter).
     */
    private record FaceFrame(Rectangle2D box, double titleY, double textY, Rectangle2D area) {

        static FaceFrame of(Graphics2D graphics, ComponentType type, boolean sideways) {
            double width = (sideways ? type.size().height() : type.size().width()) * UNIT;
            double height = (sideways ? type.size().width() : type.size().height()) * UNIT;
            double sides = sideways ? INSET : pinColumn(graphics, type, width);
            double bands = sideways ? INSET + textHeight(graphics, PIN_NAME_SIZE) + GAP : 1;
            double titleTop = bands;
            double textBottom = height - bands;
            Rectangle2D area = new Rectangle2D.Double(sides, titleTop + TITLE_ROW, width - 2 * sides,
                    textBottom - TEXT_ROW - titleTop - TITLE_ROW);
            return new FaceFrame(new Rectangle2D.Double(1, 1, width - 2, height - 2),
                    titleTop + TITLE_ROW / 2, textBottom - TEXT_ROW / 2, area);
        }

        /** How far the widest pin name reaches into the box from its side, at most a share of the box's width. */
        private static double pinColumn(Graphics2D graphics, ComponentType type, double width) {
            double widest = 0;
            for (PinSpec pin : type.pins()) {
                widest = Math.max(widest, textWidth(graphics, SubcircuitPainter.pinText(pin), PIN_NAME_SIZE));
            }
            return Math.min(width * MAXIMUM_PIN_COLUMN_SHARE, INSET + widest + GAP);
        }
    }

    /** One paint of a face: the upright graphics, its frame, the machine and its current state. */
    private record Face(Graphics2D graphics, FaceFrame frame, StateMachine machine,
                        Optional<MachineVisibleState> current, ColorScheme scheme) {

        /** The miniature, the name on top and «S0 → S1» below. */
        void paintMiniature(String name, FaceLayout layout) {
            paintTitle(name, frame.box().getCenterX(), frame.box().getWidth() - 2 * INSET);
            Optional<String> nextTransition = current.flatMap(MachineVisibleState::nextTransitionId);
            for (FaceArrow arrow : layout.arrows()) {
                if (nextTransition.filter(arrow.transitionId()::equals).isEmpty()) {
                    paintArrow(arrow, MachineColors.muted(scheme), ARROW_WIDTH);
                }
            }
            layout.arrows().stream().filter(arrow -> nextTransition.filter(arrow.transitionId()::equals).isPresent())
                    .forEach(arrow -> paintArrow(arrow, MachineColors.active(scheme), NEXT_ARROW_WIDTH));
            Optional<String> activeState = current.map(MachineVisibleState::stateId);
            for (FaceState state : layout.states()) {
                paintCircle(state.circle(), activeState.filter(state.stateId()::equals).isPresent());
            }
            String text = current.map(state -> state.stateName()
                    + state.nextStateId().map(nextId -> ARROW + nameOf(nextId)).orElse("")).orElse(NO_STATE);
            paintText(text, TEXT_SIZE, frame.textY(), scheme.text());
        }

        /** The current state's name, big, «próximo → S8» below and the machine icon next to the name on top. */
        void paintBigName(String name) {
            double iconWidth = (ICON_DOTS - 1) * ICON_DOT_SPACING + 2 * ICON_DOT_RADIUS;
            double iconLeft = frame.box().getMaxX() - INSET - iconWidth;
            // the name centred in the room left of the icon, as wide as it can be on a piece turned a quarter
            double nameLeft = frame.box().getMinX() + INSET;
            double nameRight = iconLeft - GAP;
            paintTitle(name, (nameLeft + nameRight) / 2, nameRight - nameLeft);
            paintIcon(iconLeft);
            Rectangle2D area = frame.area();
            if (current.isEmpty()) {
                paintText(NO_STATE, BIG_NAME_SIZE, area.getCenterY(), scheme.text());
                return;
            }
            MachineVisibleState state = current.get();
            paintText(state.stateName(), BIG_NAME_SIZE, area.getCenterY(), MachineColors.active(scheme));
            state.nextStateId().ifPresent(nextId -> paintText(Messages.format("machine.face.next", nameOf(nextId)),
                    TEXT_SIZE, frame.textY(), scheme.text()));
        }

        private void paintTitle(String name, double centreX, double available) {
            float size = fitted(graphics, name, NAME_SIZE, available);
            PainterSupport.centeredText(graphics, frame.box(), name, centreX, frame.titleY(), size, scheme.text());
        }

        /** {@code text} centred on the box at {@code centreY}, shrunk to fit its width (never below the minimum). */
        private void paintText(String text, float preferred, double centreY, Color colour) {
            float size = fitted(graphics, text, preferred, frame.box().getWidth() - 2 * INSET);
            PainterSupport.centeredText(graphics, frame.box(), text, frame.box().getCenterX(), centreY, size, colour);
        }

        private void paintIcon(double left) {
            for (int dot = 0; dot < ICON_DOTS; dot++) {
                double centreX = left + ICON_DOT_RADIUS + dot * ICON_DOT_SPACING;
                Ellipse2D circle = new Ellipse2D.Double(centreX - ICON_DOT_RADIUS, frame.titleY() - ICON_DOT_RADIUS,
                        2 * ICON_DOT_RADIUS, 2 * ICON_DOT_RADIUS);
                boolean lit = dot == ICON_LIT_DOT && current.isPresent();
                paintCircle(circle, lit);
            }
        }

        /** A state's circle: lit in the active colour, or the diagram's plain state. */
        private void paintCircle(Ellipse2D circle, boolean lit) {
            graphics.setColor(lit ? MachineColors.active(scheme) : MachineColors.stateFill(scheme));
            graphics.fill(circle);
            graphics.setColor(lit ? MachineColors.active(scheme) : MachineColors.stateOutline(scheme));
            graphics.setStroke(new BasicStroke(STATE_WIDTH));
            graphics.draw(circle);
        }

        private void paintArrow(FaceArrow arrow, Color colour, float width) {
            graphics.setColor(colour);
            graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(arrow.path());
            paintHead(arrow.headFrom(), arrow.tip(), width / ARROW_WIDTH);
        }

        private void paintHead(Point2D from, Point2D tip, double scale) {
            double length = from.distance(tip);
            if (length == 0) {
                return;
            }
            double unitX = (tip.getX() - from.getX()) / length;
            double unitY = (tip.getY() - from.getY()) / length;
            double headLength = HEAD_LENGTH * Math.sqrt(scale);
            double halfWidth = HEAD_HALF_WIDTH * Math.sqrt(scale);
            double baseX = tip.getX() - unitX * headLength;
            double baseY = tip.getY() - unitY * headLength;
            Path2D head = new Path2D.Double();
            head.moveTo(tip.getX(), tip.getY());
            head.lineTo(baseX - unitY * halfWidth, baseY + unitX * halfWidth);
            head.lineTo(baseX + unitY * halfWidth, baseY - unitX * halfWidth);
            head.closePath();
            graphics.fill(head);
        }

        private String nameOf(String stateId) {
            return machine.state(stateId).map(MachineState::name).orElse(stateId);
        }
    }

    private static float fitted(Graphics2D graphics, String text, float preferred, double available) {
        double textWidth = textWidth(graphics, text, preferred);
        return textWidth <= available ? preferred
                : (float) Math.max(MINIMUM_SIZE, preferred * available / textWidth);
    }

    private static double textWidth(Graphics2D graphics, String text, float size) {
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size));
        return metrics.stringWidth(text);
    }

    private static double textHeight(Graphics2D graphics, float size) {
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size));
        return metrics.getAscent() - metrics.getDescent();
    }
}
