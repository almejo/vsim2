package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.InspectorPanel;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Drawing and editing in a machine's tab (spec 2026-11-14 §1 «Drawing»), put in place by {@link MachineMode}: the
 * gestures on the diagram ({@link MachineTool}), the inline editor of names and conditions
 * ({@link MachineInlineEditor}), the right panel's rows ({@link MachineInspectorRows}) in place of the circuit's
 * «Propiedades», «Borrar» and Supr deleting the diagram's selection, and the Editar items «Nuevo estado», «Nueva
 * transición» and «Escribir el nombre o la condición» (with words «Acciones» finds them by).
 */
final class MachineEditing {

    private static final String DELETE = "machine-delete";
    private static final String DELETE_SHORTCUT_ID = "edit.delete";
    /** Retroceso deletes on the diagram as on the circuit canvas, whatever key «Borrar» is given. */
    private static final KeyStroke ALIAS_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);

    private final MachineTool tool;
    private final MachineInlineEditor inline;
    private final MachineInspectorRows rows;
    private final Runnable uninstall;

    private MachineEditing(MachineTool tool, MachineInlineEditor inline, MachineInspectorRows rows,
                           Runnable uninstall) {
        this.tool = tool;
        this.inline = inline;
        this.rows = rows;
        this.uninstall = uninstall;
    }

    /** Puts the editing of {@code canvas} and {@code palette} in {@code view}; {@link #uninstall()} takes it away. */
    static MachineEditing install(EditorView view, MachineCanvas canvas, MachinePalette palette) {
        MachineInlineEditor inline = new MachineInlineEditor(canvas);
        MachineTool tool = new MachineTool(canvas, palette, inline, view::showNotice);
        MachineInspectorRows rows = new MachineInspectorRows(canvas,
                finding -> canvas.reveal(finding, Animations.enabled(view.services().preferences())),
                MachineLibrary.of(view), view::showNotice,
                () -> Animations.enabled(view.services().preferences()));
        List<Runnable> undo = new ArrayList<>();
        undo.add(tool.install());
        if (view.inspector().title(InspectorPanel.PROPERTIES).isPresent()) {
            undo.add(view.inspector().showInstead(InspectorPanel.PROPERTIES, rows.inScrollPane()));
        }
        undo.add(view.actions().deleteContentInstead(tool));
        undo.add(view.selectionActions().clipboardContentInstead(
                new MachineClipboard(canvas, tool, FragmentClipboard.system(), view::showNotice)));
        undo.add(canvas.selection().addListener(view.selectionActions()::refresh));
        undo.add(canvas.selection().addListener(view.actions()::refresh));
        undo.add(bindDelete(canvas, view.actions().delete(), view.services().preferences()));
        undo.add(addMenuItems(view.menu(MenuId.EDIT), view.actions().delete(), tool));
        undo.add(rows::dispose);
        undo.add(inline::dispose);
        return new MachineEditing(tool, inline, rows, () -> {
            for (int index = undo.size() - 1; index >= 0; index--) {
                undo.get(index).run();
            }
        });
    }

    MachineTool tool() {
        return tool;
    }

    MachineInlineEditor inlineEditor() {
        return inline;
    }

    MachineInspectorRows rows() {
        return rows;
    }

    void uninstall() {
        uninstall.run();
    }

    /**
     * The keys of {@code delete} on the diagram: Retroceso always (the editor's fixed alias), and whatever key the
     * user gave «Borrar» in Atajos de teclado (Supr by default), following later changes made in any window.
     */
    private static Runnable bindDelete(MachineCanvas canvas, Action delete, AppPreferences preferences) {
        InputMap keys = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        keys.put(ALIAS_KEY, DELETE);
        Runnable unbind = KeyBindings.follow(canvas, DELETE_SHORTCUT_ID, DELETE, delete, preferences,
                Set.of(ALIAS_KEY));
        return () -> {
            unbind.run();
            keys.remove(ALIAS_KEY);
        };
    }

    /** The machine's items right after «Borrar» in Editar, after a separator; returns their removal. */
    private static Runnable addMenuItems(JMenu edit, Action delete, MachineTool tool) {
        List<Component> added = new ArrayList<>();
        added.add(new JPopupMenu.Separator());
        added.add(item(tool.newStateAction(), "machine.action.newStateSearch"));
        added.add(item(tool.newTransitionAction(), "machine.action.newTransitionSearch"));
        added.add(item(tool.editTextAction(), "machine.action.editTextSearch"));
        int position = indexAfter(edit, delete);
        for (Component component : added) {
            edit.getPopupMenu().insert(component, position++);
        }
        return () -> added.forEach(edit.getPopupMenu()::remove);
    }

    private static JMenuItem item(Action action, String searchKey) {
        JMenuItem item = new JMenuItem(action);
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text(searchKey));
        return item;
    }

    /** The position just after the item of {@code action} in {@code menu}, else its end. */
    private static int indexAfter(JMenu menu, Action action) {
        Component[] items = menu.getMenuComponents();
        for (int index = 0; index < items.length; index++) {
            if (items[index] instanceof JMenuItem item && item.getAction() == action) {
                return index + 1;
            }
        }
        return items.length;
    }
}
