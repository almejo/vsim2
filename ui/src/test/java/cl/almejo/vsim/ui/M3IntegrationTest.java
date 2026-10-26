package cl.almejo.vsim.ui;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Container;
import java.awt.datatransfer.Clipboard;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** M3 features working together on real example circuits, headless (one {@link EditorView} per document). */
class M3IntegrationTest {

    private static final Path VSIM1_CIRCUIT = Path.of("../core/src/test/resources/vsim1/circuit.json");
    private static final Path LABELS = Path.of("../core/src/test/resources/fixtures/m3/labels.vsim");
    private static final Path SHORT_CIRCUIT = Path.of("../core/src/test/resources/fixtures/m3/short-circuit.vsim");

    private final List<OpenDocument> documents = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private final FragmentClipboard clipboard = new FragmentClipboard(new Clipboard("integration"));

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        documents.forEach(OpenDocument::close);
    }

    private OpenDocument open(Path path) throws Exception {
        OpenDocument document = OpenDocument.open(path, StandardComponentTypes.registry());
        documents.add(document);
        return document;
    }

    private OpenDocument createEmpty() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        return document;
    }

    private EditorView viewOf(OpenDocument document) {
        EditorView view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        view.canvas().setSize(800, 600);
        views.add(view);
        return view;
    }

    /** The view's clipboard actions, on a private clipboard instead of the system one. */
    private SelectionActions clipboardActions(EditorView view) {
        return new SelectionActions(view.openDocument(), view::canvas, view.canvas().selection(), view.actions(),
                clipboard);
    }

    private static boolean synced(OpenDocument document) {
        return document.latestSnapshot().running() && document.latestSnapshot().token() == document.netlistToken();
    }

    private static <T extends java.awt.Component> Optional<T> find(Container root, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return Optional.of(type.cast(child));
            }
            if (child instanceof Container container) {
                Optional<T> found = find(container, type);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void theVsim1CircuitIsCopiedWholeIntoANewDocumentAndSimulates() throws Exception {
        OpenDocument source = open(VSIM1_CIRCUIT);
        EditorView sourceView = viewOf(source);
        CircuitDocument original = source.document();
        assertThat(original.components()).isNotEmpty();
        SelectionActions sourceActions = clipboardActions(sourceView);
        perform(sourceActions.selectAll());
        assertThat(sourceView.canvas().selection().size()).isEqualTo(original.elements().size());
        perform(sourceActions.copy());

        OpenDocument target = createEmpty();
        EditorView targetView = viewOf(target);
        perform(clipboardActions(targetView).paste());
        assertThat(targetView.canvas().tool()).isInstanceOf(FloatingSelectionTool.class);
        assertThat(target.document().isEmpty()).isTrue();
        assertThat(targetView.canvas().tool().keyPressed(targetView.canvas(), KeyEvent.VK_ENTER, false)).isTrue();

        assertThat(targetView.canvas().tool()).isInstanceOf(SelectTool.class);
        assertThat(target.document().components()).hasSameSizeAs(original.components());
        assertThat(target.document().wires()).hasSameSizeAs(original.wires());
        assertThat(target.document().labels()).hasSameSizeAs(original.labels());
        assertThat(target.history().undoDescription()).contains("command.paste");
        assertThat(targetView.canvas().selection().size()).isEqualTo(target.document().elements().size());

        waitUntil(() -> synced(target));
        assertThat(target.latestSnapshot().failure()).isEmpty();
        assertThat(target.latestSnapshot().shortCircuits()).isEmpty();
    }

    @Test
    void rotatingTheWholeVsim1CircuitAndUndoingRestoresTheFile() throws Exception {
        OpenDocument document = open(VSIM1_CIRCUIT);
        EditorView view = viewOf(document);
        CircuitFormat format = new CircuitFormat(document.registry());
        String before = format.write(document.document());
        perform(clipboardActions(view).selectAll());
        perform(view.actions().rotateClockwise());
        assertThat(document.history().undoDescription()).contains("command.rotate");
        assertThat(format.write(document.document())).isNotEqualTo(before);
        document.history().undo();
        assertThat(format.write(document.document())).isEqualTo(before);
    }

    @Test
    void labelsSurviveOpeningAndShowInTheProperties() throws Exception {
        OpenDocument document = open(LABELS);
        EditorView view = viewOf(document);
        Component first = document.document().component(new ElementId("c1")).orElseThrow();
        assertThat(first.label()).isEqualTo("A");
        assertThat(document.document().components()).filteredOn(component -> !component.label().isEmpty())
                .hasSizeGreaterThanOrEqualTo(6);
        view.canvas().selection().select(first.id());
        JTextField labelField = find(view.labelFeatures().properties(), JTextField.class).orElseThrow();
        assertThat(labelField.getText()).isEqualTo("A");
    }

    @Test
    void shortCircuitsAreListedAndF8SelectsTheWiresOfTheFirst() throws Exception {
        OpenDocument document = open(SHORT_CIRCUIT);
        EditorView view = viewOf(document);
        waitUntil(() -> synced(document) && document.latestSnapshot().shortCircuits().size() >= 2);
        // The canvas frame timer only runs while the canvas is displayed: hand the snapshot over as it would.
        view.problems().showSnapshot(document.latestSnapshot());

        JList<?> list = find(view.inspector(), JList.class).orElseThrow();
        assertThat(list.getModel().getSize()).isGreaterThanOrEqualTo(2);
        List<Problem> problems = view.problems().navigator().problems();
        assertThat(problems).hasSizeGreaterThanOrEqualTo(2);

        JComponent canvas = view.canvas();
        Object binding = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0));
        perform(canvas.getActionMap().get(binding));

        Set<ElementId> expected = new HashSet<>(
                document.netlist().net(new NetId(problems.get(0).netId())).orElseThrow().wires());
        assertThat(expected).isNotEmpty();
        assertThat(view.canvas().selection().ids()).containsExactlyInAnyOrderElementsOf(expected);
    }
}
