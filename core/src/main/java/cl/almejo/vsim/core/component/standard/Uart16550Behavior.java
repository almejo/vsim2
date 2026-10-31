package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.TerminalState;
import cl.almejo.vsim.core.component.VisibleState.UartRegisters;
import cl.almejo.vsim.core.component.VisibleState.UartState;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.KeyQueue;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TypingEdit;
import cl.almejo.vsim.core.value.Value;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * «UART 16550» (spec §12): the eight registers of a 16550 on a CPU bus, as QEMU's {@code virt} board has them.
 *
 * <p><b>The bus.</b> Q shows the register A selects while CS and RD are 1 (CS unconnected counts as 1, RD and WR as
 * 0), and lets go of its net otherwise. On the clock's edge («Flanco») with CS: RD = 1 ends the read, with its effects
 * (reading RBR takes the byte off the FIFO, reading LSR clears the overrun, reading IIR clears the «THR vacío»
 * interrupt it reported); WR = 1 writes D to the register. So a CPU reads Q before the edge, as from a Teclado.
 * Registers: RBR/THR 0, IER 1, IIR/FCR 2, LCR 3, MCR 4, LSR 5, MSR 6, SCR 7; with LCR's DLAB bit (7) on, 0 and 1 are
 * the divisor latch DLL/DLM, accepted and shown but never slowing anything: a byte written to THR leaves at once, so
 * LSR always says THRE and TEMT.
 *
 * <p><b>The FIFOs.</b> With «FIFO» 16, FCR bit 0 turns them on (IIR then has bits 7 and 6) and bit 1 empties the
 * receiver; with 1 the part is a 16450 and FCR does nothing. A byte that finds the receiver full is lost and sets the
 * overrun bit of LSR.
 *
 * <p><b>Interrupts.</b> IRQ is 1 while IIR has a cause, highest first: an overrun (IER bit 2), «dato recibido» (IER
 * bit 0, while LSR's DR is on) and «THR vacío» (IER bit 1: raised when that bit is turned on and after every byte
 * sent, cleared by reading IIR when it reports it or by writing THR).
 *
 * <p><b>The line.</b> With the integrated screen a byte sent goes to a Terminal screen inside the part (an undefined D
 * writes {@code ?}) and what the user types into the part waits in the receiver, as many bytes as «Capacidad»: that
 * queue is the keyboard's side of the line, so neither CLR nor FCR empties it (only «Reiniciar», back to «Texto
 * inicial»). With TX and RX pins a byte sent shows on TX and TXV is 1 until the next edge; on an edge with RXV = 1
 * the byte on RX enters the receiver, and RXR («receptor listo») says there is room for it, so a Teclado whose RE is
 * RXR never loses a key. CLR = 1 resets the registers at once (and the receiver with pins), winning over the clock.
 */
final class Uart16550Behavior implements Behavior, TerminalScreenPart {

    /** Q, IRQ and the line pins follow the registers this long after they change. */
    static final int OUTPUT_DELAY = 1;

    static final int RECEIVER_OR_TRANSMITTER = 0;
    static final int INTERRUPT_ENABLE = 1;
    static final int INTERRUPT_IDENTIFICATION = 2;
    static final int LINE_CONTROL = 3;
    static final int MODEM_CONTROL = 4;
    static final int LINE_STATUS = 5;
    static final int MODEM_STATUS = 6;
    static final int SCRATCH = 7;

    private static final int DIVISOR_LATCH_ACCESS = 0x80;
    private static final int INTERRUPT_ENABLE_MASK = 0x0F;
    private static final int MODEM_CONTROL_MASK = 0x1F;
    private static final int ENABLE_RECEIVED = 0x01;
    private static final int ENABLE_TRANSMITTER_EMPTY = 0x02;
    private static final int ENABLE_LINE_STATUS = 0x04;
    private static final int FIFO_ENABLE = 0x01;
    private static final int FIFO_CLEAR_RECEIVER = 0x02;
    private static final int DATA_READY = 0x01;
    private static final int OVERRUN = 0x02;
    /** THRE and TEMT: the byte written leaves at once, so the transmitter is always empty. */
    private static final int TRANSMITTER_EMPTY = 0x60;
    private static final int NO_INTERRUPT = 0x01;
    private static final int CAUSE_LINE_STATUS = 0x06;
    private static final int CAUSE_RECEIVED = 0x04;
    private static final int CAUSE_TRANSMITTER_EMPTY = 0x02;
    private static final int FIFOS_ENABLED = 0xC0;
    /** DCD, DSR and CTS: a terminal is always there, as QEMU's MSR says after a reset. */
    private static final int MODEM_STATUS_CONNECTED = 0xB0;
    private static final int NONE = -1;

    /** Hands each behaviour a number of its own for {@link TerminalState#owner()}. */
    private static final AtomicLong OWNERS = new AtomicLong();

    private final long owner = OWNERS.incrementAndGet();
    private final boolean integrated;
    private final int fifoDepth;
    private final int typingCapacity;
    private final KeyEncoding encoding;
    private final String initialText;
    private final ClockEdgeDetector edge;
    private TerminalScreen screen;
    private KeyQueue received;
    private boolean adoptedQueue;
    private TerminalState publishedScreen;

    private int interruptEnable;
    private int fifoControl;
    private int lineControl;
    private int modemControl;
    private int scratch;
    private int divisorLow;
    private int divisorHigh;
    private boolean fifosEnabled;
    private boolean overrun;
    private boolean transmitterInterruptPending;
    private int lastTransmitted = NONE;
    private boolean transmitterUndefined;
    private boolean transmitStrobe;

    Uart16550Behavior(Map<String, Object> parameters) {
        this.integrated = UartTypes.isIntegrated(parameters);
        this.fifoDepth = UartTypes.fifoDepth(parameters);
        this.typingCapacity = (Integer) parameters.getOrDefault(KeyboardTypes.CAPACITY,
                KeyboardTypes.DEFAULT_CAPACITY);
        this.encoding = KeyEncoding.forParameters(parameters);
        this.initialText = String.valueOf(parameters.getOrDefault(KeyboardTypes.INITIAL_TEXT, ""));
        this.edge = ClockEdgeDetector.of(parameters);
        this.screen = new TerminalScreen(TerminalTypes.settings(parameters));
        this.received = new KeyQueue(receiverCapacity());
    }

    @Override
    public void adoptStateFrom(Behavior previous) {
        if (!(previous instanceof Uart16550Behavior uart)) {
            return;
        }
        screen = uart.screen.adoptedWith(screen.settings());
        interruptEnable = uart.interruptEnable;
        fifoControl = uart.fifoControl;
        lineControl = uart.lineControl;
        modemControl = uart.modemControl;
        scratch = uart.scratch;
        divisorLow = uart.divisorLow;
        divisorHigh = uart.divisorHigh;
        fifosEnabled = uart.fifosEnabled && fifoDepth > 1;
        overrun = uart.overrun;
        transmitterInterruptPending = uart.transmitterInterruptPending;
        lastTransmitted = uart.lastTransmitted;
        transmitterUndefined = uart.transmitterUndefined;
        // a new «Texto inicial» or another line starts the receiver over, as «Reiniciar» would
        if (uart.integrated == integrated && (!integrated || uart.initialText.equals(initialText))) {
            received = uart.received.resizedTo(receiverCapacity());
            adoptedQueue = true;
        }
    }

    @Override
    public void initialize(BehaviorContext context) {
        edge.start(context.input(UartPinout.CLOCK));
        if (integrated && !adoptedQueue) {
            for (byte[] character : encoding.encodeText(initialText)) {
                if (!received.insert(character)) {
                    break;
                }
            }
        }
        refresh(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        boolean clockEdge = pinIndex == UartPinout.CLOCK && edge.step(context.input(UartPinout.CLOCK));
        Optional<Boolean> clear = BlockSignals.control(context.input(UartPinout.CLEAR), false);
        if (clear.isPresent() && clear.get()) {
            reset();
        } else if (clear.isPresent() && clockEdge) {
            busCycle(context);
        }
        refresh(context);
    }

    @Override
    public boolean acceptsTyping() {
        return integrated;
    }

    @Override
    public int acceptBytes(BehaviorContext context, byte[] bytes) {
        if (!integrated || !received.insert(bytes)) {
            return 0;
        }
        refresh(context);
        return bytes.length;
    }

    @Override
    public void editTyping(BehaviorContext context, TypingEdit typingEdit) {
        if (integrated) {
            received.edit(typingEdit);
            refresh(context);
        }
    }

    @Override
    public long owner() {
        return owner;
    }

    @Override
    public TerminalScreen screen() {
        return screen;
    }

    @Override
    public void clearScreen() {
        screen.clear();
    }

    @Override
    public boolean showsScreen() {
        return integrated;
    }

    @Override
    public VisibleState visibleState() {
        Optional<TerminalState> shownScreen = Optional.empty();
        if (integrated) {
            if (publishedScreen == null || publishedScreen.version() != screen.version()) {
                publishedScreen = new TerminalState(owner, screen.version(), screen.frame(), false, GlyphCache.EMPTY);
            }
            shownScreen = Optional.of(publishedScreen);
        }
        return new UartState(registers(), received.size(), shownScreen);
    }

    /** What the registers hold now, as the painter shows them. */
    private UartRegisters registers() {
        return new UartRegisters(received.isEmpty() ? NONE : received.peek(), lastTransmitted, interruptEnable,
                interruptIdentification(), fifoControl, lineControl, modemControl, lineStatus(),
                MODEM_STATUS_CONNECTED, scratch, divisorLow, divisorHigh);
    }

    /** The edge: the line's byte comes in first (RXR told the sender there was room before the edge), then the CPU. */
    private void busCycle(BehaviorContext context) {
        boolean transmitted = false;
        if (!integrated) {
            receiveFromLine(context);
        }
        boolean selected = BlockSignals.control(context.input(UartPinout.CHIP_SELECT), true).orElse(false);
        OptionalLong register = BlockSignals.unsigned(context.input(UartPinout.ADDRESS));
        if (selected && register.isPresent()) {
            if (control(context, UartPinout.READ)) {
                finishRead((int) register.getAsLong());
            }
            if (control(context, UartPinout.WRITE)) {
                transmitted = write(context, (int) register.getAsLong());
            }
        }
        if (!transmitted) {
            transmitStrobe = false;
        }
    }

    private void receiveFromLine(BehaviorContext context) {
        if (!control(context, UartPinout.RECEIVE_VALID)) {
            return;
        }
        OptionalLong value = BlockSignals.unsigned(context.input(UartPinout.RECEIVE));
        if (value.isEmpty()) {
            return;
        }
        if (!received.insert(new byte[]{(byte) value.getAsLong()})) {
            overrun = true;
        }
    }

    /** The effects of a read that ends on this edge. */
    private void finishRead(int register) {
        switch (register) {
            case RECEIVER_OR_TRANSMITTER -> {
                if (!divisorLatch()) {
                    received.pop();
                }
            }
            case INTERRUPT_IDENTIFICATION -> {
                if ((interruptIdentification() & ~FIFOS_ENABLED) == CAUSE_TRANSMITTER_EMPTY) {
                    transmitterInterruptPending = false;
                }
            }
            case LINE_STATUS -> overrun = false;
            default -> {
                // reading the other registers changes nothing
            }
        }
    }

    /** Writes D to {@code register}; true when it sent a byte. */
    private boolean write(BehaviorContext context, int register) {
        OptionalLong data = BlockSignals.unsigned(context.input(UartPinout.DATA));
        if (register == RECEIVER_OR_TRANSMITTER && !divisorLatch()) {
            transmit(context, data);
            return true;
        }
        if (data.isEmpty()) {
            return false;
        }
        int value = (int) data.getAsLong();
        switch (register) {
            case RECEIVER_OR_TRANSMITTER -> divisorLow = value;
            case INTERRUPT_ENABLE -> writeInterruptEnable(value);
            case INTERRUPT_IDENTIFICATION -> writeFifoControl(value);
            case LINE_CONTROL -> lineControl = value;
            case MODEM_CONTROL -> modemControl = value & MODEM_CONTROL_MASK;
            case SCRATCH -> scratch = value;
            default -> {
                // LSR and MSR are read-only
            }
        }
        return false;
    }

    private void writeInterruptEnable(int value) {
        if (divisorLatch()) {
            divisorHigh = value;
            return;
        }
        boolean turnedOn = (value & ENABLE_TRANSMITTER_EMPTY) != 0 && (interruptEnable & ENABLE_TRANSMITTER_EMPTY) == 0;
        interruptEnable = value & INTERRUPT_ENABLE_MASK;
        if (turnedOn) {
            transmitterInterruptPending = true;
        }
    }

    private void writeFifoControl(int value) {
        fifoControl = value;
        if (fifoDepth == 1) {
            return;
        }
        boolean enable = (value & FIFO_ENABLE) != 0;
        boolean clearReceiver = (value & FIFO_CLEAR_RECEIVER) != 0 || enable != fifosEnabled;
        fifosEnabled = enable;
        if (!integrated) {
            received = clearReceiver ? new KeyQueue(receiverCapacity()) : received.resizedTo(receiverCapacity());
        }
    }

    /** The byte leaves at once: to the screen or the TX pin; THR is empty again, which is an interrupt. */
    private void transmit(BehaviorContext context, OptionalLong data) {
        transmitterInterruptPending = true;
        transmitterUndefined = data.isEmpty();
        lastTransmitted = data.isPresent() ? (int) data.getAsLong() : NONE;
        if (integrated) {
            if (data.isPresent()) {
                screen.write(lastTransmitted, context.now());
            } else {
                screen.writeUndefined(context.now());
            }
        } else {
            transmitStrobe = true;
        }
    }

    /** CLR: the registers as after power-on; the receiver too unless it is the integrated keyboard. */
    private void reset() {
        interruptEnable = 0;
        fifoControl = 0;
        lineControl = 0;
        modemControl = 0;
        scratch = 0;
        divisorLow = 0;
        divisorHigh = 0;
        fifosEnabled = false;
        overrun = false;
        transmitterInterruptPending = false;
        transmitStrobe = false;
        if (!integrated) {
            received = new KeyQueue(receiverCapacity());
        }
    }

    private int receiverCapacity() {
        if (integrated) {
            return typingCapacity;
        }
        return fifosEnabled ? fifoDepth : 1;
    }

    private boolean divisorLatch() {
        return (lineControl & DIVISOR_LATCH_ACCESS) != 0;
    }

    private int lineStatus() {
        return (received.isEmpty() ? 0 : DATA_READY) | (overrun ? OVERRUN : 0) | TRANSMITTER_EMPTY;
    }

    private int interruptIdentification() {
        int fifoBits = fifosEnabled ? FIFOS_ENABLED : 0;
        if ((interruptEnable & ENABLE_LINE_STATUS) != 0 && overrun) {
            return fifoBits | CAUSE_LINE_STATUS;
        }
        if ((interruptEnable & ENABLE_RECEIVED) != 0 && !received.isEmpty()) {
            return fifoBits | CAUSE_RECEIVED;
        }
        if ((interruptEnable & ENABLE_TRANSMITTER_EMPTY) != 0 && transmitterInterruptPending) {
            return fifoBits | CAUSE_TRANSMITTER_EMPTY;
        }
        return fifoBits | NO_INTERRUPT;
    }

    /** The value a CPU reads from {@code register} now. */
    private int registerValue(int register) {
        return switch (register) {
            case RECEIVER_OR_TRANSMITTER -> divisorLatch() ? divisorLow : received.peek();
            case INTERRUPT_ENABLE -> divisorLatch() ? divisorHigh : interruptEnable;
            case INTERRUPT_IDENTIFICATION -> interruptIdentification();
            case LINE_CONTROL -> lineControl;
            case MODEM_CONTROL -> modemControl;
            case LINE_STATUS -> lineStatus();
            case MODEM_STATUS -> MODEM_STATUS_CONNECTED;
            default -> scratch;
        };
    }

    /** A 1-bit control input that rests at 0; a conflict on it counts as 0. */
    private static boolean control(BehaviorContext context, int pinIndex) {
        return BlockSignals.control(context.input(pinIndex), false).orElse(false);
    }

    private void refresh(BehaviorContext context) {
        context.drive(UartPinout.OUTPUT, output(context), OUTPUT_DELAY);
        context.drive(UartPinout.INTERRUPT, Value.of((interruptIdentification() & NO_INTERRUPT) == 0), OUTPUT_DELAY);
        if (!integrated) {
            Value transmittedByte = transmitterUndefined ? BlockSignals.undefined(UartPinout.BYTE_WIDTH)
                    : Value.of(UartPinout.BYTE_WIDTH, Math.max(0, lastTransmitted));
            context.drive(UartPinout.TRANSMIT, transmittedByte, OUTPUT_DELAY);
            context.drive(UartPinout.TRANSMIT_VALID, Value.of(transmitStrobe), OUTPUT_DELAY);
            context.drive(UartPinout.RECEIVER_READY, Value.of(received.size() < received.capacity()), OUTPUT_DELAY);
        }
    }

    /** Q: the register A selects while CS and RD are 1, undefined with an undefined A, else free. */
    private Value output(BehaviorContext context) {
        boolean selected = BlockSignals.control(context.input(UartPinout.CHIP_SELECT), true).orElse(false);
        if (!selected || !control(context, UartPinout.READ)) {
            return Value.highImpedance(UartPinout.BYTE_WIDTH);
        }
        OptionalLong register = BlockSignals.unsigned(context.input(UartPinout.ADDRESS));
        if (register.isEmpty()) {
            return BlockSignals.undefined(UartPinout.BYTE_WIDTH);
        }
        return Value.of(UartPinout.BYTE_WIDTH, registerValue((int) register.getAsLong()));
    }
}
