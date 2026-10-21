package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.ui.i18n.Messages;
import org.jfree.svg.SVGGraphics2D;
import org.jfree.svg.SVGHints;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

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
        writePng(target, pngPixels(image, scale));
    }

    /**
     * The pixels a PNG export of {@code image} at {@code scale} holds, painted now (on the EDT: the image reads the
     * live circuit); {@link #writePng(Path, BufferedImage)} can then encode them on another thread.
     *
     * @throws IOException when the image is too large for {@link #MAXIMUM_PNG_PIXELS} or for the memory
     */
    public static BufferedImage pngPixels(CircuitImage image, int scale) throws IOException {
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
        return pixels;
    }

    /** Encodes {@code pixels} as a PNG file at {@code target}. */
    public static void writePng(Path target, BufferedImage pixels) throws IOException {
        if (!ImageIO.write(pixels, "png", target.toFile())) {
            throw new IOException("no PNG writer available");
        }
    }

    /**
     * The PNG bytes of {@code pixels}, encoded in memory only: {@code ImageIO.write} to a stream goes through a
     * temporary cache file in {@code java.io.tmpdir} when ImageIO's disk cache is on (the default), and the AI's
     * pictures are never saved (spec §6). ImageIO's global cache setting is left alone.
     */
    public static byte[] pngBytes(BufferedImage pixels) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("no PNG writer available");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(pixels);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static IOException tooLarge(int width, int height, Throwable cause) {
        return new IOException(Messages.format("export.tooLarge", String.valueOf(width), String.valueOf(height)),
                cause);
    }

    public static void writeSvg(Path target, CircuitImage image) throws IOException {
        writeSvg(target, svgDocument(image));
    }

    /** The SVG document of {@code image}, painted now (on the EDT: the image reads the live circuit). */
    public static String svgDocument(CircuitImage image) {
        SVGGraphics2D graphics = new SVGGraphics2D(image.width(1), image.height(1));
        graphics.setRenderingHint(SVGHints.KEY_DRAW_STRING_TYPE, SVGHints.VALUE_DRAW_STRING_TYPE_VECTOR);
        image.paint(graphics, 1);
        return graphics.getSVGDocument();
    }

    public static void writeSvg(Path target, String svgDocument) throws IOException {
        Files.writeString(target, svgDocument, StandardCharsets.UTF_8);
    }
}
