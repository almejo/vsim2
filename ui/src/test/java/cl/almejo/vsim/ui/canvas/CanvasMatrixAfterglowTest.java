package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
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

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.paint;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Brillo que se apaga» fades to fully off even when the matrix stops changing (decided in the «Filas fáciles» fix
 * round): the canvas's animation clock repaints a fading matrix and stops once nothing fades.
 */
class CanvasMatrixAfterglowTest {

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
    private Component matrix;

    @BeforeEach
    void showALitMatrixThatFades() {
        canvas.overrideOnScreen(() -> true);
        canvas.useTerminalTicker(ticker);
        renderer.useTerminalClock(() -> ticker.now() * NANOS_PER_MILLISECOND);
        renderer.useAfterglowClock(() -> ticker.now() * NANOS_PER_MILLISECOND);
        canvas.setSize(800, 600);
        matrix = place(openDocument, StandardComponentTypes.LED_MATRIX, 4, 4,
                Map.of("rows", 1, "columns", 1, "afterglow", true));
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private void show(boolean lit, long time) {
        canvas.display(new SimulationSnapshot(1, openDocument.netlistToken(), time, false, Map.of(),
                Map.of(matrix.id().value(), new VisibleState.LedMatrixState(List.of(Value.of(1, lit ? 1 : 0)))),
                List.of(), Optional.empty()));
        paint(canvas);
    }

    @Test
    void aMatrixThatStopsChangingWhileItsLedsFadeRunsTheClockUntilTheyAreOff() {
        show(true, 0);
        assertThat(canvas.isAnimatingTerminals()).as("nothing fades yet").isFalse();

        ticker.advance(48);
        show(false, 10);
        assertThat(canvas.isAnimatingTerminals()).as("fading").isTrue();

        for (int frame = 0; frame < 30 && canvas.isAnimatingTerminals(); frame++) {
            ticker.advance(16);
            paint(canvas);
        }
        ticker.advance(16);

        assertThat(canvas.isAnimatingTerminals()).as("faded to off, the clock stops").isFalse();
    }

    @Test
    void withReducedMotionNothingFadesAndNoClockRuns() {
        canvas.setReducedMotion(() -> true);
        show(true, 0);
        ticker.advance(48);
        show(false, 10);
        ticker.advance(16);

        assertThat(canvas.isAnimatingTerminals()).isFalse();
    }
}
