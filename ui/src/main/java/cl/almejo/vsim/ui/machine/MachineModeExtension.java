package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;

/**
 * Puts the tab of every «Máquina de estados» in machine mode ({@link MachineMode}); other tabs are left as they are.
 * Installed after every extension that adds a menu item, a toolbar button or a shortcut, so it sees them all.
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
        return MachineMode.install(view, stateSource);
    }
}
