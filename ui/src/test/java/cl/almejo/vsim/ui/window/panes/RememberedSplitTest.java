package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Dividir la vista, Task 4: a file's window comes back split as it was; the circuit file never knows. */
class RememberedSplitTest {

    /** A delay that runs only when the test says so. */
    private static final class FakeDelay implements RememberedSplit.Delay {
        final List<Runnable> pending = new ArrayList<>();
        int scheduled;

        @Override
        public Runnable after(int milliseconds, Runnable task) {
            assertThat(milliseconds).isEqualTo(RememberedSplit.SAVE_DELAY_MILLISECONDS);
            scheduled++;
            pending.add(task);
            return () -> pending.remove(task);
        }

        void elapse() {
            List.copyOf(pending).forEach(Runnable::run);
            pending.clear();
        }
    }

    @TempDir
    Path folder;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SplitLayoutStore store = new SplitLayoutStore(preferences);
    private final FakeDelay delay = new FakeDelay();
    private final List<AutoCloseable> toClose = new ArrayList<>();

    @BeforeEach
    void language() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() throws Exception {
        for (AutoCloseable closeable : toClose.reversed()) {
            closeable.close();
        }
    }

    /** One editor window on {@code document}, with its canvas area sized {@code width} × {@code height}. */
    private Editor open(OpenDocument document, int width, int height) {
        EditorView view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        view.canvasPanes().setSize(width, height);
        view.canvasPanes().doLayout();
        RememberedSplit remembered = new RememberedSplit(store, view.canvasPanes(), document::path, delay);
        Editor editor = new Editor(view, remembered);
        toClose.add(editor);
        return editor;
    }

    private record Editor(EditorView view, RememberedSplit remembered) implements AutoCloseable {
        CanvasPanes panes() {
            return view.canvasPanes();
        }

        /** What CircuitWindow does when the window closes. */
        @Override
        public void close() {
            remembered.close();
            view.dispose();
        }

        /** What Application and CircuitWindow do when a file's window opens. */
        void opened() {
            remembered.restoreWhenShown();
            remembered.showRemembered();
        }
    }

    private OpenDocument savedDocument(String name) throws Exception {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        place(document, StandardComponentTypes.NOT, 0, 0, Map.of());
        document.save(folder.resolve(name));
        toClose.add(document);
        return document;
    }

    private OpenDocument reopen(Path file) throws Exception {
        OpenDocument document = OpenDocument.open(file, StandardComponentTypes.registry());
        toClose.add(document);
        return document;
    }

    private static void splitInFour(Editor editor) {
        editor.panes().split(Orientation.HORIZONTAL, 0.3);
        editor.panes().split(Orientation.VERTICAL, 0.6);
        List<ViewState> views = List.of(new ViewState(2, 10, 20), new ViewState(0.5, -40, 30),
                new ViewState(1, 0, 0), new ViewState(3, 50, -50));
        for (int index = 0; index < views.size(); index++) {
            editor.panes().panes().get(index).applyViewState(views.get(index));
        }
    }

    @Test
    void reopeningTheFileRestoresTheDividersAndEachPanesView() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Editor first = open(document, 800, 600);
        first.opened();
        splitInFour(first);
        PaneLayout split = first.panes().paneLayout();
        first.close();

        Editor again = open(reopen(document.path().orElseThrow()), 800, 600);
        again.opened();

        PaneLayout restored = again.panes().paneLayout();
        assertThat(restored.horizontalDivider()).isEqualTo(OptionalDouble.of(0.3));
        assertThat(restored.verticalDivider()).isEqualTo(OptionalDouble.of(0.6));
        assertThat(restored.panes()).hasSize(4);
        for (int index = 0; index < 4; index++) {
            ViewState expected = split.panes().get(index);
            ViewState actual = restored.panes().get(index);
            assertThat(actual.zoom()).as("zoom of pane " + index).isEqualTo(expected.zoom());
            assertThat(actual.centerX()).as("centre x of pane " + index).isCloseTo(expected.centerX(),
                    within(1e-6));
            assertThat(actual.centerY()).as("centre y of pane " + index).isCloseTo(expected.centerY(),
                    within(1e-6));
        }
    }

    @Test
    void aWindowThatIsNotAFileBeingOpenedKeepsOnePane() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        store.remember(document.path().orElseThrow(), new PaneLayout(OptionalDouble.of(0.5), OptionalDouble.empty(),
                List.of(new ViewState(1, 0, 0), new ViewState(1, 0, 0))));

        Editor newView = open(document, 800, 600); // Ventana ▸ Nueva vista: never asked to restore
        newView.remembered().showRemembered();

        assertThat(newView.panes().panes()).hasSize(1);
    }

    @Test
    void anUnsavedCircuitOpensWithOnePaneAndIsNotStored() throws Exception {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        toClose.add(document);
        Editor editor = open(document, 800, 600);
        editor.opened();
        assertThat(editor.panes().panes()).hasSize(1);

        editor.panes().split(Orientation.VERTICAL, 0.5);
        delay.elapse();
        editor.close();

        assertThat(preferences.splitLayoutsNode().childrenNames()).isEmpty();
    }

    @Test
    void changesAreSavedAfterAPauseOncePerBurst() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path file = document.path().orElseThrow();
        Editor editor = open(document, 800, 600);
        editor.opened();

        editor.panes().split(Orientation.HORIZONTAL, 0.5);
        editor.panes().moveDivider(Orientation.HORIZONTAL, 0.4);
        editor.panes().moveDivider(Orientation.HORIZONTAL, 0.35);
        assertThat(store.layoutFor(file)).as("nothing before the pause").isEmpty();
        assertThat(delay.pending).as("each change restarts the one pending save").hasSize(1);

        delay.elapse();

        assertThat(store.layoutFor(file).orElseThrow().horizontalDivider()).isEqualTo(OptionalDouble.of(0.35));

        editor.panes().removeAll();
        delay.elapse();
        assertThat(store.layoutFor(file)).as("back to one pane forgets the file").isEmpty();
    }

    @Test
    void closingSavesAtOnceAndStopsThePendingSave() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path file = document.path().orElseThrow();
        Editor editor = open(document, 800, 600);
        editor.opened();
        editor.panes().split(Orientation.VERTICAL, 0.5);

        editor.close();

        assertThat(store.layoutFor(file)).isPresent();
        assertThat(delay.pending).isEmpty();
        toClose.remove(editor);
    }

    @Test
    void saveAsMovesTheLayoutToTheNewFile() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path old = document.path().orElseThrow();
        Editor editor = open(document, 800, 600);
        editor.opened();
        editor.panes().split(Orientation.VERTICAL, 0.5);
        delay.elapse();
        assertThat(store.layoutFor(old)).isPresent();

        Path target = folder.resolve("copia.vsim");
        document.save(target);
        editor.remembered().saved(Optional.of(old), target);

        assertThat(store.layoutFor(old)).isEmpty();
        assertThat(store.layoutFor(target).orElseThrow().verticalDivider()).isEqualTo(OptionalDouble.of(0.5));
    }

    @Test
    void theFirstSaveOfAnUntitledCircuitRemembersItsSplit() throws Exception {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        toClose.add(document);
        Editor editor = open(document, 800, 600);
        editor.panes().split(Orientation.HORIZONTAL, 0.5);

        Path target = folder.resolve("nuevo.vsim");
        document.save(target);
        editor.remembered().saved(Optional.empty(), target);

        assertThat(store.layoutFor(target)).isPresent();
    }

    @Test
    void aStaleEntryIsIgnored() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        preferences.splitLayoutsNode().node("0").put("path", document.path().orElseThrow().toString());
        preferences.splitLayoutsNode().node("0").put("horizontal", "no es un número");

        Editor editor = open(document, 800, 600);
        editor.opened();

        assertThat(editor.panes().panes()).hasSize(1);
    }

    @Test
    void aLayoutThatDoesNotFitTheWindowFallsBackToOnePane() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        ViewState view = new ViewState(1, 0, 0);
        store.remember(document.path().orElseThrow(), new PaneLayout(OptionalDouble.of(0.05), OptionalDouble.empty(),
                List.of(view, view)));

        Editor editor = open(document, 800, 300); // the top pane would be 15 px high
        editor.opened();

        assertThat(editor.panes().panes()).hasSize(1);
    }

    @Test
    void splittingAndRestoringNeverChangeTheDocumentNorItsFile() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path file = document.path().orElseThrow();
        byte[] before = Files.readAllBytes(file);
        var lastStep = document.history().lastExecuted();
        Editor first = open(document, 800, 600);
        first.opened();
        splitInFour(first);
        delay.elapse();
        first.close();
        assertThat(document.isModified()).isFalse();
        assertThat(document.history().lastExecuted()).as("no undo step").isEqualTo(lastStep);

        assertThat(Files.readAllBytes(file)).as("closing a split window writes nothing").isEqualTo(before);
        OpenDocument reopened = reopen(file);
        String unsplit = reopened.serialized();
        Editor again = open(reopened, 800, 600);
        again.opened();
        assertThat(again.panes().panes()).hasSize(4);
        assertThat(reopened.isModified()).isFalse();
        assertThat(reopened.history().canUndo()).as("no undo step").isFalse();

        reopened.save(file);
        assertThat(Files.readString(file)).isEqualTo(unsplit).doesNotContain("horizontal", "vertical", "zoom");
    }
    @Test
    void closingAnUnsplitSecondViewAfterTheSplitOneKeepsTheRememberedSplit() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path file = document.path().orElseThrow();
        Editor split = open(document, 800, 600);
        split.opened();
        Editor newView = open(document, 800, 600); // Ventana ▸ Nueva vista: one pane, never touched
        split.panes().split(Orientation.HORIZONTAL, 0.5);

        split.close(); // Archivo ▸ Salir closes the windows in order
        assertThat(store.layoutFor(file)).as("after the split window closed").isPresent();
        newView.close();

        assertThat(store.layoutFor(file)).as("after the unsplit Nueva vista closed").isPresent();
        assertThat(store.layoutFor(file).orElseThrow().horizontalDivider()).isEqualTo(OptionalDouble.of(0.5));
    }

    @Test
    void savingFromAnUnsplitSecondViewKeepsTheRememberedSplit() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path file = document.path().orElseThrow();
        Editor split = open(document, 800, 600);
        split.opened();
        split.panes().split(Orientation.VERTICAL, 0.5);
        delay.elapse();
        Editor newView = open(document, 800, 600);

        document.save(file); // Ctrl+S in the Nueva vista window
        newView.remembered().saved(Optional.of(file), file);

        assertThat(store.layoutFor(file)).isPresent();
    }

    @Test
    void saveAsFromAnUnsplitSecondViewMovesTheRememberedSplitToTheNewFile() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path old = document.path().orElseThrow();
        Editor split = open(document, 800, 600);
        split.opened();
        split.panes().split(Orientation.VERTICAL, 0.5);
        delay.elapse();
        Editor newView = open(document, 800, 600);

        Path target = folder.resolve("copia.vsim");
        document.save(target);
        newView.remembered().saved(Optional.of(old), target);

        assertThat(store.layoutFor(old)).isEmpty();
        assertThat(store.layoutFor(target).orElseThrow().verticalDivider()).isEqualTo(OptionalDouble.of(0.5));
    }

    @Test
    void aLayoutThatDidNotFitIsStillRememberedAfterTheWindowCloses() throws Exception {
        OpenDocument document = savedDocument("contador.vsim");
        Path file = document.path().orElseThrow();
        ViewState view = new ViewState(1, 0, 0);
        store.remember(file, new PaneLayout(OptionalDouble.of(0.05), OptionalDouble.empty(), List.of(view, view)));
        Editor small = open(document, 800, 300);
        small.opened();
        assertThat(small.panes().panes()).hasSize(1);

        small.close();

        assertThat(store.layoutFor(file)).as("not applied is not deleted").isPresent();
        toClose.remove(small);
    }
}
