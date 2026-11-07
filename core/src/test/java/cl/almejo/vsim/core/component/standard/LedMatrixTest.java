package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Matriz LED: its parameters and pins, «Por filas» and «Barrido» (the row stays lit until the scan returns). */
class LedMatrixTest {

    private static final String MATRIX = "led-matrix";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType matrixType = registry.require(MATRIX);
    private final ComponentType switchType = registry.require(StandardComponentTypes.SWITCH);
    private final SimulationEngine engine = new SimulationEngine();

    private static Map<String, Object> scan(int rows, int columns) {
        return Map.of("rows", rows, "columns", columns, "input", "scan");
    }

    /** A matrix with one Switch per pin, each holding the text given (pin order); a {@code null} leaves it unwired. */
    private FlatCircuit circuit(Map<String, Object> parameters, String... switchValues) {
        ComponentType matrix = matrixType.specializedFor(parameters);
        List<FlatComponent> components = new ArrayList<>();
        List<FlatNet> nets = new ArrayList<>();
        components.add(new FlatComponent("m", matrix, parameters));
        for (int pin = 0; pin < switchValues.length; pin++) {
            if (switchValues[pin] == null) {
                continue;
            }
            int width = matrix.pin(pin).width();
            Map<String, Object> switchParameters = Map.of("width", width, "value", switchValues[pin]);
            components.add(new FlatComponent("s" + pin, switchType.specializedFor(switchParameters),
                    switchParameters));
            nets.add(new FlatNet("n" + pin, List.of(new FlatPinRef("m", pin), new FlatPinRef("s" + pin, 0)), width,
                    false));
        }
        return new FlatCircuit(components, nets);
    }

    private List<Value> shown() {
        return ((VisibleState.LedMatrixState) engine.snapshot().componentState("m")).rows();
    }

    private ParameterSpec parameter(String name) {
        return matrixType.parameters().stream().filter(spec -> spec.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theDefaultsAreAnEightByEightByRowsInRound() {
        assertThat(matrixType.resolveParameters(Map.of())).isEqualTo(Map.of("rows", 8, "columns", 8,
                "input", "rows", "style", "round", "color", "red", "show-unlit", true, "afterglow", false));
        assertThat(matrixType.category().name()).isEqualTo("INPUT_OUTPUT");
    }

    @Test
    void theLimitsAreThirtyTwoRowsAndSixtyFourColumns() {
        assertThat(parameter("rows").minimum()).isEqualTo(1);
        assertThat(parameter("rows").maximum()).isEqualTo(32);
        assertThat(parameter("columns").minimum()).isEqualTo(1);
        assertThat(parameter("columns").maximum()).isEqualTo(64);
        assertThatThrownBy(() -> matrixType.resolveParameters(Map.of("rows", 33)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLookParametersAreDisplayOnlySoChangingThemKeepsTheRunningMatrix() {
        for (String name : List.of("style", "color", "show-unlit", "afterglow")) {
            assertThat(parameter(name).displayOnly()).as(name).isTrue();
        }
        for (String name : List.of("rows", "columns", "input")) {
            assertThat(parameter(name).displayOnly()).as(name).isFalse();
        }
    }

    @Test
    void byRowsHasOneInputPerRowOnTheLeftAsWideAsTheColumns() {
        ComponentType matrix = matrixType.specializedFor(Map.of("rows", 4, "columns", 6));
        assertThat(matrix.size()).isEqualTo(new GridSize(8, 5));
        assertThat(matrix.pinCount()).isEqualTo(4);
        for (int row = 0; row < 4; row++) {
            assertThat(matrix.pin(row).name()).isEqualTo("F" + row);
            assertThat(matrix.pin(row).position()).isEqualTo(new GridPoint(0, row + 1));
            assertThat(matrix.pin(row).width()).isEqualTo(6);
            assertThat(matrix.pin(row).direction()).isEqualTo(PinDirection.INPUT);
        }
    }

    @Test
    void scanHasRowDataAndEnablePinsWithTheRowAsWideAsTheRowCountNeeds() {
        assertThat(matrixType.specializedFor(scan(8, 32)).pin(0).width()).isEqualTo(3);
        assertThat(matrixType.specializedFor(scan(9, 8)).pin(0).width()).isEqualTo(4);
        assertThat(matrixType.specializedFor(scan(32, 8)).pin(0).width()).isEqualTo(5);
        assertThat(matrixType.specializedFor(scan(1, 8)).pin(0).width()).as("never less than one bit").isEqualTo(1);
        ComponentType matrix = matrixType.specializedFor(scan(8, 32));
        assertThat(matrix.pinCount()).isEqualTo(3);
        assertThat(matrix.pin(0).name()).isEqualTo("Fila");
        assertThat(matrix.pin(1).name()).isEqualTo("Datos");
        assertThat(matrix.pin(1).width()).isEqualTo(32);
        assertThat(matrix.pin(2).name()).isEqualTo("Hab.");
        assertThat(matrix.pin(2).width()).isEqualTo(1);
    }

    @Test
    void aSmallScanMatrixIsTallEnoughForItsThreePins() {
        ComponentType matrix = matrixType.specializedFor(scan(2, 4));
        assertThat(matrix.size().height()).isGreaterThan(matrix.pin(2).position().y());
    }

    @Test
    void byRowsEachRowShowsItsInputWithTheMostSignificantBitOnTheLeft() {
        Map<String, Object> parameters = Map.of("rows", 2, "columns", 4);
        engine.update(circuit(parameters, "0b1000", "0b0011"));
        assertThat(shown()).containsExactly(Value.of(4, 0b1000), Value.of(4, 0b0011));
    }

    @Test
    void byRowsAnUnwiredRowIsUnknownAndANewValueShowsAtOnce() {
        Map<String, Object> parameters = Map.of("rows", 2, "columns", 4);
        engine.update(circuit(parameters, "0b0001", null));
        assertThat(shown().get(1)).isEqualTo(Value.highImpedance(4));
        engine.update(circuit(parameters, "0b0110", null));
        assertThat(shown().get(0)).isEqualTo(Value.of(4, 0b0110));
    }

    @Test
    void scanLatchesTheRowWhileEnabledAndKeepsItWhenTheScanMovesOn() {
        Map<String, Object> parameters = scan(4, 4);
        engine.update(circuit(parameters, "2", "0b1010", "1"));
        assertThat(shown()).containsExactly(Value.of(4, 0), Value.of(4, 0), Value.of(4, 0b1010), Value.of(4, 0));
        engine.update(circuit(parameters, "3", "0b0101", "1"));
        assertThat(shown()).containsExactly(Value.of(4, 0), Value.of(4, 0), Value.of(4, 0b1010),
                Value.of(4, 0b0101));
    }

    @Test
    void scanOverwritesARowOnlyWhenTheScanPassesItAgain() {
        Map<String, Object> parameters = scan(4, 4);
        engine.update(circuit(parameters, "1", "0b1111", "1"));
        engine.update(circuit(parameters, "1", "0b0001", "1"));
        assertThat(shown().get(1)).isEqualTo(Value.of(4, 0b0001));
    }

    /**
     * Review M9: when the data path is faster than the row path, the next row's data reaches «Datos» a moment before
     * «Fila» moves on. A real panel shows that only as a faint ghost, because the row is lit with it for a moment; the
     * row keeps the data it held longest while the scan was on it.
     */
    @Test
    void aRowKeepsTheDataItHeldLongestWhileTheScanWasOnItNotTheNextRowsDataArrivingFirst() {
        engine.update(circuit(scan(4, 4), "0", "0b1010", "1"));
        engine.advance(8);
        engine.setInput("s1", Value.of(4, 0b0101));
        engine.advance(2);
        engine.setInput("s0", Value.of(2, 1));
        engine.advance(8);

        assertThat(shown().get(0)).isEqualTo(Value.of(4, 0b1010));
        assertThat(shown().get(1)).isEqualTo(Value.of(4, 0b0101));
    }

    @Test
    void aRowTheScanStaysOnShowsNewDataAtOnce() {
        engine.update(circuit(scan(4, 4), "0", "0b1010", "1"));
        engine.advance(50);
        engine.setInput("s1", Value.of(4, 0b0101));
        engine.advance(1);

        assertThat(shown().get(0)).isEqualTo(Value.of(4, 0b0101));
    }

    @Test
    void scanDoesNotLatchWhileTheEnableIsZeroOrUnwired() {
        Map<String, Object> parameters = scan(4, 4);
        engine.update(circuit(parameters, "1", "0b1111", "0"));
        assertThat(shown()).allMatch(row -> row.equals(Value.of(4, 0)));
        engine.update(circuit(parameters, "1", "0b1111", null));
        assertThat(shown()).allMatch(row -> row.equals(Value.of(4, 0)));
    }

    @Test
    void scanDoesNotLatchWhenTheRowIsUnknownOrOutOfRange() {
        Map<String, Object> parameters = scan(5, 4);
        engine.update(circuit(parameters, null, "0b1111", "1"));
        assertThat(shown()).as("row unwired (Z)").allMatch(row -> row.equals(Value.of(4, 0)));
        engine.update(circuit(parameters, "6", "0b1111", "1"));
        assertThat(shown()).as("row 6 of 5").allMatch(row -> row.equals(Value.of(4, 0)));
        engine.update(circuit(parameters, "4", "0b1111", "1"));
        assertThat(shown().get(4)).as("the last row is in range").isEqualTo(Value.of(4, 0b1111));
    }

    @Test
    void scanKeepsTheUnknownBitsOfTheDataAsUnknown() {
        Map<String, Object> parameters = scan(2, 4);
        engine.update(circuit(parameters, "0", null, "1"));
        assertThat(shown().get(0)).isEqualTo(Value.highImpedance(4));
        assertThat(shown().get(1)).isEqualTo(Value.of(4, 0));
    }

    @Test
    void aDefaultMatrixWritesNoParametersAndOthersKeepTheirs() throws Exception {
        CircuitFormat format = new CircuitFormat(registry);
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), MATRIX, new GridPoint(0, 0), Rotation.R0, Map.of(), ""));
        assertThat(format.write(document)).doesNotContain("\"rows\"").doesNotContain("\"input\"")
                .doesNotContain("\"style\"").doesNotContain("\"show-unlit\"");

        Map<String, Object> custom = Map.of("rows", 16, "columns", 32, "input", "scan", "style", "sign",
                "color", "green", "show-unlit", false, "afterglow", true);
        CircuitDocument other = new CircuitDocument();
        other.add(new Component(other.newComponentId(), MATRIX, new GridPoint(0, 0), Rotation.R0, custom, ""));

        CircuitDocument loaded = format.read(format.write(other));

        assertThat(loaded.components().getFirst().parameters()).isEqualTo(custom);
    }
}
