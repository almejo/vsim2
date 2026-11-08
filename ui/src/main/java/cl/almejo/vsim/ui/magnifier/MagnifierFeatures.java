package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuEntry;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.tools.EnterableInstances;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * «Mirar adentro» of one editor view (spec §8.2): Ver ▸ «Mirar adentro» (a check box, shortcut id
 * {@code view.magnifier}, no default key) and a toolbar toggle (an eye) on one {@code Action}; with it on, hovering a
 * user-component instance outlines it and floats its inside ({@link MagnifierPopup}). Alt held while hovering and the
 * «Mirar adentro» item of the instance menu show it with the toggle off. The toggle is the view's own and not
 * saved. {@link #dispose()} takes back everything.
 */
public final class MagnifierFeatures {

    private static final String SHORTCUT = "view.magnifier";

    private final EditorView view;
    private final Action toggle;
    private final MagnifierPresenter popup;
    private final MagnifierHover hover;
    private final JMenu viewMenu;
    private final JCheckBoxMenuItem menuItem;
    private final Runnable toolButtonRemover;
    private final Runnable shortcutRemover;
    private final Runnable marksRemover;
    private final Runnable paneListenersRemover;
    private final Runnable contributorRemover;
    private boolean on;
    private boolean disposed;

    public MagnifierFeatures(EditorView view) {
        this(view, new MagnifierPopup(view.services().painters()));
    }

    /** With another presenter (tests use one that shows no window). */
    MagnifierFeatures(EditorView view, MagnifierPresenter presenter) {
        this.view = view;
        this.popup = presenter;
        this.hover = new MagnifierHover(this::isOn, new MagnifierPresenter() {
            @Override
            public void show(CircuitCanvas pane, Component instance, Point2D screenPoint) {
                presenter.show(pane, instance, screenPoint);
                EveryPane.repaint(view).run();
            }

            @Override
            public void hide() {
                presenter.hide();
                EveryPane.repaint(view).run();
            }

            @Override
            public boolean covers(Point2D screenLocation) {
                return presenter.covers(screenLocation);
            }
        });
        presenter.whenThePointerLeavesThePanel(hover::pointerLeftPanel);
        this.toggle = new AbstractAction(Messages.text("action.magnifier")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                setOn(!on);
            }
        };
        toggle.putValue(Action.SHORT_DESCRIPTION, Messages.text("action.magnifier.tooltip"));
        Icons.install(toggle, MagnifierIcons.EYE);
        toggle.putValue(Action.SELECTED_KEY, false);
        this.viewMenu = view.menu(MenuId.VIEW);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        viewMenu.insert(menuItem, indexInViewMenu());
        this.toolButtonRemover = view.addToolbarToggleAfterTools(new JToggleButton(toggle));
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT, toggle);
        this.marksRemover = EveryPane.addOverlay(view, new MagnifierOutline(
                hover::shownInstance, () -> view.openDocument().registry(), () -> view.canvas().scheme()));
        this.paneListenersRemover = view.forEachPane(this::listenTo);
        this.contributorRemover = view.addContextMenuContributor(
                (menu, target, selection, gridPosition) -> contribute(menu, target, gridPosition));
    }

    /** The toggle's action (menu item and toolbar button); its {@code SELECTED_KEY} is the state. */
    public Action toggleAction() {
        return toggle;
    }

    /** The hover rules, for tests. */
    MagnifierHover hover() {
        return hover;
    }

    public boolean isOn() {
        return on;
    }

    private void setOn(boolean newState) {
        on = newState;
        toggle.putValue(Action.SELECTED_KEY, on);
        hover.refresh();
        EveryPane.repaint(view).run();
    }

    /** Hover, Alt and Escape of one pane; the returned runnable takes them back. */
    private Runnable listenTo(CircuitCanvas pane) {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hover.rememberAlt(event.isAltDown());
                hover.pointerMoved(pane, event.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                hover.pointerPressed();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                hover.pointerPressed();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover.pointerExited(event.getLocationOnScreen());
            }
        };
        KeyAdapter keys = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ALT) {
                    hover.altChanged(true);
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hover.escapePressed();
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ALT) {
                    hover.altChanged(false);
                }
            }
        };
        pane.addMouseListener(mouse);
        pane.addMouseMotionListener(mouse);
        pane.addKeyListener(keys);
        // presentation mode's Esc leaves the key to the shown magnifier
        Runnable escapeClaimRemover = pane.addEscapeClaim(() -> hover.shownInstance().isPresent());
        return () -> {
            escapeClaimRemover.run();
            pane.removeMouseListener(mouse);
            pane.removeMouseMotionListener(mouse);
            pane.removeKeyListener(keys);
        };
    }

    /** «Mirar adentro» on a user-component instance, with the eye of the toolbar button, next to «Entrar». */
    private void contribute(JPopupMenu menu, Optional<DocumentElement> target, Point2D gridPosition) {
        CircuitCanvas pane = view.canvas();
        Optional<Component> instance = target.filter(Component.class::isInstance).map(Component.class::cast)
                .filter(component -> EnterableInstances.definitionOf(view.openDocument(), component).isPresent());
        if (instance.isEmpty()) {
            return;
        }
        Point2D screenPoint = pane.viewport().gridToScreen(gridPosition.getX(), gridPosition.getY());
        Action lookInside = Actions.of(Messages.text("magnifier.menu"),
                () -> hover.requestedFromMenu(pane, instance.get(), screenPoint));
        Icons.install(lookInside, MagnifierIcons.EYE);
        menu.add(ContextMenuEntry.LOOK_INSIDE.tag(new JMenuItem(lookInside)));
    }

    /** After «Valores de los buses» when the menu has it; else at the end of the menu. */
    private int indexInViewMenu() {
        String busValuesText = Messages.text("action.busValues");
        for (int index = 0; index < viewMenu.getMenuComponentCount(); index++) {
            if (viewMenu.getMenuComponent(index) instanceof JMenuItem item && busValuesText.equals(item.getText())) {
                return index + 1;
            }
        }
        return viewMenu.getMenuComponentCount();
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        popup.close();
        contributorRemover.run();
        paneListenersRemover.run();
        marksRemover.run();
        shortcutRemover.run();
        toolButtonRemover.run();
        viewMenu.remove(menuItem);
    }
}
