package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Random;

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
        assertThat(ConstantValue.bitsOf(text, 32)).hasValue(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "-", "--1", "-0x1", "0x", "0b", "0b2", "12a", "0xG", "abc", "_", "1.5", "0x-1",
            "- 1"})
    void garbageIsRejected(String text) {
        assertThat(ConstantValue.bitsOf(text, 32)).isEmpty();
        assertThatThrownBy(() -> ConstantValue.normalize(text)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConstantValue.isValidText(text)).isFalse();
    }

    @Test
    void valuesAbove32BitsAreRejectedAsNotFitting() {
        assertThat(ConstantValue.bitsOf("0x100000000", 32)).isEmpty();
        assertThat(ConstantValue.bitsOf("4294967296", 32)).isEmpty();
        assertThat(ConstantValue.isValidText("0x100000000")).isTrue();
        assertThat(ConstantValue.fitsText("0x100000000", 32)).isFalse();
    }

    @Test
    void theTopOfTheRangeFitsItsWidthAndNothingNarrower() {
        assertThat(ConstantValue.fitsText("0xFFFFFFFF", 32)).isTrue();
        assertThat(ConstantValue.fitsText("0xFFFFFFFF", 31)).isFalse();
        assertThat(ConstantValue.fitsText("4294967295", 32)).isTrue();
        assertThat(ConstantValue.fitsText("0", 1)).isTrue();
        assertThat(ConstantValue.fitsText("1", 1)).isTrue();
        assertThat(ConstantValue.fitsText("2", 1)).isFalse();
        assertThat(ConstantValue.fitsText("15", 4)).isTrue();
        assertThat(ConstantValue.fitsText("16", 4)).isFalse();
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

    @Test
    void theSignedFormatIsTheFifthChoiceAfterTheOldOnes() {
        assertThat(ConstantValue.FORMAT_PARAMETER.name()).isEqualTo("format");
        assertThat(ConstantValue.FORMAT_PARAMETER.defaultValue()).isEqualTo("auto");
        assertThat(ConstantValue.FORMAT_PARAMETER.choices())
                .containsExactly("auto", "binary", "hex", "decimal", "signed");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|1|0", "1|1|-1",
            "0xF|4|-1", "0x8|4|-8", "0x7|4|7", "0x5|4|5",
            "0xFF|8|-1", "0x80|8|-128", "0x7F|8|127",
            "0xFFFFFFFF|32|-1", "0x80000000|32|-2147483648", "0x7FFFFFFF|32|2147483647"})
    void signedDisplayReadsTheBitsInTwosComplement(String text, int width, String shown) {
        long bits = ConstantValue.bitsOf(text, width).orElseThrow();
        assertThat(ConstantValue.display(bits, width, "signed")).isEqualTo(shown);
        assertThat(ConstantValue.bitsOf(shown, width)).hasValue(bits);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "-1|1|1", "-1|4|15", "-8|4|8", "7|4|7", "15|4|15", "0b1010|4|10", "0xF|4|15",
            "-1|8|255", "-128|8|128", "255|8|255",
            "-1|32|4294967295", "-2147483648|32|2147483648", "4294967295|32|4294967295",
            "-1|64|-1", "0xFFFFFFFFFFFFFFFF|64|-1", "-9223372036854775808|64|-9223372036854775808",
            " -0 |4|0", "-0_1|4|15"})
    void bitsOfReadsEveryNotationIntoTheWidthsBits(String text, int width, long bits) {
        assertThat(ConstantValue.bitsOf(text, width)).hasValue(bits);
        assertThat(ConstantValue.fitsText(text, width)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "-2|1", "2|1", "-9|4", "16|4", "0x10|4", "0b10000|4", "-129|8", "256|8",
            "-2147483649|32", "4294967296|32", "0x1FFFFFFFFFFFFFFFF|64", "-9223372036854775809|64",
            "abc|8", "|8", "-0x1|8"})
    void bitsOfIsEmptyForWhatDoesNotFitOrIsNotANumber(String text, int width) {
        assertThat(ConstantValue.bitsOf(text, width)).isEmpty();
    }

    @Test
    void negativeDecimalsAreValidTextsStoredWithTheirSign() {
        assertThat(ConstantValue.isValidText("-3")).isTrue();
        assertThat(ConstantValue.normalize(" -0_3 ")).isEqualTo("-3");
        assertThat(ConstantValue.normalize("-0")).isEqualTo("0");
        assertThat(ConstantValue.fitsText("-3", 2)).isFalse();
        assertThat(ConstantValue.fitsText("-2", 2)).isTrue();
    }

    @Test
    void displayIgnoresBitsAboveTheWidthAndShowsUnsignedDecimalUpTo64Bits() {
        assertThat(ConstantValue.display(-1L, 4, "hex")).isEqualTo("0xF");
        assertThat(ConstantValue.display(-1L, 4, "decimal")).isEqualTo("15");
        assertThat(ConstantValue.display(-1L, 4, "binary")).isEqualTo("1111");
        assertThat(ConstantValue.display(-1L, 64, "decimal")).isEqualTo("18446744073709551615");
        assertThat(ConstantValue.display(-1L, 64, "signed")).isEqualTo("-1");
    }

    @Test
    void theLongestSignedDisplayIsTheMostNegativeValue() {
        assertThat(ConstantValue.longestDisplay(1, "signed")).isEqualTo(2);
        assertThat(ConstantValue.longestDisplay(4, "signed")).isEqualTo(2);
        assertThat(ConstantValue.longestDisplay(8, "signed")).isEqualTo(4);
        assertThat(ConstantValue.longestDisplay(32, "signed")).isEqualTo(11);
        assertThat(ConstantValue.longestDisplay(64, "signed")).isEqualTo(20);
        assertThat(ConstantValue.longestDisplay(64, "decimal")).isEqualTo(20);
        for (int width = 1; width <= 64; width++) {
            long mostNegative = 1L << (width - 1);
            for (long bits : new long[]{0, mostNegative, mostNegative - 1, -1L}) {
                assertThat(ConstantValue.display(bits, width, "signed").length())
                        .as("%d at %d bits", bits, width)
                        .isLessThanOrEqualTo(ConstantValue.longestDisplay(width, "signed"));
            }
        }
    }

    @Test
    void theEditableTextReadsBackAsTheSameBits() {
        assertThat(ConstantValue.editableText(5, 4, "auto")).isEqualTo("0b0101");
        assertThat(ConstantValue.editableText(5, 8, "auto")).isEqualTo("0x5");
        assertThat(ConstantValue.editableText(0xFF, 8, "signed")).isEqualTo("-1");
        assertThat(ConstantValue.editableText(0xFF, 8, "decimal")).isEqualTo("255");
        for (String format : new String[]{"auto", "binary", "hex", "decimal", "signed"}) {
            for (int width : new int[]{1, 4, 8, 32, 64}) {
                for (long bits : new long[]{0, 1, Value.maskFor(width), 1L << (width - 1)}) {
                    long masked = bits & Value.maskFor(width);
                    assertThat(ConstantValue.bitsOf(ConstantValue.editableText(masked, width, format), width))
                            .as("%d %s at %d", bits, format, width).hasValue(masked);
                }
            }
        }
    }

    @Test
    void randomValuesOfEveryWidthShowAndReadBackInEveryFormat() {
        Random random = new Random(20261018L);
        for (int width = 1; width <= 64; width++) {
            BigInteger modulus = BigInteger.ONE.shiftLeft(width);
            for (int sample = 0; sample < 40; sample++) {
                long bits = random.nextLong() & Value.maskFor(width);
                BigInteger unsigned = new BigInteger(Long.toUnsignedString(bits));
                BigInteger signed = unsigned.testBit(width - 1) ? unsigned.subtract(modulus) : unsigned;
                String description = Long.toHexString(bits) + " at " + width;
                String binary = unsigned.toString(2);
                assertThat(ConstantValue.display(bits, width, "binary")).as(description)
                        .isEqualTo("0".repeat(width - binary.length()) + binary);
                assertThat(ConstantValue.display(bits, width, "hex")).as(description)
                        .isEqualTo("0x" + unsigned.toString(16).toUpperCase(Locale.ROOT));
                assertThat(ConstantValue.display(bits, width, "decimal")).as(description)
                        .isEqualTo(unsigned.toString());
                assertThat(ConstantValue.display(bits, width, "signed")).as(description).isEqualTo(signed.toString());
                assertThat(ConstantValue.signed(bits, width)).as(description).isEqualTo(signed.longValueExact());
                for (String format : ConstantValue.FORMAT_PARAMETER.choices()) {
                    String shown = ConstantValue.display(bits, width, format);
                    assertThat(shown.length()).as("%s %s", description, format)
                            .isLessThanOrEqualTo(ConstantValue.longestDisplay(width, format));
                    assertThat(ConstantValue.bitsOf(ConstantValue.editableText(bits, width, format), width))
                            .as("%s %s", description, format).hasValue(bits);
                }
                assertThat(ConstantValue.bitsOf(signed.toString(), width)).as(description).hasValue(bits);
                assertThat(ConstantValue.fitsText(signed.subtract(modulus).toString(), width)).as(description)
                        .isFalse();
            }
        }
    }

    @Test
    void theRangeOfANegativeNumberEndsAtMinusTwoToTheWidthMinusOne() {
        for (int width = 1; width <= 64; width++) {
            BigInteger mostNegative = BigInteger.ONE.shiftLeft(width - 1).negate();
            assertThat(ConstantValue.fitsText(mostNegative.toString(), width)).as("at %d", width).isTrue();
            assertThat(ConstantValue.fitsText(mostNegative.subtract(BigInteger.ONE).toString(), width))
                    .as("at %d", width).isFalse();
            BigInteger largest = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
            assertThat(ConstantValue.fitsText(largest.toString(), width)).as("at %d", width).isTrue();
            assertThat(ConstantValue.fitsText(largest.add(BigInteger.ONE).toString(), width)).as("at %d", width)
                    .isFalse();
        }
    }

    @Test
    void anImpossibleWidthOrFormatIsAProgrammingError() {
        assertThatThrownBy(() -> ConstantValue.bitsOf("1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.bitsOf("1", 65)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConstantValue.bitsOf("1", 64)).hasValue(1L);
        assertThatThrownBy(() -> ConstantValue.display(1, 4, "octal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.longestDisplay(4, "octal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.normalize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConstantValue.isValidText(null)).isFalse();
        assertThat(ConstantValue.fitsText(null, 8)).isFalse();
        assertThat(ConstantValue.bitsOf(null, 8)).isEmpty();
    }
}
