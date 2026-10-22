package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * Where the «Presentar» panel floats (spec §5): over the window's canvas, in the palette layer of its root pane, at
 * the top right of the active pane; the user drags it by {@code dragHandle} and it keeps that offset (also when the
 * active pane changes or the window is resized), always inside the window. EDT only.
 */
final class FloatingPlacement {

    /** The gap between the panel and the pane's top and right edges. */
    static final int MARGIN = 12;

    private final EditorView view;
    private final JComponent panel;
    private final JComponent dragHandle;
    private final Runnable removeActivePaneListener;
    private final ComponentListener viewListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
            place();
        }

        @Override
        public void componentMoved(ComponentEvent event) {
            place();
        }
    };
    private final MouseAdapter dragger = new MouseAdapter() {
        private Point last;

        @Override
        public void mousePressed(MouseEvent event) {
            last = event.getLocationOnScreen();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (last != null) {
                Point now = event.getLocationOnScreen();
                moveBy(now.x - last.x, now.y - last.y);
                last = now;
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            last = null;
        }
    };
    /** How far the user moved the panel from its top-right spot. */
    private final Point userOffset = new Point();

    FloatingPlacement(EditorView view, JComponent panel, JComponent dragHandle) {
        this.view = Objects.requireNonNull(view, "view");
        this.panel = Objects.requireNonNull(panel, "panel");
        this.dragHandle = Objects.requireNonNull(dragHandle, "dragHandle");
        this.removeActivePaneListener = view.addActivePaneListener(this::place);
        view.addComponentListener(viewListener);
        view.canvasPanes().addComponentListener(viewListener);
        dragHandle.addMouseListener(dragger);
        dragHandle.addMouseMotionListener(dragger);
    }

    /** Floats the panel over the window, when the view is in one yet (else the next call does). */
    void show() {
        JRootPane root = SwingUtilities.getRootPane(view);
        if (root == null) {
            return;
        }
        JLayeredPane layered = root.getLayeredPane();
        if (panel.getParent() != layered) {
            detach();
            layered.add(panel, JLayeredPane.PALETTE_LAYER);
        }
        panel.setVisible(true);
        place();
    }

    void hide() {
        detach();
    }

    boolean isShown() {
        return panel.getParent() != null && panel.isVisible();
    }

    /** The user dragged the panel by {@code dx}, {@code dy}. */
    void moveBy(int dx, int dy) {
        userOffset.translate(dx, dy);
        place();
    }

    /** Puts the panel at its spot over the active pane, at its preferred size, inside the window. */
    void place() {
        if (!(panel.getParent() instanceof JLayeredPane layered)) {
            return;
        }
        Dimension size = panel.getPreferredSize();
        CircuitCanvas pane = view.canvas();
        Point corner = SwingUtilities.convertPoint(pane, pane.getWidth() - size.width - MARGIN, MARGIN, layered);
        int x = clamp(corner.x + userOffset.x, layered.getWidth() - size.width);
        int y = clamp(corner.y + userOffset.y, layered.getHeight() - size.height);
        panel.setBounds(x, y, size.width, size.height);
        panel.validate();
        layered.repaint();
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(value, maximum));
    }

    private void detach() {
        Container parent = panel.getParent();
        if (parent != null) {
            parent.remove(panel);
            parent.repaint();
        }
    }

    void dispose() {
        detach();
        removeActivePaneListener.run();
        view.removeComponentListener(viewListener);
        view.canvasPanes().removeComponentListener(viewListener);
        dragHandle.removeMouseListener(dragger);
        dragHandle.removeMouseMotionListener(dragger);
    }
}
