package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.paint;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas runs the Terminal's animation clock only while a moving terminal is in view: it stops off screen, with
 * the terminal scrolled away or deleted, and with animations off (a still screen needs no clock).
 */
class CanvasTerminalAnimationTest {

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final CircuitRenderer renderer = new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(),
            ColorScheme.LIGHT);
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument, renderer, new CanvasHost() {
        @Override
        public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
        }

        @Override
        public void configure(Component component) {
        }
    });
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private boolean onScreen = true;
    private Component terminal;

    @BeforeEach
    void showATerminalWithABlinkingCursor() {
        canvas.overrideOnScreen(() -> onScreen);
        canvas.useTerminalTicker(ticker);
        renderer.useTerminalClock(() -> ticker.now() * NANOS_PER_MILLISECOND);
        canvas.setSize(800, 600);
        terminal = place(openDocument, StandardComponentTypes.TERMINAL, 2, 2,
                Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20, TerminalTypes.CURSOR, "bar",
                        TerminalTypes.BLINK, true));
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        screen.write('a', 0);
        VisibleState state = new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
        canvas.display(new SimulationSnapshot(1, openDocument.netlistToken(), 0, true, Map.of(),
                Map.of(terminal.id().value(), state), List.of(), Optional.empty()));
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    @Test
    void aBlinkingTerminalInViewRunsTheClockAndOffScreenItStops() {
        paint(canvas);
        assertThat(canvas.isAnimatingTerminals()).isTrue();

        onScreen = false;
        ticker.advance(40);
        assertThat(canvas.isAnimatingTerminals()).isFalse();

        canvas.overrideOnScreen(() -> true);
        assertThat(canvas.isAnimatingTerminals()).as("back on screen").isTrue();
    }

    @Test
    void aTerminalScrolledOutOfViewStopsTheClock() {
        paint(canvas);
        canvas.viewport().panBy(-5_000, 0);
        ticker.advance(40);
        assertThat(canvas.isAnimatingTerminals()).isFalse();
    }

    @Test
    void aDeletedTerminalStopsTheClock() {
        paint(canvas);
        openDocument.document().remove(terminal.id());
        paint(canvas);
        ticker.advance(40);
        assertThat(canvas.isAnimatingTerminals()).isFalse();
    }

    @Test
    void withAnimationsOffTheCursorStaysOnAndNoClockRuns() {
        canvas.setAnimations(() -> false);
        paint(canvas);
        ticker.advance(40);
        assertThat(canvas.isAnimatingTerminals()).isFalse();
    }

    @Test
    void turningAnimationsOffWhileTheCursorBlinksStopsTheClockAtOnce() {
        AtomicBoolean animations = new AtomicBoolean(true);
        canvas.setAnimations(animations::get);
        paint(canvas);
        assertThat(canvas.isAnimatingTerminals()).isTrue();

        animations.set(false);
        paint(canvas);
        ticker.advance(40);

        assertThat(canvas.isAnimatingTerminals()).isFalse();
    }
}
