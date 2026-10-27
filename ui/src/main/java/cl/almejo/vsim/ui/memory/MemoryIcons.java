package cl.almejo.vsim.ui.memory;

import java.util.List;

/** The icons of the content editor's toolbar, by file name under {@code icons/}. */
public final class MemoryIcons {

    private static final List<String> NAMES = List.of("memory-dump", "memory-export", "memory-external", "memory-fill",
            "memory-goto", "memory-import", "memory-link", "memory-restore");

    private MemoryIcons() {
    }

    /** The icon of the toolbar button called {@code buttonName} ({@code memory.goTo} is {@code memory-goto}). */
    static String of(String buttonName) {
        String name = buttonName.replace('.', '-').toLowerCase(java.util.Locale.ROOT);
        if (!NAMES.contains(name)) {
            throw new IllegalArgumentException("no memory icon for " + buttonName);
        }
        return name;
    }

    /** Every memory icon, once. */
    public static List<String> names() {
        return NAMES;
    }
}
