package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.VisibleState.TerminalState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Terminal window's frames (spec §9, §11): a request, its publication, the history and the bytes since. */
class SimulationThreadTerminalTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final int ROWS = 2;
    private static final int COLUMNS = 10;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AtomicLong clock = new AtomicLong();
    private final SimulationEngine engine = new SimulationEngine();
    private final SimulationThread thread = new SimulationThread(engine, clock::get);
    private final Map<String, Object> parameters = ParameterProfiles.apply(
            registry.require(StandardComponentTypes.TERMINAL),
            Map.of(TerminalTypes.ROWS, ROWS, TerminalTypes.COLUMNS, COLUMNS), TerminalTypes.PROFILE_VT);

    /** A VT Terminal "tty" whose D, CLK and WE come from input pins of the same names (CLR unconnected). */
    private FlatCircuit circuit() {
        ComponentType terminal = registry.require(StandardComponentTypes.TERMINAL).specializedFor(parameters);
        List<FlatComponent> components = new ArrayList<>(List.of(new FlatComponent("tty", terminal, parameters)));
        List<FlatNet> nets = new ArrayList<>();
        List<String> inputs = List.of("D", "CLK", "WE");
        for (int pinIndex = 0; pinIndex < inputs.size(); pinIndex++) {
            String input = inputs.get(pinIndex);
            int width = terminal.pin(pinIndex).width();
            Map<String, Object> pinParameters = Map.of("value", "0", InterfacePins.WIDTH, width);
            components.add(new FlatComponent(input, registry.require(InterfacePins.INPUT_PIN)
                    .specializedFor(pinParameters), pinParameters));
            nets.add(new FlatNet("n-" + input, List.of(new FlatPinRef(input, 0), new FlatPinRef("tty", pinIndex)),
                    width, false));
        }
        nets.add(new FlatNet("n-CLR", List.of(new FlatPinRef("tty", 3)), 1, false));
        return new FlatCircuit(components, nets);
    }

    private void tickLater() {
        clock.addAndGet(20 * MILLISECOND);
        thread.tick();
    }

    private void type(String text) {
        for (char character : text.toCharArray()) {
            thread.setInputs(Map.of("D", Value.of(8, character), "WE", Value.ONE, "CLK", Value.ZERO));
            thread.step(10);
            thread.setInputs(Map.of("CLK", Value.ONE));
            thread.step(10);
        }
    }

    private void start() {
        thread.update(circuit());
        thread.tick();
    }

    @Test
    void aFrameHoldsTheScreenTheHistoryTheBytesAndTheTranscript() {
        start();
        type("uno\ndos\ntres");
        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 10, 0)));
        tickLater();

        TerminalWindowFrame frame = thread.latestTerminalFrame().orElseThrow();
        assertThat(frame.request()).isEqualTo(new TerminalRequest("tty", 10, 0));
        assertThat(frame.frame().text().stripTrailing()).isEqualTo("dos\ntres");
        assertThat(frame.history()).hasSize(1);
        assertThat(rowText(frame.history().getFirst())).isEqualTo("uno");
        assertThat(frame.bytes()).hasSize(12).extracting(ByteLog.Entry::value).startsWith((int) 'u', (int) 'n');
        assertThat(frame.lastByteSequence()).isEqualTo(12);
        assertThat(frame.transcript()).isEqualTo("uno\ndos\ntres");
        assertThat(frame.time()).isEqualTo(thread.latestSnapshot().time());
    }

    @Test
    void aOneShotReadingHoldsTheScreenTheHistoryTextAndTheLastBytes() {
        start();
        type("uno\ndos\ntres");

        CompletableFuture<Optional<TerminalReading>> asked = thread.requestTerminalReading("tty", 10, 4);
        assertThat(asked).isNotDone();
        thread.tick();

        TerminalReading reading = asked.join().orElseThrow();
        assertThat(reading.frame().text().stripTrailing()).isEqualTo("dos\ntres");
        assertThat(reading.history()).containsExactly("uno");
        assertThat(reading.bytes()).extracting(ByteLog.Entry::value)
                .containsExactly((int) 't', (int) 'r', (int) 'e', (int) 's');
        assertThat(reading.transcript()).isEqualTo("uno\ndos\ntres");
        assertThat(reading.time()).isEqualTo(thread.latestSnapshot().time());
    }

    @Test
    void aReadingOfAnUnknownPartOrOfAClosedThreadIsEmpty() {
        start();

        CompletableFuture<Optional<TerminalReading>> unknown = thread.requestTerminalReading("nothing", 0, 0);
        thread.tick();
        assertThat(unknown.join()).isEmpty();

        thread.close();
        assertThat(thread.requestTerminalReading("tty", 0, 0).join()).isEmpty();
    }

    @Test
    void theHistoryIsCutToTheLinesAskedFor() {
        start();
        type("a\nb\nc\nd\ne");
        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 2, 0)));
        tickLater();

        assertThat(thread.latestTerminalFrame().orElseThrow().history()).extracting(this::rowText)
                .containsExactly("b", "c");
    }

    @Test
    void theBytesStartAfterTheOnesAskedForAndAfterTheOnesAcknowledged() {
        start();
        type("abcd");
        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 0, 2)));
        tickLater();
        assertThat(thread.latestTerminalFrame().orElseThrow().bytes()).extracting(ByteLog.Entry::sequence)
                .containsExactly(3L, 4L);

        thread.acknowledgeTerminalBytes(thread.latestTerminalFrame().orElseThrow().owner(), 4);
        type("ef");
        tickLater();
        assertThat(thread.latestTerminalFrame().orElseThrow().bytes()).extracting(ByteLog.Entry::value)
                .containsExactly((int) 'e', (int) 'f');
    }

    @Test
    void afterARestartTheBytesStartOverFromTheFirst() {
        start();
        type("abcd");
        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 0, 0)));
        tickLater();
        thread.acknowledgeTerminalBytes(thread.latestTerminalFrame().orElseThrow().owner(), 4);

        thread.restart(circuit(), Map.of());
        tickLater();
        type("x");
        tickLater();

        TerminalWindowFrame frame = thread.latestTerminalFrame().orElseThrow();
        assertThat(frame.frame().text().stripTrailing()).isEqualTo("x");
        assertThat(frame.bytes()).extracting(ByteLog.Entry::sequence).containsExactly(1L);
        assertThat(frame.lastByteSequence()).isEqualTo(1);
    }

    @Test
    void whileRunningFramesArePublishedAtMostEverySixteenMilliseconds() {
        start();
        type("a");
        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 0, 0)));
        thread.resume();
        tickLater();
        TerminalWindowFrame first = thread.latestTerminalFrame().orElseThrow();

        thread.setInputs(Map.of("D", Value.of(8, 'b'), "CLK", Value.ZERO));
        clock.addAndGet(MILLISECOND);
        thread.tick();
        thread.setInputs(Map.of("CLK", Value.ONE));
        clock.addAndGet(MILLISECOND);
        thread.tick();
        assertThat(thread.latestTerminalFrame()).containsSame(first);

        tickLater();
        assertThat(thread.latestTerminalFrame().orElseThrow().frame().text().stripTrailing()).isEqualTo("ab");
    }

    @Test
    void nothingChangedPublishesNoNewFrame() {
        start();
        type("a");
        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 0, 0)));
        tickLater();
        TerminalWindowFrame first = thread.latestTerminalFrame().orElseThrow();

        tickLater();
        tickLater();

        assertThat(thread.latestTerminalFrame()).containsSame(first);
    }

    @Test
    void framesStopWithAnEmptyRequestAndNameOnlyTerminals() {
        start();
        thread.setTerminalRequest(Optional.of(new TerminalRequest("D", 0, 0)));
        tickLater();
        assertThat(thread.latestTerminalFrame()).as("an input pin is not a terminal").isEmpty();

        thread.setTerminalRequest(Optional.of(new TerminalRequest("tty", 0, 0)));
        tickLater();
        assertThat(thread.latestTerminalFrame()).isPresent();
        thread.setTerminalRequest(Optional.empty());
        tickLater();
        assertThat(thread.latestTerminalFrame()).isEmpty();
    }

    @Test
    void clearingATerminalEmptiesItsScreenAndIsNotAStep() {
        start();
        type("hola");
        tickLater();
        long steps = thread.latestStep().orElseThrow().sequence();

        thread.clearTerminal("tty");
        tickLater();

        TerminalState state = (TerminalState) thread.latestSnapshot().componentState("tty");
        assertThat(state.frame().text()).isBlank();
        assertThat(thread.latestStep().orElseThrow().sequence()).isEqualTo(steps);
    }

    @Test
    void aRequestRefusesNegativeCounts() {
        assertThatThrownBy(() -> new TerminalRequest("tty", -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TerminalRequest("tty", 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private String rowText(TerminalCell[] row) {
        StringBuilder text = new StringBuilder();
        for (TerminalCell cell : row) {
            text.appendCodePoint(cell.codePoint());
        }
        return text.toString().stripTrailing();
    }
}
