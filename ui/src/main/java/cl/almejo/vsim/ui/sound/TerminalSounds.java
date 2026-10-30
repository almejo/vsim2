package cl.almejo.vsim.ui.sound;

import java.util.function.LongSupplier;

/** The terminal's sounds: the bell and the teletype click, the latter rate-limited so a fast program cannot flood. */
public final class TerminalSounds {

    static final double BELL_HERTZ = 880.0;
    static final int BELL_MILLISECONDS = 120;
    static final double CLICK_HERTZ = 1800.0;
    static final int CLICK_MILLISECONDS = 15;
    static final int MAXIMUM_CLICKS_PER_SECOND = 60;
    private static final double BELL_VOLUME = 0.5;
    private static final double CLICK_VOLUME = 0.3;
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
    private static final long CLICK_INTERVAL_NANOSECONDS = NANOSECONDS_PER_SECOND / MAXIMUM_CLICKS_PER_SECOND;

    private final SoundSink sink;
    private final LongSupplier nanoClock;
    private long lastClickNanoseconds;
    private boolean clickedBefore;

    public TerminalSounds(SoundSink sink, LongSupplier nanoClock) {
        this.sink = sink;
        this.nanoClock = nanoClock;
    }

    public void bell() {
        sink.play(new Tone(BELL_HERTZ, BELL_MILLISECONDS, BELL_VOLUME, Waveform.SQUARE));
    }

    /** {@code count} printable characters arrived: at most one click per 1/60 s; the rest merge into it. */
    public synchronized void typed(int count) {
        if (count <= 0) {
            return;
        }
        long now = nanoClock.getAsLong();
        if (clickedBefore && now - lastClickNanoseconds < CLICK_INTERVAL_NANOSECONDS) {
            return;
        }
        clickedBefore = true;
        lastClickNanoseconds = now;
        sink.play(new Tone(CLICK_HERTZ, CLICK_MILLISECONDS, CLICK_VOLUME, Waveform.SQUARE));
    }
}
