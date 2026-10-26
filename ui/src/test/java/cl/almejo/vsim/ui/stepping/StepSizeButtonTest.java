package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.ui.icons.Icons;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StepSizeButtonTest {

    private final List<Long> chosen = new ArrayList<>();
    private final AtomicInteger others = new AtomicInteger();

    private static List<String> texts(StepSizeButton button) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : button.menu().getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private static JMenuItem item(StepSizeButton button, String text) {
        for (java.awt.Component child : button.menu().getComponents()) {
            if (child instanceof JMenuItem item && item.getText().equals(text)) {
                return item;
            }
        }
        throw new AssertionError("no item " + text);
    }

    @Test
    void theArrowOffersTheUsualSizesAndOther() {
        StepSizeButton button = new StepSizeButton(chosen::add, others::incrementAndGet);

        assertThat(texts(button)).containsExactly("1", "10", "100", "1 000", "10 000", "---", "Otro…");
        assertThat(button.getIcon()).isSameAs(Icons.get(StepIcons.SIZE_MENU, Icons.MENU_SIZE));
        assertThat(button.getToolTipText()).isEqualTo("Elegir el tamaño del avance");
        assertThat(button.getAction()).as("not an action button: ActionIcons and the tooltips leave it alone").isNull();
    }

    @Test
    void anItemChoosesItsSizeAndOtherAsks() {
        StepSizeButton button = new StepSizeButton(chosen::add, others::incrementAndGet);

        item(button, "1 000").doClick();
        item(button, "1").doClick();
        item(button, "Otro…").doClick();

        assertThat(chosen).containsExactly(1_000L, 1L);
        assertThat(others).hasValue(1);
    }
}
