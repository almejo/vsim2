package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** A command that says it continues the one on top of the history joins it into a single undo step. */
class HistoryMergeTest {

    private CircuitDocument document;
    private History history;
    private ElementId labelId;

    @BeforeEach
    void create() {
        document = new CircuitDocument();
        history = new History(document);
        TextLabel label = new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "x");
        labelId = label.id();
        history.execute(new AddElementsCommand(List.of(label)));
    }

    private GridPoint position() {
        return ((TextLabel) document.element(labelId).orElseThrow()).position();
    }

    private void nudge(int deltaX, int deltaY) {
        history.execute(MoveElementsCommand.nudge(Set.of(labelId), deltaX, deltaY));
    }

    @Test
    void consecutiveNudgesOfTheSameElementsAreOneUndoStep() {
        nudge(1, 0);
        nudge(1, 0);
        nudge(0, 5);
        assertThat(position()).isEqualTo(new GridPoint(2, 5));

        history.undo();
        assertThat(position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(history.undoDescription()).contains("command.add");
        history.redo();
        assertThat(position()).isEqualTo(new GridPoint(2, 5));
        assertThat(history.undoDescription()).contains("command.move");
    }

    @Test
    void nudgesOfOtherElementsStartANewStep() {
        TextLabel other = new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "y");
        history.execute(new AddElementsCommand(List.of(other)));
        nudge(1, 0);
        history.execute(MoveElementsCommand.nudge(Set.of(other.id()), 1, 0));
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(1, 0));
        assertThat(((TextLabel) document.element(other.id()).orElseThrow()).position()).isEqualTo(GridPoint.ORIGIN);
    }

    @Test
    void anotherEditInBetweenBreaksTheRun() {
        nudge(1, 0);
        history.execute(addLabel());
        nudge(1, 0);
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(1, 0));
    }

    @Test
    void aPlainDragIsNeverMergedWithNudges() {
        history.execute(new MoveElementsCommand(Set.of(labelId), 3, 0));
        nudge(1, 0);
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(3, 0));
        nudge(1, 0);
        nudge(1, 0);
        history.execute(new MoveElementsCommand(Set.of(labelId), 3, 0));
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(5, 0));
    }

    @Test
    void undoThenNudgeStartsANewStep() {
        nudge(1, 0);
        nudge(1, 0);
        history.undo();
        history.redo();
        nudge(1, 0);
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(2, 0));
    }

    @Test
    void savingBreaksTheRunSoTheSavedStateStaysReachable() {
        nudge(1, 0);
        history.markSaved();
        nudge(1, 0);
        assertThat(history.isModified()).isTrue();
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(1, 0));
        assertThat(history.isModified()).isFalse();
    }

    @Test
    void modifiedFollowsAMergedStep() {
        history.markSaved();
        nudge(1, 0);
        nudge(1, 0);
        assertThat(history.isModified()).isTrue();
        history.undo();
        assertThat(history.isModified()).isFalse();
    }

    @Test
    void executingClearsRedoAfterAMerge() {
        nudge(1, 0);
        history.undo();
        nudge(1, 0);
        assertThat(history.canRedo()).isFalse();
    }

    @Test
    void listenersHearEveryMergedExecute() {
        AtomicInteger notifications = new AtomicInteger();
        history.addListener(changed -> notifications.incrementAndGet());
        nudge(1, 0);
        nudge(1, 0);
        assertThat(notifications).hasValue(2);
        assertThat(position()).isEqualTo(new GridPoint(2, 0));
    }

    @Test
    void aMergedRunTakesOneSlotOfTheHistoryLimit() {
        History small = new History(document, 2);
        small.execute(MoveElementsCommand.nudge(Set.of(labelId), 1, 0));
        small.execute(MoveElementsCommand.nudge(Set.of(labelId), 1, 0));
        small.execute(MoveElementsCommand.nudge(Set.of(labelId), 1, 0));
        small.undo();
        assertThat(small.canUndo()).isFalse();
        assertThat(position()).isEqualTo(GridPoint.ORIGIN);
    }

    private AddElementsCommand addLabel() {
        return new AddElementsCommand(List.of(new TextLabel(document.newLabelId(), new GridPoint(9, 9), "z")));
    }
}
