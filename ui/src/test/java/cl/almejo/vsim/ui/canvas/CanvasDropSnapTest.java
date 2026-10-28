package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ajustar for drops on the canvas (spec §3.4, review M1): a palette drop, a Ctrl drop of a fragment, a fragment that
 * floats and a drag out brought back. At 100 % a grid unit is 8 screen pixels; the anchor is a 4×4 NOT gate at the
 * origin (lines 0, 2, 4).
 */
class CanvasDropSnapTest {

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
        canvas.useSnapper(new DefaultSnapper(preferences, canvas::getSize));
        place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private List<GridPoint> positions() {
        return openDocument.document().components().stream().map(Component::position).toList();
    }

    private static String gateFragment() {
        try (OpenDocument source = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            Component gate = place(source, StandardComponentTypes.BUFFER, 3, 3, Map.of(StandardComponentTypes.NEGATE, true));
            return new FragmentFormat(source.registry())
                    .write(CircuitFragment.of(source.document(), List.of(gate.id()), source.registry()));
        }
    }

    @Test
    void aPaletteDropSnaps() {
        assertThat(canvas.dropComponent(PaletteTransfer.encode(StandardComponentTypes.BUFFER), new Point(160, 8)))
                .isTrue();
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(20, 0));
    }

    @Test
    void aPaletteDropWithAjustarOffLandsUnderThePointer() {
        preferences.setSnapToAlignments(false);
        canvas.dropComponent(PaletteTransfer.encode(StandardComponentTypes.BUFFER), new Point(160, 8));
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(20, 1));
    }

    @Test
    void aCtrlDropOfAFragmentSnaps() {
        assertThat(canvas.dropFragment(gateFragment(), Optional.empty(), new Point(160, 8), true)).isTrue();
        assertThat(positions()).containsExactly(GridPoint.ORIGIN, new GridPoint(20, 0));
    }

    @Test
    void aFloatingDropSnapsAtOnce() {
        assertThat(canvas.dropFragment(gateFragment(), new Point(160, 8))).isTrue();
        assertThat(canvas.tool()).isInstanceOfSatisfying(FloatingSelectionTool.class,
                floating -> assertThat(floating.position()).isEqualTo(new GridPoint(20, 0)));
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    @Test
    void aDragOutBroughtBackSnapsInThePreviewAndInTheMove() throws Exception {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 20, 10, Map.of(StandardComponentTypes.NEGATE, true));
        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, 176, 96, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        canvas.getMouseMotionListeners()[0].mouseDragged(
                mouse(MouseEvent.MOUSE_DRAGGED, 176, 64, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
        assertThat(canvas.beginDragOut()).isTrue();
        Transferable transferable = ((CanvasTransferHandler) canvas.getTransferHandler()).createTransferable(canvas);
        String json = (String) transferable.getTransferData(FragmentTransferable.FRAGMENT_FLAVOR);
        // the pointer back nine units above the press: the gate's top at 1, one off the anchor's
        canvas.dragOutOver(new Point(176, 24));
        assertThat(canvas.dragOutPreview()).contains(new GridPoint(0, -10));
        assertThat(canvas.dropFragment(json, new Point(176, 24))).isTrue();
        assertThat(openDocument.document().component(gate.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(20, 0));
    }
}
