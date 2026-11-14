package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.library.ComponentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
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
}
