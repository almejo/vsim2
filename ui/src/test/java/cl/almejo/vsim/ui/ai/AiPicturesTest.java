package cl.almejo.vsim.ui.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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
}
