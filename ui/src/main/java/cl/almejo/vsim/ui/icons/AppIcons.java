package cl.almejo.vsim.ui.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

/** The application icon ({@code icons/app/vsim.svg}) rendered at the sizes window systems ask for. */
public final class AppIcons {

    public static final List<Integer> SIZES = List.of(16, 32, 48, 128, 256);
    static final String RESOURCE = "icons/app/vsim.svg";

    private static List<Image> images;

    private AppIcons() {
    }

    /** One image per size in {@link #SIZES}, rendered once. */
    public static synchronized List<Image> images() {
        if (images == null) {
            images = SIZES.stream().map(AppIcons::render).toList();
        }
        return images;
    }

    private static Image render(int size) {
        FlatSVGIcon icon = new FlatSVGIcon(RESOURCE, size, size, AppIcons.class.getClassLoader());
        if (!icon.hasFound()) {
            throw new IllegalStateException("missing " + RESOURCE);
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
