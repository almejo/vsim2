package cl.almejo.vsim.core.value;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTest {

    @Test
    void singleBitConstants() {
        assertThat(Value.ZERO.bit(0)).isEqualTo(BitState.ZERO);
        assertThat(Value.ONE.bit(0)).isEqualTo(BitState.ONE);
        assertThat(Value.HIGH_IMPEDANCE.bit(0)).isEqualTo(BitState.HIGH_IMPEDANCE);
        assertThat(Value.HIGH_IMPEDANCE.isFullyHighImpedance()).isTrue();
        assertThat(Value.ONE.isFullyDefined()).isTrue();
    }

    @Test
    void constructorMasksBitsOutsideWidthAndUnderHighImpedance() {
        Value value = new Value(4, 0xFF, 0b0001, false);
        assertThat(value.bits()).isEqualTo(0b1110);
        assertThat(value.highImpedanceMask()).isEqualTo(0b0001);
    }

    @Test
    void widthOutOfRangeIsRejected() {
        assertThatThrownBy(() -> Value.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Value.of(65, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Value.of(64, -1L).bits()).isEqualTo(-1L);
    }

    @Test
    void andTruthTableWithHighImpedanceAsIdentity() {
        assertThat(Value.ZERO.and(Value.ONE)).isEqualTo(Value.ZERO);
        assertThat(Value.ONE.and(Value.ONE)).isEqualTo(Value.ONE);
        assertThat(Value.HIGH_IMPEDANCE.and(Value.ONE)).isEqualTo(Value.ONE);
        assertThat(Value.ZERO.and(Value.HIGH_IMPEDANCE)).isEqualTo(Value.ZERO);
        assertThat(Value.HIGH_IMPEDANCE.and(Value.HIGH_IMPEDANCE)).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void orAndXorTruthTables() {
        assertThat(Value.ZERO.or(Value.ZERO)).isEqualTo(Value.ZERO);
        assertThat(Value.ZERO.or(Value.ONE)).isEqualTo(Value.ONE);
        assertThat(Value.HIGH_IMPEDANCE.or(Value.ZERO)).isEqualTo(Value.ZERO);
        assertThat(Value.ONE.xor(Value.ONE)).isEqualTo(Value.ZERO);
        assertThat(Value.ONE.xor(Value.ZERO)).isEqualTo(Value.ONE);
        assertThat(Value.HIGH_IMPEDANCE.xor(Value.ONE)).isEqualTo(Value.ONE);
    }

    @Test
    void notKeepsHighImpedance() {
        assertThat(Value.ZERO.not()).isEqualTo(Value.ONE);
        assertThat(Value.ONE.not()).isEqualTo(Value.ZERO);
        assertThat(Value.HIGH_IMPEDANCE.not()).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void multiBitOperationsWorkPerBit() {
        Value left = new Value(4, 0b0011, 0b1000, false);
        Value right = Value.of(4, 0b1010);
        Value result = left.and(right);
        assertThat(result.bits()).isEqualTo(0b1010);
        assertThat(result.highImpedanceMask()).isZero();
    }

    @Test
    void operationsRequireSameWidth() {
        assertThatThrownBy(() -> Value.ONE.and(Value.of(2, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveSingleDriver() {
        assertThat(Value.resolve(1, List.of(Value.ONE))).isEqualTo(Value.ONE);
    }

    @Test
    void resolveNoDriversIsHighImpedance() {
        assertThat(Value.resolve(4, List.of())).isEqualTo(Value.highImpedance(4));
    }

    @Test
    void resolveHighImpedanceDriversYield() {
        assertThat(Value.resolve(1, List.of(Value.HIGH_IMPEDANCE, Value.ZERO))).isEqualTo(Value.ZERO);
    }

    @Test
    void resolveAgreeingDriversHasNoConflict() {
        assertThat(Value.resolve(1, List.of(Value.ONE, Value.ONE)).conflict()).isFalse();
    }

    @Test
    void resolveDisagreeingDriversIsConflict() {
        Value resolved = Value.resolve(1, List.of(Value.ONE, Value.ZERO));
        assertThat(resolved.conflict()).isTrue();
    }

    @Test
    void resolveCombinesDisjointMultiBitDrivers() {
        Value lowNibble = new Value(8, 0x05, 0xF0, false);
        Value highNibble = new Value(8, 0xA0, 0x0F, false);
        Value resolved = Value.resolve(8, List.of(lowNibble, highNibble));
        assertThat(resolved).isEqualTo(Value.of(8, 0xA5));
    }

    @Test
    void resolveRejectsDriverOfOtherWidth() {
        assertThatThrownBy(() -> Value.resolve(1, List.of(Value.of(2, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringShowsBitsMostSignificantFirst() {
        assertThat(Value.ONE.toString()).isEqualTo("1");
        assertThat(Value.HIGH_IMPEDANCE.toString()).isEqualTo("Z");
        assertThat(new Value(4, 0b0101, 0b1000, false).toString()).isEqualTo("Z101");
        assertThat(Value.ONE.withConflict().toString()).isEqualTo("X!1");
    }
}
