package cl.almejo.vsim.ui.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

/** The application icon ({@code icons/app/vsim.svg}) rendered at the sizes window systems ask for. */
public final class AppIcons {

    public static final List<Integer> SIZES = List.of(16, 32, 48, 128, 256);
    static final String RESOURCE = "icons/app/vsim.svg";

    private static final Logger LOGGER = LoggerFactory.getLogger(AppIcons.class);

    private static List<Image> images;

    private AppIcons() {
    }

    /**
     * One image per size in {@link #SIZES}, rendered once. The icon is cosmetic: if it cannot be rendered the
     * failure is logged and the list is empty, so no window fails to open because of it.
     */
    public static synchronized List<Image> images() {
        if (images == null) {
            images = load(RESOURCE);
        }
        return images;
    }

    static List<Image> load(String resource) {
        try {
            return SIZES.stream().map(size -> render(resource, size)).toList();
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot render the application icon {}", resource, exception);
            return List.of();
        }
    }

    private static Image render(String resource, int size) {
        FlatSVGIcon icon = new FlatSVGIcon(resource, size, size, AppIcons.class.getClassLoader());
        if (!icon.hasFound() || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
            throw new IllegalStateException("missing or empty " + resource);
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            // FlatSVGIcon grows with the UI scale; scale back so the image is exactly size x size.
            graphics.scale(size / (double) icon.getIconWidth(), size / (double) icon.getIconHeight());
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
