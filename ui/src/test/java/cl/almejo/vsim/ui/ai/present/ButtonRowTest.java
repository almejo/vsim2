package cl.almejo.vsim.ui.ai.present;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Dimension;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The panel's rows of buttons show every button's whole text whatever the font: equal widths when they fit, else each
 * at least as wide as its text in one row, else one button per row.
 */
class ButtonRowTest {

    private static JButton button(int preferredWidth) {
        JButton button = new JButton("x");
        button.setPreferredSize(new Dimension(preferredWidth, 20));
        return button;
    }

    /** The row in the panel's column, {@code width} wide, laid out twice as a window does after the first pass. */
    private static JPanel column(ButtonRow row, int width) {
        StackLayout layout = new StackLayout(6);
        JPanel column = new JPanel(layout);
        column.add(row);
        column.setSize(width, 400);
        layOut(column);
        column.setSize(width, column.getPreferredSize().height);
        layOut(column);
        return column;
    }

    @Test
    void buttonsThatFitShareTheWidthEqually() {
        JButton first = button(60);
        JButton second = button(100);
        ButtonRow row = new ButtonRow(first, second);
        column(row, 306);

        assertThat(first.getWidth()).isEqualTo(150);
        assertThat(second.getWidth()).isEqualTo(150);
        assertThat(first.getY()).isEqualTo(second.getY());
        assertThat(row.getHeight()).isEqualTo(20);
    }

    @Test
    void whenEqualWidthsDoNotFitEachButtonGetsAtLeastItsTextInOneRow() {
        JButton narrow = button(80);
        JButton wide = button(180);
        ButtonRow row = new ButtonRow(narrow, wide);
        column(row, 300);

        assertThat(narrow.getWidth()).isGreaterThanOrEqualTo(80);
        assertThat(wide.getWidth()).isGreaterThanOrEqualTo(180);
        assertThat(narrow.getWidth() + ButtonRow.GAP + wide.getWidth()).isEqualTo(300);
        assertThat(narrow.getY()).isEqualTo(wide.getY());
    }

    @Test
    void whenTheyDoNotFitInOneRowEachButtonTakesARowOfItsOwnAndTheColumnMakesRoom() {
        JButton first = button(140);
        JButton second = button(210);
        ButtonRow row = new ButtonRow(first, second);
        JPanel column = column(row, 300);

        assertThat(first.getWidth()).isEqualTo(300);
        assertThat(second.getWidth()).isEqualTo(300);
        assertThat(second.getY()).isEqualTo(first.getY() + first.getHeight() + ButtonRow.GAP);
        assertThat(row.getHeight()).isEqualTo(2 * 20 + ButtonRow.GAP);
        assertThat(column.getPreferredSize().height).isEqualTo(row.getHeight());
    }

    @Test
    void aHiddenButtonTakesNoRoom() {
        JButton hidden = button(400);
        hidden.setVisible(false);
        JButton shown = button(100);
        ButtonRow row = new ButtonRow(hidden, shown);
        column(row, 300);

        assertThat(shown.getWidth()).isEqualTo(300);
        assertThat(row.getHeight()).isEqualTo(20);
    }
}
