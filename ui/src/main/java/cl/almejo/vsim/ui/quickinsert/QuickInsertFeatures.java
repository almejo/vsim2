package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.frames.FrameHitTester;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The command palette of one editor view: a floating window with the tabs «Componentes» (quick insert) and
 * «Acciones». A double click on empty canvas or Insert opens it on «Componentes»; Ayuda ▸ «Buscar una acción…» and its
 * key ({@code help.findAction}, Ctrl+K) open it on «Acciones», and that key pressed again toggles it (to «Acciones»,
 * then closed). A component is placed where the double click was, or follows the pointer after Insert; an action
 * runs after the window has closed and the canvas has the focus back, so its own dialogs open over the editor. Both
 * say what happened in a notice at the top of the circuit area. Placement and undo go through the palette's paths.
 */
final class QuickInsertFeatures {

    /** Injectable window boundary: tests exercise the real content without showing a window. */
    @FunctionalInterface
    interface PopupOpener {
        Runnable open(CircuitCanvas canvas, Point point, QuickInsertPanel panel);
    }

    static final String FIND_ACTION_ID = "help.findAction";
    private static final String QUICK_INSERT_ID = "edit.quickInsert";
    private static final Duration NOTICE_DURATION = Duration.ofMillis(1800);
    private static final int FRAME_MILLISECONDS = 16;

    private final EditorView view;
    private final RecentActions recents;
    private final PopupOpener opener;
    private final Consumer<Runnable> later;
    private final Consumer<String> notices;
    private final Action findAction;
    private final Runnable removeShortcut;
    private final Runnable removeHelpItem;
    private final Runnable removePanes;
    /** A rebound «Buscar una acción…» shows at once in the empty-canvas hint of every pane. */
    private final Runnable repaintPanes;
    private QuickInsertPanel panel;
    private final Runnable refreshPopup = () -> {
        if (panel != null) {
            panel.refresh();
        }
    };
    private Runnable removeRefresher = () -> { };
    private CircuitCanvas popupCanvas;
    private Runnable closePopup = () -> { };
    private boolean disposed;

    /** The real thing: a floating window, the Swing event queue and notices over the circuit area. */
    QuickInsertFeatures(EditorView view, RecentActions recents) {
        this(view, recents, QuickInsertFeatures::showWindow, SwingUtilities::invokeLater,
                text -> CanvasNotice.show(view.canvasPanes(), text, CanvasNotice.Position.TOP, NOTICE_DURATION,
                        FrameTicker.swing(FRAME_MILLISECONDS), view.services().preferences()));
    }

    /**
     * {@code recents} is shared by every window, {@code later} runs a chosen action after the palette has gone (the
     * event queue), {@code notices} shows a short text over the circuit area.
     */
    QuickInsertFeatures(EditorView view, RecentActions recents, PopupOpener opener, Consumer<Runnable> later,
                        Consumer<String> notices) {
        this.view = view;
        this.recents = recents;
        this.opener = opener;
        this.later = later;
        this.notices = notices;
        removeShortcut = view.registerShortcutAction(QUICK_INSERT_ID,
                Actions.of(Messages.text("action.quickInsert"), () -> openFromShortcut(PaletteTab.COMPONENTS)));
        findAction = Actions.of(Messages.text("action.findAction"), this::findAction);
        removeHelpItem = installHelpItem();
        repaintPanes = () -> view.panes().forEach(CircuitCanvas::repaint);
        removePanes = view.forEachPane(this::installOn);
        view.services().preferences().addListener(repaintPanes);
    }

    /**
     * Gives {@code canvas} the double click on empty space and the key of «Buscar una acción…» for its empty-canvas
     * hint. Returns what takes both back.
     */
    private Runnable installOn(CircuitCanvas canvas) {
        canvas.setFindActionKey(() -> new KeyBindings(view.services().preferences()).strokeFor(FIND_ACTION_ID)
                .map(KeyBindings::describe));
        Runnable removeDelegate = canvas.defaultTool() instanceof SelectTool selectTool
                ? selectTool.addDelegate((context, event) -> doubleClicked(canvas, context, event))
                : () -> {
                };
        return () -> {
            if (popupCanvas == canvas) {
                close();
            }
            removeDelegate.run();
            canvas.setFindActionKey(Optional::empty);
        };
    }

    private SelectToolDelegate.Press doubleClicked(CircuitCanvas canvas, ToolContext context,
                                                   CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY || event.clickCount() != 2 || event.shiftDown()
                || event.controlDown() || event.altDown() || !isEmpty(context, event.gridPosition())) {
            return SelectToolDelegate.Press.IGNORED;
        }
        open(canvas, new Point((int) event.screenPosition().getX(), (int) event.screenPosition().getY()),
                Optional.of(event), PaletteTab.COMPONENTS);
        return SelectToolDelegate.Press.HANDLED;
    }

    /** «Buscar una acción…» first in Ayuda, apart from the rest by a separator. */
    private Runnable installHelpItem() {
        Runnable remover = Menus.install(view, MenuId.HELP, 0, findAction, FIND_ACTION_ID);
        JMenu help = view.menu(MenuId.HELP);
        JSeparator separator = new JPopupMenu.Separator();
        help.add(separator, 1);
        return () -> {
            help.remove(separator);
            remover.run();
        };
    }

    /**
     * A non-modal window without decorations over the view's window, at {@code point} of the canvas but inside the
     * screen; it closes itself when it loses the focus (a click elsewhere).
     */
    private static Runnable showWindow(CircuitCanvas canvas, Point point, QuickInsertPanel panel) {
        Window owner = SwingUtilities.getWindowAncestor(canvas);
        JDialog dialog = new JDialog(owner, Dialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);
        dialog.setTitle(Messages.text("commandPalette.title")); // for screen readers: it shows no title bar
        dialog.getRootPane().setBorder(UIManager.getBorder("PopupMenu.border"));
        dialog.setContentPane(panel);
        dialog.pack();
        Point screen = new Point(point);
        SwingUtilities.convertPointToScreen(screen, canvas);
        Rectangle bounds = canvas.getGraphicsConfiguration() == null ? new Rectangle(screen, dialog.getSize())
                : canvas.getGraphicsConfiguration().getBounds();
        dialog.setLocation(Math.max(bounds.x, Math.min(screen.x, bounds.x + bounds.width - dialog.getWidth())),
                Math.max(bounds.y, Math.min(screen.y, bounds.y + bounds.height - dialog.getHeight())));
        WindowAdapter focusLoss = new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent event) {
                panel.cancel();
            }
        };
        dialog.addWindowFocusListener(focusLoss);
        dialog.setVisible(true);
        panel.focusSearch();
        return () -> {
            dialog.removeWindowFocusListener(focusLoss);
            dialog.dispose();
        };
    }

    /** The key of «Buscar una acción…»: opens on «Acciones», or toggles the palette that is open. */
    private void findAction() {
        if (panel != null) {
            panel.findAction();
        } else {
            openFromShortcut(PaletteTab.ACTIONS);
        }
    }

    private void openFromShortcut(PaletteTab tab) {
        CircuitCanvas canvas = view.canvas();
        Point pointer = GraphicsEnvironment.isHeadless() ? null : canvas.getMousePosition();
        Point position = pointer == null ? new Point(canvas.getWidth() / 2, canvas.getHeight() / 2) : pointer;
        open(canvas, position, Optional.empty(), tab);
    }

    private void open(CircuitCanvas canvas, Point position, Optional<CanvasMouseEvent> chosenPoint, PaletteTab tab) {
        if (disposed) {
            return;
        }
        close();
        popupCanvas = canvas;
        Opening opening = new Opening(canvas, position, chosenPoint);
        QuickInsertPanel opened = new QuickInsertPanel(content(), tab, opening);
        opening.panel = opened;
        panel = opened;
        removeRefresher = view.palette().addEntriesListener(refreshPopup);
        try {
            closePopup = opener.open(canvas, position, opened);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    private PaletteContent content() {
        List<ActionRow> actions = ActionRows.of(view, Set.of(findAction));
        KeyBindings bindings = new KeyBindings(view.services().preferences());
        return new PaletteContent(view.palette(), view.services().painters(), view.services().appearance(),
                actions, recents.rowsIn(actions), bindings.strokeFor(QUICK_INSERT_ID),
                bindings.strokeFor(FIND_ACTION_ID));
    }

    /** What one opened panel's choices do; a late cancel of a panel already replaced closes nothing. */
    private final class Opening implements QuickInsertPanel.Listener {

        private final CircuitCanvas canvas;
        private final Point position;
        private final Optional<CanvasMouseEvent> chosenPoint;
        private QuickInsertPanel panel;

        Opening(CircuitCanvas canvas, Point position, Optional<CanvasMouseEvent> chosenPoint) {
            this.canvas = canvas;
            this.position = position;
            this.chosenPoint = chosenPoint;
        }

        @Override
        public void place(PaletteEntry entry) {
            choose(canvas, position, chosenPoint, entry);
        }

        @Override
        public void run(ActionRow row) {
            runAction(row);
        }

        @Override
        public void cancel() {
            if (panel != null && panel == QuickInsertFeatures.this.panel) {
                close();
            }
        }
    }

    private void choose(CircuitCanvas canvas, Point position, Optional<CanvasMouseEvent> chosenPoint,
                        PaletteEntry entry) {
        Optional<PaletteEntry> available = view.palette().entries().stream()
                .filter(candidate -> candidate.id().equals(entry.id())).findFirst();
        if (available.isEmpty()) {
            panel.refresh();
            return;
        }
        close();
        view.palette().chooseEntry(available.get());
        if (!canvas.tool().placesFromPalette()) {
            canvas.requestFocusInWindow(); // nobody started placing it (no chooser for user components)
            return;
        }
        CanvasMouseEvent placement = chosenPoint.map(event -> new CanvasMouseEvent(event.gridPosition(),
                event.snapped(), event.screenPosition(), CanvasMouseEvent.PRIMARY, false, false, 1))
                .orElseGet(() -> new CanvasMouseEvent(canvas.viewport().screenToGrid(position.x, position.y),
                        canvas.viewport().snap(position.x, position.y), position, CanvasMouseEvent.PRIMARY,
                        false, false, 1));
        canvas.tool().mouseMoved(canvas, placement);
        chosenPoint.ifPresent(event -> {
            canvas.tool().mousePressed(canvas, placement);
            notices.accept(Messages.format("commandPalette.placedAtDoubleClick", available.get().name()));
        });
        canvas.requestFocusInWindow();
    }

    /** Closes the palette (the canvas gets the focus back), then runs {@code row} once, if it can still run. */
    private void runAction(ActionRow row) {
        if (!row.enabled()) {
            return;
        }
        recents.used(row.key());
        close();
        later.accept(() -> {
            if (disposed || !row.action().isEnabled()) {
                return;
            }
            row.action().actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, row.name()));
            notices.accept(Messages.format("commandPalette.done", withoutEllipsis(row.name())));
        });
    }

    private static String withoutEllipsis(String name) {
        return name.endsWith("…") ? name.substring(0, name.length() - 1) : name;
    }

    /**
     * Nothing there: excludes labels, bus value boxes, frame controls and pin tips, which lie outside ordinary
     * component hit bounds.
     */
    private static boolean isEmpty(ToolContext context, Point2D point) {
        CircuitDocument document = context.openDocument().document();
        if (context.hitTester().elementAt(document, point).isPresent()
                || FrameHitTester.hit(document, point, context.viewport().pixelsPerUnit()).isPresent()
                || new ConnectionPoints(context.openDocument().registry()).pinNear(document, point).isPresent()) {
            return false;
        }
        double documentX = point.getX() * GridPoint.PIXELS_PER_UNIT;
        double documentY = point.getY() * GridPoint.PIXELS_PER_UNIT;
        for (Component component : document.components()) {
            ComponentType type = context.openDocument().registry().typeOf(component);
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
        removeRefresher.run();
        removeRefresher = () -> { };
        Runnable closer = closePopup;
        closePopup = () -> { };
        closer.run();
        canvas.requestFocusInWindow();
    }

    void dispose() {
        if (!disposed) {
            disposed = true;
            close();
            view.services().preferences().removeListener(repaintPanes);
            removePanes.run();
            removeHelpItem.run();
            removeShortcut.run();
        }
    }
}
