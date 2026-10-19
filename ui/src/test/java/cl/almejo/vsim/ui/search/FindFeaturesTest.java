package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FindFeaturesTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<JComponent> focused = new ArrayList<>();
    private java.awt.Component focusOwner;
    private EditorView view;
    private FindFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        view = newView();
        features = new FindFeatures(view, () -> focusOwner, focused::add);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private EditorView newView() {
        return new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of()), NO_WINDOW);
    }

    private Component labeled(String typeId, int x, int y, String label) {
        Component component = place(openDocument, typeId, x, y, Map.of());
        openDocument.execute(new SetLabelCommand(component.id(), label));
        return component;
    }

    private void perform(javax.swing.Action action) {
        action.actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, ""));
    }

    private void type(String text) {
        features.bar().field().setText(text);
    }

    private void press(int keyCode, int modifiers) {
        SearchField field = features.bar().field();
        field.deliver(new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private List<ElementId> currentIds() {
        return features.current().map(hit -> List.of(hit.elementId())).orElse(List.of());
    }

    @Test
    void ctrlFOpensTheBarAboveTheCanvasWithTheFocusInTheField() {
        assertThat(features.isOpen()).isFalse();
        assertThat(features.bar().getParent()).isNull();

        perform(features.findAction());

        assertThat(features.isOpen()).isTrue();
        Container area = view.canvas().getParent();
        assertThat(features.bar().getParent()).isSameAs(area);
        assertThat(focused).last().isSameAs(features.bar().field());
        assertThat(texts(view.menu(MenuId.EDIT))).contains("Buscar…", "Buscar siguiente", "Buscar anterior");
        assertThat(view.shortcutActions()).containsKeys("edit.find", "edit.findNext", "edit.findPrevious");
    }

    @Test
    void ctrlFAgainSelectsTheWholeText() {
        perform(features.findAction());
        type("U1");
        features.bar().field().select(1, 1);

        perform(features.findAction());

        assertThat(features.bar().field().getSelectedText()).isEqualTo("U1");
    }

    @Test
    void typingHighlightsEveryMatchCountsThemAndSelectsTheFirst() {
        Component first = labeled(StandardComponentTypes.NOT, 20, 0, "U2");
        Component second = labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        labeled(StandardComponentTypes.NOT, 0, 20, "X");
        perform(features.findAction());

        type("u");

        assertThat(features.bar().counterText()).isEqualTo("1 de 2");
        assertThat(features.bar().showsError()).isFalse();
        assertThat(features.overlay().highlighted()).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(currentIds()).containsExactly(second.id()); // reading order: (0,0) before (20,0)
        assertThat(view.canvas().selection().ids()).containsExactly(second.id());
    }

    @Test
    void enterAndF3GoForwardWrappingShiftEnterAndShiftF3GoBack() {
        Component a = labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        Component b = labeled(StandardComponentTypes.NOT, 20, 0, "U2");
        Component c = labeled(StandardComponentTypes.NOT, 0, 20, "U3");
        perform(features.findAction());
        type("U");

        press(KeyEvent.VK_ENTER, 0);
        assertThat(currentIds()).containsExactly(b.id());
        press(KeyEvent.VK_DOWN, 0);
        assertThat(currentIds()).containsExactly(c.id());
        assertThat(features.bar().counterText()).isEqualTo("3 de 3");
        perform(features.findNextAction());
        assertThat(currentIds()).containsExactly(a.id());
        press(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
        assertThat(currentIds()).containsExactly(c.id());
        press(KeyEvent.VK_UP, 0);
        assertThat(currentIds()).containsExactly(b.id());
        perform(features.findPreviousAction());
        assertThat(currentIds()).containsExactly(a.id());
        features.bar().nextButton().doClick();
        assertThat(currentIds()).containsExactly(b.id());
        features.bar().previousButton().doClick();
        assertThat(currentIds()).containsExactly(a.id());
        assertThat(view.canvas().selection().ids()).containsExactly(a.id());
    }

    @Test
    void theCurrentMatchIsTravelledToFollowingThePreference() {
        Component far = labeled(StandardComponentTypes.NOT, 200, 100, "lejos");
        view.canvas().setSize(400, 300);
        ManualFrameTicker ticker = new ManualFrameTicker().drive(view.canvas());
        perform(features.findAction());

        type("lejos");

        assertThat(view.canvas().isTravelling()).isTrue();
        ticker.finish();
        assertThat(features.current()).isPresent();
        assertThat(features.current().get().elementId()).isEqualTo(far.id());
        Point2D center = view.canvas().viewport().screenToGrid(200, 150);
        assertThat(center.getX()).isBetween(195.0, 210.0);
        assertThat(center.getY()).isBetween(95.0, 110.0);
    }

    @Test
    void f3WithTheBarClosedOpensItAndGoesToTheNextMatch() {
        Component a = labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        Component b = labeled(StandardComponentTypes.NOT, 20, 0, "U2");
        perform(features.findAction());
        type("U");
        press(KeyEvent.VK_ESCAPE, 0);
        assertThat(features.isOpen()).isFalse();

        perform(features.findNextAction());

        assertThat(features.isOpen()).isTrue();
        assertThat(currentIds()).containsExactly(b.id());
        assertThat(features.overlay().highlighted()).containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void reopeningTheBarGoesOnFromTheMatchTheUserWasOn() {
        Component first = labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        Component second = labeled(StandardComponentTypes.NOT, 20, 0, "U2");
        Component third = labeled(StandardComponentTypes.NOT, 40, 0, "U3");
        perform(features.findAction());
        type("U");
        press(KeyEvent.VK_ENTER, 0);
        press(KeyEvent.VK_ENTER, 0);
        assertThat(currentIds()).containsExactly(third.id());
        press(KeyEvent.VK_ESCAPE, 0);

        perform(features.findAction()); // Ctrl+F: back on the same match
        assertThat(currentIds()).containsExactly(third.id());
        press(KeyEvent.VK_ESCAPE, 0);

        perform(features.findNextAction()); // F3: the one after it, wrapping around
        assertThat(currentIds()).containsExactly(first.id());
        press(KeyEvent.VK_ESCAPE, 0);

        perform(features.findPreviousAction()); // Mayús+F3: the one before it
        assertThat(currentIds()).containsExactly(third.id());
        assertThat(second).isNotNull();
    }

    @Test
    void f3WhenTheMatchOfBeforeIsGoneStartsAtTheFirstAndMayusF3AtTheLast() {
        Component first = labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        Component second = labeled(StandardComponentTypes.NOT, 20, 0, "U2");
        Component third = labeled(StandardComponentTypes.NOT, 40, 0, "U3");
        perform(features.findAction());
        type("U");
        press(KeyEvent.VK_ENTER, 0); // on U2
        press(KeyEvent.VK_ESCAPE, 0);
        openDocument.execute(new RemoveElementsCommand(List.of(second.id())));

        perform(features.findNextAction());
        assertThat(currentIds()).containsExactly(first.id());

        press(KeyEvent.VK_ESCAPE, 0);
        openDocument.execute(new RemoveElementsCommand(List.of(first.id())));
        labeled(StandardComponentTypes.NOT, 60, 0, "U4");
        perform(features.findPreviousAction());
        assertThat(features.current().orElseThrow().matchedText()).isEqualTo("U4");
        assertThat(third).isNotNull();
    }

    @Test
    void aComponentOfAnUnknownTypeIsHighlightedLikeAnyOther() {
        // An open document cannot hold one (its netlist would not compile), but the overlay paints any document.
        cl.almejo.vsim.core.document.CircuitDocument document = new cl.almejo.vsim.core.document.CircuitDocument();
        Component unknown = new Component(document.newComponentId(), "no-such-type", new GridPoint(3, 3),
                cl.almejo.vsim.core.geometry.Rotation.R0, Map.of(), "misterio");
        document.add(unknown);
        FindHighlightOverlay overlay = new FindHighlightOverlay(() -> ColorScheme.LIGHT,
                StandardComponentTypes.registry(), () -> { });
        overlay.show(java.util.Set.of(unknown.id()), java.util.Optional.of(unknown.id()));

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(200, 200,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new cl.almejo.vsim.ui.canvas.RenderState(document, null, 0,
                cl.almejo.vsim.core.simulation.SimulationSnapshot.EMPTY, java.util.Set.of(), 0));
        graphics.dispose();

        int pixel = 3 * GridPoint.PIXELS_PER_UNIT; // its position: the only place known for it
        assertThat(image.getRGB(pixel, pixel) >>> 24).as("alpha of the patch").isPositive();
    }

    @Test
    void inANarrowWindowTheFieldShrinksBeforeTheCloseButtonIsCut() {
        FindBar bar = features.bar();
        bar.setSize(400, bar.getPreferredSize().height);
        bar.doLayout();

        assertThat(bar.closeButton().getBounds().getMaxX()).isLessThanOrEqualTo(400);
        assertThat(bar.field().getWidth()).isLessThan(260).isGreaterThanOrEqualTo(100);
    }

    @Test
    void noMatchTurnsTheFieldRedWithSinResultados() {
        labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        perform(features.findAction());

        type("zzz");

        assertThat(features.bar().showsError()).isTrue();
        assertThat(features.bar().counterText()).isEqualTo("Sin resultados");
        assertThat(features.overlay().highlighted()).isEmpty();

        type("");
        assertThat(features.bar().showsError()).isFalse();
        assertThat(features.bar().counterText()).isEmpty();
    }

    @Test
    void anInvalidRegexTurnsTheFieldRedSaysWhyAndClearsTheHighlights() {
        labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        perform(features.findAction());
        type("U(");
        assertThat(features.overlay().highlighted()).isEmpty(); // plain text: no «U(» anywhere
        type("U");
        assertThat(features.overlay().highlighted()).hasSize(1);

        features.bar().regexToggle().doClick();
        type("U(");

        assertThat(preferences.findRegex()).isTrue();
        assertThat(features.bar().showsError()).isTrue();
        assertThat(features.bar().messageText()).isEqualTo("Expresión no válida: falta cerrar un paréntesis");
        assertThat(features.overlay().highlighted()).isEmpty();
        assertThat(features.current()).isEmpty();
    }

    @Test
    void aRegexThatGivesUpSaysSoAndHighlightsNothing() {
        labeled(StandardComponentTypes.NOT, 0, 0, "aa"); // matches cheaply, before the costly labels
        for (int index = 0; index < 300; index++) {
            labeled(StandardComponentTypes.NOT, 0, 10 + 10 * index, "a".repeat(20) + "!");
        }
        preferences.setFindMatchCase(true);
        preferences.setFindRegex(true);
        perform(features.findAction());

        type("^(a+)+\\1$");

        assertThat(features.bar().showsError()).isTrue();
        assertThat(features.bar().messageText()).isEqualTo("Expresión demasiado costosa de buscar; simplifícala");
        assertThat(features.hits()).isEmpty();
        assertThat(features.current()).isEmpty();
        assertThat(features.overlay().highlighted()).isEmpty();
        assertThat(view.canvas().selection().isEmpty()).isTrue();
    }

    @Test
    void twoUnlabeledPartsOfOneTypeStayApartWhenTheDocumentChanges() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        labeled(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 4, 4, "D");
        perform(features.findAction());
        type("not"); // the NOT inside each of the two unlabeled «Inversor» parts
        assertThat(features.hits()).hasSize(2);
        press(KeyEvent.VK_ENTER, 0);
        assertThat(features.bar().counterText()).isEqualTo("2 de 2");

        place(openDocument, StandardComponentTypes.LED, 0, 100, Map.of());

        assertThat(features.bar().counterText()).isEqualTo("2 de 2");
    }

    @Test
    void theOptionTogglesChangeTheResultsPersistAndFollowOtherViews() {
        labeled(StandardComponentTypes.NOT, 0, 0, "abc");
        labeled(StandardComponentTypes.NOT, 20, 0, "ABC");
        perform(features.findAction());
        type("abc");
        assertThat(features.hits()).hasSize(2);

        features.bar().matchCaseToggle().doClick();

        assertThat(preferences.findMatchCase()).isTrue();
        assertThat(features.hits()).hasSize(1);
        EditorView other = newView();
        FindFeatures otherFeatures = new FindFeatures(other, () -> null, component -> { });
        try {
            assertThat(otherFeatures.bar().matchCaseToggle().isSelected()).isTrue();
            otherFeatures.bar().matchCaseToggle().doClick();
            assertThat(features.bar().matchCaseToggle().isSelected()).isFalse();
            assertThat(features.hits()).hasSize(2);
        } finally {
            otherFeatures.dispose();
            other.dispose();
        }
        type("^a.c$");
        features.bar().regexToggle().doClick();
        assertThat(features.hits()).hasSize(2);
    }

    @Test
    void theSmoothTravelToggleIsTheAnimateViewMovesPreference() {
        preferences.setAnimateViewMoves(true);
        assertThat(features.bar().animateToggle().isSelected()).isTrue();

        features.bar().animateToggle().doClick();

        assertThat(preferences.animateViewMoves()).isFalse();
        preferences.setAnimateViewMoves(true);
        assertThat(features.bar().animateToggle().isSelected()).isTrue();
    }

    @Test
    void escapeOrTheCloseButtonClosesTheBarClearsTheHighlightsAndFocusesTheCanvas() {
        labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        perform(features.findAction());
        type("U");
        assertThat(features.overlay().highlighted()).hasSize(1);

        press(KeyEvent.VK_ESCAPE, 0);

        assertThat(features.isOpen()).isFalse();
        assertThat(features.bar().getParent()).isNull();
        assertThat(features.overlay().highlighted()).isEmpty();
        assertThat(focused).last().isSameAs(view.canvas());

        perform(features.findAction());
        assertThat(features.bar().field().getText()).isEqualTo("U"); // kept for the next search, like a browser
        assertThat(features.overlay().highlighted()).hasSize(1);
        features.bar().closeButton().doClick();
        assertThat(features.isOpen()).isFalse();
        assertThat(features.overlay().highlighted()).isEmpty();
    }

    @Test
    void editsWhileOpenKeepTheCountAndTheCurrentMatchCoherent() {
        Component a = labeled(StandardComponentTypes.NOT, 0, 0, "U1");
        Component b = labeled(StandardComponentTypes.NOT, 20, 0, "U2");
        Component c = labeled(StandardComponentTypes.NOT, 0, 20, "U3");
        perform(features.findAction());
        type("U");
        perform(features.findNextAction());
        assertThat(currentIds()).containsExactly(b.id());

        openDocument.execute(new RemoveElementsCommand(List.of(b.id())));
        assertThat(features.bar().counterText()).isEqualTo("2 de 2");
        assertThat(currentIds()).containsExactly(c.id()); // the next one in order
        assertThat(features.overlay().highlighted()).containsExactlyInAnyOrder(a.id(), c.id());

        openDocument.execute(new SetLabelCommand(a.id(), "V1"));
        assertThat(features.bar().counterText()).isEqualTo("1 de 1");
        assertThat(currentIds()).containsExactly(c.id()); // still matches: it stays current

        Component d = labeled(StandardComponentTypes.NOT, 0, -20, "U4");
        assertThat(features.bar().counterText()).isEqualTo("2 de 2");
        assertThat(currentIds()).containsExactly(c.id());
        assertThat(features.hits().get(0).elementId()).isEqualTo(d.id());

        openDocument.execute(new RemoveElementsCommand(List.of(c.id(), d.id())));
        assertThat(features.bar().counterText()).isEqualTo("Sin resultados");
        assertThat(features.current()).isEmpty();
        assertThat(features.overlay().highlighted()).isEmpty();
        perform(features.findNextAction()); // nothing to go to: no exception
        openDocument.history().undo();
        assertThat(features.bar().counterText()).isEqualTo("1 de 2");
    }

    @Test
    void aMatchInsideAUserComponentSelectsTheInstanceAndSaysWhere() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = labeled(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, 4, "U3");
        perform(features.findAction());

        type("not");

        assertThat(currentIds()).containsExactly(instance.id());
        assertThat(view.canvas().selection().ids()).containsExactly(instance.id());
        assertThat(features.bar().messageText()).isEqualTo("«NOT», dentro de «Inversor» (U3)");
    }

    @Test
    void freeTextsAreFoundToo() {
        TextLabel text = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "Acción");
        openDocument.execute(new AddElementsCommand(List.of(text)));
        perform(features.findAction());

        type("accion");

        assertThat(currentIds()).containsExactly(text.id());
    }

    @Test
    void openingTheBarDoesNotMoveTheCircuitOnScreen() {
        JPanel window = new JPanel(new java.awt.BorderLayout());
        window.add(view);
        window.setSize(900, 600);
        layoutTree(window);
        Point2D before = onScreen(view, 10, 10);
        List<String> viewChanges = new ArrayList<>();
        view.canvas().addViewListener(() -> viewChanges.add("changed"));

        perform(features.findAction());
        layoutTree(window);

        assertThat(view.canvas().getY()).isPositive();
        assertThat(onScreen(view, 10, 10)).isEqualTo(before);
        assertThat(viewChanges).isNotEmpty(); // the compensation pan is a view change (the saved view state follows)
        press(KeyEvent.VK_ESCAPE, 0);
        layoutTree(window);
        assertThat(view.canvas().getY()).isZero();
        assertThat(onScreen(view, 10, 10)).isEqualTo(before);
    }

    @Test
    void reopeningTheBarWithTextTravelsAllTheWayToTheMatchCentred() {
        reopenWithTextAndCheckTheViewEndsOnTheMatch(true);
    }

    @Test
    void reopeningTheBarWithTextJumpsCentredOnTheMatchWithoutAnimation() {
        reopenWithTextAndCheckTheViewEndsOnTheMatch(false);
    }

    private void reopenWithTextAndCheckTheViewEndsOnTheMatch(boolean animate) {
        preferences.setAnimateViewMoves(animate);
        Component far = labeled(StandardComponentTypes.NOT, 300, 200, "lejos");
        JPanel window = new JPanel(new java.awt.BorderLayout());
        window.add(view);
        window.setSize(900, 600);
        layoutTree(window);
        ManualFrameTicker ticker = new ManualFrameTicker().drive(view.canvas());
        perform(features.findAction());
        layoutTree(window);
        type("lejos");
        ticker.finish();
        press(KeyEvent.VK_ESCAPE, 0);
        layoutTree(window);
        view.canvas().applyViewState(new cl.almejo.vsim.ui.canvas.ViewState(1, 0, 0));

        perform(features.findAction()); // the text was kept: it searches again and goes to the match
        layoutTree(window); // what Swing's validation does next
        ticker.finish();

        Bounds target = DrawnBounds.of(openDocument.document().component(far.id()).orElseThrow(),
                openDocument.registry());
        Point2D centre = view.canvas().viewport().screenToGrid(view.canvas().getWidth() / 2.0,
                view.canvas().getHeight() / 2.0);
        assertThat(centre.getX()).isCloseTo(target.x() + target.width() / 2.0, within(0.01));
        assertThat(centre.getY()).isCloseTo(target.y() + target.height() / 2.0, within(0.01));
    }

    @Test
    void ctrlFInsideAComponentThatHasItsOwnFilterFocusesThatFilter() {
        JPanel tree = new JPanel();
        List<String> calls = new ArrayList<>();
        tree.putClientProperty(FindFeatures.FILTER_FOCUS_PROPERTY, (Runnable) () -> calls.add("filter"));
        JPanel row = new JPanel();
        tree.add(row);
        focusOwner = row;

        perform(features.findAction());

        assertThat(calls).containsExactly("filter");
        assertThat(features.isOpen()).isFalse();
    }

    @Test
    void disposeRemovesTheBarTheMenuItemsAndEveryListener() {
        int documentListeners = openDocument.changeListenerCount();
        int preferenceListeners = preferences.listenerCount();
        EditorView other = newView();
        int otherDocumentListeners = openDocument.changeListenerCount();
        FindFeatures otherFeatures = new FindFeatures(other, () -> null, component -> { });
        perform(otherFeatures.findAction());
        assertThat(openDocument.changeListenerCount()).isGreaterThan(otherDocumentListeners);
        int menuItems = other.menu(MenuId.EDIT).getMenuComponentCount();

        otherFeatures.dispose();

        assertThat(openDocument.changeListenerCount()).isEqualTo(otherDocumentListeners);
        assertThat(otherFeatures.bar().getParent()).isNull();
        assertThat(other.menu(MenuId.EDIT).getMenuComponentCount()).isLessThan(menuItems);
        assertThat(other.shortcutActions()).doesNotContainKeys("edit.find", "edit.findNext", "edit.findPrevious");
        other.dispose();
        assertThat(openDocument.changeListenerCount()).isEqualTo(documentListeners);
        assertThat(preferences.listenerCount()).isEqualTo(preferenceListeners);
    }

    private static Point2D onScreen(EditorView view, double gridX, double gridY) {
        Point2D inCanvas = view.canvas().viewport().gridToScreen(gridX, gridY);
        java.awt.Point point = new java.awt.Point((int) Math.round(inCanvas.getX()),
                (int) Math.round(inCanvas.getY()));
        return SwingUtilities.convertPoint(view.canvas(), point, view);
    }

    private static void layoutTree(java.awt.Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (java.awt.Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static List<String> texts(javax.swing.JMenu menu) {
        return Arrays.stream(menu.getMenuComponents())
                .map(child -> child instanceof JMenuItem item ? item.getText() : "---").toList();
    }
}
