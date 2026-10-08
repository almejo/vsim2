package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentTypeTest {

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final ComponentType buffer = registry.require(TestComponentTypes.BUFFER);

    @Test
    void integerParameterNormalization() {
        ParameterSpec delay = ParameterSpec.integer("delay", 1, 1, 10);
        assertThat(delay.normalize(5)).isEqualTo(5);
        assertThat(delay.normalize("7")).isEqualTo(7);
        assertThat(delay.normalize(3L)).isEqualTo(3);
        assertThatThrownBy(() -> delay.normalize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delay.normalize("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choiceAndTextParameterNormalization() {
        ParameterSpec value = ParameterSpec.choice("value", "0", List.of("0", "1", "Z"));
        assertThat(value.normalize(1)).isEqualTo("1");
        assertThat(value.normalize("Z")).isEqualTo("Z");
        assertThatThrownBy(() -> value.normalize("2")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ParameterSpec.text("color", "red").normalize(42)).isEqualTo("42");
    }

    @Test
    void choiceDefaultMustBeAChoice() {
        assertThatThrownBy(() -> ParameterSpec.choice("value", "9", List.of("0", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveParametersFillsDefaultsAndDropsUnknownKeys() {
        assertThat(buffer.resolveParameters(Map.of())).isEqualTo(Map.of("delay", 1));
        assertThat(buffer.resolveParameters(Map.of("delay", 4, "unknown", "x"))).isEqualTo(Map.of("delay", 4));
    }

    @Test
    void pinLocationAppliesPositionAndRotation() {
        Component component = new Component(new ElementId("c1"), buffer.id(), new GridPoint(10, 10), Rotation.R0, Map.of(), "");
        assertThat(buffer.pinLocation(component, 1)).isEqualTo(new GridPoint(12, 11));
        Component rotated = component.withRotation(Rotation.R90);
        assertThat(buffer.pinLocation(rotated, 1)).isEqualTo(new GridPoint(9, 12));
        assertThat(buffer.bounds(rotated)).isEqualTo(new Bounds(8, 10, 2, 2));
    }

    @Test
    void pinDirectionsHelpers() {
        assertThat(buffer.pin(0).isInput()).isTrue();
        assertThat(buffer.pin(0).isDriver()).isFalse();
        assertThat(buffer.pin(1).isDriver()).isTrue();
        assertThat(new PinSpec("io", GridPoint.ORIGIN, PinDirection.BIDIRECTIONAL, 1).isInput()).isTrue();
    }

    @Test
    void registryRejectsDuplicatesAndReportsUnknownTypes() {
        assertThatThrownBy(() -> registry.register(buffer)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.require("nope"))
                .isInstanceOf(UnknownComponentTypeException.class)
                .satisfies(exception -> assertThat(((UnknownComponentTypeException) exception).typeId()).isEqualTo("nope"));
        assertThat(registry.find("nope")).isEmpty();
        assertThat(registry.all()).extracting(ComponentType::id)
                .containsExactly(TestComponentTypes.SOURCE, TestComponentTypes.BUFFER,
                        TestComponentTypes.INVERTER, TestComponentTypes.WIDE_PROBE);
    }
}
