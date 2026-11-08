package cl.almejo.vsim.ui.capture;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Where a capture is copied: the system clipboard in the editor, a fake in tests (which never touch the user's
 * clipboard). The picture goes as an image ({@link DataFlavor#imageFlavor}), which the platform offers to other
 * programs as a PNG.
 */
public interface ImageClipboard {

    /** Puts {@code image} on the clipboard, replacing what was there. */
    void copy(BufferedImage image);

    /** The system clipboard, looked up at each copy (there is none headless). */
    static ImageClipboard system() {
        return image -> of(Toolkit.getDefaultToolkit().getSystemClipboard()).copy(image);
    }

    /** {@code clipboard} as an image clipboard. */
    static ImageClipboard of(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        return image -> clipboard.setContents(new ImageSelection(image), null);
    }
}
