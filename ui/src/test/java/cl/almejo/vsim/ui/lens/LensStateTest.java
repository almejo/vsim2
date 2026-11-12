package cl.almejo.vsim.ui.lens;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** When the lens shows: on, and the pointer over a pane (strings stand in for the panes). */
class LensStateTest {

    private static final Point2D POINT = new Point2D.Double(40, 50);

    private final LensState<String> state = new LensState<>();

    @Test
    void aNewLensIsOffAtThreeTimesWithNoPointer() {
        assertThat(state.isOn()).isFalse();
        assertThat(state.isShowing()).isFalse();
        assertThat(state.factor()).isEqualTo(LensState.DEFAULT_FACTOR).isEqualTo(3);
        assertThat(state.pointerPane()).isEmpty();
        assertThat(LensState.FACTORS).containsExactly(2.0, 3.0, 4.0);
        assertThat(LensState.RADIUS).isEqualTo(100);
    }

    @Test
    void onWithThePointerOverAPaneShowsOnThatPaneOnly() {
        state.pointerAt("left", POINT);
        state.setOn(true);
        assertThat(state.isShowing()).isTrue();
        assertThat(state.isShowingOn("left")).isTrue();
        assertThat(state.isShowingOn("right")).isFalse();
        assertThat(state.pointer()).contains(POINT);
    }

    @Test
    void onWithoutThePointerOverAPaneShowsNothing() {
        state.setOn(true);
        assertThat(state.isOn()).isTrue();
        assertThat(state.isShowing()).isFalse();
    }

    @Test
    void turningItOffHidesIt() {
        state.pointerAt("left", POINT);
        state.setOn(true);
        state.setOn(false);
        assertThat(state.isShowing()).isFalse();
    }

    @Test
    void thePointerLeavingItsPaneHidesItAndLeavingAnotherPaneDoesNot() {
        state.setOn(true);
        state.pointerAt("left", POINT);
        state.pointerLeft("right");
        assertThat(state.isShowingOn("left")).isTrue();
        state.pointerLeft("left");
        assertThat(state.isShowing()).isFalse();
        assertThat(state.pointerPane()).isEmpty();
    }

    @Test
    void thePointerMovingToAnotherPaneMovesTheLens() {
        state.setOn(true);
        state.pointerAt("left", POINT);
        state.pointerAt("right", new Point2D.Double(5, 6));
        assertThat(state.isShowingOn("left")).isFalse();
        assertThat(state.isShowingOn("right")).isTrue();
        assertThat(state.pointer()).contains(new Point2D.Double(5, 6));
    }

    @Test
    void onlyTheOfferedFactorsAreAccepted() {
        state.setFactor(4);
        assertThat(state.factor()).isEqualTo(4);
        assertThatThrownBy(() -> state.setFactor(5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(state.factor()).isEqualTo(4);
    }
}
