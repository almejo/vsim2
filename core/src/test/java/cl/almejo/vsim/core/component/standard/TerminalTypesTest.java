package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
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

/** The first Terminal: its place in the palette, its size, its pins, and that it is inert but wirable. */
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
                .containsEntry(TerminalTypes.ROWS, 24).containsEntry(TerminalTypes.COLUMNS, 80).hasSize(2);
    }

    @Test
    void itsSizeFollowsTheGeometryOfTheNormalFont() {
        assertThat(terminal.size()).isEqualTo(TerminalGeometry.size(24, 80, TerminalFontSize.NORMAL))
                .isEqualTo(new GridSize(82, 52));
        ComponentType single = terminal.specializedFor(Map.of(TerminalTypes.ROWS, 1, TerminalTypes.COLUMNS, 1));
        assertThat(single.size()).isEqualTo(TerminalGeometry.size(1, 1, TerminalFontSize.NORMAL));
        assertThat(single.size().width()).isGreaterThanOrEqualTo(4);
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
        assertThat(terminal.pin(0).width()).isEqualTo(7);
        assertThat(terminal.pin(0).position()).isEqualTo(new GridPoint(0, 3));
        int bottom = terminal.size().height();
        assertThat(terminal.pin(1).position()).isEqualTo(new GridPoint(1, bottom));
        assertThat(terminal.pin(2).position()).isEqualTo(new GridPoint(2, bottom));
        assertThat(terminal.pin(3).position()).isEqualTo(new GridPoint(3, bottom));
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
    void wiredToDriversItSimulatesWithoutErrorAndDrivesNothing() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.TERMINAL, Map.of());

        harness.set(0, 0x41).set(2, 1).set(3, 0).set(1, 0).settle().set(1, 1).settle().set(1, 0).settle();

        assertThat(harness.state()).isEqualTo(VisibleState.NONE);
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
