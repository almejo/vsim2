package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V1: a toolbar that does not fit wraps into more rows, in the same order, instead of cutting controls off. */
class WrapToolBarLayoutTest {

    private final List<JButton> buttons = new ArrayList<>();
    private final Component glue = Box.createHorizontalGlue();
    private final JToolBar toolBar = new JToolBar();

    /** {@code before} buttons, a separator after the second, the glue, then {@code after} buttons. */
    private void build(int before, int after) {
        toolBar.setLayout(new WrapToolBarLayout(glue));
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
    }

    private void add(int index) {
        JButton button = new JButton("b" + index);
        button.setPreferredSize(new Dimension(40, 30));
        buttons.add(button);
        toolBar.add(button);
    }

    private void layOutAt(int width) {
        toolBar.setSize(width, toolBar.getPreferredSize().height);
        toolBar.doLayout();
    }

    @Test
    void whenEverythingFitsItIsOneRowAndTheLastGroupSitsAtTheRightEdge() {
        build(3, 2);
        layOutAt(600);
        assertThat(buttons).extracting(Component::getY).containsOnly(buttons.getFirst().getY());
        assertThat(buttons.getLast().getX() + buttons.getLast().getWidth()).isEqualTo(600 - toolBar.getInsets().right);
        assertThat(buttons.get(2).getX()).isLessThan(buttons.get(3).getX());
    }

    @Test
    void whenItDoesNotFitItWrapsIntoMoreRowsKeepingEveryControlVisibleAndInOrder() {
        build(8, 2);
        layOutAt(200);
        List<Rectangle> bounds = buttons.stream().map(Component::getBounds).toList();
        for (Rectangle rectangle : bounds) {
            assertThat(rectangle.x).isGreaterThanOrEqualTo(0);
            assertThat(rectangle.x + rectangle.width).isLessThanOrEqualTo(200);
        }
        assertThat(bounds.stream().mapToInt(rectangle -> rectangle.y).distinct().count()).isGreaterThan(1);
        for (int index = 1; index < bounds.size(); index++) {
            Rectangle previous = bounds.get(index - 1);
            Rectangle current = bounds.get(index);
            assertThat(current.y > previous.y || (current.y == previous.y && current.x > previous.x))
                    .as("b%d follows b%d row by row", index, index - 1).isTrue();
        }
    }

    @Test
    void theHeightFollowsTheRowsOnceLaidOutAndShrinksBackWhenWide() {
        build(8, 2);
        layOutAt(200);
        int narrow = toolBar.getPreferredSize().height;
        layOutAt(1000);
        int wide = toolBar.getPreferredSize().height;
        assertThat(narrow).isGreaterThan(wide);
        assertThat(wide).isGreaterThanOrEqualTo(30);
    }

    @Test
    void aSeparatorNeverStartsARow() {
        build(8, 0);
        // b0 b1 | b2 ...: room for b0 and b1 only, so the separator would open the second row
        layOutAt(2 * 40 + 2 + toolBar.getInsets().left + toolBar.getInsets().right);
        assertThat(buttons.get(1).getY()).isEqualTo(buttons.get(0).getY());
        assertThat(buttons.get(2).getY()).isGreaterThan(buttons.get(1).getY());
        assertThat(buttons.get(2).getX()).isEqualTo(toolBar.getInsets().left);
    }

    @Test
    void hiddenComponentsTakeNoRoom() {
        build(4, 0);
        buttons.getFirst().setVisible(false);
        layOutAt(300);
        assertThat(buttons.get(1).getX()).isEqualTo(toolBar.getInsets().left);
    }
}
