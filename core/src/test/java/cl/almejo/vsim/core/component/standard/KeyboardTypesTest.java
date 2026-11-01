package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.terminal.TypingEdit;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Teclado: its place and pins, Logisim's peek/pop timing, typing, initial text, and rebuilding. */
class KeyboardTypesTest {

    private static final int CLK = KeyboardPinout.CLOCK;
    private static final int RE = KeyboardPinout.READ_ENABLE;
    private static final int CLR = KeyboardPinout.CLEAR;
    private static final int AV = KeyboardPinout.AVAILABLE;
    private static final int D = KeyboardPinout.DATA;

    private static BlockHarness keyboard(Map<String, Object> parameters) {
        // the harness drives every input with 0: RE is raised so the queue reads
        return new BlockHarness(StandardComponentTypes.KEYBOARD, parameters).set(RE, 1).settle();
    }

    private static void clockOnce(BlockHarness harness) {
        harness.set(CLK, 1).settle().set(CLK, 0).settle();
    }

    @Test
    void itIsAnInputOutputPartRightAfterTheTerminal() {
        List<String> inputOutput = StandardComponentTypes.registry().all().stream()
                .filter(type -> type.category() == ComponentCategory.INPUT_OUTPUT).map(ComponentType::id).toList();

        assertThat(inputOutput.indexOf(StandardComponentTypes.KEYBOARD))
                .isEqualTo(inputOutput.indexOf(StandardComponentTypes.TERMINAL) + 1);
    }

    @Test
    void itsPinsAreAtTheFixedPlaces() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);

        assertThat(type.size()).isEqualTo(new GridSize(16, 6));
        assertThat(type.pins()).extracting(PinSpec::name).containsExactly("CLK", "RE", "CLR", "AV", "D");
        assertThat(type.pin(CLK).position()).isEqualTo(new GridPoint(2, 6));
        assertThat(type.pin(RE).position()).isEqualTo(new GridPoint(4, 6));
        assertThat(type.pin(CLR).position()).isEqualTo(new GridPoint(6, 6));
        assertThat(type.pin(AV).position()).isEqualTo(new GridPoint(16, 2));
        assertThat(type.pin(D).position()).isEqualTo(new GridPoint(16, 4));
        assertThat(type.pin(D).width()).isEqualTo(8);
        assertThat(type.specializedFor(Map.of("data-width", "7")).pin(D).width()).isEqualTo(7);
    }

    @Test
    void itsPartWidthStretchesTheRightEdgeAndCarriesAvAndD() {
        ComponentType wide = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD)
                .specializedFor(Map.of(KeyboardTypes.PART_WIDTH, 24));

        assertThat(wide.size()).isEqualTo(new GridSize(24, 6));
        assertThat(wide.pin(AV).position()).isEqualTo(new GridPoint(24, 2));
        assertThat(wide.pin(D).position()).isEqualTo(new GridPoint(24, 4));
        assertThat(wide.pin(CLK).position()).isEqualTo(new GridPoint(2, 6));
        assertThat(wide.pin(RE).position()).isEqualTo(new GridPoint(4, 6));
        assertThat(wide.pin(CLR).position()).isEqualTo(new GridPoint(6, 6));
    }

    @Test
    void itsPartWidthGoesFromSixteenToSixtyFourUnits() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
        ParameterSpec width = type.parameterNamed(KeyboardTypes.PART_WIDTH).orElseThrow();

        assertThat(width.minimum()).isEqualTo(16);
        assertThat(width.maximum()).isEqualTo(64);
        assertThat(width.group()).contains("piece");
        assertThat(width.displayOnly()).isFalse();
    }

    @Test
    void aWiderKeyboardKeepsItsQueue() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
        KeyboardBehavior before = new KeyboardBehavior(new HashMap<>(type.resolveParameters(Map.of())));
        before.acceptBytes(new NoContext(), "hola".getBytes());
        KeyboardBehavior after = new KeyboardBehavior(new HashMap<>(type.resolveParameters(
                Map.of(KeyboardTypes.PART_WIDTH, 40))));

        after.adoptStateFrom(before);

        assertThat(after.contents()).containsExactly((int) 'h', (int) 'o', (int) 'l', (int) 'a');
    }

    @Test
    void itsParametersHaveTheirDefaultsAndHideTheLink() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);

        assertThat(type.resolveParameters(Map.of())).containsEntry("capacity", 64).containsEntry("data-width", "8")
                .containsEntry("encoding", "utf8").containsEntry("edge", "rising").containsEntry("enter-sends", "lf")
                .containsEntry("backspace-sends", "bs").containsEntry("arrows-as-sequences", false)
                .containsEntry("tri-state", false).containsEntry("initial-text", "")
                .containsEntry("linked-terminal", "").containsEntry("typing-sound", false).containsEntry("capture-color", "automatic")
                .containsEntry("part-width", 16).hasSize(13);
        assertThat(type.parameters().stream().filter(parameter -> parameter.name().equals("linked-terminal"))
                .findFirst().orElseThrow().hidden()).isTrue();
    }

    @Test
    void theHeadIsOnDBeforeTheEdgeAndPoppedAfterIt() {
        BlockHarness harness = keyboard(Map.of());
        assertThat(harness.output(AV)).isEqualTo(Value.ZERO);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 0));

        harness.typeText("ab");
        harness.settle();
        assertThat(harness.output(AV)).isEqualTo(Value.ONE);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'a'));

        clockOnce(harness);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'b'));
        clockOnce(harness);
        assertThat(harness.output(AV)).isEqualTo(Value.ZERO);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 0));
    }

    @Test
    void theFallingEdgeParameterPopsOnTheFallingEdge() {
        BlockHarness harness = keyboard(Map.of("edge", "falling"));
        harness.typeText("ab");
        harness.settle();

        harness.set(CLK, 1).settle();
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'a'));
        harness.set(CLK, 0).settle();
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'b'));
    }

    @Test
    void readEnableOffHoldsTheQueueAndAnUnconnectedOneReads() {
        BlockHarness held = keyboard(Map.of());
        held.typeText("ab");
        held.set(RE, 0).settle();
        clockOnce(held);
        assertThat(held.output(D)).isEqualTo(Value.of(8, 'a'));
        held.set(RE, 1).settle();
        clockOnce(held);
        assertThat(held.output(D)).isEqualTo(Value.of(8, 'b'));

        BlockHarness free = new BlockHarness(StandardComponentTypes.KEYBOARD, Map.of(), RE).settle();
        free.typeText("ab");
        free.settle();
        clockOnce(free);
        assertThat(free.output(D)).isEqualTo(Value.of(8, 'b'));
    }

    @Test
    void clearEmptiesTheQueueAndWinsOverTheClock() {
        BlockHarness harness = keyboard(Map.of());
        harness.typeText("abc");
        harness.settle();

        harness.set(CLR, 1).settle();
        assertThat(harness.output(AV)).isEqualTo(Value.ZERO);
        harness.typeText("x");
        harness.settle();
        // CLR still high: a clock edge clears instead of popping
        clockOnce(harness);
        assertThat(harness.output(AV)).isEqualTo(Value.ZERO);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 0));
    }

    @Test
    void anUndefinedOrConflictingClearDoesNotClearAndTheEdgeStillPopsAsInLogisim() {
        BlockHarness harness = keyboard(Map.of());
        harness.typeText("abc");
        harness.settle();

        harness.conflict(CLR).settle();
        assertThat(harness.output(AV)).as("only CLR = 1 clears").isEqualTo(Value.ONE);
        clockOnce(harness);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'b'));
        harness.release(CLR).settle();
        clockOnce(harness);
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'c'));
    }

    @Test
    void onlyReadEnableAtZeroHoldsTheQueueAsInLogisim() {
        BlockHarness harness = keyboard(Map.of());
        harness.typeText("ab");
        harness.settle();

        harness.conflict(RE).settle();
        clockOnce(harness);

        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'b'));
    }

    @Test
    void aFullQueueRefusesAndReportsZeroAccepted() {
        BlockHarness harness = keyboard(Map.of("capacity", 2));

        assertThat(harness.typeBytes(new byte[]{'a'})).isEqualTo(1);
        assertThat(harness.typeBytes(new byte[]{'b'})).isEqualTo(1);
        assertThat(harness.typeBytes(new byte[]{'c'})).isZero();
        harness.settle();
        assertThat(((VisibleState.KeyboardState) harness.state()).queue()).containsExactly((int) 'a', (int) 'b');
    }

    @Test
    void aMultiByteCharacterIsRefusedWholeWhenItDoesNotFit() {
        BlockHarness harness = keyboard(Map.of("capacity", 2));
        harness.typeBytes(new byte[]{'a'});

        assertThat(harness.typeBytes(new byte[]{(byte) 0xC3, (byte) 0xB1})).isZero();
        assertThat(((VisibleState.KeyboardState) harness.state()).size()).isEqualTo(1);
        assertThat(harness.typeBytes(new byte[]{'b'})).isEqualTo(1);
    }

    @Test
    void triStateLetsGoOfDWhileReadEnableIsOff() {
        BlockHarness harness = keyboard(Map.of("tri-state", true));
        harness.typeText("a");
        harness.settle();
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'a'));

        harness.set(RE, 0).settle();
        assertThat(harness.output(D)).isEqualTo(Value.highImpedance(8));
        harness.set(RE, 1).settle();
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'a'));
    }

    @Test
    void theInitialTextFillsTheQueueAfterEveryRestart() {
        BlockHarness harness = keyboard(Map.of("initial-text", "hi\nyo"));
        harness.typeText("zz");
        harness.settle();

        harness.restart().settle();

        assertThat(((VisibleState.KeyboardState) harness.state()).queue())
                .containsExactly((int) 'h', (int) 'i', 10, (int) 'y', (int) 'o');
        assertThat(harness.output(D)).isEqualTo(Value.of(8, 'h'));
    }

    @Test
    void enterInTheInitialTextFollowsEnterSends() {
        assertThat(initialQueue(Map.of("initial-text", "a\nb", "enter-sends", "cr")))
                .containsExactly((int) 'a', 13, (int) 'b');
        assertThat(initialQueue(Map.of("initial-text", "a\nb", "enter-sends", "crlf")))
                .containsExactly((int) 'a', 13, 10, (int) 'b');
    }

    @Test
    void theInitialTextUsesTheEncodingOfThePart() {
        assertThat(initialQueue(Map.of("initial-text", "ñ"))).containsExactly(0xC3, 0xB1);
        assertThat(initialQueue(Map.of("initial-text", "ñ", "encoding", "latin1"))).containsExactly(0xF1);
        assertThat(initialQueue(Map.of("initial-text", "ñ", "data-width", "7"))).isEmpty();
    }

    private static List<Integer> initialQueue(Map<String, Object> parameters) {
        return ((VisibleState.KeyboardState) keyboard(parameters).state()).queue();
    }

    @Test
    void theArrowsAndDeleteEditTheQueueAtTheCursor() {
        BlockHarness harness = keyboard(Map.of());
        harness.typeText("abc");

        harness.editTyping(TypingEdit.CURSOR_LEFT);
        harness.editTyping(TypingEdit.CURSOR_LEFT);
        harness.typeText("X");
        VisibleState.KeyboardState typed = (VisibleState.KeyboardState) harness.state();
        assertThat(typed.queue()).containsExactly((int) 'a', (int) 'X', (int) 'b', (int) 'c');
        assertThat(typed.cursor()).isEqualTo(2);

        harness.editTyping(TypingEdit.DELETE);
        harness.editTyping(TypingEdit.CURSOR_HOME);
        harness.editTyping(TypingEdit.CURSOR_RIGHT);
        harness.editTyping(TypingEdit.CURSOR_END);
        VisibleState.KeyboardState edited = (VisibleState.KeyboardState) harness.state();
        assertThat(edited.queue()).containsExactly((int) 'a', (int) 'X', (int) 'c');
        assertThat(edited.cursor()).isEqualTo(3);
        assertThat(edited.version()).isGreaterThan(typed.version());
    }

    @Test
    void aRebuiltKeyboardKeepsItsQueueTruncatedToTheNewCapacity() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
        KeyboardBehavior before = new KeyboardBehavior(new HashMap<>(type.resolveParameters(Map.of("capacity", 8))));
        before.acceptBytes(new NoContext(), "hello".getBytes());
        KeyboardBehavior after = new KeyboardBehavior(new HashMap<>(type.resolveParameters(Map.of("capacity", 3))));

        after.adoptStateFrom(before);

        assertThat(after.contents()).containsExactly((int) 'h', (int) 'e', (int) 'l');
    }

    @Test
    void shrinkingTheQueueNeverKeepsHalfAUtf8Character() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
        KeyboardBehavior before = new KeyboardBehavior(new HashMap<>(type.resolveParameters(Map.of("capacity", 8))));
        before.acceptBytes(new NoContext(), "ab\u00f1c".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // a, b, then ñ in two bytes, then c: a capacity of 3 would cut ñ in half
        KeyboardBehavior after = new KeyboardBehavior(new HashMap<>(type.resolveParameters(Map.of("capacity", 3))));

        after.adoptStateFrom(before);

        assertThat(after.contents()).containsExactly((int) 'a', (int) 'b');
    }

    @Test
    void aNewInitialTextReplacesTheQueue() {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
        KeyboardBehavior before = new KeyboardBehavior(new HashMap<>(type.resolveParameters(
                Map.of("initial-text", "old"))));
        before.acceptBytes(new NoContext(), "typed".getBytes());
        KeyboardBehavior after = new KeyboardBehavior(new HashMap<>(type.resolveParameters(
                Map.of("initial-text", "new"))));

        after.adoptStateFrom(before);
        after.initialize(new NoContext());

        assertThat(after.contents()).containsExactly((int) 'n', (int) 'e', (int) 'w');
    }

    @Test
    void theStateCarriesAtMostTheFirstSixtyFourBytes() {
        BlockHarness harness = keyboard(Map.of("capacity", 200));
        harness.typeText("x".repeat(100));

        VisibleState.KeyboardState state = (VisibleState.KeyboardState) harness.state();

        assertThat(state.queue()).hasSize(VisibleState.KeyboardState.SHOWN_BYTES);
        assertThat(state.size()).isEqualTo(100);
    }

    /** A context that ignores everything: the queue is what is under test. */
    private static final class NoContext implements cl.almejo.vsim.core.component.BehaviorContext {
        @Override
        public Value input(int pinIndex) {
            return Value.HIGH_IMPEDANCE;
        }

        @Override
        public void drive(int pinIndex, Value value, long delay) {
        }

        @Override
        public void schedule(long delay, Runnable action) {
        }

        @Override
        public long now() {
            return 0;
        }
    }
}
