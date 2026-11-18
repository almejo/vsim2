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

    @Test
    void lendingTheContentKeepsTheDockOpenAndGivesTheTopAllTheHeight() {
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);
        assertThat(dock.lendContent()).containsSame(content);
        layout(dock);
        assertThat(dock.isDockShowing()).isTrue();
        assertThat(dock.isContentLent()).isTrue();
        assertThat(dock.content()).as("still the dock's content").containsSame(content);
        assertThat(dock.getDividerSize()).isZero();
        assertThat(top.getHeight()).isEqualTo(fullHeight());
        assertThat(dock.lendContent()).as("already lent").isEmpty();
    }

    @Test
    void returningTheContentOpensItAsTallAsBefore() {
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);
        dock.lendContent();
        JPanel elsewhere = new JPanel();
        elsewhere.add(content);
        layout(dock);
        dock.returnContent();
        layout(dock);
        assertThat(content.getParent()).isNotSameAs(elsewhere);
        assertThat(content.getHeight()).isEqualTo(BottomDock.DEFAULT_HEIGHT);
        assertThat(dock.isContentLent()).isFalse();
    }

    @Test
    void theDockHeightSetIsTheOneTheDockedPanelTakes() {
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);
        dock.setDockHeight(150);
        layout(dock);
        assertThat(content.getHeight()).isEqualTo(150);
        assertThat(dock.dockHeight()).isEqualTo(150);
    }

    @Test
    void anotherComponentShowsInsteadOfTheContentUntilItIsRemoved() {
        JLabel table = new JLabel("table");
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);

        Runnable remove = dock.contentInstead(table);
        layout(dock);

        assertThat(dock.content()).containsSame(table);
        assertThat(table.getParent()).isNotNull();
        assertThat(content.getParent()).as("set aside").isNull();
        assertThat(table.getHeight()).isEqualTo(BottomDock.DEFAULT_HEIGHT);
        assertThat(dock.isDockShowing()).isTrue();

        remove.run();
        layout(dock);

        assertThat(dock.content()).containsSame(content);
        assertThat(table.getParent()).isNull();
        assertThat(content.getHeight()).isEqualTo(BottomDock.DEFAULT_HEIGHT);
    }

    @Test
    void newContentSetMeanwhileShowsOnceTheOtherComponentIsRemoved() {
        JLabel table = new JLabel("table");
        JLabel other = new JLabel("other");
        dock.setContent(content);
        Runnable remove = dock.contentInstead(table);

        dock.setContent(other);
        assertThat(dock.content()).containsSame(table);

        remove.run();
        assertThat(dock.content()).containsSame(other);
    }

    @Test
    void anOlderRemovalLeavesTheNewerComponentShown() {
        JLabel first = new JLabel("first");
        JLabel second = new JLabel("second");
        dock.setContent(content);
        Runnable removeFirst = dock.contentInstead(first);
        Runnable removeSecond = dock.contentInstead(second);

        removeFirst.run();
        assertThat(dock.content()).containsSame(second);
        removeSecond.run();
        removeSecond.run();
        assertThat(dock.content()).containsSame(content);
    }

    @Test
    void lendingWhileAnotherComponentShowsLendsItAndGivesItBack() {
        JLabel table = new JLabel("table");
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);
        Runnable remove = dock.contentInstead(table);

        assertThat(dock.lendContent()).containsSame(table);
        JPanel elsewhere = new JPanel();
        elsewhere.add(table);
        dock.returnContent();
        layout(dock);

        assertThat(table.getParent()).isNotSameAs(elsewhere).isNotNull();
        remove.run();
        assertThat(dock.content()).containsSame(content);
    }

    @Test
    void swappingWhileLentTellsTheBorrowerSoItReturnsAndLendsTheShownOne() {
        JLabel table = new JLabel("table");
        dock.setContent(content);
        dock.setSize(800, 600);
        dock.showDock();
        layout(dock);
        JPanel elsewhere = new JPanel();
        List<String> calls = new ArrayList<>();
        dock.addContentListener(() -> calls.add("swapped"));
        elsewhere.add(dock.lendContent().orElseThrow());

        Runnable remove = dock.contentInstead(table);

        assertThat(calls).containsExactly("swapped");
        assertThat(dock.content()).as("what the dock shows now").containsSame(table);
        dock.returnContent();
        assertThat(content.getParent()).as("the old one leaves the borrower").isNull();
        assertThat(dock.lendContent()).containsSame(table);
        elsewhere.add(table);

        remove.run();

        assertThat(calls).containsExactly("swapped", "swapped");
        dock.returnContent();
        assertThat(table.getParent()).isNull();
        assertThat(dock.lendContent()).containsSame(content);
    }
}
