package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.WaveformPanel;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.HEIGHT;
import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The waveform panel in presentation mode: unpinned it hides behind the bottom edge at once and comes up over the
 * circuit after the dwell; its pin (shown only in the mode, remembered) docks it with the canvas shorter; leaving
 * puts the dock back as tall as it was before entering, open or closed as it was. The bottom edge brings the panel
 * up whenever the circuit has a probe, even with the panel closed (the user, 2026-11-08); with no probes and no panel
 * open it does nothing.
 */
class FullScreenWaveformsTest {

    private static final TextPrompt NO_ANSWER = (parent, title, message, initialText) -> Optional.empty();
    private static final long OPEN_TIME = EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50;
    private static final long HIDE_TIME = EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50;

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new WaveformsExtension(NO_ANSWER, (parent, message) -> { })));
        view = fixture.addTab();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private BottomDock dock() {
        return view.bottomDock();
    }

    private WaveformPanel panel() {
        return (WaveformPanel) dock().content().orElseThrow();
    }

    private Rectangle inRoot(Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), fixture.rootPane);
    }

    /** Visible all the way up and placed inside the screen. */
    private boolean isOnScreen(Component component) {
        for (Component current = component; current != fixture.rootPane; current = current.getParent()) {
            if (current == null || !current.isVisible()) {
                return false;
            }
        }
        return inRoot(component).intersects(new Rectangle(0, 0, WIDTH, HEIGHT));
    }

    /** The canvas area's height (the dock's top part): the whole screen when nothing is docked under it. */
    private int canvasAreaHeight() {
        return inRoot(dock().getTopComponent()).height;
    }

    /** The canvas area's height with nothing docked under it in the mode: the screen less the dock's border. */
    private int wholeHeight() {
        return HEIGHT - dock().getInsets().top - dock().getInsets().bottom;
    }

    /** The bottom of {@code component}, the dock's border allowed for. */
    private void assertAtTheBottom(Component component) {
        Rectangle bounds = inRoot(component);
        assertThat(bounds.y + bounds.height).isCloseTo(HEIGHT, within(2));
    }

    private void openThePanel() {
        dock().showDock();
        fixture.layOut();
    }

    private void enter() {
        fixture.features.enter();
        fixture.layOut();
    }

    private void leave() {
        fixture.features.leave();
        fixture.layOut();
    }

    private void restAtTheBottomEdge() {
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, HEIGHT - 1));
        fixture.barTicker.advance(OPEN_TIME);
        fixture.layOut();
    }

    private void pointerToTheMiddle() {
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, HEIGHT / 2));
    }

    /** A probe on a pin of a part the empty circuit does not have: enough for the panel to have a channel. */
    private void addAProbe() {
        view.openDocument().document().setProbes(
                List.of(new Probe("p1", new ProbeTarget.PinTarget(new ElementId("u1"), 0), "a")));
    }

    /** The panel's own «Ocultar» button. */
    private AbstractButton hideButton() {
        return findButton(panel(), "Ocultar").orElseThrow();
    }

    private static Optional<AbstractButton> findButton(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && name.equals(button.getAction().getValue(Action.NAME))) {
                return Optional.of(button);
            }
            if (child instanceof Container inner) {
                Optional<AbstractButton> found = findButton(inner, name);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private JMenuItem waveformsItem() {
        for (Component item : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (item instanceof JMenuItem menuItem && "Formas de onda".equals(menuItem.getText())) {
                return menuItem;
            }
        }
        throw new AssertionError("no Ver ▸ Formas de onda");
    }

    @Test
    void thePinShowsOnlyInTheMode() {
        openThePanel();
        assertThat(panel().pinButton().isVisible()).isFalse();
        enter();
        assertThat(panel().pinButton().isVisible()).isTrue();
        leave();
        assertThat(panel().pinButton().isVisible()).isFalse();
    }

    @Test
    void enteringWithThePanelOpenAndUnpinnedHidesItBehindTheBottomEdgeAtOnce() {
        openThePanel();
        enter();
        assertThat(isOnScreen(panel())).as("hidden at once, nothing to drag").isFalse();
        assertThat(canvasAreaHeight()).as("the canvas takes the whole height").isEqualTo(wholeHeight());
        assertThat(dock().isDockShowing()).as("still open: Ver ▸ Formas de onda stays checked").isTrue();
    }

    @Test
    void theBottomEdgeBringsThePanelOverTheCircuitFullWidth() {
        openThePanel();
        enter();
        Rectangle canvas = inRoot(view.canvas());
        restAtTheBottomEdge();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isTrue();
        Rectangle panel = inRoot(panel());
        assertThat(panel.y + panel.height).isEqualTo(HEIGHT);
        assertThat(panel.width).isEqualTo(WIDTH);
        assertThat(inRoot(view.canvas())).as("over the circuit, never pushing it").isEqualTo(canvas);
    }

    @Test
    void pinnedThePanelStaysDockedAndTheCanvasIsShorter() {
        fixture.preferences.setFullScreenWaveformsPinned(true);
        openThePanel();
        enter();
        assertThat(panel().pinButton().isSelected()).isTrue();
        assertAtTheBottom(panel());
        assertThat(canvasAreaHeight()).isLessThanOrEqualTo(HEIGHT - inRoot(panel()).height);
        restAtTheBottomEdge();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).as("nothing behind the edge").isFalse();
    }

    @Test
    void clickingThePinDocksThePanelAndIsRemembered() {
        openThePanel();
        enter();
        restAtTheBottomEdge();
        panel().pinButton().doClick(0);
        fixture.layOut();
        assertThat(fixture.preferences.fullScreenWaveformsPinned()).isTrue();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();
        assertThat(isOnScreen(panel())).as("docked: it no longer hides").isTrue();
        assertThat(canvasAreaHeight()).isLessThan(wholeHeight());
    }

    @Test
    void removingThePinHidesThePanelWhenThePointerLeavesIt() {
        fixture.preferences.setFullScreenWaveformsPinned(true);
        openThePanel();
        enter();
        Rectangle docked = inRoot(panel());
        fixture.features.edgeBars().pointerAt(new Point(WIDTH / 2, docked.y + docked.height / 2));
        panel().pinButton().doClick(0);
        fixture.layOut();
        assertThat(fixture.preferences.fullScreenWaveformsPinned()).isFalse();
        assertThat(canvasAreaHeight()).isEqualTo(wholeHeight());
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();
        assertThat(isOnScreen(panel())).as("stays while the pointer is on it").isTrue();
        assertAtTheBottom(panel());
        pointerToTheMiddle();
        fixture.barTicker.advance(EdgeBars.HIDE_DELAY.toMillis() - 50);
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isTrue();
        fixture.barTicker.advance(EdgeBars.SLIDE.toMillis() + 100);
        fixture.layOut();
        assertThat(isOnScreen(panel())).isFalse();
    }

    @Test
    void leavingPutsThePanelBackInTheDockAsTallAsBefore() {
        openThePanel();
        int before = inRoot(panel()).height;
        Rectangle canvasBefore = inRoot(view.canvas());
        enter();
        restAtTheBottomEdge();
        leave();
        assertThat(SwingUtilities.isDescendingFrom(panel(), dock())).isTrue();
        assertThat(isOnScreen(panel())).isTrue();
        assertThat(inRoot(panel()).height).isEqualTo(before);
        assertThat(inRoot(view.canvas())).isEqualTo(canvasBefore);
    }

    @Test
    void leavingRestoresTheHeightOfBeforeEvenAfterDraggingTheDividerInTheMode() {
        fixture.preferences.setFullScreenWaveformsPinned(true);
        openThePanel();
        int before = inRoot(panel()).height;
        enter();
        dock().setDividerLocation(HEIGHT / 3);
        fixture.layOut();
        assertThat(inRoot(panel()).height).as("dragged in the mode").isNotEqualTo(before);
        leave();
        assertThat(inRoot(panel()).height).isEqualTo(before);
    }

    @Test
    void closingThePanelOfACircuitWithoutProbesLeavesTheBottomEdgeInert() {
        openThePanel();
        enter();
        restAtTheBottomEdge();
        dock().hideDock();
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        restAtTheBottomEdge();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isFalse();
        assertThat(isOnScreen(panel())).isFalse();
        leave();
        assertThat(dock().isDockShowing()).as("open, as it was before entering").isTrue();
        assertThat(SwingUtilities.isDescendingFrom(panel(), dock())).as("back in the dock").isTrue();
    }

    @Test
    void withNoProbesAndNoPanelOpenTheBottomEdgeDoesNothing() {
        enter();
        restAtTheBottomEdge();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isFalse();
    }

    @Test
    void openingThePanelInTheModeBringsItUpFromTheEdge() {
        enter();
        waveformsItem().doClick(0);
        fixture.barTicker.advance(EdgeBars.SLIDE.toMillis() + 50);
        fixture.layOut();
        assertThat(dock().isDockShowing()).isTrue();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isTrue();
        assertThat(isOnScreen(panel())).isTrue();
        assertThat(canvasAreaHeight()).as("over the circuit").isEqualTo(wholeHeight());
    }

    @Test
    void thePinFollowsInEveryTabLaidOutForTheMode() {
        openThePanel();
        EditorView first = view;
        enter();
        view = fixture.addTab();
        openThePanel();
        fixture.layOut();
        panel().pinButton().doClick(0);
        assertThat(((WaveformPanel) first.bottomDock().content().orElseThrow()).pinButton().isSelected()).isTrue();
    }

    @Test
    void withProbesTheBottomEdgeBringsThePanelEvenIfItWasClosed() {
        addAProbe();
        dock().hideDock();
        fixture.layOut();
        enter();
        assertThat(isOnScreen(panel())).isFalse();
        restAtTheBottomEdge();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isTrue();
        assertThat(isOnScreen(panel())).isTrue();
        assertAtTheBottom(panel());
    }

    @Test
    void theHideButtonSlidesThePanelAwayAndTheEdgeKeepsWorking() {
        addAProbe();
        openThePanel();
        enter();
        restAtTheBottomEdge();
        pointerToTheMiddle();
        hideButton().doClick(0);
        fixture.barTicker.advance(EdgeBars.SLIDE.toMillis() + 50);
        fixture.layOut();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isFalse();
        assertThat(isOnScreen(panel())).isFalse();
        assertThat(view.openDocument().document().probes()).as("the probes stay").hasSize(1);
        restAtTheBottomEdge();
        assertThat(isOnScreen(panel())).as("the edge still brings it").isTrue();
    }

    @Test
    void leavingPutsThePanelBackClosedWhenItWasClosedBeforeEntering() {
        addAProbe();
        enter();
        restAtTheBottomEdge();
        leave();
        assertThat(dock().isDockShowing()).isFalse();
        assertThat(SwingUtilities.isDescendingFrom(panel(), dock())).isTrue();
        assertThat(isOnScreen(panel())).isFalse();
    }

    @Test
    void pinnedTheClosedPanelOfACircuitWithProbesIsDockedInTheModeAndClosedAfterIt() {
        fixture.preferences.setFullScreenWaveformsPinned(true);
        addAProbe();
        enter();
        assertThat(isOnScreen(panel())).isTrue();
        assertAtTheBottom(panel());
        assertThat(canvasAreaHeight()).isLessThan(wholeHeight());
        leave();
        assertThat(dock().isDockShowing()).isFalse();
    }

    @Test
    void theFirstProbeAddedInTheModeWakesTheBottomEdge() {
        enter();
        addAProbe();
        restAtTheBottomEdge();
        assertThat(fixture.features.edgeBars().isShown(EdgeBars.Edge.BOTTOM)).isTrue();
        assertThat(isOnScreen(panel())).isTrue();
    }

    @Test
    void hidingAPinnedPanelPutsItBehindTheEdgeForNowAndKeepsThePinRemembered() {
        fixture.preferences.setFullScreenWaveformsPinned(true);
        addAProbe();
        openThePanel();
        enter();
        pointerToTheMiddle();
        hideButton().doClick(0);
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();
        assertThat(isOnScreen(panel())).isFalse();
        assertThat(canvasAreaHeight()).isEqualTo(wholeHeight());
        assertThat(panel().pinButton().isSelected()).as("it is not pinned now").isFalse();
        assertThat(fixture.preferences.fullScreenWaveformsPinned()).as("remembered for next time").isTrue();
        restAtTheBottomEdge();
        assertThat(isOnScreen(panel())).isTrue();
        panel().pinButton().doClick(0);
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();
        assertThat(isOnScreen(panel())).as("pinned again: docked").isTrue();
        assertThat(canvasAreaHeight()).isLessThan(wholeHeight());
    }
}
