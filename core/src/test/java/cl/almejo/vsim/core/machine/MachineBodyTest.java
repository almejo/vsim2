package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MachineBodyTest {

    @Test
    void aNewMachineHasOnlyResetAndClockOnTheLeft() {
        MachineBody body = MachineBody.of(StateMachine.initial());
        assertThat(names(body.pinout())).containsExactly("RST", "CLK");
        assertThat(body.pinout()).allSatisfy(pin -> {
            assertThat(pin.spec().direction()).isEqualTo(PinDirection.INPUT);
            assertThat(pin.spec().position().x()).isZero();
        });
    }

    @Test
    void inputsComeFirstThenResetThenTheClockAndOutputsGoOnTheRight() {
        StateMachine machine = MachineExamples.twoOnesDetector().withPinNames(List.of("x", "y"), List.of("Z"));
        MachineBody body = MachineBody.of(machine);
        assertThat(names(body.pinout())).containsExactly("x", "y", "RST", "CLK", "Z");
        DefinitionPin output = body.pinout().get(4);
        assertThat(output.spec().direction()).isEqualTo(PinDirection.OUTPUT);
        assertThat(output.spec().position().x()).isEqualTo(body.size().width());
        assertThat(body.pinout().get(0).spec().position().y())
                .isLessThan(body.pinout().get(3).spec().position().y());
    }

    @Test
    void theBodyHoldsOneLabelledInterfacePinPerPin() {
        MachineBody body = MachineBody.of(MachineExamples.twoOnesDetector());
        assertThat(body.body().wires()).isEmpty();
        for (DefinitionPin pin : body.pinout()) {
            Component source = body.body().components().stream()
                    .filter(component -> component.id().equals(pin.source())).findFirst().orElseThrow();
            assertThat(source.label()).isEqualTo(pin.spec().name());
            assertThat(source.typeId()).isEqualTo(pin.spec().direction() == PinDirection.OUTPUT
                    ? InterfacePins.OUTPUT_PIN : InterfacePins.INPUT_PIN);
        }
        assertThat(body.body().components()).hasSameSizeAs(body.pinout());
    }

    @Test
    void theSizeGrowsWithThePins() {
        MachineBody few = MachineBody.of(StateMachine.initial());
        MachineBody many = MachineBody.of(StateMachine.initial()
                .withPinNames(List.of("a", "b", "c", "d", "e", "f"), List.of()));
        assertThat(many.size().width()).isEqualTo(few.size().width());
        assertThat(many.size().height()).isGreaterThan(few.size().height());
    }

    @Test
    void aPinKeepsItsSourceWhenOthersAreAdded() {
        MachineBody before = MachineBody.of(StateMachine.initial().withPinNames(List.of("x"), List.of("Z")));
        MachineBody after = MachineBody.of(StateMachine.initial().withPinNames(List.of("w", "x"), List.of("Z", "Y")));
        assertThat(sourceOf(after, "x")).isEqualTo(sourceOf(before, "x"));
        assertThat(sourceOf(after, "Z")).isEqualTo(sourceOf(before, "Z"));
        assertThat(sourceOf(after, "CLK")).isEqualTo(sourceOf(before, "CLK"));
    }

    @Test
    void aPinNameWithASlashStillMakesAValidId() {
        MachineBody body = MachineBody.of(StateMachine.initial().withPinNames(List.of("a/b"), List.of()));
        assertThat(sourceOf(body, "a/b")).doesNotContain("/");
    }

    @Test
    void theGeneratedPartsMakeAValidDefinition() {
        MachineBody body = MachineBody.of(MachineExamples.twoOnesDetector());
        ComponentDefinition definition = new ComponentDefinition("maquina-1", "Máquina 1", "", body.size(),
                body.pinout(), body.body(), Optional.empty(), 1, Optional.of(MachineExamples.twoOnesDetector()));
        assertThat(definition.isMachine()).isTrue();
        assertThat(definition.pinout().get(0).spec().position()).isEqualTo(new GridPoint(0, 2));
    }

    private static List<String> names(List<DefinitionPin> pinout) {
        return pinout.stream().map(pin -> pin.spec().name()).toList();
    }

    private static String sourceOf(MachineBody body, String name) {
        return body.pinout().stream().filter(pin -> pin.spec().name().equals(name)).findFirst().orElseThrow()
                .source().value();
    }
}
