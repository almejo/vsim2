package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.List;

import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.HEIGHT;
import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Presentation mode leaves nothing behind (the user, 2026-11-08: after using the mode, the menus of the menu bar
 * closed by themselves): no pointer, focus or menu listener and no timer of the mode stays installed after leaving,
 * the menu bar is back on the frame as it was, and a menu opened from the top bar inside the mode, or from the menu
 * bar after leaving (also after a second round trip), stays open.
 */
class FullScreenLeavesNothingTest {

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new FullScreenExtension()));
        view = fixture.addTab();
    }

    @AfterEach
    void close() {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        fixture.close();
    }

    /** Everything global a component can hook into, counted. */
    private record Hooks(int pointerListeners, int focusListeners, int menuListeners, List<Component> layers) {

        static Hooks now(FullScreenFixture fixture) {
            return new Hooks(Toolkit.getDefaultToolkit().getAWTEventListeners().length,
                    KeyboardFocusManager.getCurrentKeyboardFocusManager()
                            .getPropertyChangeListeners("focusOwner").length,
                    MenuSelectionManager.defaultManager().getChangeListeners().length,
                    Arrays.asList(fixture.rootPane.getLayeredPane().getComponents()));
        }
    }

    private void roundTrip() {
        fixture.features.enter();
        fixture.layOut();
        fixture.advance(3_000);
        fixture.features.leave();
        fixture.layOut();
    }

    /** Opens the first menu of {@code menuBar} as a click does, and lets the mode's frames run. */
    private boolean openedMenuStaysOpen(JMenuBar menuBar) {
        JMenu menu = menuBar.getMenu(0);
        MenuElement[] path = {menuBar, menu, menu.getPopupMenu()};
        MenuSelectionManager.defaultManager().setSelectedPath(path);
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, HEIGHT / 2)); // on the popup, over the canvas
        fixture.features.edgeBars().focusChanged();
        fixture.advance(2_000);
        boolean open = Arrays.equals(MenuSelectionManager.defaultManager().getSelectedPath(), path);
        MenuSelectionManager.defaultManager().clearSelectedPath();
        return open;
    }

    @Test
    void leavingTakesBackEveryListenerTimerAndLayerOfTheMode() {
        Hooks before = Hooks.now(fixture);
        roundTrip();
        assertThat(Hooks.now(fixture)).isEqualTo(before);
        assertThat(fixture.barTicker.isRunning()).as("the bars' ticker").isFalse();
        assertThat(fixture.noticeTicker.isRunning()).as("the notice's ticker").isFalse();
        roundTrip();
        assertThat(Hooks.now(fixture)).as("after a second round trip").isEqualTo(before);
    }

    @Test
    void theMenuBarIsBackOnTheFrameAsItWas() {
        JMenuBar menuBar = fixture.rootPane.getJMenuBar();
        Component parent = menuBar.getParent();
        roundTrip();
        assertThat(fixture.rootPane.getJMenuBar()).isSameAs(menuBar);
        assertThat(menuBar.getParent()).isSameAs(parent);
        assertThat(menuBar.isVisible()).isTrue();
        assertThat(menuBar.getComponentCount()).isEqualTo(view.menuBar().getComponentCount());
    }

    @Test
    void aMenuOpenedAfterLeavingStaysOpen() {
        assertThat(openedMenuStaysOpen(view.menuBar())).as("before the mode").isTrue();
        roundTrip();
        assertThat(openedMenuStaysOpen(view.menuBar())).as("after leaving").isTrue();
        roundTrip();
        assertThat(openedMenuStaysOpen(view.menuBar())).as("after a second round trip").isTrue();
    }

    @Test
    void aMenuOpenedFromTheTopBarInsideTheModeStaysOpenAndKeepsTheBar() {
        fixture.features.enter();
        fixture.layOut();
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, 0));
        fixture.advance(EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50);
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.TOP)).isTrue();
        assertThat(openedMenuStaysOpenKeepingTheBar(view.menuBar())).isTrue();
    }

    private boolean openedMenuStaysOpenKeepingTheBar(JMenuBar menuBar) {
        JMenu menu = menuBar.getMenu(0);
        MenuElement[] path = {menuBar, menu, menu.getPopupMenu()};
        MenuSelectionManager.defaultManager().setSelectedPath(path);
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, HEIGHT / 2));
        fixture.advance(2_000);
        return Arrays.equals(MenuSelectionManager.defaultManager().getSelectedPath(), path)
                && fixture.features.edgeBars().isShown(EdgeBars.Edge.TOP);
    }
}
