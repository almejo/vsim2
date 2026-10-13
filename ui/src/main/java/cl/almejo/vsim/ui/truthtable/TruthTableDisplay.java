package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.ui.window.EditorView;

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

    /** Closes the window without calling {@link Controls#closed()}. */
    void close();
}
