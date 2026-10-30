package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClockEdgeDetectorTest {

    @Test
    void theEdgeParameterIsAChoiceOfRisingOrFallingThatDefaultsToRising() {
        assertThat(StandardParameters.EDGE.name()).isEqualTo("edge");
        assertThat(StandardParameters.EDGE.kind()).isEqualTo(ParameterKind.CHOICE);
        assertThat(StandardParameters.EDGE.choices()).containsExactly(StandardParameters.RISING,
                StandardParameters.FALLING);
        assertThat(StandardParameters.EDGE.defaultValue()).isEqualTo(StandardParameters.RISING);
    }

    @Test
    void aRisingDetectorSeesZeroToOneOnly() {
        ClockEdgeDetector detector = ClockEdgeDetector.forEdge(StandardParameters.RISING);
        detector.start(Value.ZERO);

        assertThat(detector.step(Value.ONE)).isTrue();
        assertThat(detector.step(Value.ONE)).as("no change, no edge").isFalse();
        assertThat(detector.step(Value.ZERO)).isFalse();
        assertThat(detector.step(Value.ONE)).isTrue();
    }

    @Test
    void aFallingDetectorSeesOneToZeroOnly() {
        ClockEdgeDetector detector = ClockEdgeDetector.forEdge(StandardParameters.FALLING);
        detector.start(Value.ONE);

        assertThat(detector.step(Value.ZERO)).isTrue();
        assertThat(detector.step(Value.ONE)).isFalse();
        assertThat(detector.step(Value.ZERO)).isTrue();
    }

    @Test
    void goingThroughHighImpedanceIsNotAnEdge() {
        ClockEdgeDetector rising = ClockEdgeDetector.forEdge(StandardParameters.RISING);
        rising.start(Value.ZERO);

        assertThat(rising.step(Value.HIGH_IMPEDANCE)).isFalse();
        assertThat(rising.step(Value.ONE)).as("0 → Z → 1 is not a direct step").isFalse();
    }

    @Test
    void anUnstartedDetectorTakesNoFirstValueAsAnEdge() {
        ClockEdgeDetector rising = ClockEdgeDetector.forEdge(StandardParameters.RISING);

        assertThat(rising.step(Value.ONE)).as("from Z, the value before the start").isFalse();
    }

    @Test
    void theDetectorFollowsTheResolvedParameters() {
        ClockEdgeDetector detector = ClockEdgeDetector.of(Map.of(StandardParameters.EDGE.name(),
                StandardParameters.FALLING));
        detector.start(Value.ONE);

        assertThat(detector.step(Value.ZERO)).isTrue();
        assertThatThrownBy(() -> ClockEdgeDetector.forEdge("sideways")).isInstanceOf(IllegalArgumentException.class);
    }
}
