package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeStickyNoteCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.frames.FramesFeatures;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The sticky notes in an editor view: tool button and menu item, gestures on the pane, menu, editor, tooltip. */
class StickyNotesExtensionTest {

    private EditorFixture fixture;
    private OpenDocument openDocument;
    private EditorView view;
    private Runnable uninstall;
    private FramesFeatures frames;
    private final List<String> asked = new ArrayList<>();

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        openDocument = fixture.openDocument();
        view = fixture.view();
        // as in Application: sticky notes before frames
        uninstall = new StickyNotesExtension((parent, firstLine) -> {
            asked.add(firstLine);
            return true;
        }).install(view);
        frames = new FramesFeatures(view);
    }

    @AfterEach
    void close() {
        frames.dispose();
        uninstall.run();
        fixture.close();
    }

    private StickyNote note(StickyContent content) {
        StickyNote note = new StickyNote(openDocument.document().newStickyNoteId(), new GridPoint(10, 10), 16, 10,
                StickyColor.YELLOW, content, false);
        openDocument.execute(new AddElementsCommand(List.of(note)));
        return note;
    }

    private StickyNote current(StickyNote note) {
        return openDocument.document().stickyNote(note.id()).orElseThrow();
    }

    private List<String> toolbarTools() {
        List<String> names = new ArrayList<>();
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof JToggleButton button && button.getAction() != null) {
                names.add(button.getAction().getValue(Action.NAME).toString());
            }
        }
        return names;
    }

    private List<String> viewMenuTexts() {
        List<String> names = new ArrayList<>();
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenuItem item) {
                names.add(item.getText());
            }
        }
        return names;
    }

    @Test
    void theToolComesRightAfterMarcoInTheToolbarAndTheViewMenu() {
        assertThat(toolbarTools()).containsSubsequence("Cable", "Marco", "Nota adhesiva", "Mover vista");
        assertThat(toolbarTools().indexOf("Nota adhesiva")).isEqualTo(toolbarTools().indexOf("Marco") + 1);
        assertThat(viewMenuTexts().subList(0, 6))
                .containsExactly("Selección", "Cable", "Marco", "Nota adhesiva", "Mover vista", "Zoom");
        assertThat(view.shortcutActions()).containsKey("view.stickyNoteTool");
    }

    @Test
    void choosingTheToolPutsItOnThePaneAndUninstallTakesEverythingBack() {
        perform(view.toolButton(StickyNoteTool.class).orElseThrow().getAction());
        assertThat(view.canvas().tool()).isInstanceOf(StickyNoteTool.class);

        uninstall.run();
        uninstall = () -> {
        };

        assertThat(view.toolButton(StickyNoteTool.class)).isEmpty();
        assertThat(viewMenuTexts()).doesNotContain("Nota adhesiva");
        assertThat(view.shortcutActions()).doesNotContainKey("view.stickyNoteTool");
        SelectTool select = (SelectTool) view.canvas().defaultTool();
        assertThat(select.delegates()).noneMatch(StickyNoteGestures.class::isInstance);
    }

    @Test
    void theNoteGesturesAreAskedBeforeTheFrameOnes() {
        SelectTool select = (SelectTool) view.canvas().defaultTool();
        assertThat(select.delegates().getFirst()).isInstanceOf(StickyNoteGestures.class);
    }

    @Test
    void aNoteLyingOnAFrameTitleBarTakesThePress() {
        openDocument.execute(new CreateFrameCommand(new Frame(openDocument.document().newFrameId(),
                new GridPoint(0, 9), 40, 30, "Bloque", FrameTint.NONE, false)));
        StickyNote note = note(new StickyText("encima"));
        SelectTool select = (SelectTool) view.canvas().defaultTool();
        select.mousePressed(view.canvas(), TestToolContext.at(12, 10.5));
        select.mouseReleased(view.canvas(), TestToolContext.at(12, 10.5));
        assertThat(view.canvas().selection().ids()).containsExactly(note.id());
    }

    @Test
    void theContextMenuOfANoteHasItsItemsAndNoPlainBorrar() {
        StickyNote note = note(new StickyText("hola"));
        view.canvas().selection().select(note.id());
        List<String> items = texts(view.contextMenu(Optional.of(note), new Point2D.Double(12, 12)));
        assertThat(items).contains("Color", "Texto", "Lista", "Plegar", "Despegar…");
        assertThat(items).doesNotContain("Borrar");
    }

    @Test
    void editingAChecklistInPlaceKeepsTheTicksOfUnchangedLines() {
        StickyNote note = note(new StickyChecklist(List.of(new StickyItem("a", true), new StickyItem("b", false),
                new StickyItem("c", true))));
        view.editLabel(note);
        assertThat(view.inlineEditor().isOpen()).isTrue();
        assertThat(view.inlineEditor().area().getText()).isEqualTo("a\nb\nc");
        assertThat(view.inlineEditor().area().getLineWrap()).isTrue();

        view.inlineEditor().area().setText("a\nc\n\nd");
        view.inlineEditor().confirm();

        assertThat(current(note).content()).isEqualTo(new StickyChecklist(List.of(new StickyItem("a", true),
                new StickyItem("c", true), new StickyItem("d", false))));
        assertThat(openDocument.history().undoDescription()).contains("command.editStickyNote");
    }

    @Test
    void editingTheTextOfAFreeNoteIsOneStepAndAnUnchangedTextNone() {
        StickyNote note = note(new StickyText("antes"));
        view.editLabel(note);
        view.inlineEditor().confirm();
        assertThat(openDocument.history().undoDescription()).isNotEqualTo(Optional.of("command.editStickyNote"));

        view.editLabel(note);
        view.inlineEditor().area().setText("después\notra línea");
        view.inlineEditor().confirm();
        assertThat(current(note).content()).isEqualTo(new StickyText("después\notra línea"));
    }

    @Test
    void aNoteInALockedFrameOpensNoEditor() {
        StickyNote note = note(new StickyText("quieta"));
        openDocument.execute(new CreateFrameCommand(new Frame(openDocument.document().newFrameId(),
                new GridPoint(5, 5), 40, 30, "Bloque", FrameTint.NONE, true)));
        view.editLabel(note);
        assertThat(view.inlineEditor().isOpen()).isFalse();
    }

    @Test
    void theDeleteKeyRemovesSelectedNotesWithoutAsking() {
        StickyNote note = note(new StickyText("borrar"));
        view.canvas().selection().select(note.id());
        perform(view.actions().delete());
        assertThat(openDocument.document().stickyNotes()).isEmpty();
        assertThat(asked).isEmpty();
        openDocument.history().undo();
        assertThat(current(note)).isEqualTo(note);
    }

    @Test
    void aFoldedNoteShowsItsTextInATooltip() {
        StickyNote note = note(new StickyChecklist(List.of(new StickyItem("listo", true),
                new StickyItem("a < b", false))));
        assertThat(StickyNotesExtension.foldedNoteToolTip(openDocument.document(), new Point2D.Double(11, 11), 8))
                .isEmpty();
        openDocument.execute(new ChangeStickyNoteCommand(note, note.withFolded(true)));
        assertThat(StickyNotesExtension.foldedNoteToolTip(openDocument.document(), new Point2D.Double(11, 11), 8))
                .contains("<html>✓ listo<br>☐ a &lt; b</html>");
        assertThat(StickyNotesExtension.foldedNoteToolTip(openDocument.document(), new Point2D.Double(30, 30), 8))
                .isEmpty();
    }
}
