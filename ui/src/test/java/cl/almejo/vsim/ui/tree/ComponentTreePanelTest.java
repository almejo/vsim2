package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ComponentTreePanelTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                            Point2D screenPosition) {
                }

                @Override
                public void configure(Component component) {
                }
            });
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private ComponentTreePanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        canvas.setSize(400, 300);
        panel = new ComponentTreePanel(openDocument, canvas, PainterRegistry.standard(), appearance);
    }

    @AfterEach
    void close() {
        panel.dispose();
        canvas.dispose();
        openDocument.close();
    }

    private Component inverterInstance() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        return place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, Map.of());
    }

    private SimulationSnapshot snapshotWith(Map<String, VisibleState> states) {
        return new SimulationSnapshot(1, openDocument.netlistToken(), 0, true, Map.of(), states, List.of(),
                Optional.empty());
    }

    private static Point2D center(Bounds bounds) {
        return new Point2D.Double(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0);
    }

    @Test
    void showsTheDocumentAndFollowsItsChanges() {
        assertThat(panel.rowText("/")).contains("Sin título");
        Component gate = place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of());
        assertThat(panel.rowText(gate.id().value())).contains("NOT");
        openDocument.history().undo();
        assertThat(panel.rowText(gate.id().value())).isEmpty();
    }

    @Test
    void groupByTypeRebuildsTheTreeInGroups() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of());
        panel.groupByTypeToggle().setSelected(true);
        assertThat(panel.rowText("/not")).contains("NOT (1)");
        assertThat(panel.treePath(gate.id().value()).orElseThrow().getPathCount()).isEqualTo(3);
        panel.groupByTypeToggle().setSelected(false);
        assertThat(panel.rowText("/not")).isEmpty();
        assertThat(panel.treePath(gate.id().value()).orElseThrow().getPathCount()).isEqualTo(2);
    }

    @Test
    void choosingATopLevelRowSelectsAndCentersItsComponent() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of());
        panel.tree().setSelectionPath(panel.treePath(gate.id().value()).orElseThrow());
        assertThat(canvas.selection().ids()).containsExactly(gate.id());
        Point2D middle = center(ElementBounds.of(gate, openDocument.registry()));
        assertThat(canvas.viewport().gridToScreen(middle.getX(), middle.getY()))
                .isEqualTo(new Point2D.Double(200, 150));
    }

    @Test
    void choosingARowTravelsToItsComponentWhenViewMovesAreAnimated() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of());
        canvas.setAnimateViewMoves(() -> true);
        ManualFrameTicker ticker = new ManualFrameTicker().drive(canvas);
        Point2D origin = canvas.viewport().gridToScreen(0, 0);
        panel.tree().setSelectionPath(panel.treePath(gate.id().value()).orElseThrow());
        assertThat(canvas.isTravelling()).isTrue();
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(origin);
        ticker.finish();
        Point2D middle = center(ElementBounds.of(gate, openDocument.registry()));
        assertThat(canvas.viewport().gridToScreen(middle.getX(), middle.getY()))
                .isEqualTo(new Point2D.Double(200, 150));
    }

    @Test
    void choosingANestedRowSelectsAndCentersItsTopLevelInstance() {
        Component instance = inverterInstance();
        panel.tree().setSelectionPath(
                panel.treePath(InstancePath.child(instance.id().value(), "c2")).orElseThrow());
        assertThat(canvas.selection().ids()).containsExactly(instance.id());
        Point2D middle = center(ElementBounds.of(instance, openDocument.registry()));
        assertThat(canvas.viewport().gridToScreen(middle.getX(), middle.getY()))
                .isEqualTo(new Point2D.Double(200, 150));
        panel.tree().setSelectionPath(panel.treePath("/").orElseThrow());
        assertThat(canvas.selection().ids()).containsExactly(instance.id());
    }

    @Test
    void aRebuildKeepsExpandedAndChosenRowsWithoutMovingTheView() {
        Component instance = inverterInstance();
        Component gate = place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of());
        panel.tree().expandPath(panel.treePath(instance.id().value()).orElseThrow());
        panel.tree().setSelectionPath(panel.treePath(gate.id().value()).orElseThrow());
        canvas.viewport().panBy(30, 0);
        canvas.selection().clear();
        Point2D origin = canvas.viewport().gridToScreen(0, 0);

        place(openDocument, StandardComponentTypes.LED, 0, 20, Map.of());

        assertThat(panel.tree().isExpanded(panel.treePath(instance.id().value()).orElseThrow())).isTrue();
        assertThat(panel.tree().getSelectionPath()).isEqualTo(panel.treePath(gate.id().value()).orElseThrow());
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(origin);
        assertThat(canvas.selection().isEmpty()).isTrue();
    }

    @Test
    void nestedPinValuesFollowTheSnapshotAndRepaintOnlyWhenTheyChange() {
        Component instance = inverterInstance();
        String input = InstancePath.child(instance.id().value(), "c1");
        String output = InstancePath.child(instance.id().value(), "c3");
        List<TreeModelEvent> changes = new ArrayList<>();
        panel.tree().getModel().addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent event) {
                changes.add(event);
            }

            @Override
            public void treeNodesInserted(TreeModelEvent event) {
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent event) {
            }

            @Override
            public void treeStructureChanged(TreeModelEvent event) {
            }
        });

        panel.showSnapshot(snapshotWith(Map.of(input, new VisibleState.LedState(Value.ONE),
                output, new VisibleState.LedState(Value.ZERO))));
        assertThat(panel.rowText(input)).contains("a · Pin de entrada = 1");
        assertThat(panel.rowText(output)).contains("y · Pin de salida = 0");
        assertThat(changes).hasSize(2);

        panel.showSnapshot(snapshotWith(Map.of(input, new VisibleState.LedState(Value.ONE),
                output, new VisibleState.LedState(Value.ZERO),
                instance.id().value(), new VisibleState.LedState(Value.ONE))));
        assertThat(changes).hasSize(2);

        panel.showSnapshot(snapshotWith(Map.of(input, new VisibleState.LedState(Value.ZERO),
                output, new VisibleState.LedState(Value.ONE))));
        assertThat(panel.rowText(output)).contains("y · Pin de salida = 1");
        assertThat(changes).hasSize(4);
    }

    @Test
    void aStaleSnapshotIsIgnoredAndRemovedRowsAreNeverRefreshed() {
        Component instance = inverterInstance();
        String input = InstancePath.child(instance.id().value(), "c1");
        panel.showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken() - 1, 0, true, Map.of(),
                Map.of(input, new VisibleState.LedState(Value.ONE)), List.of(), Optional.empty()));
        assertThat(panel.rowText(input)).contains("a · Pin de entrada");

        openDocument.history().undo(); // the instance goes away
        panel.showSnapshot(snapshotWith(Map.of(input, new VisibleState.LedState(Value.ONE))));
        assertThat(panel.rowText(input)).isEmpty();
        assertThat(panel.rowText("/")).contains("Sin título");
    }

    @Test
    void anEditDropsTheValuesOfTheSnapshotFromTheRevisionBefore() {
        Component instance = inverterInstance();
        String input = InstancePath.child(instance.id().value(), "c1");
        panel.showSnapshot(snapshotWith(Map.of(input, new VisibleState.LedState(Value.ONE))));
        assertThat(panel.rowText(input)).contains("a · Pin de entrada = 1");

        place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of()); // bumps the token and rebuilds
        assertThat(panel.rowText(input)).contains("a · Pin de entrada");
    }

    @Test
    void aDisposedPanelStopsFollowingTheDocument() {
        panel.dispose();
        Component gate = place(openDocument, StandardComponentTypes.NOT, 20, 10, Map.of());
        assertThat(panel.rowText(gate.id().value())).isEmpty();
    }
    private Frame addFrame(int x, int y, int width, int height, String title) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(x, y), width, height, title,
                FrameTint.NONE, false);
        openDocument.execute(new AddElementsCommand(List.of(frame)));
        return frame;
    }

    private Icon renderedIcon(String nodePath) {
        Object swingNode = panel.treePath(nodePath).orElseThrow().getLastPathComponent();
        JLabel label = (JLabel) panel.tree().getCellRenderer().getTreeCellRendererComponent(panel.tree(), swingNode,
                false, false, true, 0, false);
        return label.getIcon();
    }

    @Test
    void movingAComponentIntoAFrameMovesItsRowAndKeepsTheExpandedRows() {
        Frame block = addFrame(0, 0, 30, 20, "ALU");
        Component instance = inverterInstance(); // at (4, -1): straddles the top border
        Component gate = place(openDocument, StandardComponentTypes.NOT, 40, 4, Map.of());
        String framePath = ComponentTreeNode.framePath(block.id());
        panel.tree().expandPath(panel.treePath(instance.id().value()).orElseThrow());
        assertThat(panel.treePath(gate.id().value()).orElseThrow().getPathCount()).isEqualTo(2);

        openDocument.execute(new MoveElementsCommand(Set.of(gate.id()), -30, 0));

        assertThat(panel.treePath(gate.id().value()).orElseThrow().getParentPath())
                .isEqualTo(panel.treePath(framePath).orElseThrow());
        assertThat(panel.tree().isExpanded(panel.treePath(instance.id().value()).orElseThrow())).isTrue();

        openDocument.execute(new MoveElementsCommand(Set.of(instance.id()), 0, 4));
        assertThat(panel.treePath(instance.id().value()).orElseThrow().getParentPath())
                .isEqualTo(panel.treePath(framePath).orElseThrow());
        assertThat(panel.tree().isExpanded(panel.treePath(instance.id().value()).orElseThrow())).isTrue();

        openDocument.history().undo();
        openDocument.history().undo();
        assertThat(panel.treePath(gate.id().value()).orElseThrow().getPathCount()).isEqualTo(2);
    }

    @Test
    void choosingAFrameRowSelectsTheFrameAndCentersOnIt() {
        Frame block = addFrame(10, 6, 30, 20, "");
        assertThat(panel.rowText(ComponentTreeNode.framePath(block.id()))).contains("Marco");

        panel.tree().setSelectionPath(panel.treePath(ComponentTreeNode.framePath(block.id())).orElseThrow());

        assertThat(canvas.selection().ids()).containsExactly(block.id());
        Point2D middle = center(ElementBounds.of(block, openDocument.registry()));
        assertThat(canvas.viewport().gridToScreen(middle.getX(), middle.getY()))
                .isEqualTo(new Point2D.Double(200, 150));
    }

    @Test
    void componentFrameAndGroupRowsShowAnIcon() {
        Frame block = addFrame(0, 0, 30, 20, "ALU");
        Component gate = place(openDocument, StandardComponentTypes.NOT, 4, 4, Map.of());
        assertThat(renderedIcon(gate.id().value())).isInstanceOf(ComponentIcon.class);
        assertThat(renderedIcon(ComponentTreeNode.framePath(block.id()))).isNotNull();

        panel.groupByTypeToggle().setSelected(true);
        assertThat(renderedIcon(ComponentTreeNode.groupPath(ComponentTreeNode.framePath(block.id()),
                StandardComponentTypes.NOT))).isInstanceOf(ComponentIcon.class);
        assertThat(renderedIcon(ComponentTreeNode.DOCUMENT_PATH)).isNull();
    }

    @Test
    void aSchemeChangeRedrawsTheIconsWithTheNewColorsAndDisposeStopsListening() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 4, 4, Map.of());
        assertThat(((ComponentIcon) renderedIcon(gate.id().value())).scheme()).isEqualTo(ColorScheme.LIGHT);
        int listeners = appearance.listenerCount();

        ColorScheme dark = ColorSchemes.forTheme(true);
        appearance.setScheme(dark);
        assertThat(((ComponentIcon) renderedIcon(gate.id().value())).scheme()).isEqualTo(dark);

        panel.dispose();
        assertThat(appearance.listenerCount()).isEqualTo(listeners - 1);
    }
}
