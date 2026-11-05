package cl.almejo.vsim.ui.window.tabs;

import java.util.Objects;

/**
 * What a tab drag carries (spec §4.3): never one tab, always the whole group of {@code document}, taken from the
 * strip {@code source}. The drag follows the mouse itself (press, 6 px of motion, release) instead of AWT drag and
 * drop, so it works the same on every platform, crosses windows, and can be tested headless.
 */
public record TabTransfer(Object document, TabStrip source) {

    public TabTransfer {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(source, "source");
    }
}
