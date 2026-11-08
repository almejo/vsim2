package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bars behind the screen edges in presentation mode: a bar opens only after the pointer stays a second at its
 * edge, slides in, and hides 0.4 s after the pointer leaves it, never while one of its menus is open or a field of it
 * has the focus. Driven by a hand ticker; never a shown window.
 */
class EdgeBarsTest {

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int TOP_HEIGHT = 60;
    private static final int LEFT_WIDTH = 230;

    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final AtomicBoolean animate = new AtomicBoolean(true);
    private final AtomicReference<Component> focusOwner = new AtomicReference<>();
    private JRootPane area;
    private EdgeBars bars;
    private JPanel top;
    private JPanel left;

    @BeforeEach
    void create() {
        area = new JRootPane();
        area.setSize(WIDTH, HEIGHT);
        area.doLayout();
        area.getLayeredPane().setSize(WIDTH, HEIGHT);
        bars = new EdgeBars(area, ticker, ticker::now, animate::get);
        bars.useFocusOwner(focusOwner::get);
        top = bar(new Dimension(WIDTH, TOP_HEIGHT));
        left = bar(new Dimension(LEFT_WIDTH, HEIGHT));
    }

    @AfterEach
    void clearMenus() {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        bars.dispose();
    }

    private JPanel bar(Dimension size) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setPreferredSize(size);
        area.getLayeredPane().add(bar);
        return bar;
    }

    private void atTheTopEdge() {
        bars.pointerAt(new Point(WIDTH / 2, 0));
    }

    private void inTheMiddle() {
        bars.pointerAt(new Point(WIDTH / 2, HEIGHT / 2));
    }

    private void openTheTopBar() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        atTheTopEdge();
        ticker.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 32);
        assertThat(top.getY()).as("fully in").isZero();
    }

    @Test
    void aHiddenBarLiesBeyondItsEdge() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        assertThat(top.getY() + top.getHeight()).isLessThanOrEqualTo(0);
        assertThat(top.getWidth()).isEqualTo(WIDTH);
        assertThat(left.getX() + left.getWidth()).isLessThanOrEqualTo(0);
        assertThat(left.getHeight()).isEqualTo(HEIGHT);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
    }

    @Test
    void theBarOpensOnlyAfterThePointerStaysASecondAtItsEdge() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        atTheTopEdge();
        ticker.advance(EdgeBars.DWELL.toMillis() - 100);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
        assertThat(top.getY() + top.getHeight()).isLessThanOrEqualTo(0);
        ticker.advance(132);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isTrue();
    }

    @Test
    void theBarSlidesInOverAboutAFifthOfASecond() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        atTheTopEdge();
        ticker.advance(EdgeBars.DWELL.toMillis() + 16);
        ticker.advance(EdgeBars.SLIDE.toMillis() / 2);
        assertThat(top.getY()).as("half way").isStrictlyBetween(-TOP_HEIGHT, 0);
        ticker.advance(EdgeBars.SLIDE.toMillis());
        assertThat(top.getY()).isZero();
    }

    @Test
    void withoutAnimationsTheBarAppearsAtOnce() {
        animate.set(false);
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        bars.pointerAt(new Point(0, HEIGHT / 2));
        ticker.advance(EdgeBars.DWELL.toMillis() + 16);
        assertThat(left.getX()).isZero();
    }

    @Test
    void crossingTheEdgeQuicklyNeverOpensIt() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        atTheTopEdge();
        ticker.advance(600);
        inTheMiddle();
        ticker.advance(100);
        atTheTopEdge();
        ticker.advance(600);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
    }

    @Test
    void theBarStaysWhileThePointerRestsAtItsEdge() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        atTheTopEdge();
        ticker.advance(EdgeBars.DWELL.toMillis() + 3_000); // the pointer never moves again
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isTrue();
    }

    @Test
    void aDragThatReachesTheEdgeNeverOpensItsBar() {
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        bars.pointerDragged(new Point(0, HEIGHT / 2)); // a wire drawn to the border
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isFalse();
    }

    @Test
    void thePointerLeavingTheWindowCancelsTheWait() {
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        bars.pointerAt(new Point(0, HEIGHT / 2));
        ticker.advance(500);
        bars.pointerLeft(); // to a second monitor beside this one
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isFalse();
    }

    @Test
    void theBarHidesFourHundredMillisecondsAfterThePointerLeavesIt() {
        openTheTopBar();
        bars.pointerAt(new Point(WIDTH / 2, TOP_HEIGHT / 2)); // on the bar
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isTrue();
        inTheMiddle();
        ticker.advance(EdgeBars.HIDE_DELAY.toMillis() - 50);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isTrue();
        ticker.advance(66 + EdgeBars.SLIDE.toMillis() + 32);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
        assertThat(top.getY() + top.getHeight()).isLessThanOrEqualTo(0);
        assertThat(ticker.isRunning()).as("nothing left to do").isFalse();
    }

    @Test
    void theBarStaysWhileOneOfItsMenusIsOpenAndHidesAfterItClosesElsewhere() {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Archivo");
        menuBar.add(menu);
        top.add(menuBar, BorderLayout.NORTH);
        openTheTopBar();
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menuBar, menu});
        inTheMiddle();
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isTrue();
        MenuSelectionManager.defaultManager().clearSelectedPath(); // closed by a click on the canvas
        ticker.advance(EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
    }

    @Test
    void theBarStaysWhileAFieldOfItHasTheFocus() {
        JTextField field = new JTextField();
        top.add(field, BorderLayout.CENTER);
        openTheTopBar();
        focusOwner.set(field);
        inTheMiddle();
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isTrue();
        focusOwner.set(null);
        ticker.advance(EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
    }

    /** Only a text field keeps a bar open (the user saw the left column stay open with the «Circuito» header focused). */
    @Test
    void aTreeOrAHeaderWithTheFocusDoesNotKeepTheBarOpen() {
        JTree tree = new JTree();
        left.add(tree, BorderLayout.CENTER);
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        bars.pointerAt(new Point(0, HEIGHT / 2));
        ticker.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 32);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isTrue();
        focusOwner.set(tree);
        inTheMiddle();
        ticker.advance(EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isFalse();
    }

    /** The focus already in a bar when the mode starts (left on the «Circuito» header) does not open it. */
    @Test
    void theFocusAlreadyInABarWhenFollowingStartsDoesNotOpenIt() {
        JTree tree = new JTree();
        JTextField field = new JTextField();
        left.add(tree, BorderLayout.CENTER);
        left.add(field, BorderLayout.NORTH);
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        focusOwner.set(tree);
        bars.startFollowing();
        try {
            bars.focusChanged();
            focusOwner.set(null); // the focus goes away for a moment while the column moves
            bars.focusChanged();
            focusOwner.set(tree);
            bars.focusChanged();
            ticker.advance(2_000);
            assertThat(bars.isShown(EdgeBars.Edge.LEFT)).as("never moved into it").isFalse();
            focusOwner.set(field); // Tab inside the hidden column: still not a move into it
            bars.focusChanged();
            ticker.advance(2_000);
            assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isFalse();
            JTextField outside = new JTextField();
            focusOwner.set(outside); // the canvas, say
            bars.focusChanged();
            focusOwner.set(field); // Alt+2: now it moves into the bar
            bars.focusChanged();
            ticker.advance(EdgeBars.SLIDE.toMillis() + 32);
            assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isTrue();
        } finally {
            bars.stopFollowing();
        }
    }

    @Test
    void theBarStaysWhileItsOwnConditionHolds() {
        AtomicBoolean keepOpen = new AtomicBoolean(true);
        bars.register(EdgeBars.Edge.LEFT, left, keepOpen::get);
        bars.pointerAt(new Point(0, HEIGHT / 2));
        ticker.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 32);
        inTheMiddle();
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isTrue();
        keepOpen.set(false);
        ticker.advance(EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isFalse();
    }

    @Test
    void theFocusGoingIntoAHiddenBarOpensItAtOnce() {
        JTextField field = new JTextField();
        left.add(field, BorderLayout.NORTH);
        bars.register(EdgeBars.Edge.LEFT, left, () -> false);
        focusOwner.set(field);
        bars.focusChanged(); // Alt+2 gives the palette the focus
        ticker.advance(EdgeBars.SLIDE.toMillis() + 32);
        assertThat(left.getX()).isZero();
    }

    @Test
    void anEdgeWithoutABarDoesNothing() {
        bars.register(EdgeBars.Edge.TOP, top, () -> false);
        bars.pointerAt(new Point(WIDTH / 2, HEIGHT - 1));
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.BOTTOM)).isFalse();
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
    }

    @Test
    void aBarOfAHiddenTabIsNotOpened() {
        JPanel hiddenCard = new JPanel(null);
        hiddenCard.setBounds(0, 0, WIDTH, HEIGHT);
        hiddenCard.setVisible(false);
        area.getLayeredPane().add(hiddenCard);
        JComponent hiddenBar = new JPanel();
        hiddenBar.setPreferredSize(new Dimension(LEFT_WIDTH, HEIGHT));
        hiddenCard.add(hiddenBar);
        bars.register(EdgeBars.Edge.LEFT, hiddenBar, () -> false);
        bars.pointerAt(new Point(0, HEIGHT / 2));
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.LEFT)).isFalse();
    }

    @Test
    void theRemovalTakesTheBarAway() {
        Runnable removal = bars.register(EdgeBars.Edge.TOP, top, () -> false);
        removal.run();
        atTheTopEdge();
        ticker.advance(2_000);
        assertThat(bars.isShown(EdgeBars.Edge.TOP)).isFalse();
    }

    @Test
    void aBarFollowsTheSizeOfItsParent() {
        openTheTopBar();
        area.getLayeredPane().setSize(1200, HEIGHT);
        bars.relayout();
        assertThat(top.getWidth()).isEqualTo(1200);
        assertThat(top.getY()).isZero();
    }
}
