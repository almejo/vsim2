package cl.almejo.vsim.core.component.standard;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing a Constante's «Mostrar como» or «Bits de fracción» keeps the bits it drives: its value text is written
 * again in the new format (decided in the «Filas fáciles» fix round, review I2).
 */
class ConstantFormatChangeTest {

    private static Map<String, Object> constant(int width, String value, String format, int fraction) {
        return Map.of(StandardComponentTypes.WIDTH, width, StandardComponentTypes.VALUE, value,
                ConstantValue.FORMAT, format, ConstantValue.FRACTION, fraction);
    }

    private static Map<String, Object> with(Map<String, Object> parameters, String name, Object value) {
        Map<String, Object> changed = new HashMap<>(parameters);
        changed.put(name, value);
        return changed;
    }

    private static Map<String, Object> change(Map<String, Object> current, Map<String, Object> requested) {
        return ConstantFormatChange.keepingTheBits(StandardComponentTypes.CONSTANT, current, requested);
    }

    private static long bitsOf(Map<String, Object> parameters) {
        return ConstantValue.bitsOf((String) parameters.get(StandardComponentTypes.VALUE),
                (Integer) parameters.get(StandardComponentTypes.WIDTH), (String) parameters.get(ConstantValue.FORMAT),
                (Integer) parameters.get(ConstantValue.FRACTION)).orElseThrow();
    }

    @Test
    void aDecimalTenSwitchedToFloatStillDrivesTenAndShowsItAsAFloat() {
        Map<String, Object> current = constant(16, "10", ConstantValue.AUTO, 0);

        Map<String, Object> changed = change(current, with(current, ConstantValue.FORMAT, ConstantValue.FLOAT));

        assertThat(bitsOf(changed)).isEqualTo(10);
        assertThat(changed.get(StandardComponentTypes.VALUE)).isEqualTo(ConstantValue.display(10, 16,
                ConstantValue.FLOAT));
    }

    @Test
    void aDecimalTenSwitchedToFixedPointWithFourFractionBitsReadsZeroPointSixTwoFive() {
        Map<String, Object> current = constant(16, "10", ConstantValue.DECIMAL, 0);
        Map<String, Object> requested = with(with(current, ConstantValue.FORMAT, ConstantValue.FIXED),
                ConstantValue.FRACTION, 4);

        Map<String, Object> changed = change(current, requested);

        assertThat(changed.get(StandardComponentTypes.VALUE)).isEqualTo("0.625");
        assertThat(bitsOf(changed)).isEqualTo(10);
    }

    @Test
    void changingOnlyTheFractionBitsKeepsTheBitsToo() {
        Map<String, Object> current = constant(8, "1.5", ConstantValue.FIXED, 2);

        Map<String, Object> changed = change(current, with(current, ConstantValue.FRACTION, 1));

        assertThat(changed.get(StandardComponentTypes.VALUE)).isEqualTo("3.0");
        assertThat(bitsOf(changed)).isEqualTo(6);
    }

    @Test
    void floatAtAWidthWithoutAnIeeeFormatWritesTheBitsInHex() {
        Map<String, Object> current = constant(8, "10", ConstantValue.AUTO, 0);

        Map<String, Object> changed = change(current, with(current, ConstantValue.FORMAT, ConstantValue.FLOAT));

        assertThat(changed.get(StandardComponentTypes.VALUE)).isEqualTo("0xA");
    }

    @Test
    void aFloatSwitchedToDecimalShowsItsBitsAsADecimal() {
        Map<String, Object> current = constant(16, "1.5", ConstantValue.FLOAT, 0);

        Map<String, Object> changed = change(current, with(current, ConstantValue.FORMAT, ConstantValue.DECIMAL));

        assertThat(changed.get(StandardComponentTypes.VALUE)).isEqualTo("15872");
    }

    @Test
    void bitsTheNewFormatCannotWriteBackBecomeHex() {
        Map<String, Object> current = constant(16, "0x7E01", ConstantValue.HEX, 0);

        Map<String, Object> changed = change(current, with(current, ConstantValue.FORMAT, ConstantValue.FLOAT));

        assertThat(changed.get(StandardComponentTypes.VALUE)).isEqualTo("0x7E01");
        assertThat(bitsOf(changed)).isEqualTo(0x7E01);
    }

    @Test
    void aValueTypedTogetherWithTheNewFormatIsReadInTheNewFormat() {
        Map<String, Object> current = constant(16, "10", ConstantValue.AUTO, 0);
        Map<String, Object> requested = with(with(current, ConstantValue.FORMAT, ConstantValue.FLOAT),
                StandardComponentTypes.VALUE, "2.5");

        assertThat(change(current, requested)).isEqualTo(requested);
    }

    @Test
    void anEditThatKeepsTheFormatOrIsNotAConstantIsLeftAlone() {
        Map<String, Object> current = constant(16, "10", ConstantValue.AUTO, 0);
        Map<String, Object> wider = with(current, StandardComponentTypes.WIDTH, 32);

        assertThat(change(current, wider)).isEqualTo(wider);
        Map<String, Object> asPin = with(current, ConstantValue.FORMAT, ConstantValue.FLOAT);
        assertThat(ConstantFormatChange.keepingTheBits(StandardComponentTypes.INPUT_PIN, current, asPin))
                .isEqualTo(asPin);
    }
}
