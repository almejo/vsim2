package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.value.Value;

import java.util.Arrays;

/**
 * How a Terminal with «Generador de caracteres: Memoria externa» reads its glyphs (spec §8.8, decision 8). Each code
 * 0–255 that reaches the screen and has no glyph yet is queued once; a scan drives CODE = the code and ROW = 0 to 7 in
 * turn, each pair for «Tiempo de lectura» time units, and reads PIX at the end of each slot, so a code takes eight slots
 * and n codes take 8n. When the queue is empty the scan stops and schedules nothing: «Paso a paso» and «run until
 * stable» still end. Outside a scan CODE and ROW keep their last values.
 *
 * <p>A PIX bit that is undefined (Z or a conflict) reads as off; the glyph is kept so the painter can draw it, and the
 * code is read again the next time it is printed. «Reiniciar» starts from an empty cache (a new behaviour);
 * «Releer la ROM de caracteres» ({@link #reread}) empties it and reads every code on the screen again.
 */
final class CharacterRomScan {

    private static final Value FIRST_CODE = Value.of(TerminalPinout.CODE_WIDTH, 0);
    private static final Value FIRST_ROW = Value.of(TerminalPinout.ROW_WIDTH, 0);
    private static final long ROW_MASK = 0xFF;

    private final long readTime;
    private GlyphCache glyphs = GlyphCache.EMPTY;
    /** Codes with a glyph read with every PIX bit defined: never read again until «Releer» or «Reiniciar». */
    private final boolean[] resolved = new boolean[GlyphCache.CODES];
    /** Codes waiting in {@link #pending} or being read now. */
    private final boolean[] queued = new boolean[GlyphCache.CODES];
    /** A ring of the codes to read, each at most once, so 256 places are enough. */
    private final int[] pending = new int[GlyphCache.CODES];
    private int pendingFirst;
    private int pendingCount;
    private boolean scanning;
    private int code;
    private int row;
    private long bitmap;
    private boolean undefined;
    /** Grows when a scan is abandoned («Releer»): a read scheduled by the old scan does nothing. */
    private long scanNumber;

    CharacterRomScan(long readTime) {
        this.readTime = readTime;
    }

    /** The glyphs read so far. */
    GlyphCache glyphs() {
        return glyphs;
    }

    /** A rebuilt Terminal keeps the glyphs read by the one it replaces; the codes it was reading are read again. */
    void adoptFrom(CharacterRomScan previous) {
        glyphs = previous.glyphs;
        System.arraycopy(previous.resolved, 0, resolved, 0, resolved.length);
    }

    /** At the start (and after a rebuild): CODE and ROW drive 0, and every code already on the screen is queued. */
    void start(BehaviorContext context, TerminalScreen screen) {
        context.drive(TerminalPinout.CODE, FIRST_CODE, 0);
        context.drive(TerminalPinout.ROW, FIRST_ROW, 0);
        queueScreen(context, screen);
    }

    /** {@code codePoint} reached the screen: queues it unless it has a glyph already or does not fit in CODE. */
    void printed(BehaviorContext context, int codePoint) {
        if (codePoint < 0 || codePoint >= GlyphCache.CODES || resolved[codePoint] || queued[codePoint]) {
            return;
        }
        queued[codePoint] = true;
        pending[(pendingFirst + pendingCount) % pending.length] = codePoint;
        pendingCount++;
        if (!scanning) {
            scanning = true;
            nextCode(context);
        }
    }

    /** «Releer la ROM de caracteres»: no glyph any more, and every code on the screen is read again. */
    void reread(BehaviorContext context, TerminalScreen screen) {
        scanNumber++;
        scanning = false;
        pendingFirst = 0;
        pendingCount = 0;
        Arrays.fill(resolved, false);
        Arrays.fill(queued, false);
        glyphs = glyphs.cleared();
        queueScreen(context, screen);
    }

    private void queueScreen(BehaviorContext context, TerminalScreen screen) {
        screen.forEachWrittenCodePoint(codePoint -> printed(context, codePoint));
    }

    private void nextCode(BehaviorContext context) {
        if (pendingCount == 0) {
            scanning = false;
            return;
        }
        code = pending[pendingFirst];
        pendingFirst = (pendingFirst + 1) % pending.length;
        pendingCount--;
        row = 0;
        bitmap = 0L;
        undefined = false;
        driveRow(context);
    }

    private void driveRow(BehaviorContext context) {
        context.drive(TerminalPinout.CODE, Value.of(TerminalPinout.CODE_WIDTH, code), 0);
        context.drive(TerminalPinout.ROW, Value.of(TerminalPinout.ROW_WIDTH, row), 0);
        long scan = scanNumber;
        context.schedule(readTime, () -> {
            if (scan == scanNumber) {
                readRow(context);
            }
        });
    }

    private void readRow(BehaviorContext context) {
        Value pixels = context.input(TerminalPinout.PIX);
        long defined = pixels.conflict() ? 0L : ~pixels.highImpedanceMask();
        if (!pixels.isFullyDefined()) {
            undefined = true;
        }
        bitmap |= (pixels.bits() & defined & ROW_MASK) << (Byte.SIZE * row);
        row++;
        if (row < GlyphCache.GLYPH_ROWS) {
            driveRow(context);
            return;
        }
        glyphs = glyphs.with(code, bitmap);
        queued[code] = false;
        resolved[code] = !undefined;
        nextCode(context);
    }
}
