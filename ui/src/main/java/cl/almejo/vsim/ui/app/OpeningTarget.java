package cl.almejo.vsim.ui.app;

import java.util.Objects;

/**
 * Where a circuit file being opened goes (spec §10.2): the window that already shows it (its main tab is focused), a
 * new group in the window that asked (Abrir, Recientes, Ejemplos, a drop), or a new window when nothing asked
 * (start-up, the command line, the system's open-file events).
 *
 * @param <W> the window type ({@code CircuitWindow} in the application, plain values in tests)
 */
public sealed interface OpeningTarget<W> {

    /** The file is open already: focus {@code document}'s main tab in {@code window}. */
    record FocusTab<W>(W window, OpenDocument document) implements OpeningTarget<W> {

        public FocusTab {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(document, "document");
        }
    }

    /** Open it as a new group of tabs in {@code window}. */
    record NewGroup<W>(W window) implements OpeningTarget<W> {

        public NewGroup {
            Objects.requireNonNull(window, "window");
        }
    }

    /** Open it in a window of its own. */
    record NewWindow<W>() implements OpeningTarget<W> {
    }
}
