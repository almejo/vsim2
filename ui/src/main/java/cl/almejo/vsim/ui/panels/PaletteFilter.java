package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Locale;

public final class PaletteFilter {

    private PaletteFilter() {
    }

    public static boolean matches(ComponentType type, String query) {
        return matches(type.id(), Messages.componentName(type), query);
    }

    /** Matches the entry's id ({@code nand} for a preset) or its visible name («NAND»). */
    public static boolean matches(PaletteEntry entry, String query) {
        return matches(entry.id(), entry.name(), query);
    }

    private static boolean matches(String id, String name, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return id.toLowerCase(Locale.ROOT).contains(needle) || name.toLowerCase(Locale.ROOT).contains(needle);
    }
}
