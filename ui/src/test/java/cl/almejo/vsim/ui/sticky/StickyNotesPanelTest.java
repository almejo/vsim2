package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeStickyNoteCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Container;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

class StickyNotesPanelTest {

    private OpenDocument openDocument;
    private EditorView view;
    private StickyNotesPanel panel;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new StickyNotesTabExtension())), NO_WINDOW);
        panel = find(view).orElseThrow();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private static Optional<StickyNotesPanel> find(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof StickyNotesPanel found) {
                return Optional.of(found);
            }
            if (child instanceof Container nested) {
                Optional<StickyNotesPanel> found = find(nested);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private StickyNote add(int x, int y, StickyContent content, boolean folded) {
        StickyNote note = new StickyNote(openDocument.document().newStickyNoteId(), new GridPoint(x, y), 8, 6,
                StickyColor.YELLOW, content, folded);
        openDocument.execute(new AddElementsCommand(List.of(note)));
        return note;
    }

    private static List<String> texts(StickyNotesPanel target) {
        return target.rows().stream().map(StickyNoteRow::text).toList();
    }

    @Test
    void threeNotesAreThreeRowsInReadingOrderAndTheTitleCountsThem() {
        StickyChecklist list = new StickyText("a\nb\nc\nd").toChecklist().toggled(0).toggled(1);
        add(20, 10, new StickyText("lower\nsecond line"), false);
        add(30, 0, list, false);
        add(5, 0, new StickyChecklist(List.of()).edited(List.of("x")).toggled(0), true);

        assertThat(texts(panel)).containsExactly("✓ todo listo (plegada)", "c · 2/4", "lower");
        view.inspector().showTab("Notas (3)");
        assertThat(view.inspector().selectedTab()).contains("Notas (3)");
    }

    @Test
    void emptyDocumentShowsTheHintAndTheTitleSaysZero() {
        assertThat(panel.showsHint()).isTrue();
        assertThat(panel.hint().getText()).contains("No hay notas adhesivas");
        view.inspector().showTab("Notas (0)");
    }

    @Test
    void addingEditingAndRemovingUpdateRowsAndTitle() {
        StickyNote note = add(0, 0, new StickyText("hello"), false);
        assertThat(texts(panel)).containsExactly("hello");
        view.inspector().showTab("Notas (1)");

        StickyNote edited = note.withContent(new StickyText("bye"));
        openDocument.execute(new ChangeStickyNoteCommand(note, edited));
        assertThat(texts(panel)).containsExactly("bye");

        openDocument.execute(new RemoveElementsCommand(List.of(edited.id())));
        assertThat(panel.rows()).isEmpty();
        assertThat(panel.showsHint()).isTrue();
        view.inspector().showTab("Notas (0)");
    }

    @Test
    void changesFromAnotherPaneOfTheSameDocumentUpdateTheTab() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas other = view.panes().get(1);
        view.canvasPanes().activate(other);
        StickyNote note = new StickyNote(openDocument.document().newStickyNoteId(), GridPoint.ORIGIN, 8, 6,
                StickyColor.PINK, new StickyText("from the other pane"), false);
        other.openDocument().execute(new AddElementsCommand(List.of(note)));
        assertThat(texts(panel)).containsExactly("from the other pane");

        view.canvasPanes().activate(view.panes().get(0));
        openDocument.execute(new ChangeStickyNoteCommand(note, note.withContent(new StickyText("edited"))));
        assertThat(texts(panel)).containsExactly("edited");
    }

    @Test
    void choosingAFoldedNoteSelectsItTravelsAndUnfoldsItInOneUndoStep() {
        StickyNote folded = add(200, 100, new StickyText("far"), true);
        Optional<cl.almejo.vsim.core.command.Command> undoBefore = openDocument.history().undoCommand();

        panel.activate(panel.rows().getFirst());

        assertThat(openDocument.document().stickyNote(folded.id()).orElseThrow().folded()).isFalse();
        assertThat(view.canvas().selection().ids()).containsExactly(folded.id());
        assertThat(openDocument.history().undoCommand().orElseThrow().description())
                .isEqualTo("command.unfoldStickyNote");
        openDocument.history().undo();
        assertThat(openDocument.history().undoCommand()).isEqualTo(undoBefore);
        assertThat(openDocument.document().stickyNote(folded.id()).orElseThrow().folded()).isTrue();
    }

    @Test
    void choosingAnUnfoldedNoteSelectsItWithoutAHistoryEntry() {
        StickyNote note = add(3, 3, new StickyText("here"), false);
        Optional<cl.almejo.vsim.core.command.Command> undoBefore = openDocument.history().undoCommand();

        panel.activate(panel.rows().getFirst());

        assertThat(view.canvas().selection().ids()).containsExactly(note.id());
        assertThat(openDocument.history().undoCommand()).isEqualTo(undoBefore);
    }

    @Test
    void theTabShowsTheNotesOfItsOwnDocument() {
        add(0, 0, new StickyText("mine"), false);
        try (OpenDocument second = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            EditorView secondView = new EditorView(second, EditorServices.of(PainterRegistry.standard(),
                    ColorScheme.LIGHT).withExtensions(List.of(new StickyNotesTabExtension())), NO_WINDOW);
            try {
                StickyNotesPanel secondPanel = find(secondView).orElseThrow();
                assertThat(secondPanel.rows()).isEmpty();
                assertThat(secondPanel.showsHint()).isTrue();
                assertThat(texts(panel)).containsExactly("mine");
            } finally {
                secondView.dispose();
            }
        }
    }

    @Test
    void disposingTheViewStopsTheTabListening() {
        int listenersWithTab = openDocument.changeListenerCount();
        view.dispose();
        assertThat(openDocument.changeListenerCount()).isLessThan(listenersWithTab);
        add(0, 0, new StickyText("late"), false);
        assertThat(panel.rows()).isEmpty();
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT),
                NO_WINDOW);
    }
}
