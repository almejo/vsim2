package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeStickyNoteCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final StickyNote text = new StickyNote(new ElementId("s1"), new GridPoint(2, 3), 8, 6,
            StickyColor.YELLOW, new StickyText("hola"), false);
    private final StickyNote list = text.withContent(new StickyChecklist(
            List.of(new StickyItem("a", false), new StickyItem("b", true))));

    private static String describe(StickyNote before, StickyNote after) {
        return new ChangeStickyNoteCommand(before, after).description();
    }

    @Test
    void executeAndRevertRestoreExactly() {
        document.add(text);
        StickyNote changed = text.withContent(new StickyText("chau")).withColor(StickyColor.BLUE).withSize(10, 7)
                .withFolded(true);
        ChangeStickyNoteCommand command = new ChangeStickyNoteCommand(text, changed);

        command.apply(document);
        assertThat(document.stickyNote(text.id())).contains(changed);
        command.revert(document);

        assertThat(document.stickyNote(text.id())).contains(text);
    }

    @Test
    void aContentEditIsDescribedAsAnEdit() {
        StickyChecklist checklist = (StickyChecklist) list.content();

        assertThat(describe(text, text.withContent(new StickyText("otro")))).isEqualTo("command.editStickyNote");
        assertThat(describe(list, list.withContent(checklist.edited(List.of("a", "x")))))
                .isEqualTo("command.editStickyNote");
    }

    @Test
    void exactlyOneFlippedItemIsATickOrAnUntick() {
        StickyChecklist checklist = (StickyChecklist) list.content();

        assertThat(describe(list, list.withContent(checklist.toggled(0)))).isEqualTo("command.tickStickyItem");
        assertThat(describe(list, list.withContent(checklist.toggled(1)))).isEqualTo("command.untickStickyItem");
        assertThat(describe(list, list.withContent(checklist.toggled(0).toggled(1))))
                .isEqualTo("command.editStickyNote");
    }

    @Test
    void sizeColourKindAndFoldingHaveTheirOwnDescriptions() {
        assertThat(describe(text, text.withSize(9, 9))).isEqualTo("command.resizeStickyNote");
        assertThat(describe(text, text.withColor(StickyColor.GREEN))).isEqualTo("command.colorStickyNote");
        assertThat(describe(text, text.withContent(text.content().toChecklist()))).isEqualTo("command.stickyNoteKind");
        assertThat(describe(list, list.withContent(list.content().toText()))).isEqualTo("command.stickyNoteKind");
        assertThat(describe(text, text.withFolded(true))).isEqualTo("command.foldStickyNote");
        assertThat(describe(text.withFolded(true), text)).isEqualTo("command.unfoldStickyNote");
    }

    @Test
    void theNotesMustShareTheirId() {
        StickyNote other = text.placed(new ElementId("s2"), text.position());

        assertThatThrownBy(() -> new ChangeStickyNoteCommand(text, other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creatingAndDeletingAreOneUndoStepEach() {
        History history = new History(document);
        history.execute(new AddElementsCommand(List.of(text)));
        assertThat(document.stickyNotes()).containsExactly(text);
        history.execute(new RemoveElementsCommand(List.of(text.id())));
        assertThat(document.stickyNotes()).isEmpty();

        history.undo();
        assertThat(document.stickyNotes()).containsExactly(text);
        history.undo();
        assertThat(document.stickyNotes()).isEmpty();
    }
}
