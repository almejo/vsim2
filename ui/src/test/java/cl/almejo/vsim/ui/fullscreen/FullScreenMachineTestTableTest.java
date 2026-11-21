package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineMode;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
import cl.almejo.vsim.ui.machine.MachineTestTable;
import cl.almejo.vsim.ui.waveform.WaveformPanel;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.waveform.WaveformsToolbarExtension;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.HEIGHT;
import static cl.almejo.vsim.ui.fullscreen.FullScreenFixture.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine's test table in presentation mode (hito 4, fix round 2): it behaves exactly like the wave strip it
 * replaces in the dock: it waits behind the bottom edge and comes up when the pointer touches it, and it has the pin
 * (shown only in the mode, remembered for every view) that docks it.
 */
class FullScreenMachineTestTableTest {

    private static final TextPrompt NO_ANSWER = (parent, title, message, initialText) -> Optional.empty();
    private static final long OPEN_TIME = EdgeBars.DWELL.toMillis() + EdgeBars.SLIDE.toMillis() + 50;
    private static final long HIDE_TIME = EdgeBars.HIDE_DELAY.toMillis() + EdgeBars.SLIDE.toMillis() + 50;

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new WaveformsExtension(NO_ANSWER, (parent, message) -> { }),
                new WaveformsToolbarExtension(), new MachineModeExtension()));
        OpenDocument document = MachineFixtures.circuitWithGeneratedMachines(1);
        view = fixture.addComponentTab(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(0)));
        MachineMode.of(view).orElseThrow().startTest();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private MachineTestTable table() {
        return (MachineTestTable) view.bottomDock().content().orElseThrow();
    }

    private Rectangle inRoot(Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), fixture.rootPane);
    }

    private boolean isOnScreen(Component component) {
        for (Component current = component; current != fixture.rootPane; current = current.getParent()) {
            if (current == null || !current.isVisible()) {
                return false;
            }
        }
        return inRoot(component).intersects(new Rectangle(0, 0, WIDTH, HEIGHT));
    }

    private void enter() {
        fixture.features.enter();
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

    @Test
    void theTableTakesTheStripsPlaceInTheDock() {
        assertThat(view.bottomDock().content().orElseThrow()).isInstanceOf(MachineTestTable.class);
        assertThat(view.bottomDock().content().orElseThrow()).isNotInstanceOf(WaveformPanel.class);
    }

    @Test
    void theTableWaitsBehindTheBottomEdgeAndComesUpFromIt() {
        enter();
        assertThat(isOnScreen(table())).as("behind the edge at once").isFalse();

        restAtTheBottomEdge();

        assertThat(isOnScreen(table())).isTrue();
        Rectangle bounds = inRoot(table());
        assertThat(bounds.y + bounds.height).isEqualTo(HEIGHT);
    }

    @Test
    void thePinIsShownOnlyInTheMode() {
        assertThat(table().pinButton().isVisible()).isFalse();

        enter();
        assertThat(table().pinButton().isVisible()).isTrue();
        assertThat(table().pinButton().isSelected()).isFalse();

        fixture.features.leave();
        fixture.layOut();
        assertThat(table().pinButton().isVisible()).isFalse();
    }

    @Test
    void thePinDocksTheTableAndIsRememberedAsTheStripsPin() {
        enter();
        restAtTheBottomEdge();
        table().pinButton().doClick(0);
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();

        assertThat(fixture.preferences.fullScreenWaveformsPinned()).isTrue();
        assertThat(table().pinButton().isSelected()).isTrue();
        assertThat(isOnScreen(table())).as("docked: it no longer hides").isTrue();
    }

    @Test
    void anotherClickOnThePinLetsItGoBehindTheEdgeAgain() {
        enter();
        restAtTheBottomEdge();
        table().pinButton().doClick(0);
        fixture.layOut();
        table().pinButton().doClick(0);
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();

        assertThat(fixture.preferences.fullScreenWaveformsPinned()).isFalse();
        assertThat(table().pinButton().isSelected()).isFalse();
        assertThat(isOnScreen(table())).isFalse();
    }

    @Test
    void aPinnedStripStaysPinnedWhenTheTestBegins() {
        fixture.preferences.setFullScreenWaveformsPinned(true);
        enter();
        fixture.layOut();

        assertThat(table().pinButton().isSelected()).isTrue();
        assertThat(isOnScreen(table())).as("docked from the start, the canvas shorter").isTrue();
    }

    @Test
    void aTestThatBeginsInsideTheModeBehavesTheSame() {
        cl.almejo.vsim.ui.machine.MachineTestModes.leaveTheTest(view);
        enter();
        restAtTheBottomEdge();
        assertThat(view.bottomDock().content().orElseThrow()).isInstanceOf(WaveformPanel.class);

        MachineMode.of(view).orElseThrow().startTest();
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();
        assertThat(isOnScreen(table())).as("behind the edge, as the strip was").isFalse();
        assertThat(table().pinButton().isVisible()).isTrue();

        restAtTheBottomEdge();
        assertThat(isOnScreen(table())).as("the edge brings it up").isTrue();

        table().pinButton().doClick(0);
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();
        assertThat(isOnScreen(table())).as("pinned: docked").isTrue();
    }

    @Test
    void leavingTheTestPutsTheStripAndItsPinBack() {
        enter();
        table().pinButton().doClick(0);
        fixture.layOut();

        cl.almejo.vsim.ui.machine.MachineTestModes.leaveTheTest(view);
        fixture.layOut();

        WaveformPanel strip = (WaveformPanel) view.bottomDock().content().orElseThrow();
        assertThat(strip.pinButton().isVisible()).isTrue();
        assertThat(strip.pinButton().isSelected()).as("the pin is the same for the strip").isTrue();
    }
}
