package cl.almejo.vsim.ui.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Values that one window reads instead of the saved preferences (spec §7: the AI's temporary editor settings). The
 * {@link AppPreferences} made by {@link AppPreferences#layered()} reads a setting here first; nothing here is ever
 * written to disk, and the other windows never see it. When the user changes a layered setting in that window, the
 * user's choice is saved and replaces the layered value. EDT only.
 */
public final class PreferenceLayer {

    /** The settings a layer may hold: the editor settings the AI may change while it works, and its pace. */
    public enum Setting {
        SNAP_TO_ALIGNMENTS(AppPreferences.SNAP_TO_ALIGNMENTS),
        SNAP_TO_PARTS(AppPreferences.SNAP_TO_PARTS),
        SNAP_TO_GUIDES(AppPreferences.SNAP_TO_GUIDES),
        SNAP_TO_GRID_LINES(AppPreferences.SNAP_TO_GRID_LINES),
        SHOW_GUIDES(AppPreferences.SHOW_GUIDES),
        GRID_STYLE(AppPreferences.GRID_STYLE),
        GRID_LINE_SPACING(AppPreferences.GRID_LINE_SPACING),
        WIRES_FOLLOW(AppPreferences.WIRES_FOLLOW),
        /** «Presentar»'s pace and pause for the AI's task ({@code vsim_set_presentation}): not editor settings. */
        AI_PACE(AppPreferences.AI_PACE),
        AI_PAUSE_MILLIS(AppPreferences.AI_PAUSE_MILLIS);

        private final String key;

        Setting(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }

    /** In the order they were first layered. */
    private final Map<Setting, String> values = new LinkedHashMap<>();
    private final Runnable changed;

    /** @param changed tells the window's preference listeners that what they read may have changed */
    PreferenceLayer(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /**
     * Layers {@code storedValue}, written as the preferences store it ({@code "true"}, {@code "LINES"}, {@code "4"});
     * the caller validates it (an unreadable value reads as the setting's default).
     */
    public void put(Setting setting, String storedValue) {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(storedValue, "storedValue");
        if (storedValue.equals(values.get(setting))) {
            return;
        }
        values.remove(setting); // a changed value moves to the end, as the latest change
        values.put(setting, storedValue);
        changed.run();
    }

    /** The layered values, in the order they were layered (read-only). */
    public Map<Setting, String> values() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Drops every layered value: the window reads the saved preferences again. */
    public void clear() {
        if (!values.isEmpty()) {
            values.clear();
            changed.run();
        }
    }

    /** Drops the layered values of {@code settings} (the others stay): the window reads their saved values again. */
    public void remove(Set<Setting> settings) {
        Objects.requireNonNull(settings, "settings");
        if (values.keySet().removeAll(settings)) {
            changed.run();
        }
    }

    Optional<String> valueOf(String key) {
        for (Map.Entry<Setting, String> entry : values.entrySet()) {
            if (entry.getKey().key().equals(key)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /** Drops the value of {@code key} without telling anyone; true when there was one. */
    boolean forget(String key) {
        return values.keySet().removeIf(setting -> setting.key().equals(key));
    }
}
