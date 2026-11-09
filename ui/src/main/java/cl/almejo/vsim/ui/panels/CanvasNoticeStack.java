package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import javax.swing.JComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Several {@link CanvasNotice}s at once over one area (the chips of the shortcuts used): the newest next to the
 * edge, the older ones above it, at most {@code maximum} (a new one beyond that takes the oldest away), each going
 * on its own after its duration. EDT only.
 */
public final class CanvasNoticeStack {

    private final JComponent over;
    private final CanvasNotice.Position position;
    private final Duration duration;
    private final int maximum;
    private final Supplier<FrameTicker> tickers;
    private final LongSupplier clock;
    private final BooleanSupplier fade;
    private final List<CanvasNotice> shown = new ArrayList<>();

    /**
     * @param tickers a fresh ticker for each notice
     * @param fade    asked at every frame of each notice: whether it fades (animations on)
     */
    public CanvasNoticeStack(JComponent over, CanvasNotice.Position position, Duration duration, int maximum,
                             Supplier<FrameTicker> tickers, LongSupplier clock, BooleanSupplier fade) {
        this.over = over;
        this.position = position;
        this.duration = duration;
        this.maximum = maximum;
        this.tickers = tickers;
        this.clock = clock;
        this.fade = fade;
    }

    /** Shows {@code text} as the newest notice, in the normal notice size. */
    public void push(String text) {
        push(text, null);
    }

    /**
     * Shows a big chip as the newest notice: the shortcut's keys as keycaps (with a «+» between them when
     * {@code plusBetween}), then « · » and the action's name; no keys, the name alone. For projectors and videos.
     */
    public void push(List<String> keys, boolean plusBetween, String name) {
        String text = keys.isEmpty() ? name : String.join(plusBetween ? "+" : "", keys) + " · " + name;
        push(text, new CanvasNotice.Chip(List.copyOf(keys), plusBetween, name));
    }

    private void push(String text, CanvasNotice.Chip chip) {
        while (shown.size() >= maximum) {
            shown.get(0).takeAway();
        }
        CanvasNotice[] holder = new CanvasNotice[1];
        CanvasNotice notice = CanvasNotice.showStacked(over, text, position, duration, tickers.get(), clock,
                fade, () -> {
                    shown.remove(holder[0]);
                    stackUp();
                }, chip);
        if (notice == null) {
            return;
        }
        holder[0] = notice;
        shown.add(notice);
        stackUp();
    }

    /** Takes every notice away. */
    public void clear() {
        for (CanvasNotice notice : new ArrayList<>(shown)) {
            notice.takeAway();
        }
    }

    private void stackUp() {
        for (int index = 0; index < shown.size(); index++) {
            shown.get(index).setLevel(shown.size() - 1 - index);
        }
    }
}
