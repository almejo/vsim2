package cl.almejo.vsim.ui.whatsnew;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One bubble of a release's news: what it points at ({@link NewsTargets}), its title and text (bundle keys) and the
 * action whose key («Atajo») or place in the menus («En el menú») it shows, if any.
 */
record NewsStep(String targetId, String titleKey, String textKey, Optional<ActionReference> action) {

    NewsStep {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(textKey, "textKey");
        Objects.requireNonNull(action, "action");
    }

    /**
     * An action by its {@code ShortcutCatalog} id, or one of the extra entries the command palette lists after it
     * ({@code paletteEntry}, an index of its {@code Actions.PALETTE_ENTRIES}: «Preferencias ▸ General» is entry 0 of
     * {@code file.preferences}).
     */
    record ActionReference(String shortcutId, OptionalInt paletteEntry) {

        ActionReference {
            Objects.requireNonNull(shortcutId, "shortcutId");
            Objects.requireNonNull(paletteEntry, "paletteEntry");
        }
    }
}
