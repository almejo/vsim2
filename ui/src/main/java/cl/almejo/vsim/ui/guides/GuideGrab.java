package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.frames.FrameHitTester;
import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * The guide lines themselves as a handle (spec §4.3): with the select tool, a primary press within 3 screen pixels of
 * a guide where the select tool would not take the press itself (elements always win, and so do their pins and the
 * parts of a frame, resize edges included) grabs the guide; the pointer over such a place
 * shows the up-down or left-right resize cursor. Lowest priority of the guide handles, so the rulers win at the edges.
 * Other buttons are never claimed: a right click on a guide reaches the tool and opens the context menu.
 */
final class GuideGrab implements EdgeHandle {

    static final int PRIORITY = 10;

    private final GuidesFeatures features;

    GuideGrab(GuidesFeatures features) {
        this.features = features;
    }

    @Override
    public Shape hitArea(Dimension canvasSize) {
        Area strips = new Area();
        double reach = GuidesFeatures.GRAB_TOLERANCE + 0.5;
        for (Guide guide : features.document().guides()) {
            double screen = features.screenCoordinate(guide);
            strips.add(new Area(guide.orientation() == Orientation.HORIZONTAL
                    ? new Rectangle2D.Double(0, screen - reach, canvasSize.width, 2 * reach)
                    : new Rectangle2D.Double(screen - reach, 0, 2 * reach, canvasSize.height)));
        }
        return strips;
    }

    @Override
    public boolean claims(CanvasMouseEvent event) {
        CircuitCanvas canvas = features.canvas();
        // moving a guide needs no key: Shift and Ctrl presses stay the select tool's bands and toggles
        return event.button() == CanvasMouseEvent.PRIMARY
                && !event.shiftDown() && !event.controlDown()
                && canvas.tool() instanceof SelectTool
                && features.guideNear(event.screenPosition()).isPresent()
                && !selectToolTakes(canvas, event);
    }

    /**
     * The select tool would do something with a press here: an element under it, a pin within its capture radius
     * (starts a wire) or any part of a frame, its resize edges sticking out of it included.
     */
    private boolean selectToolTakes(CircuitCanvas canvas, CanvasMouseEvent event) {
        CircuitDocument document = features.document();
        return canvas.hitTester().elementAt(document, event.gridPosition()).isPresent()
                || new ConnectionPoints(canvas.openDocument().registry())
                        .pinNear(document, event.gridPosition()).isPresent()
                || FrameHitTester.hit(document, event.gridPosition(), canvas.viewport().pixelsPerUnit()).isPresent();
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getDefaultCursor();
    }

    @Override
    public Cursor cursorAt(CanvasMouseEvent event) {
        return features.guideNear(event.screenPosition()).map(GuidesFeatures::moveCursor).orElseGet(this::cursor);
    }

    @Override
    public String tooltip() {
        return "";
    }

    @Override
    public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
        // the guides are painted by the overlay, under the tool's own drawing
    }

    @Override
    public EdgeGesture press(CanvasMouseEvent event) {
        Guide guide = features.guideNear(event.screenPosition()).orElseThrow();
        return features.startDrag(guide.orientation(), Optional.of(guide), event);
    }
}
