package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.OptionalLong;

/**
 * Opens the content editor of a memory over an editor window: from a double click, the context menu, the inspector
 * (later) and the memory map (a click on a cell passes its address). The link actions are the application's
 * ({@link MemoryLinkActions#NONE} until the linked files exist).
 */
public class MemoryEditorLauncher {

    private final MemoryLinkActions linkActions;

    public MemoryEditorLauncher(MemoryLinkActions linkActions) {
        this.linkActions = linkActions;
    }

    /**
     * Shows the editor of the memory {@code componentId} (modal, on the EDT) starting at {@code address} when given.
     * Does nothing when the component is gone or is not a memory.
     */
    public void open(EditorView view, ElementId componentId, OptionalLong address) {
        boolean memory = view.openDocument().document().component(componentId)
                .filter(component -> MemoryTypes.kindOf(component.typeId()).isPresent())
                .isPresent();
        if (!memory) {
            return;
        }
        new MemoryEditorDialog(view.openDocument(), componentId, view, linkActions).show(view, address);
    }

    /**
     * «Volcar…» without opening the editor (the part's context menu, the map): asks for a file and writes the words
     * the simulation holds now. Never the linked file.
     */
    public void dump(EditorView view, ElementId componentId) {
        new MemoryEditorDialog(view.openDocument(), componentId, view, linkActions,
                new SwingMemoryEditorPrompts(() -> view)).dumpToFile();
    }
}
