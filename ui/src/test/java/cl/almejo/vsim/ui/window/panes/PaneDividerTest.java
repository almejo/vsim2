package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The dividers between the panes (split views spec §2, canvas-edges §1): dragged they resize the panes continuously,
 * dragged within 24 px of an edge or double-clicked they go away, and their crossing point moves both.
 */
class PaneDividerTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private CanvasPanes panes;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        panes = new CanvasPanes(this::newCanvas, newCanvas());
        panes.setSize(800, 600);
        panes.doLayout();
    }

    @AfterEach
    void close() {
        panes.dispose();
        openDocument.close();
    }

    private CircuitCanvas newCanvas() {
        return new CircuitCanvas(openDocument,
                new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
                new CanvasHost() {
                    @Override
                    public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                                Point2D screenPosition) {
                    }

                    @Override
                    public void configure(Component component) {
                    }
                }, selection);
    }

    private PaneDivider divider(Orientation orientation) {
        return panes.divider(orientation).orElseThrow();
    }

    /** A mouse event on {@code divider} at the area's point ({@code areaX}, {@code areaY}). */
    private static MouseEvent event(PaneDivider divider, int id, int areaX, int areaY, int modifiers, int clicks) {
        return new MouseEvent(divider, id, 0, modifiers, areaX - divider.getX(), areaY - divider.getY(), clicks,
                false, MouseEvent.BUTTON1);
    }

    private static void press(PaneDivider divider, int areaX, int areaY) {
        MouseEvent pressed = event(divider, MouseEvent.MOUSE_PRESSED, areaX, areaY, InputEvent.BUTTON1_DOWN_MASK, 1);
        for (MouseListener listener : divider.getMouseListeners()) {
            listener.mousePressed(pressed);
        }
    }

    private static void drag(PaneDivider divider, int areaX, int areaY) {
        MouseEvent dragged = event(divider, MouseEvent.MOUSE_DRAGGED, areaX, areaY, InputEvent.BUTTON1_DOWN_MASK, 1);
        for (MouseMotionListener listener : divider.getMouseMotionListeners()) {
            listener.mouseDragged(dragged);
        }
    }

    private static void release(PaneDivider divider, int areaX, int areaY) {
        MouseEvent released = event(divider, MouseEvent.MOUSE_RELEASED, areaX, areaY, 0, 1);
        for (MouseListener listener : divider.getMouseListeners()) {
            listener.mouseReleased(released);
        }
    }

    private static void doubleClick(PaneDivider divider, int areaX, int areaY) {
        MouseEvent clicked = event(divider, MouseEvent.MOUSE_CLICKED, areaX, areaY, 0, 2);
        for (MouseListener listener : divider.getMouseListeners()) {
            listener.mouseClicked(clicked);
        }
    }

    /** Where a divider sits when its middle is at {@code position} of an area {@code length} long. */
    private static double fractionAt(int position, int length) {
        int thickness = PaneDivider.thickness();
        return (position - thickness / 2.0) / (length - thickness);
    }

    @Test
    void draggingADividerResizesThePanesAsItGoesAndKeepsThemOnRelease() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        PaneDivider divider = divider(Orientation.HORIZONTAL);
        int middle = divider.getY() + divider.getHeight() / 2;

        press(divider, 400, middle);
        drag(divider, 400, 200);
        assertThat(panes.dividerFraction(Orientation.HORIZONTAL).getAsDouble())
                .isCloseTo(fractionAt(200, 600), within(0.01));
        drag(divider, 400, 450);
        release(divider, 400, 450);

        assertThat(panes.panes()).hasSize(2);
        assertThat(panes.dividerFraction(Orientation.HORIZONTAL).getAsDouble())
                .isCloseTo(fractionAt(450, 600), within(0.01));
        assertThat(panes.panes().get(1).getY()).isGreaterThan(400);
    }

    @Test
    void releasingADividerNearAnEdgeRemovesItAndClosesThePanesOnThatSide() {
        panes.split(Orientation.VERTICAL, 0.5);
        CircuitCanvas left = panes.panes().get(0);
        CircuitCanvas right = panes.panes().get(1);
        PaneDivider divider = divider(Orientation.VERTICAL);
        int middle = divider.getX() + divider.getWidth() / 2;

        press(divider, middle, 300);
        drag(divider, 12, 300);
        release(divider, 12, 300);

        assertThat(panes.divider(Orientation.VERTICAL)).isEmpty();
        assertThat(panes.panes()).containsExactly(right);
        assertThat(left.getParent()).isNull();
    }

    @Test
    void releasingADividerNearTheFarEdgeClosesThePanesThere() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        CircuitCanvas top = panes.panes().get(0);
        PaneDivider divider = divider(Orientation.HORIZONTAL);

        press(divider, 400, divider.getY() + 2);
        drag(divider, 400, 590);
        release(divider, 400, 590);

        assertThat(panes.panes()).containsExactly(top);
    }

    @Test
    void aDoubleClickRemovesTheDivider() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        PaneDivider divider = divider(Orientation.HORIZONTAL);

        doubleClick(divider, 100, divider.getY() + 2);

        assertThat(panes.divider(Orientation.HORIZONTAL)).isEmpty();
        assertThat(panes.panes()).hasSize(1);
    }

    @Test
    void theCrossingPointMovesBothDividers() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        panes.split(Orientation.VERTICAL, 0.5);
        PaneDivider horizontal = divider(Orientation.HORIZONTAL);
        PaneDivider vertical = divider(Orientation.VERTICAL);
        int crossingX = vertical.getX() + vertical.getWidth() / 2;
        int crossingY = horizontal.getY() + horizontal.getHeight() / 2;
        // whichever divider is on top at the crossing gets the press
        PaneDivider underThePointer = panes.getComponentAt(crossingX, crossingY) instanceof PaneDivider divider
                ? divider : horizontal;

        press(underThePointer, crossingX, crossingY);
        drag(underThePointer, 200, 150);
        release(underThePointer, 200, 150);

        assertThat(panes.dividerFraction(Orientation.HORIZONTAL).getAsDouble())
                .isCloseTo(fractionAt(150, 600), within(0.01));
        assertThat(panes.dividerFraction(Orientation.VERTICAL).getAsDouble())
                .isCloseTo(fractionAt(200, 800), within(0.01));
        assertThat(panes.panes()).hasSize(4);
    }

    @Test
    void aPressAwayFromTheCrossingMovesOnlyItsDivider() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        panes.split(Orientation.VERTICAL, 0.5);
        PaneDivider horizontal = divider(Orientation.HORIZONTAL);
        double vertical = panes.dividerFraction(Orientation.VERTICAL).getAsDouble();

        press(horizontal, 100, horizontal.getY() + 2);
        drag(horizontal, 300, 150);
        release(horizontal, 300, 150);

        assertThat(panes.dividerFraction(Orientation.VERTICAL).getAsDouble()).isEqualTo(vertical);
        assertThat(panes.dividerFraction(Orientation.HORIZONTAL).getAsDouble())
                .isCloseTo(fractionAt(150, 600), within(0.01));
    }

    @Test
    void theDividerExplainsItselfAndUsesTheResizeCursors() {
        panes.split(Orientation.HORIZONTAL, 0.5);
        panes.split(Orientation.VERTICAL, 0.5);
        for (Orientation orientation : List.of(Orientation.HORIZONTAL, Orientation.VERTICAL)) {
            PaneDivider divider = divider(orientation);
            assertThat(divider.getToolTipText())
                    .isEqualTo("Arrastra para cambiar el tamaño; doble clic para quitar la división");
            assertThat(divider.getCursor().getType()).isEqualTo(orientation == Orientation.HORIZONTAL
                    ? Cursor.N_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR);
        }
    }
}
