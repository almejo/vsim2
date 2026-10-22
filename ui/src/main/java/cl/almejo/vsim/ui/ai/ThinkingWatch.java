package cl.almejo.vsim.ui.ai;

import javax.swing.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * «Claude está pensando o te preguntó algo en Claude Code» (IA.3): while a task runs, VSim hears nothing from the AI
 * for {@link #LIMIT} and nothing waits for the user (no step waiting for «Siguiente», no pause running, no question
 * in VSim), the AI may have asked something only in the chat. A muted hint, gone at the next call; never the
 * heartbeat that stops a task (that one needs a step waiting and two minutes). A one-second Swing timer runs between
 * {@link #start} and {@link #stop}. EDT only.
 */
public final class ThinkingWatch {

    public static final Duration LIMIT = Duration.ofSeconds(20);
    private static final long LIMIT_NANOS = LIMIT.toNanos();
    private static final int TICK_MILLISECONDS = (int) TimeUnit.SECONDS.toMillis(1);

    private final LongSupplier nanoClock;
    private final BooleanSupplier quiet;
    private final Runnable changed;
    private final Timer timer;
    private long lastHeard;
    private boolean running;
    private boolean showing;

    /**
     * @param quiet   true while a task runs and nothing waits for the user (the hint may show)
     * @param changed runs when the hint shows or goes
     */
    public ThinkingWatch(LongSupplier nanoClock, BooleanSupplier quiet, Runnable changed) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.quiet = Objects.requireNonNull(quiet, "quiet");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.timer = new Timer(TICK_MILLISECONDS, event -> tick());
        this.lastHeard = nanoClock.getAsLong();
    }

    /** A task began: the silence counts from now. */
    public void start() {
        running = true;
        heard();
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    /** The task ended or stopped: the hint goes and the timer stops. */
    public void stop() {
        running = false;
        timer.stop();
        show(false);
    }

    /** VSim heard from the AI (any call or event of its task): the hint goes and the silence counts again. */
    public void heard() {
        lastHeard = nanoClock.getAsLong();
        show(false);
    }

    /** One look at the clock (the timer's, or a test's). */
    public void tick() {
        show(running && quiet.getAsBoolean() && nanoClock.getAsLong() - lastHeard >= LIMIT_NANOS);
    }

    private void show(boolean shown) {
        if (shown != showing) {
            showing = shown;
            changed.run();
        }
    }

    public boolean showing() {
        return showing;
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    public void dispose() {
        running = false;
        timer.stop();
        showing = false;
    }
}
