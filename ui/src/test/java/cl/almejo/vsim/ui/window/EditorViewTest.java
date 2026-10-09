package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
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
        assertThat(inspector.tabCount()).isEqualTo(1); // the Properties tab of the label features
        assertThat(inspectorSplit.getDividerSize()).isPositive();
    }

    @Test
    void inspectorSitsNextToTheCanvasAndShowsItsTabs() {
        InspectorPanel inspector = view.inspector();
        assertThat(inspector.isVisible()).isTrue();
        assertThat(inspector.tabCount()).isEqualTo(1); // the Properties tab of the label features
        assertThat(((JSplitPane) inspector.getParent()).getLeftComponent()).isSameAs(view.canvas());
        inspector.addTab("Uno", new JLabel("1"));
        inspector.addTab("Dos", new JLabel("2"));
        assertThat(inspector.isVisible()).isTrue();
        assertThat(inspector.tabCount()).isEqualTo(3);
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
        assertThat(toggle("Cable").isSelected()).isTrue();
        view.palette().choose(StandardComponentTypes.LED);
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);
        assertThat(toggle("Cable").isSelected()).isFalse();
        assertThat(toggle("Selección").isSelected()).isFalse();
        perform(view.actions().panTool());
        assertThat(view.canvas().tool()).isInstanceOf(PanTool.class);
        view.palette().choose(StandardComponentTypes.LED);
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);
        view.canvas().useDefaultTool();
        assertThat(toggle("Selección").isSelected()).isTrue();
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

    private AbstractButton toggle(String text) {
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("no toolbar button " + text);
    }

    private static void layoutTree(java.awt.Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (java.awt.Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }
}
