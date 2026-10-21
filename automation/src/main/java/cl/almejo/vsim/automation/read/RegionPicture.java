package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Objects;

/**
 * A PNG of a region of one circuit for the AI to check its own layout (spec §6): never shown to the user, never saved.
 * {@code region} is what it shows, in grid units; {@code asOnScreen} says whether it is the active pane as the user
 * sees it (theme, grid, guides, selection) or the export rendering.
 */
public record RegionPicture(DocumentId document, DocumentVersion version, Bounds region, boolean asOnScreen,
                            int width, int height, byte[] png) {

    public static final int MAXIMUM_WIDTH = 1600;
    public static final int MAXIMUM_HEIGHT = 1000;

    public RegionPicture {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(png, "png");
        if (width < 1 || height < 1 || width > MAXIMUM_WIDTH || height > MAXIMUM_HEIGHT) {
            throw new IllegalArgumentException("a region picture is 1×1 to " + MAXIMUM_WIDTH + "×" + MAXIMUM_HEIGHT
                    + " pixels: " + width + "×" + height);
        }
    }
}
