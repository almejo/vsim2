package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PalettePanel.StarState;
import cl.almejo.vsim.ui.panels.PalettePanel.UserComponent;

import javax.swing.AbstractAction;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.Set;

/** Intercepts the star before BasicTreeUI selects a row or starts a drag. */
final class FavoritesTree extends JTree {
    private static final int STAR_COLUMN_WIDTH = 24;
    private Optional<Point> hoverPoint = Optional.empty();
    private boolean starGesture;
    private final PalettePanel panel;

    FavoritesTree(PalettePanel panel) {
        super(new DefaultMutableTreeNode());
        this.panel = panel;
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hoverAt(Optional.of(event.getPoint()));
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                hoverAt(Optional.empty());
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent event) {
                hoverAt(Optional.empty());
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK),
                "palette.contextMenu");
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "palette.contextMenu");
        getActionMap().put("palette.contextMenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                TreePath path = getSelectionPath();
                if (path != null && isShowing()) {
                    Rectangle bounds = getPathBounds(path);
                    panel.popupFor(path).ifPresent(menu -> menu.show(FavoritesTree.this, bounds.x,
                            bounds.y + bounds.height));
                }
            }
        });
    }

    /**
     * The row under the pointer, worked out from where the pointer is rather than kept as a path: a section that
     * changes under it (a click on the star) leaves the star of the row now under the pointer showing.
     */
    Optional<TreePath> hoverPath() {
        return hoverPoint.map(point -> pathAtRow(point.x, point.y));
    }

    private void hoverAt(Optional<Point> point) {
        int previous = hoverPath().map(this::getRowForPath).orElse(-1);
        hoverPoint = point;
        int next = hoverPath().map(this::getRowForPath).orElse(-1);
        if (previous != next) {
            repaintRow(previous);
            repaintRow(next);
        }
    }

    /**
     * The star {@code row} shows: filled for a favourite, in its own category as well as in «Favoritos» (no hover
     * needed); an outline under the pointer while no popup is open; none on headers and hints.
     */
    StarState starState(int row, Optional<TreePath> hovered) {
        TreePath path = getPathForRow(row);
        Optional<String> typeId = favoriteType(path);
        if (typeId.isEmpty()) {
            return StarState.NONE;
        }
        if (panel.favoriteIds().contains(typeId.get())) {
            return StarState.FILLED;
        }
        return hovered.filter(path::equals).isPresent()
                && MenuSelectionManager.defaultManager().getSelectedPath().length == 0
                ? StarState.OUTLINE : StarState.NONE;
    }

    /** Repaints the rows of {@code typeIds} (their stars filled or emptied). */
    void repaintRowsOf(Set<String> typeIds) {
        for (int row = 0; row < getRowCount(); row++) {
            if (favoriteType(getPathForRow(row)).filter(typeIds::contains).isPresent()) {
                repaintRow(row);
            }
        }
    }

    private void repaintRow(int row) {
        Rectangle bounds = row < 0 ? null : getRowBounds(row);
        if (bounds != null) {
            repaint(0, bounds.y, getWidth(), bounds.height);
        }
    }

    TreePath pathAtRow(int x, int y) {
        TreePath path = getClosestPathForLocation(x, y);
        Rectangle bounds = path == null ? null : getPathBounds(path);
        return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? path : null;
    }

    private Rectangle starBounds(TreePath path) {
        Rectangle row = getPathBounds(path);
        Rectangle visible = getVisibleRect();
        return new Rectangle(visible.x + visible.width - STAR_COLUMN_WIDTH, row.y,
                STAR_COLUMN_WIDTH, row.height);
    }

    private Optional<String> favoriteType(TreePath path) {
        if (path == null || panel.favoriteToggle().isEmpty()) {
            return Optional.empty();
        }
        Object item = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return item instanceof ComponentType || item instanceof UserComponent
                ? Optional.of(PalettePanel.typeIdOf(item)) : Optional.empty();
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_PRESSED) {
            starGesture = false;
            TreePath path = pathAtRow(event.getX(), event.getY());
            Optional<String> typeId = favoriteType(path);
            if (SwingUtilities.isLeftMouseButton(event) && typeId.isPresent()
                    && starBounds(path).contains(event.getPoint())) {
                starGesture = true;
                panel.toggleFavorite(typeId.get());
                event.consume();
                return;
            }
        } else if (starGesture && (event.getID() == MouseEvent.MOUSE_RELEASED
                || event.getID() == MouseEvent.MOUSE_CLICKED)) {
            event.consume();
            return;
        }
        super.processMouseEvent(event);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent event) {
        if (starGesture && event.getID() == MouseEvent.MOUSE_DRAGGED) {
            event.consume();
            return;
        }
        super.processMouseMotionEvent(event);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        TreePath path = pathAtRow(event.getX(), event.getY());
        Optional<String> typeId = favoriteType(path);
        if (typeId.isPresent() && starBounds(path).contains(event.getPoint())) {
            return Messages.text(panel.favoriteIds().contains(typeId.get())
                    ? "palette.favorite.remove" : "palette.favorite.add");
        }
        return super.getToolTipText(event);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Rectangle clip = graphics.getClipBounds() != null ? graphics.getClipBounds() : getVisibleRect();
        int firstRow = Math.max(0, getClosestRowForLocation(clip.x, clip.y));
        int lastRow = getClosestRowForLocation(clip.x, clip.y + clip.height);
        Optional<TreePath> hovered = hoverPath();
        for (int row = firstRow; row <= lastRow && row < getRowCount(); row++) {
            StarState state = starState(row, hovered);
            Rectangle bounds = state == StarState.NONE ? null : starBounds(getPathForRow(row));
            if (bounds == null || !bounds.intersects(getVisibleRect())) {
                continue;
            }
            boolean selected = isRowSelected(row);
            FavoriteStar star = new FavoriteStar(state == StarState.FILLED, selected);
            if (!selected) {
                // Hides the tail of a long name; a selected row keeps its highlight whole.
                graphics.setColor(getBackground());
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            star.paintIcon(this, graphics, bounds.x + (bounds.width - star.getIconWidth()) / 2,
                    bounds.y + (bounds.height - star.getIconHeight()) / 2);
        }
    }
}
