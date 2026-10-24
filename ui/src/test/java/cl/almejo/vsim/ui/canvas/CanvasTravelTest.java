package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CanvasTravelTest {

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
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final AtomicBoolean animate = new AtomicBoolean(true);

    @BeforeEach
    void setUp() {
        canvas.setSize(400, 300);
        canvas.applyViewState(new ViewState(1, 0, 0));
        canvas.setAnimateViewMoves(animate::get);
        ticker.drive(canvas);
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private double centerX() {
        return canvas.viewState().centerX();
    }

    @Test
    void withTheAnimationOffTheViewJumpsAtOnce() {
        animate.set(false);
        canvas.travelTo(new Bounds(20, 10, 4, 2));
        assertThat(canvas.isTravelling()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 22, 11));
    }

    @Test
    void aCanvasWithoutAnimationsSetJumps() {
        CircuitCanvas plain = new CircuitCanvas(openDocument,
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
        try {
            plain.setSize(400, 300);
            plain.travelTo(new Bounds(20, 10, 4, 2));
            assertThat(plain.isTravelling()).isFalse();
            assertThat(plain.viewState().centerX()).isCloseTo(22, within(1e-9));
        } finally {
            plain.dispose();
        }
    }

    @Test
    void theEasedPanReachesTheExactTargetAndTellsTheListenersEveryFrame() {
        AtomicInteger viewportChanges = new AtomicInteger();
        AtomicInteger viewChanges = new AtomicInteger();
        canvas.addViewportListener(viewportChanges::incrementAndGet);
        canvas.addViewListener(viewChanges::incrementAndGet);

        canvas.travelTo(new Bounds(20, 10, 4, 2));
        assertThat(canvas.isTravelling()).isTrue();
        assertThat(centerX()).isZero();

        ticker.advance(160);
        assertThat(centerX()).isBetween(1.0, 21.0);
        assertThat(viewportChanges.get()).isEqualTo(10);
        assertThat(viewChanges.get()).isEqualTo(10);

        ticker.advance(160);
        assertThat(canvas.isTravelling()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
        assertThat(canvas.viewport().gridToScreen(22, 11)).isEqualTo(new Point2D.Double(200, 150));
        assertThat(canvas.viewState().zoom()).isEqualTo(1.0);
    }

    @Test
    void aFarTargetZoomsOutThenBackToTheUsersZoom() {
        canvas.applyViewState(new ViewState(1.5, 0, 0));
        canvas.travelTo(new Bounds(150, 0, 2, 2));
        double lowest = 1.5;
        while (canvas.isTravelling()) {
            ticker.advance(16);
            lowest = Math.min(lowest, canvas.viewState().zoom());
        }
        assertThat(lowest).isLessThan(1.0);
        assertThat(canvas.viewState().zoom()).isEqualTo(1.5);
        assertThat(canvas.viewport().gridToScreen(151, 1)).isEqualTo(new Point2D.Double(200, 150));
    }

    @Test
    void boundsThatDoNotFitZoomOutUntilTheyDo() {
        canvas.travelTo(new Bounds(0, 0, 100, 10));
        ticker.finish();
        double zoom = canvas.viewState().zoom();
        assertThat(100 * 8 * zoom).isLessThanOrEqualTo(400);
        assertThat(zoom).isGreaterThan(0.4);
    }

    @Test
    void aNewTravelMidFlightContinuesFromWhereTheViewIs() {
        canvas.travelTo(new Bounds(20, 0, 0, 0));
        ticker.advance(144);
        ViewState midway = canvas.viewState();

        canvas.travelTo(new Bounds(-20, 30, 0, 0));
        assertThat(canvas.viewState()).isEqualTo(midway);
        assertThat(canvas.isTravelling()).isTrue();
        ticker.advance(16);
        // It keeps going, now towards the new target, without first stopping in place.
        assertThat(canvas.viewState().centerY()).isGreaterThan(1.0);

        ticker.finish();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, -20, 30));
    }

    @Test
    void aNewTravelDuringAFlightLandsOnTheUsersZoomNotTheZoomedOutOne() {
        canvas.travelTo(new Bounds(300, 0, 0, 0));
        ticker.advance(300);
        assertThat(canvas.viewState().zoom()).isLessThan(1.0);

        canvas.travelTo(new Bounds(10, 10, 0, 0));
        ticker.finish();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 10, 10));
    }

    @Test
    void afterAZoomOutToFitTheNextTargetComesBackToTheUsersZoom() {
        for (boolean animated : new boolean[]{true, false}) {
            animate.set(animated);
            canvas.applyViewState(new ViewState(2, 0, 0)); // the user's zoom
            canvas.travelTo(new Bounds(0, 0, 200, 10)); // too wide: zooms out to fit
            ticker.finish();
            assertThat(canvas.viewState().zoom()).as("animated " + animated).isLessThan(1.0);

            canvas.travelTo(new Bounds(40, 40, 2, 2));
            ticker.finish();

            assertThat(canvas.viewState()).as("animated " + animated).isEqualTo(new ViewState(2, 41, 41));
        }
    }

    @Test
    void aZoomByHandBecomesTheUsersZoom() {
        canvas.travelTo(new Bounds(0, 0, 200, 10));
        ticker.finish();
        canvas.viewport().zoomAt(200, 150, 0.75);

        canvas.travelTo(new Bounds(40, 40, 2, 2));
        ticker.finish();

        assertThat(canvas.viewState().zoom()).isEqualTo(0.75);
    }

    @Test
    void aDisposedCanvasJumpsInsteadOfStartingATravel() {
        canvas.dispose();

        canvas.travelTo(new Bounds(20, 10, 0, 0));

        assertThat(canvas.isTravelling()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 20, 10));
    }

    @Test
    void turningTheAnimationOffMidFlightJumpsToTheTarget() {
        canvas.travelTo(new Bounds(20, 10, 0, 0));
        ticker.advance(48);
        animate.set(false);
        ticker.advance(16);
        assertThat(canvas.isTravelling()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 20, 10));
    }

    @Test
    void panningOrZoomingByHandStopsTheTravelWhereItIs() {
        canvas.travelTo(new Bounds(20, 10, 0, 0));
        ticker.advance(48);
        canvas.viewport().panBy(5, 0);
        ViewState left = canvas.viewState();
        assertThat(canvas.isTravelling()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
        ticker.advance(400);
        assertThat(canvas.viewState()).isEqualTo(left);
    }

    @Test
    void disposingStopsTheTicker() {
        canvas.travelTo(new Bounds(20, 10, 0, 0));
        ticker.advance(48);
        canvas.dispose();
        assertThat(ticker.isRunning()).isFalse();
        assertThat(canvas.isTravelling()).isFalse();
    }

    @Test
    void anUnsizedCanvasJumps() {
        canvas.setSize(0, 0);
        canvas.travelTo(new Bounds(20, 10, 0, 0));
        assertThat(canvas.isTravelling()).isFalse();
    }

    // zoomTo: a zoom the user asked for (zoom spec §3)

    /** The canvas counts as on screen, so a {@link CircuitCanvas#zoomTo} may travel. */
    private void showOnScreen() {
        canvas.overrideOnScreen(() -> true);
    }

    @Test
    void zoomToWithReducedMotionJumps() {
        showOnScreen();
        animate.set(false);

        canvas.zoomTo(new ViewState(2, 10, 5));

        assertThat(canvas.isTravelling()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 10, 5));
    }

    @Test
    void zoomToOnACanvasThatIsNotOnScreenJumps() {
        canvas.zoomTo(new ViewState(2, 10, 5));

        assertThat(canvas.isTravelling()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 10, 5));
    }

    @Test
    void zoomToTravelsToTheExactTargetAndTellsTheViewListenersEveryFrame() {
        showOnScreen();
        AtomicInteger viewChanges = new AtomicInteger();
        canvas.addViewListener(viewChanges::incrementAndGet);

        canvas.zoomTo(new ViewState(2, 10, 5));
        assertThat(canvas.isTravelling()).isTrue();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 0, 0));

        ticker.advance(160);
        assertThat(viewChanges.get()).isEqualTo(10);
        assertThat(canvas.viewState().zoom()).isBetween(1.0, 2.0);

        ticker.finish();
        assertThat(canvas.isTravelling()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 10, 5));
    }

    @Test
    void zoomToStopsAtTheZoomLimits() {
        showOnScreen();
        canvas.zoomTo(new ViewState(10, 3, 3));
        ticker.finish();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(Viewport.MAXIMUM_ZOOM, 3, 3));

        animate.set(false);
        canvas.zoomTo(new ViewState(0.01, 3, 3));
        assertThat(canvas.viewState()).isEqualTo(new ViewState(Viewport.MINIMUM_ZOOM, 3, 3));
    }

    @Test
    void zoomToBecomesTheUsersZoomEvenBeforeItArrives() {
        showOnScreen();
        canvas.zoomTo(new ViewState(2, 0, 0));
        ticker.advance(48);

        canvas.travelTo(new Bounds(10, 10, 2, 2)); // takes over: rests at the zoom asked for, not the one passing by
        ticker.finish();

        assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 11, 11));
    }

    @Test
    void zoomToTellsTheViewTakenListenersOnceBecauseTheUserAskedForIt() {
        showOnScreen();
        AtomicInteger taken = new AtomicInteger();
        canvas.addViewTakenListener(taken::incrementAndGet);

        canvas.zoomTo(new ViewState(2, 10, 5));
        ticker.finish();

        assertThat(taken.get()).isEqualTo(1);
    }

    @Test
    void aWheelZoomPanOrStepMidTravelStopsTheZoomWhereItIs() {
        showOnScreen();
        List<Runnable> interventions = List.of(
                () -> canvas.viewport().zoomAt(100, 100, canvas.viewport().zoom() * Viewport.ZOOM_STEP),
                () -> canvas.viewport().panBy(5, 0),
                canvas::zoomIn,
                canvas::zoomOut,
                () -> canvas.zoomAroundCenter(1.5));
        for (Runnable intervention : interventions) {
            canvas.applyViewState(new ViewState(1, 0, 0));
            canvas.zoomTo(new ViewState(3, 10, 5));
            ticker.advance(48);

            intervention.run();
            ViewState left = canvas.viewState();
            assertThat(canvas.isTravelling()).isFalse();
            assertThat(ticker.isRunning()).isFalse();
            ticker.advance(400);
            assertThat(canvas.viewState()).isEqualTo(left);
        }
    }

    @Test
    void anotherZoomToMidTravelGoesToTheNewTargetInstead() {
        showOnScreen();
        canvas.zoomTo(new ViewState(3, 10, 5));
        ticker.advance(48);

        canvas.zoomTo(new ViewState(0.5, -4, 2));
        ticker.finish();

        assertThat(canvas.viewState()).isEqualTo(new ViewState(0.5, -4, 2));
    }

    @Test
    void resetZoomTravelsBackToOneHundredPercentAroundTheMiddle() {
        showOnScreen();
        canvas.applyViewState(new ViewState(2.5, 10, 10));

        canvas.resetZoom();
        assertThat(canvas.isTravelling()).isTrue();
        while (canvas.isTravelling()) {
            ticker.advance(16);
            assertThat(canvas.viewState().centerX()).isCloseTo(10, within(1e-9));
            assertThat(canvas.viewState().centerY()).isCloseTo(10, within(1e-9));
        }

        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 10, 10));
    }

    @Test
    void resetZoomWithReducedMotionJumps() {
        showOnScreen();
        animate.set(false);
        canvas.applyViewState(new ViewState(2.5, 10, 10));

        canvas.resetZoom();

        assertThat(canvas.isTravelling()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(1, 10, 10));
    }

    @Test
    void zoomAroundCenterIsInstantKeepsTheMiddleAndBecomesTheUsersZoom() {
        canvas.applyViewState(new ViewState(1, 5, 5));

        canvas.zoomAroundCenter(2);

        assertThat(canvas.isTravelling()).isFalse();
        assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 5, 5));
        canvas.travelTo(new Bounds(40, 40, 2, 2));
        ticker.finish();
        assertThat(canvas.viewState().zoom()).isEqualTo(2.0);
        canvas.zoomAroundCenter(100);
        assertThat(canvas.viewState().zoom()).isEqualTo(Viewport.MAXIMUM_ZOOM);
    }

    @Test
    void theCanvasToolsZoomThroughIt() {
        ToolContext context = canvas;
        assertThat(context.viewSize()).isEqualTo(new Dimension(400, 300));
        context.zoomTo(new ViewState(2, 10, 5));
        assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 10, 5));
    }

    @Test
    void aGestureOverAnotherPaneZoomsThePanePressedOnly() {
        CircuitCanvas other = new CircuitCanvas(openDocument,
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
        try {
            other.setSize(200, 100);
            other.applyViewState(new ViewState(1, 0, 0));
            ToolContext crossing = new CrossPaneContext(canvas, other);

            assertThat(crossing.viewSize()).isEqualTo(new Dimension(400, 300));
            crossing.zoomTo(new ViewState(2, 10, 5));

            assertThat(canvas.viewState()).isEqualTo(new ViewState(2, 10, 5));
            assertThat(other.viewState()).isEqualTo(new ViewState(1, 0, 0));
        } finally {
            other.dispose();
        }
    }
}
