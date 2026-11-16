package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A machine's native pins are found by name and direction, never by position, and a disagreement fails loudly. */
class MachinePinLayoutTest {

    @Test
    void theDetectorsPinsAreFoundByName() {
        StateMachine machine = MachineExamples.twoOnesDetector();

        MachinePinLayout layout = MachinePinLayout.of(nativeType(machine), machine);

        assertThat(layout.inputBus()).isEmpty();
        assertThat(layout.inputPins()).isEqualTo(Map.of("x", 0));
        assertThat(layout.reset()).isEqualTo(1);
        assertThat(layout.clock()).isEqualTo(2);
        assertThat(layout.outputBus()).isEmpty();
        assertThat(layout.outputPins()).isEqualTo(Map.of("Z", 3));
        assertThat(layout.state()).isEqualTo(4);
    }

    @Test
    void aBusSideHasOnePinAndNoPinPerName() {
        StateMachine machine = MachineExamples.twoOnesDetector().withPinNames(List.of("x", "y"), List.of("Z", "W"))
                .withBus(true, true);

        MachinePinLayout layout = MachinePinLayout.of(nativeType(machine), machine);

        assertThat(layout.inputBus()).isEqualTo(OptionalInt.of(0));
        assertThat(layout.inputPins()).isEmpty();
        assertThat(layout.reset()).isEqualTo(1);
        assertThat(layout.clock()).isEqualTo(2);
        assertThat(layout.outputBus()).isEqualTo(OptionalInt.of(3));
        assertThat(layout.outputPins()).isEmpty();
        assertThat(layout.state()).isEqualTo(4);
    }

    @Test
    void pinsInAnotherOrderAreStillFoundByName() {
        StateMachine machine = MachineExamples.twoOnesDetector().withPinNames(List.of("x", "y"), List.of("Z"));
        List<PinSpec> pins = new ArrayList<>(nativeType(machine).pins());
        pins.add(0, pins.remove(4)); // Z first

        MachinePinLayout layout = MachinePinLayout.of(typeWith(pins), machine);

        assertThat(layout.outputPins()).isEqualTo(Map.of("Z", 0));
        assertThat(layout.inputPins()).isEqualTo(Map.of("x", 1, "y", 2));
        assertThat(layout.clock()).isEqualTo(4);
    }

    @Test
    void aPinoutWithoutAnOutputOfTheModelFailsLoudly() {
        StateMachine machine = MachineExamples.twoOnesDetector();
        List<PinSpec> pins = new ArrayList<>(nativeType(machine).pins());
        pins.remove(3); // Z

        assertThatThrownBy(() -> MachinePinLayout.of(typeWith(pins), machine))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Z");
    }

    @Test
    void aBusOfTheWrongWidthFailsLoudly() {
        StateMachine machine = MachineExamples.twoOnesDetector().withPinNames(List.of("x", "y"), List.of("Z"));
        ComponentType oneBitBus = nativeType(machine.withBus(true, false).withPinNames(List.of("x"), List.of("Z")));

        assertThatThrownBy(() -> MachinePinLayout.of(oneBitBus, machine.withBus(true, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(MachineBody.BUS_INPUT_NAME);
    }

    @Test
    void anExtraPinFailsLoudly() {
        StateMachine machine = MachineExamples.twoOnesDetector();
        List<PinSpec> pins = new ArrayList<>(nativeType(machine).pins());
        pins.add(PinSpec.input("q", 0, 20, 1));

        assertThatThrownBy(() -> MachinePinLayout.of(typeWith(pins), machine))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("q");
    }

    private static ComponentType nativeType(StateMachine machine) {
        ComponentDefinition definition = MachineDefinitions.create("maquina-1", "Máquina 1").withMachine(machine);
        return StateMachineTypes.typeOf(definition);
    }

    private static ComponentType typeWith(List<PinSpec> pins) {
        return new ComponentType("machine:maquina-1", ComponentCategory.CUSTOM, new GridSize(12, 8), pins, List.of(),
                parameters -> {
                    throw new UnsupportedOperationException();
                });
    }
}
