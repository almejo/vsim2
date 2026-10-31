package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.terminal.KeyEncoding.BackspaceSends;
import cl.almejo.vsim.core.terminal.KeyEncoding.EnterSends;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What each typed character and special key sends to a part, by data width, encoding and options (spec §10). */
class KeyEncodingTest {

    private static final int ESCAPE = 0x1B;

    private static final KeyEncoding UTF8 = new KeyEncoding(8, TerminalEncoding.UTF8, EnterSends.LF,
            BackspaceSends.BS, false);
    private static final KeyEncoding LATIN1 = new KeyEncoding(8, TerminalEncoding.LATIN1, EnterSends.LF,
            BackspaceSends.BS, false);
    private static final KeyEncoding SEVEN_BITS = new KeyEncoding(7, TerminalEncoding.UTF8, EnterSends.LF,
            BackspaceSends.BS, false);
    private static final KeyEncoding SEQUENCES = new KeyEncoding(8, TerminalEncoding.UTF8, EnterSends.LF,
            BackspaceSends.BS, true);

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private static byte[] escape(String rest) {
        byte[] tail = rest.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[tail.length + 1];
        result[0] = (byte) ESCAPE;
        System.arraycopy(tail, 0, result, 1, tail.length);
        return result;
    }

    private static void assertSends(Optional<byte[]> sent, byte[] expected) {
        assertThat(sent).isPresent();
        assertThat(sent.orElseThrow()).containsExactly(expected);
    }

    @Test
    void asciiIsOneByteInEveryEncoding() {
        assertThat(UTF8.encode('A')).containsExactly(bytes('A'));
        assertThat(LATIN1.encode('A')).containsExactly(bytes('A'));
        assertThat(SEVEN_BITS.encode('A')).containsExactly(bytes('A'));
    }

    @Test
    void utf8SendsTwoThreeAndFourByteCharacters() {
        assertThat(UTF8.encode('ñ')).containsExactly(bytes(0xC3, 0xB1));
        assertThat(UTF8.encode('€')).containsExactly(bytes(0xE2, 0x82, 0xAC));
        assertThat(UTF8.encode(0x1F600)).containsExactly(bytes(0xF0, 0x9F, 0x98, 0x80));
    }

    @Test
    void latin1SendsUpTo255AndRefusesTheRest() {
        assertThat(LATIN1.encode('ñ')).containsExactly(bytes(0xF1));
        assertThat(LATIN1.encode(0xFF)).containsExactly(bytes(0xFF));
        assertThat(LATIN1.encode('€')).isEmpty();
    }

    @Test
    void sevenBitsRefusesEverythingAbove127WhateverTheEncoding() {
        assertThat(SEVEN_BITS.encode(0x7F)).containsExactly(bytes(0x7F));
        assertThat(SEVEN_BITS.encode('ñ')).isEmpty();
        KeyEncoding sevenBitsLatin1 = new KeyEncoding(7, TerminalEncoding.LATIN1, EnterSends.LF, BackspaceSends.BS,
                false);
        assertThat(sevenBitsLatin1.encode('ñ')).isEmpty();
    }

    @Test
    void aSurrogateOrAnInvalidCodePointIsNotRepresentable() {
        assertThat(UTF8.encode(0xD800)).isEmpty();
        assertThat(UTF8.encode(0x110000)).isEmpty();
        assertThat(UTF8.encode(-1)).isEmpty();
    }

    @Test
    void enterSendsTheChosenLineEnd() {
        assertSends(UTF8.encode(SpecialKey.ENTER), bytes(0x0A));
        KeyEncoding carriageReturn = new KeyEncoding(8, TerminalEncoding.UTF8, EnterSends.CR, BackspaceSends.BS, false);
        assertSends(carriageReturn.encode(SpecialKey.ENTER), bytes(0x0D));
        KeyEncoding both = new KeyEncoding(8, TerminalEncoding.UTF8, EnterSends.CR_LF, BackspaceSends.BS, false);
        assertSends(both.encode(SpecialKey.ENTER), bytes(0x0D, 0x0A));
    }

    @Test
    void backspaceSendsBackspaceOrDelete() {
        assertSends(UTF8.encode(SpecialKey.BACKSPACE), bytes(0x08));
        KeyEncoding delete = new KeyEncoding(8, TerminalEncoding.UTF8, EnterSends.LF, BackspaceSends.DEL, false);
        assertSends(delete.encode(SpecialKey.BACKSPACE), bytes(0x7F));
    }

    @Test
    void tabSendsATabWithOrWithoutSequences() {
        assertSends(UTF8.encode(SpecialKey.TAB), bytes(0x09));
        assertSends(SEQUENCES.encode(SpecialKey.TAB), bytes(0x09));
    }

    @Test
    void withSequencesTheArrowsHomeEndAndDeleteSendVtSequences() {
        Map<SpecialKey, String> expected = Map.of(
                SpecialKey.UP, "[A", SpecialKey.DOWN, "[B", SpecialKey.RIGHT, "[C", SpecialKey.LEFT, "[D",
                SpecialKey.HOME, "[H", SpecialKey.END, "[F", SpecialKey.DELETE, "[3~");
        expected.forEach((key, sequence) -> assertSends(SEQUENCES.encode(key), escape(sequence)));
    }

    @Test
    void withSequencesTheFunctionKeysSendXtermSequences() {
        Map<SpecialKey, String> expected = Map.ofEntries(
                Map.entry(SpecialKey.F1, "OP"), Map.entry(SpecialKey.F2, "OQ"), Map.entry(SpecialKey.F3, "OR"),
                Map.entry(SpecialKey.F4, "OS"), Map.entry(SpecialKey.F5, "[15~"), Map.entry(SpecialKey.F6, "[17~"),
                Map.entry(SpecialKey.F7, "[18~"), Map.entry(SpecialKey.F8, "[19~"), Map.entry(SpecialKey.F9, "[20~"),
                Map.entry(SpecialKey.F10, "[21~"), Map.entry(SpecialKey.F11, "[23~"),
                Map.entry(SpecialKey.F12, "[24~"));
        expected.forEach((key, sequence) -> assertSends(SEQUENCES.encode(key), escape(sequence)));
    }

    @ParameterizedTest
    @EnumSource(value = SpecialKey.class, names = {"UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "DELETE", "F1", "F5",
            "F12"})
    void withoutSequencesTheEditingAndFunctionKeysSendNothing(SpecialKey key) {
        assertThat(UTF8.encode(key)).isEmpty();
    }

    @Test
    void theKeysThatEditTheQueueSayHow() {
        assertThat(SpecialKey.LEFT.typingEdit()).contains(TypingEdit.CURSOR_LEFT);
        assertThat(SpecialKey.RIGHT.typingEdit()).contains(TypingEdit.CURSOR_RIGHT);
        assertThat(SpecialKey.HOME.typingEdit()).contains(TypingEdit.CURSOR_HOME);
        assertThat(SpecialKey.END.typingEdit()).contains(TypingEdit.CURSOR_END);
        assertThat(SpecialKey.DELETE.typingEdit()).contains(TypingEdit.DELETE);
        assertThat(SpecialKey.UP.typingEdit()).isEmpty();
        assertThat(SpecialKey.ENTER.typingEdit()).isEmpty();
    }

    @Test
    void controlLettersAreCodesOneToTwentySixInEitherCase() {
        assertThat(UTF8.encodeControl('A')).containsExactly(bytes(1));
        assertThat(UTF8.encodeControl('c')).containsExactly(bytes(3));
        assertThat(UTF8.encodeControl('Z')).containsExactly(bytes(26));
        assertThatThrownBy(() -> UTF8.encodeControl('1')).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDataWidthIsSevenOrEight() {
        assertThatThrownBy(() -> new KeyEncoding(6, TerminalEncoding.UTF8, EnterSends.LF, BackspaceSends.BS, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theParametersOfAPartGiveItsEncoding() {
        KeyEncoding encoding = KeyEncoding.forParameters(Map.of(
                KeyEncoding.DATA_WIDTH, 8, KeyEncoding.ENCODING, "latin1", KeyEncoding.ENTER_SENDS, "crlf",
                KeyEncoding.BACKSPACE_SENDS, "del", KeyEncoding.ARROWS_AS_SEQUENCES, true));

        assertThat(encoding).isEqualTo(new KeyEncoding(8, TerminalEncoding.LATIN1, EnterSends.CR_LF,
                BackspaceSends.DEL, true));
    }

    @Test
    void missingParametersTakeTheTecladoDefaults() {
        assertThat(KeyEncoding.forParameters(Map.of())).isEqualTo(UTF8);
        assertThat(KeyEncoding.forParameters(Map.of(KeyEncoding.DATA_WIDTH, "7")).dataWidth()).isEqualTo(7);
    }

    @Test
    void aTextIsOneArrayPerCharacterWithLineBreaksAsEnter() {
        KeyEncoding carriageReturnLineFeed = new KeyEncoding(8, TerminalEncoding.UTF8, EnterSends.CR_LF,
                BackspaceSends.BS, false);

        List<byte[]> characters = carriageReturnLineFeed.encodeText("añ\r\nb\rc\nd\t");

        assertThat(characters).hasSize(9);
        assertThat(characters.get(1)).containsExactly(bytes(0xC3, 0xB1));
        assertThat(characters.get(2)).containsExactly(bytes(0x0D, 0x0A));
        assertThat(characters.get(4)).containsExactly(bytes(0x0D, 0x0A));
        assertThat(characters.get(6)).containsExactly(bytes(0x0D, 0x0A));
        assertThat(characters.get(8)).containsExactly(bytes(0x09));
        assertThat(KeyEncoding.characterCount("añ\r\nb\rc\nd\t")).isEqualTo(9);
    }

    @Test
    void aTextLeavesOutWhatTheEncodingCannotSendAndOtherControls() {
        List<byte[]> characters = SEVEN_BITS.encodeText("a€\u0007b😀");

        assertThat(characters).hasSize(2);
        assertThat(KeyEncoding.characterCount("a€\u0007b😀")).isEqualTo(5);
    }

    @Test
    void escapeAlwaysSendsTheEscapeByte() {
        assertThat(UTF8.encode(SpecialKey.ESCAPE).orElseThrow()).containsExactly((byte) ESCAPE);
        assertThat(SEQUENCES.encode(SpecialKey.ESCAPE).orElseThrow()).containsExactly((byte) ESCAPE);
        assertThat(SpecialKey.ESCAPE.typingEdit()).isEmpty();
    }

    @Test
    void anEncodedSequenceIsACopy() {
        Optional<byte[]> sequence = SEQUENCES.encode(SpecialKey.UP);
        sequence.orElseThrow()[0] = 0;
        assertThat(SEQUENCES.encode(SpecialKey.UP).orElseThrow()[0]).isEqualTo((byte) ESCAPE);
    }
}
