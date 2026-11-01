package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * Adds the «↔» / «⤡» tab of resizable parts (Tasks 6b, 7b) to the select tool of every pane. Installed after the
 * sticky notes and before the frames, so a note lying over the tab keeps its press and the tab wins over a frame's title bar behind it.
 * No menu item and no shortcut. The returned runnable takes the gestures off every pane.
 */
public final class PartResizeExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        return view.forEachPane(PartResizeExtension::installOn);
    }

    private static Runnable installOn(CircuitCanvas pane) {
        return pane.defaultTool() instanceof SelectTool selectTool
                ? selectTool.addDelegate(new ResizeGestures())
                : () -> {
                };
    }
}
