package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ShownAcceleratorItem;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.labels.TextTool;
import cl.almejo.vsim.ui.sticky.StickyNoteTool;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.InspectorPanel;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Drawing and editing in a machine's tab (spec 2026-11-14 §1 «Drawing»), put in place by {@link MachineMode}: the
 * gestures on the diagram ({@link MachineTool}), the inline editor of names and conditions
 * ({@link MachineInlineEditor}), the right panel's rows ({@link MachineInspectorRows}) in place of the circuit's
 * «Propiedades», «Borrar» and Supr deleting the diagram's selection, and the Editar items «Nuevo estado», «Nueva
 * transición» and «Escribir el nombre, la condición o la nota» (with words «Acciones» finds them by).
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
                () -> Animations.enabled(view.services().preferences()), inline::editNote,
                () -> deleteKeyName(view.services().preferences()));
        List<Runnable> undo = new ArrayList<>();
        canvas.setAnimations(() -> Animations.enabled(view.services().preferences()));
        undo.add(Animations.onChange(view.services().preferences(), canvas::animationsChanged));
        undo.add(tool.install());
        undo.add(followStickyNoteTool(view, palette));
        undo.add(followTextTool(view, palette));
        undo.add(followPanTool(view, canvas, palette));
        if (view.inspector().title(InspectorPanel.PROPERTIES).isPresent()) {
            undo.add(view.inspector().showInstead(InspectorPanel.PROPERTIES, rows.inScrollPane()));
        }
        undo.add(view.actions().deleteContentInstead(tool));
        undo.add(view.selectionActions().clipboardContentInstead(
                new MachineClipboard(canvas, tool, FragmentClipboard.system(), view::showNotice)));
        undo.add(refreshPasteOnFocus(view, canvas));
        undo.add(canvas.selection().addListener(view.selectionActions()::refresh));
        undo.add(canvas.selection().addListener(view.actions()::refresh));
        undo.add(bindDelete(canvas, view.actions().delete(), view.services().preferences()));
        undo.add(addMenuItems(view.menu(MenuId.EDIT), view.actions().delete(), tool));
        undo.add(rows::dispose);
        undo.add(inline::dispose);
        // a close finishes a half-typed name or condition first, as it does the circuit's (review finding 12)
        undo.add(view.addInlineEditor(inline.editor()));
        return new MachineEditing(tool, inline, rows, () -> {
            for (int index = undo.size() - 1; index >= 0; index--) {
                undo.get(index).run();
            }
        });
    }

    /** Pegar asks the clipboard again when the diagram gets the focus back, as a circuit's pane does. */
    private static Runnable refreshPasteOnFocus(EditorView view, MachineCanvas canvas) {
        FocusListener refresh = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                view.selectionActions().refreshPaste();
            }
        };
        canvas.addFocusListener(refresh);
        return () -> canvas.removeFocusListener(refresh);
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
     * The toolbar's «Nota adhesiva» (and its menu item and shortcut) chooses the palette's «Nota» in this tab instead
     * of the circuit's note tool, and the two show the same choice: the toolbar's button is pressed while «Nota» is
     * chosen, and Selección is pressed again once it is used or dropped. Returns what takes it back.
     */
    private static Runnable followStickyNoteTool(EditorView view, MachinePalette palette) {
        JToggleButton note = palette.noteButton();
        Runnable stopChoosing = view.chooseToolInstead(StickyNoteTool.class, () -> note.setSelected(true));
        ItemListener follow = event -> {
            Optional<JToggleButton> toolbarButton = view.toolButton(StickyNoteTool.class);
            if (event.getStateChange() == ItemEvent.SELECTED) {
                toolbarButton.ifPresent(button -> button.setSelected(true));
            } else if (toolbarButton.filter(JToggleButton::isSelected).isPresent()) {
                view.useDefaultToolOnEveryPane();
            }
        };
        note.addItemListener(follow);
        return () -> {
            note.removeItemListener(follow);
            stopChoosing.run();
        };
    }

    /**
     * The toolbar's «Texto» (and its menu item and shortcut) chooses the palette's «Texto» in this tab instead of the
     * circuit's text tool, and the two show the same choice, as with the sticky note. Returns what takes it back.
     */
    private static Runnable followTextTool(EditorView view, MachinePalette palette) {
        JToggleButton text = palette.textButton();
        Runnable stopChoosing = view.chooseToolInstead(TextTool.class, () -> text.setSelected(true));
        ItemListener follow = event -> {
            Optional<JToggleButton> toolbarButton = view.toolButton(TextTool.class);
            if (event.getStateChange() == ItemEvent.SELECTED) {
                toolbarButton.ifPresent(button -> button.setSelected(true));
            } else if (toolbarButton.filter(JToggleButton::isSelected).isPresent()) {
                view.useDefaultToolOnEveryPane();
            }
        };
        text.addItemListener(follow);
        return () -> {
            text.removeItemListener(follow);
            stopChoosing.run();
        };
    }

    /**
     * The toolbar's hand («Mover vista», its menu item and shortcut) pans the diagram: it reaches the hidden panes as
     * on any tab (so its button is pressed while it is chosen and Selección takes it back), and while the active pane
     * holds it a left drag on the diagram pans ({@link MachineCanvas#handChosen}). The hand and the palette's tools
     * take each other's place, and Esc on the diagram puts the hand down. Returns what takes it back.
     */
    private static Runnable followPanTool(EditorView view, MachineCanvas canvas, MachinePalette palette) {
        Runnable follow = () -> {
            boolean chosen = view.canvas().tool() instanceof PanTool;
            if (chosen && !canvas.isPanning()) {
                palette.clearTool();
                canvas.handChosen(view::useDefaultToolOnEveryPane);
            } else if (!chosen && canvas.isPanning()) {
                canvas.handPutDown();
            }
        };
        Runnable stopFollowingPanes = view.forEachPane(pane -> pane.addToolListener(tool -> follow.run()));
        ItemListener putHandDown = event -> {
            if (event.getStateChange() == ItemEvent.SELECTED && canvas.isPanning()) {
                view.useDefaultToolOnEveryPane();
            }
        };
        List<JToggleButton> paletteTools = List.of(palette.stateButton(), palette.transitionButton(),
                palette.noteButton(), palette.textButton());
        paletteTools.forEach(button -> button.addItemListener(putHandDown));
        follow.run();
        return () -> {
            paletteTools.forEach(button -> button.removeItemListener(putHandDown));
            stopFollowingPanes.run();
            canvas.handPutDown();
        };
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

    /** The name of the key «Borrar» has now («Supr» by default), or Retroceso's when the user left it without one. */
    private static String deleteKeyName(AppPreferences preferences) {
        return KeyNames.describe(new KeyBindings(preferences).strokeFor(DELETE_SHORTCUT_ID).orElse(ALIAS_KEY));
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

    /** An Editar item of the diagram, showing the diagram's key of its action (F2) without running by it. */
    private static JMenuItem item(Action action, String searchKey) {
        JMenuItem item = new ShownAcceleratorItem(action);
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
