package cl.almejo.vsim.ui.panels;

import java.awt.datatransfer.Transferable;

/** Lets tests in other packages see a palette's package-private state. */
public final class PalettePanelAccess {

    private PalettePanelAccess() {
    }

    /** The tree model now shown: the same instance means the palette did not rebuild. */
    public static Object treeModel(PalettePanel palette) {
        return palette.treeModel();
    }

    /** True while an entry of the palette is highlighted. */
    public static boolean hasSelection(PalettePanel palette) {
        return palette.hasSelection();
    }

    /** What a drag from the highlighted entry carries. */
    public static Transferable dragTransferable(PalettePanel palette) {
        return palette.dragTransferable();
    }

    /** The drop actions a drag from the palette offers. */
    public static int dragSourceActions(PalettePanel palette) {
        return palette.dragSourceActions();
    }
}
