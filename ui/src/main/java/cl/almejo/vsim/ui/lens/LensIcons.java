package cl.almejo.vsim.ui.lens;

import java.util.List;

/** The icon of the lens' toolbar button and menu item: a small piece opening into a big one, by file name under {@code icons/}. */
public final class LensIcons {

    public static final String LENS = "lens";

    private LensIcons() {
    }

    /** Every lens icon, once. */
    public static List<String> names() {
        return List.of(LENS);
    }
}
