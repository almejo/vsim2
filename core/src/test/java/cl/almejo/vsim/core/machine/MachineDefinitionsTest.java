package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.logic.LogicValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MachineDefinitionsTest {

    @Test
    void aNewMachineDefinitionHoldsTheInitialMachineAndItsGeneratedBody() {
        ComponentDefinition definition = MachineDefinitions.create("maquina-1", "Máquina 1");
        assertThat(definition.id()).isEqualTo("maquina-1");
        assertThat(definition.name()).isEqualTo("Máquina 1");
        assertThat(definition.machine()).contains(StateMachine.initial());
        assertThat(definition.revision()).isEqualTo(1);
        MachineBody expected = MachineBody.of(StateMachine.initial());
        assertThat(definition.body()).isEqualTo(expected.body());
        assertThat(definition.pinout()).isEqualTo(expected.pinout());
        assertThat(definition.size()).isEqualTo(expected.size());
    }

    @Test
    void withMachineRegeneratesTheBodyAndKeepsTheRevision() {
        ComponentDefinition definition = MachineDefinitions.create("maquina-1", "Máquina 1");
        StateMachine machine = MachineExamples.twoOnesDetector();
        ComponentDefinition changed = definition.withMachine(machine);
        assertThat(changed.machine()).contains(machine);
        assertThat(changed.pinout()).isEqualTo(MachineBody.of(machine).pinout());
        assertThat(changed.revision()).isEqualTo(definition.revision());
    }

    @Test
    void aDefinitionWhoseBodyDisagreesIsRegenerated() {
        ComponentDefinition definition = MachineDefinitions.create("maquina-1", "Máquina 1");
        StateMachine machine = StateMachine.initial().withPinNames(List.of("x"), List.of());
        ComponentDefinition stale = new ComponentDefinition(definition.id(), definition.name(), "", definition.size(),
                definition.pinout(), definition.body(), definition.appearance(), 4, Optional.of(machine));
        ComponentDefinition regenerated = MachineDefinitions.regenerated(stale);
        assertThat(regenerated.pinout()).isEqualTo(MachineBody.of(machine).pinout());
        assertThat(regenerated.revision()).isEqualTo(4);
        assertThat(MachineDefinitions.regenerated(regenerated)).isSameAs(regenerated);
    }

    // --- tests live only in the circuit (the user's rule, hito 4) ---

    @Test
    void aDefinitionForTheLibraryLeavesItsTestsBehindAndKeepsItsRevision() {
        ComponentDefinition withTests = detectorWithTests(MachineExamples.twoOnesDetector());

        ComponentDefinition stripped = MachineDefinitions.withoutTests(withTests);

        assertThat(stripped.machine().orElseThrow()).isEqualTo(withTests.machine().orElseThrow()
                .withTests(List.of()));
        assertThat(stripped.revision()).isEqualTo(withTests.revision());
        assertThat(stripped.pinout()).isEqualTo(withTests.pinout());
        assertThat(MachineDefinitions.withoutTests(stripped)).isSameAs(stripped);
    }

    @Test
    void aBundleForTheLibraryLeavesTheTestsOfEveryMachineInItBehind() {
        ComponentDefinition machine = detectorWithTests(MachineExamples.twoOnesDetector());
        ComponentDefinition plain = new ComponentDefinition("plain", "Plain", "", machine.size(), List.of(),
                new CircuitFragment(List.of(new Component(new ElementId("m"), SubcircuitTypes.typeIdOf("maquina-1"),
                        GridPoint.ORIGIN, Rotation.R0, Map.of(), "")), List.of(), List.of()));
        DefinitionBundle bundle = DefinitionBundle.of(plain,
                id -> id.equals("maquina-1") ? Optional.of(machine) : Optional.empty());

        DefinitionBundle forLibrary = MachineDefinitions.forLibrary(bundle);

        assertThat(forLibrary.root()).isEqualTo(plain);
        assertThat(forLibrary.dependencies()).singleElement()
                .satisfies(dependency -> assertThat(dependency.machine().orElseThrow().tests()).isEmpty());
        assertThat(MachineDefinitions.forLibrary(forLibrary)).isEqualTo(forLibrary);
    }

    @Test
    void followingTheLibrarysCopyKeepsTheCircuitsTestsFittedToTheNewPins() {
        ComponentDefinition inCircuit = detectorWithTests(MachineExamples.twoOnesDetector()
                .withPinNames(List.of("x", "y"), List.of("Z")));
        StateMachine libraryMachine = MachinePinEdits.renamed(MachineExamples.twoOnesDetector(),
                MachinePinEdits.Side.INPUT, "x", "a");
        ComponentDefinition fromLibrary = MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(libraryMachine);

        ComponentDefinition followed = MachineDefinitions.keepingTests(inCircuit, fromLibrary);

        StateMachine machine = followed.machine().orElseThrow();
        assertThat(machine.withTests(List.of())).isEqualTo(libraryMachine);
        assertThat(machine.tests()).singleElement().satisfies(test -> {
            assertThat(test.columns().get(0).inputs()).as("x kept by its id, y gone").containsOnlyKeys("x");
            assertThat(test.columns().get(0).expected("Z")).isEqualTo(LogicValue.ONE);
        });
        assertThat(followed.revision()).isEqualTo(fromLibrary.revision());
        assertThat(followed.pinout()).isEqualTo(fromLibrary.pinout());
    }

    @Test
    void followingTheLibraryWithoutTestsInTheCircuitTakesTheLibrarysCopyAsIs() {
        ComponentDefinition inCircuit = MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector());
        ComponentDefinition fromLibrary = inCircuit.withName("Detector");

        assertThat(MachineDefinitions.keepingTests(inCircuit, fromLibrary)).isSameAs(fromLibrary);
    }

    /** The machine definition {@code maquina-1} of {@code machine} with one test setting x, y and expecting Z = 1. */
    private static ComponentDefinition detectorWithTests(StateMachine machine) {
        MachineTest test = MachineTest.empty("p1", "Prueba 1", 2);
        test = MachineTests.withInput(MachineTests.withInput(test, 0, "x", true), 0, "y", true);
        test = MachineTests.withExpected(test, 0, "Z", LogicValue.ONE);
        return MachineDefinitions.edited(MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(machine),
                machine.withTests(List.of(test)));
    }
}
