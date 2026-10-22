package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.PreferenceLayer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The AI's temporary editor settings in one window (spec §7): «Ajustar» and «Ajustar a ▸», the grid style and
 * spacing, «Mostrar guías» and «Los cables siguen», layered over the saved preferences of that window only. The saved
 * values on disk and the other windows never change; {@link #drop()} brings the saved values back (the task ended),
 * {@link #keep()} saves them as the user's own (the user asked to keep them). EDT only.
 */
public final class SettingsOverride {

    /** One setting the AI changed, with the value it gave. */
    public record Change(EditorSetting setting, String value) {
    }

    private final AppPreferences windowPreferences;
    private final PreferenceLayer layer;

    private SettingsOverride(AppPreferences windowPreferences, PreferenceLayer layer) {
        this.windowPreferences = windowPreferences;
        this.layer = layer;
    }

    /** The override of the window whose preferences are {@code windowPreferences}; empty for the saved preferences. */
    public static Optional<SettingsOverride> of(AppPreferences windowPreferences) {
        Objects.requireNonNull(windowPreferences, "windowPreferences");
        return windowPreferences.layer().map(layer -> new SettingsOverride(windowPreferences, layer));
    }

    /**
     * Shows {@code value} for {@code setting} in this window; {@link AutomationError#INVALID_PARAMETER} (and nothing
     * changes) when it is not one of the setting's allowed values.
     */
    public void set(EditorSetting setting, String value) {
        Objects.requireNonNull(setting, "setting");
        if (value == null || !setting.allowedValues().contains(value)) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER,
                    "«" + value + "» is not a value of " + setting + "; use one of " + setting.allowedValues(),
                    Map.of("allowedValues", setting.allowedValues()));
        }
        layer.put(layered(setting), value);
    }

    /** The editor settings the AI changed in this window, in the order it changed them (not its pace). */
    public List<Change> changes() {
        List<Change> changes = new ArrayList<>();
        layer.values().forEach((setting, value) -> editorSetting(setting)
                .ifPresent(editor -> changes.add(new Change(editor, value))));
        return List.copyOf(changes);
    }

    /** True when the AI changed no editor setting here (its pace may still be layered). */
    public boolean isEmpty() {
        return changes().isEmpty();
    }

    /**
     * Shows the AI's «Presentar» pace and pause for its task in this window ({@code vsim_set_presentation}); the
     * caller checks the pause's range. Given back by {@link #drop()} and {@link #keep()} (never saved).
     */
    public void layerPresentation(Optional<PresentationPace> pace, Optional<Duration> pause) {
        Objects.requireNonNull(pace, "pace");
        Objects.requireNonNull(pause, "pause");
        pace.ifPresent(chosen -> layer.put(PreferenceLayer.Setting.AI_PACE, chosen.name()));
        pause.ifPresent(chosen -> layer.put(PreferenceLayer.Setting.AI_PAUSE_MILLIS,
                Long.toString(chosen.toMillis())));
    }

    /** The window shows the saved preferences again. */
    public void drop() {
        layer.clear();
    }

    /** Saves the AI's values as the user's preferences, for every window; the layer is then empty. */
    public void keep() {
        for (Change change : changes()) {
            save(change.setting(), change.value());
        }
        layer.clear();
    }

    /** Through the window's preferences: saving a setting also drops its layered value. */
    private void save(EditorSetting setting, String value) {
        switch (setting) {
            case SNAP -> windowPreferences.setSnapToAlignments(Boolean.parseBoolean(value));
            case SNAP_TO_PARTS -> windowPreferences.setSnapToParts(Boolean.parseBoolean(value));
            case SNAP_TO_GUIDES -> windowPreferences.setSnapToGuides(Boolean.parseBoolean(value));
            case SNAP_TO_GRID_LINES -> windowPreferences.setSnapToGridLines(Boolean.parseBoolean(value));
            case SHOW_GUIDES -> windowPreferences.setShowGuides(Boolean.parseBoolean(value));
            case GRID_STYLE -> windowPreferences.setGridStyle(AppPreferences.GridStyle.valueOf(value));
            case GRID_SPACING -> windowPreferences.setGridLineSpacing(Integer.parseInt(value));
            case WIRES_FOLLOW -> windowPreferences.setWiresFollow(Boolean.parseBoolean(value));
        }
    }

    private static PreferenceLayer.Setting layered(EditorSetting setting) {
        return switch (setting) {
            case SNAP -> PreferenceLayer.Setting.SNAP_TO_ALIGNMENTS;
            case SNAP_TO_PARTS -> PreferenceLayer.Setting.SNAP_TO_PARTS;
            case SNAP_TO_GUIDES -> PreferenceLayer.Setting.SNAP_TO_GUIDES;
            case SNAP_TO_GRID_LINES -> PreferenceLayer.Setting.SNAP_TO_GRID_LINES;
            case SHOW_GUIDES -> PreferenceLayer.Setting.SHOW_GUIDES;
            case GRID_STYLE -> PreferenceLayer.Setting.GRID_STYLE;
            case GRID_SPACING -> PreferenceLayer.Setting.GRID_LINE_SPACING;
            case WIRES_FOLLOW -> PreferenceLayer.Setting.WIRES_FOLLOW;
        };
    }

    private static Optional<EditorSetting> editorSetting(PreferenceLayer.Setting setting) {
        return Optional.ofNullable(switch (setting) {
            case SNAP_TO_ALIGNMENTS -> EditorSetting.SNAP;
            case SNAP_TO_PARTS -> EditorSetting.SNAP_TO_PARTS;
            case SNAP_TO_GUIDES -> EditorSetting.SNAP_TO_GUIDES;
            case SNAP_TO_GRID_LINES -> EditorSetting.SNAP_TO_GRID_LINES;
            case SHOW_GUIDES -> EditorSetting.SHOW_GUIDES;
            case GRID_STYLE -> EditorSetting.GRID_STYLE;
            case GRID_LINE_SPACING -> EditorSetting.GRID_SPACING;
            case WIRES_FOLLOW -> EditorSetting.WIRES_FOLLOW;
            case AI_PACE, AI_PAUSE_MILLIS -> null;
        });
    }
}
