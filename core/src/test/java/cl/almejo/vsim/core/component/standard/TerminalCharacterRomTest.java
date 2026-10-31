package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibilityCondition;
import cl.almejo.vsim.core.component.VisibleState.TerminalState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Generador de caracteres: Memoria externa» (spec §8.8, decision 8): the Terminal drives CODE and ROW, reads PIX at
 * the end of each «Tiempo de lectura» slot and keeps the glyphs of the codes on its screen; a ROM 2K × 8 wired as
 * address = code × 8 + row gives them.
 */
class TerminalCharacterRomTest {

    private static final int DATA = 0;
    private static final int CLOCK = 1;
    private static final int WRITE_ENABLE = 2;
    private static final int CODE = 4;
    private static final int ROW = 5;
    private static final int PIX = 6;
    private static final int READ_TIME = 10;
    private static final int GLYPH_ROWS = 8;

    private static final ComponentType TERMINAL = StandardComponentTypes.registry()
            .require(StandardComponentTypes.TERMINAL);

    /** The glyphs of the test ROM, one PIX byte per row (bit 7 the leftmost pixel). */
    static final Map<Character, int[]> GLYPHS = Map.of(
            'H', new int[] {0x82, 0x82, 0x82, 0xFE, 0x82, 0x82, 0x82, 0x00},
            'O', new int[] {0x7C, 0x82, 0x82, 0x82, 0x82, 0x82, 0x7C, 0x00},
            'L', new int[] {0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0xFE, 0x00},
            'A', new int[] {0x38, 0x44, 0x82, 0xFE, 0x82, 0x82, 0x82, 0x00});

    private static Map<String, Object> external(Map<String, Object> extra) {
        Map<String, Object> parameters = new HashMap<>(ParameterProfiles.apply(TERMINAL,
                Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20), TerminalTypes.PROFILE_VT));
        parameters.put(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL);
        parameters.putAll(extra);
        return parameters;
    }

    /** The ROM's contents: {@link #GLYPHS} at address code × 8 + row, in the memory text format. */
    static String romContents() {
        StringBuilder text = new StringBuilder();
        GLYPHS.forEach((code, rows) -> {
            text.append('@').append(Integer.toHexString(code * GLYPH_ROWS));
            for (int row : rows) {
                text.append(' ').append(String.format("%02X", row));
            }
            text.append('\n');
        });
        return text.toString();
    }

    // --- the type -------------------------------------------------------------------------------------------------

    @Test
    void theExternalGeneratorAddsCodeRowAndPixOnTheRightEdge() {
        ComponentType internal = TERMINAL.specializedFor(Map.of());
        ComponentType rom = TERMINAL.specializedFor(Map.of(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL));

        assertThat(internal.pins()).extracting(PinSpec::name).containsExactly("D", "CLK", "WE", "CLR");
        assertThat(rom.pins()).extracting(PinSpec::name).containsExactly("D", "CLK", "WE", "CLR", "CODE", "ROW",
                "PIX");
        int right = rom.size().width();
        assertThat(rom.pin(CODE).position()).isEqualTo(new GridPoint(right, 3));
        assertThat(rom.pin(ROW).position()).isEqualTo(new GridPoint(right, 5));
        assertThat(rom.pin(PIX).position()).isEqualTo(new GridPoint(right, 7));
        assertThat(rom.pin(CODE).direction()).isEqualTo(PinDirection.OUTPUT);
        assertThat(rom.pin(ROW).direction()).isEqualTo(PinDirection.OUTPUT);
        assertThat(rom.pin(PIX).direction()).isEqualTo(PinDirection.INPUT);
        assertThat(rom.pin(CODE).width()).isEqualTo(8);
        assertThat(rom.pin(ROW).width()).isEqualTo(3);
        assertThat(rom.pin(PIX).width()).isEqualTo(8);
    }

    @Test
    void aOneRowScreenStillHasRoomForPixAboveTheCornerGrip() {
        ComponentType tiny = TERMINAL.specializedFor(Map.of(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_EXTERNAL,
                TerminalTypes.ROWS, 1, TerminalTypes.COLUMNS, 1, TerminalTypes.FONT_SIZE,
                TerminalTypes.FONT_SIZE_SMALL));

        assertThat(tiny.size().height()).as("PIX at y = 7 stays inside the right edge, away from the corner")
                .isGreaterThan(8);
        assertThat(TERMINAL.specializedFor(Map.of(TerminalTypes.ROWS, 1, TerminalTypes.COLUMNS, 1,
                TerminalTypes.FONT_SIZE, TerminalTypes.FONT_SIZE_SMALL)).size().height())
                .as("the internal generator keeps its size").isLessThan(8);
    }

    @Test
    void theReadTimeIsAnIntegerShownOnlyWithTheExternalGenerator() {
        ParameterSpec readTime = TERMINAL.parameterNamed(TerminalTypes.GLYPH_READ_TIME).orElseThrow();

        assertThat(TERMINAL.resolveParameters(Map.of())).containsEntry(TerminalTypes.GLYPH_READ_TIME, 10);
        assertThat(readTime.minimum()).isEqualTo(1);
        assertThat(readTime.maximum()).isEqualTo(1000);
        assertThat(readTime.visibility()).contains(new VisibilityCondition(TerminalTypes.GLYPHS,
                TerminalTypes.GLYPHS_EXTERNAL));
        assertThat(readTime.displayOnly()).isFalse();
        assertThat(TERMINAL.profile(TerminalTypes.PROFILE_VT).orElseThrow().values())
                .doesNotContainKey(TerminalTypes.GLYPH_READ_TIME);
    }

    // --- the scan, with a fake ROM: PIX driven by the test -------------------------------------------------------

    private static BlockHarness harness(Map<String, Object> extra) {
        return new BlockHarness(StandardComponentTypes.TERMINAL, external(extra)).set(WRITE_ENABLE, 1).settle();
    }

    private static void pulse(BlockHarness harness, int value) {
        harness.set(DATA, value).set(CLOCK, 0).advance(1).set(CLOCK, 1);
    }

    private static GlyphCache glyphs(BlockHarness harness) {
        return ((TerminalState) harness.state()).glyphs();
    }

    @Test
    void anInternalGeneratorNeverScans() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TERMINAL,
                ParameterProfiles.apply(TERMINAL, Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20),
                        TerminalTypes.PROFILE_VT)).set(WRITE_ENABLE, 1).settle();

        pulse(harness, 'H');

        assertThat(harness.engine().nextEventTime()).isEmpty();
        assertThat(((TerminalState) harness.state()).externalGlyphs()).isFalse();
    }

    @Test
    void eachRowOfANewCodeIsDrivenForOneReadTimeAndPixIsReadAtTheEndOfTheSlot() {
        BlockHarness harness = harness(Map.of());
        harness.set(PIX, 0x81);
        pulse(harness, 'H');
        long start = harness.now();

        for (int row = 0; row < GLYPH_ROWS; row++) {
            assertThat(harness.output(CODE)).as("CODE in slot " + row).isEqualTo(Value.of(8, 'H'));
            assertThat(harness.output(ROW)).as("ROW in slot " + row).isEqualTo(Value.of(3, row));
            harness.advance(READ_TIME - 1);
            assertThat(glyphs(harness).has('H')).as("not before the last slot ends").isFalse();
            harness.advance(1);
        }

        assertThat(harness.now()).isEqualTo(start + GLYPH_ROWS * READ_TIME);
        assertThat(glyphs(harness).has('H')).isTrue();
        assertThat(glyphs(harness).row('H', 0)).isEqualTo(0x81);
        assertThat(harness.engine().nextEventTime()).as("the scan ends: run until stable ends").isEmpty();
        assertThat(harness.output(CODE)).as("CODE stays at the last value").isEqualTo(Value.of(8, 'H'));
        assertThat(harness.output(ROW)).as("ROW stays at the last value").isEqualTo(Value.of(3, 7));
    }

    @Test
    void theReadTimeSetsTheSlot() {
        BlockHarness harness = harness(Map.of(TerminalTypes.GLYPH_READ_TIME, 3));
        pulse(harness, 'H');
        long start = harness.now();

        harness.advance(GLYPH_ROWS * 3 - 1);
        assertThat(glyphs(harness).has('H')).isFalse();
        harness.advance(1);
        assertThat(glyphs(harness).has('H')).isTrue();
        assertThat(harness.now()).isEqualTo(start + 24);
    }

    @Test
    void aCodeAlreadyCachedIsNotScannedAgain() {
        BlockHarness harness = harness(Map.of());
        pulse(harness, 'H');
        harness.settle();
        GlyphCache cached = glyphs(harness);

        pulse(harness, 'H');
        pulse(harness, 'H');

        assertThat(harness.engine().nextEventTime()).as("nothing to scan").isEmpty();
        assertThat(glyphs(harness).generation()).isEqualTo(cached.generation());
    }

    @Test
    void undefinedPixBitsPaintAsOffAndTheCodeIsRetriedWhenItIsPrintedAgain() {
        BlockHarness harness = harness(Map.of());
        harness.release(PIX);
        pulse(harness, 'H');
        harness.settle();
        assertThat(glyphs(harness).has('H')).as("kept, blank, to paint meanwhile").isTrue();
        assertThat(glyphs(harness).row('H', 0)).isZero();

        harness.set(PIX, 0xFF);
        pulse(harness, 'H');
        assertThat(harness.engine().nextEventTime()).as("the code is scanned again").isPresent();
        harness.settle();
        assertThat(glyphs(harness).row('H', 3)).isEqualTo(0xFF);
    }

    @Test
    void aCodeThatDoesNotFitInCodeUsesTheInternalFontAndIsNeverScanned() {
        BlockHarness harness = harness(Map.of());
        for (byte value : "€".getBytes(StandardCharsets.UTF_8)) {
            pulse(harness, value & 0xFF);
        }

        assertThat(harness.engine().nextEventTime()).isEmpty();
        assertThat(glyphs(harness).size()).isZero();
    }

    @Test
    void aRebuiltTerminalKeepsItsGlyphsAndScansWhatItsScreenNeeds() {
        Map<String, Object> internal = new HashMap<>(external(Map.of()));
        internal.put(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_INTERNAL);
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TERMINAL, internal).set(WRITE_ENABLE, 1)
                .settle();
        pulse(harness, 'H');
        harness.settle();

        harness.reconfigure(external(Map.of())).set(WRITE_ENABLE, 1).set(PIX, 0x18).settle();
        assertThat(glyphs(harness).row('H', 0)).as("the screen's H is scanned").isEqualTo(0x18);

        harness.reconfigure(external(Map.of(TerminalTypes.GLYPH_READ_TIME, 20))).set(WRITE_ENABLE, 1).settle();
        assertThat(glyphs(harness).has('H')).as("a rebuilt part keeps its glyphs").isTrue();
    }

    // --- the real thing: a ROM 2K × 8 wired as address = code × 8 + row ---------------------------------------------

    /**
     * A Terminal with the external generator; CODE and ROW go through two bus splitters into an 11-bit bus joiner
     * (ROW on bits 0–2, CODE on bits 3–10) to the A of a ROM 2K × 8 holding {@link #GLYPHS}, whose Q goes to PIX.
     */
    private static FlatCircuit romCircuit() {
        Map<String, Object> terminalParameters = external(Map.of());
        ComponentType terminal = TERMINAL.specializedFor(terminalParameters);
        Map<String, Object> romParameters = Map.of(MemoryTypes.WORDS, "2048",
                MemoryTypes.WIDTH, 8, MemoryTypes.CONTENTS,
                MemoryContents.decode(romContents()).encode());
        List<FlatComponent> components = new ArrayList<>(List.of(
                new FlatComponent("terminal", terminal, terminalParameters),
                new FlatComponent("rom", type(StandardComponentTypes.ROM, romParameters), romParameters),
                bus(StandardComponentTypes.BUS_SPLITTER, "codes", 8),
                bus(StandardComponentTypes.BUS_SPLITTER, "rows", 3),
                bus(StandardComponentTypes.BUS_JOINER, "address", 11),
                source("data", 8, "0"), source("clock", 1, "0"), source("we", 1, "1")));
        List<FlatNet> nets = new ArrayList<>(List.of(
                net("d", 8, pin("terminal", DATA), pin("data", 0)),
                net("clk", 1, pin("terminal", CLOCK), pin("clock", 0)),
                net("we", 1, pin("terminal", WRITE_ENABLE), pin("we", 0)),
                net("code", 8, pin("terminal", CODE), pin("codes", 0)),
                net("row", 3, pin("terminal", ROW), pin("rows", 0)),
                net("a", 11, pin("address", 0), pin("rom", 0)),
                net("q", 8, pin("rom", 2), pin("terminal", PIX))));
        for (int bit = 0; bit < 3; bit++) {
            nets.add(net("row" + bit, 1, pin("rows", 1 + bit), pin("address", 1 + bit)));
        }
        for (int bit = 0; bit < 8; bit++) {
            nets.add(net("code" + bit, 1, pin("codes", 1 + bit), pin("address", 1 + 3 + bit)));
        }
        return new FlatCircuit(components, nets);
    }

    private static ComponentType type(String typeId, Map<String, Object> parameters) {
        return StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
    }

    private static FlatComponent bus(String typeId, String id, int width) {
        Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, width);
        return new FlatComponent(id, type(typeId, parameters), parameters);
    }

    private static FlatComponent source(String id, int width, String value) {
        Map<String, Object> parameters = Map.of("value", value, InterfacePins.WIDTH, width);
        return new FlatComponent(id, type(InterfacePins.INPUT_PIN, parameters), parameters);
    }

    private static FlatPinRef pin(String componentId, int pinIndex) {
        return new FlatPinRef(componentId, pinIndex);
    }

    private static FlatNet net(String id, int width, FlatPinRef... pins) {
        return new FlatNet(id, List.of(pins), width, false, List.of());
    }

    private static void write(SimulationEngine engine, String text) {
        for (char character : text.toCharArray()) {
            engine.setInput("data", Value.of(8, character));
            engine.setInput("clock", Value.of(1, 0));
            engine.advance(1);
            engine.setInput("clock", Value.of(1, 1));
            engine.advance(1);
        }
    }

    private static GlyphCache glyphs(SimulationEngine engine) {
        return ((TerminalState) engine.snapshot().componentState("terminal")).glyphs();
    }

    private static void assertHolaGlyphs(GlyphCache cache) {
        GLYPHS.forEach((code, rows) -> {
            for (int row = 0; row < GLYPH_ROWS; row++) {
                assertThat(cache.row(code, row)).as(code + " row " + row).isEqualTo(rows[row]);
            }
        });
    }

    @Test
    void aRomWithTheGlyphsOfHolaFillsTheCacheInOneScanOfPairsTimesTheReadTime() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(romCircuit());
        engine.setInput("data", Value.of(8, 'H'));
        engine.setInput("clock", Value.of(1, 1));
        long start = engine.now();
        engine.advance(1);
        write(engine, "OLA");

        long pairs = 4L * GLYPH_ROWS;
        engine.advance(start + pairs * READ_TIME - 1 - engine.now());
        assertThat(glyphs(engine).has('A')).as("the last pair is read at the end of its slot").isFalse();
        engine.advance(1);

        assertHolaGlyphs(glyphs(engine));
        assertThat(glyphs(engine).size()).isEqualTo(4);
        assertThat(engine.nextEventTime()).as("the scan ends").isEmpty();
    }

    @Test
    void reiniciarAndRereadClearTheCacheAndScanAgain() {
        SimulationEngine engine = new SimulationEngine();
        FlatCircuit circuit = romCircuit();
        engine.update(circuit);
        write(engine, "HOLA");
        engine.advance(1_000);
        assertHolaGlyphs(glyphs(engine));

        engine.rereadTerminalGlyphs("terminal");
        assertThat(glyphs(engine).size()).as("«Releer» clears at once").isZero();
        engine.advance(4L * GLYPH_ROWS * READ_TIME);
        assertHolaGlyphs(glyphs(engine));
        assertThat(engine.nextEventTime()).isEmpty();

        engine.setInput("clock", Value.of(1, 0)); // the switches keep their values: no clock edge at the restart
        engine.restart(circuit, Map.of());
        assertThat(glyphs(engine).size()).as("«Reiniciar» starts with no glyph").isZero();
        engine.advance(READ_TIME);
        assertThat(engine.nextEventTime()).as("an empty screen needs no scan").isEmpty();
        write(engine, "LA");
        engine.advance(1_000);
        assertThat(glyphs(engine).size()).isEqualTo(2);
        assertThat(glyphs(engine).row('L', 6)).isEqualTo(0xFE);
    }

    @Test
    void theStateChangesWhenAGlyphArrivesEvenIfTheScreenDoesNot() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(romCircuit());
        write(engine, "H");
        TerminalState before = (TerminalState) engine.snapshot().componentState("terminal");

        engine.advance(1_000);
        TerminalState after = (TerminalState) engine.snapshot().componentState("terminal");

        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after).as("the painter must see the new glyph").isNotEqualTo(before);
    }
}
