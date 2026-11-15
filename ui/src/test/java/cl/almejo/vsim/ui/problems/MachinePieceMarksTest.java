package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine piece whose machine has an error glows red with the warning triangle in the circuit (plan 2026-11-14,
 * hito 2 task 8); a machine with only warnings leaves its piece plain.
 */
class MachinePieceMarksTest {

    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Component placed(StateMachine machine) {
        openDocument.execute(new PutDefinitionsCommand(List.of(
                MachineDefinitions.regenerated(MachineFixtures.definition(machine)))));
        return place(openDocument, SubcircuitTypes.typeIdOf(MachineFixtures.DEFINITION_ID), 4, 4, Map.of());
    }

    @Test
    void aPieceWhoseMachineHasAnErrorShowsTheTriangle() {
        // S0 goes to S1 and to S2 on x: two arrows true at once
        StateMachine detector = MachineFixtures.detector();
        List<MachineTransition> withDuplicate = new ArrayList<>(detector.transitions());
        withDuplicate.add(new MachineTransition("twin", "s0", "s2", "x", List.of()));
        Component piece = placed(detector.withTransitions(withDuplicate));
        Bounds bounds = openDocument.registry().typeOf(piece).bounds(piece);

        assertThat(pixel(bounds.maximumX() * UNIT + 4, bounds.y() * UNIT + 6)).as("the triangle").isNotZero();
    }

    @Test
    void warningsLeaveThePiecePlain() {
        // without S0 → S1, S1 and S2 are never reached: two warnings, no error
        StateMachine detector = MachineFixtures.detector();
        Component piece = placed(detector.withTransitions(detector.transitions().subList(1,
                detector.transitions().size())));
        Bounds bounds = openDocument.registry().typeOf(piece).bounds(piece);

        assertThat(pixel(bounds.maximumX() * UNIT + 4, bounds.y() * UNIT + 6)).isZero();
    }

    private int pixel(int x, int y) {
        ConnectivityOverlay overlay = new ConnectivityOverlay(openDocument, () -> ColorScheme.LIGHT);
        BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return image.getRGB(x, y);
    }
}
