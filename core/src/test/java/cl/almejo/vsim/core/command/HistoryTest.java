package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryTest {

    private static AddElementsCommand addLabel(CircuitDocument document) {
        return new AddElementsCommand(List.of(new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "x")));
    }

    @Test
    void undoAndRedo() {
        CircuitDocument document = new CircuitDocument();
        History history = new History(document);
        assertThat(history.canUndo()).isFalse();

        history.execute(addLabel(document));
        assertThat(document.labels()).hasSize(1);
        assertThat(history.undoDescription()).contains("command.add");

        history.undo();
        assertThat(document.labels()).isEmpty();
        assertThat(history.canRedo()).isTrue();

        history.redo();
        assertThat(document.labels()).hasSize(1);
    }

    @Test
    void executingClearsRedo() {
        CircuitDocument document = new CircuitDocument();
        History history = new History(document);
        history.execute(addLabel(document));
        history.undo();
        history.execute(addLabel(document));
        assertThat(history.canRedo()).isFalse();
    }

    @Test
    void modifiedTracksSavedPoint() {
        CircuitDocument document = new CircuitDocument();
        History history = new History(document);
        assertThat(history.isModified()).isFalse();
        history.execute(addLabel(document));
        assertThat(history.isModified()).isTrue();
        history.markSaved();
        assertThat(history.isModified()).isFalse();
        history.undo();
        assertThat(history.isModified()).isTrue();
        history.redo();
        assertThat(history.isModified()).isFalse();
    }

    @Test
    void limitDropsOldestAndSavedPointBecomesUnreachable() {
        CircuitDocument document = new CircuitDocument();
        History history = new History(document, 2);
        history.markSaved();
        history.execute(addLabel(document));
        history.execute(addLabel(document));
        history.execute(addLabel(document));
        history.undo();
        history.undo();
        assertThat(history.canUndo()).isFalse();
        assertThat(history.isModified()).isTrue();
    }

    @Test
    void listenersAreNotified() {
        CircuitDocument document = new CircuitDocument();
        History history = new History(document);
        AtomicInteger notifications = new AtomicInteger();
        history.addListener(changed -> notifications.incrementAndGet());
        history.execute(addLabel(document));
        history.undo();
        history.redo();
        history.markSaved();
        assertThat(notifications).hasValue(4);
    }
}
