package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class BlockSignalsTest {

    private static final Value CONFLICT = Value.ONE.withConflict();

    @Test
    void theWidthParameterGoesFromOneToThirtyTwoBitsAndDefaultsToEight() {
        assertThat(BlockSignals.WIDTH.name()).isEqualTo(StandardComponentTypes.WIDTH);
        assertThat(BlockSignals.WIDTH.kind()).isEqualTo(ParameterKind.INTEGER);
        assertThat(BlockSignals.WIDTH.defaultValue()).isEqualTo(8);
        assertThat(BlockSignals.WIDTH.minimum()).isEqualTo(1);
        assertThat(BlockSignals.WIDTH.maximum()).isEqualTo(32);
    }

    @Test
    void aControlInputAtZTakesItsRestValueAndAConflictIsUndefined() {
        assertThat(BlockSignals.control(Value.ZERO, true)).contains(false);
        assertThat(BlockSignals.control(Value.ONE, false)).contains(true);
        assertThat(BlockSignals.control(Value.HIGH_IMPEDANCE, true)).contains(true);
        assertThat(BlockSignals.control(Value.HIGH_IMPEDANCE, false)).contains(false);
        assertThat(BlockSignals.control(CONFLICT, true)).isEmpty();
        assertThat(BlockSignals.control(Value.ZERO.withConflict(), false)).isEqualTo(Optional.empty());
    }

    @Test
    void unsignedAndSignedReadOnlyFullyDefinedValues() {
        assertThat(BlockSignals.unsigned(Value.of(4, 0b1011))).isEqualTo(OptionalLong.of(11));
        assertThat(BlockSignals.signed(Value.of(4, 0b1011))).isEqualTo(OptionalLong.of(-5));
        assertThat(BlockSignals.signed(Value.of(4, 0b0111))).isEqualTo(OptionalLong.of(7));
        assertThat(BlockSignals.signed(Value.of(4, 0b1000))).isEqualTo(OptionalLong.of(-8));
        assertThat(BlockSignals.signed(Value.ONE)).isEqualTo(OptionalLong.of(-1));
        assertThat(BlockSignals.unsigned(Value.ONE)).isEqualTo(OptionalLong.of(1));
        assertThat(BlockSignals.unsigned(Value.of(32, 0xFFFF_FFFFL))).isEqualTo(OptionalLong.of(0xFFFF_FFFFL));
        assertThat(BlockSignals.signed(Value.of(32, 0xFFFF_FFFFL))).isEqualTo(OptionalLong.of(-1));
        assertThat(BlockSignals.signed(Value.of(32, 0x8000_0000L))).isEqualTo(OptionalLong.of(Integer.MIN_VALUE));
        assertThat(BlockSignals.signed(Value.of(32, 0x7FFF_FFFFL))).isEqualTo(OptionalLong.of(Integer.MAX_VALUE));

        Value oneBitInZ = new Value(4, 0b0011, 0b0100, false);
        assertThat(BlockSignals.unsigned(oneBitInZ)).isEmpty();
        assertThat(BlockSignals.signed(oneBitInZ)).isEmpty();
        assertThat(BlockSignals.unsigned(Value.highImpedance(4))).isEmpty();
        assertThat(BlockSignals.unsigned(Value.of(4, 3).withConflict())).isEmpty();
        assertThat(BlockSignals.signed(Value.of(4, 3).withConflict())).isEmpty();
    }

    @Test
    void allDefinedNeedsEveryValueFullyDefined() {
        assertThat(BlockSignals.allDefined(Value.ONE, Value.of(4, 9))).isTrue();
        assertThat(BlockSignals.allDefined(Value.ONE, Value.highImpedance(4))).isFalse();
        assertThat(BlockSignals.allDefined(CONFLICT)).isFalse();
        assertThat(BlockSignals.allDefined()).isTrue();
    }

    @Test
    void aResultKeepsOnlyTheBitsOfItsWidthAndUndefinedIsAllZ() {
        assertThat(BlockSignals.result(4, 0x1F)).isEqualTo(Value.of(4, 0xF));
        assertThat(BlockSignals.result(4, -1)).isEqualTo(Value.of(4, 0xF));
        assertThat(BlockSignals.result(32, -2)).isEqualTo(Value.of(32, 0xFFFF_FFFEL));
        assertThat(BlockSignals.result(32, -2).bits()).isEqualTo(0xFFFF_FFFEL);
        assertThat(BlockSignals.result(1, 2)).isEqualTo(Value.ZERO);
        assertThat(BlockSignals.undefined(8)).isEqualTo(Value.highImpedance(8));
        assertThat(BlockSignals.undefined(8).isFullyHighImpedance()).isTrue();
        assertThat(BlockSignals.undefined(1)).isEqualTo(Value.HIGH_IMPEDANCE);
    }
}
