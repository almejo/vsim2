package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.HEIGHT;
import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Presentation mode in one window: entering hides the menu bar, the tabs and toolbar, the left column, the right
 * panel with its strip and the status bar behind the edges, with a 2 s notice; leaving puts everything back as it was,
 * and the circuit stays where it is on screen both ways.
 */
class FullScreenFeaturesTest {

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of());
        view = fixture.addTab();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Rectangle inRoot(Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), fixture.rootPane);
    }

    private boolean isOffScreen(Component component) {
        return !inRoot(component).intersects(new Rectangle(0, 0, WIDTH, HEIGHT));
    }

    private List<CanvasNotice> notices() {
        return java.util.Arrays.stream(fixture.rootPane.getLayeredPane().getComponents())
                .filter(CanvasNotice.class::isInstance).map(CanvasNotice.class::cast).toList();
    }

    @Test
    void enteringHidesEveryBarBehindTheEdgesAndTheStatusBar() {
        JMenuBar menuBar = view.menuBar();
        fixture.features.enter();
        fixture.layOut();
        assertThat(fixture.features.isActive()).isTrue();
        assertThat(fixture.screen.entered).isEqualTo(1);
        assertThat(fixture.rootPane.getJMenuBar()).as("not on the frame").isNull();
        assertThat(isOffScreen(menuBar)).as("menu bar").isTrue();
        assertThat(isOffScreen(view.toolBar())).as("toolbar").isTrue();
        assertThat(isOffScreen(view.leftColumn())).as("left column").isTrue();
        assertThat(isOffScreen(view.inspector().strip())).as("the right panel's strip").isTrue();
        assertThat(view.statusBar().isShowing() || view.statusBar().isVisible()).as("status bar").isFalse();
        assertThat(inRoot(view.bottomDock())).as("the canvas area takes the whole screen")
                .isEqualTo(new Rectangle(0, 0, WIDTH, HEIGHT));
    }

    @Test
    void leavingPutsEveryBarAndTheFrameBackAsTheyWere() {
        JMenuBar menuBar = fixture.rootPane.getJMenuBar();
        Rectangle leftColumn = inRoot(view.leftColumn());
        Rectangle toolBar = inRoot(view.toolBar());
        Rectangle strip = inRoot(view.inspector().strip());
        Rectangle statusBar = inRoot(view.statusBar());
        Rectangle canvas = inRoot(view.canvas());
        fixture.features.enter();
        fixture.layOut();
        fixture.features.leave();
        fixture.layOut();
        assertThat(fixture.features.isActive()).isFalse();
        assertThat(fixture.screen.left).isEqualTo(1);
        assertThat(fixture.rootPane.getJMenuBar()).isSameAs(menuBar);
        assertThat(view.toolBar().getParent()).isSameAs(fixture.tabs.toolBarSlot());
        assertThat(inRoot(view.leftColumn())).isEqualTo(leftColumn);
        assertThat(inRoot(view.toolBar())).isEqualTo(toolBar);
        assertThat(inRoot(view.inspector().strip())).isEqualTo(strip);
        assertThat(view.statusBar().isVisible()).isTrue();
        assertThat(inRoot(view.statusBar())).isEqualTo(statusBar);
        assertThat(inRoot(view.canvas())).isEqualTo(canvas);
    }

    @Test
    void theCircuitKeepsItsCentreAndZoomWhenEnteringAndLeaving() {
        view.canvas().applyViewState(new ViewState(1.5, 40, 25));
        ViewState before = view.canvas().viewState();
        fixture.features.enter();
        fixture.layOut();
        assertSameView(view.canvas().viewState(), before);
        fixture.features.leave();
        fixture.layOut();
        assertSameView(view.canvas().viewState(), before);
    }

    private static void assertSameView(ViewState shown, ViewState expected) {
        assertThat(shown.zoom()).isCloseTo(expected.zoom(), within(1e-9));
        assertThat(shown.centerX()).isCloseTo(expected.centerX(), within(1e-6));
        assertThat(shown.centerY()).isCloseTo(expected.centerY(), within(1e-6));
    }

    @Test
    void enteringShowsTheNoticeForTwoSeconds() {
        fixture.features.enter();
        assertThat(notices()).hasSize(1);
        assertThat(FullScreenFeatures.noticeText(fixture.preferences))
                .isEqualTo("Modo presentación · Esc o F11 para salir · lleva el puntero a un borde para ver sus barras");
        fixture.advance(1_900);
        assertThat(notices()).hasSize(1);
        fixture.advance(200);
        assertThat(notices()).isEmpty();
    }

    @Test
    void leavingTakesTheNoticeAwayAtOnce() {
        fixture.features.enter();
        fixture.features.leave();
        assertThat(notices()).isEmpty();
    }

    @Test
    void theActiveListenersHearEnteringAndLeaving() {
        AtomicInteger heard = new AtomicInteger();
        Runnable removal = fixture.features.addActiveListener(heard::incrementAndGet);
        fixture.features.toggle();
        fixture.features.toggle();
        assertThat(heard).hasValue(2);
        removal.run();
        fixture.features.toggle();
        assertThat(heard).hasValue(2);
    }

    @Test
    void aTabShownDuringTheModeIsLaidOutForItTooAndComesBackOnLeaving() {
        fixture.features.enter();
        EditorView second = fixture.addTab();
        assertThat(fixture.rootPane.getJMenuBar()).as("its menu bar stays behind the top edge").isNull();
        assertThat(isOffScreen(second.menuBar())).isTrue();
        assertThat(isOffScreen(second.leftColumn())).isTrue();
        assertThat(second.statusBar().isVisible()).isFalse();
        fixture.features.leave();
        fixture.layOut();
        assertThat(fixture.rootPane.getJMenuBar()).isSameAs(second.menuBar());
        assertThat(second.statusBar().isVisible()).isTrue();
        assertThat(view.statusBar().isVisible()).isTrue();
        assertThat(second.leftColumn().getParent()).isNotNull();
    }

    @Test
    void eachWindowHasItsOwnMode() {
        try (FullScreenFixture other = new FullScreenFixture(List.of())) {
            other.addTab();
            fixture.features.enter();
            assertThat(other.features.isActive()).isFalse();
            assertThat(other.rootPane.getJMenuBar()).isNotNull();
        }
    }

    @Test
    void theTopEdgeBringsTheMenuBarTabsAndToolbarOverTheCircuit() {
        fixture.features.enter();
        fixture.layOut();
        Rectangle canvas = inRoot(view.canvas());
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, 0));
        fixture.barTicker.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(inRoot(view.menuBar()).y).isZero();
        assertThat(isOffScreen(view.toolBar())).isFalse();
        assertThat(isOffScreen(fixture.tabs.toolBarSlot())).isFalse();
        assertThat(inRoot(view.canvas())).as("over the circuit, never pushing it").isEqualTo(canvas);
    }

    @Test
    void theLeftAndRightEdgesBringTheLeftColumnAndTheRightPanelWithItsStrip() {
        fixture.features.enter();
        fixture.layOut();
        fixture.features.edgeBars().pointerAt(new Point(0, HEIGHT / 2));
        fixture.barTicker.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(inRoot(view.leftColumn()).x).isZero();
        fixture.features.edgeBars().pointerAt(new Point(WIDTH - 1, HEIGHT / 2));
        fixture.barTicker.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        Rectangle strip = inRoot(view.inspector().strip());
        assertThat(strip.x + strip.width).isEqualTo(WIDTH);
    }

    @Test
    void theBottomEdgeDoesNothingWithoutTheWaveforms() {
        fixture.features.enter();
        fixture.layOut();
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, HEIGHT - 1));
        fixture.barTicker.advance(3_000);
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isFalse();
        assertThat(view.statusBar().isVisible()).as("the status bar never shows in the mode").isFalse();
    }

    @Test
    void aPictureOfTheWindowInTheModeHasNoneOfTheHiddenBars() {
        fixture.features.enter();
        fixture.layOut();
        JRootPane rootPane = fixture.rootPane;
        Rectangle picture = new Rectangle(0, 0, rootPane.getWidth(), rootPane.getHeight());
        for (JComponent bar : List.of(view.menuBar(), view.toolBar(), view.leftColumn(), view.inspector().strip())) {
            assertThat(inRoot(bar).intersects(picture)).as(bar.getClass().getSimpleName()).isFalse();
        }
        assertThat(view.statusBar().isVisible()).isFalse();
    }

    @Test
    void enteringMovesTheFocusToTheActiveCanvas() {
        List<Component> focused = new java.util.ArrayList<>();
        fixture.features.useFocusMover(focused::add);
        fixture.features.enter();
        assertThat(focused).containsExactly(view.canvas());
    }

    /**
     * The user saw the left column stay open on entering, with the focus left on the «Circuito» header: a focus that
     * was already in a bar does not open it, and it does not keep it open either.
     */
    @Test
    void aFocusLeftInTheLeftColumnWhenEnteringNeitherOpensItNorKeepsItOpen() {
        Component header = view.leftColumn().getComponent(0);
        fixture.features.useFocusMover(component -> { }); // the request is lost: the focus stays on the header
        fixture.features.edgeBars().useFocusOwner(() -> header);
        fixture.features.enter();
        fixture.layOut();
        fixture.features.edgeBars().focusChanged(); // the focus manager tells the column moved
        fixture.advance(3_000);
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.LEFT)).isFalse();
        fixture.features.edgeBars().pointerAt(new Point(0, HEIGHT / 2));
        fixture.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.LEFT)).isTrue();
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, HEIGHT / 2));
        fixture.advance(EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.LEFT)).as("a header does not keep it").isFalse();
    }

    /**
     * The picture of «Ventana» in the mode, painted as the capture paints it: along the top and left edges it is the
     * circuit, pixel for pixel, with none of the bars hidden behind those edges (task 4 could only check bounds).
     */
    @Test
    void aPaintedPictureOfTheWindowInTheModeShowsTheCircuitAtItsEdges() {
        assertThat(edgesShowTheCircuit()).as("outside the mode the menu bar and the left column are there").isFalse();
        fixture.features.enter();
        fixture.layOut();
        fixture.advance(FullScreenFeatures.NOTICE_DURATION.toMillis() + 1_000); // the notice on entering is gone
        fixture.layOut();
        assertThat(edgesShowTheCircuit()).isTrue();
    }

    /**
     * Whether the window's picture shows, along its top and left edges (past the dock's thin border), what the canvas
     * panes paint at those places of the screen.
     */
    private boolean edgesShowTheCircuit() {
        java.awt.image.BufferedImage window = cl.almejo.vsim.ui.capture.WindowCapture.paint(view,
                cl.almejo.vsim.ui.capture.CaptureKind.WINDOW, 1);
        java.awt.image.BufferedImage circuit = cl.almejo.vsim.ui.capture.WindowCapture.paint(view,
                cl.almejo.vsim.ui.capture.CaptureKind.VISIBLE_CIRCUIT, 1);
        Point offset = SwingUtilities.convertPoint(view.canvasPanes(), 0, 0, fixture.rootPane);
        int border = 3;
        int band = 40;
        for (int windowY = border; windowY < window.getHeight() - border; windowY++) {
            for (int windowX = border; windowX < window.getWidth() - border; windowX++) {
                boolean atAnEdge = windowY < band || windowX < band;
                if (atAnEdge && !samePixel(window, circuit, offset, windowX, windowY)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean samePixel(java.awt.image.BufferedImage window, java.awt.image.BufferedImage circuit,
                                     Point offset, int windowX, int windowY) {
        int circuitX = windowX - offset.x;
        int circuitY = windowY - offset.y;
        if (circuitX < 0 || circuitY < 0 || circuitX >= circuit.getWidth() || circuitY >= circuit.getHeight()) {
            return false; // something else is painted there
        }
        return (window.getRGB(windowX, windowY) & 0xFFFFFF) == (circuit.getRGB(circuitX, circuitY) & 0xFFFFFF);
    }

    /**
     * The final review's M14: the top bar's holder is outside the window's component tree while the mode is off, so a
     * theme change then never reached it; entering brings it up to the current look.
     */
    @Test
    void theTopBarTakesTheCurrentLookOnEntering() {
        java.awt.Color changed = new javax.swing.plaf.ColorUIResource(0x12, 0x34, 0x56);
        try {
            javax.swing.UIManager.put("Panel.background", changed); // a theme change while the mode is off
            fixture.features.enter();
            assertThat(view.menuBar().getParent().getBackground()).isEqualTo(changed);
        } finally {
            javax.swing.UIManager.put("Panel.background", null); // removes the override: the theme's own value again
        }
    }

    @Test
    void closingTheWindowInTheModeLeavesTheFullScreen() {
        fixture.features.enter();
        fixture.features.dispose();
        assertThat(fixture.features.isActive()).isFalse();
        assertThat(fixture.screen.closed).as("out of the full screen, with no restore for a window going away")
                .isEqualTo(1);
        assertThat(fixture.screen.left).as("no bounds or maximize to restore on a closing frame").isZero();
    }

    /** The final review's M1: the screen mode's timers and frame listeners go with the window, in the mode or not. */
    @Test
    void closingTheWindowOutsideTheModeAlsoClosesTheScreenMode() {
        fixture.features.enter();
        fixture.features.leave();
        fixture.features.dispose();
        assertThat(fixture.screen.left).isEqualTo(1);
        assertThat(fixture.screen.closed).isEqualTo(1);
    }
}
