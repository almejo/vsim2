package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.tabs.TabMark;

import java.util.Optional;

/**
 * Which places of the open circuits carry the AI's mark now (the robot on their tabs, the Ventana list and «▾»): the
 * windows read it, and tell it when the user looks at a place. One for the whole application. EDT only.
 */
public interface PlaceMarks {

    /**
     * The mark of the place a tab shows: the circuit {@code document} itself when {@code definitionId} is empty, else
     * the body of that definition with the values of the piece at {@code contextPath} (empty: «Sin valores»).
     */
    Optional<TabMark> markOf(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath);

    /** The user brought that place's tab to the front: a finished mark there goes at once. */
    void visited(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath);

    /** {@code listener} runs whenever a mark comes, changes or goes; the returned runnable takes it back. */
    Runnable addListener(Runnable listener);

    /** No marks ever (tests, and windows of an application without the AI). */
    PlaceMarks NONE = new PlaceMarks() {
        @Override
        public Optional<TabMark> markOf(OpenDocument document, Optional<String> definitionId,
                                        Optional<String> contextPath) {
            return Optional.empty();
        }

        @Override
        public void visited(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath) {
        }

        @Override
        public Runnable addListener(Runnable listener) {
            return () -> {
            };
        }
    };
}
