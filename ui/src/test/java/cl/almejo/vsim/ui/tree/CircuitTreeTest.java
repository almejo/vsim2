package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.search.FindFeatures;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** The circuit tree of the left column (spec §5.3), in a view whose window records what it is asked. */
class CircuitTreeTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> entered = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final List<String> places = new ArrayList<>();
    /** The single click a double click may still cancel: run by hand, as the timer would. */
    private final List<Runnable> pending = new ArrayList<>();
    private final Delay manualDelay = (milliseconds, task) -> {
        pending.add(task);
        return () -> pending.remove(task);
    };
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public void enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                          Optional<ViewState> from) {
            entered.add(definitionId + "@" + contextPath.orElse("-"));
        }

        @Override
        public Optional<EditorView> showPlace(OpenDocument document, Optional<String> definitionId,
                                              Optional<String> contextPath) {
            places.add(definitionId.orElse("-") + "@" + contextPath.orElse("-"));
            return Optional.empty();
        }

        @Override
        public boolean goToPart(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                                ElementId part) {
            shown.add(definitionId.orElse("-") + "@" + contextPath.orElse("-") + ":" + part.value());
            return true;
        }
    };
    private final List<EditorView> views = new ArrayList<>();
    private final List<CircuitTree> trees = new ArrayList<>();
    private final List<OpenDocument> bodies = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        trees.forEach(CircuitTree::dispose);
        views.forEach(EditorView::dispose);
        bodies.forEach(OpenDocument::close);
        openDocument.close();
    }

    private CircuitTree treeOf(OpenDocument place) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance, preferences,
                new cl.almejo.vsim.core.library.ComponentLibrary(), List.of());
        EditorView view = new EditorView(place, services, window);
        views.add(view);
        CircuitTree tree = new CircuitTree(view, manualDelay);
        trees.add(tree);
        return tree;
    }

    private CircuitTree tree() {
        return treeOf(openDocument);
    }

    /**
     * A LED (c1), an inverter on the circuit (c2) and a double inverter (c3) holding two more (c3/c2, c3/c3); each
     * inverter holds a NOT ({@code …/c2}).
     */
    private void inverterInThreePlaces() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place(openDocument, StandardComponentTypes.LED, 40, 10, Map.of());
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30, 0, Map.of());
    }

    private OpenDocument body(String definitionId, Optional<String> context) {
        OpenDocument body = openDocument.openBody(definitionId, context);
        bodies.add(body);
        return body;
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem menuItem && menuItem.getText().equals(text)) {
                return menuItem;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu));
    }

    @Test
    void rowsHaveTheDocumentThenEveryPieceWithItsIcon() {
        inverterInThreePlaces();
        CircuitTree tree = tree();

        assertThat(tree.rowText(CircuitTreeNode.DOCUMENT_PATH)).contains(openDocument.displayName());
        assertThat(tree.rightText(CircuitTreeNode.DOCUMENT_PATH)).contains("3 piezas");
        assertThat(tree.childPaths(CircuitTreeNode.DOCUMENT_PATH)).containsExactly("c1", "c2", "c3");
        assertThat(tree.childPaths("c3")).containsExactly("c3/c1", "c3/c2", "c3/c3", "c3/c4");
        assertThat(tree.childPaths("c3/c2")).containsExactly("c3/c2/c1", "c3/c2/c2", "c3/c2/c3");
        assertThat(tree.iconOf("c1")).containsInstanceOf(ComponentIcon.class);
        assertThat(tree.iconOf("c3")).containsInstanceOf(ComponentIcon.class);
        assertThat(tree.iconOf("c3/c2/c2")).containsInstanceOf(ComponentIcon.class);
        assertThat(tree.toolTipOf("c3/c2")).hasValueSatisfying(tip -> assertThat(tip).contains("c3/c2"));
    }

    @Test
    void unlabeledPiecesOfOneTypeAreNumbered() {
        inverterInThreePlaces();
        Component labeled = place(openDocument, StandardComponentTypes.BUFFER, 60, 0, Map.of());
        openDocument.execute(new SetLabelCommand(labeled.id(), "IR"));
        CircuitTree tree = tree();

        assertThat(tree.rowText("c2")).contains("Inversor");
        assertThat(tree.rowText("c3")).contains("Doble inversor");
        assertThat(tree.rowText("c3/c2")).contains("Inversor 1");
        assertThat(tree.rowText("c3/c3")).contains("Inversor 2");
        assertThat(tree.rowText(labeled.id().value())).contains("IR · Buffer");
    }

    @Test
    void aClickSelectsAndTravelsThroughGoToPart() {
        inverterInThreePlaces();
        CircuitTree tree = tree();

        tree.rowClicked("c2", 1); // a user component: waits to see whether a double click follows
        assertThat(shown).isEmpty();
        pending.removeFirst().run();
        tree.rowClicked("c3/c2/c2", 1); // a plain part: at once

        assertThat(shown).containsExactly("-@-:c2", ExampleDefinitions.INVERTER + "@c3/c2:c2");
        tree.rowClicked(CircuitTreeNode.DOCUMENT_PATH, 1);
        assertThat(places).containsExactly("-@-");
    }

    @Test
    void doubleClickOnAUserComponentEnters() {
        inverterInThreePlaces();
        CircuitTree tree = tree();

        tree.rowClicked("c3/c3", 1);
        tree.rowClicked("c3/c3", 2);
        tree.rowClicked("c3/c2/c2", 2); // a plain part has nothing to enter

        assertThat(entered).containsExactly(ExampleDefinitions.INVERTER + "@c3/c3");
        assertThat(pending).as("the double click cancelled the single one").isEmpty();
    }

    // --- the keyboard (round C, item 3) ---

    private final List<java.awt.Component> focusRequests = new ArrayList<>();
    private final KeyboardFocus recordingFocus = new KeyboardFocus() {
        @Override
        public Optional<java.awt.Component> owner() {
            return focusRequests.isEmpty() ? Optional.empty() : Optional.of(focusRequests.getLast());
        }

        @Override
        public void request(java.awt.Component target) {
            focusRequests.add(target);
        }
    };

    /** A tree whose view records the focus requests. */
    private CircuitTree keyboardTree() {
        CircuitTree tree = tree();
        views.getLast().useKeyboardFocus(recordingFocus);
        return tree;
    }

    private static boolean press(javax.swing.JComponent focused, int keyCode) {
        return SwingUtilities.processKeyBindings(new KeyEvent(focused, KeyEvent.KEY_PRESSED, 0, 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void enterOnARowGoesThereLikeAClickEvenOnAUserComponent() {
        inverterInThreePlaces();
        CircuitTree tree = keyboardTree();
        tree.choose("c3");

        assertThat(press(tree.tree(), KeyEvent.VK_ENTER)).isTrue();
        tree.choose("c1");
        press(tree.tree(), KeyEvent.VK_ENTER);

        assertThat(shown).containsExactly("-@-:c3", "-@-:c1");
        assertThat(entered).as("a double click or «Entrar» enters").isEmpty();
        assertThat(pending).as("no waiting for a double click").isEmpty();
    }

    @Test
    void upOnTheFirstRowFocusesTheSearchAndDownFromTheSearchComesBack() {
        inverterInThreePlaces();
        CircuitTree tree = keyboardTree();
        tree.choose("c1");

        press(tree.tree(), KeyEvent.VK_UP);
        assertThat(tree.chosenRow()).contains(CircuitTreeNode.DOCUMENT_PATH);
        assertThat(focusRequests).isEmpty();
        press(tree.tree(), KeyEvent.VK_UP);
        assertThat(focusRequests).containsExactly(tree.searchField());
        press(tree.searchField(), KeyEvent.VK_DOWN);

        assertThat(tree.chosenRow()).contains(CircuitTreeNode.DOCUMENT_PATH);
        assertThat(focusRequests).containsExactly(tree.searchField(), tree.tree());
        assertThat(shown).as("moving never travels").isEmpty();
        assertThat(places).isEmpty();
    }

    @Test
    void downFromASearchGoesToTheFirstResult() {
        inverterInThreePlaces();
        CircuitTree tree = keyboardTree();
        tree.searchField().setText("not");

        press(tree.searchField(), KeyEvent.VK_DOWN);

        assertThat(tree.chosenRow()).contains(tree.resultPaths().getFirst());
        assertThat(focusRequests).containsExactly(tree.tree());
    }

    @Test
    void escapeInTheTreeGivesTheFocusToTheCanvas() {
        inverterInThreePlaces();
        CircuitTree tree = keyboardTree();
        tree.choose("c1");

        assertThat(press(tree.tree(), KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(focusRequests).containsExactly(views.getLast().canvas());
    }

    @Test
    void theContextMenuOffersEnterSeePieceChangeShapeAndProperties() {
        inverterInThreePlaces();
        CircuitTree tree = tree();

        JPopupMenu instanceMenu = tree.contextMenu("c3/c2").orElseThrow();
        assertThat(texts(instanceMenu)).containsExactly("Entrar", "Ver la pieza", Messages.text("shape.change"),
                "Propiedades");
        assertThat(texts(tree.contextMenu("c3/c2/c2").orElseThrow())).containsExactly("Ver la pieza", "Propiedades");
        assertThat(tree.contextMenu(CircuitTreeNode.DOCUMENT_PATH)).isEmpty();

        item(instanceMenu, "Entrar").doClick(0);
        item(instanceMenu, "Ver la pieza").doClick(0);
        item(instanceMenu, "Propiedades").doClick(0);

        assertThat(entered).containsExactly(ExampleDefinitions.INVERTER + "@c3/c2");
        assertThat(shown).containsExactly(ExampleDefinitions.DOUBLE_INVERTER + "@c3:c2");
        assertThat(places).containsExactly(ExampleDefinitions.DOUBLE_INVERTER + "@c3");
    }

    @Test
    void changeShapeOfAPieceOfThisPlaceOpensTheShapeEditorHere() {
        inverterInThreePlaces();
        CircuitTree tree = tree();
        List<ElementId> shaped = new ArrayList<>();
        views.getFirst().setInstanceShapeEditor(shaped::add);

        item(tree.contextMenu("c2").orElseThrow(), Messages.text("shape.change")).doClick(0);

        assertThat(shaped).containsExactly(new ElementId("c2"));
        assertThat(views.getFirst().selection().ids()).containsExactly(new ElementId("c2"));
    }

    @Test
    void searchFindsAPieceInsideAComponentWithItsRoute() {
        inverterInThreePlaces();
        CircuitTree tree = tree();

        tree.searchField().setText("nOt");

        assertThat(tree.resultPaths()).containsExactly("c2/c2", "c3/c2/c2", "c3/c3/c2");
        assertThat(tree.rowText("c3/c2/c2")).contains("NOT");
        assertThat(tree.routeOf("c3/c2/c2")).contains("en Doble inversor › Inversor 1");
        assertThat(tree.routeOf("c2/c2")).contains("en Inversor");
        assertThat(tree.countText()).isEqualTo("3 resultados");

        tree.searchField().setText("nada así");
        assertThat(tree.resultPaths()).isEmpty();
        assertThat(tree.countText()).isEqualTo("Nada se llama así en este circuito.");
    }

    @Test
    void theMatchIsBoldEvenWhenItOnlyExistsIgnoringAccents() {
        inverterInThreePlaces();
        openDocument.execute(new SetLabelCommand(new ElementId("c1"), "Mi Acción"));
        CircuitTree tree = tree();

        tree.searchField().setText("accion");
        assertThat(tree.boldTextOf("c1")).contains("Acción");

        tree.searchField().setText("cci");
        assertThat(tree.boldTextOf("c1")).contains("cci");

        tree.searchField().setText("zzz");
        assertThat(tree.boldTextOf("c1")).isEmpty();
    }

    @Test
    void enterOnTheSearchGoesToTheFirst() {
        inverterInThreePlaces();
        CircuitTree tree = tree();
        tree.searchField().setText("not");

        tree.searchField().deliver(new KeyEvent(tree.searchField(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER,
                KeyEvent.CHAR_UNDEFINED));

        assertThat(shown).containsExactly(ExampleDefinitions.INVERTER + "@c2:c2");
    }

    @Test
    void escapeClearsTheSearchAndBringsBackTheTree() {
        inverterInThreePlaces();
        CircuitTree tree = tree();
        tree.searchField().setText("not");

        tree.searchField().deliver(new KeyEvent(tree.searchField(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED));

        assertThat(tree.searchField().getText()).isEmpty();
        assertThat(tree.resultPaths()).isEmpty();
        assertThat(tree.childPaths(CircuitTreeNode.DOCUMENT_PATH)).containsExactly("c1", "c2", "c3");
        assertThat(tree.getClientProperty(FindFeatures.FILTER_FOCUS_PROPERTY)).isInstanceOf(Runnable.class);
    }

    @Test
    void followsTheActiveTabAndMarksItsPlace() {
        inverterInThreePlaces();
        CircuitTree main = tree();
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c3/c3"));
        CircuitTree inside = treeOf(body);

        assertThat(main.markedRow()).contains(CircuitTreeNode.DOCUMENT_PATH);
        assertThat(inside.markedRow()).contains("c3/c3");
        assertThat(inside.isExpanded("c3")).isTrue();
        assertThat(inside.isExpanded("c3/c3")).isTrue();

        body.setContextPath(Optional.of("c2"));

        assertThat(inside.markedRow()).contains("c2");
        assertThat(inside.isExpanded("c2")).isTrue();
        assertThat(inside.rowText("c2/c2")).contains("NOT");
    }

    @Test
    void choosingAPieceOnTheCanvasChoosesItsRow() {
        inverterInThreePlaces();
        OpenDocument body = body(ExampleDefinitions.DOUBLE_INVERTER, Optional.of("c3"));
        CircuitTree inside = treeOf(body);

        views.getLast().selection().set(List.of(new ElementId("c2")));

        assertThat(inside.chosenRow()).contains("c3/c2");
        assertThat(shown).as("following the canvas never travels").isEmpty();
    }

    @Test
    void theTreeFollowsTheCircuitsEdits() {
        CircuitTree tree = tree();
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 20, 10, Map.of());
        assertThat(tree.rowText(gate.id().value())).contains("Buffer");
        assertThat(tree.rightText(CircuitTreeNode.DOCUMENT_PATH)).contains("1 pieza");

        openDocument.history().undo();

        assertThat(tree.rowText(gate.id().value())).isEmpty();
    }

    @Test
    void aSchemeChangeRedrawsTheIconsAndDisposeStopsListening() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, 4, Map.of());
        CircuitTree tree = tree();
        assertThat(((ComponentIcon) tree.iconOf(gate.id().value()).orElseThrow()).scheme())
                .isEqualTo(ColorScheme.LIGHT);
        int appearanceListeners = appearance.listenerCount();
        int documentListeners = openDocument.changeListenerCount();
        int preferenceListeners = preferences.listenerCount();

        ColorScheme dark = ColorSchemes.forTheme(true);
        appearance.setScheme(dark);
        assertThat(((ComponentIcon) tree.iconOf(gate.id().value()).orElseThrow()).scheme()).isEqualTo(dark);

        tree.dispose();
        trees.remove(tree);
        assertThat(appearance.listenerCount()).isEqualTo(appearanceListeners - 1);
        assertThat(openDocument.changeListenerCount()).isEqualTo(documentListeners - 1);
        assertThat(preferences.listenerCount()).isEqualTo(preferenceListeners - 1);
    }
}
