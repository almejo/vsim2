package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import javax.swing.Box;
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
    private final Component glue = Box.createHorizontalGlue();
    private final JButton overflowButton = new JButton("»");
    private final JToolBar toolBar = new JToolBar();
    private OverflowToolBarLayout layout;

    /** {@code before} buttons, a separator after the second, the glue, then {@code after} buttons, then the » button. */
    private void build(int before, int after) {
        layout = new OverflowToolBarLayout(glue, overflowButton);
        toolBar.setLayout(layout);
        for (int index = 0; index < before; index++) {
            add(index);
            if (index == 1) {
                toolBar.addSeparator();
            }
        }
        toolBar.add(glue);
        for (int index = before; index < before + after; index++) {
            add(index);
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
    void whenEverythingFitsItIsOneRowWithoutTheOverflowButtonAndTheLastGroupAtTheRightEdge() {
        build(3, 2);
        layOutAt(600);
        assertThat(buttons).allMatch(this::onTheBar);
        assertThat(buttons).extracting(Component::getY).containsOnly(buttons.getFirst().getY());
        assertThat(overflowButton.isVisible()).isFalse();
        assertThat(layout.hidden()).isEmpty();
        assertThat(buttons.getLast().getX() + buttons.getLast().getWidth()).isEqualTo(600 - toolBar.getInsets().right);
    }

    @Test
    void whenItDoesNotFitTheLastOfTheLeftGroupLeaveInOrderAndTheButtonShowsAtTheRightEnd() {
        build(8, 2);
        layOutAt(200);
        assertThat(overflowButton.isVisible()).isTrue();
        assertThat(overflowButton.getX() + overflowButton.getWidth()).isEqualTo(200 - toolBar.getInsets().right);
        List<JButton> hidden = buttons.stream().filter(button -> !onTheBar(button)).toList();
        assertThat(hidden).isNotEmpty();
        assertThat(layout.hidden()).containsSubsequence(hidden);
        // what stays is a prefix of the left group: no hidden button before a shown one
        int firstHidden = buttons.indexOf(hidden.getFirst());
        assertThat(buttons.subList(0, firstHidden)).allMatch(this::onTheBar);
        for (JButton button : buttons.subList(0, firstHidden)) {
            assertThat(button.getX() + button.getWidth()).isLessThanOrEqualTo(overflowButton.getX());
        }
        assertThat(popupTexts(layout.overflowMenu()))
                .containsExactlyElementsOf(hidden.stream().map(JButton::getText).toList());
    }

    @Test
    void theOverflowGoesAwayWhenTheBarGrowsAndComesBackWhenItShrinks() {
        build(8, 2);
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
        build(8, 0);
        // b0 b1 | b2 ...: room for b0, b1, the separator and the » button only
        int separatorWidth = toolBar.getComponent(2).getPreferredSize().width;
        layOutAt(2 * 40 + separatorWidth + overflowButton.getPreferredSize().width
                + toolBar.getInsets().left + toolBar.getInsets().right);
        assertThat(layout.hidden().getFirst()).isInstanceOf(JToolBar.Separator.class);
        assertThat(popupTexts(layout.overflowMenu())).startsWith("b2");
        assertThat(layout.overflowMenu().getComponent(0)).isNotInstanceOf(JPopupMenu.Separator.class);
    }

    @Test
    void theEnabledStateAndTheSelectionOfAHiddenButtonAreFollowedLiveByItsMenuItem() {
        layout = new OverflowToolBarLayout(glue, overflowButton);
        toolBar.setLayout(layout);
        add(0);
        JToggleButton toggle = new JToggleButton("t");
        toggle.setPreferredSize(new Dimension(40, 30));
        toolBar.add(toggle);
        toolBar.add(glue);
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
        build(8, 0);
        layOutAt(120);
        int[] pressed = {0};
        JButton firstHidden = (JButton) layout.hidden().stream().filter(JButton.class::isInstance).findFirst().orElseThrow();
        firstHidden.addActionListener(event -> pressed[0]++);
        JMenuItem item = (JMenuItem) layout.overflowMenu().getComponent(0);
        item.doClick(0);
        assertThat(pressed[0]).isEqualTo(1);
    }

    @Test
    void theRightGroupAlsoOverflowsWhenTheBarIsTooNarrowToKeepIt() {
        build(4, 3);
        layOutAt(100);
        assertThat(layout.hidden()).containsAll(buttons.subList(4, 7));
        assertThat(popupTexts(layout.overflowMenu()).getLast()).isEqualTo("b6");
    }
}
