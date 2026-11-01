package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.KeyQueue;
import cl.almejo.vsim.core.terminal.TerminalEncoding;
import cl.almejo.vsim.core.terminal.TypingEdit;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;

/**
 * «Teclado»: a queue of typed bytes, Logisim's Keyboard timing. D always shows the head of the queue (0 when it is
 * empty) and AV is 1 while the queue is not empty, so the circuit reads the head before the edge. On the clock's edge
 * (rising or falling, «Flanco») with RE not 0 (unconnected counts as 1) the head is popped, and D and AV show the
 * next byte {@value #OUTPUT_DELAY} unit later, after whatever sampled the old one. Only RE = 0 holds the queue and only
 * CLR = 1 clears it (an undefined or conflicting RE reads and CLR does not clear, as in Logisim); CLR = 1
 * empties it at once and wins over the clock. With «Tercer estado» D lets go of its net while RE is 0. Typing and the
 * arrows reach it through {@link #acceptBytes} and {@link #editTyping}. The queue starts with «Texto inicial»,
 * encoded as typing it would be; a rebuilt behaviour ({@link #adoptStateFrom}) keeps the queue instead, unless the
 * «Texto inicial» itself changed.
 */
final class KeyboardBehavior implements Behavior {

    /** D and AV follow the queue this long after it changes. */
    static final int OUTPUT_DELAY = 1;

    private final int dataWidth;
    private final boolean triState;
    private final KeyEncoding encoding;
    private final String initialText;
    private final ClockEdgeDetector edge;
    private KeyQueue queue;
    private boolean adopted;
    private VisibleState.KeyboardState published;

    KeyboardBehavior(Map<String, Object> parameters) {
        this.encoding = KeyEncoding.forParameters(parameters);
        this.dataWidth = encoding.dataWidth();
        this.triState = Boolean.TRUE.equals(parameters.get(KeyboardTypes.TRI_STATE));
        this.initialText = String.valueOf(parameters.getOrDefault(KeyboardTypes.INITIAL_TEXT, ""));
        this.edge = ClockEdgeDetector.of(parameters);
        this.queue = new KeyQueue((Integer) parameters.getOrDefault(KeyboardTypes.CAPACITY,
                KeyboardTypes.DEFAULT_CAPACITY));
    }

    @Override
    public void adoptStateFrom(Behavior previous) {
        // A new «Texto inicial» starts the queue over with it, as «Reiniciar» would; any other change keeps the queue.
        if (previous instanceof KeyboardBehavior keyboard && keyboard.initialText.equals(initialText)) {
            queue = keyboard.queue.resizedTo(queue.capacity(),
                    encoding.encoding() == TerminalEncoding.UTF8);
            adopted = true;
        }
    }

    @Override
    public void initialize(BehaviorContext context) {
        edge.start(context.input(KeyboardPinout.CLOCK));
        if (!adopted) {
            for (byte[] character : encoding.encodeText(initialText)) {
                if (!queue.insert(character)) {
                    break;
                }
            }
        }
        refresh(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        boolean clockEdge = pinIndex == KeyboardPinout.CLOCK && edge.step(context.input(KeyboardPinout.CLOCK));
        // as Logisim's Keyboard: only CLR = 1 clears; an undefined or conflicting CLR lets the edge pop
        if (Value.ONE.equals(context.input(KeyboardPinout.CLEAR))) {
            queue.clear();
        } else if (clockEdge && readEnabled(context)) {
            queue.pop();
        }
        refresh(context);
    }

    @Override
    public boolean acceptsTyping() {
        return true;
    }

    @Override
    public int acceptBytes(BehaviorContext context, byte[] bytes) {
        if (!queue.insert(bytes)) {
            return 0;
        }
        refresh(context);
        return bytes.length;
    }

    @Override
    public void editTyping(BehaviorContext context, TypingEdit typingEdit) {
        queue.edit(typingEdit);
        refresh(context);
    }

    @Override
    public VisibleState visibleState() {
        if (published == null || published.version() != queue.version()) {
            published = new VisibleState.KeyboardState(queue.version(),
                    queue.head(VisibleState.KeyboardState.SHOWN_BYTES), queue.size(), queue.cursor());
        }
        return published;
    }

    /** As Logisim's Keyboard: only RE = 0 holds the queue; unconnected, undefined or in conflict, it reads. */
    private static boolean readEnabled(BehaviorContext context) {
        return !Value.ZERO.equals(context.input(KeyboardPinout.READ_ENABLE));
    }

    private void refresh(BehaviorContext context) {
        context.drive(KeyboardPinout.AVAILABLE, Value.of(!queue.isEmpty()), OUTPUT_DELAY);
        Value data = triState && !readEnabled(context) ? Value.highImpedance(dataWidth)
                : Value.of(dataWidth, queue.peek());
        context.drive(KeyboardPinout.DATA, data, OUTPUT_DELAY);
    }

    /** The bytes the queue holds, for tests. */
    List<Integer> contents() {
        return queue.head(queue.size());
    }
}
