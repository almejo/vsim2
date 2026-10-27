package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.document.ElementId;

/**
 * What the «Mapa de memoria» asks of the rest of the editor, bound to one editor view: open the content editor at an
 * address (a click on a cell) and «Volcar…» a RAM. {@link #NONE} does neither (the cells do not open anything and
 * «Volcar…» is disabled) until the content editor is wired in.
 */
public interface MemoryMapActions {

    MemoryMapActions NONE = new MemoryMapActions() { };

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
