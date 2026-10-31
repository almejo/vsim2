package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.TerminalState;
import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.core.terminal.ColorReference;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The Terminal's pins and screen (spec §2, §5, §7, §8.7, §11): edge, WE, CLR, undefined data, profiles, adoption. */
class TerminalBehaviorTest {

    private static final int DATA = 0;
    private static final int CLOCK = 1;
    private static final int WRITE_ENABLE = 2;
    private static final int CLEAR = 3;

    private static final ComponentType TERMINAL = StandardComponentTypes.registry()
            .require(StandardComponentTypes.TERMINAL);

    /** The parameters of {@code profile} on a {@code rows} × {@code columns} screen, plus {@code extra}. */
    private static Map<String, Object> profile(String profile, int rows, int columns, Map<String, Object> extra) {
        Map<String, Object> parameters = new HashMap<>(ParameterProfiles.apply(TERMINAL,
                Map.of(TerminalTypes.ROWS, rows, TerminalTypes.COLUMNS, columns), profile));
        parameters.putAll(extra);
        return parameters;
    }

    private static BlockHarness terminal(String profile, Map<String, Object> extra) {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TERMINAL, profile(profile, 4, 20, extra));
        return harness.set(WRITE_ENABLE, 1).settle();
    }

    /** One rising-edge clock pulse with {@code value} on D. */
    private static void pulse(BlockHarness harness, int value) {
        harness.set(DATA, value).set(CLOCK, 0).settle().set(CLOCK, 1).settle();
    }

    private static void type(BlockHarness harness, byte[] bytes) {
        for (byte value : bytes) {
            pulse(harness, value & 0xFF);
        }
    }

    private static void type(BlockHarness harness, String text) {
        type(harness, text.getBytes(StandardCharsets.UTF_8));
    }

    private static TerminalFrame frame(BlockHarness harness) {
        return ((TerminalState) harness.state()).frame();
    }

    private static String text(BlockHarness harness) {
        return frame(harness).text().stripTrailing();
    }

    @Test
    void itWritesOnTheChosenEdgeOnly() {
        BlockHarness rising = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of());
        rising.set(DATA, 'a').set(CLOCK, 1).settle();
        assertThat(text(rising)).as("rising edge").startsWith("a");
        rising.set(DATA, 'b').set(CLOCK, 0).settle();
        assertThat(text(rising)).as("the falling edge writes nothing").startsWith("a").doesNotContain("b");

        BlockHarness falling = terminal(TerminalTypes.PROFILE_LOGISIM,
                Map.of(StandardParameters.EDGE.name(), StandardParameters.FALLING));
        falling.set(DATA, 'a').set(CLOCK, 1).settle();
        assertThat(text(falling)).isBlank();
        falling.set(CLOCK, 0).settle();
        assertThat(text(falling)).startsWith("a");
    }

    @Test
    void writeEnableLowHoldsTheScreen() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_VT, Map.of());
        harness.set(WRITE_ENABLE, 0).settle();

        type(harness, "abc");

        assertThat(text(harness)).isBlank();
    }

    @Test
    void anUnconnectedWriteEnableWritesInLogisimModeOnly() {
        BlockHarness logisim = new BlockHarness(StandardComponentTypes.TERMINAL,
                profile(TerminalTypes.PROFILE_LOGISIM, 4, 20, Map.of()), WRITE_ENABLE);
        BlockHarness vt = new BlockHarness(StandardComponentTypes.TERMINAL,
                profile(TerminalTypes.PROFILE_VT, 4, 20, Map.of()), WRITE_ENABLE);

        type(logisim, "ok");
        type(vt, "ok");

        assertThat(text(logisim)).startsWith("ok");
        assertThat(text(vt)).isBlank();
    }

    @Test
    void clearWinsOverTheClockAndEmptiesTheScreen() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of());
        type(harness, "hola");
        assertThat(text(harness)).startsWith("hola");

        harness.set(CLEAR, 1).settle();
        assertThat(text(harness)).isBlank();
        pulse(harness, 'x');
        assertThat(text(harness)).as("CLR held high wins over the clock").isBlank();

        harness.set(CLEAR, 0).settle();
        pulse(harness, 'y');
        assertThat(text(harness)).isEqualTo("y");
        assertThat(frame(harness).cursorColumn()).isEqualTo(1);
    }

    @Test
    void anUndefinedDataByteWritesAQuestionMark() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of());
        type(harness, "a");
        harness.release(DATA).set(CLOCK, 0).settle().set(CLOCK, 1).settle();
        harness.conflict(DATA).set(CLOCK, 0).settle().set(CLOCK, 1).settle();

        assertThat(text(harness)).startsWith("a??");
    }

    @Test
    void sevenBitDataIsSevenPinsAndAnEightBitSevenBitModeMasks() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of());
        assertThat(harness.type().pin(DATA).width()).isEqualTo(7);
        type(harness, "Az");

        assertThat(text(harness)).startsWith("Az");
    }

    @Test
    void theLrv32imFactorialSessionLooksAsInLogisim() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of());

        type(harness, "? 5\nfact(5)=120\n? ");

        assertThat(text(harness)).isEqualTo("? 5\nfact(5)=120\n?");
        assertThat(frame(harness).cursorRow()).isEqualTo(2);
        assertThat(frame(harness).cursorColumn()).isEqualTo(2);
    }

    @Test
    void theVtProfileReadsColoursAndUtf8() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_VT, Map.of());

        type(harness, "\u001b[31mñ\u001b[0m!");

        TerminalCell first = frame(harness).cell(0, 0);
        assertThat(first.codePoint()).isEqualTo('ñ');
        assertThat(first.style().foreground()).contains(new ColorReference.PaletteIndex(1));
        assertThat(frame(harness).cell(0, 1).style().foreground()).isEmpty();
        assertThat(text(harness)).isEqualTo("ñ!");
    }

    @Test
    void changingTheRowsOrTheModeKeepsTheTextAndALookParameterKeepsTheBehaviour() {
        Map<String, Object> parameters = profile(TerminalTypes.PROFILE_VT, 4, 20, Map.of());
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TERMINAL, parameters)
                .set(WRITE_ENABLE, 1).settle();
        type(harness, "uno\ndos\ntres");

        Map<String, Object> fewerRows = new HashMap<>(parameters);
        fewerRows.put(TerminalTypes.ROWS, 2);
        harness.reconfigure(fewerRows).set(WRITE_ENABLE, 1).settle();
        assertThat(text(harness)).isEqualTo("dos\ntres");

        Map<String, Object> logisimMode = new HashMap<>(fewerRows);
        logisimMode.put(TerminalTypes.MODE, TerminalTypes.MODE_LOGISIM);
        harness.reconfigure(logisimMode).set(WRITE_ENABLE, 1).settle();
        assertThat(text(harness)).isEqualTo("dos\ntres");

        TerminalState before = (TerminalState) harness.state();
        Map<String, Object> strongEffect = new HashMap<>(logisimMode);
        strongEffect.put(TerminalTypes.EFFECT, "strong");
        strongEffect.put(TerminalTypes.SCREEN_COLORS, "amber");
        harness.reconfigure(strongEffect);
        assertThat(((TerminalState) harness.state()).owner()).as("same behaviour").isEqualTo(before.owner());
    }

    @Test
    void restartClearsTheScreen() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_VT, Map.of());
        type(harness, "hola");
        TerminalState before = (TerminalState) harness.state();

        harness.restart().settle();

        TerminalState after = (TerminalState) harness.state();
        assertThat(after.frame().text()).isBlank();
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void stopOnPausesOnceWhenTheTextArrivesAndAgainAfterMoreText() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of(TerminalTypes.STOP_ON, "=120"));

        type(harness, "fact(5)=12");
        assertThat(harness.takePauseRequest()).isEmpty();
        type(harness, "0");
        Optional<PauseNotice> notice = harness.takePauseRequest();
        assertThat(notice).isPresent();
        assertThat(notice.get().messageKey()).isEqualTo(TerminalBehavior.STOPPED_MESSAGE);
        assertThat(notice.get().arguments()).containsExactly("=120");

        pulse(harness, 0x07);
        assertThat(harness.takePauseRequest()).as("a byte that adds no text does not stop again").isEmpty();
        type(harness, "\nfact(5)=120");
        assertThat(harness.takePauseRequest()).as("more text, then the same match").isPresent();
    }

    @Test
    void anEmptyStopTextNeverPauses() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_LOGISIM, Map.of());

        type(harness, "anything");

        assertThat(harness.takePauseRequest()).isEmpty();
    }

    @Test
    void theStateComparesByOwnerAndVersionOnly() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_VT, Map.of());
        TerminalState first = (TerminalState) harness.state();
        assertThat(harness.state()).as("rebuilt only on change").isSameAs(first);

        type(harness, "a");
        TerminalState second = (TerminalState) harness.state();
        assertThat(second).isNotEqualTo(first);
        assertThat(second.version()).isGreaterThan(first.version());

        TerminalState sameVersion = new TerminalState(second.owner(), second.version(), first.frame(), false);
        assertThat(sameVersion).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(new TerminalState(second.owner() + 1, second.version(), second.frame(), false))
                .isNotEqualTo(second);
    }

    @Test
    void aTerminalStateIsNeverTheEmptyState() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_VT, Map.of());

        assertThat(harness.state()).isInstanceOf(TerminalState.class).isNotEqualTo(VisibleState.NONE);
        assertThat(((TerminalState) harness.state()).externalGlyphs()).isFalse();
    }

    @Test
    void theEngineGivesTheTerminalAndClearsItLikeClr() {
        BlockHarness harness = terminal(TerminalTypes.PROFILE_VT, Map.of());
        type(harness, "hola");

        assertThat(harness.engine().terminal("dut")).isPresent();
        assertThat(harness.engine().terminal("missing")).isEmpty();
        assertThat(harness.engine().terminal("source0")).isEmpty();
        harness.engine().clearTerminal("dut");

        assertThat(text(harness)).isBlank();
        assertThat(harness.engine().terminal("dut").orElseThrow().screen().byteLog().lastSequence())
                .as("clearing the screen keeps the byte log").isEqualTo(4);
    }
}
