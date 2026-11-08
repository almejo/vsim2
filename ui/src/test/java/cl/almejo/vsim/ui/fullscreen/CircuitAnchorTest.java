package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The circuit stays in view across presentation mode (the user, 2026-11-08: after leaving, the view ended panned away
 * and the circuit was out of sight): each pane keeps its zoom and the grid point at its centre through every resize
 * of the transition, the window manager's late ones included, and lets go once the window settled or the user acts.
 */
class CircuitAnchorTest {

    private final ManualFrameTicker clock = new ManualFrameTicker();
    private EditorFixture fixture;
    private JRootPane rootPane;
    private CircuitAnchor anchor;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        rootPane = new JRootPane();
        rootPane.setContentPane(fixture.view());
        anchor = new CircuitAnchor(rootPane, clock::now);
    }

    @AfterEach
    void close() {
        anchor.release();
        fixture.close();
    }

    private static CircuitCanvas sized(CircuitCanvas pane, int width, int height) {
        pane.setSize(width, height);
        return pane;
    }

    private static void assertShows(CircuitCanvas pane, ViewState expected) {
        ViewState shown = pane.viewState();
        assertThat(shown.zoom()).isCloseTo(expected.zoom(), within(1e-9));
        assertThat(shown.centerX()).isCloseTo(expected.centerX(), within(1e-6));
        assertThat(shown.centerY()).isCloseTo(expected.centerY(), within(1e-6));
    }

    @Test
    void severalResizesDuringTheTransitionEndWithTheSavedCentreAndZoom() {
        CircuitCanvas pane = sized(fixture.view().canvas(), 800, 600);
        ViewState saved = new ViewState(1.5, 40, 25);
        pane.applyViewState(saved);
        anchor.hold(List.of(pane));
        for (int[] size : new int[][]{{1280, 820}, {1920, 1200}, {1880, 1163}, {840, 890}}) {
            sized(pane, size[0], size[1]);
            anchor.paneChanged(pane); // the resize event of the pane
            clock.advance(200);
        }
        assertShows(pane, saved);
    }

    @Test
    void everySplitPaneKeepsItsOwnCentre() {
        CanvasPanes panes = fixture.view().canvasPanes();
        panes.split(CanvasPanes.Orientation.HORIZONTAL, 0.5);
        List<CircuitCanvas> both = fixture.view().panes();
        assertThat(both).hasSize(2);
        sized(both.get(0), 400, 600).applyViewState(new ViewState(1, 10, 10));
        sized(both.get(1), 400, 600).applyViewState(new ViewState(2, -30, 50));
        ViewState first = both.get(0).viewState();
        ViewState second = both.get(1).viewState();
        anchor.hold(both);
        sized(both.get(0), 960, 1200);
        sized(both.get(1), 960, 1200);
        anchor.follow();
        assertShows(both.get(0), first);
        assertShows(both.get(1), second);
    }

    /** The final review's M4: a zoom by the user before the window settled is never undone by a later resize. */
    /**
     * Runs {@code body} on the EDT, as the editor does: the pane's resize events then reach the anchor only after it,
     * never between a user's zoom and the next resize.
     */
    private static void onTheEdt(Runnable body) throws Exception {
        try {
            javax.swing.SwingUtilities.invokeAndWait(body);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    @Test
    void aZoomByTheUserBeforeTheWindowSettledIsKept() throws Exception {
        onTheEdt(this::zoomBeforeTheWindowSettled);
    }

    private void zoomBeforeTheWindowSettled() {
        CircuitCanvas pane = sized(fixture.view().canvas(), 800, 600);
        pane.applyViewState(new ViewState(1, 0, 0));
        anchor.hold(List.of(pane));
        sized(pane, 1280, 820);
        anchor.paneChanged(pane);
        clock.advance(500);
        pane.zoomIn(); // Ctrl+=, the wheel with Ctrl
        double zoomed = pane.viewState().zoom();
        sized(pane, 1270, 800); // the window manager's late resize
        anchor.paneChanged(pane);
        assertThat(pane.viewState().zoom()).isCloseTo(zoomed, within(1e-9));
    }

    @Test
    void aPanByTheUserBeforeTheWindowSettledIsKept() throws Exception {
        onTheEdt(this::panBeforeTheWindowSettled);
    }

    private void panBeforeTheWindowSettled() {
        CircuitCanvas pane = sized(fixture.view().canvas(), 800, 600);
        pane.applyViewState(new ViewState(1, 0, 0));
        anchor.hold(List.of(pane));
        sized(pane, 1280, 820);
        anchor.paneChanged(pane);
        pane.viewport().panBy(0, -120); // the wheel: no view event at all
        java.awt.geom.Point2D topLeft = pane.viewport().screenToGrid(0, 0);
        sized(pane, 1270, 800);
        anchor.paneChanged(pane);
        java.awt.geom.Point2D after = pane.viewport().screenToGrid(0, 0);
        assertThat(after.getX()).isCloseTo(topLeft.getX(), within(1e-6));
        assertThat(after.getY()).isCloseTo(topLeft.getY(), within(1e-6));
    }

    @Test
    void afterTheWindowSettledAResizeIsTheUsersAgain() {
        CircuitCanvas pane = sized(fixture.view().canvas(), 800, 600);
        ViewState saved = new ViewState(1, 0, 0);
        pane.applyViewState(saved);
        anchor.hold(List.of(pane));
        clock.advance(CircuitAnchor.SETTLE_MILLISECONDS + 100);
        pane.applyViewState(new ViewState(1, 100, 100)); // the user panned
        sized(pane, 900, 700);
        anchor.paneChanged(pane);
        assertThat(pane.viewState().centerX()).isNotCloseTo(saved.centerX(), within(1.0));
    }
}
