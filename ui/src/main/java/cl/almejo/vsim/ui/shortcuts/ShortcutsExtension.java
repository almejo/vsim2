package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the keyboard shortcuts of {@link KeyBindings} to one editor view (spec §5.2): menu accelerators for WINDOW
 * entries, canvas bindings named {@code shortcut.<id>} for CANVAS entries on every pane of the view. Installed last,
 * so the actions earlier extensions registered ({@link EditorView#registerShortcutAction}) are there. It re-applies on every preference
 * change, so a change made in any window shows at once in all of them. A catalog id without an action in this view
 * is skipped. If stored overrides give two actions the same stroke, the first in catalog order keeps it. Toolbar
 * tooltips end with the current shortcut ({@link ToolbarTooltips}, M6.1-E).
 */
public final class ShortcutsExtension implements EditorExtension {

    static final String BINDING_PREFIX = "shortcut.";

    @Override
    public Runnable install(EditorView view) {
        AppPreferences preferences = view.services().preferences();
        Map<String, Action> actions = actionsOf(view);
        Applier applier = new Applier(actions, new KeyBindings(preferences));
        ToolbarTooltips tooltips = new ToolbarTooltips(view.toolBar(), actions, applier::strokeOf);
        applier.apply();
        // every pane gets the canvas keys, the panes created later too, and a removed pane gives them back
        Runnable paneBindings = view.forEachPane(applier::bindOn);
        tooltips.refresh();
        Runnable listener = () -> {
            applier.apply();
            tooltips.refresh();
        };
        preferences.addListener(listener);
        return () -> {
            preferences.removeListener(listener);
            paneBindings.run();
            tooltips.dispose();
        };
    }

    /** Every action of {@code view} the catalog can name: the editor's own plus the registered ones. */
    public static Map<String, Action> actionsOf(EditorView view) {
        Map<String, Action> actions = builtInActions(view);
        view.shortcutActions().forEach(actions::putIfAbsent);
        return actions;
    }

    private static Map<String, Action> builtInActions(EditorView view) {
        EditorActions actions = view.actions();
        SelectionActions selection = view.selectionActions();
        Map<String, Action> map = new LinkedHashMap<>();
        map.put("file.new", actions.newDocument());
        map.put("file.open", actions.open());
        map.put("file.save", actions.save());
        map.put("file.saveAs", actions.saveAs());
        map.put("file.close", actions.closeWindow());
        map.put("file.exit", actions.exit());
        map.put("edit.undo", actions.undo());
        map.put("edit.redo", actions.redo());
        map.put("edit.delete", actions.delete());
        map.put("edit.rotateClockwise", actions.rotateClockwise());
        map.put("edit.rotateCounterClockwise", actions.rotateCounterClockwise());
        map.put("edit.configure", actions.configure());
        map.put("edit.cut", selection.cut());
        map.put("edit.copy", selection.copy());
        map.put("edit.paste", selection.paste());
        map.put("edit.duplicate", selection.duplicate());
        map.put("edit.selectAll", selection.selectAll());
        map.put("edit.label", view.labelFeatures().actions().editLabel());
        map.putAll(actions.moveActions());
        map.put("view.selectTool", actions.selectTool());
        map.put("view.wireTool", actions.wireTool());
        map.put("view.panTool", actions.panTool());
        map.put("view.zoomIn", actions.zoomIn());
        map.put("view.zoomOut", actions.zoomOut());
        map.put("view.zoomReset", actions.zoomReset());
        map.put("view.centerView", actions.centerView());
        map.put("view.nextProblem", view.problems().nextProblem());
        map.put("view.previousProblem", view.problems().previousProblem());
        map.put("simulation.start", actions.start());
        map.put("simulation.pause", actions.pause());
        map.put("simulation.startOrPause", actions.startOrPause());
        map.put("simulation.restart", actions.restart());
        map.put("simulation.step", actions.step());
        map.put("window.newView", actions.newView());
        return map;
    }

    /**
     * The bindings of one view: the menu accelerators, and on each of its panes the canvas keys (remembered per pane,
     * so the next apply, or the pane going away, can take them back).
     */
    private static final class Applier {

        private final Map<String, Action> actions;
        private final KeyBindings bindings;
        private final Map<String, KeyStroke> applied = new HashMap<>();
        private final Map<CircuitCanvas, Map<String, KeyStroke>> boundByPane = new LinkedHashMap<>();

        Applier(Map<String, Action> actions, KeyBindings bindings) {
            this.actions = actions;
            this.bindings = bindings;
        }

        /** Gives {@code pane} the canvas keys; the returned runnable takes them back. */
        Runnable bindOn(CircuitCanvas pane) {
            Map<KeyStroke, Object> released = releaseDefaultCanvasStrokes(pane);
            Map<String, KeyStroke> bound = new HashMap<>();
            boundByPane.put(pane, bound);
            bindCanvasKeys(pane, bound);
            return () -> {
                unbindCanvasKeys(pane, bound);
                boundByPane.remove(pane);
                restoreDefaultCanvasStrokes(pane, released);
            };
        }

        /**
         * The editor bound the default strokes under its own names; those strokes now follow the user's choice. Only
         * a stroke still bound to the editor's own name is removed. The editor's action-map entries stay, so its
         * fixed aliases (Retroceso = Borrar) keep working. Returns what was removed, to give it back on dispose.
         */
        private Map<KeyStroke, Object> releaseDefaultCanvasStrokes(CircuitCanvas pane) {
            InputMap keys = pane.getInputMap(JComponent.WHEN_FOCUSED);
            Map<KeyStroke, Object> released = new HashMap<>();
            for (ShortcutEntry entry : ShortcutCatalog.entries()) {
                if (entry.scope() != Scope.CANVAS || !actions.containsKey(entry.id())) {
                    continue;
                }
                entry.defaultStroke().ifPresent(stroke -> {
                    Object name = keys.get(stroke);
                    if (name != null && !name.toString().startsWith(BINDING_PREFIX)) {
                        keys.remove(stroke);
                        released.put(stroke, name);
                    }
                });
            }
            return released;
        }

        /** The extension goes: the editor's own default strokes come back where nothing else took them. */
        private static void restoreDefaultCanvasStrokes(CircuitCanvas pane, Map<KeyStroke, Object> released) {
            InputMap keys = pane.getInputMap(JComponent.WHEN_FOCUSED);
            released.forEach((stroke, name) -> {
                if (keys.get(stroke) == null) {
                    keys.put(stroke, name);
                }
            });
        }

        /**
         * Works out every stroke again, sets the menu accelerators and rebinds the canvas keys of every pane;
         * unbinding everything first, so a stroke that moved between two canvas actions is never lost.
         */
        void apply() {
            applied.clear();
            Set<KeyStroke> taken = new HashSet<>();
            for (ShortcutEntry entry : ShortcutCatalog.entries()) {
                Action action = actions.get(entry.id());
                if (action == null) {
                    continue;
                }
                Optional<KeyStroke> stroke = bindings.strokeFor(entry.id()).filter(taken::add);
                stroke.ifPresent(found -> applied.put(entry.id(), found));
                if (entry.scope() == Scope.WINDOW) {
                    action.putValue(Action.ACCELERATOR_KEY, stroke.orElse(null));
                }
            }
            boundByPane.forEach((pane, bound) -> {
                unbindCanvasKeys(pane, bound);
                bindCanvasKeys(pane, bound);
            });
        }

        private void bindCanvasKeys(CircuitCanvas pane, Map<String, KeyStroke> bound) {
            for (ShortcutEntry entry : ShortcutCatalog.entries()) {
                Action action = actions.get(entry.id());
                KeyStroke stroke = applied.get(entry.id());
                if (entry.scope() == Scope.CANVAS && action != null && stroke != null) {
                    pane.bindKey(stroke, BINDING_PREFIX + entry.id(), action);
                    bound.put(entry.id(), stroke);
                }
            }
        }

        private static void unbindCanvasKeys(CircuitCanvas pane, Map<String, KeyStroke> bound) {
            bound.forEach((id, stroke) -> pane.unbindKey(stroke, BINDING_PREFIX + id));
            bound.clear();
        }

        /** The shortcut the last {@link #apply()} gave {@code id} (menu accelerator or canvas key), if any. */
        Optional<KeyStroke> strokeOf(String id) {
            return Optional.ofNullable(applied.get(id));
        }
    }
}
