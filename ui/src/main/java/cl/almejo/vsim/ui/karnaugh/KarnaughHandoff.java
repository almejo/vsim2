package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.TableCircuits;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.List;
import java.util.Optional;

/**
 * «Abrir en el asistente de Karnaugh» in the truth-table window (spec §2): which computed tables the wizard takes
 * (2..4 one-bit inputs, 1..4 one-bit outputs) and the wizard's table made of one (unsettled or undefined outputs
 * become X).
 */
public final class KarnaughHandoff {

    private KarnaughHandoff() {
    }

    /** Why the wizard cannot take {@code table}, ready to show; empty when it can. */
    public static Optional<String> refusal(TruthTable table) {
        if (!oneBitWide(table.inputs()) || table.inputs().size() > KarnaughModel.MAXIMUM_INPUTS) {
            return Optional.of(Messages.text("karnaugh.handoff.tooManyInputs"));
        }
        if (table.inputs().size() < KarnaughModel.MINIMUM_INPUTS) {
            return Optional.of(Messages.text("karnaugh.handoff.tooFewInputs"));
        }
        if (!oneBitWide(table.outputs()) || table.outputs().size() > KarnaughModel.MAXIMUM_OUTPUTS) {
            return Optional.of(Messages.text("karnaugh.handoff.outputs"));
        }
        return Optional.empty();
    }

    /**
     * The wizard's table of {@code table}, titled {@code title}.
     *
     * @throws IllegalArgumentException when {@link #refusal} is not empty
     */
    public static TruthTableSpec spec(TruthTable table, String title) {
        refusal(table).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
        return TableCircuits.specOf(table, title);
    }

    private static boolean oneBitWide(List<Column> columns) {
        return columns.stream().allMatch(column -> column.width() == 1);
    }
}
