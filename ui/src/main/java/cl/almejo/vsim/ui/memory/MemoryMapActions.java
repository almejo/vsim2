package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.OptionalLong;

/**
 * What the «Mapa de memoria» asks of the rest of the editor, bound to one editor view: open the content editor at an
 * address (a click on a cell) and «Volcar…» a RAM. The defaults do neither (the cells open nothing and
 * «Volcar…» is disabled); {@link #of} wires in the editor.
 */
public interface MemoryMapActions {

    /** The live actions of {@code view}: cells open the content editor, «Volcar…» dumps through {@code launcher}. */
    static MemoryMapActions of(EditorView view, MemoryEditorLauncher launcher) {
        return new MemoryMapActions() {
            @Override
            public boolean canOpenEditor() {
                return true;
            }

            @Override
            public void openEditor(ElementId componentId, long address) {
                launcher.open(view, componentId, OptionalLong.of(address));
            }

            @Override
            public boolean canDump() {
                return true;
            }

            @Override
            public void dump(ElementId componentId) {
                launcher.dump(view, componentId);
            }
        };
    }

    default boolean canOpenEditor() {
        return false;
    }

    /** Opens the content editor of the memory {@code componentId} at {@code address}. */
    default void openEditor(ElementId componentId, long address) {
    }

    default boolean canDump() {
        return false;
    }

    /** «Volcar…»: saves the live words of the RAM {@code componentId} to a file the user chooses. */
    default void dump(ElementId componentId) {
    }
}
