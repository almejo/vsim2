package cl.almejo.vsim.ui.panels;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * What every dialog of VSim shares (I5, I6, I7, V2): the action button on the left and «Cancelar» (or «Cerrar») on
 * the right, the action as the default button, Escape pressing the cancel button, the standard insets, and a window
 * never larger than the usable screen area, its content scrolling when it is cut.
 */
public final class DialogButtons {

    /** Unscaled gaps of the button row, at 100 %. */
    private static final int GAP = 6;

    private DialogButtons() {
    }

    /** The button row: {@code action} first, {@code cancel} last, right-aligned. */
    public static JPanel row(JButton action, JButton cancel) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(GAP), UIScale.scale(GAP)));
        row.add(action);
        row.add(cancel);
        return row;
    }

    /** Makes {@code action} the default button of {@code root} and Escape, anywhere in the window, press {@code cancel}. */
    public static void install(JRootPane root, JButton action, JButton cancel) {
        root.setDefaultButton(action);
        root.registerKeyboardAction(event -> cancel.doClick(0), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Puts {@code content} (scrolling when cut) over the button row in {@code window}, with the default button and
     * Escape, and sizes it with {@code pack()} and {@link #fitToScreen}. The caller places and shows it.
     */
    public static void assemble(RootPaneContainer window, JComponent content, JButton action, JButton cancel) {
        window.getContentPane().setLayout(new BorderLayout());
        window.getContentPane().add(scrolling(content), BorderLayout.CENTER);
        window.getContentPane().add(row(action, cancel), BorderLayout.SOUTH);
        install(window.getRootPane(), action, cancel);
        if (window instanceof Window awtWindow) {
            awtWindow.pack();
            fitToScreen(awtWindow);
        }
    }

    /** {@code content} in a borderless scroll pane: scrollbars appear only when the window is made smaller than it. */
    public static JScrollPane scrolling(JComponent content) {
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** After {@code pack()}: shrinks {@code window} to the usable area of the screen it is on, and keeps it inside. */
    public static void fitToScreen(Window window) {
        GraphicsConfiguration configuration = window.getGraphicsConfiguration();
        if (configuration == null) {
            return;
        }
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        Rectangle usable = usable(configuration.getBounds(), screenInsets);
        window.setSize(fitted(window.getSize(), usable));
        window.setBounds(placed(window.getBounds(), usable)); // it was centred for its size before the cut
    }

    /** {@code bounds} moved, not resized, to lie inside {@code usable} (as far as it fits there). */
    static Rectangle placed(Rectangle bounds, Rectangle usable) {
        int x = Math.max(usable.x, Math.min(bounds.x, usable.x + usable.width - bounds.width));
        int y = Math.max(usable.y, Math.min(bounds.y, usable.y + usable.height - bounds.height));
        return new Rectangle(x, y, bounds.width, bounds.height);
    }

    /** The screen {@code bounds} minus the desktop's bars ({@code screenInsets}). */
    static Rectangle usable(Rectangle bounds, Insets screenInsets) {
        return new Rectangle(bounds.x + screenInsets.left, bounds.y + screenInsets.top,
                bounds.width - screenInsets.left - screenInsets.right,
                bounds.height - screenInsets.top - screenInsets.bottom);
    }

    /** {@code size} cut to fit inside {@code usable}. */
    static Dimension fitted(Dimension size, Rectangle usable) {
        return new Dimension(Math.min(size.width, usable.width), Math.min(size.height, usable.height));
    }
}
