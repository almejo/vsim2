package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaneLayoutTest {

    private static final ViewState VIEW = new ViewState(1, 10, 20);

    @Test
    void theNumberOfPanesFollowsTheDividers() {
        assertThat(PaneLayout.single(VIEW).isSplit()).isFalse();
        assertThat(new PaneLayout(OptionalDouble.of(0.5), OptionalDouble.empty(), List.of(VIEW, VIEW)).isSplit())
                .isTrue();
        assertThat(new PaneLayout(OptionalDouble.of(0.3), OptionalDouble.of(0.6), List.of(VIEW, VIEW, VIEW, VIEW))
                .panes()).hasSize(4);
        assertThatThrownBy(() -> new PaneLayout(OptionalDouble.of(0.5), OptionalDouble.empty(), List.of(VIEW)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaneLayout(OptionalDouble.of(0.5), OptionalDouble.of(0.5), List.of(VIEW, VIEW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDividerSitsStrictlyInsideTheArea() {
        assertThatThrownBy(() -> new PaneLayout(OptionalDouble.of(0), OptionalDouble.empty(), List.of(VIEW, VIEW)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaneLayout(OptionalDouble.empty(), OptionalDouble.of(1), List.of(VIEW, VIEW)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
