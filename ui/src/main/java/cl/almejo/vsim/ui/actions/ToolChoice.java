package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.ui.tools.Tool;

import java.util.function.Supplier;

/**
 * The window's choice of tool (toolbar, Ver menu): one decision for every pane of the window, each pane getting its
 * own tool instance from {@link #choose}.
 */
public interface ToolChoice {

    /** Every pane uses a tool made by {@code newTool} (one instance each). */
    void choose(Supplier<? extends Tool> newTool);

    /** Every pane goes back to its default tool (Elegir). */
    void useDefault();
}
