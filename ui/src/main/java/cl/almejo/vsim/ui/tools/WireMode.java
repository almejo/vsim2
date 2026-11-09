package cl.almejo.vsim.ui.tools;

import java.util.Arrays;

/**
 * How Selección draws a wire from a pin or a wire (Editar ▸ «Dibujar cables»): {@link #CONTINUOUS}, each click fixes a
 * corner and a click on a pin or a wire ends (the default), or {@link #PIECES}, one press-drag-release per «L» as VSim
 * always did. The ids are what the preferences keep ({@code wireMode}).
 */
public enum WireMode {
    CONTINUOUS("continuous"),
    PIECES("pieces");

    private final String id;

    WireMode(String id) {
        this.id = id;
    }

    /** The id stored in the preferences. */
    public String id() {
        return id;
    }

    /** The mode stored as {@code id}; {@link #CONTINUOUS} for an id this version does not know. */
    public static WireMode of(String id) {
        return Arrays.stream(values()).filter(mode -> mode.id.equals(id)).findFirst().orElse(CONTINUOUS);
    }
}
