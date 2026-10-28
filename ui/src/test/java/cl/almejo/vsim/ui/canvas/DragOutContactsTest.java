package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A drag out brought back over its canvas previews a move: it shows the contacts that move would make (spec §3.7).
 * At 100 % a grid unit is 8 screen pixels.
 */
class DragOutContactsTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
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

    @BeforeEach
    void create() {
        canvas.overrideOnScreen(() -> true);
        canvas.useManualAutoscrollClock();
        canvas.setSize(400, 300);
        preferences.setSnapToAlignments(false);
        canvas.useSnapper(new DefaultSnapper(preferences, canvas::getSize));
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    @Test
    void theMovePreviewedByADragOutBroughtBackShowsItsContacts() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 20, 10, Map.of(StandardComponentTypes.NEGATE, true));
        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(), new GridPoint(20, 15),
                new GridPoint(20, 25))));
        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, 176, 96, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        canvas.getMouseMotionListeners()[0].mouseDragged(
                mouse(MouseEvent.MOUSE_DRAGGED, 176, 64, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
        assertThat(canvas.beginDragOut()).isTrue();

        canvas.dragOutOver(new Point(176, 160)); // eight units below the press: the input pin onto the wire
        assertThat(canvas.dragOutContacts()).containsExactly(new NewContact(new GridPoint(20, 20),
                Optional.of(gate.id()), Optional.of("in"), Optional.of("in"), Optional.empty()));

        canvas.dragOutLeft();
        assertThat(canvas.dragOutContacts()).isEmpty();
    }
}
