package cl.almejo.vsim.ui.canvas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewStateTest {

    @Test
    void refusesValuesThatCannotBeShown() {
        assertThatThrownBy(() -> new ViewState(Double.NaN, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewState(0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewState(1, Double.POSITIVE_INFINITY, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewState(1, 0, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
