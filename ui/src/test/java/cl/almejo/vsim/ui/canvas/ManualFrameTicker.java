package cl.almejo.vsim.ui.canvas;

/**
 * A {@link FrameTicker} and its clock for headless tests: nothing runs by itself; {@link #advance} moves the clock and
 * runs the frames that would have happened in between, one every 16 ms.
 */
public final class ManualFrameTicker implements FrameTicker {

    private Runnable frame;
    private long now = 1_000;

    @Override
    public void start(Runnable frame) {
        this.frame = frame;
    }

    @Override
    public void stop() {
        frame = null;
    }

    @Override
    public boolean isRunning() {
        return frame != null;
    }

    /** The clock the travels read, in milliseconds. */
    public long now() {
        return now;
    }

    /** Moves the clock {@code milliseconds} forward in 16 ms frames, running the frame after each step. */
    public void advance(long milliseconds) {
        long end = now + milliseconds;
        while (now < end) {
            now = Math.min(end, now + 16);
            if (frame != null) {
                frame.run();
            }
        }
    }

    /** Runs frames until the ticker stops (at most ten seconds of them). */
    public void finish() {
        for (int frames = 0; frame != null && frames < 625; frames++) {
            advance(16);
        }
    }

    /** Installs this ticker as the canvas's travel clock. */
    public ManualFrameTicker drive(CircuitCanvas canvas) {
        canvas.useViewTicker(this, this::now);
        return this;
    }
}
