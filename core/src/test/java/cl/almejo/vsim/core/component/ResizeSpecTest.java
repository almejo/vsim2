package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
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

    // --- two axes: the Terminal's corner (Task 7b)

    private final ComponentType terminal = StandardComponentTypes.registry().require(StandardComponentTypes.TERMINAL);

    private ResizeSpec corner() {
        return terminal.resize().orElseThrow();
    }

    private ResizeSpec.Axis columns() {
        return corner().axis(ResizeSpec.Edge.RIGHT).orElseThrow();
    }

    private ResizeSpec.Axis rows() {
        return corner().axis(ResizeSpec.Edge.BOTTOM).orElseThrow();
    }

    private static Map<String, Object> withFont(String fontSize) {
        return Map.of(TerminalTypes.FONT_SIZE, fontSize);
    }

    @Test
    void theTerminalGrowsAtItsBottomRightCornerWithColumnsAndRows() {
        assertThat(corner().isCorner()).isTrue();
        assertThat(columns().parameter()).isEqualTo(TerminalTypes.COLUMNS);
        assertThat(rows().parameter()).isEqualTo(TerminalTypes.ROWS);
        assertThat(columns().minimum(terminal)).isEqualTo(1);
        assertThat(columns().maximum(terminal)).isEqualTo(160);
        assertThat(rows().minimum(terminal)).isEqualTo(1);
        assertThat(rows().maximum(terminal)).isEqualTo(60);
        assertThat(spec().isCorner()).as("the Teclado keeps its one axis").isFalse();
        assertThat(spec().axes()).hasSize(1);
    }

    @Test
    void aDraggedCornerSnapsToWholeCellsOfTheFontSize() {
        // Normal: 8 × 16 px cells, 1 × 2 units.
        assertThat(columns().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_NORMAL), -40)).isEqualTo(40);
        assertThat(rows().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_NORMAL), -24)).isEqualTo(12);
        assertThat(columns().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_NORMAL), 0.4)).isEqualTo(80);
        // Pequeña: 6 × 12 px cells, 0.75 × 1.5 units.
        assertThat(columns().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_SMALL), -30)).isEqualTo(40);
        assertThat(columns().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_SMALL), 0.75)).isEqualTo(81);
        assertThat(rows().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_SMALL), -18)).isEqualTo(12);
        // Grande: 10 × 20 px cells, 1.25 × 2.5 units.
        assertThat(columns().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_LARGE), -50)).isEqualTo(40);
        assertThat(rows().valueAfter(terminal, withFont(TerminalTypes.FONT_SIZE_LARGE), -30)).isEqualTo(12);
    }

    @Test
    void theCornerStaysInsideBothRanges() {
        assertThat(columns().valueAfter(terminal, Map.of(), -1000)).isEqualTo(1);
        assertThat(columns().valueAfter(terminal, Map.of(), 1000)).isEqualTo(160);
        assertThat(rows().valueAfter(terminal, Map.of(), -1000)).isEqualTo(1);
        assertThat(rows().valueAfter(terminal, Map.of(), 1000)).isEqualTo(60);
    }

    @Test
    void theOneAxisDragStillLandsOnTheNearestExtent() {
        ResizeSpec.Axis width = spec().axis(ResizeSpec.Edge.RIGHT).orElseThrow();
        assertThat(width.valueAfter(keyboard, Map.of(), 7.6)).isEqualTo(24);
        assertThat(width.valueAfter(keyboard, Map.of(KeyboardTypes.PART_WIDTH, 24), -100)).isEqualTo(16);
        assertThat(spec().axis(ResizeSpec.Edge.BOTTOM)).isEmpty();
    }

    @Test
    void theCornerKnowsBothValuesAndItsDefaults() {
        Map<String, Object> parameters = corner().parametersWith(Map.of(),
                Map.of(TerminalTypes.COLUMNS, 40, TerminalTypes.ROWS, 12));
        assertThat(corner().valuesOf(terminal, parameters))
                .containsExactlyInAnyOrderEntriesOf(Map.of(TerminalTypes.COLUMNS, 40, TerminalTypes.ROWS, 12));
        assertThat(corner().defaults(terminal))
                .containsExactlyInAnyOrderEntriesOf(Map.of(TerminalTypes.COLUMNS, 80, TerminalTypes.ROWS, 24));
        assertThat(spec().defaults(keyboard)).containsExactlyEntriesOf(Map.of(KeyboardTypes.PART_WIDTH, 16));
    }

    @Test
    void twoAxesNeedTwoEdgesAndTwoParameters() {
        assertThatThrownBy(() -> new ResizeSpec(List.of(new ResizeSpec.Axis(TerminalTypes.COLUMNS,
                ResizeSpec.Edge.RIGHT), new ResizeSpec.Axis(TerminalTypes.ROWS, ResizeSpec.Edge.RIGHT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResizeSpec(List.of(new ResizeSpec.Axis(TerminalTypes.COLUMNS,
                ResizeSpec.Edge.RIGHT), new ResizeSpec.Axis(TerminalTypes.COLUMNS, ResizeSpec.Edge.BOTTOM))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResizeSpec(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
