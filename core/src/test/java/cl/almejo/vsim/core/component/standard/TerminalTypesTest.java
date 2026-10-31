package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalGeometry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Terminal type: its place in the palette, its parameters and profiles, its size and its pins. */
class TerminalTypesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType terminal = registry.require(StandardComponentTypes.TERMINAL);

    @Test
    void itIsAnInputOutputPartAfterTheSevenSegmentDisplays() {
        List<String> inputOutput = registry.all().stream()
                .filter(type -> type.category() == ComponentCategory.INPUT_OUTPUT).map(ComponentType::id).toList();

        assertThat(terminal.category()).isEqualTo(ComponentCategory.INPUT_OUTPUT);
        assertThat(inputOutput.indexOf(StandardComponentTypes.TERMINAL))
                .isGreaterThan(inputOutput.indexOf(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE));
        assertThat(terminal.resolveParameters(Map.of()))
                .containsEntry(TerminalTypes.ROWS, 24).containsEntry(TerminalTypes.COLUMNS, 80);
    }

    @Test
    void aNewTerminalTakesTheVtProfileAndItsValues() {
        Map<String, Object> defaults = terminal.resolveParameters(Map.of());

        assertThat(defaults).containsEntry(TerminalTypes.PROFILE, TerminalTypes.PROFILE_VT)
                .containsEntry(TerminalTypes.SCROLLBACK, 1000).containsEntry(TerminalTypes.WRAP, true)
                .containsEntry(TerminalTypes.GLYPHS, TerminalTypes.GLYPHS_INTERNAL)
                .containsEntry(TerminalTypes.DATA_WIDTH, "8").containsEntry(TerminalTypes.ENCODING, "utf8")
                .containsEntry(StandardParameters.EDGE.name(), StandardParameters.RISING)
                .containsEntry(TerminalTypes.MODE, TerminalTypes.MODE_VT)
                .containsEntry(TerminalTypes.LINE_FEED_RETURNS, true)
                .containsEntry(TerminalTypes.BACKSPACE_ERASES, false)
                .containsEntry(TerminalTypes.FORM_FEED_CLEARS, true).containsEntry(TerminalTypes.TAB_WIDTH, 8)
                .containsEntry(TerminalTypes.SCREEN_COLORS, "white").containsEntry(TerminalTypes.ANSI_PALETTE, "xterm")
                .containsEntry(TerminalTypes.EFFECT, "none").containsEntry(TerminalTypes.FONT, "plex")
                .containsEntry(TerminalTypes.FONT_SIZE, "normal").containsEntry(TerminalTypes.CURSOR, "bar")
                .containsEntry(TerminalTypes.BLINK, true).containsEntry(TerminalTypes.MARK_LAST, true)
                .containsEntry(TerminalTypes.STOP_ON, "").containsEntry(TerminalTypes.EXPECTED, "none")
                .containsEntry(TerminalTypes.WAVEFORM_FORMAT, "chars").containsEntry(TerminalTypes.BELL, "flash")
                .containsEntry(TerminalTypes.TYPING_SOUND, false).containsEntry(TerminalTypes.SHOW, "canvas")
                .containsEntry(TerminalTypes.SAVE_OUTPUT, "no");
        assertThat(ParameterProfiles.differences(terminal, defaults)).isEmpty();
    }

    @Test
    void theProfilesSetTheValuesOfTheSpecTable() {
        Map<String, Object> logisim = ParameterProfiles.apply(terminal, Map.of(TerminalTypes.ROWS, 30),
                TerminalTypes.PROFILE_LOGISIM);
        Map<String, Object> teletype = ParameterProfiles.apply(terminal, Map.of(), TerminalTypes.PROFILE_TELETYPE);

        assertThat(logisim).containsEntry(TerminalTypes.ROWS, 30).containsEntry(TerminalTypes.MODE, "logisim")
                .containsEntry(TerminalTypes.DATA_WIDTH, "7").containsEntry(TerminalTypes.BACKSPACE_ERASES, true)
                .containsEntry(TerminalTypes.FORM_FEED_CLEARS, true).containsEntry(TerminalTypes.SCROLLBACK, 0)
                .containsEntry(TerminalTypes.SCREEN_COLORS, "green").containsEntry(TerminalTypes.EFFECT, "soft")
                .containsEntry(TerminalTypes.FONT, "vt323").containsEntry(TerminalTypes.CURSOR, "block")
                .containsEntry(TerminalTypes.BLINK, true).containsEntry(TerminalTypes.BELL, "none")
                .containsEntry(TerminalTypes.TYPING_SOUND, false).containsEntry(TerminalTypes.MARK_LAST, true);
        assertThat(teletype).containsEntry(TerminalTypes.MODE, "teletype").containsEntry(TerminalTypes.DATA_WIDTH, "7")
                .containsEntry(TerminalTypes.BACKSPACE_ERASES, false)
                .containsEntry(TerminalTypes.FORM_FEED_CLEARS, false).containsEntry(TerminalTypes.SCROLLBACK, 10_000)
                .containsEntry(TerminalTypes.SCREEN_COLORS, "paper").containsEntry(TerminalTypes.ANSI_PALETTE, "vga")
                .containsEntry(TerminalTypes.CURSOR, "underline").containsEntry(TerminalTypes.BLINK, false)
                .containsEntry(TerminalTypes.BELL, "both").containsEntry(TerminalTypes.TYPING_SOUND, true);
        for (String profile : List.of(TerminalTypes.PROFILE_LOGISIM, TerminalTypes.PROFILE_VT,
                TerminalTypes.PROFILE_TELETYPE)) {
            assertThat(terminal.profile(profile).orElseThrow().values()).as(profile)
                    .doesNotContainKeys(TerminalTypes.ROWS, TerminalTypes.COLUMNS, TerminalTypes.STOP_ON,
                            TerminalTypes.EXPECTED, TerminalTypes.WAVEFORM_FORMAT, TerminalTypes.SHOW,
                            TerminalTypes.SAVE_OUTPUT, TerminalTypes.GLYPHS)
                    .containsEntry(StandardParameters.EDGE.name(), StandardParameters.RISING)
                    .containsEntry(TerminalTypes.LINE_FEED_RETURNS, true).containsEntry(TerminalTypes.WRAP, true)
                    .containsEntry(TerminalTypes.TAB_WIDTH, 8).containsEntry(TerminalTypes.FONT_SIZE, "normal");
        }
    }

    @Test
    void theLookParametersNeverRebuildTheBehaviourButTheFontSizeDoes() {
        List<String> displayOnly = terminal.parameters().stream().filter(ParameterSpec::displayOnly)
                .map(ParameterSpec::name).toList();

        assertThat(displayOnly).contains(TerminalTypes.SCREEN_COLORS, TerminalTypes.ANSI_PALETTE,
                TerminalTypes.EFFECT, TerminalTypes.FONT, TerminalTypes.CURSOR, TerminalTypes.BLINK,
                TerminalTypes.MARK_LAST, TerminalTypes.EXPECTED, TerminalTypes.EXPECTED_TEXT,
                TerminalTypes.EXPECTED_FILE, TerminalTypes.WAVEFORM_FORMAT, TerminalTypes.BELL,
                TerminalTypes.TYPING_SOUND, TerminalTypes.SHOW, TerminalTypes.SAVE_OUTPUT, TerminalTypes.OUTPUT_FILE)
                .doesNotContain(TerminalTypes.FONT_SIZE, TerminalTypes.STOP_ON, TerminalTypes.ROWS,
                        TerminalTypes.MODE, TerminalTypes.PROFILE);
        assertThat(terminal.parameterNamed(TerminalTypes.EXPECTED_TEXT).orElseThrow().multiline()).isTrue();
        assertThat(terminal.parameterNamed(TerminalTypes.ROWS).orElseThrow().group()).contains("screen");
        assertThat(terminal.parameterNamed(TerminalTypes.PROFILE).orElseThrow().group()).contains("piece");
        assertThat(terminal.isShown(terminal.parameterNamed(TerminalTypes.ENCODING).orElseThrow(),
                Map.of(TerminalTypes.DATA_WIDTH, "7"))).isFalse();
        assertThat(terminal.isShown(terminal.parameterNamed(TerminalTypes.OUTPUT_FILE).orElseThrow(),
                Map.of(TerminalTypes.SAVE_OUTPUT, "file"))).isTrue();
    }

    @Test
    void theDataPinIsAsWideAsTheDataAndTheFontSizeSetsTheSize() {
        assertThat(terminal.pin(0).width()).isEqualTo(8);
        assertThat(terminal.specializedFor(Map.of(TerminalTypes.DATA_WIDTH, "7")).pin(0).width()).isEqualTo(7);
        assertThat(terminal.specializedFor(Map.of(TerminalTypes.FONT_SIZE, "large")).size())
                .isEqualTo(TerminalGeometry.size(24, 80, TerminalFontSize.LARGE));
        assertThat(terminal.specializedFor(Map.of(TerminalTypes.FONT_SIZE, "small")).size())
                .isEqualTo(TerminalGeometry.size(24, 80, TerminalFontSize.SMALL));
    }

    @Test
    void itsSizeFollowsTheGeometryOfTheNormalFont() {
        assertThat(terminal.size()).isEqualTo(TerminalGeometry.size(24, 80, TerminalFontSize.NORMAL))
                .isEqualTo(new GridSize(82, 52));
        ComponentType single = terminal.specializedFor(Map.of(TerminalTypes.ROWS, 1, TerminalTypes.COLUMNS, 1));
        assertThat(single.size()).isEqualTo(TerminalGeometry.size(1, 1, TerminalFontSize.NORMAL));
        assertThat(single.size().width()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void cellSizesAreFixedPerFontSize() {
        assertThat(TerminalGeometry.size(10, 10, TerminalFontSize.SMALL)).isEqualTo(new GridSize(10, 19));
        assertThat(TerminalGeometry.size(10, 10, TerminalFontSize.LARGE)).isEqualTo(new GridSize(15, 29));
    }

    @Test
    void itsPinsAreInputsAtTheFixedPlaces() {
        assertThat(terminal.pins()).extracting(PinSpec::name).containsExactly("D", "CLK", "WE", "CLR");
        assertThat(terminal.pins()).extracting(PinSpec::direction).containsOnly(PinDirection.INPUT);
        assertThat(terminal.pin(0).position()).isEqualTo(new GridPoint(0, 3));
        int bottom = terminal.size().height();
        assertThat(terminal.pin(1).position()).isEqualTo(new GridPoint(2, bottom));
        assertThat(terminal.pin(2).position()).isEqualTo(new GridPoint(4, bottom));
        assertThat(terminal.pin(3).position()).isEqualTo(new GridPoint(6, bottom));
    }

    @Test
    void pinPositionsTurnWithTheRotation() {
        Component turned = terminal.newComponent(new cl.almejo.vsim.core.document.ElementId("c1"),
                new GridPoint(10, 10), Rotation.R90, Map.of(), "");
        Component upright = terminal.newComponent(new cl.almejo.vsim.core.document.ElementId("c2"),
                new GridPoint(10, 10), Rotation.R0, Map.of(), "");

        for (int pinIndex = 0; pinIndex < terminal.pinCount(); pinIndex++) {
            GridPoint relative = terminal.pin(pinIndex).position();
            assertThat(terminal.pinLocation(upright, pinIndex)).isEqualTo(new GridPoint(10 + relative.x(),
                    10 + relative.y()));
            assertThat(terminal.pinLocation(turned, pinIndex))
                    .isEqualTo(new GridPoint(10, 10).plus(Rotation.R90.apply(relative)));
        }
    }

    @Test
    void rowsAndColumnsSurviveSavingAndLoading() throws CircuitFormatException {
        CircuitFormat format = new CircuitFormat(registry);
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), StandardComponentTypes.TERMINAL, new GridPoint(0, 0),
                Rotation.R0, Map.of(TerminalTypes.ROWS, 10, TerminalTypes.COLUMNS, 40), "tty"));

        CircuitDocument loaded = format.read(format.write(document));

        assertThat(loaded.components().getFirst().parameters())
                .containsEntry(TerminalTypes.ROWS, 10).containsEntry(TerminalTypes.COLUMNS, 40);
    }

    @Test
    void changingTheRowsIsOneCommandThatResizesThePartAndUndoesInOneStep() {
        CircuitDocument document = new CircuitDocument();
        Component placed = terminal.newComponent(document.newComponentId(), new GridPoint(5, 5), Rotation.R0,
                Map.of(), "");
        document.add(placed);
        Map<String, Object> changed = new HashMap<>(placed.parameters());
        changed.put(TerminalTypes.ROWS, 10);
        ChangeParametersCommand command = new ChangeParametersCommand(placed.id(), terminal, changed);

        command.apply(document);
        Component after = document.component(placed.id()).orElseThrow();
        assertThat(terminal.specializedFor(after.parameters()).size())
                .isEqualTo(TerminalGeometry.size(10, 80, TerminalFontSize.NORMAL));
        assertThat(terminal.specializedFor(after.parameters()).size().height())
                .isLessThan(terminal.size().height());

        command.revert(document);
        assertThat(document.component(placed.id()).orElseThrow()).isEqualTo(placed);
    }
}
