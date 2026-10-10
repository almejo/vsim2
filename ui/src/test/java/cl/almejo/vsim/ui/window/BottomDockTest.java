package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BottomDockTest {

    private final JPanel top = new JPanel();
    private final JLabel content = new JLabel("panel");
    private final BottomDock dock = new BottomDock(top);

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layout(inner);
            }
        }
    }

    private int fullHeight() {
        return dock.getHeight() - dock.getInsets().top - dock.getInsets().bottom;
    }

    @Test
    void anEmptyDockIsHiddenAndTheTopTakesAllTheHeight() {
        dock.setSize(800, 600);
        layout(dock);
        assertThat(dock.isDockShowing()).isFalse();
        assertThat(dock.getDividerSize()).isZero();
        assertThat(dock.getTopComponent()).isSameAs(top);
        assertThat(top.getHeight()).isEqualTo(fullHeight());
        assertThat(dock.content()).isEmpty();
    }

    @Test
    void showingGivesTheContentTheDefaultHeightAndHidingGivesItBack() {
        List<Boolean> events = new ArrayList<>();
        dock.addDockListener(() -> events.add(dock.isDockShowing()));
        dock.setContent(content);
        dock.setSize(800, 600);
        layout(dock);

        dock.showDock();
        dock.showDock();
        layout(dock);
        assertThat(dock.getBottomComponent().getHeight()).isEqualTo(BottomDock.DEFAULT_HEIGHT);
        assertThat(content.getHeight()).isEqualTo(BottomDock.DEFAULT_HEIGHT);
        assertThat(dock.getDividerSize()).isPositive();

        dock.hideDock();
        layout(dock);
        assertThat(top.getHeight()).isEqualTo(fullHeight());
        assertThat(events).containsExactly(true, false);
    }

    @Test
    void theDockKeepsTheHeightTheUserGaveIt() {
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);
        dock.setDividerLocation(300);
        layout(dock);
        int chosen = dock.getBottomComponent().getHeight();
        assertThat(chosen).isNotEqualTo(BottomDock.DEFAULT_HEIGHT);

        dock.hideDock();
        layout(dock);
        dock.showDock();
        layout(dock);

        assertThat(dock.getBottomComponent().getHeight()).isEqualTo(chosen);
    }

    @Test
    void newContentReplacesTheOldAndListenersCanBeRemoved() {
        List<String> calls = new ArrayList<>();
        Runnable listener = () -> calls.add("dock");
        dock.addDockListener(listener);
        dock.removeDockListener(listener);
        dock.setContent(content);
        JLabel other = new JLabel("other");
        dock.setContent(other);
        assertThat(dock.content()).containsSame(other);
        dock.setContent(null);
        assertThat(dock.content()).isEmpty();
        dock.showDock();
        assertThat(calls).isEmpty();
    }
}
