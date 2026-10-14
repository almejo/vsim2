package cl.almejo.vsim.ui.export;

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

    private ImageExport() {
    }

    public static void writePng(Path target, CircuitImage image, int scale) throws IOException {
        BufferedImage pixels = new BufferedImage(image.width(scale), image.height(scale), BufferedImage.TYPE_INT_ARGB);
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

    public static void writeSvg(Path target, CircuitImage image) throws IOException {
        SVGGraphics2D graphics = new SVGGraphics2D(image.width(1), image.height(1));
        graphics.setRenderingHint(SVGHints.KEY_DRAW_STRING_TYPE, SVGHints.VALUE_DRAW_STRING_TYPE_VECTOR);
        image.paint(graphics, 1);
        Files.writeString(target, graphics.getSVGDocument(), StandardCharsets.UTF_8);
    }
}
