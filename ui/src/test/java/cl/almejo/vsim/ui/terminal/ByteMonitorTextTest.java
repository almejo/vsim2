package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The byte monitor's texts: a row per byte and a {@code hexdump -C} dump. */
class ByteMonitorTextTest {

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static List<ByteLog.Entry> entries(String text) {
        List<ByteLog.Entry> entries = new ArrayList<>();
        for (int index = 0; index < text.length(); index++) {
            entries.add(new ByteLog.Entry(index + 1, 10L * (index + 1), text.charAt(index)));
        }
        return entries;
    }

    @Test
    void controlBytesGoByTheirNamesAndTheRestByCaret() {
        assertThat(List.of(0, 7, 8, 9, 10, 12, 13, 27, 127)).extracting(ByteMonitorText::characterName)
                .containsExactly("NUL", "BEL", "BS", "HT", "LF", "FF", "CR", "ESC", "DEL");
        assertThat(ByteMonitorText.characterName(1)).isEqualTo("^A");
        assertThat(ByteMonitorText.characterName(26)).isEqualTo("^Z");
        assertThat(ByteMonitorText.characterName('A')).isEqualTo("A");
        assertThat(ByteMonitorText.characterName(' ')).isEqualTo("␠");
        assertThat(ByteMonitorText.characterName(0xC3)).as("half of a UTF-8 character").isEqualTo("·");
        assertThat(ByteMonitorText.characterName(ByteLog.UNDEFINED)).isEqualTo("?");
    }

    @Test
    void aRowHasTheTimeTheHexTheDecimalAndTheCharacter() {
        assertThat(ByteMonitorText.row(new ByteLog.Entry(1, 12_340, 'A'))).containsExactly("12.340", "41", "65", "A");
        assertThat(ByteMonitorText.row(new ByteLog.Entry(2, 5, 10))).containsExactly("5", "0A", "10", "LF");
        assertThat(ByteMonitorText.row(new ByteLog.Entry(3, 7, ByteLog.UNDEFINED)))
                .containsExactly("7", "??", "?", "?");
    }

    @Test
    void theHexDumpLooksLikeHexdumpC() {
        String dump = ByteMonitorText.hexDump(entries("Hola, mundo!\n? 5\nfin"));

        assertThat(dump.lines()).containsExactly(
                "00000000  48 6f 6c 61 2c 20 6d 75  6e 64 6f 21 0a 3f 20 35  |Hola, mundo!.? 5|",
                "00000010  0a 66 69 6e                                       |.fin|");
    }

    @Test
    void anEmptyDumpIsEmpty() {
        assertThat(ByteMonitorText.hexDump(List.of())).isEmpty();
    }
}
