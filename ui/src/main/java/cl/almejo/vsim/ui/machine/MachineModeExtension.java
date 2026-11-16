package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;

/**
 * Puts the tab of every «Máquina de estados» in machine mode ({@link MachineMode}); other tabs are left as they are.
 * Installed after every extension that adds a menu item, a toolbar button or a shortcut, so it sees them all. In every
 * tab, a machine's row in Problemas goes to the machine ({@link MachineProblemNavigation}), and a machine's piece
 * shows its machine's name in Propiedades, to rename it ({@link MachineNameRows}), and a summary of its machine with
 * «Entrar ↘ para cambiarlas» ({@link MachinePieceRows}).
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
        Runnable leaveMachineMode = MachineMode.install(view, stateSource);
        Runnable stopNavigating = MachineProblemNavigation.install(view);
        Runnable removeNameRows = view.labelFeatures().properties()
                .addComponentRows(new MachineNameRows(view.openDocument(), view.services().library()));
        Runnable removePieceRows = view.labelFeatures().properties()
                .addComponentRows(new MachinePieceRows(view.openDocument(), view::enterComponent));
        return () -> {
            removePieceRows.run();
            removeNameRows.run();
            stopNavigating.run();
            leaveMachineMode.run();
        };
    }
}
