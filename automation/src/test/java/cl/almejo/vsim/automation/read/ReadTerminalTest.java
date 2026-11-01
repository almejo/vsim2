package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.ReadHost;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.TerminalReading;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code vsim_read_terminal}: what a byte stream shows on the simulated screen, described by the circuit's part. */
class ReadTerminalTest {

    private static final int ROWS = 3;
    private static final int COLUMNS = 10;

    private final ReadFixture fixture = new ReadFixture();
    private TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(ROWS, COLUMNS));
    private boolean simulating = true;
    private int askedHistory = -1;
    private int askedBytes = -1;

    private final ReadHost host = new ReadHost() {
        @Override
        public <T> CompletableFuture<T> reading(DocumentId document, Function<ReadSource, T> work) {
            return CompletableFuture.completedFuture(work.apply(fixture.source()));
        }

        @Override
        public CompletableFuture<Optional<TerminalReading>> currentTerminal(DocumentId document, String component,
                                                                           int historyLines, int byteCount) {
            askedHistory = historyLines;
            askedBytes = byteCount;
            return CompletableFuture.completedFuture(simulating
                    ? Optional.of(TerminalReading.of(screen, historyLines, byteCount, 99)) : Optional.empty());
        }
    };
    private final Reads reads = new Reads(host, Duration.ofSeconds(5));

    private Component terminal(Map<String, Object> parameters) {
        Map<String, Object> all = new java.util.HashMap<>(parameters);
        all.putIfAbsent(TerminalTypes.ROWS, ROWS);
        all.putIfAbsent(TerminalTypes.COLUMNS, COLUMNS);
        return fixture.place(StandardComponentTypes.TERMINAL, 0, 0, Rotation.R0, all, "tty");
    }

    private void write(String text) {
        long time = 0;
        for (char character : text.toCharArray()) {
            screen.write(character, time++);
        }
    }

    @Test
    void theRowsTheCursorTheSizeAndTheModeShowWhatTheBytesWrote() {
        Component part = terminal(Map.of());
        write("hola\r\nmundo");

        TerminalView view = reads.readTerminal(ReadFixture.ID, part.id().value(), 0, 0);

        assertThat(view.component()).isEqualTo(part.id().value());
        assertThat(view.rows()).isEqualTo(ROWS);
        assertThat(view.columns()).isEqualTo(COLUMNS);
        assertThat(view.screen()).containsExactly("hola", "mundo", "");
        assertThat(view.cursorRow()).isEqualTo(1);
        assertThat(view.cursorColumn()).isEqualTo(5);
        assertThat(view.mode()).isEqualTo("VT");
        assertThat(view.profile()).isEqualTo(TerminalTypes.PROFILE_VT);
        assertThat(view.history()).isEmpty();
        assertThat(view.bytes()).isEmpty();
        assertThat(view.time()).isEqualTo(99);
    }

    @Test
    void theHistoryAndTheLastBytesAreWhatWasAskedFor() {
        Component part = terminal(Map.of());
        write("a\r\nb\r\nc\r\nd");

        TerminalView view = reads.readTerminal(ReadFixture.ID, part.id().value(), 5, 3);

        assertThat(askedHistory).isEqualTo(5);
        assertThat(askedBytes).isEqualTo(3);
        assertThat(view.history()).containsExactly("a");
        assertThat(view.bytes()).hasSize(3);
        TerminalView.ByteView last = view.bytes().getLast();
        assertThat(last.hex()).isEqualTo("64");
        assertThat(last.character()).isEqualTo("d");
        assertThat(last.time()).isEqualTo(9);
        assertThat(view.bytes().getFirst().hex()).isEqualTo("0D");
        assertThat(view.bytes().getFirst().character()).isEqualTo("\\r");
    }

    @Test
    void theExpectedOutputIsComparedWithTheTranscript() {
        Component part = terminal(Map.of(TerminalTypes.EXPECTED, TerminalTypes.EXPECTED_WRITTEN,
                TerminalTypes.EXPECTED_TEXT, "hola"));
        write("hol");
        assertThat(reads.readTerminal(ReadFixture.ID, part.id().value(), 0, 0).expected()).contains("differs");

        write("a");
        assertThat(reads.readTerminal(ReadFixture.ID, part.id().value(), 0, 0).expected()).contains("matches");

        Component plain = terminal(Map.of());
        assertThat(reads.readTerminal(ReadFixture.ID, plain.id().value(), 0, 0).expected()).isEmpty();
    }

    @Test
    void aBigScreenWithAllItsHistoryStaysWithinTheTextBudgetDroppingTheOldestLines() {
        screen = new TerminalScreen(TerminalSettings.vt(60, 160));
        Component part = terminal(Map.of(TerminalTypes.ROWS, 60, TerminalTypes.COLUMNS, 160));
        for (int line = 0; line < TerminalReader.MAXIMUM_HISTORY + 60; line++) {
            write(String.format("%04d", line) + "x".repeat(150) + "\r\n");
        }

        TerminalView view = reads.readTerminal(ReadFixture.ID, part.id().value(), TerminalReader.MAXIMUM_HISTORY, 0);

        int characters = view.screen().stream().mapToInt(String::length).sum()
                + view.history().stream().mapToInt(String::length).sum();
        assertThat(characters).isLessThanOrEqualTo(TerminalReader.MAXIMUM_TEXT_CHARACTERS);
        assertThat(view.historyTrimmed()).isPositive()
                .isEqualTo(TerminalReader.MAXIMUM_HISTORY - view.history().size());
        assertThat(view.history().getLast()).as("the newest lines are kept").startsWith(String.format("%04d",
                TerminalReader.MAXIMUM_HISTORY));
    }

    @Test
    void theCountsAreCappedAndTheWrongPartIsRefused() {
        Component part = terminal(Map.of());
        Component gate = fixture.place("and", 30, 30);

        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, part.id().value(), 1001, 0),
                AutomationError.INVALID_PARAMETER);
        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, part.id().value(), 0, 257),
                AutomationError.INVALID_PARAMETER);
        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, part.id().value(), -1, 0),
                AutomationError.INVALID_PARAMETER);
        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, gate.id().value(), 0, 0),
                AutomationError.INVALID_REFERENCE);
        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, "c999", 0, 0), AutomationError.INVALID_REFERENCE);
        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, "c1/c2", 0, 0), AutomationError.INVALID_REFERENCE);
    }

    @Test
    void withoutASimulationTheReadIsRefused() {
        Component part = terminal(Map.of());
        simulating = false;

        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, part.id().value(), 0, 0), AutomationError.NOT_ALLOWED);
    }

    @Test
    void aPartIsAlsoFoundByItsLabel() {
        terminal(Map.of());
        write("x");

        assertThat(reads.readTerminal(ReadFixture.ID, "tty", 0, 0).screen().getFirst()).isEqualTo("x");
    }

    @Test
    void aKeyboardIsFoundWithItsEncodingAndAnythingElseIsRefused() {
        Component keyboard = fixture.place(StandardComponentTypes.KEYBOARD, 0, 20, Rotation.R0,
                Map.of(KeyEncoding.ENCODING, KeyEncoding.LATIN1_VALUE), "teclas");
        Component gate = fixture.place("and", 30, 30);

        assertThat(reads.keyboardOf(ReadFixture.ID, keyboard.id().value()).encoding().encode(0xE9))
                .containsExactly((byte) 0xE9);
        assertThat(reads.keyboardOf(ReadFixture.ID, "teclas").component()).isEqualTo(keyboard.id().value());
        assertInvalid(() -> reads.keyboardOf(ReadFixture.ID, gate.id().value()), AutomationError.INVALID_REFERENCE);
    }

    @Test
    void anIntegratedUartIsReadAndTypedIntoLikeATerminalAndATeclado() {
        Component uart = fixture.place(StandardComponentTypes.UART_16550, 0, 40, Rotation.R0,
                Map.of(TerminalTypes.ROWS, ROWS, TerminalTypes.COLUMNS, COLUMNS), "uart0");
        write("hola");

        TerminalView view = reads.readTerminal(ReadFixture.ID, "uart0", 0, 0);

        assertThat(view.component()).isEqualTo(uart.id().value());
        assertThat(view.screen().getFirst()).isEqualTo("hola");
        assertThat(view.mode()).isEqualTo("VT");
        assertThat(reads.keyboardOf(ReadFixture.ID, "uart0").component()).isEqualTo(uart.id().value());
    }

    @Test
    void anIntegratedUartsExpectedOutputIsComparedLikeATerminals() {
        fixture.place(StandardComponentTypes.UART_16550, 0, 40, Rotation.R0,
                Map.of(TerminalTypes.ROWS, ROWS, TerminalTypes.COLUMNS, COLUMNS, TerminalTypes.EXPECTED,
                        TerminalTypes.EXPECTED_WRITTEN, TerminalTypes.EXPECTED_TEXT, "hola"), "uart0");
        write("hol");
        assertThat(reads.readTerminal(ReadFixture.ID, "uart0", 0, 0).expected()).contains("differs");

        write("a");
        assertThat(reads.readTerminal(ReadFixture.ID, "uart0", 0, 0).expected()).contains("matches");
    }

    @Test
    void aUartWithTxAndRxPinsHasNoScreenAndNoKeyboard() {
        Component uart = fixture.place(StandardComponentTypes.UART_16550, 0, 40, Rotation.R0,
                Map.of(UartTypes.SCREEN, UartTypes.SCREEN_PINS), "uart0");

        assertInvalid(() -> reads.readTerminal(ReadFixture.ID, uart.id().value(), 0, 0),
                AutomationError.INVALID_REFERENCE);
        assertInvalid(() -> reads.keyboardOf(ReadFixture.ID, uart.id().value()), AutomationError.INVALID_REFERENCE);
    }

    private static void assertInvalid(Runnable call, AutomationError error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(error));
    }
}
