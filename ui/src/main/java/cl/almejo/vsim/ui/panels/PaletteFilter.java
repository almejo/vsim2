package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Locale;

public final class PaletteFilter {

    private PaletteFilter() {
    }

    public static boolean matches(ComponentType type, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return type.id().toLowerCase(Locale.ROOT).contains(needle)
                || Messages.componentName(type).toLowerCase(Locale.ROOT).contains(needle);
    }
}
