package cl.almejo.vsim.ui.capture;

import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A window as VSim paints it into a picture (EDT only): its root pane (menu bar, toolbar, palette, panes, inspector,
 * status bar) and, on top, the dialogs it has open at their places relative to it. Painted from the components
 * themselves, never grabbed from the screen, so no other program can be in it. Shared by the screenshot, Archivo ▸
 * Exportar ▸ «Imagen de la ventana…» and the AI's picture of the window (which caps its size).
 */
public record WindowLayers(JComponent root, List<PlacedDialog> dialogs) {

    /** An open dialog: its root pane, where it is relative to the window's root pane, and its title. */
    public record PlacedDialog(JComponent root, Rectangle place, String title) {

        public PlacedDialog {
            Objects.requireNonNull(root, "root");
            place = new Rectangle(place);
            title = title == null ? "" : title;
        }

        @Override
        public Rectangle place() {
            return new Rectangle(place);
        }
    }

    public WindowLayers {
        Objects.requireNonNull(root, "root");
        dialogs = List.copyOf(dialogs);
    }

    /**
     * {@code root} with those of {@code windows} that are dialogs on screen (VSim's own, painted from their root
     * panes) at the places they have now; with {@code root} off screen there are no places to measure, so no dialogs.
     */
    public static WindowLayers of(JComponent root, List<Window> windows) {
        List<PlacedDialog> dialogs = new ArrayList<>();
        for (Window window : windows) {
            if (window instanceof RootPaneContainer container && window.isShowing() && root.isShowing()) {
                JComponent dialogRoot = container.getRootPane();
                Point dialogCorner = dialogRoot.getLocationOnScreen();
                Point rootCorner = root.getLocationOnScreen();
                Rectangle place = new Rectangle(dialogCorner.x - rootCorner.x, dialogCorner.y - rootCorner.y,
                        dialogRoot.getWidth(), dialogRoot.getHeight());
                dialogs.add(new PlacedDialog(dialogRoot, place, window instanceof Dialog titled ? titled.getTitle()
                        : ""));
            }
        }
        return new WindowLayers(root, dialogs);
    }

    /** The area the picture covers, in the root pane's pixels: the root pane and every dialog, also standing out. */
    public Rectangle extent() {
        Rectangle extent = new Rectangle(0, 0, root.getWidth(), root.getHeight());
        for (PlacedDialog dialog : dialogs) {
            extent = extent.union(dialog.place());
        }
        return extent;
    }

    /**
     * The picture at {@code scale} (2 on a 200 % screen: as sharp as the screen shows it), never wider than
     * {@code maximumWidth} nor taller than {@code maximumHeight} pixels (what rounding up would add is cut).
     */
    public BufferedImage paint(double scale, int maximumWidth, int maximumHeight) {
        Rectangle extent = extent();
        BufferedImage result = new BufferedImage(fit(extent.width * scale, maximumWidth),
                fit(extent.height * scale, maximumHeight), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.scale(scale, scale);
            graphics.translate(-extent.x, -extent.y);
            paintAt(graphics, root, new Rectangle(0, 0, root.getWidth(), root.getHeight()));
            for (PlacedDialog dialog : dialogs) {
                paintAt(graphics, dialog.root(), dialog.place());
            }
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static void paintAt(Graphics2D graphics, JComponent component, Rectangle place) {
        Graphics2D child = (Graphics2D) graphics.create(place.x, place.y, place.width, place.height);
        try {
            component.printAll(child);
        } finally {
            child.dispose();
        }
    }

    private static int fit(double pixels, int maximum) {
        return Math.max(1, Math.min(maximum, (int) Math.ceil(pixels)));
    }
}
