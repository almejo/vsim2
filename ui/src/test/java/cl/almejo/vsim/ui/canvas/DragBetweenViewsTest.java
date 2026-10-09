package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tree.ComponentTreeExtension;
import cl.almejo.vsim.ui.tree.ComponentTreePanel;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;
import javax.swing.tree.TreeModel;
import java.awt.Container;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.dragOut;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.handlerOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agreed drag semantics on full editor views, headless: two views of one document (Ventana ▸ Nueva ventana de
 * este circuito) and a view of a second document. Swing's DnD is replaced by handing the dragged transferable to the
 * target's transfer handler, as a drop would.
 */
class DragBetweenViewsTest {

    private static final WindowCommands WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final List<EditorView> views = new ArrayList<>();
    private OpenDocument first;
    private OpenDocument second;
    private EditorView firstView;
    private EditorView firstOtherView;
    private EditorView secondView;
    private Component gate;

    @BeforeEach
    void open() {
        Messages.initialize(Messages.SPANISH);
        first = OpenDocument.createEmpty(StandardComponentTypes.registry());
        second = OpenDocument.createEmpty(StandardComponentTypes.registry());
        firstView = viewOf(first);
        firstOtherView = viewOf(first);
        secondView = viewOf(second);
        gate = place(first, StandardComponentTypes.NOT, 5, 5, Map.of());
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        first.close();
        second.close();
    }

    private EditorView viewOf(OpenDocument document) {
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new ComponentTreeExtension()));
        EditorView view = new EditorView(document, services, WINDOW);
        view.canvas().setSize(400, 300);
        views.add(view);
        return view;
    }

    private GridPoint positionOf(OpenDocument document, Component component) {
        return document.document().component(component.id()).orElseThrow().position();
    }

    @Test
    void aDragBetweenTwoViewsOfOneDocumentIsAMove() {
        Transferable transferable = dragOut(firstView.canvas(), 56, 56);

        assertThat(handlerOf(firstOtherView.canvas()).importFragment(transferable, new Point(160, 160), false))
                .isTrue();

        assertThat(first.document().components()).hasSize(1);
        assertThat(positionOf(first, gate)).isEqualTo(new GridPoint(18, 18));
        assertThat(firstOtherView.canvas().selection().ids()).containsExactly(gate.id());
        assertThat(firstView.canvas().isDraggingOut()).isFalse();
        first.history().undo();
        assertThat(positionOf(first, gate)).isEqualTo(new GridPoint(5, 5));
        first.history().redo();
        assertThat(positionOf(first, gate)).isEqualTo(new GridPoint(18, 18));
        assertThat(second.document().isEmpty()).isTrue();
    }

    @Test
    void ctrlCopiesBetweenViewsAndBetweenDocuments() {
        Transferable transferable = dragOut(firstView.canvas(), 56, 56);
        assertThat(handlerOf(firstOtherView.canvas()).importFragment(transferable, new Point(160, 160), true))
                .isTrue();
        assertThat(first.document().components()).extracting(Component::position)
                .containsExactly(new GridPoint(5, 5), new GridPoint(18, 18));

        Transferable again = dragOut(firstView.canvas(), 56, 56);
        assertThat(handlerOf(secondView.canvas()).importFragment(again, new Point(240, 80), true)).isTrue();
        assertThat(second.document().components()).singleElement()
                .satisfies(copy -> assertThat(copy.position()).isEqualTo(new GridPoint(28, 8)));
        assertThat(first.document().components()).hasSize(2);
        second.history().undo();
        assertThat(second.document().isEmpty()).isTrue();
    }

    @Test
    void withoutCtrlAnotherDocumentGetsAFloatingCopyFixedWithAClick() {
        Transferable transferable = dragOut(firstView.canvas(), 56, 56);
        assertThat(handlerOf(secondView.canvas()).importFragment(transferable, new Point(160, 160), false)).isTrue();
        assertThat(secondView.canvas().tool()).isInstanceOf(FloatingSelectionTool.class);

        CircuitCanvas target = secondView.canvas();
        target.getMouseListeners()[0].mousePressed(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK, 160, 160, 1, false, MouseEvent.BUTTON1));
        target.getMouseListeners()[0].mouseReleased(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, 0, 0,
                160, 160, 1, false, MouseEvent.BUTTON1));

        assertThat(second.document().components()).singleElement()
                .satisfies(copy -> assertThat(copy.position()).isEqualTo(new GridPoint(20, 20)));
        assertThat(positionOf(first, gate)).isEqualTo(new GridPoint(5, 5));
        assertThat(first.document().components()).hasSize(1);
    }

    /** The tree of the "Componentes" tab: the first JTree inside the inspector's ComponentTreePanel. */
    private static JTree treeOf(java.awt.Component root) {
        ComponentTreePanel panel = first(root, ComponentTreePanel.class);
        return panel == null ? null : first(panel, JTree.class);
    }

    private static <T> T first(java.awt.Component root, Class<T> kind) {
        if (kind.isInstance(root)) {
            return kind.cast(root);
        }
        if (root instanceof Container container) {
            for (java.awt.Component child : container.getComponents()) {
                T found = first(child, kind);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The number of component rows under the document row of the view's "Componentes" tab. */
    private static int componentRows(EditorView view) {
        JTree tree = treeOf(view.inspector());
        assertThat(tree).as("the tree tab is installed").isNotNull();
        TreeModel model = tree.getModel();
        return model.getChildCount(model.getRoot());
    }

    @Test
    void everyViewHasATreeTabThatFollowsItsDocument() {
        assertThat(componentRows(firstView)).isEqualTo(1);
        assertThat(componentRows(firstOtherView)).isEqualTo(1);
        assertThat(componentRows(secondView)).isZero();

        Transferable transferable = dragOut(firstView.canvas(), 56, 56);
        assertThat(handlerOf(firstOtherView.canvas()).importFragment(transferable, new Point(160, 160), true))
                .isTrue();
        assertThat(componentRows(firstView)).isEqualTo(2);
        assertThat(componentRows(firstOtherView)).isEqualTo(2);
        assertThat(componentRows(secondView)).isZero();

        first.history().undo();
        assertThat(componentRows(firstView)).isEqualTo(1);
        assertThat(componentRows(firstOtherView)).isEqualTo(1);
    }

    @Test
    void ctrlCopyingAUserComponentCarriesItsDefinitionAndOneUndoRemovesBoth() {
        first.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = place(first, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 30, 20, Map.of());
        Bounds bounds = ElementBounds.of(instance, first.registry());
        int pressX = (bounds.x() + bounds.width() / 2) * 8;
        int pressY = (bounds.y() + bounds.height() / 2) * 8;
        assertThat(second.document().definition(ExampleDefinitions.INVERTER)).isEmpty();

        Transferable transferable = dragOut(firstView.canvas(), pressX, pressY);
        assertThat(handlerOf(secondView.canvas()).importFragment(transferable, new Point(160, 160), true)).isTrue();

        assertThat(second.document().definition(ExampleDefinitions.INVERTER)).isPresent();
        assertThat(second.document().components()).singleElement()
                .extracting(Component::typeId).isEqualTo(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER));
        assertThat(componentRows(secondView)).isEqualTo(1);
        second.history().undo();
        assertThat(second.document().components()).isEmpty();
        assertThat(second.document().definition(ExampleDefinitions.INVERTER)).isEmpty();
        assertThat(first.document().definition(ExampleDefinitions.INVERTER)).isPresent();
        assertThat(first.document().components()).hasSize(2);
    }
}
