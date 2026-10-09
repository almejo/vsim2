package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class WireToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final WireTool tool = new WireTool();

    @AfterEach
    void close() {
        context.close();
    }

    private List<Wire> wires() {
        return context.openDocument().document().wires();
    }

    @Test
    void dragAddsAnLShapedWireAsOneUndoStep() {
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(3, 1));
        tool.mouseDragged(context, TestToolContext.at(6, 4));
        tool.mouseReleased(context, TestToolContext.at(6, 4));
        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(GridPoint.ORIGIN, new GridPoint(6, 0)),
                tuple(new GridPoint(6, 0), new GridPoint(6, 4)));
        assertThat(context.openDocument().history().undoDescription()).contains("command.wire");
        context.openDocument().history().undo();
        assertThat(wires()).isEmpty();
    }

    @Test
    void firstMovementMostlyVerticalRoutesVerticallyFirst() {
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(0.4, 2));
        tool.mouseReleased(context, TestToolContext.at(6, 4));
        assertThat(wires()).extracting(Wire::start).contains(new GridPoint(0, 4));
    }

    @Test
    void releasingWhereItStartedAddsNothing() {
        tool.mousePressed(context, TestToolContext.at(2, 2));
        tool.mouseReleased(context, TestToolContext.at(2.2, 1.9));
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void previewShowsTheRouteWhileDragging() {
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(4, 0));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).singleElement().isInstanceOfSatisfying(Wire.class,
                wire -> assertThat(wire.end()).isEqualTo(new GridPoint(4, 0)));
    }

    @Test
    void escapeCancelsTheWireThenReturnsToTheDefaultTool() {
        context.tool = tool;
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(4, 0));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(4, 0));
        assertThat(wires()).isEmpty();
        assertThat(context.tool).isSameAs(tool);
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.tool).isSameAs(context.defaultTool);
    }
}
