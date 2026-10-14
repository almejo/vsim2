package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.ui.i18n.Messages;
import org.jfree.svg.SVGGraphics2D;
import org.jfree.svg.SVGHints;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a {@link CircuitImage} as a PNG (any scale) or an SVG (always 1x, text as vector shapes). */
public final class ImageExport {

    /**
     * A PNG with more pixels than this (4 bytes each in memory, so 400 MB) is refused before allocating it; a smaller
     * one that still does not fit in memory is reported the same way.
     */
    public static final long MAXIMUM_PNG_PIXELS = 100_000_000L;

    private ImageExport() {
    }

    /** @throws IOException also when the image is too large for {@link #MAXIMUM_PNG_PIXELS} or for the memory */
    public static void writePng(Path target, CircuitImage image, int scale) throws IOException {
        int width = image.width(scale);
        int height = image.height(scale);
        if ((long) width * height > MAXIMUM_PNG_PIXELS) {
            throw tooLarge(width, height, null);
        }
        BufferedImage pixels;
        try {
            pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        } catch (OutOfMemoryError error) {
            throw tooLarge(width, height, error);
        }
        Graphics2D graphics = pixels.createGraphics();
        try {
            image.paint(graphics, scale);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(pixels, "png", target.toFile())) {
            throw new IOException("no PNG writer available");
        }
    }

    private static IOException tooLarge(int width, int height, Throwable cause) {
        return new IOException(Messages.format("export.tooLarge", String.valueOf(width), String.valueOf(height)),
                cause);
    }

    public static void writeSvg(Path target, CircuitImage image) throws IOException {
        SVGGraphics2D graphics = new SVGGraphics2D(image.width(1), image.height(1));
        graphics.setRenderingHint(SVGHints.KEY_DRAW_STRING_TYPE, SVGHints.VALUE_DRAW_STRING_TYPE_VECTOR);
        image.paint(graphics, 1);
        Files.writeString(target, graphics.getSVGDocument(), StandardCharsets.UTF_8);
    }
}
