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
    @ValueSource(strings = {"", "   ", "-", "--1", "-0x1", "0x", "0b", "0b2", "12a", "0xG", "abc", "_", "0x-1",
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
    void theFormatChoicesKeepTheOldOnesInOrderAndAddTheNewOnes() {
        assertThat(ConstantValue.FORMAT_PARAMETER.name()).isEqualTo("format");
        assertThat(ConstantValue.FORMAT_PARAMETER.defaultValue()).isEqualTo("auto");
        assertThat(ConstantValue.FORMAT_PARAMETER.choices())
                .containsExactly("auto", "binary", "hex", "octal", "decimal", "signed", "ascii", "float", "fixed");
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
                    if (shown.equals("nan")) {
                        continue; // a NaN has many bit patterns and shows as one
                    }
                    assertThat(ConstantValue.bitsOf(ConstantValue.editableText(bits, width, format), width, format, 0))
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
        assertThatThrownBy(() -> ConstantValue.display(1, 4, "bogus")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.longestDisplay(4, "bogus"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConstantValue.normalize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConstantValue.isValidText(null)).isFalse();
        assertThat(ConstantValue.fitsText(null, 8)).isFalse();
        assertThat(ConstantValue.bitsOf(null, 8)).isEmpty();
    }

    @Test
    void asciiShowsPrintableBytesAsQuotedCharactersControlsByNameAndTheRestAsHex() {
        assertThat(ConstantValue.display(0x66, 8, "ascii")).isEqualTo("'f'");
        assertThat(ConstantValue.display(0x41, 8, "ascii")).isEqualTo("'A'");
        assertThat(ConstantValue.display(0x20, 8, "ascii")).isEqualTo("' '");
        assertThat(ConstantValue.display(0x7E, 8, "ascii")).isEqualTo("'~'");
        assertThat(ConstantValue.display(0x0A, 8, "ascii")).isEqualTo("LF");
        assertThat(ConstantValue.display(0x0D, 8, "ascii")).isEqualTo("CR");
        assertThat(ConstantValue.display(0x00, 7, "ascii")).isEqualTo("NUL");
        assertThat(ConstantValue.display(0x07, 8, "ascii")).isEqualTo("BEL");
        assertThat(ConstantValue.display(0x1B, 8, "ascii")).isEqualTo("ESC");
        assertThat(ConstantValue.display(0x7F, 8, "ascii")).isEqualTo("DEL");
        assertThat(ConstantValue.display(0xE9, 8, "ascii")).isEqualTo("0xE9");
        assertThat(ConstantValue.display(0x141, 12, "ascii")).isEqualTo("0x141");
    }

    @Test
    void asciiHasALongestDisplay() {
        assertThat(ConstantValue.longestDisplay(8, "ascii")).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {"0o17|0o17|15", "0O777|0o777|511", "0o0|0o0|0", "'A'|'A'|65", "' '|' '|32",
            "'''|'''|39"})
    void octalAndCharacterTextsParseAndNormalize(String text, String normalized, long value) {
        assertThat(ConstantValue.isValidText(text)).isTrue();
        assertThat(ConstantValue.normalize(text)).isEqualTo(normalized);
        assertThat(ConstantValue.bitsOf(text, 16)).hasValue(value);
    }

    @Test
    void octalAndCharactersHaveTheirLimits() {
        assertThat(ConstantValue.bitsOf("0o400", 8)).isEmpty();
        assertThat(ConstantValue.bitsOf("0o377", 8)).hasValue(255L);
        assertThat(ConstantValue.bitsOf("'A'", 6)).isEmpty();
        assertThat(ConstantValue.isValidText("'AB'")).isFalse();
        assertThat(ConstantValue.isValidText("'é'")).isFalse();
        assertThat(ConstantValue.display(15, 8, "octal")).isEqualTo("0o17");
        assertThat(ConstantValue.display(0, 8, "octal")).isEqualTo("0o0");
        assertThat(ConstantValue.longestDisplay(8, "octal")).isEqualTo(5);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {"octal|0o17", "ascii|'A'", "ascii|LF", "ascii|0xE9", "decimal|42",
            "signed|-5", "hex|0x2A", "binary|0b101"})
    void everyFormatsEditableTextReadsBackAsTheSameBits(String format, String typed) {
        long bits = ConstantValue.bitsOf(typed, 8, format, 0).orElseThrow();
        assertThat(ConstantValue.bitsOf(ConstantValue.editableText(bits, 8, format), 8, format, 0)).hasValue(bits);
    }

    @Test
    void floatShowsTheIeeeValueOfItsWidth() {
        assertThat(ConstantValue.display(0x3FC00000L, 32, "float")).isEqualTo("1.5");
        assertThat(ConstantValue.display(0x3E00, 16, "float")).isEqualTo("1.5");
        assertThat(ConstantValue.display(0x3FF8000000000000L, 64, "float")).isEqualTo("1.5");
        assertThat(ConstantValue.display(0xC0200000L, 32, "float")).isEqualTo("-2.5");
        assertThat(ConstantValue.display(0x7F800000L, 32, "float")).isEqualTo("inf");
        assertThat(ConstantValue.display(0xFF800000L, 32, "float")).isEqualTo("-inf");
        assertThat(ConstantValue.display(0x7FC00000L, 32, "float")).isEqualTo("nan");
        assertThat(ConstantValue.display(0x7C00, 16, "float")).isEqualTo("inf");
        assertThat(ConstantValue.display(0x7E00, 16, "float")).isEqualTo("nan");
        // the shortest digits that read back as the same half, not the digits of the exact float
        assertThat(ConstantValue.display(0x2E66, 16, "float")).isEqualTo("0.1");
    }

    @Test
    void floatAtAnotherWidthShowsHexBecauseThereIsNoFormatToRead() {
        assertThat(ConstantValue.resolveFormat("float", 8)).isEqualTo("hex");
        assertThat(ConstantValue.display(0x3C, 8, "float")).isEqualTo("0x3C");
        assertThat(ConstantValue.display(0x234, 12, "float")).isEqualTo("0x234");
        assertThat(ConstantValue.longestDisplay(12, "float")).isEqualTo(5);
        assertThat(ConstantValue.bitsOf("1.5", 12, "float", 0)).isEmpty();
        assertThat(ConstantValue.bitsOf("0x3C", 8, "float", 0)).hasValue(0x3CL);
        assertThat(ConstantValue.resolveFormat("float", 16)).isEqualTo("float");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"16|1.5|15872", "16|0.1|11878", "32|1.5|1069547520", "32|-2.5|3223322624",
            "64|1.5|4609434218613702656", "32|1e3|1148846080", "32|3|1077936128", "32|inf|2139095040",
            "32|-INF|4286578688", "16|inf|31744"})
    void floatTextReadsIntoTheWidthsFormat(int width, String text, long expected) {
        assertThat(ConstantValue.isValidText(text, "float")).isTrue();
        assertThat(ConstantValue.bitsOf(text, width, "float", 0)).hasValue(expected);
    }

    @Test
    void floatTextThatDoesNotFitIsRejected() {
        assertThat(ConstantValue.bitsOf("1e50", 32, "float", 0)).isEmpty();
        assertThat(ConstantValue.bitsOf("70000", 16, "float", 0)).isEmpty();
        assertThat(ConstantValue.bitsOf("1.5abc", 32, "float", 0)).isEmpty();
        assertThat(ConstantValue.isValidText("1.5", "hex")).isFalse();
        assertThat(ConstantValue.bitsOf("1.5", 32, "hex", 0)).isEmpty();
        assertThat(ConstantValue.bitsOf("nan", 32, "float", 0)).hasValue(0x7FC00000L);
    }

    @Test
    void everyFloatThatIsNotNanReadsBackFromItsDisplay() {
        Random random = new Random(7);
        for (int round = 0; round < 500; round++) {
            for (int width : new int[]{16, 32, 64}) {
                long bits = random.nextLong() & Value.maskFor(width);
                String shown = ConstantValue.display(bits, width, "float");
                if (shown.equals("nan")) {
                    continue;
                }
                assertThat(ConstantValue.bitsOf(shown, width, "float", 0)).as("%d bits: %s", width, shown)
                        .hasValue(bits);
            }
        }
    }

    @Test
    void fixedShowsTwosComplementWithBitsAfterThePoint() {
        assertThat(ConstantValue.display(0b0110, 8, "fixed", 1)).isEqualTo("3.0");
        assertThat(ConstantValue.display(0b0111, 8, "fixed", 1)).isEqualTo("3.5");
        assertThat(ConstantValue.display(0xFF, 8, "fixed", 4)).isEqualTo("-0.0625");
        assertThat(ConstantValue.display(0x80, 8, "fixed", 0)).isEqualTo("-128");
        assertThat(ConstantValue.display(0x80, 8, "fixed", 7)).isEqualTo("-1.0");
        assertThat(ConstantValue.display(0x40, 8, "fixed", 8)).isEqualTo("0.25");
        // more fraction bits than width: clamped to the width
        assertThat(ConstantValue.display(0x40, 8, "fixed", 20)).isEqualTo("0.25");
    }

    @Test
    void fixedTextReadsIntoThePartsFormat() {
        assertThat(ConstantValue.bitsOf("3.5", 8, "fixed", 1)).hasValue(7L);
        assertThat(ConstantValue.bitsOf("-0.0625", 8, "fixed", 4)).hasValue(0xFFL);
        assertThat(ConstantValue.bitsOf("2", 8, "fixed", 4)).hasValue(32L);
        assertThat(ConstantValue.bitsOf("0.3", 8, "fixed", 2)).hasValue(1L);
        assertThat(ConstantValue.bitsOf("0x7F", 8, "fixed", 4)).hasValue(0x7FL);
        // out of the signed range of 8 bits with 4 fraction bits: -8 .. 7.9375
        assertThat(ConstantValue.bitsOf("8", 8, "fixed", 4)).isEmpty();
        assertThat(ConstantValue.bitsOf("-8", 8, "fixed", 4)).hasValue(0x80L);
        assertThat(ConstantValue.bitsOf("-8.07", 8, "fixed", 4)).isEmpty();
        assertThat(ConstantValue.isValidText("1.5", "fixed")).isTrue();
        assertThat(ConstantValue.isValidText("1.5e3", "fixed")).isTrue();
    }

    @Test
    void everyFixedValueReadsBackFromItsDisplay() {
        for (int width : new int[]{1, 3, 8, 13, 32, 64}) {
            for (int fraction : new int[]{0, 1, width / 2, width}) {
                for (long bits : new long[]{0, 1, Value.maskFor(width), 1L << (width - 1),
                        0x2AAAAAAAAAAAAAAAL & Value.maskFor(width)}) {
                    String shown = ConstantValue.display(bits & Value.maskFor(width), width, "fixed", fraction);
                    assertThat(ConstantValue.bitsOf(shown, width, "fixed", fraction))
                            .as("width %d fraction %d shown %s", width, fraction, shown)
                            .hasValue(bits & Value.maskFor(width));
                    assertThat(shown.length()).isLessThanOrEqualTo(ConstantValue.longestDisplay(width, "fixed",
                            fraction));
                }
            }
        }
    }

    @Test
    void controlNamesAreReadBackInAsciiOnly() {
        assertThat(ConstantValue.bitsOf("LF", 8, "ascii", 0)).hasValue(10L);
        assertThat(ConstantValue.bitsOf("DEL", 8, "ascii", 0)).hasValue(127L);
        assertThat(ConstantValue.bitsOf("LF", 8, "hex", 0)).isEmpty();
        assertThat(ConstantValue.isValidText("LF")).isFalse();
    }

    @Test
    void fractionalTextIsKeptAsTypedByTheNormalizer() {
        assertThat(ConstantValue.normalize(" 1.5 ")).isEqualTo("1.5");
        assertThat(ConstantValue.normalize("INF")).isEqualTo("inf");
        assertThat(ConstantValue.normalize("0O17")).isEqualTo("0o17");
        assertThatThrownBy(() -> ConstantValue.normalize("1.5x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFormatChoicesIncludeEveryNewFormat() {
        assertThat(ConstantValue.FORMAT_PARAMETER.choices())
                .containsExactly("auto", "binary", "hex", "octal", "decimal", "signed", "ascii", "float", "fixed");
        assertThat(ConstantValue.FORMAT_PARAMETER.displayOnly()).isTrue();
        assertThat(ConstantValue.READING_FORMAT_PARAMETER.displayOnly()).isFalse();
    }
}
