package cl.almejo.vsim.core.terminal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** UTF-8 decoding one byte at a time: complete characters with their bytes, U+FFFD for bad ones. */
class Utf8DecoderTest {

    private record Decoded(int codePoint, String bytes) {
    }

    private static List<Decoded> decode(String hexBytes) {
        Utf8Decoder decoder = new Utf8Decoder();
        List<Decoded> decoded = new ArrayList<>();
        for (byte value : HexFormat.of().parseHex(hexBytes)) {
            decoder.feed(value & 0xFF, (codePoint, bytes) ->
                    decoded.add(new Decoded(codePoint, HexFormat.of().formatHex(bytes))));
        }
        return decoded;
    }

    @Test
    void asciiPassesThroughWithItsByte() {
        assertThat(decode("41")).containsExactly(new Decoded('A', "41"));
    }

    @Test
    void twoThreeAndFourByteCharactersKeepTheirBytes() {
        assertThat(decode("c3b1" + "e282ac" + "f09f9880")).containsExactly(new Decoded(0xF1, "c3b1"),
                new Decoded(0x20AC, "e282ac"), new Decoded(0x1F600, "f09f9880"));
    }

    @Test
    void nothingComesOutUntilTheLastByteArrives() {
        Utf8Decoder decoder = new Utf8Decoder();
        List<Integer> codePoints = new ArrayList<>();
        decoder.feed(0xE2, (codePoint, bytes) -> codePoints.add(codePoint));
        decoder.feed(0x82, (codePoint, bytes) -> codePoints.add(codePoint));
        assertThat(codePoints).isEmpty();
        assertThat(decoder.isPending()).isTrue();
        decoder.feed(0xAC, (codePoint, bytes) -> codePoints.add(codePoint));
        assertThat(codePoints).containsExactly(0x20AC);
        assertThat(decoder.isPending()).isFalse();
    }

    @Test
    void aLoneContinuationByteIsAReplacementCharacter() {
        assertThat(decode("80" + "41")).containsExactly(new Decoded(0xFFFD, "80"), new Decoded('A', "41"));
    }

    @Test
    void aCutSequenceIsReplacedAndTheNextByteStillDecodes() {
        assertThat(decode("e282" + "41" + "c3b1")).containsExactly(new Decoded(0xFFFD, "e282"),
                new Decoded('A', "41"), new Decoded(0xF1, "c3b1"));
    }

    @Test
    void aCutSequenceFollowedByANewLeadByteStartsTheNewCharacter() {
        assertThat(decode("c3" + "c3b1")).containsExactly(new Decoded(0xFFFD, "c3"), new Decoded(0xF1, "c3b1"));
    }

    @Test
    void overlongSurrogateAndTooHighSequencesAreRejected() {
        assertThat(decode("c0")).containsExactly(new Decoded(0xFFFD, "c0"));
        assertThat(decode("e080")).containsExactly(new Decoded(0xFFFD, "e0"), new Decoded(0xFFFD, "80"));
        assertThat(decode("eda0")).containsExactly(new Decoded(0xFFFD, "ed"), new Decoded(0xFFFD, "a0"));
        assertThat(decode("f490")).containsExactly(new Decoded(0xFFFD, "f4"), new Decoded(0xFFFD, "90"));
        assertThat(decode("ff")).containsExactly(new Decoded(0xFFFD, "ff"));
    }

    @Test
    void resetForgetsAHalfCharacter() {
        Utf8Decoder decoder = new Utf8Decoder();
        decoder.feed(0xC3, (codePoint, bytes) -> {
        });
        decoder.reset();
        List<Integer> codePoints = new ArrayList<>();
        decoder.feed(0x41, (codePoint, bytes) -> codePoints.add(codePoint));
        assertThat(codePoints).containsExactly((int) 'A');
    }
}
