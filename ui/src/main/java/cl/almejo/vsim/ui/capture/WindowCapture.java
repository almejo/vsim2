package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Paints what the user sees of an editor view into an image (EDT only), at the screen's scale so it is as sharp as on
 * a HiDPI screen: the whole window with its open dialogs ({@link CaptureKind#WINDOW}) or every canvas pane of the
 * view as seen, with its values, glows, selection and overlays ({@link CaptureKind#VISIBLE_CIRCUIT}). Painted by VSim
 * from its own components, never grabbed from the screen, and without the system's title bar.
 */
public final class WindowCapture {

    /** No size limit: a window never outgrows the screens it is on. */
    private static final int UNLIMITED = Integer.MAX_VALUE;

    private WindowCapture() {
    }

    /**
     * {@code kind} of {@code view} at {@code scale} (see {@link #screenScale}): the image is the painted area's size
     * in pixels times {@code scale}.
     *
     * @throws IllegalStateException when what is painted has no size yet (never laid out)
     */
    public static BufferedImage paint(EditorView view, CaptureKind kind, double scale) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(kind, "kind");
        if (!(scale > 0)) {
            throw new IllegalArgumentException("the scale must be positive: " + scale);
        }
        return switch (kind) {
            case WINDOW -> paintWindow(rootOf(view), dialogsOf(view), scale);
            case VISIBLE_CIRCUIT -> paintComponent(view.canvasPanes(), scale);
        };
    }

    /**
     * {@code root} with the dialogs among {@code windows} at their places, at {@code scale}; for a window known by its
     * root pane and dialogs (the AI's export of the window).
     *
     * @throws IllegalStateException when {@code root} has no size yet
     */
    public static BufferedImage paintWindow(JComponent root, List<Window> windows, double scale) {
        requireSize(root);
        return WindowLayers.of(root, windows).paint(scale, UNLIMITED, UNLIMITED);
    }

    /**
     * The scale the screen showing {@code component} paints at (2.0 on a 200 % screen), from its graphics
     * configuration's default transform; 1.0 when it is on no screen (headless, or not shown yet).
     */
    public static double screenScale(Component component) {
        GraphicsConfiguration configuration = component.getGraphicsConfiguration();
        if (configuration == null) {
            return 1.0;
        }
        double scale = configuration.getDefaultTransform().getScaleX();
        return scale > 0 ? scale : 1.0;
    }

    /** The root pane of the window showing {@code view} (menu bar included); the view itself when it is in none. */
    private static JComponent rootOf(EditorView view) {
        JRootPane root = SwingUtilities.getRootPane(view);
        return root == null ? view : root;
    }

    /** The windows the frame showing {@code view} owns and shows now (its dialogs); none without a frame. */
    private static List<Window> dialogsOf(EditorView view) {
        Window frame = SwingUtilities.getWindowAncestor(view);
        if (frame == null) {
            return List.of();
        }
        return Arrays.stream(frame.getOwnedWindows()).filter(Window::isShowing).toList();
    }

    private static BufferedImage paintComponent(JComponent component, double scale) {
        requireSize(component);
        BufferedImage result = new BufferedImage((int) Math.ceil(component.getWidth() * scale),
                (int) Math.ceil(component.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.scale(scale, scale);
            component.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void requireSize(JComponent component) {
        if (component.getWidth() < 1 || component.getHeight() < 1) {
            throw new IllegalStateException("nothing to paint: " + component.getClass().getSimpleName()
                    + " has no size yet");
        }
    }
}
