package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.ADDER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.AND;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_JOINER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The example checks catch the defects of the audited hand-drawn examples, and pass a clean circuit. */
class ExampleChecksTest {

    private static final Map<String, Object> OFF = Map.of("value", "0");

    /** A switch feeding an LED standing two rows above the end of its wire, titled and explained. */
    private static CircuitBuilder clean() {
        CircuitBuilder builder = new CircuitBuilder("Prueba limpia");
        Component input = builder.placeByPin(SWITCH, new GridPoint(6, 6), 0, Rotation.R0, OFF, "A");
        builder.route(new GridPoint(6, 6), new GridPoint(14, 6), new GridPoint(14, 4));
        Component led = builder.placeByPin(LED, new GridPoint(14, 4), 0, Rotation.R0, Map.of(), "Y");
        builder.link(input, 0, led, 0);
        builder.text(2, 0, "Prueba limpia");
        builder.text(2, 9, "Prueba: cambia A");
        return builder;
    }

    @Test
    void aCleanCircuitHasNoProblems() {
        assertThat(ExampleChecks.wiringProblems(clean())).isEmpty();
        assertThat(ExampleChecks.explanationProblems(clean())).isEmpty();
        ExampleChecks.assertExample(clean());
    }

    @Test
    void aWireOverTheNextSwitchsPinShortsTheTwoSwitches() {
        // the audit's sumador de 4 bits: one straight wire from A to the gate passes over B's output pin
        CircuitBuilder builder = new CircuitBuilder("corto");
        Component first = builder.placeByPin(SWITCH, new GridPoint(6, 4), 0, Rotation.R0, OFF, "A");
        Component second = builder.placeByPin(SWITCH, new GridPoint(14, 4), 0, Rotation.R0, OFF, "B");
        Component gate = builder.placeByBox(AND, 20, 4, Rotation.R0, Map.of(), "");
        builder.route(new GridPoint(6, 4), new GridPoint(20, 4));
        builder.link(first, 0, gate, 0);
        builder.link(second, 0, gate, 1);

        assertThat(ExampleChecks.wiringProblems(builder))
                .anyMatch(problem -> problem.startsWith("wire w") && problem.contains("runs over pin 0 of switch"))
                .anyMatch(problem -> problem.startsWith("short circuit: 2 drivers"))
                .anyMatch(problem -> problem.startsWith("unintended net"))
                .anyMatch(problem -> problem.startsWith("intended net missing"))
                .anyMatch(problem -> problem.startsWith("unconnected input"));
    }

    @Test
    void gatesStackedFourRowsApartPutAPinOnAPin() {
        CircuitBuilder builder = new CircuitBuilder("pines");
        builder.placeByBox(AND, 10, 4, Rotation.R0, Map.of(), "");
        builder.placeByBox(AND, 10, 8, Rotation.R0, Map.of(), "");
        assertThat(ExampleChecks.wiringProblems(builder))
                .anyMatch(problem -> problem.endsWith("share GridPoint[x=10, y=8]"));
    }

    @Test
    void aBusIntoAOneBitLedIsAWidthMismatch() {
        CircuitBuilder builder = new CircuitBuilder("ancho");
        Component joiner = builder.placeByBox(BUS_JOINER, 10, 4, Rotation.R0, Map.of(WIDTH, 2), "");
        Component led = builder.placeByPin(LED, new GridPoint(16, 2), 0, Rotation.R0, Map.of(), "Y");
        builder.route(new GridPoint(12, 4), new GridPoint(16, 4), new GridPoint(16, 2));
        builder.link(joiner, 0, led, 0);
        assertThat(ExampleChecks.wiringProblems(builder)).anyMatch(problem -> problem.startsWith("width mismatch"));
    }

    @Test
    void aWireEndingOnNothingAndAWireThroughABodyAreReported() {
        CircuitBuilder builder = clean();
        builder.route(new GridPoint(14, 6), new GridPoint(20, 6));
        builder.route(new GridPoint(13, 2), new GridPoint(15, 2));
        assertThat(ExampleChecks.wiringProblems(builder))
                .anyMatch(problem -> problem.contains("on nothing"))
                .anyMatch(problem -> problem.contains("runs through the body of led"));
    }

    @Test
    void overlappingWiresAreReported() {
        CircuitBuilder builder = clean();
        builder.route(new GridPoint(8, 6), new GridPoint(12, 6));
        assertThat(ExampleChecks.wiringProblems(builder)).anyMatch(problem -> problem.endsWith(" overlap"));
    }

    @Test
    void aTextOverAWireOrAnotherTextIsReported() {
        CircuitBuilder builder = clean();
        builder.text(8, 5, "acarreo");
        builder.text(2, 10, "otra línea");
        assertThat(ExampleChecks.wiringProblems(builder))
                .anyMatch(problem -> problem.startsWith("text «acarreo» covers wire"))
                .anyMatch(problem -> problem.contains("covers text «otra línea»"));
    }

    @Test
    void anExampleNeedsATitleAPruebaLineAndNamedInputsAndOutputs() {
        CircuitBuilder builder = new CircuitBuilder("Sin explicar");
        Component input = builder.placeByPin(SWITCH, new GridPoint(6, 6), 0, Rotation.R0, OFF, "");
        builder.route(new GridPoint(6, 6), new GridPoint(14, 6), new GridPoint(14, 4));
        Component led = builder.placeByPin(LED, new GridPoint(14, 4), 0, Rotation.R0, Map.of(), "Y");
        builder.link(input, 0, led, 0);

        assertThat(ExampleChecks.explanationProblems(builder)).containsExactlyInAnyOrder(
                "no title: a free text equal to «Sin explicar»", "no «Prueba: …» text",
                "switch " + input.id().value() + " has no label");
        assertThatThrownBy(() -> ExampleChecks.assertExample(builder)).isInstanceOf(AssertionError.class);
    }

    @Test
    void aBlockNeedsALabelToo() {
        CircuitBuilder builder = clean();
        Component adder = builder.placeByBox(ADDER, 30, 10, Rotation.R0, Map.of(), "");
        assertThat(ExampleChecks.explanationProblems(builder))
                .containsExactly("adder " + adder.id().value() + " has no label");
    }

    @Test
    void aBlockControlInputWithARestValueMayStayUnconnectedButADataInputMayNot() {
        CircuitBuilder builder = clean();
        builder.placeByBox(ADDER, 30, 10, Rotation.R0, Map.of(), "Suma");
        assertThat(ExampleChecks.wiringProblems(builder))
                .as("A and B are data; Cin rests at 0")
                .hasSize(2)
                .allMatch(problem -> problem.startsWith("unconnected input on [adder"))
                .noneMatch(problem -> problem.endsWith("#2]"));
    }
}
