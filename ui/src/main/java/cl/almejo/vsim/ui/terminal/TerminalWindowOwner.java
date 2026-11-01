package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Window;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Who opens a Terminal window: {@code key} names the editor view (its windows close with it), {@code window} is the
 * editor window that owns the dialog, {@code notices} its status bar and {@code scheme} its colours now (the screen's
 * «Del tema de VSim» colours follow it).
 */
public record TerminalWindowOwner(Object key, Optional<Window> window, Consumer<String> notices,
                                  Supplier<ColorScheme> scheme) {

    public TerminalWindowOwner {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(notices, "notices");
        Objects.requireNonNull(scheme, "scheme");
    }
}
