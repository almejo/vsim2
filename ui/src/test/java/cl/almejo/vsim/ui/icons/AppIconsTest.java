package cl.almejo.vsim.ui.icons;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppIconsTest {

    @Test
    void theApplicationIconComesInEverySizeWithAnOpaqueMiddleAndRoundCorners() {
        List<Image> images = AppIcons.images();
        assertThat(images).hasSize(AppIcons.SIZES.size());
        for (int index = 0; index < images.size(); index++) {
            int size = AppIcons.SIZES.get(index);
            BufferedImage image = (BufferedImage) images.get(index);
            assertThat(image.getWidth()).isEqualTo(size);
            assertThat(image.getHeight()).isEqualTo(size);
            assertThat(image.getRGB(size / 2, size / 2) >>> 24).as("middle at " + size).isEqualTo(255);
            assertThat(image.getRGB(0, 0) >>> 24).as("corner at " + size).isZero();
        }
        assertThat(AppIcons.images()).isSameAs(images);
    }
}
