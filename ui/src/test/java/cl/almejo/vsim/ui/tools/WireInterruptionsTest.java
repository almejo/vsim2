package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What ends a continuous wire's path besides the user's own keys (a tool change, an interruption), what keeps it (the
 * focus going away), and the dot the idle tool shows over a wire where a click would join it.
 */
class WireInterruptionsTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final AtomicReference<WireMode> mode = new AtomicReference<>(WireMode.CONTINUOUS);
    private final WireTool tool = new WireTool(mode::get);

    {
        context.tool = tool;
    }

    @AfterEach
    void close() {
        context.close();
    }

    private void click(double gridX, double gridY) {
        tool.mousePressed(context, TestToolContext.at(gridX, gridY));
        tool.mouseReleased(context, TestToolContext.at(gridX, gridY));
    }

    private void startAPathWithOneCorner() {
        click(0, 0);
        click(4, 2);
        tool.mouseMoved(context, TestToolContext.at(8, 6));
    }

    @Test
    void deactivatingTheToolWithAPathCancelsItWithTheNotice() {
        startAPathWithOneCorner();

        tool.deactivated(context);

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(context.notices).containsExactly(Messages.text("wire.cancelled"));
        assertThat(context.openDocument().document().wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void deactivatingTheToolWithoutAPathSaysNothing() {
        tool.deactivated(context);

        assertThat(context.notices).isEmpty();
    }

    @Test
    void anInterruptionCancelsThePathAndLeavesTheNoticeToTheCaller() {
        startAPathWithOneCorner();

        assertThat(tool.interrupt(context)).contains(Messages.text("wire.cancelled"));

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(context.notices).isEmpty();
        assertThat(context.openDocument().document().wires()).isEmpty();
        assertThat(tool.interrupt(context)).isEmpty(); // nothing left to cancel
    }

    @Test
    void anInterruptionLeavesADragInPiecesAsItIs() {
        mode.set(WireMode.PIECES);
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(4, 2));

        assertThat(tool.interrupt(context)).isEmpty();
        assertThat(tool.drawsBetweenClicks()).isFalse();
    }

    @Test
    void losingTheFocusKeepsThePath() {
        startAPathWithOneCorner();

        tool.focusLost(context);

        assertThat(tool.holdsGesture()).isTrue();
        assertThat(tool.drawsBetweenClicks()).isTrue();
        click(8, 6);
        tool.mousePressed(context, TestToolContext.event(8, 6, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(8, 6, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.openDocument().document().wires()).isNotEmpty();
    }

    @Test
    void itDrawsBetweenClicksOnlyWhileAPathIsInProgress() {
        assertThat(tool.drawsBetweenClicks()).isFalse();
        click(0, 0);
        assertThat(tool.drawsBetweenClicks()).isTrue();
        tool.dropLastStep(context);
        assertThat(tool.drawsBetweenClicks()).isFalse();
    }

    @Test
    void hoveringAWireWithNoPathShowsTheJunctionDotWhereAClickWouldJoin() {
        addWire(context.openDocument(), 0, 5, 10, 5);

        tool.mouseMoved(context, TestToolContext.at(4, 5));
        assertThat(tool.hoverJunction()).contains(new GridPoint(4, 5));

        tool.mouseMoved(context, TestToolContext.at(4, 8)); // empty space
        assertThat(tool.hoverJunction()).isEmpty();

        tool.mouseMoved(context, TestToolContext.at(10, 5)); // the wire's end
        assertThat(tool.hoverJunction()).contains(new GridPoint(10, 5));
        tool.mouseExited(context);
        assertThat(tool.hoverJunction()).isEmpty();
    }

    @Test
    void theHoverDotGoesOnceAPathStartsAndIsNotShownOverAPin() {
        addWire(context.openDocument(), 0, 5, 10, 5);
        Component buffer = place(context.openDocument(), StandardComponentTypes.BUFFER, 20, 20,
                Map.of(StandardComponentTypes.NEGATE, true));
        GridPoint pin = context.openDocument().registry().typeOf(buffer).pinLocation(buffer, 0);

        tool.mouseMoved(context, TestToolContext.at(pin.x(), pin.y()));
        assertThat(tool.hoverJunction()).isEmpty(); // a pin shows itself

        tool.mouseMoved(context, TestToolContext.at(4, 5));
        click(4, 5);
        assertThat(tool.hoverJunction()).isEmpty(); // the path's own preview shows its dots
    }

    @Test
    void piecesShowsNoHoverDot() {
        mode.set(WireMode.PIECES);
        addWire(context.openDocument(), 0, 5, 10, 5);

        tool.mouseMoved(context, TestToolContext.at(4, 5));

        assertThat(tool.hoverJunction()).isEmpty();
    }
}
