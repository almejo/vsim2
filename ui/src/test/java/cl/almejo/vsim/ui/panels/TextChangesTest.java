package cl.almejo.vsim.ui.panels;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class TextChangesTest {

    @Test
    void insertionsAndRemovalsRunTheListener() {
        JTextField field = new JTextField();
        int[] runs = {0};
        TextChanges.onChange(field, () -> runs[0]++);

        field.setText("abc");
        assertThat(runs[0]).isEqualTo(1);

        field.setText("");
        assertThat(runs[0]).isEqualTo(2);

        field.setText("x");
        field.setText("y");
        assertThat(runs[0]).isEqualTo(5);
    }

    @Test
    void theSameListenerCanWatchTwoComponents() {
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        int[] runs = {0};
        Runnable listener = () -> runs[0]++;
        TextChanges.onChange(first, listener);
        TextChanges.onChange(second, listener);

        first.setText("a");
        second.setText("b");

        assertThat(runs[0]).isEqualTo(2);
    }
}
