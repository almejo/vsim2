package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;
import java.util.Objects;

/**
 * A PNG of the whole VSim window that shows one circuit, painted from the window's own components (never a capture
 * of the screen, so no other program is ever in it), scaled down to fit {@link #MAXIMUM_WIDTH} ×
 * {@link #MAXIMUM_HEIGHT}, never saved. {@code windowTitle} is the title bar's text (the bar itself belongs to the
 * system and is not painted); {@code dialogs} are the titles of the open dialogs painted on top.
 */
public record WindowPicture(DocumentId document, DocumentVersion version, String windowTitle, List<String> dialogs,
                            int width, int height, byte[] png) {

    public static final int MAXIMUM_WIDTH = 1920;
    public static final int MAXIMUM_HEIGHT = 1200;

    public WindowPicture {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(windowTitle, "windowTitle");
        dialogs = List.copyOf(dialogs);
        Objects.requireNonNull(png, "png");
        if (width < 1 || height < 1 || width > MAXIMUM_WIDTH || height > MAXIMUM_HEIGHT) {
            throw new IllegalArgumentException("a window picture is 1×1 to " + MAXIMUM_WIDTH + "×" + MAXIMUM_HEIGHT
                    + " pixels: " + width + "×" + height);
        }
    }
}
