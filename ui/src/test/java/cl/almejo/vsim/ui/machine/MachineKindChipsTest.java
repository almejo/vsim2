package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineKind;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MachineKindChipsTest {

    private static final OutputAssignment Z_ON = new OutputAssignment("Z", true);
    private static final OutputAssignment Y_ON = new OutputAssignment("Y", true);

    @BeforeAll
    static void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterAll
    static void restore() {
        Messages.initialize(Locale.getDefault());
    }

    private static StateMachine machine(List<OutputAssignment> inState, List<OutputAssignment> onArrow) {
        return new StateMachine(
                List.of(new MachineState("a", "A", new DiagramPoint(0, 0), inState),
                        new MachineState("b", "B", new DiagramPoint(100, 0), List.of())),
                List.of(new MachineTransition("t1", "a", "b", "x", onArrow)), Optional.of("a"), List.of("x"),
                List.of("Z", "Y"), false, false, StateMachine.EDGE_FALLING, List.of());
    }

    private static MachineKind kindOf(List<OutputAssignment> inState, List<OutputAssignment> onArrow) {
        return MachineKind.of(machine(inState, onArrow));
    }

    private static Point2D centerOf(Rectangle2D bounds) {
        return new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
    }

    @Test
    void theSummaryOfAMixedMachineCountsOutputsInStatesAndOnArrows() {
        assertThat(MachineKindChips.summary(kindOf(List.of(Z_ON, Y_ON), List.of(Z_ON))))
                .contains("2 salidas en estados, 1 en flechas");
        assertThat(MachineKindChips.summary(kindOf(List.of(Z_ON), List.of(Z_ON, Y_ON))))
                .contains("1 salida en estados, 2 en flechas");
    }

    @Test
    void thereIsNoSummaryUnlessMixed() {
        assertThat(MachineKindChips.summary(kindOf(List.of(Z_ON), List.of()))).isEmpty();
        assertThat(MachineKindChips.summary(kindOf(List.of(), List.of()))).isEmpty();
    }

    @Test
    void eachChipExplainsItselfInATooltip() {
        Font font = MachineKindChips.defaultFont();
        MachineKind moore = kindOf(List.of(Z_ON), List.of());
        assertThat(MachineKindChips.tooltipAt(centerOf(MachineKindChips.mooreBounds(font)), moore, font))
                .contains("Moore: la salida depende solo del estado");
        assertThat(MachineKindChips.tooltipAt(centerOf(MachineKindChips.mealyBounds(font)), moore, font))
                .hasValueSatisfying(text -> assertThat(text).startsWith("Mealy:"));
    }

    @Test
    void withoutOutputsTheTooltipSaysSo() {
        Font font = MachineKindChips.defaultFont();
        assertThat(MachineKindChips.tooltipAt(centerOf(MachineKindChips.mooreBounds(font)),
                kindOf(List.of(), List.of()), font)).contains("Todavía no tiene salidas");
    }

    @Test
    void awayFromTheChipsThereIsNoTooltip() {
        assertThat(MachineKindChips.tooltipAt(new Point2D.Double(700, 400), kindOf(List.of(Z_ON), List.of()),
                MachineKindChips.defaultFont())).isEmpty();
    }

    @Test
    void theLitChipDiffersFromTheUnlitOneAndNeitherIsOrangeOrAmber() {
        Font font = MachineKindChips.defaultFont();
        for (ColorScheme scheme : List.of(ColorSchemes.LIGHT, ColorSchemes.DARK)) {
            BufferedImage image = paint(kindOf(List.of(Z_ON), List.of()), scheme);
            Rectangle2D mooreBounds = MachineKindChips.mooreBounds(font);
            Rectangle2D mealyBounds = MachineKindChips.mealyBounds(font);
            // just inside the left edge of each chip, away from its text
            int mooreInside = image.getRGB((int) mooreBounds.getMinX() + 5, (int) mooreBounds.getCenterY());
            int mealyInside = image.getRGB((int) mealyBounds.getMinX() + 5, (int) mealyBounds.getCenterY());
            assertThat(mooreInside).as("lit vs unlit in %s", scheme).isNotEqualTo(mealyInside);
            assertThat(distance(new Color(mooreInside), scheme.machineActive())).isGreaterThan(60);
            assertThat(distance(new Color(mooreInside), scheme.warning())).isGreaterThan(60);
        }
    }

    private static BufferedImage paint(MachineKind kind, ColorScheme scheme) {
        BufferedImage image = new BufferedImage(260, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 260, 90);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        MachineKindChips.paint(graphics, kind, scheme);
        graphics.dispose();
        return image;
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}
