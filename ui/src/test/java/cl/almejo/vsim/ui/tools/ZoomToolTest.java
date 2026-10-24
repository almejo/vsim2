package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.canvas.ZoomTargets;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The zoom tool (zoom spec §4): a rectangle fits the pane, a click steps in, Alt/Shift/right click steps out. */
class ZoomToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final ZoomTool tool = new ZoomTool();

    @BeforeEach
    void useTheTool() {
        Messages.initialize(Messages.SPANISH);
        context.tool = tool;
    }

    @AfterEach
    void close() {
        context.close();
    }

    /** An event at screen pixel ({@code x}, {@code y}) of the pane, as the canvas builds it. */
    private CanvasMouseEvent screen(double x, double y, int button, boolean shiftDown, boolean altDown) {
        Point2D grid = context.viewport.screenToGrid(x, y);
        return new CanvasMouseEvent(grid, context.viewport.snap(x, y), new Point2D.Double(x, y), button, shiftDown,
                false, altDown, 1);
    }

    private CanvasMouseEvent screen(double x, double y) {
        return screen(x, y, CanvasMouseEvent.PRIMARY, false, false);
    }

    private void click(double x, double y, int button, boolean shiftDown, boolean altDown) {
        tool.mousePressed(context, screen(x, y, button, shiftDown, altDown));
        tool.mouseReleased(context, screen(x, y, button, shiftDown, altDown));
    }

    @Test
    void aDraggedRectangleIsWhatTheViewTravelsToExactly() {
        context.viewport.showAt(1.0, 50, 37.5, 800, 600);
        Viewport before = new Viewport();
        before.showAt(1.0, 50, 37.5, 800, 600);
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(300, 200));
        tool.mouseDragged(context, screen(500, 400));
        tool.mouseReleased(context, screen(500, 400));

        ViewState expected = ZoomTargets.region(new Rectangle2D.Double(100, 100, 400, 300), before, 800, 600);
        assertThat(context.zoomTargets).containsExactly(expected);
        assertThat(context.lastZoomTarget().zoom()).isEqualTo(2.0);
        Point2D rectangleCentre = before.screenToGrid(300, 250);
        assertThat(context.lastZoomTarget().centerX()).isCloseTo(rectangleCentre.getX(), within(1e-9));
        assertThat(context.lastZoomTarget().centerY()).isCloseTo(rectangleCentre.getY(), within(1e-9));
        assertThat(context.tool).as("the tool stays after a zoom").isSameAs(tool);
    }

    @Test
    void aRectangleDraggedUpAndLeftCountsTheSame() {
        tool.mousePressed(context, screen(500, 400));
        tool.mouseDragged(context, screen(100, 100));
        tool.mouseReleased(context, screen(100, 100));
        assertThat(context.lastZoomTarget().zoom()).isEqualTo(2.0);
        assertThat(context.lastZoomTarget().centerX()).isCloseTo(300 / 8.0, within(1e-9));
        assertThat(context.lastZoomTarget().centerY()).isCloseTo(250 / 8.0, within(1e-9));
    }

    @Test
    void theRectangleIsClampedToThePaneWhenThePointerLeavesIt() {
        tool.mousePressed(context, screen(700, 500));
        tool.mouseDragged(context, screen(1000, 900));
        tool.mouseReleased(context, screen(1000, 900));
        ViewState target = context.lastZoomTarget();
        assertThat(target.zoom()).as("a 100 × 100 rectangle lands at the limit").isEqualTo(Viewport.MAXIMUM_ZOOM);
        assertThat(target.centerX()).isCloseTo(750 / 8.0, within(1e-9));
        assertThat(target.centerY()).isCloseTo(550 / 8.0, within(1e-9));
    }

    @Test
    void aClickStepsInAroundThePointClickedAndTheViewMovesAtOnce() {
        Point2D underTheClick = context.viewport.screenToGrid(200, 150);
        tool.mousePressed(context, screen(200, 150));
        tool.mouseDragged(context, screen(203, 152)); // under 5 px: still a click
        tool.mouseReleased(context, screen(203, 152));

        assertThat(context.zoomTargets).as("a step does not travel").isEmpty();
        assertThat(context.viewport.zoom()).isCloseTo(Viewport.ZOOM_STEP, within(1e-9));
        Point2D after = context.viewport.screenToGrid(200, 150);
        assertThat(after.getX()).isCloseTo(underTheClick.getX(), within(1e-9));
        assertThat(after.getY()).isCloseTo(underTheClick.getY(), within(1e-9));
    }

    @Test
    void altClickShiftClickAndARightClickStepOut() {
        click(200, 150, CanvasMouseEvent.PRIMARY, false, true);
        assertThat(context.viewport.zoom()).isCloseTo(1 / Viewport.ZOOM_STEP, within(1e-9));
        click(200, 150, CanvasMouseEvent.PRIMARY, true, false);
        assertThat(context.viewport.zoom()).isCloseTo(1 / Math.pow(Viewport.ZOOM_STEP, 2), within(1e-9));
        Point2D underTheClick = context.viewport.screenToGrid(400, 300);
        click(400, 300, CanvasMouseEvent.SECONDARY, false, false);
        assertThat(context.viewport.zoom()).isCloseTo(1 / Math.pow(Viewport.ZOOM_STEP, 3), within(1e-9));
        assertThat(context.viewport.screenToGrid(400, 300).getX()).isCloseTo(underTheClick.getX(), within(1e-9));
        assertThat(context.zoomTargets).isEmpty();
        assertThat(context.contextMenuTarget).as("no context menu in this tool").isNull();
    }

    @Test
    void aDragWithAltOrShiftHeldStillZoomsIntoTheRectangle() {
        tool.mousePressed(context, screen(100, 100, CanvasMouseEvent.PRIMARY, true, true));
        tool.mouseDragged(context, screen(500, 400, CanvasMouseEvent.PRIMARY, true, true));
        tool.mouseReleased(context, screen(500, 400, CanvasMouseEvent.PRIMARY, true, true));
        assertThat(context.lastZoomTarget().zoom()).isEqualTo(2.0);
    }

    @Test
    void atTheLimitsAClickMovesNothing() {
        context.viewport.showAt(Viewport.MAXIMUM_ZOOM, 10, 10, 800, 600);
        Point2D origin = context.viewport.gridToScreen(0, 0);
        click(100, 100, CanvasMouseEvent.PRIMARY, false, false);
        assertThat(context.viewport.zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
        assertThat(context.viewport.gridToScreen(0, 0)).isEqualTo(origin);
    }

    @Test
    void escapeWhileDraggingDropsTheRectangleAndKeepsTheTool() {
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(500, 400));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.tool).isSameAs(tool);
        assertThat(tool.band()).isNull();

        tool.mouseDragged(context, screen(600, 450));
        tool.mouseReleased(context, screen(600, 450));
        assertThat(context.zoomTargets).as("the release after Esc zooms nothing").isEmpty();
        assertThat(context.viewport.zoom()).isEqualTo(1.0);
    }

    @Test
    void escapeWhileIdleGoesBackToTheDefaultTool() {
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void otherKeysAreLeftToTheCanvas() {
        assertThat(tool.keyPressed(context, KeyEvent.VK_A, false)).isFalse();
        assertThat(context.tool).isSameAs(tool);
    }

    @Test
    void whileDraggingTheBandShowsTheZoomTheReleaseWouldGive() {
        tool.mousePressed(context, screen(100, 100));
        assertThat(tool.band()).as("no band before the pointer moves 5 px").isNull();
        assertThat(tool.targetLabel(context)).isEmpty();
        tool.mouseDragged(context, screen(500, 400));
        assertThat(tool.band()).isEqualTo(new Rectangle2D.Double(100, 100, 400, 300));
        assertThat(tool.targetLabel(context)).contains("200 %");
        tool.mouseDragged(context, screen(120, 110));
        assertThat(tool.targetLabel(context)).as("clamped to the limit").contains("400 %");
        tool.mouseReleased(context, screen(120, 110));
        assertThat(tool.band()).isNull();
        assertThat(tool.targetLabel(context)).isEmpty();
    }

    @Test
    void theOverlayPaintsTheBandAndItsLabelOnlyWhileDragging() {
        assertThat(paintedPixels()).as("idle: nothing").isZero();
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(300, 250));
        BufferedImage image = paint();
        assertThat(alpha(image, 200, 175)).as("the band's fill").isPositive();
        assertThat(alpha(image, 50, 50)).as("outside the band").isZero();
        assertThat(painted(image, 302, 252, 120, 30)).as("the label just outside the bottom-right corner").isPositive();
        assertThat(tool.animatesOutline()).as("the marching ants move").isTrue();
        tool.mouseReleased(context, screen(300, 250));
        assertThat(paintedPixels()).as("after the release: nothing").isZero();
        assertThat(tool.animatesOutline()).isFalse();
    }

    @Test
    void theBandIsPaintedInScreenPixelsAtAnyZoom() {
        context.viewport.showAt(2.0, 40, 30, 800, 600);
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(300, 250));
        BufferedImage image = paint();
        assertThat(alpha(image, 200, 175)).isPositive();
        assertThat(alpha(image, 320, 175)).as("not scaled by the zoom").isZero();
        assertThat(alpha(image, 98, 175)).isZero();
    }

    @Test
    void theBandReadsTheSchemeAtPaintTime() {
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(300, 250));
        int light = paint().getRGB(200, 175);
        context.scheme = ColorSchemes.DARK;
        int dark = paint().getRGB(200, 175);
        assertThat(dark).isNotEqualTo(light);
    }

    @Test
    void theLabelStaysInsideThePaneWhenTheBandReachesItsCorner() {
        Dimension pane = new Dimension(800, 600);
        Rectangle2D label = ZoomTool.labelBounds(new Rectangle2D.Double(600, 450, 200, 150), 60, 20, pane);
        assertThat(new Rectangle2D.Double(0, 0, 800, 600).contains(label)).isTrue();
        Rectangle2D free = ZoomTool.labelBounds(new Rectangle2D.Double(100, 100, 200, 150), 60, 20, pane);
        assertThat(free.getMinX()).isGreaterThan(300);
        assertThat(free.getMinY()).isGreaterThan(250);
    }

    @Test
    void theCursorIsTheMagnifierAndSwitchesToMinusWithAltOrShift() {
        assertThat(tool.cursor()).isNotNull();
        assertThat(tool.modifiersChanged(context, false, false, true)).isFalse();
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.ZOOM_OUT);
        tool.modifiersChanged(context, false, false, false);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.ZOOM_IN);
        tool.modifiersChanged(context, true, false, false);
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.ZOOM_OUT);
        tool.modifiersChanged(context, false, true, false);
        assertThat(context.lastCursor()).as("Ctrl means nothing here").isEqualTo(CanvasCursor.ZOOM_IN);
        tool.mouseMoved(context, screen(10, 10, CanvasMouseEvent.PRIMARY, false, true));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.ZOOM_OUT);
    }

    @Test
    void whileDraggingTheCursorIsTheCrosshairAndTheMagnifierComesBackAfter() {
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(500, 400));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.CROSSHAIR);
        tool.modifiersChanged(context, false, false, true);
        assertThat(context.lastCursor()).as("modifiers are ignored during a drag").isEqualTo(CanvasCursor.CROSSHAIR);
        tool.mouseReleased(context, screen(500, 400, CanvasMouseEvent.PRIMARY, false, true));
        assertThat(context.lastCursor()).isEqualTo(CanvasCursor.ZOOM_OUT);
    }

    @Test
    void headlessTheCursorIsAPlainCrosshair() {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless());
        assertThat(tool.cursor().getType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
    }

    @Test
    void deactivatingDropsADragUnderWay() {
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(500, 400));
        tool.deactivated(context);
        assertThat(tool.band()).isNull();
        tool.mouseReleased(context, screen(500, 400));
        assertThat(context.zoomTargets).isEmpty();
    }

    @Test
    void theToolNeverChangesTheDocumentNorCrossesPanes() {
        tool.mousePressed(context, screen(100, 100));
        tool.mouseDragged(context, screen(500, 400));
        assertThat(tool.crossesPanes()).isFalse();
        tool.mouseReleased(context, screen(500, 400));
        click(200, 200, CanvasMouseEvent.PRIMARY, false, false);
        assertThat(context.openDocument().history().canUndo()).isFalse();
        assertThat(context.openDocument().isModified()).isFalse();
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.transform(context.viewport.documentTransform()); // as the canvas gives it: document units
            tool.paintOverlay(graphics, context);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private int paintedPixels() {
        return painted(paint(), 0, 0, 800, 600);
    }

    private static int painted(BufferedImage image, int x, int y, int width, int height) {
        int count = 0;
        for (int row = y; row < Math.min(image.getHeight(), y + height); row++) {
            for (int column = x; column < Math.min(image.getWidth(), x + width); column++) {
                if (alpha(image, column, row) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
