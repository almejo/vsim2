package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitBounds;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paints the AI's pictures (spec §6, IA.1 Task 2) into off-screen images on the EDT: a region of the circuit as the
 * export paints it or as the active pane shows it, and a whole window painted from its own components (never a
 * capture of the screen, so no other program can be in it). Nothing is shown or saved; {@link #png} encodes off the
 * EDT.
 */
final class AiPictures {

    /** Small regions are painted bigger than the canvas at 100 % so the AI can read them, up to this factor. */
    static final double MAXIMUM_EXPORT_SCALE = 2.0;

    private AiPictures() {
    }

    /** A painted picture waiting to be encoded, with what it shows. */
    record Painted(BufferedImage image, Bounds region, String title, List<String> dialogs) {
    }

    /**
     * {@code region} (empty: the whole circuit with the export's margin) as the PNG export paints it, in
     * {@code scheme}.
     */
    static Painted exportRegion(OpenDocument document, Optional<Bounds> region, ColorScheme scheme) {
        Bounds painted = region.or(() -> CircuitBounds.of(document.document(), document.registry())
                        .map(bounds -> new Bounds(bounds.x() - CircuitImage.MARGIN, bounds.y() - CircuitImage.MARGIN,
                                bounds.width() + 2 * CircuitImage.MARGIN, bounds.height() + 2 * CircuitImage.MARGIN)))
                .orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE,
                        "The circuit is empty: there is nothing to paint; give a region", Map.of()));
        CircuitImage image = CircuitImage.ofRegion(document.document(), document.netlist(), document.netlistToken(),
                document.latestSnapshot(), document.registry(), PainterRegistry.standard(), scheme, painted);
        double gridWidth = Math.max(1, painted.width()) * (double) GridPoint.PIXELS_PER_UNIT;
        double gridHeight = Math.max(1, painted.height()) * (double) GridPoint.PIXELS_PER_UNIT;
        double scale = Math.min(MAXIMUM_EXPORT_SCALE, Math.min(RegionPicture.MAXIMUM_WIDTH / gridWidth,
                RegionPicture.MAXIMUM_HEIGHT / gridHeight));
        BufferedImage result = new BufferedImage(
                fit(gridWidth * scale, RegionPicture.MAXIMUM_WIDTH),
                fit(gridHeight * scale, RegionPicture.MAXIMUM_HEIGHT), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            image.paint(graphics, scale);
        } finally {
            graphics.dispose();
        }
        return new Painted(result, painted, "", List.of());
    }

    /**
     * {@code canvas} as the user sees it now (theme, grid, guides, selection), cut to {@code region} when given, never
     * bigger than the canvas at its zoom except to fit the size limit.
     */
    static Painted onScreenRegion(CircuitCanvas canvas, Optional<Bounds> region) {
        Rectangle pane = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        if (pane.isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE,
                    "The circuit's pane is not on screen; ask for the export picture instead", Map.of());
        }
        Rectangle crop = region.map(bounds -> screenRectangle(canvas, bounds).intersection(pane)).orElse(pane);
        if (crop.isEmpty()) {
            Bounds visible = gridBounds(canvas, pane);
            throw new AutomationException(AutomationError.INVALID_REFERENCE,
                    "That region is not visible in the active pane", Map.of("visibleRegion", Map.of(
                            "x", visible.x(), "y", visible.y(), "width", visible.width(),
                            "height", visible.height())));
        }
        double scale = Math.min(1.0, Math.min(RegionPicture.MAXIMUM_WIDTH / (double) crop.width,
                RegionPicture.MAXIMUM_HEIGHT / (double) crop.height));
        BufferedImage result = new BufferedImage(fit(crop.width * scale, RegionPicture.MAXIMUM_WIDTH),
                fit(crop.height * scale, RegionPicture.MAXIMUM_HEIGHT), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.scale(scale, scale);
            graphics.translate(-crop.x, -crop.y);
            graphics.setClip(crop);
            canvas.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return new Painted(result, gridBounds(canvas, crop), "", List.of());
    }

    /**
     * The whole of {@code window}: its root pane (menus, toolbar, palette, panes, inspector, status bar) and, on top,
     * the dialogs it has open where they are, scaled down to fit {@link WindowPicture#MAXIMUM_WIDTH} ×
     * {@link WindowPicture#MAXIMUM_HEIGHT}.
     */
    static Painted window(ShownWindow window) {
        JComponent root = window.root();
        if (root.getWidth() < 1 || root.getHeight() < 1) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "The circuit's window has no size yet",
                    Map.of());
        }
        Rectangle extent = new Rectangle(0, 0, root.getWidth(), root.getHeight());
        List<DialogPlace> dialogs = new ArrayList<>();
        for (Window dialog : window.dialogs()) {
            if (dialog instanceof RootPaneContainer container && dialog.isShowing() && root.isShowing()) {
                JComponent dialogRoot = container.getRootPane();
                Point dialogCorner = dialogRoot.getLocationOnScreen();
                Point rootCorner = root.getLocationOnScreen();
                Rectangle place = new Rectangle(dialogCorner.x - rootCorner.x, dialogCorner.y - rootCorner.y,
                        dialogRoot.getWidth(), dialogRoot.getHeight());
                dialogs.add(new DialogPlace(dialogRoot, place, dialog instanceof Dialog titled ? titled.getTitle()
                        : ""));
                extent = extent.union(place);
            }
        }
        double scale = Math.min(1.0, Math.min(WindowPicture.MAXIMUM_WIDTH / (double) extent.width,
                WindowPicture.MAXIMUM_HEIGHT / (double) extent.height));
        BufferedImage result = new BufferedImage(fit(extent.width * scale, WindowPicture.MAXIMUM_WIDTH),
                fit(extent.height * scale, WindowPicture.MAXIMUM_HEIGHT), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.scale(scale, scale);
            graphics.translate(-extent.x, -extent.y);
            paintAt(graphics, root, new Rectangle(0, 0, root.getWidth(), root.getHeight()));
            for (DialogPlace dialog : dialogs) {
                paintAt(graphics, dialog.root(), dialog.place());
            }
        } finally {
            graphics.dispose();
        }
        return new Painted(result, new Bounds(0, 0, 0, 0), window.title(),
                dialogs.stream().map(DialogPlace::title).toList());
    }

    private record DialogPlace(JComponent root, Rectangle place, String title) {
    }

    private static void paintAt(Graphics2D graphics, JComponent component, Rectangle place) {
        Graphics2D child = (Graphics2D) graphics.create(place.x, place.y, place.width, place.height);
        try {
            component.printAll(child);
        } finally {
            child.dispose();
        }
    }

    /** The PNG bytes of {@code image}; called off the EDT. */
    static byte[] png(BufferedImage image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    private static int fit(double pixels, int maximum) {
        return Math.max(1, Math.min(maximum, (int) Math.ceil(pixels)));
    }

    private static Rectangle screenRectangle(CircuitCanvas canvas, Bounds bounds) {
        Point2D corner = canvas.viewport().gridToScreen(bounds.x(), bounds.y());
        Point2D end = canvas.viewport().gridToScreen(bounds.maximumX(), bounds.maximumY());
        int left = (int) Math.floor(corner.getX());
        int top = (int) Math.floor(corner.getY());
        return new Rectangle(left, top, Math.max(1, (int) Math.ceil(end.getX()) - left),
                Math.max(1, (int) Math.ceil(end.getY()) - top));
    }

    /** The grid area {@code rectangle} of {@code canvas} shows, rounded outwards. */
    private static Bounds gridBounds(CircuitCanvas canvas, Rectangle rectangle) {
        Point2D corner = canvas.viewport().screenToGrid(rectangle.x, rectangle.y);
        Point2D end = canvas.viewport().screenToGrid(rectangle.getMaxX(), rectangle.getMaxY());
        int left = (int) Math.floor(corner.getX());
        int top = (int) Math.floor(corner.getY());
        return new Bounds(left, top, (int) Math.ceil(end.getX()) - left, (int) Math.ceil(end.getY()) - top);
    }
}
