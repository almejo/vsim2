package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.CanvasClicks;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.tools.PanTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.netcard.NetCardCircuits.netOf;
import static cl.almejo.vsim.ui.netcard.NetCardCircuits.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

class NetCardFeaturesTest {

    private static final long DELAY = 750;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final AtomicReference<SimulationSnapshot> latest = new AtomicReference<>(SimulationSnapshot.EMPTY);
    private EditorFixture fixture;
    private OpenDocument openDocument;
    private CircuitCanvas canvas;
    private NetCardFeatures features;
    private NetCardCircuits.AndExample example;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        openDocument = fixture.openDocument();
        canvas = fixture.view().canvas();
        canvas.setSize(1200, 800);
        example = NetCardCircuits.andExample(openDocument);
        features = new NetCardFeatures(fixture.view(), preferences,
                new NetCardFeatures.Setup(() -> ticker, ticker::now, () -> DELAY, document -> latest.get()));
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    private Point2D screenOf(double gridX, double gridY) {
        return canvas.viewport().gridToScreen(gridX, gridY);
    }

    private void moveTo(double gridX, double gridY) {
        Point2D point = screenOf(gridX, gridY);
        CanvasClicks.move(canvas, (int) Math.round(point.getX()), (int) Math.round(point.getY()));
    }

    private void moveOverTheAndOutput() {
        moveTo(32.5, 10);
    }

    private void moveOverNothing() {
        moveTo(60, 60);
    }

    private PaneNetCard pane() {
        return features.paneOf(canvas).orElseThrow();
    }

    @Test
    void hoveringAWireLightsItsWholeNetOnly() {
        moveOverTheAndOutput();

        assertThat(pane().shown()).hasValueSatisfying(summary -> {
            assertThat(summary.netId().value()).isEqualTo(netOf(openDocument, example.andOutput()));
            assertThat(summary.wires()).contains(example.notInput().id()).doesNotContain(example.inputA().id());
        });
        assertThat(canvas.overlays()).contains(pane().halo());
    }

    @Test
    void aRubberBandOverAWireLightsNothing() {
        Point2D start = screenOf(60, 60);
        Point2D over = screenOf(32.5, 10);
        MouseEvent pressed = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                (int) start.getX(), (int) start.getY(), 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(pressed);
        }
        MouseEvent dragged = new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
                (int) over.getX(), (int) over.getY(), 0, false, MouseEvent.NOBUTTON);
        for (MouseMotionListener listener : canvas.getMouseMotionListeners()) {
            listener.mouseDragged(dragged);
        }

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void anotherToolLightsNothing() {
        fixture.view().setToolOnEveryPane(PanTool::new);

        moveOverTheAndOutput();

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void leavingTheWireKeepsTheHaloForOneSecond() {
        moveOverTheAndOutput();
        ticker.advance(DELAY);

        moveOverNothing();
        ticker.advance(999);
        assertThat(pane().shown()).isPresent();
        ticker.advance(1);

        assertThat(pane().shown()).isEmpty();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void crossingAnotherWireBrieflyKeepsTheFirstNetAndRestingThereSwitches() {
        moveOverTheAndOutput();
        ticker.advance(DELAY);
        String first = netOf(openDocument, example.andOutput());

        moveTo(example.inputA().start().x() + 3, example.inputA().start().y());
        ticker.advance(DELAY - 16);
        assertThat(pane().shown().orElseThrow().netId().value()).isEqualTo(first);
        ticker.advance(16);

        assertThat(pane().shown().orElseThrow().netId().value()).isEqualTo(netOf(openDocument, example.inputA()));
    }

    @Test
    void withThePreferenceOffNothingLightsAndTheBusBalloonComesBack() {
        Component constant = NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.CONSTANT,
                Map.of(WIDTH, 4), "", 0, 10, 40);
        Wire bus = addWire(openDocument, 10, 40, 20, 40);
        fixture.view().problems().showSnapshot(snapshot(openDocument, Map.of(netOf(openDocument, bus),
                Value.of(4, 0xB))));
        Point2D over = screenOf(15, 40);
        MouseEvent hover = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, (int) over.getX(), (int) over.getY(),
                0, false);
        assertThat(canvas.getToolTipText(hover)).isNull();

        preferences.setHighlightNetOnHover(false);
        moveTo(15, 40);

        assertThat(pane().shown()).isEmpty();
        assertThat(canvas.getToolTipText(hover)).startsWith("Bus de 4 bits = 0xB");
        assertThat(constant).isNotNull();
    }

    @Test
    void turningThePreferenceOffPutsOutTheHalo() {
        moveOverTheAndOutput();

        preferences.setHighlightNetOnHover(false);

        assertThat(pane().shown()).isEmpty();
    }

    @Test
    void theStaticInstallReturnsItsUndoing() {
        features.dispose();
        int overlays = canvas.overlays().size();
        int listeners = canvas.snapshotListenerCount();

        Runnable uninstall = NetCardFeatures.install(fixture.view(), preferences);
        assertThat(canvas.overlays()).hasSize(overlays + 1);
        uninstall.run();

        assertThat(canvas.overlays()).hasSize(overlays);
        assertThat(canvas.snapshotListenerCount()).isEqualTo(listeners);
    }
}
