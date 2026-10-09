package cl.almejo.vsim.ui.panels;

import java.util.Optional;

/** Text carried by drag and drop from the palette to a canvas. */
public final class PaletteTransfer {

    private static final String PREFIX = "vsim2-component:";

    private PaletteTransfer() {
    }

    public static String encode(String typeId) {
        return PREFIX + typeId;
    }

    public static Optional<String> decode(String text) {
        if (text == null || !text.startsWith(PREFIX) || text.length() == PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(text.substring(PREFIX.length()));
    }
}
