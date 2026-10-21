package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.panels.ColorSwatches;
import com.formdev.flatlaf.FlatLaf;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The colour of a tunnel entrada in the inspector (spec §5): the shared {@link ColorSwatches} with one swatch per
 * colour of the tunnel palette, each painted in the variant of the theme shown now and named by its tooltip.
 */
public final class TunnelColorSwatches {

    /** The name of each palette colour, in palette order (one per {@link Tunnels#COLOR_COUNT}). */
    private static final List<String> COLOR_NAME_KEYS = List.of("tunnel.color.red", "tunnel.color.cyan",
            "tunnel.color.amber", "tunnel.color.purple", "tunnel.color.green", "tunnel.color.blue",
            "tunnel.color.pink", "tunnel.color.brown");

    private TunnelColorSwatches() {
    }

    /** Swatches for the palette colours 0 .. {@link Tunnels#COLOR_COUNT} - 1, with {@code selectedColor} chosen. */
    public static ColorSwatches<Integer> create(int selectedColor) {
        List<Integer> colors = IntStream.range(0, Tunnels.COLOR_COUNT).boxed().toList();
        return new ColorSwatches<>(colors, selectedColor, color -> Messages.text(COLOR_NAME_KEYS.get(color)),
                color -> Optional.of(ColorSchemes.forTheme(FlatLaf.isLafDark()).tunnelColor(color)));
    }
}
