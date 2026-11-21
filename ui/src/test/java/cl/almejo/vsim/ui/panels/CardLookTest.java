package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.icons.Icons;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The floating cards share one corner and one close icon. */
class CardLookTest {

    @Test
    void oneCornerAndOneCloseIcon() {
        assertThat(CardLook.CORNER).isEqualTo(10);
        assertThat(CardLook.CLOSE_ICON).isEqualTo("find-close");
        assertThat(Icons.get(CardLook.CLOSE_ICON, 14)).isNotNull();
    }
}
