package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.ui.i18n.Messages;

import java.math.BigInteger;
import java.util.Locale;

/** The texts of the truth-table feature (spec §5.1): refusals, the summary line, the banner, titles. */
public final class TruthTableMessages {

    private TruthTableMessages() {
    }

    /** Why there is no table. Row counts are grouped with spaces, like times: «4 096». */
    public static String refusal(Preparation.Refused refused) {
        return switch (refused.reason()) {
            case NO_INPUTS -> Messages.text("truthTable.refused.noInputs");
            case NO_OUTPUTS -> Messages.text("truthTable.refused.noOutputs");
            case SEQUENTIAL -> Messages.text("truthTable.refused.sequential");
            case TOO_MANY_INPUT_BITS -> Messages.format("truthTable.refused.tooManyInputBits",
                    grouped(BigInteger.ONE.shiftLeft(refused.inputBits())),
                    grouped(BigInteger.ONE.shiftLeft(Preparation.MAXIMUM_INPUT_BITS)),
                    String.valueOf(Preparation.MAXIMUM_INPUT_BITS));
        };
    }

    /** The question before computing more than 1 024 rows: «La tabla tendrá 2 048 filas y puede tardar. …». */
    public static String confirmation(int inputBits) {
        return Messages.format("truthTable.confirmation", grouped(BigInteger.ONE.shiftLeft(inputBits)));
    }

    /** The button that answers {@link #confirmation} with yes. */
    public static String computeOption() {
        return Messages.text("truthTable.compute");
    }

    /** «3 entradas, 2 salidas, 8 filas»: input and output columns, rows. */
    public static String summary(TruthTable table) {
        return Messages.format("truthTable.summary", table.inputs().size(), table.outputs().size(),
                table.rows().size(),
                grouped(BigInteger.valueOf(table.rows().size())));
    }

    /** The warning above a table whose circuit has feedback without flip-flops. */
    public static String feedbackBanner() {
        return Messages.text("truthTable.feedback");
    }

    /** The tooltip of a row that never settled. */
    public static String unsettledTooltip() {
        return Messages.text("truthTable.unsettled");
    }

    /** The hint under a table whose rows can be tried in the circuit with a double-click. */
    public static String tryHint() {
        return Messages.text("truthTable.tryHint");
    }

    /** «Tabla de verdad — sumador». */
    public static String title(String scopeName) {
        return Messages.format("truthTable.title", scopeName);
    }

    /** What a selection is called in the title. */
    public static String selectionName() {
        return Messages.text("truthTable.scope.selection");
    }

    /** The menu text on a single user-component instance: «Tabla de verdad de «Sumador completo»…». */
    public static String instanceMenuText(String definitionName) {
        return Messages.format("truthTable.menu.instance", definitionName);
    }

    static String grouped(BigInteger number) {
        return String.format(Locale.ROOT, "%,d", number).replace(',', ' ');
    }
}
