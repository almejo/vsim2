package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.util.Arrays;
import java.util.List;

/**
 * What a Matriz LED shows. «Por filas»: row {@code r} is whatever its input {@code F<r>} carries now. «Barrido»: a row
 * is selected while «Hab.» is 1 and «Fila» names it, and shows «Datos» as it comes; when the scan moves on, the row
 * keeps the data it held <em>longest</em> while it was selected, until the scan passes it again (no times in
 * milliseconds, so the picture never flickers nor depends on the simulation speed). That is what the eye sees on a real
 * scanned panel: when the data path is faster than the row path, the next row's data reaches «Datos» a moment before
 * «Fila» moves on, and the row is lit with it only for that moment (a faint ghost), so it keeps its own (review M9).
 * With «Hab.» 0, Z or unwired, or a «Fila» that is not fully known or beyond the last row, no row is selected. A row
 * nobody has written yet is off.
 *
 * <p>Unknown bits (Z, or the whole input in conflict) are kept as they come, so the painter can show them as unknown.
 */
final class LedMatrixBehavior implements Behavior {

    /** No row selected: «Hab.» is not 1 or «Fila» names no row. */
    private static final int NONE = -1;

    private final int rows;
    private final int columns;
    private final boolean scan;
    private final Value[] shown;
    /** «Barrido»: the row selected now, the data on it since when, and the data it held longest so far. */
    private int selectedRow = NONE;
    private Value heldData;
    private long heldSince;
    private Value longestData;
    private long longestHeld;

    LedMatrixBehavior(int rows, int columns, boolean scan) {
        this.rows = rows;
        this.columns = columns;
        this.scan = scan;
        this.shown = new Value[rows];
        Arrays.fill(shown, scan ? Value.of(columns, 0) : Value.highImpedance(columns));
    }

    @Override
    public void initialize(BehaviorContext context) {
        refresh(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        refresh(context);
    }

    private void refresh(BehaviorContext context) {
        if (!scan) {
            for (int row = 0; row < rows; row++) {
                shown[row] = context.input(row);
            }
            return;
        }
        latchScannedRow(context);
    }

    private void latchScannedRow(BehaviorContext context) {
        int row = selectedRow(context);
        Value data = context.input(1);
        long now = context.now();
        if (row != selectedRow) {
            endSelection(now);
            selectedRow = row;
            if (row != NONE) {
                heldData = data;
                heldSince = now;
                longestData = data;
                longestHeld = -1;
                shown[row] = data;
            }
            return;
        }
        if (row == NONE) {
            return;
        }
        if (!data.equals(heldData)) {
            rememberHeld(now);
            heldData = data;
            heldSince = now;
        }
        shown[row] = data;
    }

    private int selectedRow(BehaviorContext context) {
        Value enable = context.input(2);
        if (enable.conflict() || enable.bit(0) != BitState.ONE) {
            return NONE;
        }
        Value rowNumber = context.input(0);
        if (!rowNumber.isFullyDefined() || rowNumber.bits() >= rows) {
            return NONE;
        }
        return (int) rowNumber.bits();
    }

    /** The data on the selected row since {@link #heldSince} becomes the longest held when it is (the latest on a tie). */
    private void rememberHeld(long now) {
        long held = now - heldSince;
        if (held >= longestHeld) {
            longestHeld = held;
            longestData = heldData;
        }
    }

    /** The scan leaves the selected row: it keeps the data it held longest. */
    private void endSelection(long now) {
        if (selectedRow == NONE) {
            return;
        }
        rememberHeld(now);
        shown[selectedRow] = longestData;
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.LedMatrixState(List.of(shown));
    }
}
