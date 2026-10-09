package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.AppearanceTestSupport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.WireTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditorViewTest {

    private final List<String> windowCalls = new ArrayList<>();
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { windowCalls.add("new"); }
        @Override public void open() { windowCalls.add("open"); }
        @Override public void save() { windowCalls.add("save"); }
        @Override public void saveAs() { windowCalls.add("saveAs"); }
        @Override public void closeWindow() { windowCalls.add("close"); }
        @Override public void exit() { windowCalls.add("exit"); }
    };
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void twoViewsOfOneDocumentFollowItWithTheirOwnSelection() {
        EditorView second = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
        try {
            Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
            assertThat(view.actions().undo().isEnabled()).isTrue();
            assertThat(second.actions().undo().isEnabled()).isTrue();
            view.canvas().selection().select(gate.id());
            assertThat(second.canvas().selection().isEmpty()).isTrue();
            perform(second.actions().undo());
            assertThat(view.canvas().selection().isEmpty()).isTrue();
            assertThat(view.actions().undo().isEnabled()).isFalse();
        } finally {
            second.dispose();
        }
    }

    @Test
    void aDisposedViewStopsFollowingTheDocument() {
        EditorView second = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
        second.dispose();
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        assertThat(second.actions().undo().isEnabled()).isFalse();
        assertThat(view.actions().undo().isEnabled()).isTrue();
        second.dispose();
    }

    @Test
    void menuBarHasTheSixMenusInOrder() {
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < view.menuBar().getMenuCount(); index++) {
            JMenu menu = view.menuBar().getMenu(index);
            titles.add(menu.getText());
        }
        assertThat(titles).containsExactly("Archivo", "Editar", "Ver", "Simulación", "Ventana", "Ayuda");
    }

    @Test
    void menuReturnsEachMenuOfTheBarById() {
        for (MenuId id : MenuId.values()) {
            assertThat(view.menu(id)).isSameAs(view.menuBar().getMenu(id.ordinal()));
        }
        assertThat(view.menu(MenuId.WINDOW).getItemCount()).isZero();
    }

    @Test
    void theInspectorDividerIsDrawnOnceTheEditorHasTabs() {
        InspectorPanel inspector = view.inspector();
        JSplitPane inspectorSplit = (JSplitPane) inspector.getParent();
        assertThat(inspector.tabCount()).isEqualTo(2); // the Properties (labels) and Problems tabs
        assertThat(inspectorSplit.getDividerSize()).isPositive();
    }

    @Test
    void aNewViewOpensTheInspectorAtItsPreferredWidthWithAVisibleDivider() {
        InspectorPanel inspector = view.inspector();
        JSplitPane inspectorSplit = (JSplitPane) inspector.getParent();
        view.setSize(1280, 760);
        layoutTree(view);

        assertThat(inspector.getWidth()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);
        assertThat(inspectorSplit.getDividerSize()).isGreaterThanOrEqualTo(InspectorSplit.DIVIDER_SIZE);
        assertThat(inspectorSplit.getDividerLocation()).isEqualTo(inspectorSplit.getWidth()
                - inspectorSplit.getInsets().right - inspectorSplit.getDividerSize() - InspectorPanel.PREFERRED_WIDTH);
        assertThat(view.canvas().getWidth()).isGreaterThan(InspectorPanel.PREFERRED_WIDTH);
    }

    @Test
    void showingATabReopensACollapsedInspectorAtItsPreferredWidth() {
        InspectorPanel inspector = view.inspector();
        JSplitPane inspectorSplit = (JSplitPane) inspector.getParent();
        view.setSize(1280, 760);
        layoutTree(view);
        inspectorSplit.setDividerLocation(inspectorSplit.getWidth());
        layoutTree(view);
        assertThat(inspector.getWidth()).isZero();

        inspector.showTab(inspector.selectedTab().orElseThrow());
        layoutTree(view);

        assertThat(inspector.getWidth()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);
    }

    @Test
    void inspectorSitsNextToTheCanvasAndShowsItsTabs() {
        InspectorPanel inspector = view.inspector();
        assertThat(inspector.isVisible()).isTrue();
        assertThat(inspector.tabCount()).isEqualTo(2); // the Properties and Problems tabs
        assertThat(((JSplitPane) inspector.getParent()).getLeftComponent()).isSameAs(view.canvas());
        inspector.addTab("Uno", new JLabel("1"));
        inspector.addTab("Dos", new JLabel("2"));
        assertThat(inspector.isVisible()).isTrue();
        assertThat(inspector.tabCount()).isEqualTo(4);
        inspector.showTab("Dos");
        assertThat(inspector.selectedTab()).contains("Dos");
        assertThatThrownBy(() -> inspector.showTab("Tres")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inspector.addTab("Uno", new JLabel())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void undoIsDisabledUntilSomethingIsDoneAndThenNamesTheCommand() {
        Action undo = view.actions().undo();
        assertThat(undo.isEnabled()).isFalse();
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        assertThat(undo.isEnabled()).isTrue();
        assertThat(undo.getValue(Action.NAME)).isEqualTo("Deshacer agregar");
        perform(undo);
        assertThat(openDocument.document().isEmpty()).isTrue();
        assertThat(view.actions().redo().getValue(Action.NAME)).isEqualTo("Rehacer agregar");
    }

    @Test
    void deleteRemovesTheSelectionAsOneStep() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        assertThat(view.actions().delete().isEnabled()).isFalse();
        view.canvas().selection().select(gate.id());
        assertThat(view.actions().delete().isEnabled()).isTrue();
        perform(view.actions().delete());
        assertThat(openDocument.document().isEmpty()).isTrue();
        assertThat(openDocument.history().undoDescription()).contains("command.remove");
        assertThat(view.actions().delete().isEnabled()).isFalse();
    }

    @Test
    void rotateActionsRotateTheSelectedComponent() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());
        perform(view.actions().rotateClockwise());
        assertThat(openDocument.document().component(gate.id()).orElseThrow().rotation()).isEqualTo(Rotation.R90);
        perform(view.actions().rotateCounterClockwise());
        perform(view.actions().rotateCounterClockwise());
        assertThat(openDocument.document().component(gate.id()).orElseThrow().rotation()).isEqualTo(Rotation.R270);
    }

    @Test
    void configureIsEnabledOnlyForOneComponentWithParameters() {
        Component display = place(openDocument, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 0, 0, Map.of());
        Component clock = place(openDocument, StandardComponentTypes.CLOCK, 20, 0, Map.of());
        view.canvas().selection().select(display.id());
        assertThat(view.actions().configure().isEnabled()).isFalse();
        view.canvas().selection().select(clock.id());
        assertThat(view.actions().configure().isEnabled()).isTrue();
        view.canvas().selection().clear();
        assertThat(view.actions().configure().isEnabled()).isFalse();
    }

    @Test
    void rotateAndDeleteAreCanvasBindingsNotGlobalAccelerators() {
        assertThat(view.actions().delete().getValue(Action.ACCELERATOR_KEY)).isNull();
        assertThat(view.actions().rotateClockwise().getValue(Action.ACCELERATOR_KEY)).isNull();
        assertThat(view.actions().rotateCounterClockwise().getValue(Action.ACCELERATOR_KEY)).isNull();
        var canvasKeys = view.canvas().getInputMap(JComponent.WHEN_FOCUSED);
        assertThat(canvasKeys.get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0))).isEqualTo("delete");
        assertThat(canvasKeys.get(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0))).isEqualTo("delete");
        assertThat(canvasKeys.get(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0))).isEqualTo("rotateClockwise");
        assertThat(canvasKeys.get(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK)))
                .isEqualTo("rotateCounterClockwise");
        assertThat(view.palette().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0))).isNull();
        assertThat(view.actions().undo().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
    }

    @Test
    void toolButtonsAndPaletteFollowTheCanvasTool() {
        perform(view.actions().wireTool());
        assertThat(view.canvas().tool()).isInstanceOf(WireTool.class);
        assertThat(toggle(view.actions().wireTool()).isSelected()).isTrue();
        view.palette().choose(StandardComponentTypes.LED);
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);
        assertThat(toggle(view.actions().wireTool()).isSelected()).isFalse();
        assertThat(toggle(view.actions().selectTool()).isSelected()).isFalse();
        perform(view.actions().panTool());
        assertThat(view.canvas().tool()).isInstanceOf(PanTool.class);
        view.palette().choose(StandardComponentTypes.LED);
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);
        view.canvas().useDefaultTool();
        assertThat(toggle(view.actions().selectTool()).isSelected()).isTrue();
    }

    @Test
    void fileActionsAreDelegatedToTheWindow() {
        perform(view.actions().newDocument());
        perform(view.actions().open());
        perform(view.actions().save());
        perform(view.actions().saveAs());
        perform(view.actions().closeWindow());
        perform(view.actions().exit());
        assertThat(windowCalls).containsExactly("new", "open", "save", "saveAs", "close", "exit");
    }

    @Test
    void simulationButtonsFollowTheSnapshot() {
        view.actions().refreshSimulationButtons(true);
        assertThat(view.actions().start().isEnabled()).isFalse();
        assertThat(view.actions().pause().isEnabled()).isTrue();
        assertThat(view.actions().step().isEnabled()).isFalse();
        view.actions().refreshSimulationButtons(false);
        assertThat(view.actions().start().isEnabled()).isTrue();
        assertThat(view.actions().pause().isEnabled()).isFalse();
        assertThat(view.actions().step().isEnabled()).isTrue();
    }

    @Test
    void viewWithTheOldFourBitAdderPaints() throws Exception {
        view.dispose();
        openDocument.close();
        openDocument = OpenDocument.open(Path.of("../examples/vsim1/4-bit-adder.json"), StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
        view.setSize(1200, 800);
        layoutTree(view);
        view.canvas().centerCircuit();
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        view.paint(graphics);
        graphics.dispose();
        assertThat(view.canvas().getWidth()).isGreaterThan(400);
        assertThat(image.getRGB(600, 400)).isNotZero();
    }

    @Test
    void zoomAcceptsPlusAndKeypadKeys() {
        var keys = view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        int control = InputEvent.CTRL_DOWN_MASK;
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, control))).isEqualTo("zoomIn");
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, control))).isEqualTo("zoomIn");
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, control | InputEvent.SHIFT_DOWN_MASK)))
                .isEqualTo("zoomIn");
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, control))).isEqualTo("zoomOut");
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, control))).isEqualTo("zoomReset");
        assertThat(view.getActionMap().get("zoomIn")).isSameAs(view.actions().zoomIn());
    }

    private AbstractButton toggle(javax.swing.Action action) {
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() == action) {
                return button;
            }
        }
        throw new AssertionError("no toolbar button for " + action.getValue(javax.swing.Action.NAME));
    }

    private static void layoutTree(java.awt.Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (java.awt.Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    @Test
    void deleteRemovesAMultiSelectionAsOneStep() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 8), new GridPoint(6, 8));
        openDocument.execute(new AddWireCommand(wire));
        TextLabel note = new TextLabel(openDocument.document().newLabelId(), new GridPoint(0, 10), "nota");
        openDocument.execute(new AddElementsCommand(List.of(note)));
        view.canvas().selection().set(List.of(gate.id(), wire.id(), note.id()));
        perform(view.actions().delete());
        assertThat(openDocument.document().isEmpty()).isTrue();
        assertThat(view.canvas().selection().isEmpty()).isTrue();
        assertThat(openDocument.history().undoDescription()).contains("command.remove");
        openDocument.history().undo();
        assertThat(openDocument.document().elements()).hasSize(3);
    }

    @Test
    void rotateTurnsAMultiSelectionAsAGroupInOneStep() {
        Component first = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 8, 0, Map.of());
        view.canvas().selection().set(List.of(first.id(), second.id()));
        perform(view.actions().rotateClockwise());
        Component rotatedFirst = openDocument.document().component(first.id()).orElseThrow();
        Component rotatedSecond = openDocument.document().component(second.id()).orElseThrow();
        assertThat(List.of(rotatedFirst, rotatedSecond)).allSatisfy(component ->
                assertThat(component.rotation()).isEqualTo(Rotation.R90));
        assertThat(rotatedFirst.position().y()).isNotEqualTo(rotatedSecond.position().y());
        assertThat(openDocument.history().undoDescription()).contains("command.rotate");
        openDocument.history().undo();
        assertThat(openDocument.document().components()).containsExactly(first, second);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    /** Review M2: a turn that changes nothing (a lone text label) adds no undo step and leaves the file clean. */
    @Test
    void rotatingOnlyATextLabelAddsNoUndoStep() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "nota");
        openDocument.document().add(label);
        view.canvas().selection().select(label.id());
        perform(view.actions().rotateClockwise());
        perform(view.actions().rotateCounterClockwise());
        assertThat(openDocument.document().label(label.id())).contains(label);
        assertThat(openDocument.history().canUndo()).isFalse();
        assertThat(openDocument.isModified()).isFalse();
    }

    @Test
    void rotateIsEnabledForAnySelection() {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 8), new GridPoint(6, 8));
        openDocument.execute(new AddWireCommand(wire));
        assertThat(view.actions().rotateClockwise().isEnabled()).isFalse();
        view.canvas().selection().select(wire.id());
        assertThat(view.actions().rotateClockwise().isEnabled()).isTrue();
        assertThat(view.actions().rotateCounterClockwise().isEnabled()).isTrue();
        view.canvas().selection().clear();
        assertThat(view.actions().rotateCounterClockwise().isEnabled()).isFalse();
    }

    @Test
    void statusBarCountsTheSelection() {
        Component first = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 8, 0, Map.of());
        view.canvas().selection().set(List.of(first.id(), second.id()));
        assertThat(view.statusBar().text()).contains("2 seleccionados");
        view.canvas().selection().clear();
        assertThat(view.statusBar().text()).doesNotContain("seleccionado");
    }

    @Test
    void editMenuOffersTheClipboardWithPlatformShortcuts() {
        JMenu edit = view.menu(MenuId.EDIT);
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < edit.getItemCount(); index++) {
            if (edit.getItem(index) != null) {
                texts.add(edit.getItem(index).getText());
            }
        }
        assertThat(texts).contains("Cortar", "Copiar", "Pegar", "Duplicar", "Seleccionar todo");
        int control = InputEvent.CTRL_DOWN_MASK;
        assertThat(view.selectionActions().cut().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_X, control));
        assertThat(view.selectionActions().copy().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_C, control));
        assertThat(view.selectionActions().paste().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_V, control));
        assertThat(view.selectionActions().duplicate().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_D, control));
        assertThat(view.selectionActions().selectAll().getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_A, control));
    }

    @Test
    void contextMenuForwardsRegisteredContributors() {
        view.addContextMenuContributor((menu, target, selection, gridPosition) ->
                menu.add(new javax.swing.JMenuItem("Stub")));
        javax.swing.JPopupMenu menu = view.contextMenu(java.util.Optional.empty(), new java.awt.geom.Point2D.Double());
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof javax.swing.JMenuItem item) {
                texts.add(item.getText());
            }
        }
        assertThat(texts).contains("Stub", "Pegar");
    }

    @Test
    void extensionsAreInstalledInOrderAndDisposedInReverse() {
        List<String> events = new ArrayList<>();
        EditorExtension first = target -> {
            events.add("install first " + (target.canvas() != null));
            return () -> events.add("dispose first");
        };
        EditorExtension second = target -> {
            events.add("install second");
            return () -> events.add("dispose second");
        };
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(first, second));
        EditorView extended = new EditorView(openDocument, services, window);
        assertThat(extended.services()).isSameAs(services);
        extended.dispose();
        assertThat(events).containsExactly("install first true", "install second", "dispose second", "dispose first");
    }

    @Test
    void anExtensionThatFailsToInstallUndoesTheEarlierOnesAndLeavesNoListener() {
        List<String> events = new ArrayList<>();
        Appearance appearance = new Appearance(ColorScheme.LIGHT);
        EditorExtension first = target -> {
            events.add("install first");
            return () -> events.add("dispose first");
        };
        EditorExtension failing = target -> {
            throw new IllegalStateException("broken extension");
        };
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance,
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(first, failing));
        assertThatThrownBy(() -> new EditorView(openDocument, services, window))
                .isInstanceOf(IllegalStateException.class).hasMessage("broken extension");
        assertThat(events).containsExactly("install first", "dispose first");
        assertThat(appearance.listenerCount()).isZero();
    }

    @Test
    void aDisposerThatThrowsStillLetsTheOtherDisposersAndTheViewCleanUp() {
        List<String> events = new ArrayList<>();
        Appearance appearance = new Appearance(ColorScheme.LIGHT);
        EditorExtension first = target -> () -> events.add("dispose first");
        EditorExtension broken = target -> () -> {
            events.add("dispose broken");
            throw new IllegalStateException("broken disposer");
        };
        EditorExtension alsoBroken = target -> () -> {
            throw new IllegalArgumentException("also broken");
        };
        EditorExtension last = target -> () -> events.add("dispose last");
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance,
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(first, broken, alsoBroken, last));
        EditorView extended = new EditorView(openDocument, services, window);

        assertThatThrownBy(extended::dispose)
                .isInstanceOf(IllegalArgumentException.class).hasMessage("also broken")
                .satisfies(exception -> assertThat(exception.getSuppressed()).hasSize(1)
                        .allSatisfy(suppressed -> assertThat(suppressed).hasMessage("broken disposer")));
        assertThat(events).containsExactly("dispose last", "dispose broken", "dispose first");
        assertThat(appearance.listenerCount()).isZero();
    }

    @Test
    void theViewRepaintsWithTheNewSchemeAndForgetsTheAppearanceWhenDisposed() {
        Appearance appearance = new Appearance(ColorScheme.LIGHT);
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance,
                AppPreferences.inMemory(), new ComponentLibrary(), List.of());
        EditorView themed = new EditorView(openDocument, services, window);
        assertThat(appearance.listenerCount()).isGreaterThan(0);

        appearance.setScheme(AppearanceTestSupport.withBackground(ColorScheme.LIGHT, Color.MAGENTA));
        themed.canvas().setSize(40, 40);
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        themed.canvas().paint(graphics);
        graphics.dispose();
        assertThat(image.getRGB(3, 3)).isEqualTo(Color.MAGENTA.getRGB());

        themed.dispose();
        assertThat(appearance.listenerCount()).isZero();
    }

    @Test
    void twoViewsSharingServicesFollowTheSchemeAndEachDisposeRemovesOnlyItsOwnListeners() {
        Appearance appearance = new Appearance(ColorScheme.LIGHT);
        AppPreferences preferences = AppPreferences.inMemory();
        ComponentLibrary library = new ComponentLibrary();
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance, preferences, library,
                List.of());
        try (OpenDocument otherDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            EditorView first = new EditorView(openDocument, services, window);
            int perView = appearance.listenerCount();
            EditorView second = new EditorView(otherDocument, services, window);
            assertThat(appearance.listenerCount()).isEqualTo(2 * perView);

            appearance.setScheme(AppearanceTestSupport.withBackground(ColorScheme.LIGHT, Color.MAGENTA));
            assertThat(backgroundOf(first)).isEqualTo(Color.MAGENTA.getRGB());
            assertThat(backgroundOf(second)).isEqualTo(Color.MAGENTA.getRGB());

            first.dispose();
            assertThat(appearance.listenerCount()).isEqualTo(perView);
            appearance.setScheme(AppearanceTestSupport.withBackground(ColorScheme.LIGHT, Color.CYAN));
            assertThat(backgroundOf(second)).isEqualTo(Color.CYAN.getRGB());

            second.dispose();
            assertThat(appearance.listenerCount()).isZero();
            assertThat(library.listenerCount()).isZero();
            assertThat(preferences.listenerCount()).isZero();
        }
    }

    private static int backgroundOf(EditorView editor) {
        editor.canvas().setSize(40, 40);
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        editor.canvas().paint(graphics);
        graphics.dispose();
        return image.getRGB(3, 3);
    }
}
