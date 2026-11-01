package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.CellPosition;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * The tooltip of a written cell of a Terminal (spec §8.4), on the canvas and in the window:
 * {@code 'ñ'  U+00F1  ·  bytes C3 B1  ·  llegó en t = 12.340  ·  fila 3, col 7}. Rows and columns count from 1, the
 * time is the simulation time as the status bar shows it. Blank cells have none.
 */
public final class TerminalCellToolTip {

    private TerminalCellToolTip() {
    }

    /** The tooltip of {@code cell} at {@code position} (from 0). */
    public static String text(TerminalCell cell, CellPosition position) {
        return Messages.format("terminal.cell.tooltip", Character.toString(cell.codePoint()),
                String.format("%04X", cell.codePoint()), bytes(cell.bytes()), cell.time(), position.row() + 1,
                position.column() + 1);
    }

    /** The tooltip of {@code frame}'s cell at {@code position}, empty for a blank cell or one outside the frame. */
    public static Optional<String> forCell(TerminalFrame frame, CellPosition position) {
        if (position.row() >= frame.rows() || position.column() >= frame.columns()) {
            return Optional.empty();
        }
        TerminalCell cell = frame.cell(position.row(), position.column());
        return cell.written() ? Optional.of(text(cell, position)) : Optional.empty();
    }

    /**
     * The tooltip of the written cell under {@code gridPosition} on a Terminal (or a UART 16550's integrated screen)
     * placed on the circuit itself, from the screen {@code snapshot} shows; empty elsewhere.
     */
    public static Optional<String> at(CircuitDocument document, ComponentTypeRegistry registry,
                                      SimulationSnapshot snapshot, Point2D gridPosition) {
        for (Component component : List.copyOf(document.components())) {
            if (!component.typeId().equals(StandardComponentTypes.TERMINAL)
                    && !component.typeId().equals(StandardComponentTypes.UART_16550)) {
                continue;
            }
            Optional<CellPosition> cell = TerminalScreenArea.cellAt(registry.typeOf(component), component,
                    gridPosition);
            if (cell.isPresent()) {
                return VisibleState.terminalScreen(snapshot.componentState(component.id().value()))
                        .flatMap(state -> forCell(state.frame(), cell.get()));
            }
        }
        return Optional.empty();
    }

    private static String bytes(byte[] bytes) {
        if (bytes.length == 0) {
            return Messages.text("terminal.cell.undefinedBytes");
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (byte value : bytes) {
            joiner.add(String.format("%02X", value & 0xFF));
        }
        return Messages.format("terminal.cell.bytes", joiner.toString());
    }
}
