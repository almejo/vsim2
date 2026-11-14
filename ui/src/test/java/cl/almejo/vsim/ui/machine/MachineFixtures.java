package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;

/**
 * Machines for the machine tab's tests: a definition with a {@code machine} and an empty pins-only body (no pins is a
 * valid body), placed in a circuit. The generated body of the real machines comes with the core work of hito 1.
 */
public final class MachineFixtures {

    public static final String DEFINITION_ID = "detector";

    private MachineFixtures() {
    }

    /** «Detector 11»: S0 → S1 on x, S1 → S2 on x, back to S0 otherwise, S2 stays on x; Z=1 in S2. */
    public static StateMachine detector() {
        List<MachineState> states = List.of(
                new MachineState("s0", "S0", new DiagramPoint(0, 0), List.of()),
                new MachineState("s1", "S1", new DiagramPoint(160, 0), List.of()),
                new MachineState("s2", "S2", new DiagramPoint(320, 0), List.of(new OutputAssignment("Z", true))));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "s0", "s1", "x", List.of()),
                new MachineTransition("t2", "s1", "s0", "x'", List.of()),
                new MachineTransition("t3", "s1", "s2", "x", List.of()),
                new MachineTransition("t4", "s2", "s2", "x", List.of()),
                new MachineTransition("t5", "s2", "s0", "", List.of()),
                new MachineTransition("t6", "s0", "s0", "", List.of()));
        return new StateMachine(states, transitions, Optional.of("s0"), List.of("x"), List.of("Z"), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }

    /** A machine definition of {@code machine} with an empty body (no pins). */
    public static ComponentDefinition definition(StateMachine machine) {
        return new ComponentDefinition(DEFINITION_ID, "Detector 11", "", new GridSize(6, 4), List.of(),
                new CircuitFragment(List.of(), List.of(), List.of()), Optional.empty(), 1, Optional.of(machine));
    }

    /** A new circuit holding the detector machine, placed {@code copies} times; returns the document. */
    public static OpenDocument circuitWithMachines(int copies) {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        document.execute(new PutDefinitionsCommand(List.of(definition(detector()))));
        for (int copy = 0; copy < copies; copy++) {
            place(document, SubcircuitTypes.typeIdOf(DEFINITION_ID), copy * 20, 0, Map.of());
        }
        return document;
    }

    /** The ids of the placed machine copies, in placing order. */
    public static List<String> copies(OpenDocument document) {
        return document.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(DEFINITION_ID)))
                .map(Component::id)
                .map(ElementId::value)
                .toList();
    }
}
