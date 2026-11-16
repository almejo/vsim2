package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.actions.DisabledWithReason;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.LeftColumn;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which of a view's actions a machine's tab disables, and why (spec 2026-11-14 §1 «The machine tab»: the toolbar is
 * the same, what does not apply is disabled with a tooltip saying why; review focus 4: no circuit action may reach the
 * hidden pins-only body). The rule leans to safety, so a circuit feature added later is disabled here until someone
 * decides it applies:
 * <ul>
 * <li>Archivo, Simulación, Ventana and Ayuda keep their items, except the ones that act on the circuit's parts
 * (Importar, the exports of the drawing, printing, splitting the panes);</li>
 * <li>Editar, Ver, Herramientas and Lógica lose theirs, except undo/redo, Borrar, Cortar, Copiar, Pegar, Duplicar and
 * Seleccionar todo (they act on the diagram), the zoom, the panels, the problems, the presentation and the practice,
 * which work on any tab;</li>
 * <li>a toolbar button or a keyboard-only action that no menu keeps is disabled too.</li>
 * </ul>
 * Items without an action (Recientes, Ejemplos, the pace of Presentar) open files or change settings and stay.
 */
final class MachineActionPolicy {

    /** The window features that act on the window, the simulation or the settings, never on the circuit's parts. */
    private static final Set<String> KEPT_IDS = Set.of("waveforms.toggle", "view.presentationMode", "view.aiPresent",
            "view.showUsedShortcuts", "tools.karnaughPractice", "help.findAction", "tools.aiPanel",
            "file.preferences", "file.exportWindowImage");
    /** Items of the kept menus that act on the circuit's parts. */
    private static final Set<String> CIRCUIT_IDS_IN_KEPT_MENUS = Set.of("file.print", "file.exportImage",
            "file.exportPdf", "file.exportLogisim", "window.splitHorizontal", "window.splitVertical",
            "window.removeSplit", "window.nextPane");
    private static final Set<MenuId> CIRCUIT_MENUS = Set.of(MenuId.EDIT, MenuId.VIEW, MenuId.TOOLS, MenuId.LOGIC);
    /** The disabled ones with a reason of their own; the rest say they are for parts and wires. */
    private static final Map<String, String> WHY_BY_ID = Map.ofEntries(
            Map.entry("edit.tidyWires", "machine.disabled.noWiresToTidy"),
            Map.entry("edit.tidyWiresOptions", "machine.disabled.noWiresToTidy"),
            Map.entry("edit.wiresFollow", "machine.disabled.noWires"),
            Map.entry("edit.wireModeContinuous", "machine.disabled.noWires"),
            Map.entry("edit.wireModePieces", "machine.disabled.noWires"),
            Map.entry("view.highlightNet", "machine.disabled.noWires"),
            Map.entry("view.busValues", "machine.disabled.noBuses"),
            Map.entry("view.showSheets", "machine.disabled.noSheets"));
    /** What a machine will do on its diagram in a later milestone: «Llega en un próximo hito». */
    private static final Set<String> LATER_IDS = Set.of("edit.find", "edit.findNext", "edit.findPrevious",
            "view.lens", "file.exportImage", "file.exportPdf", "file.print", "edit.copyCapture");

    private MachineActionPolicy() {
    }

    /** Disables every circuit action of {@code view} with its reason; the returned runnable gives them back. */
    static Runnable disableCircuitActions(EditorView view) {
        List<Runnable> releases = new ArrayList<>();
        reasons(view).forEach((action, reason) -> releases.add(DisabledWithReason.disable(action, reason)));
        return () -> {
            for (int index = releases.size() - 1; index >= 0; index--) {
                releases.get(index).run();
            }
        };
    }

    /** The actions of {@code view} a machine's tab disables, each with the tooltip that says why. */
    static Map<Action, String> reasons(EditorView view) {
        Map<Action, String> idByAction = new IdentityHashMap<>();
        view.shortcutActions().forEach((id, action) -> idByAction.put(action, id));
        Set<Action> kept = keptEverywhere(view, idByAction);
        Set<Action> later = laterActions(view.actions());
        Map<Action, String> reasons = new LinkedHashMap<>();
        for (MenuId menuId : MenuId.values()) {
            JMenu menu = view.menu(menuId);
            List<JMenuItem> imported = menuId == MenuId.FILE
                    ? ImportMenu.find(menu).map(MachineActionPolicy::itemsDeep).orElse(List.of()) : List.of();
            for (JMenuItem item : itemsDeep(menu)) {
                Action action = item.getAction();
                if (action == null || kept.contains(action) || reasons.containsKey(action)) {
                    continue;
                }
                String id = idByAction.getOrDefault(action, "");
                if (CIRCUIT_MENUS.contains(menuId) || imported.contains(item)
                        || CIRCUIT_IDS_IN_KEPT_MENUS.contains(id)) {
                    reasons.put(action, reason(id, later.contains(action)));
                } else {
                    kept.add(action); // Nuevo, Guardar, Iniciar, Reiniciar…: their toolbar buttons share it
                }
            }
        }
        List<Action> buttonsAndKeys = new ArrayList<>(toolbarActions(view));
        buttonsAndKeys.addAll(view.shortcutActions().values());
        for (Action action : buttonsAndKeys) {
            if (!kept.contains(action) && !reasons.containsKey(action)) {
                reasons.put(action, reason(idByAction.getOrDefault(action, ""), later.contains(action)));
            }
        }
        return reasons;
    }

    private static String reason(String id, boolean comesLater) {
        if (comesLater || LATER_IDS.contains(id)) {
            return Messages.text("machine.comesLater");
        }
        return Messages.format("machine.disabled.reason",
                Messages.text(WHY_BY_ID.getOrDefault(id, "machine.disabled.circuitOnly")));
    }

    /**
     * The actions that work the same on any tab, wherever they are: undo, the zoom, the panels, the problems… and
     * «Borrar» and the clipboard's, which the machine's tab points at the diagram's selection.
     */
    private static Set<Action> keptEverywhere(EditorView view, Map<Action, String> idByAction) {
        EditorActions actions = view.actions();
        Set<Action> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(List.of(actions.undo(), actions.redo(), actions.delete(), actions.selectTool(),
                actions.zoomIn(), actions.zoomOut(), actions.zoomReset(), actions.zoomToFit(), actions.centerView(),
                view.zoomControl().typeZoomAction(), view.problems().nextProblem(), view.problems().previousProblem(),
                view.leftColumn().toggleAction(LeftColumn.Section.CIRCUIT),
                view.leftColumn().toggleAction(LeftColumn.Section.PALETTE)));
        SelectionActions clipboard = view.selectionActions();
        kept.addAll(List.of(clipboard.cut(), clipboard.copy(), clipboard.paste(), clipboard.duplicate(),
                clipboard.selectAll()));
        kept.addAll(view.inspectorToggleActions());
        idByAction.forEach((action, id) -> {
            if (KEPT_IDS.contains(id)) {
                kept.add(action);
            }
        });
        return kept;
    }

    /** The editing a machine will do on its diagram (the hand and the zoom tool). */
    private static Set<Action> laterActions(EditorActions actions) {
        Set<Action> later = Collections.newSetFromMap(new IdentityHashMap<>());
        later.addAll(List.of(actions.panTool(), actions.zoomTool(), actions.zoomToSelection()));
        return later;
    }

    private static List<Action> toolbarActions(EditorView view) {
        List<Action> actions = new ArrayList<>();
        for (Component component : view.toolBar().getComponents()) {
            if (component instanceof AbstractButton button && button.getAction() != null) {
                actions.add(button.getAction());
            }
        }
        return actions;
    }

    /** Every item of {@code menu} and of its submenus (submenus themselves included), depth first. */
    private static List<JMenuItem> itemsDeep(JMenu menu) {
        List<JMenuItem> items = new ArrayList<>();
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item) {
                items.add(item);
                if (item instanceof JMenu submenu) {
                    items.addAll(itemsDeep(submenu));
                }
            }
        }
        return items;
    }
}
