package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.UartState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The UART 16550 (spec §12) driven through its bus as a CPU would: the QEMU {@code virt} polling driver
 * ({@code while (!(LSR & 0x20)); THR = c;} and {@code while (!(LSR & 1)); c = RBR;}), DLAB, the FIFO, the interrupts,
 * the third state on Q, the register stride and the TX/RX pins.
 */
class UartTypesTest {

    private static final int A = UartPinout.ADDRESS;
    private static final int D = UartPinout.DATA;
    private static final int CS = UartPinout.CHIP_SELECT;
    private static final int RD = UartPinout.READ;
    private static final int WR = UartPinout.WRITE;
    private static final int CLK = UartPinout.CLOCK;
    private static final int CLR = UartPinout.CLEAR;
    private static final int Q = UartPinout.OUTPUT;
    private static final int IRQ = UartPinout.INTERRUPT;
    private static final int RX = UartPinout.RECEIVE;
    private static final int RXV = UartPinout.RECEIVE_VALID;
    private static final int TX = UartPinout.TRANSMIT;
    private static final int TXV = UartPinout.TRANSMIT_VALID;
    private static final int RXR = UartPinout.RECEIVER_READY;

    private static final int RBR_THR = 0;
    private static final int IER = 1;
    private static final int IIR_FCR = 2;
    private static final int LCR = 3;
    private static final int LSR = 5;
    private static final int SCR = 7;
    private static final int DATA_READY = 0x01;
    private static final int OVERRUN = 0x02;
    private static final int TRANSMITTER_EMPTY = 0x20;
    /** Polling never needs this many reads: the UART sends and receives at once. */
    private static final int POLL_LIMIT = 8;

    private static final ComponentType UART = StandardComponentTypes.registry()
            .require(StandardComponentTypes.UART_16550);

    private static Map<String, Object> parameters(Map<String, Object> extra) {
        Map<String, Object> parameters = new HashMap<>(UART.resolveParameters(Map.of(TerminalTypes.ROWS, 4,
                TerminalTypes.COLUMNS, 20)));
        parameters.putAll(extra);
        return parameters;
    }

    private static BlockHarness integrated() {
        return new BlockHarness(StandardComponentTypes.UART_16550, parameters(Map.of())).set(CS, 1).settle();
    }

    private static BlockHarness withPins(Map<String, Object> extra) {
        Map<String, Object> pins = new HashMap<>(extra);
        pins.put(UartTypes.SCREEN, UartTypes.SCREEN_PINS);
        return new BlockHarness(StandardComponentTypes.UART_16550, parameters(pins)).set(CS, 1).settle();
    }

    private static void clockOnce(BlockHarness harness) {
        harness.set(CLK, 1).settle().set(CLK, 0).settle();
    }

    /** A CPU store: the register's address and the byte, WR for one clock edge. */
    private static void write(BlockHarness harness, int register, int value) {
        harness.set(A, register).set(D, value).set(WR, 1).settle();
        clockOnce(harness);
        harness.set(WR, 0).settle();
    }

    /** A CPU load: Q is sampled before the edge that ends the read (and pops RBR, clears flags). */
    private static int read(BlockHarness harness, int register) {
        harness.set(A, register).set(RD, 1).settle();
        int value = (int) harness.output(Q).bits();
        clockOnce(harness);
        harness.set(RD, 0).settle();
        return value;
    }

    /** {@code while (!(LSR & 0x20)); THR = c;} for every character of {@code text}. */
    private static void print(BlockHarness harness, String text) {
        for (char character : text.toCharArray()) {
            int polls = 0;
            while ((read(harness, LSR) & TRANSMITTER_EMPTY) == 0) {
                assertThat(++polls).isLessThan(POLL_LIMIT);
            }
            write(harness, RBR_THR, character);
        }
    }

    /** {@code while (!(LSR & 1)); c = RBR;} */
    private static int receive(BlockHarness harness) {
        int polls = 0;
        while ((read(harness, LSR) & DATA_READY) == 0) {
            assertThat(++polls).isLessThan(POLL_LIMIT);
        }
        return read(harness, RBR_THR);
    }

    /** The QEMU {@code virt} driver's set-up: divisor through DLAB, 8N1, FIFOs on and cleared, no interrupts. */
    private static void initialize(BlockHarness harness) {
        write(harness, LCR, 0x80);
        write(harness, 0, 0x01);
        write(harness, 1, 0x00);
        write(harness, LCR, 0x03);
        write(harness, IIR_FCR, 0x07);
        write(harness, IER, 0x00);
    }

    private static UartState state(BlockHarness harness) {
        return (UartState) harness.state();
    }

    private static String screenRow(BlockHarness harness, int row) {
        return state(harness).screen().orElseThrow().frame().rowText(row);
    }

    @Test
    void itIsAnInputOutputPartRightAfterTheKeyboard() {
        List<String> inputOutput = StandardComponentTypes.registry().all().stream()
                .filter(type -> type.category() == ComponentCategory.INPUT_OUTPUT).map(ComponentType::id).toList();

        assertThat(inputOutput.indexOf(StandardComponentTypes.UART_16550))
                .isEqualTo(inputOutput.indexOf(StandardComponentTypes.KEYBOARD) + 1);
    }

    @Test
    void itsBusPinsAreTheSpecsAndThePinsModeAddsTxAndRx() {
        assertThat(UART.pins()).extracting(PinSpec::name)
                .containsExactly("A", "D", "CS", "RD", "WR", "CLK", "CLR", "Q", "IRQ");
        assertThat(UART.pin(A).width()).isEqualTo(3);
        assertThat(UART.pin(D).width()).isEqualTo(8);
        assertThat(UART.pin(Q).width()).isEqualTo(8);
        assertThat(UART.pin(Q).isInput()).isFalse();
        assertThat(UART.pin(IRQ).isInput()).isFalse();

        ComponentType pins = UART.specializedFor(Map.of(UartTypes.SCREEN, UartTypes.SCREEN_PINS));

        assertThat(pins.pins()).extracting(PinSpec::name)
                .containsExactly("A", "D", "CS", "RD", "WR", "CLK", "CLR", "Q", "IRQ", "RX", "RXV", "TX", "TXV",
                        "RXR");
        assertThat(pins.pin(RX).width()).isEqualTo(8);
        assertThat(pins.pin(TX).width()).isEqualTo(8);
        assertThat(pins.pin(TX).isInput()).isFalse();
        assertThat(pins.pin(TXV).isInput()).isFalse();
        assertThat(pins.pin(RXR).isInput()).isFalse();
    }

    @Test
    void theIntegratedScreenSitsRightOfTheRegisterTableAndOnlyMovesTheRightAndBottomPins() {
        ComponentType pins = UART.specializedFor(Map.of(UartTypes.SCREEN, UartTypes.SCREEN_PINS));
        ComponentType screen = UART.specializedFor(Map.of(TerminalTypes.ROWS, 24, TerminalTypes.COLUMNS, 80));

        assertThat(pins.size()).isEqualTo(new GridSize(21, 14));
        assertThat(screen.size()).isEqualTo(new GridSize(17 + 80 + 4, 2 + 48 + 2));
        assertThat(screen.pin(A).position()).isEqualTo(pins.pin(A).position()).isEqualTo(new GridPoint(0, 4));
        assertThat(screen.pin(CS).position()).isEqualTo(new GridPoint(0, 8));
        assertThat(pins.pin(Q).position()).isEqualTo(new GridPoint(21, 4));
        assertThat(screen.pin(Q).position()).isEqualTo(new GridPoint(101, 4));
        assertThat(screen.pin(CLK).position()).isEqualTo(new GridPoint(2, 52));
        assertThat(pins.pin(CLR).position()).isEqualTo(new GridPoint(8, 14));
    }

    @Test
    void thePollingDriverPrintsOnTheIntegratedScreen() {
        BlockHarness harness = integrated();
        initialize(harness);

        print(harness, "hola");

        assertThat(screenRow(harness, 0)).startsWith("hola");
    }

    @Test
    void thePollingDriverReadsWhatIsTyped() {
        BlockHarness harness = integrated();
        initialize(harness);

        assertThat(read(harness, LSR) & DATA_READY).isZero();
        assertThat(harness.typeText("ok")).isEqualTo(2);

        assertThat(receive(harness)).isEqualTo('o');
        assertThat(receive(harness)).isEqualTo('k');
        assertThat(read(harness, LSR) & DATA_READY).isZero();
    }

    @Test
    void theIntegratedUartTakesTypingAndThePinsOneDoesNot() {
        assertThat(integrated().typeText("x")).isEqualTo(1);
        assertThat(withPins(Map.of()).typeText("x")).isZero();
    }

    @Test
    void dlabHidesRbrThrAndIerBehindTheDivisorLatch() {
        BlockHarness harness = integrated();
        write(harness, IER, 0x03);
        write(harness, LCR, 0x80);

        write(harness, 0, 0x0C);
        write(harness, 1, 0x02);

        assertThat(read(harness, 0)).isEqualTo(0x0C);
        assertThat(read(harness, 1)).isEqualTo(0x02);
        assertThat(screenRow(harness, 0)).isBlank();
        write(harness, LCR, 0x03);
        assertThat(read(harness, IER)).isEqualTo(0x03);
        assertThat(state(harness).registers().divisorLow()).isEqualTo(0x0C);
        assertThat(state(harness).registers().divisorHigh()).isEqualTo(0x02);
    }

    @Test
    void theScratchRegisterKeepsWhatIsWritten() {
        BlockHarness harness = integrated();

        write(harness, SCR, 0x5A);

        assertThat(read(harness, SCR)).isEqualTo(0x5A);
    }

    @Test
    void theReceiveFifoHoldsSixteenBytesAndThenOverruns() {
        BlockHarness harness = withPins(Map.of(UartTypes.FIFO, "16"));
        write(harness, IIR_FCR, 0x01);
        harness.set(RXV, 1);
        for (int index = 0; index < 16; index++) {
            assertThat(harness.output(RXR)).isEqualTo(Value.ONE);
            harness.set(RX, 'a' + index);
            clockOnce(harness);
        }

        assertThat(harness.output(RXR)).isEqualTo(Value.ZERO);
        harness.set(RX, 'z');
        clockOnce(harness);
        harness.set(RXV, 0).settle();

        int status = read(harness, LSR);
        assertThat(status & DATA_READY).isEqualTo(DATA_READY);
        assertThat(status & OVERRUN).isEqualTo(OVERRUN);
        assertThat(read(harness, LSR) & OVERRUN).as("reading LSR clears the overrun").isZero();
        for (int index = 0; index < 16; index++) {
            assertThat(read(harness, RBR_THR)).isEqualTo('a' + index);
        }
        assertThat(read(harness, LSR) & DATA_READY).isZero();
        assertThat(harness.output(RXR)).isEqualTo(Value.ONE);
    }

    @Test
    void aOneByteFifoIsA16450AndTheFifoBitsOfIirStayOff() {
        BlockHarness harness = withPins(Map.of(UartTypes.FIFO, "1"));
        write(harness, IIR_FCR, 0x07);
        harness.set(RXV, 1).set(RX, 'p');
        clockOnce(harness);

        assertThat(harness.output(RXR)).isEqualTo(Value.ZERO);
        assertThat(read(harness, IIR_FCR)).isEqualTo(0x01);
    }

    @Test
    void enabledFifosShowInIirTopBits() {
        BlockHarness harness = integrated();

        assertThat(read(harness, IIR_FCR)).isEqualTo(0x01);
        write(harness, IIR_FCR, 0x01);
        assertThat(read(harness, IIR_FCR)).isEqualTo(0xC1);
    }

    @Test
    void receivedDataRaisesIrqAndIirSaysWhy() {
        BlockHarness harness = integrated();
        write(harness, IER, 0x01);
        assertThat(harness.output(IRQ)).isEqualTo(Value.ZERO);

        harness.typeText("r");
        harness.settle();

        assertThat(harness.output(IRQ)).isEqualTo(Value.ONE);
        assertThat(read(harness, IIR_FCR)).isEqualTo(0x04);
        assertThat(read(harness, RBR_THR)).isEqualTo('r');
        assertThat(harness.output(IRQ)).isEqualTo(Value.ZERO);
        assertThat(read(harness, IIR_FCR)).isEqualTo(0x01);
    }

    @Test
    void anEmptyTransmitterRaisesIrqUntilIirIsReadAndAgainAfterEachByte() {
        BlockHarness harness = integrated();

        write(harness, IER, 0x02);

        assertThat(harness.output(IRQ)).isEqualTo(Value.ONE);
        assertThat(read(harness, IIR_FCR)).isEqualTo(0x02);
        assertThat(harness.output(IRQ)).isEqualTo(Value.ZERO);
        assertThat(read(harness, IIR_FCR)).isEqualTo(0x01);
        write(harness, RBR_THR, 'x');
        assertThat(harness.output(IRQ)).isEqualTo(Value.ONE);
    }

    @Test
    void receivedDataComesBeforeAnEmptyTransmitter() {
        BlockHarness harness = integrated();
        write(harness, IER, 0x03);
        harness.typeText("y");
        harness.settle();

        assertThat(read(harness, IIR_FCR)).isEqualTo(0x04);
    }

    @Test
    void qIsInTheThirdStateUnlessCsAndRd() {
        BlockHarness harness = integrated();
        harness.set(A, LSR).settle();

        assertThat(harness.output(Q).isFullyHighImpedance()).isTrue();
        harness.set(RD, 1).settle();
        assertThat(harness.output(Q)).isEqualTo(Value.of(8, 0x60));
        harness.set(CS, 0).settle();
        assertThat(harness.output(Q).isFullyHighImpedance()).isTrue();
    }

    @Test
    void readingWithoutChipSelectPopsNothing() {
        BlockHarness harness = integrated();
        harness.typeText("w");
        harness.set(CS, 0).settle();
        read(harness, RBR_THR);
        harness.set(CS, 1).settle();

        assertThat(read(harness, RBR_THR)).isEqualTo('w');
    }

    @Test
    void theRegisterStrideOnlyChangesTheDrawing() {
        ComponentType wide = UART.specializedFor(Map.of(UartTypes.REGISTER_STRIDE, "4"));

        assertThat(wide.size()).isEqualTo(UART.size());
        assertThat(wide.pins()).isEqualTo(UART.pins());
        assertThat(UART.parameterNamed(UartTypes.REGISTER_STRIDE).orElseThrow().displayOnly()).isTrue();
        BlockHarness harness = new BlockHarness(StandardComponentTypes.UART_16550,
                parameters(Map.of(UartTypes.REGISTER_STRIDE, "4"))).set(CS, 1).settle();
        print(harness, "ok");
        assertThat(screenRow(harness, 0)).startsWith("ok");
    }

    @Test
    void withPinsATransmittedByteGoesOutOnTxWithATxvPulseOfOneClock() {
        BlockHarness harness = withPins(Map.of());

        write(harness, RBR_THR, 'H');

        assertThat(harness.output(TX)).isEqualTo(Value.of(8, 'H'));
        assertThat(harness.output(TXV)).isEqualTo(Value.ONE);
        clockOnce(harness);
        assertThat(harness.output(TXV)).isEqualTo(Value.ZERO);
        assertThat(harness.output(TX)).isEqualTo(Value.of(8, 'H'));
    }

    @Test
    void clrResetsTheRegisters() {
        BlockHarness harness = integrated();
        write(harness, SCR, 0x11);
        write(harness, LCR, 0x83);

        harness.set(CLR, 1).settle().set(CLR, 0).settle();

        assertThat(read(harness, LCR)).isZero();
        assertThat(read(harness, SCR)).isZero();
    }

    @Test
    void moreRowsKeepTheScreen() {
        BlockHarness harness = integrated();
        print(harness, "hi");

        harness.reconfigure(parameters(Map.of(TerminalTypes.ROWS, 6)));

        assertThat(screenRow(harness, 0)).startsWith("hi");
    }

    @Test
    void theWindowAndTheAiReachTheIntegratedScreenAndQueueButNotThePinsOnes() {
        BlockHarness harness = integrated();
        print(harness, "hi");
        harness.typeText("abc");

        assertThat(harness.engine().terminal("dut")).hasValueSatisfying(
                part -> assertThat(part.screen().frame().rowText(0)).startsWith("hi"));
        assertThat(harness.engine().typingQueueSize("dut")).hasValue(3);
        harness.engine().clearTerminal("dut");
        assertThat(screenRow(harness, 0)).isBlank();

        BlockHarness pins = withPins(Map.of());
        assertThat(pins.engine().terminal("dut")).isEmpty();
        assertThat(pins.engine().typingQueueSize("dut")).isEmpty();
    }

    @Test
    void theInitialTextWaitsInTheReceiverAndSurvivesTheDriversFifoReset() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.UART_16550,
                parameters(Map.of(KeyboardTypes.INITIAL_TEXT, "5\n"))).set(CS, 1).settle();
        initialize(harness);

        assertThat(receive(harness)).isEqualTo('5');
        assertThat(receive(harness)).isEqualTo('\n');
    }

    @Test
    void itsStateTellsTheRegistersAndTheBytesWaiting() {
        BlockHarness harness = integrated();
        harness.typeText("ab");
        harness.settle();

        VisibleState.UartRegisters registers = state(harness).registers();
        assertThat(registers.lineStatus() & DATA_READY).isEqualTo(DATA_READY);
        assertThat(registers.receiver()).isEqualTo('a');
        assertThat(state(harness).received()).isEqualTo(2);
    }

    @Test
    void withoutFifosANewByteOverwritesRbrAndOverrunSaysTheOldOneWasLost() {
        BlockHarness harness = withPins(Map.of(UartTypes.FIFO, "1"));
        harness.set(RXV, 1).set(RX, 'a');
        clockOnce(harness);
        harness.set(RX, 'b');
        clockOnce(harness);
        harness.set(RXV, 0).settle();

        assertThat(read(harness, LSR) & OVERRUN).isEqualTo(OVERRUN);
        assertThat(read(harness, RBR_THR)).as("the 16450 keeps the newest byte, as the real chip").isEqualTo('b');
    }

    @Test
    void readingAnEmptyRbrGivesTheLastByteAgain() {
        BlockHarness harness = withPins(Map.of(UartTypes.FIFO, "1"));
        assertThat(read(harness, RBR_THR)).as("nothing received yet").isZero();
        harness.set(RXV, 1).set(RX, 'k');
        clockOnce(harness);
        harness.set(RXV, 0).settle();

        assertThat(read(harness, RBR_THR)).isEqualTo('k');
        assertThat(read(harness, LSR) & DATA_READY).isZero();
        assertThat(read(harness, RBR_THR)).as("a stale RBR, as the real chip").isEqualTo('k');
    }

    @Test
    void theIntegratedScreenHasTheTerminalsBellAndTeletypeClatterAndThePinsOneHidesThem() {
        Map<String, Object> integrated = Map.of(UartTypes.SCREEN, UartTypes.SCREEN_INTEGRATED);
        Map<String, Object> pins = Map.of(UartTypes.SCREEN, UartTypes.SCREEN_PINS);
        for (String name : List.of(TerminalTypes.BELL, TerminalTypes.TELETYPE_SOUND)) {
            ParameterSpec parameter = UART.parameterNamed(name).orElseThrow();
            assertThat(parameter.displayOnly()).as(name).isTrue();
            assertThat(UART.isShown(parameter, integrated)).as(name).isTrue();
            assertThat(UART.isShown(parameter, pins)).as(name).isFalse();
        }
        assertThat(UART.resolveParameters(Map.of())).containsEntry(TerminalTypes.BELL, "flash")
                .containsEntry(TerminalTypes.TELETYPE_SOUND, false);
    }
}
