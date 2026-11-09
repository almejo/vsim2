package cl.almejo.vsim.ui.wiring;

import java.util.List;

/** The icons of the two items of Editar ▸ «Dibujar cables», by file name under {@code icons/}. */
public final class WireModeIcons {

    public static final String CONTINUOUS = "wire-continuous";
    public static final String PIECES = "wire-pieces";

    private WireModeIcons() {
    }

    /** Every wire mode icon, once. */
    public static List<String> names() {
        return List.of(CONTINUOUS, PIECES);
    }
}
