package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import static org.assertj.core.api.Assertions.assertThat;

class TextRenderersTest {

    private final JList<Object> list = new JList<>();

    private String textOf(ListCellRenderer<Object> renderer, Object value, int index) {
        return ((JLabel) renderer.getListCellRendererComponent(list, value, index, false, false)).getText();
    }

    @Test
    void theTextComesFromTheValueAndTheIndex() {
        ListCellRenderer<Object> renderer = TextRenderers.of((value, index) -> value + "@" + index);

        assertThat(textOf(renderer, "a", 3)).isEqualTo("a@3");
        assertThat(textOf(renderer, "b", -1)).isEqualTo("b@-1");
    }

    @Test
    void aTypedRendererChangesOnlyTheValuesOfItsType() {
        ListCellRenderer<Object> renderer = TextRenderers.of(Integer.class,
                (number, index) -> "#" + (number + index));

        assertThat(textOf(renderer, 5, 2)).isEqualTo("#7");
        assertThat(textOf(renderer, "plain", 2)).isEqualTo("plain");
    }
}
