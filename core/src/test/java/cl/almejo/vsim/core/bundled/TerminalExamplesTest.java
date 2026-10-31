package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «Hola, terminal» is wired and explained cleanly, prints its message once and echoes what is typed. */
class TerminalExamplesTest {

    /** The clock of the example: 250 up, 250 down, so a rising edge every 500 units (the first at 250). */
    private static final long PERIOD = 500;
    private static final String MESSAGE = "Hola, terminal\r\nEscribe algo\r\n> ";

    private static String screen(ExampleSimulation simulation) {
        Component terminal = simulation.component("Terminal", StandardComponentTypes.TERMINAL);
        return simulation.engine().terminal(terminal.id().value()).orElseThrow().screen().frame().text().stripTrailing();
    }

    private static ExampleSimulation started() {
        return new ExampleSimulation(TerminalExamples.holaTerminal(), Map.of());
    }

    @Test
    void theExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(TerminalExamples.holaTerminal());
    }

    @Test
    void theRomHoldsTheMessageInItsFirstThirtyTwoWords() {
        assertThat(TerminalExamples.MESSAGE).isEqualTo(MESSAGE).hasSize(32);
        assertThat(TerminalExamples.messageContents().word(0)).isEqualTo('H');
        assertThat(TerminalExamples.messageContents().word(31)).isEqualTo(' ');
        assertThat(TerminalExamples.messageContents().word(32)).isZero();
    }

    @Test
    void theMessageAppearsOneCharacterPerRisingEdge() {
        ExampleSimulation simulation = started();
        simulation.runUntil(PERIOD / 2 + 100);
        assertThat(screen(simulation)).startsWith("H");
        simulation.runUntil(PERIOD * 4 + PERIOD / 2 + 100);
        assertThat(screen(simulation)).startsWith("Hola,");
    }

    @Test
    void theWholeMessageIsPrintedOnceAndThenTheCounterStops() {
        ExampleSimulation simulation = started();
        simulation.runUntil(PERIOD * 40);
        assertThat(screen(simulation)).isEqualTo("Hola, terminal\nEscribe algo\n>");
        simulation.runUntil(PERIOD * 400);
        assertThat(screen(simulation)).as("it does not print again").isEqualTo("Hola, terminal\nEscribe algo\n>");
    }

    @Test
    void whatIsTypedIsEchoedAfterTheMessage() {
        ExampleSimulation simulation = started();
        simulation.runUntil(PERIOD * 40);
        Component keyboard = simulation.component("Teclado", StandardComponentTypes.KEYBOARD);
        simulation.engine().typeBytes(keyboard.id().value(), "D".getBytes(StandardCharsets.UTF_8));
        simulation.runUntil(PERIOD * 44);
        assertThat(screen(simulation)).isEqualTo("Hola, terminal\nEscribe algo\n> D");
        simulation.engine().typeBytes(keyboard.id().value(), "AV\r".getBytes(StandardCharsets.UTF_8));
        simulation.runUntil(PERIOD * 52);
        assertThat(screen(simulation)).isEqualTo("Hola, terminal\nEscribe algo\n> DAV");
        assertThat(simulation.engine().typingQueueSize(keyboard.id().value())).hasValue(0);
    }

    @Test
    void aKeyTypedWhileTheMessageIsPrintingWaitsInTheQueueAndIsEchoedAfterIt() {
        ExampleSimulation simulation = started();
        Component keyboard = simulation.component("Teclado", StandardComponentTypes.KEYBOARD);
        simulation.runUntil(PERIOD * 5);
        simulation.engine().typeBytes(keyboard.id().value(), "X".getBytes(StandardCharsets.UTF_8));
        simulation.runUntil(PERIOD * 20);
        assertThat(screen(simulation)).doesNotContain("X");
        assertThat(simulation.engine().typingQueueSize(keyboard.id().value())).hasValue(1);
        simulation.runUntil(PERIOD * 44);
        assertThat(screen(simulation)).isEqualTo("Hola, terminal\nEscribe algo\n> X");
    }
}
