package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTextTest {

    @Test
    void readsBinaryHexadecimalAndDecimal() {
        assertThat(ValueText.parse("1", 1)).isEqualTo(Value.ONE);
        assertThat(ValueText.parse("0b1010", 4)).isEqualTo(Value.of(4, 10));
        assertThat(ValueText.parse("0x3F", 8)).isEqualTo(Value.of(8, 0x3F));
        assertThat(ValueText.parse(" 200 ", 8)).isEqualTo(Value.of(8, 200));
        assertThat(ValueText.parse("0b1111_0000", 8)).isEqualTo(Value.of(8, 0xF0));
        assertThat(ValueText.parse("0xFFFFFFFFFFFFFFFF", 64)).isEqualTo(Value.of(64, -1L));
    }

    @Test
    void aValueThatDoesNotFitOrIsNotANumberIsRefusedWithAHint() {
        assertThatThrownBy(() -> ValueText.parse("1010", 4)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not fit in 4 bits").hasMessageContaining("0b1010");
        assertThatThrownBy(() -> ValueText.parse("2", 1)).hasMessageContaining("1 bit");
        assertThatThrownBy(() -> ValueText.parse("-1", 4)).hasMessageContaining("is not a value");
        assertThatThrownBy(() -> ValueText.parse("0x", 4)).hasMessageContaining("is not a value");
        assertThatThrownBy(() -> ValueText.parse("high", 1)).hasMessageContaining("is not a value");
    }

    @Test
    void writesBitsMasksAndCells() {
        Value partlyFloating = new Value(4, 0b0101, 0b1000, false);

        assertThat(ValueText.bits(partlyFloating)).isEqualTo("Z101");
        assertThat(ValueText.highImpedanceMask(partlyFloating)).isEqualTo("1000");
        assertThat(ValueText.cell(Value.ONE.withConflict())).isEqualTo("X!1");
        assertThat(ValueText.cell(Value.ZERO)).isEqualTo("0");
    }
}
