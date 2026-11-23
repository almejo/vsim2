package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.EnterableInstances;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuSlot;
import cl.almejo.vsim.ui.window.MenuSlots;

import javax.swing.Action;
import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where the test mode of a machine starts (plan 2026-11-14, hito 4, decision 7), besides the palette's «Probar…»:
 * Lógica ▸ «Probar la máquina…» (so «Acciones» finds it; enabled in a machine's tab, disabled with «Ya estás
 * probando» while it tests, and in any other tab with «Entra a una máquina de estados para probarla»), and the
 * right-click menu of a machine's piece, which enters that copy and starts testing it.
 */
final class MachineTestEntries {

    /** The shortcut catalog's id of Lógica ▸ «Probar la máquina…». */
    static final String SHORTCUT_ID = "tools.testMachine";

    private final EditorView view;
    private final Action action;
    private final List<Runnable> removals = new ArrayList<>();

    private MachineTestEntries(EditorView view) {
        this.view = view;
        this.action = Actions.of(Messages.text("action.testMachine"),
                () -> MachineMode.of(view).ifPresent(MachineMode::startTest));
        Actions.disabledBecause(action, () -> Messages.text(MachineMode.of(view).isEmpty() ? "machine.test.enterFirst"
                : "machine.test.alreadyTesting"));
    }

    /**
     * Puts the Lógica item and the piece's menu item in {@code view}. Call before the machine mode is installed, so
     * a machine's tab knows the item ({@link MachineActionPolicy} keeps it), and {@link #followMachineMode()} after.
     */
    static MachineTestEntries install(EditorView view) {
        MachineTestEntries entries = new MachineTestEntries(view);
        JMenuItem item = new JMenuItem(entries.action);
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text("action.testMachine.searchTerms"));
        Runnable shortcutRemover = view.registerShortcutAction(SHORTCUT_ID, entries.action);
        entries.removals.add(shortcutRemover);
        entries.removals.add(MenuSlots.add(view, MenuSlot.TEST_MACHINE, item));
        entries.removals.add(view.addContextMenuContributor(entries.pieceContributor()));
        entries.refresh();
        return entries;
    }

    /** The item follows this tab's machine mode from now on (call once it is installed). */
    void followMachineMode() {
        MachineMode.of(view).ifPresent(mode -> removals.add(mode.addTestModeListener(this::refresh)));
        refresh();
    }

    /** Takes everything back. */
    void uninstall() {
        for (int index = removals.size() - 1; index >= 0; index--) {
            removals.get(index).run();
        }
        removals.clear();
    }

    /** Lógica ▸ «Probar la máquina…». */
    Action action() {
        return action;
    }

    private void refresh() {
        Optional<MachineMode> mode = MachineMode.of(view);
        boolean testing = mode.filter(MachineMode::isTesting).isPresent();
        action.setEnabled(mode.isPresent() && !testing);
    }

    /** «Probar la máquina…» on the right-click menu of a machine's piece: enters that copy and tests it. */
    private ContextMenuContributor pieceContributor() {
        return (menu, target, selection, gridPosition) -> {
            Optional<DocumentElement> element = target.filter(found -> selection.size() == 1
                    && selection.contains(found.id()));
            if (element.isPresent() && element.get() instanceof Component piece
                    && MachineDuplicationActions.isMachinePiece(view.openDocument(), piece)) {
                menu.add(new JMenuItem(Actions.of(Messages.text("action.testMachine"), () -> testPiece(piece))));
            }
        };
    }

    private void testPiece(Component piece) {
        OpenDocument place = view.openDocument();
        EnterableInstances.definitionOf(place, piece).ifPresent(definitionId -> view.windowCommands()
                .showPlace(place.parent().orElse(place), Optional.of(definitionId),
                        EnterableInstances.contextOf(place, piece))
                .filter(view.windowCommands()::isShown) // not when the shown tab kept the front
                .flatMap(MachineMode::of)
                .ifPresent(MachineMode::startTest));
    }
}
