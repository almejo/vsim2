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

    @Test
    void hexShowsEveryNibbleMostSignificantFirst() {
        assertThat(Value.of(8, 0x3F).hex()).isEqualTo("3F");
        assertThat(Value.of(4, 0xA).hex()).isEqualTo("A");
        assertThat(Value.of(5, 0x1A).hex()).isEqualTo("1A");
        assertThat(Value.of(12, 0x0A0).hex()).isEqualTo("0A0");
        assertThat(Value.ONE.hex()).isEqualTo("1");
        assertThat(Value.of(64, -1L).hex()).isEqualTo("FFFFFFFFFFFFFFFF");
    }

    @Test
    void hexMarksHighImpedanceNibbles() {
        assertThat(Value.highImpedance(8).hex()).isEqualTo("ZZ");
        assertThat(new Value(8, 0x0F, 0x30, false).hex()).isEqualTo("?F");
        assertThat(new Value(6, 0, 0b110000, false).hex()).isEqualTo("Z0");
        assertThat(Value.of(8, 0x5A).withConflict().hex()).isEqualTo("5A");
    }

    @Test
    void oneBitValuesAreShared() {
        assertThat(Value.of(1, 1L)).isSameAs(Value.ONE);
        assertThat(Value.of(1, 0L)).isSameAs(Value.ZERO);
        assertThat(Value.highImpedance(1)).isSameAs(Value.HIGH_IMPEDANCE);
        assertThat(Value.ONE.not()).isSameAs(Value.ZERO);
        assertThat(Value.ONE.and(Value.ZERO)).isSameAs(Value.ZERO);
        assertThat(Value.resolve(1, List.of(Value.HIGH_IMPEDANCE, Value.ONE))).isSameAs(Value.ONE);
        assertThat(Value.ZERO.withConflict().not()).isSameAs(Value.ONE.withConflict());
        assertThat(Value.resolve(1, List.of(Value.ONE, Value.ZERO))).isSameAs(Value.ONE.withConflict());
    }

    @Test
    void filledWithTakesOnlyTheBitsInHighImpedanceAndKeepsTheConflict() {
        Value resolved = new Value(4, 0b0001, 0b1100, true);
        Value filler = new Value(4, 0b1110, 0b0001, false);

        assertThat(resolved.filledWith(filler)).isEqualTo(new Value(4, 0b1101, 0b0000, true));
        assertThat(resolved.filledWith(Value.highImpedance(4))).isEqualTo(resolved);
        assertThatThrownBy(() -> resolved.filledWith(Value.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolutionAccumulatesLikeResolve() {
        Value.Resolution resolution = new Value.Resolution(2);
        assertThat(resolution.isEmpty()).isTrue();
        resolution.add(new Value(2, 0b01, 0b10, false));
        resolution.add(new Value(2, 0b10, 0b01, false));
        resolution.add(new Value(2, 0b00, 0b01, false));

        assertThat(resolution.isEmpty()).isFalse();
        assertThat(resolution.value()).isEqualTo(Value.resolve(2, List.of(new Value(2, 0b01, 0b10, false),
                new Value(2, 0b10, 0b01, false), new Value(2, 0b00, 0b01, false))));
        assertThat(resolution.value().conflict()).as("bit 1: 1 against 0").isTrue();
        resolution.reset();
        assertThat(resolution.isEmpty()).isTrue();
        assertThat(resolution.value()).isEqualTo(Value.highImpedance(2));
    }
}
