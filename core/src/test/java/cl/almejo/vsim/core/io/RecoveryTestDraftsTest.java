package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineExamples;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A recovery copy carries the machines' test drafts apart, in a field only recovery copies have (fix round 2 of hito
 * 4): the circuit reads as before (an older VSim, or the normal reader, never sees them), and the drafts come back.
 */
class RecoveryTestDraftsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    private static CircuitDocument circuit() {
        ComponentDefinition definition = MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector());
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(definition);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(10, 10), Rotation.R0, Map.of(), ""));
        return document;
    }

    private static StateMachine draft() {
        StateMachine detector = MachineExamples.twoOnesDetector();
        return detector.withInitialStateId(Optional.of(detector.states().getLast().id()))
                .withTests(List.of(MachineTest.empty("p1", "dos unos", 3)));
    }

    @Test
    void theDraftsComeBackAndTheCircuitReadsAsWithoutThem() throws CircuitFormatException {
        CircuitDocument document = circuit();
        String plain = format.write(document);

        String copy = RecoveryTestDrafts.withDrafts(plain, Map.of("maquina-1", draft()));

        assertThat(copy).contains("\"" + RecoveryTestDrafts.FIELD + "\"");
        assertThat(RecoveryTestDrafts.drafts(copy)).containsExactly(Map.entry("maquina-1", draft()));
        // the normal reader ignores the field: the same circuit, written back without it
        assertThat(format.write(format.read(copy))).isEqualTo(plain);
    }

    @Test
    void aCopyWithoutDraftsIsTheCircuitItselfAndHasNone() {
        String plain = format.write(circuit());

        assertThat(RecoveryTestDrafts.withDrafts(plain, Map.of())).isEqualTo(plain);
        assertThat(RecoveryTestDrafts.drafts(plain)).isEmpty();
        assertThat(RecoveryTestDrafts.drafts("not json")).isEmpty();
    }
}
