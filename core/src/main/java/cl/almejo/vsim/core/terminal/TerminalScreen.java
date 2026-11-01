package cl.almejo.vsim.core.terminal;

import cl.almejo.vsim.core.terminal.ColorReference.PaletteIndex;
import cl.almejo.vsim.core.terminal.ColorReference.Rgb;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.Action;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.ControlSequence;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.Escape;
import cl.almejo.vsim.core.terminal.ControlSequenceParser.Execute;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * A Terminal's screen: the cells, the cursor, the history, the transcript and the byte log, written one byte at a time
 * (spec §5, §6, §11). Mutable and owned by the simulation thread; the UI reads {@link #frame()}, an immutable copy
 * rebuilt at most once per {@link #version()}.
 *
 * <p>Both modes share one model. The cursor is the place of the next character; after a character on the last column
 * it stays there with a <em>pending wrap</em>, and the next printable character first goes to the next row (VT style,
 * and exactly Logisim's «commit when the row is full»). Writing a byte is amortised O(1): only scrolling moves rows,
 * and it moves references, never cells.
 */
public final class TerminalScreen {

    /** How many characters {@link #transcript()} keeps. */
    public static final int TRANSCRIPT_LIMIT = Transcript.LIMIT;

    private static final int BELL = 0x07;
    private static final int BACKSPACE = 0x08;
    private static final int TAB = 0x09;
    private static final int LINE_FEED = 0x0A;
    private static final int FORM_FEED = 0x0C;
    private static final int CARRIAGE_RETURN = 0x0D;
    private static final int ESCAPE = ControlSequenceParser.ESCAPE;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    /** C1 controls (U+0080–U+009F) are ignored, as Logisim's {@code Character.isISOControl}. */
    private static final int LAST_C1_CONTROL = 0x9F;
    private static final int UNDEFINED_CHARACTER = '?';
    private static final byte[] NO_BYTES = {};
    /** DECTCEM, {@code ESC[?25h} / {@code ESC[?25l}. */
    private static final int CURSOR_VISIBILITY_MODE = 25;

    private final TerminalSettings settings;
    private final TerminalCell[][] cells;
    private final ArrayDeque<TerminalCell[]> history = new ArrayDeque<>();
    private final Transcript transcript;
    private final ByteLog byteLog;
    private final Utf8Decoder decoder = new Utf8Decoder();
    private final ControlSequenceParser parser = new ControlSequenceParser();
    private final Utf8Decoder.Sink characterSink = this::character;

    private int cursorRow;
    private int cursorColumn;
    private boolean pendingWrap;
    private boolean cursorVisible = true;
    private CellStyle style = CellStyle.PLAIN;
    private TerminalCell erasedCell = TerminalCell.BLANK;
    private SavedCursor savedCursor = SavedCursor.HOME;
    private long time;
    private long version;
    private long bellCount;
    private long printedCount;
    private long scrollOrClearCount;
    private CellPosition lastWritten;
    private long lastWrittenSerial;
    private int lastPrintedCodePoint = ' ';
    private TerminalFrame frame;

    /** What DECSC and {@code ESC[s} keep. */
    private record SavedCursor(int row, int column, boolean pendingWrap, CellStyle style) {
        static final SavedCursor HOME = new SavedCursor(0, 0, false, CellStyle.PLAIN);
    }

    /** An empty screen with the cursor at the top-left corner. */
    public TerminalScreen(TerminalSettings settings) {
        this(settings, new Transcript(), new ByteLog());
    }

    private TerminalScreen(TerminalSettings settings, Transcript transcript, ByteLog byteLog) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.transcript = transcript;
        this.byteLog = byteLog;
        cells = new TerminalCell[settings.rows()][];
        for (int row = 0; row < settings.rows(); row++) {
            cells[row] = blankRow();
        }
    }

    /** The settings this screen was built with. */
    public TerminalSettings settings() {
        return settings;
    }

    /** Receives a data byte at simulation time {@code time}; only the low «Ancho del dato» bits are read. */
    public void write(int byteValue, long time) {
        int value = byteValue & settings.dataMask();
        byteLog.append(time, value);
        this.time = time;
        if (value <= DELETE && !decoder.isPending()) {
            character(value, Utf8Decoder.asciiBytes(value));
        } else if (settings.encoding() == TerminalEncoding.LATIN1) {
            character(value, new byte[] {(byte) value});
        } else {
            decoder.feed(value, characterSink);
        }
    }

    /** Receives an undefined or high-impedance data byte: writes {@code ?} as Logisim does, abandoning any sequence. */
    public void writeUndefined(long time) {
        byteLog.append(time, ByteLog.UNDEFINED);
        this.time = time;
        decoder.reset();
        parser.reset();
        print(UNDEFINED_CHARACTER, NO_BYTES);
    }

    /** CLR: empties the screen and the history and homes the cursor. The transcript and the byte log stay. */
    public void clear() {
        history.clear();
        eraseRows(0, settings.rows());
        moveCursor(0, 0);
        lastWritten = null;
        changed();
    }

    /**
     * A new screen with other settings that keeps this one's text, applying Logisim's {@code TtyState.updateSize} rule
     * in every mode: added rows and columns are blank; removing rows keeps the top rows while the cursor still fits,
     * else the rows up to the cursor's (the rows above go to the history); removing columns trims every row. History,
     * transcript, byte log, counters and style are kept; a half-received character or sequence is dropped.
     */
    public TerminalScreen adoptedWith(TerminalSettings newSettings) {
        TerminalScreen adopted = new TerminalScreen(newSettings, transcript.copy(), byteLog.copy());
        int firstKept = Math.max(0, cursorRow - (newSettings.rows() - 1));
        history.forEach(adopted::keepInHistory);
        for (int row = 0; row < firstKept; row++) {
            adopted.keepInHistory(cells[row].clone());
        }
        for (int row = firstKept; row < Math.min(settings.rows(), firstKept + newSettings.rows()); row++) {
            adopted.copyRow(cells[row], row - firstKept);
        }
        adopted.placeAdoptedCursor(cursorRow - firstKept, pendingWrap ? settings.columns() : cursorColumn);
        adopted.cursorVisible = cursorVisible;
        adopted.style = style;
        adopted.erasedCell = TerminalCell.blank(style.erasing());
        adopted.bellCount = bellCount;
        adopted.printedCount = printedCount;
        adopted.scrollOrClearCount = scrollOrClearCount;
        adopted.lastWrittenSerial = lastWrittenSerial;
        adopted.adoptLastWritten(lastWritten, firstKept);
        adopted.time = time;
        adopted.version = version + 1;
        return adopted;
    }

    /** Changes on every visible change (cells, cursor, bells), never on bytes that change nothing on the screen. */
    public long version() {
        return version;
    }

    /** An immutable copy of the screen, built at most once per {@link #version()}. */
    public TerminalFrame frame() {
        if (frame == null || frame.version() != version) {
            frame = new TerminalFrame(settings.rows(), settings.columns(), Arrays.asList(cells), cursorRow,
                    cursorColumn, cursorVisible, Optional.ofNullable(lastWritten), lastWrittenSerial, version,
                    bellCount, printedCount, scrollOrClearCount);
        }
        return frame;
    }

    /**
     * The last {@code maximumLines} rows that scrolled off the top, oldest first, in an unmodifiable list. The rows are
     * shared, not copied (a Teletipo's 10 000 lines go into a frame every 16 ms): a row never changes once it scrolled
     * off, and readers must never write into one.
     */
    public List<TerminalCell[]> history(int maximumLines) {
        if (maximumLines < 0) {
            throw new IllegalArgumentException("maximumLines must not be negative: " + maximumLines);
        }
        int skipped = Math.max(0, history.size() - maximumLines);
        List<TerminalCell[]> lines = new ArrayList<>(history.size() - skipped);
        Iterator<TerminalCell[]> rows = history.iterator();
        for (int index = 0; rows.hasNext(); index++) {
            TerminalCell[] row = rows.next();
            if (index >= skipped) {
                lines.add(row);
            }
        }
        return Collections.unmodifiableList(lines);
    }

    /** The last {@link #TRANSCRIPT_LIMIT} characters received, with {@code \n} for line feeds and BS applied. */
    public String transcript() {
        return transcript.text();
    }

    /**
     * Whether {@link #transcript()} ends with {@code suffix} («Detener al recibir»), checked after every byte: costs
     * the suffix's length, never a copy of the transcript.
     */
    public boolean transcriptEndsWith(String suffix) {
        return suffix.length() <= TRANSCRIPT_LIMIT && transcript.endsWith(suffix);
    }

    /** Every byte received, for the byte monitor, the sounds and «Guardar salida». */
    public ByteLog byteLog() {
        return byteLog;
    }

    /** BEL bytes received so far. */
    public long bellCount() {
        return bellCount;
    }

    /** Characters placed on the screen so far. */
    public long printedCount() {
        return printedCount;
    }

    /** The character placed on the screen last ({@link #printedCount()} counts them); a space before the first. */
    public int lastPrintedCodePoint() {
        return lastPrintedCodePoint;
    }

    /**
     * Gives {@code action} the character of every cell of the screen (not the history) that a byte wrote, row by row;
     * a character in several cells comes several times.
     */
    public void forEachWrittenCodePoint(IntConsumer action) {
        for (TerminalCell[] row : cells) {
            for (TerminalCell cell : row) {
                if (cell.written()) {
                    action.accept(cell.codePoint());
                }
            }
        }
    }

    private void character(int codePoint, byte[] bytes) {
        if (!parser.inGround() || (codePoint == ESCAPE && settings.mode() != TerminalMode.LOGISIM)) {
            act(parser.feed(codePoint));
        } else if (codePoint < FIRST_PRINTABLE || (codePoint >= DELETE && codePoint <= LAST_C1_CONTROL)) {
            control(codePoint);
        } else {
            print(codePoint, bytes);
        }
    }

    private void act(Action action) {
        switch (action) {
            case Execute execute -> control(execute.control());
            case Escape escape -> escape(escape.finalCharacter());
            case ControlSequence sequence -> controlSequence(sequence);
            case ControlSequenceParser.Pending pending -> {
            }
        }
    }

    private void control(int control) {
        switch (control) {
            case LINE_FEED -> lineFeedFromControl();
            case CARRIAGE_RETURN -> carriageReturnFromControl();
            case BACKSPACE -> backspace();
            case FORM_FEED -> formFeed();
            case TAB -> tab();
            case BELL -> bell();
            default -> {
            }
        }
    }

    private boolean logisim() {
        return settings.mode() == TerminalMode.LOGISIM;
    }

    private void lineFeedFromControl() {
        transcript.newLine();
        if (settings.lineFeedReturns()) {
            carriageReturn();
        }
        lineFeed();
    }

    /** Logisim treats CR as a new line; VT modes only go back to the start of the row. */
    private void carriageReturnFromControl() {
        carriageReturn();
        if (logisim()) {
            transcript.newLine();
            lineFeed();
        }
    }

    private void formFeed() {
        if (!settings.formFeedClears()) {
            lineFeedFromControl();
            return;
        }
        eraseRows(0, settings.rows());
        moveCursor(0, 0);
        lastWritten = null;
        changed();
    }

    /**
     * Steps back one column (never up a row). On a full row with a pending wrap the cursor is logically past the last
     * column, so it lands on the last column: Logisim then erases the row's last character.
     */
    private void backspace() {
        transcript.backspace();
        int target;
        if (pendingWrap) {
            target = cursorColumn;
        } else if (cursorColumn == 0) {
            return;
        } else {
            target = cursorColumn - 1;
        }
        pendingWrap = false;
        cursorColumn = target;
        if (settings.backspaceErases()) {
            cells[cursorRow][target] = erasedCell;
        }
        changed();
    }

    private void tab() {
        if (logisim() || pendingWrap) {
            return;
        }
        int tabWidth = settings.tabWidth();
        moveCursor(cursorRow, Math.min(settings.columns() - 1, (cursorColumn / tabWidth + 1) * tabWidth));
    }

    private void bell() {
        bellCount++;
        changed();
    }

    private void print(int codePoint, byte[] bytes) {
        transcript.append(codePoint);
        if (pendingWrap) {
            if (!settings.wrap()) {
                return;
            }
            carriageReturn();
            lineFeed();
        }
        cells[cursorRow][cursorColumn] = new TerminalCell(codePoint, style, bytes, time);
        lastPrintedCodePoint = codePoint;
        lastWritten = new CellPosition(cursorRow, cursorColumn);
        lastWrittenSerial++;
        printedCount++;
        if (cursorColumn == settings.columns() - 1) {
            pendingWrap = true;
        } else {
            cursorColumn++;
        }
        changed();
    }

    private void carriageReturn() {
        moveCursor(cursorRow, 0);
    }

    private void lineFeed() {
        if (cursorRow < settings.rows() - 1) {
            moveCursor(cursorRow + 1, cursorColumn);
        } else {
            pendingWrap = false;
            scrollUp();
        }
    }

    private void scrollUp() {
        scrollOrClearCount++;
        keepInHistory(cells[0]);
        System.arraycopy(cells, 1, cells, 0, cells.length - 1);
        cells[cells.length - 1] = blankRow();
        if (lastWritten != null) {
            lastWritten = lastWritten.row() == 0 ? null
                    : new CellPosition(lastWritten.row() - 1, lastWritten.column());
        }
        changed();
    }

    private void keepInHistory(TerminalCell[] row) {
        if (settings.scrollbackLines() == 0) {
            return;
        }
        history.addLast(row);
        if (history.size() > settings.scrollbackLines()) {
            history.removeFirst();
        }
    }

    private void escape(int finalCharacter) {
        switch (finalCharacter) {
            case '7' -> saveCursor();
            case '8' -> restoreCursor();
            case 'c' -> resetToInitialState();
            default -> {
            }
        }
    }

    private void controlSequence(ControlSequence sequence) {
        if (sequence.privateMarker() == '?') {
            privateMode(sequence);
            return;
        }
        if (sequence.privateMarker() != ControlSequence.NO_PRIVATE_MARKER) {
            return;
        }
        int count = Math.max(1, sequence.parameter(0, 1));
        switch (sequence.finalCharacter()) {
            case 'A' -> cursorUp(count);
            case 'B' -> moveCursor(Math.min(settings.rows() - 1, cursorRow + count), cursorColumn);
            case 'C' -> moveCursor(cursorRow, Math.min(settings.columns() - 1, cursorColumn + count));
            case 'D' -> moveCursor(cursorRow, Math.max(0, cursorColumn - count));
            case 'H', 'f' -> cursorPosition(sequence);
            case 'J' -> eraseInDisplay(sequence.parameter(0, 0));
            case 'K' -> eraseInLine(sequence.parameter(0, 0));
            case 'm' -> selectGraphicRendition(sequence);
            case 's' -> saveCursor();
            case 'u' -> restoreCursor();
            default -> {
            }
        }
    }

    private void privateMode(ControlSequence sequence) {
        if (sequence.parameter(0, 0) != CURSOR_VISIBILITY_MODE) {
            return;
        }
        boolean visible = switch (sequence.finalCharacter()) {
            case 'h' -> true;
            case 'l' -> false;
            default -> cursorVisible;
        };
        if (visible != cursorVisible) {
            cursorVisible = visible;
            changed();
        }
    }

    private boolean teletype() {
        return settings.mode() == TerminalMode.TELETYPE;
    }

    private void cursorUp(int count) {
        if (!teletype()) {
            moveCursor(Math.max(0, cursorRow - count), cursorColumn);
        }
    }

    private void cursorPosition(ControlSequence sequence) {
        int row = Math.min(settings.rows(), Math.max(1, sequence.parameter(0, 1))) - 1;
        int column = Math.min(settings.columns(), Math.max(1, sequence.parameter(1, 1))) - 1;
        if (teletype() && row < cursorRow) {
            return;
        }
        moveCursor(row, column);
    }

    private void eraseInDisplay(int mode) {
        switch (mode) {
            case 0 -> {
                eraseColumns(cursorRow, cursorColumn, settings.columns());
                eraseRows(cursorRow + 1, settings.rows());
            }
            case 1 -> {
                eraseRows(0, cursorRow);
                eraseColumns(cursorRow, 0, cursorColumn + 1);
            }
            case 2 -> eraseRows(0, settings.rows());
            case 3 -> {
                eraseRows(0, settings.rows());
                history.clear();
            }
            default -> {
                return;
            }
        }
        changed();
    }

    private void eraseInLine(int mode) {
        switch (mode) {
            case 0 -> eraseColumns(cursorRow, cursorColumn, settings.columns());
            case 1 -> eraseColumns(cursorRow, 0, cursorColumn + 1);
            case 2 -> eraseColumns(cursorRow, 0, settings.columns());
            default -> {
                return;
            }
        }
        changed();
    }

    private void selectGraphicRendition(ControlSequence sequence) {
        style = SelectGraphicRendition.apply(style, sequence);
        erasedCell = TerminalCell.blank(style.erasing());
    }

    private void saveCursor() {
        savedCursor = new SavedCursor(cursorRow, cursorColumn, pendingWrap, style);
    }

    private void restoreCursor() {
        moveCursor(Math.min(savedCursor.row(), settings.rows() - 1),
                Math.min(savedCursor.column(), settings.columns() - 1));
        pendingWrap = savedCursor.pendingWrap();
        style = savedCursor.style();
        erasedCell = TerminalCell.blank(style.erasing());
    }

    /** RIS ({@code ESC c}): a clean screen, default style, visible cursor at home. The history stays. */
    private void resetToInitialState() {
        style = CellStyle.PLAIN;
        erasedCell = TerminalCell.BLANK;
        savedCursor = SavedCursor.HOME;
        cursorVisible = true;
        eraseRows(0, settings.rows());
        moveCursor(0, 0);
        lastWritten = null;
        changed();
    }

    /** Moves the cursor, ending any pending wrap; the version changes only when the position does. */
    private void moveCursor(int row, int column) {
        pendingWrap = false;
        if (row != cursorRow || column != cursorColumn) {
            cursorRow = row;
            cursorColumn = column;
            changed();
        }
    }

    private void eraseRows(int fromRow, int toRow) {
        if (fromRow == 0 && toRow == settings.rows()) {
            scrollOrClearCount++;
        }
        for (int row = fromRow; row < toRow; row++) {
            Arrays.fill(cells[row], erasedCell);
        }
    }

    private void eraseColumns(int row, int fromColumn, int toColumn) {
        Arrays.fill(cells[row], fromColumn, Math.min(toColumn, settings.columns()), erasedCell);
    }

    private TerminalCell[] blankRow() {
        TerminalCell[] row = new TerminalCell[settings.columns()];
        Arrays.fill(row, erasedCell);
        return row;
    }

    private void copyRow(TerminalCell[] source, int row) {
        System.arraycopy(source, 0, cells[row], 0, Math.min(source.length, settings.columns()));
    }

    /** Places the cursor of an adopted screen; a column at or past the right edge becomes a pending wrap. */
    private void placeAdoptedCursor(int row, int logicalColumn) {
        cursorRow = row;
        if (logicalColumn >= settings.columns()) {
            cursorColumn = settings.columns() - 1;
            pendingWrap = true;
        } else {
            cursorColumn = logicalColumn;
            pendingWrap = false;
        }
    }

    private void adoptLastWritten(CellPosition previous, int rowsDropped) {
        if (previous == null) {
            return;
        }
        int row = previous.row() - rowsDropped;
        if (row >= 0 && row < settings.rows() && previous.column() < settings.columns()) {
            lastWritten = new CellPosition(row, previous.column());
        }
    }

    private void changed() {
        version++;
    }

    /** SGR ({@code ESC[...m}, spec §6) as a pure function of the current style and the sequence's parameters. */
    private static final class SelectGraphicRendition {

        private static final int RESET = 0;
        private static final int BOLD = 1;
        private static final int FAINT = 2;
        private static final int UNDERLINE = 4;
        private static final int REVERSE = 7;
        private static final int NORMAL_INTENSITY = 22;
        private static final int NOT_UNDERLINED = 24;
        private static final int NOT_REVERSED = 27;
        private static final int FIRST_FOREGROUND = 30;
        private static final int LAST_FOREGROUND = 37;
        private static final int EXTENDED_FOREGROUND = 38;
        private static final int DEFAULT_FOREGROUND = 39;
        private static final int FIRST_BACKGROUND = 40;
        private static final int LAST_BACKGROUND = 47;
        private static final int EXTENDED_BACKGROUND = 48;
        private static final int DEFAULT_BACKGROUND = 49;
        private static final int FIRST_BRIGHT_FOREGROUND = 90;
        private static final int LAST_BRIGHT_FOREGROUND = 97;
        private static final int FIRST_BRIGHT_BACKGROUND = 100;
        private static final int LAST_BRIGHT_BACKGROUND = 107;
        /** Bright colours are palette entries 8–15. */
        private static final int BRIGHT_OFFSET = 8;
        /** {@code 38;5;n}: an index into the 256-colour table. */
        private static final int INDEXED_COLOR = 5;
        /** {@code 38;2;r;g;b}: a literal colour. */
        private static final int TRUE_COLOR = 2;
        private static final int MAXIMUM_COMPONENT = 255;
        private static final int BITS_PER_COMPONENT = 8;
        private static final int TRUE_COLOR_PARAMETERS = 3;

        private SelectGraphicRendition() {
        }

        static CellStyle apply(CellStyle current, ControlSequence sequence) {
            if (sequence.parameterCount() == 0) {
                return CellStyle.PLAIN;
            }
            CellStyle style = current;
            int index = 0;
            while (index < sequence.parameterCount()) {
                int code = sequence.parameter(index, RESET);
                if (code == EXTENDED_FOREGROUND || code == EXTENDED_BACKGROUND) {
                    Optional<ColorReference> color = extendedColor(sequence, index + 1);
                    if (color.isEmpty()) {
                        return style;
                    }
                    style = code == EXTENDED_FOREGROUND ? style.withForeground(color) : style.withBackground(color);
                    index += sequence.parameter(index + 1, 0) == INDEXED_COLOR ? 3 : 2 + TRUE_COLOR_PARAMETERS;
                } else {
                    style = simple(style, code);
                    index++;
                }
            }
            return style;
        }

        private static CellStyle simple(CellStyle style, int code) {
            return switch (code) {
                case RESET -> CellStyle.PLAIN;
                case BOLD -> style.withBold(true);
                case FAINT -> style.withFaint(true);
                case UNDERLINE -> style.withUnderline(true);
                case REVERSE -> style.withReverse(true);
                case NORMAL_INTENSITY -> style.withBold(false).withFaint(false);
                case NOT_UNDERLINED -> style.withUnderline(false);
                case NOT_REVERSED -> style.withReverse(false);
                case DEFAULT_FOREGROUND -> style.withForeground(Optional.empty());
                case DEFAULT_BACKGROUND -> style.withBackground(Optional.empty());
                default -> paletteColor(style, code);
            };
        }

        private static CellStyle paletteColor(CellStyle style, int code) {
            if (code >= FIRST_FOREGROUND && code <= LAST_FOREGROUND) {
                return style.withForeground(palette(code - FIRST_FOREGROUND));
            }
            if (code >= FIRST_BACKGROUND && code <= LAST_BACKGROUND) {
                return style.withBackground(palette(code - FIRST_BACKGROUND));
            }
            if (code >= FIRST_BRIGHT_FOREGROUND && code <= LAST_BRIGHT_FOREGROUND) {
                return style.withForeground(palette(code - FIRST_BRIGHT_FOREGROUND + BRIGHT_OFFSET));
            }
            if (code >= FIRST_BRIGHT_BACKGROUND && code <= LAST_BRIGHT_BACKGROUND) {
                return style.withBackground(palette(code - FIRST_BRIGHT_BACKGROUND + BRIGHT_OFFSET));
            }
            return style;
        }

        private static Optional<ColorReference> palette(int index) {
            return Optional.of(new PaletteIndex(index));
        }

        /** The colour after {@code 38} or {@code 48}; empty when the parameters are missing or out of range. */
        private static Optional<ColorReference> extendedColor(ControlSequence sequence, int kindIndex) {
            int kind = sequence.parameter(kindIndex, ControlSequence.ABSENT);
            if (kind == INDEXED_COLOR) {
                int index = sequence.parameter(kindIndex + 1, ControlSequence.ABSENT);
                return index >= 0 && index < PaletteIndex.PALETTE_SIZE ? palette(index) : Optional.empty();
            }
            if (kind != TRUE_COLOR || kindIndex + TRUE_COLOR_PARAMETERS >= sequence.parameterCount()) {
                return Optional.empty();
            }
            int rgb = 0;
            for (int component = 1; component <= TRUE_COLOR_PARAMETERS; component++) {
                int value = sequence.parameter(kindIndex + component, 0);
                if (value > MAXIMUM_COMPONENT) {
                    return Optional.empty();
                }
                rgb = (rgb << BITS_PER_COMPONENT) | value;
            }
            return Optional.of(new Rgb(rgb));
        }
    }
}
