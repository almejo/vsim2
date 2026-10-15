package cl.almejo.vsim.ui.i18n;

import cl.almejo.vsim.core.document.FrameTint;

/** The visible names of the frame colours, shared by the frame menu and the Propiedades panel. */
public final class FrameTintNames {

    private FrameTintNames() {
    }

    /** The visible name of {@code tint}. */
    public static String of(FrameTint tint) {
        return Messages.text(switch (tint) {
            case NONE -> "frames.tint.none";
            case BLUE -> "frames.tint.blue";
            case GREEN -> "frames.tint.green";
            case YELLOW -> "frames.tint.yellow";
            case RED -> "frames.tint.red";
            case PURPLE -> "frames.tint.purple";
            case GRAY -> "frames.tint.gray";
        });
    }
}
