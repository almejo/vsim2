package cl.almejo.vsim.core.component.standard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantValueTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0b1010|0b1010|10",
            "0B0011|0b11|3",
            "0b0000|0b0|0",
            "0xff|0xFF|255",
            "0XFF|0xFF|255",
            "0x00ff|0xFF|255",
            "0x0|0x0|0",
            "255|255|255",
            " 007 |7|7",
            "000|0|0",
            "0b1010_0101|0b10100101|165",
            "1_000|1000|1000",
            "0xFFFFFFFF|0xFFFFFFFF|4294967295",
            "4294967295|4294967295|4294967295"})
    void validTextsParseAndNormalize(String text, String normalized, long value) {
        assertThat(ConstantValue.normalize(text)).isEqualTo(normalized);
        assertThat(ConstantValue.parse(text)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "-1", "0x", "0b", "0b2", "12a", "0xG", "abc", "_", "1.5", "0x-1", "- 1"})
    void garbageIsRejected(String text) {
        assertThatThrownBy(() -> ConstantValue.parse(text)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.normalize(text)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConstantValue.isValidText(text)).isFalse();
    }

    @Test
    void valuesAbove32BitsAreRejectedAsNotFitting() {
        assertThatThrownBy(() -> ConstantValue.parse("0x100000000")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.parse("4294967296")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConstantValue.isValidText("0x100000000")).isTrue();
        assertThat(ConstantValue.fitsText("0x100000000", 32)).isFalse();
    }

    @Test
    void theTopOfTheRangeFitsItsWidthAndNothingNarrower() {
        long top = ConstantValue.parse("0xFFFFFFFF");
        assertThat(ConstantValue.fits(top, 32)).isTrue();
        assertThat(ConstantValue.fits(top, 31)).isFalse();
        assertThat(ConstantValue.fits(ConstantValue.parse("4294967295"), 32)).isTrue();
        assertThat(ConstantValue.fits(0, 1)).isTrue();
        assertThat(ConstantValue.fits(1, 1)).isTrue();
        assertThat(ConstantValue.fits(2, 1)).isFalse();
        assertThat(ConstantValue.fits(15, 4)).isTrue();
        assertThat(ConstantValue.fits(16, 4)).isFalse();
    }

    @Test
    void autoShowsBinaryUpToFourBitsAndHexFromFive() {
        assertThat(ConstantValue.resolveFormat("auto", 4)).isEqualTo("binary");
        assertThat(ConstantValue.resolveFormat("auto", 5)).isEqualTo("hex");
        assertThat(ConstantValue.resolveFormat("decimal", 1)).isEqualTo("decimal");
        assertThat(ConstantValue.resolveFormat("binary", 32)).isEqualTo("binary");
    }

    @Test
    void displayFollowsTheFormatAndPadsBinaryToTheFullWidth() {
        assertThat(ConstantValue.display(5, 4, "auto")).isEqualTo("0101");
        assertThat(ConstantValue.display(5, 4, "binary")).isEqualTo("0101");
        assertThat(ConstantValue.display(5, 4, "hex")).isEqualTo("0x5");
        assertThat(ConstantValue.display(5, 4, "decimal")).isEqualTo("5");
        assertThat(ConstantValue.display(255, 8, "auto")).isEqualTo("0xFF");
        assertThat(ConstantValue.display(1, 8, "binary")).isEqualTo("00000001");
        assertThat(ConstantValue.display(4294967295L, 32, "decimal")).isEqualTo("4294967295");
        assertThat(ConstantValue.display(4294967295L, 32, "hex")).isEqualTo("0xFFFFFFFF");
        assertThat(ConstantValue.display(0, 1, "auto")).isEqualTo("0");
    }

    @Test
    void theLongestDisplayIsAsLongAsTheLongestValueOfThatWidthAndFormat() {
        assertThat(ConstantValue.longestDisplay(8, "binary")).isEqualTo(8);
        assertThat(ConstantValue.longestDisplay(8, "hex")).isEqualTo(4);
        assertThat(ConstantValue.longestDisplay(5, "hex")).isEqualTo(4);
        assertThat(ConstantValue.longestDisplay(1, "hex")).isEqualTo(3);
        assertThat(ConstantValue.longestDisplay(8, "decimal")).isEqualTo(3);
        assertThat(ConstantValue.longestDisplay(32, "decimal")).isEqualTo(10);
        assertThat(ConstantValue.longestDisplay(32, "auto")).isEqualTo(10);
        assertThat(ConstantValue.longestDisplay(4, "auto")).isEqualTo(4);
        for (String format : new String[]{"binary", "hex", "decimal", "auto"}) {
            for (int width = 1; width <= 32; width++) {
                long largest = (1L << width) - 1;
                assertThat(ConstantValue.display(largest, width, format).length())
                        .as("%s at %d bits", format, width)
                        .isEqualTo(ConstantValue.longestDisplay(width, format));
            }
        }
    }
}
