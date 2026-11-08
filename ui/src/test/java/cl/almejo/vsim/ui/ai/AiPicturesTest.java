package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static org.assertj.core.api.Assertions.assertThat;

class AiPicturesTest {

    @TempDir
    Path cacheFolder;

    @Test
    void thePngIsEncodedInMemoryAndNeverPassesThroughADiskCacheFile() throws Exception {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(4, 4, 20, 10);
        graphics.dispose();
        File previousFolder = ImageIO.getCacheDirectory();
        boolean previousUseCache = ImageIO.getUseCache();
        ImageIO.setUseCache(true);
        // A cache folder that vanishes: any encoding that goes through a disk cache file fails to create it.
        Path vanished = Files.createDirectory(cacheFolder.resolve("cache"));
        ImageIO.setCacheDirectory(vanished.toFile());
        Files.delete(vanished);
        byte[] bytes;
        try {
            bytes = AiPictures.png(image);
        } finally {
            ImageIO.setCacheDirectory(previousFolder);
            ImageIO.setUseCache(previousUseCache);
        }
        try (Stream<Path> files = Files.list(cacheFolder)) {
            assertThat(files).isEmpty();
        }
        assertThat(ImageIO.getUseCache()).isEqualTo(previousUseCache);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded.getWidth()).isEqualTo(64);
        assertThat(decoded.getHeight()).isEqualTo(32);
        assertThat(decoded.getRGB(10, 8)).isEqualTo(Color.MAGENTA.getRGB());
    }

    /** The window painter is shared with the screenshot; the AI's picture still never grows and stays capped. */
    @Test
    void theWindowPictureIsNeverEnlargedAndNeverPassesTheCap() throws Exception {
        assertThat(windowPictureSize(640, 400)).containsExactly(640, 400);
        assertThat(windowPictureSize(3840, 2400)).containsExactly(WindowPicture.MAXIMUM_WIDTH,
                WindowPicture.MAXIMUM_HEIGHT);
    }

    private static int[] windowPictureSize(int width, int height) throws Exception {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            return onEditor(() -> {
                EditorView view = new EditorView(document, EditorServices.of(PainterRegistry.standard(),
                        ColorScheme.LIGHT), NO_WINDOW);
                try {
                    JRootPane root = new JRootPane();
                    root.setContentPane(view);
                    root.setSize(width, height);
                    layOut(root);
                    BufferedImage image = AiPictures.window(new ShownWindow(view, root, "", List.of())).image();
                    return new int[] {image.getWidth(), image.getHeight()};
                } finally {
                    view.dispose();
                }
            });
        } finally {
            document.close();
        }
    }
}
