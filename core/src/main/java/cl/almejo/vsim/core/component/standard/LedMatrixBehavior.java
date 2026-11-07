package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.util.Arrays;
import java.util.List;

/**
 * What a Matriz LED shows. «Por filas»: row {@code r} is whatever its input {@code F<r>} carries now. «Barrido»: each
 * row keeps the last data it was given, and a row is given data whenever «Hab.» is 1 and «Fila» names it; when the
 * scan has moved on the row keeps its data until the scan passes it again (no times in milliseconds, so the picture
 * never flickers nor depends on the simulation speed). With «Hab.» 0, Z or unwired, or a «Fila» that is not fully known
 * or beyond the last row, nothing is latched. A row nobody has written yet is off.
 *
 * <p>Unknown bits (Z, or the whole input in conflict) are kept as they come, so the painter can show them as unknown.
 */
final class LedMatrixBehavior implements Behavior {

    private final int rows;
    private final int columns;
    private final boolean scan;
    private final Value[] shown;

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
        Value enable = context.input(2);
        if (enable.conflict() || enable.bit(0) != BitState.ONE) {
            return;
        }
        Value rowNumber = context.input(0);
        if (!rowNumber.isFullyDefined() || rowNumber.bits() >= rows) {
            return;
        }
        shown[(int) rowNumber.bits()] = context.input(1);
    }

    @Override
    public VisibleState visibleState() {
        return new VisibleState.LedMatrixState(List.of(shown));
    }
}
