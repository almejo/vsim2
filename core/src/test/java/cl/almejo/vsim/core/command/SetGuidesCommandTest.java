package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Guide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SetGuidesCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);
    private final Guide top = new Guide(Guide.Orientation.HORIZONTAL, 4);
    private final Guide left = new Guide(Guide.Orientation.VERTICAL, 8);
    private final Guide bottom = new Guide(Guide.Orientation.HORIZONTAL, 30);

    @Test
    void applyAndRevertSetTheGivenListsAndKeepTheDescription() {
        SetGuidesCommand command = new SetGuidesCommand(List.of(top), List.of(left), "command.moveGuide");
        document.setGuides(List.of(top));

        command.apply(document);
        assertThat(document.guides()).containsExactly(left);
        command.revert(document);
        assertThat(document.guides()).containsExactly(top);
        assertThat(command.description()).isEqualTo("command.moveGuide");
    }

    @Test
    void eachEditIsOneUndoStepThatMarksTheDocumentModifiedButNeverBumpsTheRevision() {
        List<String> calls = new ArrayList<>();
        document.addListener((changed, change) -> calls.add("document"));
        document.addGuideListener(() -> calls.add("guides"));

        history.execute(SetGuidesCommand.adding(document, top).orElseThrow());
        history.execute(SetGuidesCommand.adding(document, left).orElseThrow());

        assertThat(document.guides()).containsExactly(top, left);
        assertThat(history.isModified()).isTrue();
        assertThat(history.undoDescription()).contains("command.addGuide");
        history.undo();
        assertThat(document.guides()).containsExactly(top);
        history.undo();
        assertThat(document.guides()).isEmpty();
        assertThat(history.isModified()).isFalse();
        history.redo();
        assertThat(document.guides()).containsExactly(top);
        assertThat(document.revision()).isZero();
        assertThat(calls).containsOnly("guides").hasSize(5);
    }

    @Test
    void addingAGuideThatIsAlreadyThereIsNoCommand() {
        document.setGuides(List.of(top));

        assertThat(SetGuidesCommand.adding(document, new Guide(Guide.Orientation.HORIZONTAL, 4))).isEmpty();
    }

    @Test
    void movingKeepsTheGuideInItsPlaceInTheList() {
        document.setGuides(List.of(top, left, bottom));
        Guide moved = new Guide(Guide.Orientation.VERTICAL, 12);

        SetGuidesCommand command = SetGuidesCommand.moving(document, left, moved).orElseThrow();
        history.execute(command);

        assertThat(document.guides()).containsExactly(top, moved, bottom);
        assertThat(command.description()).isEqualTo("command.moveGuide");
        history.undo();
        assertThat(document.guides()).containsExactly(top, left, bottom);
    }

    @Test
    void movingOntoAnotherGuideMergesThemInOneStep() {
        document.setGuides(List.of(top, left, bottom));

        history.execute(SetGuidesCommand.moving(document, bottom, top).orElseThrow());

        assertThat(document.guides()).containsExactly(top, left);
        assertThat(history.undoDescription()).contains("command.moveGuide");
        history.undo();
        assertThat(document.guides()).containsExactly(top, left, bottom);
    }

    @Test
    void movingAGuideThatIsGoneOrToWhereItIsIsNoCommand() {
        document.setGuides(List.of(top));

        assertThat(SetGuidesCommand.moving(document, left, bottom)).isEmpty();
        assertThat(SetGuidesCommand.moving(document, top, top)).isEmpty();
    }

    @Test
    void removingTakesOutOnlyThatGuide() {
        document.setGuides(List.of(top, left, bottom));

        SetGuidesCommand command = SetGuidesCommand.removing(document, left).orElseThrow();
        history.execute(command);

        assertThat(document.guides()).containsExactly(top, bottom);
        assertThat(command.description()).isEqualTo("command.removeGuide");
        assertThat(SetGuidesCommand.removing(document, left)).isEmpty();
    }

    @Test
    void clearingRemovesEveryGuideAndUndoBringsThemBackInOrder() {
        assertThat(SetGuidesCommand.clearing(document)).as("nothing to clear").isEmpty();
        document.setGuides(List.of(bottom, top, left));

        SetGuidesCommand command = SetGuidesCommand.clearing(document).orElseThrow();
        history.execute(command);

        assertThat(document.guides()).isEmpty();
        assertThat(command.description()).isEqualTo("command.clearGuides");
        history.undo();
        assertThat(document.guides()).containsExactly(bottom, top, left);
    }

    @Test
    void theHelpersReadTheListAsItIsWhenTheyAreCalled() {
        SetGuidesCommand addTop = SetGuidesCommand.adding(document, top).orElseThrow();
        document.setGuides(List.of(left));

        SetGuidesCommand addBottom = SetGuidesCommand.adding(document, bottom).orElseThrow();
        addBottom.apply(document);
        assertThat(document.guides()).containsExactly(left, bottom);
        addBottom.revert(document);
        assertThat(document.guides()).containsExactly(left);
        addTop.apply(document);
        assertThat(document.guides()).as("built from the empty list it saw").containsExactly(top);
    }
}
