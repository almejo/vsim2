package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.ui.i18n.Messages;

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * The texts of the «Monitor de bytes» tab (spec §9, mockup §5): a row «t · hex · dec · carácter» per byte, with the
 * control bytes by name (NUL, BEL, BS, HT, LF, FF, CR, ESC, DEL, the rest {@code ^X}), and a dump like
 * {@code hexdump -C}. An undefined byte (D not fully driven) shows {@code ??} and {@code ?}, as the screen does.
 */
final class ByteMonitorText {

    private static final Map<Integer, String> CONTROL_NAMES = Map.of(0, "NUL", 7, "BEL", 8, "BS", 9, "HT", 10, "LF",
            12, "FF", 13, "CR", 27, "ESC", 127, "DEL");
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    /** {@code ^A} is 1 + this. */
    private static final int CARET_OFFSET = '@';
    /** The space is shown as a visible symbol: an empty cell says nothing. */
    private static final String SPACE_SYMBOL = "␠";
    /** A byte of a character outside ASCII (half of a UTF-8 character, a Latin-1 letter). */
    private static final String HIGH_BYTE = "·";
    private static final String UNDEFINED_HEX = "??";
    private static final String UNDEFINED_CHARACTER = "?";
    private static final int BYTES_PER_LINE = 16;
    private static final int HALF_LINE = 8;
    /** Two hex digits and a space per byte, one more space after the eighth, as {@code hexdump -C} lays them out. */
    private static final int HEX_COLUMN_WIDTH = BYTES_PER_LINE * 3 + 1;
    private static final char UNPRINTABLE_IN_DUMP = '.';

    private ByteMonitorText() {
    }

    /** What the «carácter» column shows for {@code value} (or {@link ByteLog#UNDEFINED}). */
    static String characterName(int value) {
        if (value == ByteLog.UNDEFINED) {
            return UNDEFINED_CHARACTER;
        }
        String name = CONTROL_NAMES.get(value);
        if (name != null) {
            return name;
        }
        if (value < FIRST_PRINTABLE) {
            return "^" + (char) (value + CARET_OFFSET);
        }
        if (value > DELETE) {
            return HIGH_BYTE;
        }
        return value == ' ' ? SPACE_SYMBOL : String.valueOf((char) value);
    }

    /** The four columns of {@code entry}: time, hex, decimal and character. */
    static List<String> row(ByteLog.Entry entry) {
        String time = NumberFormat.getIntegerInstance(Messages.locale()).format(entry.time());
        if (entry.undefined()) {
            return List.of(time, UNDEFINED_HEX, UNDEFINED_CHARACTER, UNDEFINED_CHARACTER);
        }
        return List.of(time, String.format("%02X", entry.value()), String.valueOf(entry.value()),
                characterName(entry.value()));
    }

    /** The marker row for {@code lost} bytes the log dropped: the text in the first column, the rest empty. */
    static List<String> lostRow(long lost) {
        return List.of(Messages.format("terminal.monitor.lost", lost), "", "", "");
    }

    /** The bytes as {@code hexdump -C} shows them, offsets counted from the first byte given; no final line feed. */
    static String hexDump(List<ByteLog.Entry> entries) {
        StringBuilder dump = new StringBuilder();
        for (int offset = 0; offset < entries.size(); offset += BYTES_PER_LINE) {
            if (offset > 0) {
                dump.append('\n');
            }
            List<ByteLog.Entry> line = entries.subList(offset, Math.min(entries.size(), offset + BYTES_PER_LINE));
            dump.append(String.format("%08x  ", offset));
            StringBuilder hex = new StringBuilder();
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < line.size(); index++) {
                ByteLog.Entry entry = line.get(index);
                hex.append(entry.undefined() ? UNDEFINED_HEX : String.format("%02x", entry.value())).append(' ');
                if (index == HALF_LINE - 1) {
                    hex.append(' ');
                }
                text.append(entry.value() >= FIRST_PRINTABLE && entry.value() < DELETE ? (char) entry.value()
                        : UNPRINTABLE_IN_DUMP);
            }
            dump.append(String.format("%-" + HEX_COLUMN_WIDTH + "s", hex)).append(" |").append(text).append('|');
        }
        return dump.toString();
    }
}
