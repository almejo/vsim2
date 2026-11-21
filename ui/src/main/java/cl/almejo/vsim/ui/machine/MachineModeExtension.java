package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;

/**
 * Puts the tab of every «Máquina de estados» in machine mode ({@link MachineMode}); other tabs are left as they are.
 * Installed after every extension that adds a menu item, a toolbar button or a shortcut, so it sees them all. In every
 * tab, a machine's row in Problemas goes to the machine ({@link MachineProblemNavigation}), and a machine's piece
 * shows its machine's name in Propiedades, to rename it ({@link MachineNameRows}), and a summary of its machine with
 * «Entrar ↘ para cambiarlas» ({@link MachinePieceRows}); «Duplicar como otra máquina» is offered in Editar, on a
 * machine's piece and in the palette ({@link MachineDuplicationActions}); a machine's test mode starts from Lógica ▸
 * «Probar la máquina…» and from a machine's piece too ({@link MachineTestEntries}).
 */
public final class MachineModeExtension implements EditorExtension {

    private final MachineStateSource stateSource;

    public MachineModeExtension() {
        this(MachineStateSource.standard());
    }

    /** With the active state read through {@code stateSource} (tests). */
    MachineModeExtension(MachineStateSource stateSource) {
        this.stateSource = Objects.requireNonNull(stateSource, "stateSource");
    }

    @Override
    public Runnable install(EditorView view) {
        // before the machine mode: a machine's tab disables «Duplicar como otra máquina» with the circuit's actions
        Runnable removeDuplication = MachineDuplicationActions.install(view);
        // before the machine mode too: a machine's tab keeps Lógica ▸ «Probar la máquina…»
        MachineTestEntries testEntries = MachineTestEntries.install(view);
        Runnable leaveMachineMode = MachineMode.install(view, stateSource);
        testEntries.followMachineMode();
        Runnable stopNavigating = MachineProblemNavigation.install(view);
        Runnable removeNameRows = view.labelFeatures().properties()
                .addComponentRows(new MachineNameRows(view.openDocument(), MachineLibrary.of(view)));
        Runnable removePieceRows = view.labelFeatures().properties()
                .addComponentRows(new MachinePieceRows(view.openDocument(), view::enterComponent));
        // last: a new view of a machine being tested (Nueva vista, the group moved) tests too, everything installed
        MachineMode.of(view).ifPresent(MachineMode::joinTheTestUnderWay);
        return () -> {
            removePieceRows.run();
            removeNameRows.run();
            stopNavigating.run();
            leaveMachineMode.run();
            testEntries.uninstall();
            removeDuplication.run();
        };
    }
}
