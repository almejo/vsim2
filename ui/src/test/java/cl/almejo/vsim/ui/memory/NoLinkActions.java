package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.window.EditorView;

/** {@link MemoryLinkActions} for tests that never link: every call does nothing. */
final class NoLinkActions implements MemoryLinkActions {

    static final NoLinkActions INSTANCE = new NoLinkActions();

    @Override
    public void link(EditorView view, ElementId componentId) {
    }

    @Override
    public void unlink(EditorView view, ElementId componentId) {
    }

    @Override
    public void openExternal(EditorView view, ElementId componentId) {
    }
}
