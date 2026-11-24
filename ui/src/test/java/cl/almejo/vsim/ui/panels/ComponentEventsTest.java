package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import java.awt.event.ComponentListener;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentEventsTest {

    @Test
    void resizedRunsOnlyWhenResized() {
        int[] runs = {0};
        ComponentListener listener = ComponentEvents.onResized(() -> runs[0]++);

        listener.componentMoved(null);
        listener.componentShown(null);
        listener.componentHidden(null);
        assertThat(runs[0]).isZero();
        listener.componentResized(null);
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void movedOrResizedRunsOnBothAndNothingElse() {
        int[] runs = {0};
        ComponentListener listener = ComponentEvents.onMovedOrResized(() -> runs[0]++);

        listener.componentShown(null);
        listener.componentHidden(null);
        assertThat(runs[0]).isZero();
        listener.componentMoved(null);
        listener.componentResized(null);
        assertThat(runs[0]).isEqualTo(2);
    }

    @Test
    void theSameListenerCanBeRemoved() {
        javax.swing.JPanel panel = new javax.swing.JPanel();
        ComponentListener listener = ComponentEvents.onResized(() -> { });
        panel.addComponentListener(listener);
        panel.removeComponentListener(listener);
        assertThat(panel.getComponentListeners()).isEmpty();
    }

    @Test
    void hiddenRunsOnlyWhenHidden() {
        int[] runs = {0};
        ComponentListener listener = ComponentEvents.onHidden(() -> runs[0]++);

        listener.componentShown(null);
        listener.componentMoved(null);
        listener.componentResized(null);
        assertThat(runs[0]).isZero();
        listener.componentHidden(null);
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void shownOrHiddenRunsOnBothAndNothingElse() {
        int[] runs = {0};
        ComponentListener listener = ComponentEvents.onShownOrHidden(() -> runs[0]++);

        listener.componentMoved(null);
        listener.componentResized(null);
        assertThat(runs[0]).isZero();
        listener.componentShown(null);
        listener.componentHidden(null);
        assertThat(runs[0]).isEqualTo(2);
    }

    @Test
    void anyChosenChangeRunsAndTheOthersDoNot() {
        int[] runs = {0};
        ComponentListener listener = ComponentEvents.on(() -> runs[0]++, ComponentEvents.Change.RESIZED,
                ComponentEvents.Change.HIDDEN);

        listener.componentMoved(null);
        listener.componentShown(null);
        assertThat(runs[0]).isZero();
        listener.componentResized(null);
        listener.componentHidden(null);
        assertThat(runs[0]).isEqualTo(2);
    }
}
