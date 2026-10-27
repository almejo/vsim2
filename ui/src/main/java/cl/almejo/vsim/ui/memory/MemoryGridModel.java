package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MutableMemoryContents;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * The cells of the content editor: an address column, {@link #columns()} value columns and, for words of up to 8 bits,
 * an ASCII column. It edits a copy of the memory's initial contents (never cutting: words beyond the last address stay
 * in the contents, they are just not shown), keeps its own undo / redo stack for the dialog, and can overlay the
 * values of the running simulation and the last access's highlight. Not thread safe: the EDT only.
 */
public final class MemoryGridModel extends AbstractTableModel {

    /** Message key: the cell is not a number in the shown base. */
    public static final String NOT_A_NUMBER = "memory.edit.notNumber";
    /** Message key: the value has more bits than the word. */
    public static final String TOO_WIDE = "memory.edit.tooWide";
    /** Message key: the address is beyond the memory. */
    public static final String OUT_OF_RANGE = "memory.edit.outOfRange";
    /** Message key: the memory is linked, its grid is read-only. */
    public static final String READ_ONLY = "memory.edit.readOnly";

    private static final int HISTORY_LIMIT = 200;

    private final int width;
    private final long words;
    private final int columns;
    private final int addressDigits;
    private MemoryRadix radix;
    private MemoryContents contents;
    private final Deque<MemoryContents> undoStack = new ArrayDeque<>();
    private final Deque<MemoryContents> redoStack = new ArrayDeque<>();
    private boolean readOnly;
    private MemoryContents live;
    private long accessAddress = -1;
    private VisibleState.MemoryAccess access = VisibleState.MemoryAccess.NONE;

    public MemoryGridModel(MemoryContents contents, int width, long words, MemoryRadix radix) {
        this.contents = contents;
        this.width = width;
        this.words = words;
        this.radix = radix;
        this.columns = columnsFor(width);
        this.addressDigits = Math.max(1, (Long.numberOfTrailingZeros(words) + 3) / 4);
    }

    /** 16 columns up to 8-bit words, 8 up to 16, 4 up to 32, 2 beyond. */
    public static int columnsFor(int width) {
        if (width <= 8) {
            return 16;
        }
        if (width <= 16) {
            return 8;
        }
        return width <= 32 ? 4 : 2;
    }

    /** Value columns (not counting the address and ASCII ones). */
    public int columns() {
        return columns;
    }

    public boolean hasAsciiColumn() {
        return width <= 8;
    }

    public long words() {
        return words;
    }

    public int width() {
        return width;
    }

    public MemoryRadix radix() {
        return radix;
    }

    public void setRadix(MemoryRadix radix) {
        this.radix = radix;
        fireTableDataChanged();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        fireTableDataChanged();
    }

    /** The contents as edited so far (the initial contents plus every accepted edit). */
    public MemoryContents edited() {
        return contents;
    }

    /** Replaces the contents (an import) as one undoable edit of the dialog. */
    public void replaceContents(MemoryContents replacement) {
        commit(replacement);
    }

    // Table model

    @Override
    public int getRowCount() {
        return (int) ((words + columns - 1) / columns);
    }

    @Override
    public int getColumnCount() {
        return 1 + columns + (hasAsciiColumn() ? 1 : 0);
    }

    @Override
    public String getColumnName(int column) {
        if (column == 0) {
            return "";
        }
        return column <= columns ? Integer.toHexString(column - 1).toUpperCase(Locale.ROOT) : "";
    }

    public boolean isAddressColumn(int column) {
        return column == 0;
    }

    public boolean isAsciiColumn(int column) {
        return hasAsciiColumn() && column == columns + 1;
    }

    /** The address of a value cell; empty for the address / ASCII columns and for cells past the last address. */
    public OptionalLong addressAt(int row, int column) {
        if (column < 1 || column > columns) {
            return OptionalLong.empty();
        }
        long address = (long) row * columns + (column - 1);
        return address < words ? OptionalLong.of(address) : OptionalLong.empty();
    }

    public int rowOf(long address) {
        return (int) (address / columns);
    }

    public int columnOf(long address) {
        return (int) (address % columns) + 1;
    }

    @Override
    public Object getValueAt(int row, int column) {
        if (column == 0) {
            return hexAddress((long) row * columns);
        }
        if (isAsciiColumn(column)) {
            return asciiOfRow(row);
        }
        OptionalLong address = addressAt(row, column);
        return address.isPresent() ? format(valueAt(address.getAsLong())) : "";
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return !readOnly && addressAt(row, column).isPresent();
    }

    @Override
    public void setValueAt(Object text, int row, int column) {
        OptionalLong address = addressAt(row, column);
        if (address.isPresent()) {
            setCell(address.getAsLong(), String.valueOf(text));
        }
    }

    /** The word shown at {@code address}: the simulation's while one is overlaid, the edited contents' otherwise. */
    public long valueAt(long address) {
        return live != null ? live.word(address) : contents.word(address);
    }

    public String hexAddress(long address) {
        return pad(Long.toHexString(address).toUpperCase(Locale.ROOT), addressDigits);
    }

    /** {@code value} in the shown base (hex and binary padded to the word's digits). */
    public String format(long value) {
        return switch (radix) {
            case HEX -> pad(Long.toHexString(value).toUpperCase(Locale.ROOT), (width + 3) / 4);
            case BINARY -> pad(Long.toBinaryString(value), width);
            case DECIMAL -> Long.toUnsignedString(value);
        };
    }

    private String asciiOfRow(int row) {
        StringBuilder text = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
            OptionalLong address = addressAt(row, column);
            if (address.isPresent()) {
                long value = valueAt(address.getAsLong());
                text.append(value >= 32 && value < 127 ? (char) value : '.');
            }
        }
        return text.toString();
    }

    private static String pad(String digits, int minimum) {
        return digits.length() >= minimum ? digits : "0".repeat(minimum - digits.length()) + digits;
    }

    // Edits

    /** Sets one cell from text in the shown base. Empty when accepted, else the message key of the refusal. */
    public Optional<String> setCell(long address, String text) {
        Optional<String> refusal = refuseEdit(address);
        if (refusal.isPresent()) {
            return refusal;
        }
        OptionalLong value = parseNumber(text, radix.base());
        if (value.isEmpty()) {
            return Optional.of(NOT_A_NUMBER);
        }
        if (!fits(value.getAsLong())) {
            return Optional.of(TOO_WIDE);
        }
        commit(contents.with(address, value.getAsLong()));
        return Optional.empty();
    }

    /**
     * Pastes hex text (VSim's tokens: words separated by spaces or lines, {@code #} comments, {@code @address}
     * jumps) from {@code startAddress} on. Nothing changes when anything is refused; one undo step otherwise.
     */
    public Optional<String> paste(long startAddress, String text) {
        Optional<String> refusal = refuseEdit(startAddress);
        if (refusal.isPresent()) {
            return refusal;
        }
        TreeMap<Long, Long> changes = new TreeMap<>();
        long address = startAddress;
        for (String line : text.split("\\R")) {
            int comment = line.indexOf('#');
            String code = comment < 0 ? line : line.substring(0, comment);
            for (String token : code.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                if (token.startsWith("@")) {
                    OptionalLong jump = parseNumber(token.substring(1), 16);
                    if (jump.isEmpty() || jump.getAsLong() < 0) {
                        return Optional.of(NOT_A_NUMBER);
                    }
                    address = jump.getAsLong();
                    continue;
                }
                OptionalLong value = parseNumber(token, 16);
                if (value.isEmpty()) {
                    return Optional.of(NOT_A_NUMBER);
                }
                if (!fits(value.getAsLong())) {
                    return Optional.of(TOO_WIDE);
                }
                if (address >= words || address < 0) {
                    return Optional.of(OUT_OF_RANGE);
                }
                changes.put(address, value.getAsLong());
                address++;
            }
        }
        if (!changes.isEmpty()) {
            commit(contents.withAll(changes));
        }
        return Optional.empty();
    }

    /** Fills {@code firstAddress..lastAddress} (inclusive) with a value in the shown base; one undo step. */
    public Optional<String> fill(long firstAddress, long lastAddress, String text) {
        Optional<String> refusal = refuseEdit(firstAddress);
        if (refusal.isPresent()) {
            return refusal;
        }
        if (lastAddress < firstAddress || lastAddress >= words) {
            return Optional.of(OUT_OF_RANGE);
        }
        OptionalLong value = parseNumber(text, radix.base());
        if (value.isEmpty()) {
            return Optional.of(NOT_A_NUMBER);
        }
        if (!fits(value.getAsLong())) {
            return Optional.of(TOO_WIDE);
        }
        MutableMemoryContents filled = new MutableMemoryContents(contents);
        filled.fill(firstAddress, lastAddress, value.getAsLong());
        commit(filled.snapshot());
        return Optional.empty();
    }

    private Optional<String> refuseEdit(long address) {
        if (readOnly) {
            return Optional.of(READ_ONLY);
        }
        return address < 0 || address >= words ? Optional.of(OUT_OF_RANGE) : Optional.empty();
    }

    private boolean fits(long value) {
        return width >= Long.SIZE || (value >>> width) == 0;
    }

    /** A number in {@code base} ({@code _} allowed between digits); empty when it is not one, or beyond 64 bits. */
    static OptionalLong parseNumber(String text, int base) {
        String digits = text.trim().replace("_", "");
        if (digits.isEmpty() || digits.startsWith("-") || digits.startsWith("+")) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseUnsignedLong(digits, base));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /** The address typed in «Ir a…»: hex, optionally with {@code 0x} or {@code @}. Empty when it is not one. */
    public OptionalLong parseAddress(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("0x")) {
            trimmed = trimmed.substring(2);
        }
        OptionalLong address = parseNumber(trimmed, 16);
        return address.isPresent() && address.getAsLong() >= 0 && address.getAsLong() < words
                ? address : OptionalLong.empty();
    }

    // Copy

    /**
     * The hex text of the cells from {@code firstRow..lastRow} and {@code firstColumn..lastColumn} (table columns),
     * one line per row, words separated by spaces; the address and ASCII columns and cells past the end are skipped.
     */
    public String copyText(int firstRow, int lastRow, int firstColumn, int lastColumn) {
        List<String> lines = new ArrayList<>();
        for (int row = firstRow; row <= lastRow; row++) {
            List<String> tokens = new ArrayList<>();
            for (int column = Math.max(1, firstColumn); column <= Math.min(columns, lastColumn); column++) {
                OptionalLong address = addressAt(row, column);
                if (address.isPresent()) {
                    tokens.add(pad(Long.toHexString(valueAt(address.getAsLong())).toUpperCase(Locale.ROOT),
                            (width + 3) / 4));
                }
            }
            if (!tokens.isEmpty()) {
                lines.add(String.join(" ", tokens));
            }
        }
        return String.join("\n", lines);
    }

    // Undo / redo inside the dialog

    private void commit(MemoryContents replacement) {
        if (replacement.equals(contents)) {
            return;
        }
        undoStack.push(contents);
        if (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
        contents = replacement;
        fireTableDataChanged();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(contents);
            contents = undoStack.pop();
            fireTableDataChanged();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(contents);
            contents = redoStack.pop();
            fireTableDataChanged();
        }
    }

    // Simulation overlay

    /** Shows the running simulation's words instead of the initial ones (the edits stay in {@link #edited()}). */
    public void showLive(MemoryContents liveContents) {
        this.live = liveContents;
        fireTableDataChanged();
    }

    public void hideLive() {
        if (live != null) {
            live = null;
            fireTableDataChanged();
        }
    }

    public boolean isShowingLive() {
        return live != null;
    }

    /** The last access, for the cell highlight. */
    public void showAccess(VisibleState.MemoryAccess lastAccess, long address) {
        this.access = lastAccess;
        this.accessAddress = address;
        fireTableDataChanged();
    }

    /** The kind of the last access if it touched {@code address}, NONE otherwise. */
    public VisibleState.MemoryAccess accessAt(long address) {
        return address == accessAddress ? access : VisibleState.MemoryAccess.NONE;
    }
}
