package cl.almejo.vsim.ui.favorites;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.frames.FrameHitTester;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.Optional;

/** Installs favourites and quick insertion on every pane, preserving the existing placement and undo paths. */
final class QuickInsertFeatures {

    /** Injectable popup boundary: tests exercise the real content without showing a window. */
    @FunctionalInterface
    interface PopupOpener {
        Runnable open(CircuitCanvas canvas, Point point, QuickInsertPanel panel);
    }

    private final EditorView view;
    private final PopupOpener opener;
    private final Runnable removeShortcut;
    private final Runnable removeDelegates;
    private QuickInsertPanel panel;
    private final Runnable refreshPopup = () -> {
        if (panel != null) {
            panel.refresh();
        }
    };
    private CircuitCanvas popupCanvas;
    private Runnable closePopup = () -> { };
    private boolean disposed;

    QuickInsertFeatures(EditorView view) {
        this(view, QuickInsertFeatures::showPopup);
    }

    QuickInsertFeatures(EditorView view, PopupOpener opener) {
        this.view = view;
        this.opener = opener;
        view.palette().enableFavorites(view.services().preferences());
        removeShortcut = view.registerShortcutAction("edit.quickInsert",
                new AbstractAction(Messages.text("action.quickInsert")) {
                    @Override public void actionPerformed(ActionEvent event) { openFromShortcut(); }
                });
        removeDelegates = view.forEachPane(canvas -> {
            Runnable remove = ((SelectTool) canvas.defaultTool()).addDelegate((context, event) -> {
                if (event.button() != CanvasMouseEvent.PRIMARY || event.clickCount() != 2 || event.shiftDown()
                        || event.controlDown() || event.altDown() || !isEmpty(context, event.gridPosition())) {
                    return SelectToolDelegate.Press.IGNORED;
                }
                open(canvas, new Point((int) event.screenPosition().getX(), (int) event.screenPosition().getY()),
                        Optional.of(event));
                return SelectToolDelegate.Press.HANDLED;
            });
            return () -> {
                if (popupCanvas == canvas) {
                    close();
                }
                remove.run();
            };
        });
    }

    private static Runnable showPopup(CircuitCanvas canvas, Point point, QuickInsertPanel panel) {
        JPopupMenu popup = new JPopupMenu();
        popup.setFocusable(true);
        popup.add(panel);
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { panel.cancel(); }
            @Override public void popupMenuCanceled(PopupMenuEvent event) { panel.cancel(); }
        });
        popup.show(canvas, point.x, point.y);
        SwingUtilities.invokeLater(() -> {
            if (popup.isVisible()) {
                panel.focusSearch();
            }
        });
        return () -> popup.setVisible(false);
    }

    private void openFromShortcut() {
        CircuitCanvas canvas = view.canvas();
        Point pointer = GraphicsEnvironment.isHeadless() ? null : canvas.getMousePosition();
        Point position = pointer == null ? new Point(canvas.getWidth() / 2, canvas.getHeight() / 2) : pointer;
        open(canvas, position, Optional.empty());
    }

    private void open(CircuitCanvas canvas, Point position, Optional<CanvasMouseEvent> chosenPoint) {
        if (disposed) {
            return;
        }
        close();
        popupCanvas = canvas;
        panel = new QuickInsertPanel(view.palette(), view.services().preferences(), view.services().painters(),
                view.services().appearance(), entry -> choose(canvas, position, chosenPoint, entry), this::close);
        view.services().preferences().addListener(refreshPopup);
        view.openDocument().addChangeListener(refreshPopup);
        try {
            closePopup = opener.open(canvas, position, panel);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    private void choose(CircuitCanvas canvas, Point position, Optional<CanvasMouseEvent> chosenPoint,
                        PaletteEntry entry) {
        Optional<PaletteEntry> available = view.palette().entries().stream()
                .filter(candidate -> candidate.type().id().equals(entry.type().id())).findFirst();
        if (available.isEmpty()) {
            panel.refresh();
            return;
        }
        close();
        view.palette().chooseEntry(available.get());
        CanvasMouseEvent placement = chosenPoint.map(event -> new CanvasMouseEvent(event.gridPosition(),
                event.snapped(), event.screenPosition(), CanvasMouseEvent.PRIMARY, false, false, 1))
                .orElseGet(() -> new CanvasMouseEvent(canvas.viewport().screenToGrid(position.x, position.y),
                        canvas.viewport().snap(position.x, position.y), position, CanvasMouseEvent.PRIMARY,
                        false, false, 1));
        canvas.tool().mouseMoved(canvas, placement);
        chosenPoint.ifPresent(event -> canvas.tool().mousePressed(canvas, placement));
        canvas.requestFocusInWindow();
    }

    /** Excludes labels, bus value boxes and frame controls, which lie outside ordinary component hit bounds. */
    private static boolean isEmpty(ToolContext context, Point2D point) {
        if (context.hitTester().elementAt(context.openDocument().document(), point).isPresent()
                || FrameHitTester.hit(context.openDocument().document(), point,
                context.viewport().pixelsPerUnit()).isPresent()) {
            return false;
        }
        double documentX = point.getX() * GridPoint.PIXELS_PER_UNIT;
        double documentY = point.getY() * GridPoint.PIXELS_PER_UNIT;
        for (Component component : context.openDocument().document().components()) {
            cl.almejo.vsim.core.component.ComponentType type = context.openDocument().registry().typeOf(component);
            if (LabelLayout.componentLabel(type, component)
                    .map(label -> label.bounds().contains(documentX, documentY)).orElse(false)
                    || InterfacePinPainter.valueBox(type, component)
                    .map(box -> box.contains(documentX, documentY)).orElse(false)) {
                return false;
            }
        }
        return true;
    }

    private void close() {
        if (panel == null) {
            return;
        }
        CircuitCanvas canvas = popupCanvas;
        panel = null;
        popupCanvas = null;
        view.services().preferences().removeListener(refreshPopup);
        view.openDocument().removeChangeListener(refreshPopup);
        Runnable closer = closePopup;
        closePopup = () -> { };
        closer.run();
        canvas.requestFocusInWindow();
    }

    void dispose() {
        if (!disposed) {
            disposed = true;
            close();
            removeDelegates.run();
            removeShortcut.run();
            view.palette().disableFavorites();
        }
    }
}
