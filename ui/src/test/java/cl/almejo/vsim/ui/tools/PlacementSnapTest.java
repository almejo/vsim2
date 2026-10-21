package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ajustar for what is not in the circuit yet (spec §3.4): the component being placed and a floating paste. The
 * anchor is a 4×4 NOT gate at the origin (lines 0, 2, 4 on both axes); at 100 % the magnet reaches one unit.
 */
class PlacementSnapTest {

    private final TestToolContext context = TestToolContext.withNewDocument();

    @BeforeEach
    void create() {
        context.snapper = new DefaultSnapper(context.preferences, Dimension::new);
        place(context.openDocument(), StandardComponentTypes.NOT, 0, 0, Map.of());
    }

    @AfterEach
    void close() {
        context.close();
    }

    private PlaceComponentTool placing() {
        return new PlaceComponentTool(context.openDocument().registry().require(StandardComponentTypes.NOT));
    }

    private List<GridPoint> positions() {
        return context.openDocument().document().components().stream().map(Component::position).toList();
    }

    @Test
    void theGhostSnapsAndThePressPlacesItWhereTheGhostIs() {
        PlaceComponentTool tool = placing();
        tool.mouseMoved(context, TestToolContext.at(20, 1));
        assertThat(tool.hover()).isEqualTo(new GridPoint(20, 0));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        tool.mousePressed(context, TestToolContext.at(20, 1));
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(20, 0));
    }

    @Test
    void aPressRightAfterARefreshPlacesWhatTheRefreshShows() {
        PlaceComponentTool tool = placing();
        tool.mouseMoved(context, TestToolContext.at(20, 1));
        context.preferences.setSnapToAlignments(false);
        assertThat(tool.refreshSnap(context)).isTrue();
        assertThat(tool.hover()).isEqualTo(new GridPoint(20, 1));
        tool.mousePressed(context, TestToolContext.at(20, 1));
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(20, 1));
    }

    @Test
    void rotatingTheGhostSnapsItAgain() {
        PlaceComponentTool tool = placing();
        ComponentTypeRegistry registry = context.openDocument().registry();
        tool.mouseMoved(context, TestToolContext.at(9, 20));
        assertThat(tool.hover()).isEqualTo(new GridPoint(9, 20)); // its left edge at 9: five units off
        tool.keyPressed(context, KeyEvent.VK_R, false);
        Component rotated = registry.require(StandardComponentTypes.NOT).newComponent(new ElementId("probe"),
                new GridPoint(9, 20), tool.rotation(), Map.of(), "");
        Bounds rotatedBounds = ElementBounds.of(rotated, registry);
        // whatever way the rotation turns the box, the ghost now lands with a vertical line on one of 0, 2, 4
        GridPoint hover = tool.hover();
        Bounds landed = rotatedBounds.translate(hover.x() - 9, hover.y() - 20);
        assertThat(List.of(landed.x(), landed.x() + landed.width() / 2, landed.maximumX()))
                .containsAnyOf(0, 2, 4);
        tool.mousePressed(context, TestToolContext.at(9, 20));
        assertThat(positions()).contains(hover);
    }

    @Test
    void withAjustarOffTheGhostFollowsThePointer() {
        context.preferences.setSnapToAlignments(false);
        PlaceComponentTool tool = placing();
        tool.mouseMoved(context, TestToolContext.at(20, 1));
        tool.mousePressed(context, TestToolContext.at(20, 1));
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(20, 1));
    }

    private CircuitFragment oneGate() {
        Component gate = context.openDocument().registry().require(StandardComponentTypes.NOT)
                .newComponent(new ElementId("c9"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        return new CircuitFragment(List.of(gate), List.of(), List.of());
    }

    @Test
    void aFloatingPasteSnapsWhenItStartsAndWhileItFollowsAndEnterCommitsWhatIsShown() {
        FloatingSelectionTool tool = new FloatingSelectionTool(oneGate(), new GridPoint(20, 1));
        tool.activated(context);
        assertThat(tool.position()).isEqualTo(new GridPoint(20, 0));
        tool.mouseMoved(context, TestToolContext.at(21, 5));
        assertThat(tool.position()).isEqualTo(new GridPoint(21, 4));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        tool.keyPressed(context, KeyEvent.VK_ENTER, false);
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(21, 4));
    }

    @Test
    void aDraggedFloatingPasteSnapsAndARefreshBeforeEnterIsCommitted() {
        FloatingSelectionTool tool = new FloatingSelectionTool(oneGate(), new GridPoint(30, 30));
        tool.activated(context);
        tool.mousePressed(context, TestToolContext.at(31, 31));
        tool.mouseDragged(context, TestToolContext.at(22, 2)); // the fragment's origin at (21, 1)
        tool.mouseReleased(context, TestToolContext.at(22, 2));
        assertThat(tool.position()).isEqualTo(new GridPoint(21, 0));
        context.preferences.setSnapToAlignments(false);
        assertThat(tool.refreshSnap(context)).isTrue();
        tool.keyPressed(context, KeyEvent.VK_ENTER, false);
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(21, 1));
    }

    @Test
    void aPasteWithItsOwnDefinitionAlignsWithItsOwnBounds() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(inverter.id()),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(instance), List.of(), List.of(), List.of(inverter));
        Bounds own = ElementBounds.of(instance, fragment.registryFor(context.openDocument().registry()));
        // its bottom one unit above the anchor's top (0): the magnet puts the bottom on 0
        FloatingSelectionTool tool = new FloatingSelectionTool(fragment, new GridPoint(30, -1 - own.maximumY()));
        tool.activated(context);
        assertThat(tool.position()).isEqualTo(new GridPoint(30, -own.maximumY()));
    }
}
