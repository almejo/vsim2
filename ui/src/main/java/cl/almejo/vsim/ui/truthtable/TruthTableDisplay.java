package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.ui.window.EditorView;
import java.util.OptionalInt;

/** One truth-table window as the feature sees it ({@link TruthTableWindow} in the application, a fake in tests). */
public interface TruthTableDisplay {

    /** What the window's buttons ask of the feature. */
    interface Controls {
        /** «Recalcular»: compute the same scope again on the current circuit. */
        void recalculate();

        /** «Cancelar» on the progress line. */
        void cancel();

        /** The window was closed by the user. */
        void closed();

        /** Row {@code row} was double-clicked: try it in the live circuit when its inputs are switches. */
        void tryRow(int row);
    }

    /** Opens a window for {@code view} titled {@code title}, empty until the first table or message. */
    @FunctionalInterface
    interface Factory {
        TruthTableDisplay open(EditorView view, String title, Controls controls);
    }

    /** «Calculando… 37 %» with «Cancelar». */
    void showProgress(int percent);

    void showTable(TruthTable table);

    /** A line instead of the progress (cancelled, failed). */
    void showMessage(String message);

    /** Highlights {@code row} of the table shown (the live input values) and scrolls to it; empty: no highlight. */
    void showCurrentRow(OptionalInt row);

    /** Whether a double-click on a row tries it in the circuit; the window's hint line shows only then. */
    void allowTryingRows(boolean allowed);

    /** Closes the window without calling {@link Controls#closed()}. */
    void close();
}
