package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Turning a machine's inputs into a bus takes their pins away: the wires that went to them are listed in Problemas
 * (never hidden), and undo takes the listing away with the bus.
 */
class MachineBusProblemsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private OpenDocument document;

    @AfterEach
    void close() {
        if (document != null) {
            document.close();
        }
    }

    @Test
    void theWiresOfThePinsABusTookAwayAreListedUntilUndo() {
        StateMachine detector = MachineFixtures.detector().withPinNames(List.of("x", "y"), List.of("Z"));
        ComponentDefinition machine = MachineDefinitions.regenerated(MachineFixtures.definition(detector));
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(machine)));
        Component piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(machine.id()), 20, 0, Map.of());
        switchOn(piece, machine, "x");
        switchOn(piece, machine, "y");
        assertThat(document.insideProblems()).isEmpty();

        document.execute(new EditMachineCommand(machine.id(),
                MachinePinEdits.asBus(detector, MachinePinEdits.Side.INPUT, true)));

        assertThat(document.insideProblems()).extracting(ConnectivityProblem::elementId,
                        ConnectivityProblem::cause)
                .containsExactlyInAnyOrder(
                        tuple(piece.id().value(),
                                ConnectivityProblem.Cause.WIRE_OF_REMOVED_PIN),
                        tuple(piece.id().value(),
                                ConnectivityProblem.Cause.WIRE_OF_REMOVED_PIN));
        document.history().undo();
        assertThat(document.insideProblems()).isEmpty();
    }

    /** A switch wired to {@code pinName} of {@code piece} from 4 units to its left. */
    private void switchOn(Component piece, ComponentDefinition machine, String pinName) {
        GridPoint pin = document.registry().typeOf(piece).pinLocation(piece,
                machine.pinIndexOf(pinName).orElseThrow());
        GridPoint end = pin.translate(-4, 0);
        ComponentType switchType = document.registry().require(StandardComponentTypes.SWITCH);
        Component probe = switchType.newComponent(new ElementId("probe"), GridPoint.ORIGIN, Rotation.R0, Map.of(),
                "");
        GridPoint offset = switchType.pinLocation(probe, 0);
        UiTestSupport.place(document, StandardComponentTypes.SWITCH, end.x() - offset.x(), end.y() - offset.y(),
                Map.of());
        UiTestSupport.addWire(document, pin.x(), pin.y(), end.x(), end.y());
    }
}
