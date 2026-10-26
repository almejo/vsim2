package cl.almejo.vsim.ui.ai.present;

import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PauseMenuTest {

    @Test
    void offersOneRadioPerChoiceAndReportsThePickedMillis() {
        List<Integer> picked = new ArrayList<>();
        PauseMenu menu = new PauseMenu(picked::add);

        assertThat(menu.items()).hasSize(PauseMenu.CHOICES_MILLIS.size());

        menu.items().get(3).doClick();

        assertThat(picked).containsExactly(2000);
    }

    @Test
    void selectMarksOnlyTheMatchingRadioWithoutCallingTheConsumer() {
        List<Integer> picked = new ArrayList<>();
        PauseMenu menu = new PauseMenu(picked::add);

        menu.select(1000);

        assertThat(menu.items()).extracting(JMenuItem::isSelected)
                .containsExactly(false, false, true, false, false, false);
        assertThat(picked).isEmpty();
    }
}
