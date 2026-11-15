package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
import cl.almejo.vsim.ui.waveform.WaveformPanel;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.waveform.WaveformsToolbarExtension;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
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

/**
 * A machine tab's wave strip in presentation mode (hito 2, task 6): it is the view's waveform panel, so it waits
 * behind the bottom edge and comes up from it, its «Ocultar» slides it away and the edge keeps bringing it (the copy
 * always has channels, though the machine's body has no probes), and its pin docks it.
 */
class FullScreenMachineStripTest {

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
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private WaveformPanel strip() {
        return (WaveformPanel) view.bottomDock().content().orElseThrow();
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

    @Test
    void theStripComesUpFromTheBottomEdge() {
        assertThat(strip().channelNames()).containsExactly("x", "estado", "Z");
        enter();
        assertThat(isOnScreen(strip())).as("behind the edge at once").isFalse();
        assertThat(strip().pinButton().isVisible()).isTrue();

        restAtTheBottomEdge();

        assertThat(isOnScreen(strip())).isTrue();
        Rectangle bounds = inRoot(strip());
        assertThat(bounds.y + bounds.height).isEqualTo(HEIGHT);
    }

    @Test
    void itsHideSlidesItAwayAndTheEdgeStillBringsIt() {
        enter();
        restAtTheBottomEdge();
        pointerToTheMiddle();
        findButton(strip(), "Ocultar").orElseThrow().doClick(0);
        fixture.barTicker.advance(EdgeBars.SLIDE.toMillis() + 50);
        fixture.layOut();
        assertThat(isOnScreen(strip())).isFalse();

        restAtTheBottomEdge();

        assertThat(isOnScreen(strip())).as("the copy's channels keep the edge awake").isTrue();
    }

    @Test
    void itsPinDocksIt() {
        enter();
        restAtTheBottomEdge();
        strip().pinButton().doClick(0);
        fixture.layOut();
        pointerToTheMiddle();
        fixture.barTicker.advance(HIDE_TIME);
        fixture.layOut();

        assertThat(fixture.preferences.fullScreenWaveformsPinned()).isTrue();
        assertThat(isOnScreen(strip())).as("docked: it no longer hides").isTrue();
    }

    @Test
    void closedWithVerFormasDeOndaTheEdgeStillBringsTheStrip() {
        view.bottomDock().hideDock();
        enter();

        restAtTheBottomEdge();

        assertThat(isOnScreen(strip())).isTrue();
    }
}
