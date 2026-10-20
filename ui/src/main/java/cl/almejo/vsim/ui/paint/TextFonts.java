package cl.almejo.vsim.ui.paint;

import java.awt.Font;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The text font in use, app-wide (M6.8): set from the preference at start and on every change, read at paint and
 * measure time by the static layouts ({@code LabelLayout}, {@code FramePainter}, {@code DrawnBounds}). The derived
 * fonts are made once per change, not once per paint. Listeners (the canvases) repaint when the font changes.
 * <p>
 * A paint that must not follow the app's font (a PDF page painted from a frozen copy, while the user may pick another
 * font) lends its own font to its thread with {@link #withFont}: on that thread, and only during the call, every
 * reader sees the lent font; the app's font, other threads and the listeners are untouched.
 */
public final class TextFonts {

    /** The chosen font and the fonts derived from it, swapped as one so a reader never mixes two choices. */
    private record Current(TextFont textFont, Font label, Font title, Font tag) {
        static Current of(TextFont textFont) {
            return new Current(textFont, textFont.labelFont(), textFont.titleFont(), textFont.tagFont());
        }
    }

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Current current = Current.of(TextFont.DEFAULT);
    /** The font lent to this thread by {@link #withFont}, or {@code null} to read the app's font. */
    private static final ThreadLocal<Current> LENT = new ThreadLocal<>();
    /** The last font lent, kept so that painting page after page derives its fonts once. */
    private static volatile Current lastLent = Current.of(TextFont.DEFAULT);

    private TextFonts() {
    }

    public static TextFont current() {
        return active().textFont();
    }

    private static Current active() {
        Current lent = LENT.get();
        return lent != null ? lent : current;
    }

    /**
     * Runs {@code work} on this thread with {@code textFont} as the font every reader sees, then gives the previous
     * one back (also when {@code work} throws); calls nest. Nothing is notified: the app's font does not change.
     */
    public static <T> T withFont(TextFont textFont, Supplier<T> work) {
        Objects.requireNonNull(textFont, "textFont");
        Objects.requireNonNull(work, "work");
        Current previous = LENT.get();
        LENT.set(lent(textFont));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                LENT.remove();
            } else {
                LENT.set(previous);
            }
        }
    }

    private static Current lent(TextFont textFont) {
        Current last = lastLent;
        if (last.textFont().equals(textFont)) {
            return last;
        }
        Current made = Current.of(textFont);
        lastLent = made;
        return made;
    }

    /** {@link #withFont} for work that returns nothing. */
    public static void runWithFont(TextFont textFont, Runnable work) {
        Objects.requireNonNull(work, "work");
        withFont(textFont, () -> {
            work.run();
            return null;
        });
    }

    /** Listeners run only when the font really changes; use it on the EDT. */
    public static void set(TextFont textFont) {
        Objects.requireNonNull(textFont, "textFont");
        if (textFont.equals(current.textFont())) {
            return;
        }
        current = Current.of(textFont);
        LISTENERS.forEach(Runnable::run);
    }

    public static Font labelFont() {
        return active().label();
    }

    public static Font titleFont() {
        return active().title();
    }

    public static Font tagFont() {
        return active().tag();
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /** For tests: views must remove what they add when disposed. */
    public static int listenerCount() {
        return LISTENERS.size();
    }
}
