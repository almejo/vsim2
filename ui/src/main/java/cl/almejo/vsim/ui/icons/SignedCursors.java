package cl.almejo.vsim.ui.icons;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A drawn cursor in a «+» and a «−» version (the selection arrow with a badge, the zoom magnifier, the shape editor's
 * node crosshair): one painter given the sign, each cursor made the first time it is asked for (EDT only, like every
 * cursor change), the {@code fallback} where the platform has no custom cursors.
 */
public final class SignedCursors {

    private final Map<DrawnCursors.Sign, String> names;
    private final BiConsumer<Graphics2D, DrawnCursors.Sign> painter;
    private final int hotspot;
    private final Cursor fallback;
    private final Map<DrawnCursors.Sign, Cursor> made = new EnumMap<>(DrawnCursors.Sign.class);

    /**
     * The cursors named {@code names} (one per sign), drawn by {@code painter} in design pixels, with the hotspot at
     * {@code hotspot} design pixels on both axes.
     */
    public SignedCursors(Map<DrawnCursors.Sign, String> names, BiConsumer<Graphics2D, DrawnCursors.Sign> painter,
                         int hotspot, Cursor fallback) {
        this.names = Map.copyOf(names);
        this.painter = painter;
        this.hotspot = hotspot;
        this.fallback = fallback;
    }

    /** The cursor of {@code sign}, made once. */
    public Cursor cursor(DrawnCursors.Sign sign) {
        return made.computeIfAbsent(sign, wanted -> DrawnCursors.create(names.get(wanted),
                size -> image(size, wanted), DrawnCursors.scaledHotspot(hotspot), fallback));
    }

    /** The picture of {@code sign}, {@code size} pixels square. */
    public BufferedImage image(int size, DrawnCursors.Sign sign) {
        return DrawnCursors.picture(size, graphics -> painter.accept(graphics, sign));
    }
}
