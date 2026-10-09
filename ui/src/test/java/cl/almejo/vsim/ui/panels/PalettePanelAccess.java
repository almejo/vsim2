package cl.almejo.vsim.ui.panels;

/** Lets tests in other packages see a palette's package-private state. */
public final class PalettePanelAccess {

    private PalettePanelAccess() {
    }

    /** The tree model now shown: the same instance means the palette did not rebuild. */
    public static Object treeModel(PalettePanel palette) {
        return palette.treeModel();
    }
}
