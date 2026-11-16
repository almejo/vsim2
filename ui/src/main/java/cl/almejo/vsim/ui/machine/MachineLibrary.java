package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.library.TemplateActions;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * «Mis componentes» as a machine's rows see it: its entries (whose ids and names a machine must not take) and how a
 * changed entry is written back to its file (renaming a machine that is in the library renames its file too).
 *
 * @param entries the library's components
 * @param save    writes a bundle to the library's file and puts it in {@code entries}
 */
record MachineLibrary(ComponentLibrary entries, Consumer<DefinitionBundle> save) {

    MachineLibrary {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(save, "save");
    }

    /**
     * {@code view}'s library, written through its «Mis componentes» features ({@link TemplateActions}, which report a
     * failed write), looked up when a bundle is saved: they are installed in every window. A view without them (some
     * tests) only keeps the bundle in memory.
     */
    static MachineLibrary of(EditorView view) {
        ComponentLibrary entries = view.services().library();
        return new MachineLibrary(entries, bundle -> view.templateActions().ifPresentOrElse(
                actions -> actions.saveToLibrary(bundle), () -> entries.put(bundle)));
    }
}
