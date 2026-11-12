package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyCodes;
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
import javax.swing.JComponent;
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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The accessibility lens of one editor view: an enlarged circle of the circuit around the pointer, like a screen
 * magnifier, painted from the real scene of the pane under the pointer ({@link LensPainter}). One action turns it on
 * and off: Ver ▸ «Lupa», the toolbar button's main part and the key of {@value #SHORTCUT} (by default the key under
 * Esc, left of 1, with no modifier: ` or º; rebindable; it only acts with the focus in a canvas pane). Each physical
 * press toggles it once: the key repeating while held is taken by a key hook before anybody else sees it (the canvas
 * binding and the key echo of «Mostrar los atajos que se usan» included). It is purely visual: no tool, cursor,
 * selection or document changes, no undo step. Being on is never saved. {@link #dispose()} takes back everything.
 *
 * <p>The toolbar button right after «Mirar adentro»'s eye is a {@link SplitButton}: its main part is «Lupa» (the same
 * action as the menu item and the key) and its ▾ picks the magnification (2×, 3×, 4×; 3× by default). The
 * magnification is saved in the preferences and shared by every view; being on is never saved.
 */
public final class LensFeatures {

    /** The catalog id of the key that turns the lens on and off. */
    public static final String SHORTCUT = "view.lens";

    /** The id the ▾'s choice is saved under ({@code splitButton.lens.zoom}). */
    static final String ZOOM_PREFERENCE = "lens.zoom";

    /** The modifiers a key stroke of the keyboard can hold (mouse buttons left out). */
    private static final int KEYBOARD_MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    /**
     * A press of the key this soon after its release is the key repeating, not a new press: some systems repeat a
     * held key as release and press pairs (with the same time, or a few milliseconds apart), and nobody presses a key
     * again this fast.
     */
    static final long REPEAT_GAP_MILLISECONDS = 50;

    /** Where a view hears that its window stopped being the active one; the returned runnable stops listening. */
    @FunctionalInterface
    interface WindowDeactivations {

        Runnable watch(EditorView view, Runnable whenDeactivated);
    }

    private final EditorView view;
    private final KeyBindings keyBindings;
    private final KeyDispatchers dispatchers;
    private final LensState<CircuitCanvas> state = new LensState<>();
    private final Action toggle;
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
    /** The lens's key while it is down (its code as {@link KeyCodes#of} gives it): another press is a repeat. */
    private OptionalInt keyDown = OptionalInt.empty();
    /** The lens's key's last release, to tell a repeat's release and press pair from a new press. */
    private Optional<KeyRelease> lastRelease = Optional.empty();
    private boolean disposed;

    public LensFeatures(EditorView view) {
        this(view, KeyDispatchers.focusManager(), LensFeatures::watchTheActiveWindow);
    }

    /** With the key hooks and the window watch of a test. */
    LensFeatures(EditorView view, KeyDispatchers dispatchers, WindowDeactivations deactivations) {
        this.view = Objects.requireNonNull(view, "view");
        this.keyBindings = new KeyBindings(view.services().preferences());
        this.dispatchers = Objects.requireNonNull(dispatchers, "dispatchers");
        this.toggle = new AbstractAction(Messages.text("action.lens")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                setOn(!state.isOn());
            }
        };
        toggle.putValue(Action.SELECTED_KEY, false);
        Icons.install(toggle, LensIcons.LENS);
        this.preferences = view.services().preferences();
        this.viewMenu = view.menu(MenuId.VIEW);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        menuItem.putClientProperty(Actions.SEARCH_TERMS, Messages.text("lens.searchTerms"));
        viewMenu.insert(menuItem, indexInViewMenu());
        this.button = SplitButton.forToggle(ZOOM_PREFERENCE, Messages.text("lens.zoomMenuTitle"), zoomModes(),
                preferences, toggle, String.valueOf((int) LensState.DEFAULT_FACTOR));
        state.setFactor(factorOf(button.mode()));
        button.addModeListener(mode -> setFactor(factorOf(mode)));
        NewsTargets.register(button, NewsTargets.TOOLBAR_LENS, NewsTargets.Side.BELOW);
        refreshTooltip();
        this.toolbarRemover = view.addToolbarComponentAfterAction(button,
                view.shortcutActions().get("view.magnifier"));
        preferences.addListener(preferenceFollower);
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT, toggle);
        this.paneRemover = view.forEachPane(this::listenTo);
        dispatchers.add(keyHook);
        this.deactivationRemover = deactivations.watch(view, this::forgetTheKey);
    }

    /** The toolbar's split button: «Lupa» and its ▾ with the magnification. */
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

    /** «Lupa: … (º)», with the key the catalog gives now (none: without the parenthesis). */
    private void refreshTooltip() {
        String tooltip = keyBindings.strokeFor(SHORTCUT)
                .map(stroke -> Messages.format("action.lens.tooltip", KeyNames.describe(stroke)))
                .orElse(Messages.text("action.lens.tooltipNoKey"));
        toggle.putValue(Action.SHORT_DESCRIPTION, tooltip);
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

    /** «Lupa» (menu item, toolbar button and key): its {@code SELECTED_KEY} is whether the lens is on. */
    public Action toggleAction() {
        return toggle;
    }

    /** On, magnification and pointer: read it, change it through this class. */
    public LensState<CircuitCanvas> state() {
        return state;
    }

    /** Shows the lens {@code factor} times larger, one of {@link LensState#FACTORS}. */
    public void setFactor(double factor) {
        changing(() -> state.setFactor(factor));
    }

    private void setOn(boolean on) {
        changing(() -> state.setOn(on));
        toggle.putValue(Action.SELECTED_KEY, on);
    }

    /**
     * Every key of the application, before the components get it. A first press of the lens's key in a canvas pane
     * of this view turns the lens on or off (unless the pane's own binding will, see {@link #thePaneBindingRunsIt})
     * and goes on; the key repeating is taken, so nobody else sees the repeats.
     */
    private boolean keyEvent(KeyEvent event) {
        if (disposed) {
            return false;
        }
        if (event.getID() == KeyEvent.KEY_PRESSED && comesFromThisView(event) && isTheBoundStroke(event)) {
            int key = KeyCodes.of(event);
            boolean repeat = isRepeat(key, event.getWhen());
            keyDown = OptionalInt.of(key);
            if (repeat) {
                return true;
            }
            if (!thePaneBindingRunsIt(event)) {
                toggle.actionPerformed(new ActionEvent(event.getSource(), ActionEvent.ACTION_PERFORMED, SHORTCUT));
            }
        } else if (event.getID() == KeyEvent.KEY_RELEASED && keyDown.isPresent()
                && keyDown.getAsInt() == KeyCodes.of(event)) {
            keyDown = OptionalInt.empty();
            lastRelease = Optional.of(new KeyRelease(KeyCodes.of(event), event.getWhen()));
        }
        return false;
    }

    /** Still down, or released only a moment ago: the key repeating (see {@link #REPEAT_GAP_MILLISECONDS}). */
    private boolean isRepeat(int key, long when) {
        if (keyDown.isPresent() && keyDown.getAsInt() == key) {
            return true;
        }
        return lastRelease.filter(release -> release.key() == key
                && when - release.when() < REPEAT_GAP_MILLISECONDS).isPresent();
    }

    /** The window went away with the key perhaps down: its release will not come here, so the next press is new. */
    private void forgetTheKey() {
        keyDown = OptionalInt.empty();
        lastRelease = Optional.empty();
    }

    /**
     * Whether the pane's key bindings will run «Lupa» for {@code event} right after the hooks (the canvas binding
     * {@code ShortcutsExtension} makes for the current stroke), looked up as Swing does: the extended key code first.
     * Then the hook leaves the toggling to it, so a press toggles once. Another key under Esc (º when the binding is
     * `) has no binding, and the hook toggles itself.
     */
    private boolean thePaneBindingRunsIt(KeyEvent event) {
        JComponent pane = (JComponent) event.getSource();
        KeyStroke plain = KeyStroke.getKeyStrokeForEvent(event);
        List<KeyStroke> strokes = event.getKeyCode() == event.getExtendedKeyCode() ? List.of(plain)
                : List.of(KeyStroke.getKeyStroke(event.getExtendedKeyCode(), event.getModifiersEx(), false), plain);
        for (KeyStroke stroke : strokes) {
            Object name = pane.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
            Action action = name == null ? null : pane.getActionMap().get(name);
            if (action != null && action.isEnabled()) {
                return action == toggle;
            }
        }
        return false;
    }

    /** Only a canvas pane of this view: in a text field, the inspector or the find bar the key types normally. */
    private boolean comesFromThisView(KeyEvent event) {
        return event.getSource() instanceof CircuitCanvas source && SwingUtilities.isDescendingFrom(source, view);
    }

    /** The current binding, read at each press so a rebinding works at once. */
    private boolean isTheBoundStroke(KeyEvent event) {
        return keyBindings.strokeFor(SHORTCUT)
                .filter(stroke -> KeyCodes.matches(stroke.getKeyCode(), KeyCodes.of(event))
                        && (stroke.getModifiers() & KEYBOARD_MODIFIERS) == (event.getModifiersEx() & KEYBOARD_MODIFIERS))
                .isPresent();
    }

    /** When the lens's key went up, and which key it was. */
    private record KeyRelease(int key, long when) {
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
        changing(() -> state.setOn(false));
        deactivationRemover.run();
        dispatchers.remove(keyHook);
        paneRemover.run();
        shortcutRemover.run();
        viewMenu.remove(menuItem);
    }
}
