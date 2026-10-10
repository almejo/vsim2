package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.KeyStroke;
import java.util.Objects;
import java.util.Optional;

/** One action whose shortcut the user can change: catalog id, message key of its name, menu, scope and default. */
public record ShortcutEntry(String id, String nameKey, MenuId group, Scope scope, Optional<KeyStroke> defaultStroke) {

    public ShortcutEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(defaultStroke, "defaultStroke");
    }
}
