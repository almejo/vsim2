package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.TerminalState;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalMode;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * «Terminal»: on the chosen clock edge («Flanco») with WE = 1 the byte on D goes to the screen ({@link TerminalScreen});
 * a D with a bit undefined writes {@code ?} as Logisim does. CLR = 1 clears the screen at once and wins over the clock.
 * Unconnected, WE counts as 0 and CLR as 0, and a conflict on either does nothing; «Modo» Logisim reads them as
 * Logisim 2.7's TTY instead: it writes unless WE is exactly 0 and clears only when CLR is exactly 1. It drives nothing.
 *
 * <p>A behaviour rebuilt after a parameter change keeps the screen ({@link #adoptStateFrom}, Logisim's resize rule);
 * «Reiniciar» starts from an empty one. With «Generador de caracteres: Memoria externa» it reads the glyphs of the
 * codes on its screen from a character ROM through CODE, ROW and PIX ({@link CharacterRomScan}). With «Detener al
 * recibir» the simulation pauses when the received text ends with it, once per arrival: the latch opens again when the
 * text no longer ends with it.
 */
public final class TerminalBehavior implements Behavior, TerminalScreenPart {

    /** The bundle key of the pause notice; its argument is the text received (the UI adds the part's name). */
    public static final String STOPPED_MESSAGE = "terminal.stopped";

    /** Hands each behaviour a number of its own for {@link TerminalState#owner()}. */
    private static final AtomicLong OWNERS = new AtomicLong();

    private final long owner = OWNERS.incrementAndGet();
    private final boolean logisimControls;
    private final boolean externalGlyphs;
    /** Empty with the internal generator: no ROM pins, nothing to read. */
    private final Optional<CharacterRomScan> characterRom;
    private final String stopOn;
    private final ClockEdgeDetector edge;
    private TerminalScreen screen;
    private boolean stopLatchOpen = true;
    private TerminalState published;

    TerminalBehavior(Map<String, Object> parameters) {
        TerminalSettings settings = TerminalTypes.settings(parameters);
        this.logisimControls = settings.mode() == TerminalMode.LOGISIM;
        this.externalGlyphs = TerminalTypes.externalGlyphs(parameters);
        this.characterRom = externalGlyphs
                ? Optional.of(new CharacterRomScan((Integer) parameters.get(TerminalTypes.GLYPH_READ_TIME)))
                : Optional.empty();
        this.stopOn = String.valueOf(parameters.getOrDefault(TerminalTypes.STOP_ON, ""));
        this.edge = ClockEdgeDetector.of(parameters);
        this.screen = new TerminalScreen(settings);
    }

    @Override
    public void adoptStateFrom(Behavior previous) {
        if (previous instanceof TerminalBehavior terminal) {
            screen = terminal.screen.adoptedWith(screen.settings());
            stopLatchOpen = terminal.stopLatchOpen || !stopOn.equals(terminal.stopOn);
            if (characterRom.isPresent() && terminal.characterRom.isPresent()) {
                characterRom.get().adoptFrom(terminal.characterRom.get());
            }
        }
    }

    @Override
    public void initialize(BehaviorContext context) {
        edge.start(context.input(TerminalPinout.CLOCK));
        characterRom.ifPresent(scan -> scan.start(context, screen));
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        boolean clockEdge = pinIndex == TerminalPinout.CLOCK && edge.step(context.input(TerminalPinout.CLOCK));
        Optional<Boolean> clear = clear(context.input(TerminalPinout.CLEAR));
        if (clear.isEmpty()) {
            return;
        }
        if (clear.get()) {
            screen.clear();
            return;
        }
        if (clockEdge && writeEnabled(context.input(TerminalPinout.WRITE_ENABLE))) {
            write(context);
        }
    }

    /** «Limpiar» from the window or the context menu: the same as CLR, not an edit. */
    @Override
    public void clearScreen() {
        screen.clear();
    }

    /**
     * «Releer la ROM de caracteres»: forgets every glyph and reads the codes on the screen again from the ROM. Not an
     * edit. Without an external character ROM it does nothing.
     */
    public void rereadGlyphs(BehaviorContext context) {
        characterRom.ifPresent(scan -> scan.reread(context, screen));
    }

    /** The glyphs read from the character ROM so far; empty with the internal generator. */
    @Override
    public GlyphCache glyphs() {
        return characterRom.map(CharacterRomScan::glyphs).orElse(GlyphCache.EMPTY);
    }

    /** The number of this behaviour in {@link TerminalState#owner()}. */
    @Override
    public long owner() {
        return owner;
    }

    /** The screen, read by the window's frames on the simulation thread only. */
    @Override
    public TerminalScreen screen() {
        return screen;
    }

    @Override
    public VisibleState visibleState() {
        GlyphCache glyphs = glyphs();
        if (published == null || published.version() != screen.version() || published.glyphs() != glyphs) {
            published = new TerminalState(owner, screen.version(), screen.frame(), externalGlyphs, glyphs);
        }
        return published;
    }

    private void write(BehaviorContext context) {
        OptionalLong data = BlockSignals.unsigned(context.input(TerminalPinout.DATA));
        long printedBefore = screen.printedCount();
        if (data.isPresent()) {
            screen.write((int) data.getAsLong(), context.now());
        } else {
            screen.writeUndefined(context.now());
        }
        if (screen.printedCount() != printedBefore) {
            characterRom.ifPresent(scan -> scan.printed(context, screen.lastPrintedCodePoint()));
        }
        checkStopText(context);
    }

    private void checkStopText(BehaviorContext context) {
        if (stopOn.isEmpty()) {
            return;
        }
        boolean matches = screen.transcriptEndsWith(stopOn);
        if (!matches) {
            stopLatchOpen = true;
        } else if (stopLatchOpen) {
            stopLatchOpen = false;
            context.requestPause(STOPPED_MESSAGE, List.of(stopOn));
        }
    }

    /** CLR: present and true clears; empty (a conflict in the strict modes) does nothing at all. */
    private Optional<Boolean> clear(Value value) {
        if (logisimControls) {
            return Optional.of(value.equals(Value.ONE));
        }
        return BlockSignals.control(value, false);
    }

    private boolean writeEnabled(Value value) {
        if (logisimControls) {
            return !value.equals(Value.ZERO);
        }
        return BlockSignals.control(value, false).orElse(false);
    }
}
