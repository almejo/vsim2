package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
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

    /** A nudge whose merge with the next one moves the label far away and then fails half way. */
    private final class BrokenMergeNudge implements Command {
        private final MoveElementsCommand move = new MoveElementsCommand(Set.of(labelId), 1, 0);

        @Override
        public void apply(CircuitDocument target) {
            move.apply(target);
        }

        @Override
        public void revert(CircuitDocument target) {
            move.revert(target);
        }

        @Override
        public String description() {
            return "command.move";
        }

        @Override
        public Optional<Command> mergedWith(Command next) {
            return Optional.of(new Command() {
                @Override
                public void apply(CircuitDocument target) {
                    new MoveElementsCommand(Set.of(labelId), 50, 50).apply(target);
                    target.add(new TextLabel(target.newLabelId(), new GridPoint(7, 7), "half"));
                    throw new IllegalStateException("broken merge");
                }

                @Override
                public void revert(CircuitDocument target) {
                    throw new AssertionError("never applied");
                }

                @Override
                public String description() {
                    return "command.move";
                }
            });
        }
    }

    @Test
    void aMergeThatFailsPutsTheRunBackAndExecutesTheCommandAsItsOwnStep() {
        history.execute(new BrokenMergeNudge());
        int elementsBefore = document.elements().size();
        AtomicInteger notifications = new AtomicInteger();
        history.addListener(changed -> notifications.incrementAndGet());

        history.execute(new BrokenMergeNudge());

        assertThat(position()).isEqualTo(new GridPoint(2, 0));
        assertThat(document.elements()).hasSize(elementsBefore);
        assertThat(notifications).hasValue(1);
        history.undo();
        assertThat(position()).isEqualTo(new GridPoint(1, 0));
        history.undo();
        assertThat(position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(history.undoDescription()).contains("command.add");
    }

    @Test
    void aRunThatComesBackToWhereItStartedLeavesNoUndoStep() {
        history.markSaved();
        nudge(-1, 0);
        nudge(1, 0);

        assertThat(position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(history.undoDescription()).contains("command.add");
        assertThat(history.isModified()).isFalse();
        assertThat(history.lastExecuted()).isEmpty();
        history.undo();
        assertThat(document.elements()).isEmpty();
    }

    @Test
    void aRunThatComesBackStaysModifiedWhenTheStateBeforeItWasNotSaved() {
        nudge(-1, 0);
        nudge(1, 0);
        assertThat(history.isModified()).isTrue();
        assertThat(history.undoDescription()).contains("command.add");
    }

    @Test
    void aRunThatPassesThroughWhereItStartedIsOneStepOfTheRest() {
        nudge(-1, 0);
        nudge(1, 0);
        nudge(-1, 0);

        assertThat(position()).isEqualTo(new GridPoint(-1, 0));
        assertThat(history.undoDescription()).contains("command.move");
        history.undo();
        assertThat(position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(history.undoDescription()).contains("command.add");
    }

    @Test
    void aDroppedRunNeverAppliesItsZeroMove() {
        nudge(-1, 0);
        AtomicInteger notifications = new AtomicInteger();
        history.addListener(changed -> notifications.incrementAndGet());
        Set<DocumentElement> beforeRun = Set.of(document.element(labelId).orElseThrow().translate(1, 0));

        nudge(1, 0);

        assertThat(Set.copyOf(document.elements())).isEqualTo(beforeRun);
        assertThat(notifications).hasValue(1);
    }

    @Test
    void zeroMovesSayTheyChangeNothing() {
        assertThat(MoveElementsCommand.nudge(Set.of(labelId), 0, 0).changesNothing()).isTrue();
        assertThat(new MoveElementsCommand(Set.of(labelId), 0, 0).changesNothing()).isTrue();
        assertThat(MoveElementsCommand.nudge(Set.of(labelId), 1, 0).changesNothing()).isFalse();
        assertThat(addLabel().changesNothing()).isFalse();
    }

    private AddElementsCommand addLabel() {
        return new AddElementsCommand(List.of(new TextLabel(document.newLabelId(), new GridPoint(9, 9), "z")));
    }
}
