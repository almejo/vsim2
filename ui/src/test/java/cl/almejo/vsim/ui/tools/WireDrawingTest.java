package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;

class WireDrawingTest {

    private final TestToolContext context = TestToolContext.withNewDocument();

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void movementIsDetectedAfterHalfAGridUnit() {
        WireDrawing drawing = new WireDrawing(new GridPoint(2, 2), new Point2D.Double(2, 2));
        drawing.dragTo(TestToolContext.at(2.3, 2.2));
        assertThat(drawing.hasMoved()).isFalse();
        drawing.dragTo(TestToolContext.at(2.6, 2.1));
        assertThat(drawing.hasMoved()).isTrue();
    }

    @Test
    void commitAddsTheRouteAsOneUndoStep() {
        WireDrawing drawing = new WireDrawing(GridPoint.ORIGIN, new Point2D.Double(0, 0));
        drawing.dragTo(TestToolContext.at(3, 0.2));
        assertThat(drawing.commit(context, new GridPoint(5, 3))).isTrue();
        assertThat(context.openDocument().document().wires()).hasSize(2);
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().wires()).isEmpty();
    }

    @Test
    void commitWithoutLengthAddsNothing() {
        WireDrawing drawing = new WireDrawing(GridPoint.ORIGIN, new Point2D.Double(0, 0));
        assertThat(drawing.commit(context, GridPoint.ORIGIN)).isFalse();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void previewPaintsOneGhostPerSegment() {
        WireDrawing drawing = new WireDrawing(GridPoint.ORIGIN, new Point2D.Double(0, 0));
        drawing.dragTo(TestToolContext.at(4, 3));
        drawing.paintPreview(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).hasSize(2).allMatch(Wire.class::isInstance);
    }
}
