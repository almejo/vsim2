package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every action whose keyboard shortcut the user can change (spec §5.2), ordered by menu and then menu order. Ids are
 * {@code <group>.<action>}; the name is the action's own menu text. The 23 defaults are the editor's accelerators,
 * its canvas keys and F7 for «Avanzar» (M6.1); the others start without a shortcut. Defaults use the platform menu
 * modifier (⌘ on macOS).
 */
public final class ShortcutCatalog {

    private static final List<ShortcutEntry> ENTRIES = build(menuMask());

    private ShortcutCatalog() {
    }

    public static List<ShortcutEntry> entries() {
        return ENTRIES;
    }

    public static Optional<ShortcutEntry> find(String id) {
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    /** The platform menu modifier (Ctrl, or ⌘ on macOS); Ctrl when headless. The one place that decides it. */
    public static int menuMask() {
        // Toolkit.getMenuShortcutKeyMaskEx() throws HeadlessException in headless tests.
        return GraphicsEnvironment.isHeadless() ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static List<ShortcutEntry> build(int menu) {
        int shift = InputEvent.SHIFT_DOWN_MASK;
        List<ShortcutEntry> entries = new ArrayList<>();
        window(entries, "file.new", "action.new", MenuId.FILE, stroke(KeyEvent.VK_N, menu));
        window(entries, "file.open", "action.open", MenuId.FILE, stroke(KeyEvent.VK_O, menu));
        window(entries, "file.save", "action.save", MenuId.FILE, stroke(KeyEvent.VK_S, menu));
        window(entries, "file.saveAs", "action.saveAs", MenuId.FILE, stroke(KeyEvent.VK_S, menu | shift));
        window(entries, "file.preferences", "action.preferences", MenuId.FILE, null);
        window(entries, "file.close", "action.close", MenuId.FILE, stroke(KeyEvent.VK_W, menu));
        window(entries, "file.exit", "action.exit", MenuId.FILE, stroke(KeyEvent.VK_Q, menu));
        window(entries, "edit.undo", "action.undo", MenuId.EDIT, stroke(KeyEvent.VK_Z, menu));
        window(entries, "edit.redo", "action.redo", MenuId.EDIT, stroke(KeyEvent.VK_Z, menu | shift));
        canvas(entries, "edit.delete", "action.delete", stroke(KeyEvent.VK_DELETE, 0));
        canvas(entries, "edit.rotateClockwise", "action.rotateClockwise", stroke(KeyEvent.VK_R, 0));
        canvas(entries, "edit.rotateCounterClockwise", "action.rotateCounterClockwise", stroke(KeyEvent.VK_R, shift));
        window(entries, "edit.configure", "action.configure", MenuId.EDIT, null);
        window(entries, "edit.cut", "action.cut", MenuId.EDIT, stroke(KeyEvent.VK_X, menu));
        window(entries, "edit.copy", "action.copy", MenuId.EDIT, stroke(KeyEvent.VK_C, menu));
        window(entries, "edit.paste", "action.paste", MenuId.EDIT, stroke(KeyEvent.VK_V, menu));
        window(entries, "edit.duplicate", "action.duplicate", MenuId.EDIT, stroke(KeyEvent.VK_D, menu));
        window(entries, "edit.selectAll", "action.selectAll", MenuId.EDIT, stroke(KeyEvent.VK_A, menu));
        canvas(entries, "edit.label", "action.label", stroke(KeyEvent.VK_F2, 0));
        window(entries, "view.selectTool", "action.toolSelect", MenuId.VIEW, null);
        window(entries, "view.wireTool", "action.toolWire", MenuId.VIEW, null);
        window(entries, "view.panTool", "action.toolPan", MenuId.VIEW, null);
        window(entries, "view.zoomIn", "action.zoomIn", MenuId.VIEW, stroke(KeyEvent.VK_EQUALS, menu));
        window(entries, "view.zoomOut", "action.zoomOut", MenuId.VIEW, stroke(KeyEvent.VK_MINUS, menu));
        window(entries, "view.zoomReset", "action.zoomReset", MenuId.VIEW, stroke(KeyEvent.VK_0, menu));
        window(entries, "view.centerView", "action.centerView", MenuId.VIEW, null);
        entries.add(new ShortcutEntry("view.nextProblem", "action.nextProblem", MenuId.VIEW, Scope.CANVAS,
                Optional.of(stroke(KeyEvent.VK_F8, 0))));
        entries.add(new ShortcutEntry("view.previousProblem", "action.previousProblem", MenuId.VIEW, Scope.CANVAS,
                Optional.of(stroke(KeyEvent.VK_F8, shift))));
        window(entries, "waveforms.toggle", "menu.view.waveforms", MenuId.VIEW, null);
        window(entries, "simulation.start", "action.start", MenuId.SIMULATION, null);
        window(entries, "simulation.pause", "action.pause", MenuId.SIMULATION, null);
        window(entries, "simulation.step", "action.step", MenuId.SIMULATION, null);
        window(entries, "simulation.stepMode", "action.stepMode", MenuId.SIMULATION, null);
        window(entries, "simulation.advance", "action.advance", MenuId.SIMULATION, stroke(KeyEvent.VK_F7, 0));
        window(entries, "simulation.truthTable", "action.truthTable", MenuId.SIMULATION, null);
        window(entries, "window.newView", "action.newWindow", MenuId.WINDOW, null);
        return List.copyOf(entries);
    }

    private static void window(List<ShortcutEntry> entries, String id, String nameKey, MenuId group,
                               KeyStroke defaultStroke) {
        entries.add(new ShortcutEntry(id, nameKey, group, Scope.WINDOW, Optional.ofNullable(defaultStroke)));
    }

    /** Canvas keys of the Editar menu (Supr, R, Mayús+R, F2). */
    private static void canvas(List<ShortcutEntry> entries, String id, String nameKey, KeyStroke defaultStroke) {
        entries.add(new ShortcutEntry(id, nameKey, MenuId.EDIT, Scope.CANVAS, Optional.of(defaultStroke)));
    }

    private static KeyStroke stroke(int keyCode, int modifiers) {
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }
}
