package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The slider ↔ zoom mapping and the percentage parsing of the status-bar zoom control (zoom spec §6.2, §6.3). */
class ZoomScaleTest {

    @Test
    void theSliderIsLogarithmicWithOneHundredPercentInTheMiddle() {
        assertThat(ZoomScale.sliderValue(0.25)).isEqualTo(-200);
        assertThat(ZoomScale.sliderValue(0.5)).isEqualTo(-100);
        assertThat(ZoomScale.sliderValue(1.0)).isZero();
        assertThat(ZoomScale.sliderValue(2.0)).isEqualTo(100);
        assertThat(ZoomScale.sliderValue(4.0)).isEqualTo(200);
        assertThat(ZoomScale.zoomOf(-200)).isCloseTo(0.25, within(1e-12));
        assertThat(ZoomScale.zoomOf(100)).isCloseTo(2.0, within(1e-12));
        assertThat(ZoomScale.zoomOf(200)).isCloseTo(4.0, within(1e-12));
        assertThat(ZoomScale.MINIMUM_VALUE).isEqualTo(-200);
        assertThat(ZoomScale.MAXIMUM_VALUE).isEqualTo(200);
    }

    @Test
    void eachDoublingIsTheSameDistanceAndTheMappingIsMonotonic() {
        assertThat(ZoomScale.sliderValue(1.0) - ZoomScale.sliderValue(0.5))
                .isEqualTo(ZoomScale.sliderValue(4.0) - ZoomScale.sliderValue(2.0));
        double previous = 0;
        for (int value = ZoomScale.MINIMUM_VALUE; value <= ZoomScale.MAXIMUM_VALUE; value++) {
            double zoom = ZoomScale.zoomOf(value);
            assertThat(zoom).as("zoom at " + value).isGreaterThanOrEqualTo(previous);
            previous = zoom;
        }
    }

    @Test
    void outOfRangeZoomsSitAtTheEnds() {
        assertThat(ZoomScale.sliderValue(0.1)).isEqualTo(-200);
        assertThat(ZoomScale.sliderValue(10)).isEqualTo(200);
    }

    @Test
    void nearOneHundredPercentTheSliderSnapsToIt() {
        for (int value = -4; value <= 4; value++) {
            assertThat(ZoomScale.zoomOf(value)).as("detent at " + value).isEqualTo(1.0);
        }
        assertThat(ZoomScale.zoomOf(5)).isGreaterThan(1.0);
        assertThat(ZoomScale.zoomOf(-5)).isLessThan(1.0);
    }

    @Test
    void aPercentageCanBeWrittenWithOrWithoutTheSignAndWithACommaOrAPoint() {
        assertThat(ZoomScale.parsePercent("150")).hasValue(1.5);
        assertThat(ZoomScale.parsePercent("150 %")).hasValue(1.5);
        assertThat(ZoomScale.parsePercent("150%")).hasValue(1.5);
        assertThat(ZoomScale.parsePercent("  150 ")).hasValue(1.5);
        assertThat(ZoomScale.parsePercent(" 62,5 ")).hasValue(0.625);
        assertThat(ZoomScale.parsePercent("62.5")).hasValue(0.625);
        assertThat(ZoomScale.parsePercent("125 %")).hasValue(1.25);
    }

    @Test
    void textThatIsNotANumberGivesNothing() {
        assertThat(ZoomScale.parsePercent("abc")).isEmpty();
        assertThat(ZoomScale.parsePercent("")).isEmpty();
        assertThat(ZoomScale.parsePercent("  ")).isEmpty();
        assertThat(ZoomScale.parsePercent("%")).isEmpty();
        assertThat(ZoomScale.parsePercent("1e3")).isEmpty();
        assertThat(ZoomScale.parsePercent("NaN")).isEmpty();
        assertThat(ZoomScale.parsePercent("12,5,0")).isEmpty();
    }

    @Test
    void percentagesOutOfRangeAreClampedInsteadOfRefused() {
        assertThat(ZoomScale.parsePercent("1000")).isEqualTo(OptionalDouble.of(4.0));
        assertThat(ZoomScale.parsePercent("-5")).isEqualTo(OptionalDouble.of(0.25));
        assertThat(ZoomScale.parsePercent("0")).isEqualTo(OptionalDouble.of(0.25));
        assertThat(ZoomScale.clamp(7)).isEqualTo(4.0);
        assertThat(ZoomScale.clamp(0.01)).isEqualTo(0.25);
    }

    @Test
    void thePresetsGoFromTwentyFiveToFourHundred() {
        assertThat(ZoomScale.presets()).containsExactly(25, 50, 75, 100, 150, 200, 300, 400);
    }

    @Test
    void theShownPercentageIsRounded() {
        assertThat(ZoomScale.percent(1.5625)).isEqualTo(156);
        assertThat(ZoomScale.percent(0.25)).isEqualTo(25);
    }
}
