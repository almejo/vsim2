package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The «Nota adhesiva» tool (spec §3 «Create»). */
class StickyNoteToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final StickyNoteTool tool = new StickyNoteTool();

    @BeforeEach
    void useTheTool() {
        context.setTool(tool);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private List<StickyNote> notes() {
        return context.openDocument().document().stickyNotes();
    }

    @Test
    void aClickMakesADefaultYellowTextNoteAtTheSnappedPointAndOpensItsEditor() {
        context.click(tool, 10.2, 9.8);

        assertThat(notes()).hasSize(1);
        StickyNote note = notes().getFirst();
        assertThat(note.position()).isEqualTo(new GridPoint(10, 10));
        assertThat(note.width()).isEqualTo(StickyNote.DEFAULT_WIDTH);
        assertThat(note.height()).isEqualTo(StickyNote.DEFAULT_HEIGHT);
        assertThat(note.width()).isEqualTo(8);
        assertThat(note.height()).isEqualTo(6);
        assertThat(note.color()).isEqualTo(StickyColor.YELLOW);
        assertThat(note.content()).isEqualTo(new StickyText(""));
        assertThat(note.folded()).isFalse();
        assertThat(context.editedLabel).isEqualTo(note);
        assertThat(context.selection.ids()).containsExactly(note.id());
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void aDragOfTwoByOneCellsMakesTheMinimumNote() {
        context.drag(tool, 10, 10, 12, 11);

        StickyNote note = notes().getFirst();
        assertThat(note.position()).isEqualTo(new GridPoint(10, 10));
        assertThat(note.width()).isEqualTo(4);
        assertThat(note.height()).isEqualTo(3);
    }

    @Test
    void aDragGivesTheNoteThatRectangleWhicheverWayItGoes() {
        context.drag(tool, 20, 20, 8, 12);

        StickyNote note = notes().getFirst();
        assertThat(note.position()).isEqualTo(new GridPoint(8, 12));
        assertThat(note.width()).isEqualTo(12);
        assertThat(note.height()).isEqualTo(8);
    }

    @Test
    void oneUndoStepRemovesTheNewNote() {
        context.click(tool, 10, 10);

        context.openDocument().history().undo();

        assertThat(notes()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void escapeWithoutADragGoesBackToSelection() {
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();

        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(notes()).isEmpty();
    }

    @Test
    void escapeDuringADragCreatesNothing() {
        tool.mousePressed(context, TestToolContext.at(10, 10));
        tool.mouseDragged(context, TestToolContext.at(20, 20));
        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        tool.mouseReleased(context, TestToolContext.at(20, 20));

        assertThat(notes()).isEmpty();
    }
}
