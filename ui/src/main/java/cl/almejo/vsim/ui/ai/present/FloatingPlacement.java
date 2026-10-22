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
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the «Presentar» panel floats (spec §5): over the window's active pane, in the palette layer of its root pane,
 * at most {@link PresentationPanel#PREFERRED_WIDTH} wide and never taller than the pane, at the top right with a
 * {@link #MARGIN} until the user moves it. The user drags it by its header (the {@code dragHandles}); it stays inside
 * the active pane while dragged, and keeps to the corner it was left nearest to (at the same distance from that
 * corner's edges) when the pane resizes or the active pane changes. A double click on the header, or
 * {@link #moveTo(Corner)}, puts it in a corner again. The position lasts while the window lives. EDT only.
 */
final class FloatingPlacement {

    /** The gap between the panel and the pane's edges at its default spot and after «Mover a ▸». */
    static final int MARGIN = 12;
    /** The narrowest the panel gets in a narrow pane. */
    static final int MINIMUM_WIDTH = 200;

    /** A corner of the pane, with its «Mover a ▸» item. */
    enum Corner {
        TOP_RIGHT(true, false, "ai.present.moveTo.topRight"),
        TOP_LEFT(false, false, "ai.present.moveTo.topLeft"),
        BOTTOM_RIGHT(true, true, "ai.present.moveTo.bottomRight"),
        BOTTOM_LEFT(false, true, "ai.present.moveTo.bottomLeft");

        private final boolean right;
        private final boolean bottom;
        private final String messageKey;

        Corner(boolean right, boolean bottom, String messageKey) {
            this.right = right;
            this.bottom = bottom;
            this.messageKey = messageKey;
        }

        String messageKey() {
            return messageKey;
        }

        static Corner of(boolean right, boolean bottom) {
            for (Corner corner : values()) {
                if (corner.right == right && corner.bottom == bottom) {
                    return corner;
                }
            }
            throw new IllegalStateException();
        }
    }

    private final EditorView view;
    private final JComponent panel;
    private final List<JComponent> dragHandles;
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
            if (SwingUtilities.isLeftMouseButton(event)) {
                last = event.getLocationOnScreen();
            }
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

        @Override
        public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
                moveTo(Corner.TOP_RIGHT);
            }
        }
    };
    /** The corner the panel keeps to, and its distances from that corner's vertical and horizontal edges. */
    private Corner corner = Corner.TOP_RIGHT;
    private int horizontalDistance = MARGIN;
    private int verticalDistance = MARGIN;

    FloatingPlacement(EditorView view, JComponent panel, List<JComponent> dragHandles) {
        this.view = Objects.requireNonNull(view, "view");
        this.panel = Objects.requireNonNull(panel, "panel");
        this.dragHandles = List.copyOf(dragHandles);
        this.removeActivePaneListener = view.addActivePaneListener(this::place);
        view.addComponentListener(viewListener);
        view.canvasPanes().addComponentListener(viewListener);
        for (JComponent handle : this.dragHandles) {
            handle.addMouseListener(dragger);
            handle.addMouseMotionListener(dragger);
        }
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

    /** The user dragged the panel by {@code deltaX}, {@code deltaY}: it moves, kept inside the active pane. */
    void moveBy(int deltaX, int deltaY) {
        Optional<Rectangle> pane = paneInLayered();
        if (pane.isEmpty() || !(panel.getParent() instanceof JLayeredPane)) {
            return;
        }
        Rectangle area = pane.get();
        Dimension size = sizeFor(area);
        int x = clamp(panel.getX() + deltaX, area.x, area.x + area.width - size.width);
        int y = clamp(panel.getY() + deltaY, area.y, area.y + area.height - size.height);
        rememberNearestCorner(area, new Rectangle(x, y, size.width, size.height));
        place();
    }

    /** Puts the panel in {@code wanted} corner of the pane, {@link #MARGIN} from its edges. */
    void moveTo(Corner wanted) {
        corner = Objects.requireNonNull(wanted, "wanted");
        horizontalDistance = MARGIN;
        verticalDistance = MARGIN;
        place();
    }

    Corner corner() {
        return corner;
    }

    private void rememberNearestCorner(Rectangle area, Rectangle bounds) {
        boolean right = bounds.getCenterX() > area.getCenterX();
        boolean bottom = bounds.getCenterY() > area.getCenterY();
        corner = Corner.of(right, bottom);
        horizontalDistance = right ? area.x + area.width - (bounds.x + bounds.width) : bounds.x - area.x;
        verticalDistance = bottom ? area.y + area.height - (bounds.y + bounds.height) : bounds.y - area.y;
    }

    /** Puts the panel at its spot over the active pane, inside it, at the size the pane leaves it. */
    void place() {
        if (!(panel.getParent() instanceof JLayeredPane layered)) {
            return;
        }
        Optional<Rectangle> pane = paneInLayered();
        if (pane.isEmpty()) {
            return;
        }
        Rectangle area = pane.get();
        Dimension size = sizeFor(area);
        int x = corner.right ? area.x + area.width - horizontalDistance - size.width : area.x + horizontalDistance;
        int y = corner.bottom ? area.y + area.height - verticalDistance - size.height : area.y + verticalDistance;
        x = clamp(x, area.x, area.x + area.width - size.width);
        y = clamp(y, area.y, area.y + area.height - size.height);
        panel.setBounds(x, y, size.width, size.height);
        panel.validate();
        layered.repaint();
    }

    /** The preferred size, narrowed and shortened to the pane less the margins. */
    private Dimension sizeFor(Rectangle area) {
        Dimension preferred = panel.getPreferredSize();
        int width = Math.max(Math.min(MINIMUM_WIDTH, area.width), Math.min(preferred.width,
                area.width - 2 * MARGIN));
        int height = Math.max(0, Math.min(preferred.height, area.height - 2 * MARGIN));
        return new Dimension(width, height);
    }

    /** The active pane's bounds in the layered pane's pixels; empty before it is in the same window. */
    private Optional<Rectangle> paneInLayered() {
        if (!(panel.getParent() instanceof JLayeredPane layered)) {
            return Optional.empty();
        }
        CircuitCanvas pane = view.canvas();
        if (pane.getParent() == null || SwingUtilities.getRootPane(pane) != SwingUtilities.getRootPane(layered)) {
            return Optional.empty();
        }
        return Optional.of(SwingUtilities.convertRectangle(pane.getParent(), pane.getBounds(), layered));
    }

    /**
     * The part of {@code pane} the panel covers, in that pane's pixels; empty while the panel is hidden or does not
     * lie over it. The travel and the callout keep clear of it.
     */
    Optional<Rectangle> coveredIn(CircuitCanvas pane) {
        if (!isShown() || pane.getParent() == null || pane.getWidth() <= 0 || pane.getHeight() <= 0) {
            return Optional.empty();
        }
        Container parent = panel.getParent();
        if (SwingUtilities.getRootPane(pane) != SwingUtilities.getRootPane(parent)) {
            return Optional.empty();
        }
        Rectangle inPane = SwingUtilities.convertRectangle(parent, panel.getBounds(), pane);
        Rectangle covered = inPane.intersection(new Rectangle(0, 0, pane.getWidth(), pane.getHeight()));
        return covered.isEmpty() ? Optional.empty() : Optional.of(covered);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
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
        for (JComponent handle : dragHandles) {
            handle.removeMouseListener(dragger);
            handle.removeMouseMotionListener(dragger);
        }
    }
}
