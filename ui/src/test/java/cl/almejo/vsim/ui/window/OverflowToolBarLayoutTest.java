package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V1: a toolbar that does not fit stays one row and the controls that do not fit go behind a «»» button, in order. */
class OverflowToolBarLayoutTest {

    private final List<JButton> buttons = new ArrayList<>();
    private final JButton overflowButton = new JButton("»");
    private final JToolBar toolBar = new JToolBar();
    private OverflowToolBarLayout layout;

    /** {@code count} buttons of 40 px with a separator after the second, then the » button. */
    private void build(int count) {
        layout = new OverflowToolBarLayout(overflowButton);
        toolBar.setLayout(layout);
        for (int index = 0; index < count; index++) {
            add(index);
            if (index == 1) {
                toolBar.addSeparator();
            }
        }
        toolBar.add(overflowButton);
    }

    private JButton add(int index) {
        JButton button = new JButton("b" + index);
        button.setPreferredSize(new Dimension(40, 30));
        buttons.add(button);
        toolBar.add(button);
        return button;
    }

    private void layOutAt(int width) {
        toolBar.setSize(width, toolBar.getPreferredSize().height);
        toolBar.doLayout();
    }

    private boolean onTheBar(Component component) {
        return component.getWidth() > 0 && component.getX() + component.getWidth() <= toolBar.getWidth();
    }

    private List<String> popupTexts(JPopupMenu popup) {
        List<String> texts = new ArrayList<>();
        for (Component child : popup.getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    @Test
    void whenEverythingFitsItIsOneRowFromTheLeftWithoutTheOverflowButton() {
        build(5);
        layOutAt(600);
        assertThat(buttons).allMatch(this::onTheBar);
        assertThat(buttons).extracting(Component::getY).containsOnly(buttons.getFirst().getY());
        assertThat(overflowButton.isVisible()).isFalse();
        assertThat(layout.hidden()).isEmpty();
        assertThat(buttons.getFirst().getX()).isEqualTo(toolBar.getInsets().left);
        assertThat(buttons.getLast().getX() + buttons.getLast().getWidth()).as("nothing is pushed to the right edge")
                .isLessThan(300);
    }

    @Test
    void whenItDoesNotFitTheLastLeaveInOrderAndTheButtonShowsRightAfterWhatStays() {
        build(10);
        layOutAt(200);
        assertThat(overflowButton.isVisible()).isTrue();
        List<JButton> hidden = buttons.stream().filter(button -> !onTheBar(button)).toList();
        assertThat(hidden).isNotEmpty();
        assertThat(layout.hidden()).containsSubsequence(hidden);
        int firstHidden = buttons.indexOf(hidden.getFirst());
        assertThat(buttons.subList(0, firstHidden)).allMatch(this::onTheBar);
        JButton lastShown = buttons.get(firstHidden - 1);
        assertThat(overflowButton.getX()).as("no gap before the » button")
                .isEqualTo(lastShown.getX() + lastShown.getWidth());
        int free = 200 - toolBar.getInsets().right - (overflowButton.getX() + overflowButton.getWidth());
        assertThat(free).as("only what must leaves the bar").isLessThan(hidden.getFirst().getPreferredSize().width);
        assertThat(popupTexts(layout.overflowMenu()))
                .containsExactlyElementsOf(hidden.stream().map(JButton::getText).toList());
    }

    @Test
    void theOverflowGoesAwayWhenTheBarGrowsAndComesBackWhenItShrinks() {
        build(10);
        layOutAt(200);
        assertThat(overflowButton.isVisible()).isTrue();
        layOutAt(2000);
        assertThat(overflowButton.isVisible()).isFalse();
        assertThat(buttons).allMatch(this::onTheBar);
        layOutAt(300);
        assertThat(overflowButton.isVisible()).isTrue();
    }

    @Test
    void aSeparatorLeftAtTheEndOfWhatStaysIsHiddenAndOneOpeningThePopupIsDropped() {
        build(8);
        // b0 b1 | b2 ...: room for b0, b1, the separator and the » button only
        int separatorWidth = toolBar.getComponent(2).getPreferredSize().width;
        layOutAt(2 * 40 + separatorWidth + overflowButton.getPreferredSize().width
                + toolBar.getInsets().left + toolBar.getInsets().right);
        assertThat(layout.hidden().getFirst()).isInstanceOf(JToolBar.Separator.class);
        assertThat(popupTexts(layout.overflowMenu())).startsWith("b2");
        assertThat(layout.overflowMenu().getComponent(0)).isNotInstanceOf(JPopupMenu.Separator.class);
    }

    @Test
    void aSeparatorEndingTheBarWhenEverythingFitsTakesNoRoom() {
        layout = new OverflowToolBarLayout(overflowButton);
        toolBar.setLayout(layout);
        add(0);
        toolBar.addSeparator();
        toolBar.add(overflowButton);
        layOutAt(600);
        assertThat(toolBar.getComponent(1).getWidth()).isZero();
        assertThat(layout.hidden()).isEmpty();
    }

    @Test
    void theEnabledStateAndTheSelectionOfAHiddenButtonAreFollowedLiveByItsMenuItem() {
        layout = new OverflowToolBarLayout(overflowButton);
        toolBar.setLayout(layout);
        add(0);
        JToggleButton toggle = new JToggleButton("t");
        toggle.setPreferredSize(new Dimension(40, 30));
        toolBar.add(toggle);
        toolBar.add(overflowButton);
        layOutAt(60);
        assertThat(layout.hidden()).contains(toggle);
        JPopupMenu popup = layout.overflowMenu();
        JCheckBoxMenuItem item = (JCheckBoxMenuItem) popup.getComponent(layout.hidden().indexOf(toggle));
        assertThat(item.isSelected()).isFalse();
        toggle.setSelected(true);
        assertThat(item.isSelected()).isTrue();
        toggle.setEnabled(false);
        assertThat(item.isEnabled()).isFalse();
    }

    @Test
    void aClickOnAMenuItemPressesItsButton() {
        build(8);
        layOutAt(120);
        int[] pressed = {0};
        JButton firstHidden = (JButton) layout.hidden().stream().filter(JButton.class::isInstance).findFirst().orElseThrow();
        firstHidden.addActionListener(event -> pressed[0]++);
        JMenuItem item = (JMenuItem) layout.overflowMenu().getComponent(0);
        item.doClick(0);
        assertThat(pressed[0]).isEqualTo(1);
    }
}
