package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.toolbar.SplitButtonMode;
import cl.almejo.vsim.ui.whatsnew.NewsTargets;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The accessibility lens of one editor view: an enlarged circle of the circuit around the pointer, like a screen
 * magnifier, painted from the real scene of the pane under the pointer ({@link LensPainter}). It shows while the key
 * of {@value #SHORTCUT} is held (Ctrl+Mayús+L, ⌘+Mayús+L on macOS; rebindable) and goes when that key or one of its
 * modifiers is released or the window loses the focus; Ver ▸ «Lupa fija» keeps it on. It is purely visual: no tool,
 * cursor, selection or document changes, no undo step. Neither held nor fixed is saved. {@link #dispose()} takes
 * back everything.
 *
 * <p>The toolbar button right after «Mirar adentro»'s eye is a {@link SplitButton}: its main part is «Lupa fija» (the
 * same action as the menu item) and its ▾ picks the magnification (2×, 3×, 4×; 3× by default). The magnification is
 * saved in the preferences and shared by every view; being fixed is never saved.
 */
public final class LensFeatures {

    /** The catalog id of the key to hold. */
    public static final String SHORTCUT = "view.lens";

    /** The id the ▾'s choice is saved under ({@code splitButton.lens.zoom}). */
    static final String ZOOM_PREFERENCE = "lens.zoom";

    /** The modifiers a key stroke of the keyboard can hold (mouse buttons left out), with the key of each one. */
    private static final Map<Integer, Integer> MODIFIER_KEYS = Map.of(
            InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT,
            InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_CONTROL,
            InputEvent.ALT_DOWN_MASK, KeyEvent.VK_ALT,
            InputEvent.META_DOWN_MASK, KeyEvent.VK_META,
            InputEvent.ALT_GRAPH_DOWN_MASK, KeyEvent.VK_ALT_GRAPH);
    private static final int KEYBOARD_MODIFIERS = MODIFIER_KEYS.keySet().stream()
            .mapToInt(Integer::intValue).reduce(0, (all, mask) -> all | mask);

    /** Where a view hears that its window stopped being the active one; the returned runnable stops listening. */
    @FunctionalInterface
    interface WindowDeactivations {

        Runnable watch(EditorView view, Runnable whenDeactivated);
    }

    private final EditorView view;
    private final KeyBindings keyBindings;
    private final KeyDispatchers dispatchers;
    private final LensState<CircuitCanvas> state = new LensState<>();
    private final Action hold;
    private final Action fixed;
    private final JMenu viewMenu;
    private final JCheckBoxMenuItem menuItem;
    private final KeyEventDispatcher keyHook = this::keyEvent;
    private final Runnable shortcutRemover;
    private final AppPreferences preferences;
    private final SplitButton button;
    private final Runnable toolbarRemover;
    private final Runnable preferenceFollower = this::followPreferences;
    private final Runnable paneRemover;
    private final Runnable deactivationRemover;
    /** The stroke that showed the held lens: its key and modifiers are what hide it (a rebinding meanwhile too). */
    private Optional<KeyStroke> heldStroke = Optional.empty();
    private boolean disposed;

    public LensFeatures(EditorView view) {
        this(view, KeyDispatchers.focusManager(), LensFeatures::watchTheActiveWindow);
    }

    /** With the key hooks and the window watch of a test. */
    LensFeatures(EditorView view, KeyDispatchers dispatchers, WindowDeactivations deactivations) {
        this.view = Objects.requireNonNull(view, "view");
        this.keyBindings = new KeyBindings(view.services().preferences());
        this.dispatchers = Objects.requireNonNull(dispatchers, "dispatchers");
        this.hold = new AbstractAction(Messages.text("action.lens")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                pressed(keyBindings.strokeFor(SHORTCUT));
            }
        };
        this.fixed = new AbstractAction(Messages.text("action.lensFixed")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                setFixed(!state.isFixed());
            }
        };
        fixed.putValue(Action.SELECTED_KEY, false);
        Icons.install(fixed, LensIcons.LENS);
        this.preferences = view.services().preferences();
        this.viewMenu = view.menu(MenuId.VIEW);
        this.menuItem = new JCheckBoxMenuItem(fixed);
        menuItem.putClientProperty(Actions.SEARCH_TERMS, Messages.text("lens.searchTerms"));
        viewMenu.insert(menuItem, indexInViewMenu());
        this.button = SplitButton.forToggle(ZOOM_PREFERENCE, Messages.text("lens.zoomMenuTitle"), zoomModes(),
                preferences, fixed, String.valueOf((int) LensState.DEFAULT_FACTOR));
        state.setFactor(factorOf(button.mode()));
        button.addModeListener(mode -> setFactor(factorOf(mode)));
        NewsTargets.register(button, NewsTargets.TOOLBAR_LENS, NewsTargets.Side.BELOW);
        refreshTooltip();
        this.toolbarRemover = view.addToolbarComponentAfterAction(button,
                view.shortcutActions().get("view.magnifier"));
        preferences.addListener(preferenceFollower);
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT, hold);
        this.paneRemover = view.forEachPane(this::listenTo);
        dispatchers.add(keyHook);
        this.deactivationRemover = deactivations.watch(view, this::released);
    }

    /** The toolbar's split button: «Lupa fija» and its ▾ with the magnification. */
    public SplitButton splitButton() {
        return button;
    }

    private static List<SplitButtonMode> zoomModes() {
        Icon none = new BlankIcon();
        return LensState.FACTORS.stream().map(factor -> {
            String text = Messages.format("lens.zoomLabel", (int) (double) factor);
            return new SplitButtonMode(String.valueOf((int) (double) factor), none, text, text);
        }).toList();
    }

    private static double factorOf(SplitButtonMode mode) {
        return Double.parseDouble(mode.id());
    }

    /** What another view's ▾ chose shows here too, and a rebinding of the key shows in the tooltip. */
    private void followPreferences() {
        preferences.splitButtonMode(ZOOM_PREFERENCE)
                .filter(chosen -> zoomModes().stream().anyMatch(mode -> mode.id().equals(chosen)))
                .filter(chosen -> !chosen.equals(button.mode().id()))
                .ifPresent(button::setMode);
        refreshTooltip();
    }

    /** «Lupa fija: …(o mantén Ctrl+Mayús+L)», with the key the catalog gives now (none: without the parenthesis). */
    private void refreshTooltip() {
        String tooltip = keyBindings.strokeFor(SHORTCUT)
                .map(stroke -> Messages.format("action.lens.tooltip", KeyNames.describe(stroke)))
                .orElse(Messages.text("action.lens.tooltipNoKey"));
        fixed.putValue(Action.SHORT_DESCRIPTION, tooltip);
        button.mainButton().setToolTipText(tooltip);
    }

    /** An empty 1 px icon: the ▾'s items show the magnification as text alone. */
    private static final class BlankIcon implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
        }

        @Override
        public int getIconWidth() {
            return 1;
        }

        @Override
        public int getIconHeight() {
            return 1;
        }
    }

    /** «Lupa fija» (menu item, and the toolbar's button): its {@code SELECTED_KEY} is the state. */
    public Action fixedAction() {
        return fixed;
    }

    /** Held, fixed, magnification and pointer: read it, change it through this class. */
    public LensState<CircuitCanvas> state() {
        return state;
    }

    /** Shows the lens {@code factor} times larger, one of {@link LensState#FACTORS}. */
    public void setFactor(double factor) {
        changing(() -> state.setFactor(factor));
    }

    private void setFixed(boolean on) {
        changing(() -> state.setFixed(on));
        fixed.putValue(Action.SELECTED_KEY, on);
    }

    private void pressed(Optional<KeyStroke> stroke) {
        heldStroke = stroke;
        changing(state::press);
    }

    private void released() {
        heldStroke = Optional.empty();
        changing(state::release);
    }

    /** Every key of the application, before the components get it; none is taken. */
    private boolean keyEvent(KeyEvent event) {
        if (disposed) {
            return false;
        }
        if (event.getID() == KeyEvent.KEY_PRESSED && comesFromThisView(event) && isTheBoundStroke(event)) {
            // a key repeating sends more presses (X11 too, without releases in between): the lens just stays
            hold.actionPerformed(new ActionEvent(event.getSource(), ActionEvent.ACTION_PERFORMED, SHORTCUT));
        } else if (event.getID() == KeyEvent.KEY_RELEASED && releasesTheHeldStroke(event)) {
            released();
        }
        return false;
    }

    private boolean comesFromThisView(KeyEvent event) {
        return event.getSource() instanceof Component source && SwingUtilities.isDescendingFrom(source, view);
    }

    /** The current binding, read at each press so a rebinding works at once. */
    private boolean isTheBoundStroke(KeyEvent event) {
        return keyBindings.strokeFor(SHORTCUT).filter(stroke -> stroke.getKeyCode() == event.getKeyCode()
                && (stroke.getModifiers() & KEYBOARD_MODIFIERS) == (event.getModifiersEx() & KEYBOARD_MODIFIERS))
                .isPresent();
    }

    /** The held stroke's key, or the key of one of its modifiers (Ctrl, Mayús, ⌘…), went up. */
    private boolean releasesTheHeldStroke(KeyEvent event) {
        return state.isHeld() && heldStroke.filter(stroke -> isPartOf(stroke, event.getKeyCode())).isPresent();
    }

    private static boolean isPartOf(KeyStroke stroke, int keyCode) {
        if (stroke.getKeyCode() == keyCode) {
            return true;
        }
        return MODIFIER_KEYS.entrySet().stream().anyMatch(
                modifier -> (stroke.getModifiers() & modifier.getKey()) != 0 && modifier.getValue() == keyCode);
    }

    /** The pointer over one pane and the lens painted on it; the returned runnable takes both back. */
    private Runnable listenTo(CircuitCanvas pane) {
        MouseAdapter pointer = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                changing(() -> state.pointerAt(pane, event.getPoint()));
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                changing(() -> state.pointerAt(pane, event.getPoint()));
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                // a drag keeps sending the points beyond the pane's edge: there the lens is gone
                if (pane.contains(event.getPoint())) {
                    changing(() -> state.pointerAt(pane, event.getPoint()));
                } else {
                    changing(() -> state.pointerLeft(pane));
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                changing(() -> state.pointerLeft(pane));
            }
        };
        pane.addMouseListener(pointer);
        pane.addMouseMotionListener(pointer);
        Runnable overlayRemover = pane.addScreenOverlay((screen, painted) -> {
            if (state.isShowingOn(painted)) {
                LensPainter.paint(screen, painted, state.pointer().orElseThrow(), state.factor());
            }
        });
        return () -> {
            changing(() -> state.pointerLeft(pane));
            overlayRemover.run();
            pane.removeMouseListener(pointer);
            pane.removeMouseMotionListener(pointer);
        };
    }

    /**
     * Applies {@code change} and repaints where the lens was and where it is now (only those areas: a pointer move
     * never repaints the whole pane; simulation frames repaint the pane anyway).
     */
    private void changing(Runnable change) {
        Optional<ShownLens> before = shown();
        change.run();
        Optional<ShownLens> after = shown();
        if (before.equals(after)) {
            return;
        }
        before.ifPresent(ShownLens::repaint);
        after.ifPresent(ShownLens::repaint);
    }

    private Optional<ShownLens> shown() {
        if (!state.isShowing()) {
            return Optional.empty();
        }
        return Optional.of(new ShownLens(state.pointerPane().orElseThrow(),
                LensPainter.area(state.pointer().orElseThrow()), state.factor()));
    }

    /** Where a shown lens paints, and at which magnification (a new one repaints the same area). */
    private record ShownLens(CircuitCanvas pane, Rectangle area, double factor) {

        void repaint() {
            pane.repaint(area);
        }
    }

    /** Right after «Mirar adentro» when the menu has it; else at the end of the menu. */
    private int indexInViewMenu() {
        String lookInside = Messages.text("action.magnifier");
        for (int index = 0; index < viewMenu.getMenuComponentCount(); index++) {
            if (viewMenu.getMenuComponent(index) instanceof JMenuItem item && lookInside.equals(item.getText())) {
                return index + 1;
            }
        }
        return viewMenu.getMenuComponentCount();
    }

    /** The real watch: the focus manager's active window becoming another one (a dialog, another app). */
    private static Runnable watchTheActiveWindow(EditorView view, Runnable whenDeactivated) {
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        PropertyChangeListener listener = event -> {
            if (event.getNewValue() != SwingUtilities.getWindowAncestor(view)) {
                whenDeactivated.run();
            }
        };
        focusManager.addPropertyChangeListener("activeWindow", listener);
        return () -> focusManager.removePropertyChangeListener("activeWindow", listener);
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        preferences.removeListener(preferenceFollower);
        toolbarRemover.run();
        changing(state::release);
        deactivationRemover.run();
        dispatchers.remove(keyHook);
        paneRemover.run();
        shortcutRemover.run();
        viewMenu.remove(menuItem);
    }
}
