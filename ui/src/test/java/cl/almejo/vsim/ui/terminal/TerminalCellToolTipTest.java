package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.CellPosition;
import cl.almejo.vsim.core.terminal.CellStyle;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Where the cells of a Terminal are on the canvas, and the tooltip of a written cell (spec §8.4). */
class TerminalCellToolTipTest {

    private OpenDocument document;
    private Component terminal;
    private ComponentType type;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        type = document.registry().require(StandardComponentTypes.TERMINAL);
        terminal = place(Rotation.R0);
    }

    @AfterEach
    void close() {
        document.close();
    }

    private Component place(Rotation rotation) {
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(10, 20),
                rotation, Map.of(TerminalTypes.ROWS, 24, TerminalTypes.COLUMNS, 80), "consola");
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    /** The middle of a cell of the Normal font (8 × 16 px) at the screen's top-left (1 unit right, 3 down). */
    private static Point2D middleOf(int row, int column) {
        double localX = 8 + column * 8 + 4;
        double localY = 24 + row * 16 + 8;
        return new Point2D.Double(10 + localX / 8, 20 + localY / 8);
    }

    @Test
    void aPointOnTheScreenIsInItsCell() {
        assertThat(TerminalScreenArea.cellAt(type, terminal, middleOf(2, 6))).contains(new CellPosition(2, 6));
        assertThat(TerminalScreenArea.cellAt(type, terminal, middleOf(23, 79))).contains(new CellPosition(23, 79));
        assertThat(TerminalScreenArea.isOnScreen(type, terminal, middleOf(0, 0))).isTrue();
    }

    @Test
    void theTitleBarAndTheMarginAreNotOnTheScreen() {
        assertThat(TerminalScreenArea.cellAt(type, terminal, new Point2D.Double(12.5, 21))).isEmpty();
        assertThat(TerminalScreenArea.cellAt(type, terminal, new Point2D.Double(10.5, 25))).isEmpty();
        assertThat(TerminalScreenArea.isOnScreen(type, terminal, new Point2D.Double(12.5, 21))).isFalse();
    }

    @Test
    void aTurnedTerminalFindsTheSameCellWhereItIsDrawn() {
        Component turned = place(Rotation.R90);
        // a quarter turn clockwise about the origin: local (x, y) is drawn at (-y, x)
        Point2D local = new Point2D.Double(8 + 6 * 8 + 4, 24 + 2 * 16 + 8);
        Point2D grid = new Point2D.Double(10 - local.getY() / 8, 20 + local.getX() / 8);

        assertThat(TerminalScreenArea.cellAt(type, turned, grid)).contains(new CellPosition(2, 6));
    }

    @Test
    void aWrittenCellSaysItsCharacterBytesTimeAndPlace() {
        TerminalCell cell = new TerminalCell('ñ', CellStyle.PLAIN, "ñ".getBytes(StandardCharsets.UTF_8), 12_340);

        assertThat(TerminalCellToolTip.text(cell, new CellPosition(2, 6)))
                .isEqualTo("'ñ'  U+00F1  ·  bytes C3 B1  ·  llegó en t = 12.340  ·  fila 3, col 7");
    }

    @Test
    void anUndefinedByteHasNoBytesToShow() {
        TerminalCell cell = new TerminalCell('?', CellStyle.PLAIN, new byte[0], 5);

        assertThat(TerminalCellToolTip.text(cell, new CellPosition(0, 0)))
                .isEqualTo("'?'  U+003F  ·  bytes indefinidos  ·  llegó en t = 5  ·  fila 1, col 1");
    }

    @Test
    void theCanvasTooltipComesFromTheSnapshotOnlyForWrittenCells() {
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(24, 80));
        screen.write('h', 40);
        screen.write('i', 50);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, 1, 50, false, Map.of(),
                Map.of(terminal.id().value(), new VisibleState.TerminalState(1, screen.version(), screen.frame(),
                        false)), List.of(), Optional.empty());

        assertThat(TerminalCellToolTip.at(document.document(), document.registry(), snapshot, middleOf(0, 1)))
                .contains("'i'  U+0069  ·  bytes 69  ·  llegó en t = 50  ·  fila 1, col 2");
        assertThat(TerminalCellToolTip.at(document.document(), document.registry(), snapshot, middleOf(0, 2)))
                .as("a blank cell").isEmpty();
        assertThat(TerminalCellToolTip.at(document.document(), document.registry(), SimulationSnapshot.EMPTY,
                middleOf(0, 1))).as("no simulation yet").isEmpty();
    }
}
