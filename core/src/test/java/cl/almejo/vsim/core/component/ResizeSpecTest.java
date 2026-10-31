package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A part whose size comes from a parameter: which one, which edge grows, and the value a dragged edge asks for. */
class ResizeSpecTest {

    private final ComponentType keyboard = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);

    private ResizeSpec spec() {
        return keyboard.resize().orElseThrow();
    }

    @Test
    void theTecladoGrowsToTheRightWithItsPartWidth() {
        assertThat(spec()).isEqualTo(new ResizeSpec(KeyboardTypes.PART_WIDTH, ResizeSpec.Edge.RIGHT));
        assertThat(spec().minimum(keyboard)).isEqualTo(16);
        assertThat(spec().maximum(keyboard)).isEqualTo(64);
    }

    @Test
    void anEdgeDraggedToAnExtentTakesTheNearestWholeValue() {
        assertThat(spec().valueFor(keyboard, Map.of(), 23.6)).isEqualTo(24);
        assertThat(spec().valueFor(keyboard, Map.of(), 24.4)).isEqualTo(24);
        assertThat(spec().extentOf(keyboard, Map.of(KeyboardTypes.PART_WIDTH, 24))).isEqualTo(24);
        assertThat(spec().valueOf(keyboard, Map.of(KeyboardTypes.PART_WIDTH, 24))).isEqualTo(24);
    }

    @Test
    void theValueStaysInsideTheParameterRange() {
        assertThat(spec().valueFor(keyboard, Map.of(), 3)).isEqualTo(16);
        assertThat(spec().valueFor(keyboard, Map.of(), 400)).isEqualTo(64);
    }

    @Test
    void otherPartsDoNotResize() {
        assertThat(StandardComponentTypes.registry().require(StandardComponentTypes.BUFFER).resize()).isEmpty();
    }

    @Test
    void onlyAnIntegerParameterOfTheTypeCanDriveTheSize() {
        ComponentType plain = StandardComponentTypes.registry().require(StandardComponentTypes.BUFFER);
        assertThatThrownBy(() -> plain.withResize(new ResizeSpec("no-such-parameter", ResizeSpec.Edge.RIGHT)))
                .isInstanceOf(IllegalArgumentException.class);
        ComponentType withChoice = new ComponentType("test-choice", ComponentCategory.INPUT_OUTPUT,
                new GridSize(2, 2), List.of(), List.of(ParameterSpec.choice("look", "a", List.of("a", "b"))),
                parameters -> null);
        assertThatThrownBy(() -> withChoice.withResize(new ResizeSpec("look", ResizeSpec.Edge.RIGHT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSpecializedTypeKeepsItsResize() {
        assertThat(keyboard.specializedFor(Map.of(KeyboardTypes.PART_WIDTH, 30)).resize())
                .isEqualTo(keyboard.resize());
    }
}
