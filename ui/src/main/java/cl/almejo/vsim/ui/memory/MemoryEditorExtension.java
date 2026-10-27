package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.OptionalLong;

/**
 * Gives every editor view its memory content editor: a double click on a RAM or ROM and the part's context menu open
 * it through {@link MemoryEditorLauncher}.
 */
public final class MemoryEditorExtension implements EditorExtension {

    private final MemoryEditorLauncher launcher;

    public MemoryEditorExtension(MemoryEditorLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Runnable install(EditorView view) {
        view.setMemoryEditor(component -> launcher.open(view, component.id(), OptionalLong.empty()));
        Runnable removeContextMenu = view.addContextMenuContributor(new MemoryContextMenu(view, launcher));
        return () -> {
            removeContextMenu.run();
            view.clearMemoryEditor();
        };
    }
}
