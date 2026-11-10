package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Ver ▸ «Mostrar los atajos que se usan» in every editor view ({@link ShortcutEchoFeatures}). Installed late, after
 * the other extensions have added their menu items and toolbar buttons and before {@link ShortcutsExtension}.
 */
public final class ShortcutEchoExtension implements EditorExtension {

    private static final int FRAME_MILLISECONDS = 16;

    private final Seams seams;

    /**
     * What the feature touches outside the view, replaceable in tests.
     *
     * @param tickers      a fresh ticker for each chip
     * @param clock        what the chips read
     * @param animations   whether the chips fade
     * @param dispatchers  where the key dispatcher is installed
     * @param focusOwner   the component with the keyboard focus, if any
     * @param currentEvent the AWT event being dispatched, if any (a key event: the action came from the keyboard)
     * @param macNotation  shortcuts as ⌘⇧Z instead of Ctrl+Mayús+Z
     */
    public record Seams(Supplier<FrameTicker> tickers, LongSupplier clock, Predicate<AppPreferences> animations,
                        KeyDispatchers dispatchers, Supplier<Component> focusOwner,
                        Supplier<AWTEvent> currentEvent, boolean macNotation) {

        public Seams {
            Objects.requireNonNull(tickers, "tickers");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(animations, "animations");
            Objects.requireNonNull(dispatchers, "dispatchers");
            Objects.requireNonNull(focusOwner, "focusOwner");
            Objects.requireNonNull(currentEvent, "currentEvent");
        }
    }

    /** The real thing: Swing timers, the system clock, the real focus owner and event, this platform's notation. */
    public ShortcutEchoExtension() {
        this(new Seams(() -> FrameTicker.swing(FRAME_MILLISECONDS), System::currentTimeMillis, Animations::enabled,
                KeyDispatchers.focusManager(), () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                EventQueue::getCurrentEvent, KeyNames.MAC_NOTATION));
    }

    public ShortcutEchoExtension(Seams seams) {
        this.seams = Objects.requireNonNull(seams, "seams");
    }

    @Override
    public Runnable install(EditorView view) {
        ShortcutEchoFeatures features = new ShortcutEchoFeatures(view, seams);
        return features::dispose;
    }
}
