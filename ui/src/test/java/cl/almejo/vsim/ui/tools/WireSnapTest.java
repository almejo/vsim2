package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ajustar for wire points (alignment-guides spec §4.4): a free start or end goes to a guide within reach, a pin or a
 * wire never moves; the preview and the commit share one resolution; the tools say which guides they lie on.
 */
class WireSnapTest {

    private static final Guide ROW_21 = new Guide(Orientation.HORIZONTAL, 21);

    private final TestToolContext context = TestToolContext.withNewDocument();
    private Component gate;
    private GridPoint pin;

    @BeforeEach
    void create() {
        context.snapper = new DefaultSnapper(context.preferences, Dimension::new);
        gate = place(context.openDocument(), StandardComponentTypes.NOT, 0, 0, Map.of());
        pin = context.openDocument().registry().typeOf(gate).pinLocation(gate, 0);
        context.openDocument().execute(new AddWireCommand(new Wire(context.openDocument().document().newWireId(),
                new GridPoint(40, 0), new GridPoint(50, 0))));
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void guides(Guide... guides) {
        context.openDocument().document().setGuides(List.of(guides));
    }

    /** The end points of every wire drawn after the one set up. */
    private Set<GridPoint> drawnEnds() {
        return context.openDocument().document().wires().stream()
                .filter(wire -> !wire.start().equals(new GridPoint(40, 0)))
                .flatMap(wire -> java.util.stream.Stream.of(wire.start(), wire.end()))
                .collect(Collectors.toSet());
    }

    @Test
    void aFreeStartAndAFreeEndGoToTheGuidesWithinReach() {
        guides(new Guide(Orientation.VERTICAL, 11), ROW_21);
        WireTool tool = new WireTool();
        tool.mousePressed(context, TestToolContext.at(10, 10));
        tool.mouseDragged(context, TestToolContext.at(20, 20));
        assertThat(tool.snappedGuides()).containsExactlyInAnyOrder(new Guide(Orientation.VERTICAL, 11), ROW_21);
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        tool.mouseReleased(context, TestToolContext.at(20, 20));

        assertThat(drawnEnds()).contains(new GridPoint(11, 10), new GridPoint(20, 21));
        assertThat(tool.snappedGuides()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 1.0})
    void aWireStartingOnAPinAndEndingOnAWireStaysOnThem(double zoom) {
        context.viewport.zoomAt(0, 0, zoom);
        guides(new Guide(Orientation.VERTICAL, pin.x() + 1), new Guide(Orientation.HORIZONTAL, pin.y() - 1),
                new Guide(Orientation.VERTICAL, 46), new Guide(Orientation.HORIZONTAL, 1));
        WireTool tool = new WireTool();
        tool.mousePressed(context, TestToolContext.at(pin.x(), pin.y()));
        tool.mouseDragged(context, TestToolContext.at(45, 0));
        tool.mouseReleased(context, TestToolContext.at(45, 0));

        assertThat(drawnEnds()).contains(pin, new GridPoint(45, 0));
    }

    @Test
    void aReleaseRightAfterARefreshCommitsTheRefreshedEnd() {
        WireTool tool = new WireTool();
        tool.mousePressed(context, TestToolContext.at(10, 10));
        tool.mouseDragged(context, TestToolContext.at(20, 20));
        guides(ROW_21);
        assertThat(tool.refreshSnap(context)).isTrue();
        assertThat(tool.refreshSnap(context)).isFalse(); // nothing changed the second time
        tool.mouseReleased(context, TestToolContext.at(20, 20));

        assertThat(drawnEnds()).contains(new GridPoint(10, 10), new GridPoint(20, 21));
    }

    @Test
    void withAjustarOffTheWireGoesWhereThePointerSays() {
        context.preferences.setSnapToAlignments(false);
        guides(new Guide(Orientation.VERTICAL, 11), ROW_21);
        WireTool tool = new WireTool();
        context.drag(tool, 10, 10, 20, 20);
        assertThat(drawnEnds()).contains(new GridPoint(10, 10), new GridPoint(20, 20));
        assertThat(drawnEnds()).doesNotContain(new GridPoint(11, 10), new GridPoint(20, 21));
    }

    @Test
    void aWireDrawnFromAPinWithTheSelectToolEndsOnTheGuideAndRefreshes() {
        SelectTool tool = new SelectTool();
        tool.mousePressed(context, TestToolContext.at(pin.x(), pin.y()));
        tool.mouseDragged(context, TestToolContext.at(20, 20));
        assertThat(tool.snappedGuides()).isEmpty();
        guides(ROW_21);
        assertThat(tool.refreshSnap(context)).isTrue();
        assertThat(tool.snappedGuides()).containsExactly(ROW_21);
        tool.mouseReleased(context, TestToolContext.at(20, 20));

        assertThat(drawnEnds()).contains(pin, new GridPoint(20, 21));
    }

    @Test
    void aBoxMoveNamesTheGuideItLandsOn() {
        guides(new Guide(Orientation.VERTICAL, 31));
        SelectTool tool = new SelectTool();
        tool.mousePressed(context, TestToolContext.at(2, 2)); // the gate's middle
        tool.mouseDragged(context, TestToolContext.at(31.2, 22)); // its centre lands on 31
        assertThat(tool.snappedGuides()).contains(new Guide(Orientation.VERTICAL, 31));
        tool.mouseReleased(context, TestToolContext.at(31.2, 22));
        assertThat(tool.snappedGuides()).isEmpty();
    }

    @Test
    void aPlacementGhostAndAFloatingPasteNameTheGuideTheyLandOn() {
        guides(ROW_21);
        PlaceComponentTool placing = new PlaceComponentTool(
                context.openDocument().registry().require(StandardComponentTypes.NOT));
        placing.mouseMoved(context, TestToolContext.at(20, 20)); // its top at 20: one unit off the guide
        assertThat(placing.hover().y()).isEqualTo(21);
        assertThat(placing.snappedGuides()).containsExactly(ROW_21);

        Component copied = context.openDocument().registry().require(StandardComponentTypes.NOT)
                .newComponent(new ElementId("c9"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        FloatingSelectionTool floating = new FloatingSelectionTool(
                new CircuitFragment(List.of(copied), List.of(), List.of()), new GridPoint(30, 20));
        floating.activated(context);
        assertThat(floating.position().y()).isEqualTo(21);
        assertThat(floating.snappedGuides()).containsExactly(ROW_21);
    }
}
