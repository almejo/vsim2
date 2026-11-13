package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.CanvasClicks;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;

/** The signal card in a split view: each pane has its own, and lighting a net in one puts out the other's. */
class NetCardSplitViewTest {

    private static final long DELAY = 750;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private EditorFixture fixture;
    private NetCardFeatures features;
    private CircuitCanvas left;
    private CircuitCanvas right;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        NetCardCircuits.andExample(fixture.openDocument());
        CanvasPanes panes = fixture.view().canvasPanes();
        panes.setSize(1600, 600);
        panes.split(CanvasPanes.Orientation.VERTICAL, 0.5);
        panes.doLayout();
        left = fixture.view().panes().get(0);
        right = fixture.view().panes().get(1);
        left.applyViewState(new ViewState(1, 0, 0));
        right.applyViewState(new ViewState(1, 0, 0));
        features = new NetCardFeatures(fixture.view(), preferences,
                new NetCardFeatures.Setup(() -> ticker, ticker::now, () -> DELAY,
                        document -> SimulationSnapshot.EMPTY));
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    private PaneNetCard cardOf(CircuitCanvas pane) {
        return features.paneOf(pane).orElseThrow();
    }

    private void showTheCardIn(CircuitCanvas pane) {
        Point2D point = pane.viewport().gridToScreen(32.5, 10);
        CanvasClicks.move(pane, (int) Math.round(point.getX()), (int) Math.round(point.getY()));
        ticker.advance(DELAY);
    }

    private void pinTheCardIn(CircuitCanvas pane) {
        showTheCardIn(pane);
        SignalCard card = cardOf(pane).card().orElseThrow();
        MouseEvent entered = new MouseEvent(card, MouseEvent.MOUSE_ENTERED, 0, 0, 2, 2, 0, false);
        for (MouseListener listener : card.getMouseListeners()) {
            listener.mouseEntered(entered);
        }
        card.pinButton().doClick();
    }

    @Test
    void eachPaneHasItsOwnCard() {
        assertThat(features.paneOf(left)).isPresent();
        assertThat(features.paneOf(right)).isPresent();

        showTheCardIn(right);

        assertThat(cardOf(right).card()).hasValueSatisfying(card -> assertThat(card.getParent()).isSameAs(right));
        assertThat(cardOf(left).card()).isEmpty();
    }

    @Test
    void lightingANetInOnePanePutsOutTheUnpinnedCardOfTheOther() {
        showTheCardIn(left);

        showTheCardIn(right);

        assertThat(cardOf(left).card()).isEmpty();
        assertThat(cardOf(left).shown()).isEmpty();
        assertThat(cardOf(right).card()).isPresent();
    }

    @Test
    void aPinnedCardInOnePaneStaysWhileWorkingInTheOther() {
        pinTheCardIn(left);

        showTheCardIn(right);

        assertThat(cardOf(left).card()).hasValueSatisfying(card -> assertThat(card.isPinned()).isTrue());
        assertThat(cardOf(right).card()).isPresent();
    }
}
