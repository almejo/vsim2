package cl.almejo.vsim.ui.capture;

import org.junit.jupiter.api.Test;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/** The image clipboard puts the picture on a clipboard as an image, nothing else. */
class ImageClipboardTest {

    @Test
    void theCopiedPictureIsOnTheClipboardAsAnImage() throws Exception {
        Clipboard clipboard = new Clipboard("test");
        BufferedImage image = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB);

        ImageClipboard.of(clipboard).copy(image);

        Transferable contents = clipboard.getContents(null);
        assertThat(contents.getTransferDataFlavors()).containsExactly(DataFlavor.imageFlavor);
        assertThat(contents.isDataFlavorSupported(DataFlavor.stringFlavor)).isFalse();
        assertThat(contents.getTransferData(DataFlavor.imageFlavor)).isSameAs(image);
    }
}
