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
import java.util.Set;

/**
 * Every action whose keyboard shortcut the user can change (spec §5.2), ordered by menu and then menu order. Ids are
 * {@code <group>.<action>}; the name is the action's own menu text. The 45 defaults are the editor's accelerators
 * (F5 for «Iniciar o pausar», F6 for «Avanzar N t», Ctrl+F, F3 and Mayús+F3 for «Buscar»), its canvas keys (the eight
 * arrows that move the selection, Z for the zoom tool, Mayús+1 and Mayús+2 for «Ver todo el circuito» and «Zoom a la
 * selección» included), F7 and Mayús+F7 for M6.1-B's two advances; the others start without a shortcut. Defaults use
 * the platform menu modifier (⌘ on macOS).
 */
public final class ShortcutCatalog {

    /**
     * Ids whose action does not exist yet (empty now that fronts B and C have arrived; kept for later fronts): they
     * stay out of the dialog and cannot be bound until the front that adds the action removes its id here.
     */
    private static final Set<String> AWAITING_ACTION = Set.of();

    private static final List<ShortcutEntry> ENTRIES = build(menuMask()).stream()
            .filter(entry -> !AWAITING_ACTION.contains(entry.id())).toList();

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
        window(entries, "file.exportImage", "action.exportImage", MenuId.FILE, null);
        // no default: Ctrl/⌘+P stays free for «Imprimir…»
        window(entries, "file.exportPdf", "action.exportPdf", MenuId.FILE, null);
        window(entries, "file.importTruthTable", "action.importTruthTable", MenuId.FILE, null);
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
        window(entries, "edit.find", "action.find", MenuId.EDIT, stroke(KeyEvent.VK_F, menu));
        window(entries, "edit.findNext", "action.findNext", MenuId.EDIT, stroke(KeyEvent.VK_F3, 0));
        window(entries, "edit.findPrevious", "action.findPrevious", MenuId.EDIT, stroke(KeyEvent.VK_F3, shift));
        window(entries, "edit.frameSelection", "action.frameSelection", MenuId.EDIT, stroke(KeyEvent.VK_G, menu));
        window(entries, "edit.tidyWires", "action.tidyWires", MenuId.EDIT,
                stroke(KeyEvent.VK_L, menu | InputEvent.ALT_DOWN_MASK));
        window(entries, "edit.tidyWiresOptions", "action.tidyWiresOptions", MenuId.EDIT,
                stroke(KeyEvent.VK_L, menu | InputEvent.ALT_DOWN_MASK | shift));
        window(entries, "edit.wiresFollow", "action.wiresFollow", MenuId.EDIT, null);
        canvas(entries, "edit.label", "action.label", stroke(KeyEvent.VK_F2, 0));
        moveKeys(entries, "edit.moveLeft", "action.moveLeft", "action.moveLeftFar", KeyEvent.VK_LEFT);
        moveKeys(entries, "edit.moveRight", "action.moveRight", "action.moveRightFar", KeyEvent.VK_RIGHT);
        moveKeys(entries, "edit.moveUp", "action.moveUp", "action.moveUpFar", KeyEvent.VK_UP);
        moveKeys(entries, "edit.moveDown", "action.moveDown", "action.moveDownFar", KeyEvent.VK_DOWN);
        window(entries, "view.selectTool", "action.toolSelect", MenuId.VIEW, null);
        window(entries, "view.wireTool", "action.toolWire", MenuId.VIEW, null);
        window(entries, "view.frameTool", "action.toolFrame", MenuId.VIEW, null);
        window(entries, "view.panTool", "action.toolPan", MenuId.VIEW, null);
        // a plain letter: a canvas key, so typing «z» in a text field never picks the tool (zoom spec §5.1)
        canvasView(entries, "view.zoomTool", "action.toolZoom", stroke(KeyEvent.VK_Z, 0));
        window(entries, "view.zoomIn", "action.zoomIn", MenuId.VIEW, stroke(KeyEvent.VK_EQUALS, menu));
        window(entries, "view.zoomOut", "action.zoomOut", MenuId.VIEW, stroke(KeyEvent.VK_MINUS, menu));
        window(entries, "view.zoomReset", "action.zoomReset", MenuId.VIEW, stroke(KeyEvent.VK_0, menu));
        // Figma's keys; canvas keys, because a menu accelerator without Ctrl would fire while typing (zoom spec §5.1)
        canvasView(entries, "view.zoomToFit", "action.zoomToFit", stroke(KeyEvent.VK_1, shift));
        canvasView(entries, "view.zoomToSelection", "action.zoomToSelection", stroke(KeyEvent.VK_2, shift));
        window(entries, "view.centerView", "action.centerView", MenuId.VIEW, null);
        window(entries, "view.cycleGrid", "action.cycleGrid", MenuId.VIEW, null);
        window(entries, "view.snap", "action.snap", MenuId.VIEW, stroke(KeyEvent.VK_M, menu | shift));
        window(entries, "view.snapToParts", "shortcut.snapToParts", MenuId.VIEW, null);
        window(entries, "view.snapToGuides", "shortcut.snapToGuides", MenuId.VIEW, null);
        window(entries, "view.snapToGridLines", "shortcut.snapToGridLines", MenuId.VIEW, null);
        window(entries, "view.showGuides", "action.showGuides", MenuId.VIEW, null);
        window(entries, "view.addHorizontalGuide", "action.addHorizontalGuide", MenuId.VIEW, null);
        window(entries, "view.addVerticalGuide", "action.addVerticalGuide", MenuId.VIEW, null);
        window(entries, "view.clearGuides", "action.clearGuides", MenuId.VIEW, null);
        canvasView(entries, "view.nextProblem", "action.nextProblem", stroke(KeyEvent.VK_F8, 0));
        canvasView(entries, "view.previousProblem", "action.previousProblem", stroke(KeyEvent.VK_F8, shift));
        window(entries, "waveforms.toggle", "menu.view.waveforms", MenuId.VIEW, null);
        window(entries, "view.aiPresent", "action.aiPresent", MenuId.VIEW, null);
        window(entries, "simulation.start", "action.start", MenuId.SIMULATION, null);
        window(entries, "simulation.pause", "action.pause", MenuId.SIMULATION, null);
        window(entries, "simulation.startOrPause", "action.startOrPause", MenuId.SIMULATION,
                stroke(KeyEvent.VK_F5, 0));
        window(entries, "simulation.restart", "action.restart", MenuId.SIMULATION, stroke(KeyEvent.VK_F5, menu));
        window(entries, "simulation.step", "shortcut.fixedStep", MenuId.SIMULATION, stroke(KeyEvent.VK_F6, 0));
        window(entries, "simulation.stepSize", "action.stepSize", MenuId.SIMULATION, null);
        window(entries, "simulation.stepMode", "action.stepMode", MenuId.SIMULATION, null);
        window(entries, "simulation.advance", "action.advance", MenuId.SIMULATION, stroke(KeyEvent.VK_F7, 0));
        window(entries, "simulation.advanceToClockEdge", "action.advanceToClockEdge", MenuId.SIMULATION,
                stroke(KeyEvent.VK_F7, shift));
        window(entries, "simulation.truthTable", "action.truthTable", MenuId.SIMULATION, null);
        window(entries, "tools.circuitFromTruthTable", "action.circuitFromTruthTable", MenuId.TOOLS, null);
        window(entries, "tools.karnaughPractice", "action.karnaughPractice", MenuId.TOOLS, null);
        window(entries, "tools.circuitFromExpressions", "action.circuitFromExpressions", MenuId.TOOLS, null);
        window(entries, "tools.aiPanel", "action.aiPanel", MenuId.TOOLS, null);
        window(entries, "window.newView", "action.newWindow", MenuId.WINDOW, null);
        // no defaults: F6 belongs to «Avanzar N t» (and to Swing's split-pane focus)
        window(entries, "window.splitHorizontal", "shortcut.splitHorizontal", MenuId.WINDOW, null);
        window(entries, "window.splitVertical", "shortcut.splitVertical", MenuId.WINDOW, null);
        window(entries, "window.removeSplit", "action.removeSplit", MenuId.WINDOW, null);
        window(entries, "window.nextPane", "action.nextPane", MenuId.WINDOW, null);
        return List.copyOf(entries);
    }

    private static void window(List<ShortcutEntry> entries, String id, String nameKey, MenuId group,
                               KeyStroke defaultStroke) {
        entries.add(new ShortcutEntry(id, nameKey, group, Scope.WINDOW, Optional.ofNullable(defaultStroke)));
    }

    /** Canvas keys of the Editar menu (Supr, R, Mayús+R, F2, the arrows). */
    private static void canvas(List<ShortcutEntry> entries, String id, String nameKey, KeyStroke defaultStroke) {
        entries.add(new ShortcutEntry(id, nameKey, MenuId.EDIT, Scope.CANVAS, Optional.of(defaultStroke)));
    }

    /** Canvas keys of the Ver menu (Z, Mayús+1, Mayús+2, F8, Mayús+F8): the menu items show no accelerator. */
    private static void canvasView(List<ShortcutEntry> entries, String id, String nameKey, KeyStroke defaultStroke) {
        entries.add(new ShortcutEntry(id, nameKey, MenuId.VIEW, Scope.CANVAS, Optional.of(defaultStroke)));
    }

    /** An arrow that moves the selection one grid unit; with Mayús five (the id of that one ends in «Far»). */
    private static void moveKeys(List<ShortcutEntry> entries, String id, String nameKey, String farNameKey,
                                 int arrowKey) {
        canvas(entries, id, nameKey, stroke(arrowKey, 0));
        canvas(entries, id + "Far", farNameKey, stroke(arrowKey, InputEvent.SHIFT_DOWN_MASK));
    }

    private static KeyStroke stroke(int keyCode, int modifiers) {
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }
}
