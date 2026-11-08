package cl.almejo.vsim.ui.window;

/** Where in the toolbar an extension hangs a component: groups by meaning, not «at the end». */
public enum ToolbarSlot {
    /** Right after Guardar, in the file group, before the separator that precedes Deshacer. */
    AFTER_FILE,
    /** Right after the speed selector, in the simulation group. */
    AFTER_SIMULATION,
    /** The far right, after a horizontal glue that pushes the slot to the window's right edge. */
    RIGHT
}
