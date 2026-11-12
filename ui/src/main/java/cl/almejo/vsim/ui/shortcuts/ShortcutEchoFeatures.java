package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.panels.CanvasNoticeStack;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;

/**
 * «Mostrar los atajos al usarlos» of one editor view: while the option is on, each action run by its shortcut, a
 * menu item or a toolbar button shows a chip at the bottom centre of the circuit area with its shortcut and name
 * («Ctrl+Z · Deshacer»; only the name when it has none), about 1.5 s, three at most. Menu items and toolbar buttons
 * are listened to (the listener is added after the action's own, and Swing calls the last added first, so the name
 * is the one before the action ran: «Deshacer agregar» does not turn into the next undo's). Canvas keys do not go
 * through a button: a key dispatcher recognizes the ones bound by {@link ShortcutsExtension} on a pane. It never
 * takes a key. Never shown for what is typed in a text field, nor while a part of the circuit has the keyboard (the
 * Terminal), nor for the mouse on the circuit. The shortcut shown is the user's current binding.
 */
final class ShortcutEchoFeatures {

    /** The catalog id of the lens ({@code LensFeatures.SHORTCUT}; the lens package depends on this one). */
    private static final String LENS_ID = "view.lens";

    static final String SHORTCUT_ID = "view.showUsedShortcuts";
    private static final Duration CHIP_DURATION = Duration.ofMillis(1500);
    private static final int MAXIMUM_CHIPS = 3;
    /**
     * A held key repeats: the same chip is not shown again while its key keeps coming within this time of the last
     * time, so a key held for seconds shows one chip (two separate uses a moment apart show one too).
     */
    private static final long REPEAT_MILLISECONDS = 400;

    private final EditorView view;
    private final AppPreferences preferences;
    private final ShortcutEchoExtension.Seams seams;
    private final Action toggle;
    private final JMenu viewMenu;
    private final JCheckBoxMenuItem menuItem;
    private final Runnable shortcutRemover;
    private final CanvasNoticeStack chips;
    private final KeyEventDispatcher keyDispatcher = this::keyPressed;
    private final Runnable preferencesListener = this::refresh;
    private final PropertyChangeListener hiddenListener = this::hiddenChanged;
    private final ActionListener buttonListener = this::buttonPressed;
    private final Set<AbstractButton> hooked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Runnable> menuListenerRemovers = new ArrayList<>();
    private String lastKeyChip = "";
    private long lastKeyChipTime;
    private boolean disposed;

    ShortcutEchoFeatures(EditorView view, ShortcutEchoExtension.Seams seams) {
        this.view = view;
        this.preferences = view.services().preferences();
        this.seams = seams;
        this.chips = new CanvasNoticeStack(view.canvasPanes(), CanvasNotice.Position.BOTTOM, CHIP_DURATION,
                MAXIMUM_CHIPS, seams.tickers(), seams.clock(), () -> seams.animations().test(preferences));
        this.toggle = Actions.of(Messages.text("action.showUsedShortcuts"),
                () -> preferences.setShowUsedShortcuts(!preferences.showUsedShortcuts()));
        this.viewMenu = view.menu(MenuId.VIEW);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        viewMenu.add(menuItem);
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT_ID, toggle);
        hookAll();
        for (JMenu menu : topMenus()) {
            MenuListener listener = Menus.onOpen(menu, this::hookAll);
            menuListenerRemovers.add(() -> menu.removeMenuListener(listener));
        }
        seams.dispatchers().add(keyDispatcher);
        preferences.addListener(preferencesListener);
        view.addPropertyChangeListener(ShortcutEchoExtension.CHIPS_HIDDEN_PROPERTY, hiddenListener);
        refresh();
    }

    private boolean chipsHidden() {
        return Boolean.TRUE.equals(view.getClientProperty(ShortcutEchoExtension.CHIPS_HIDDEN_PROPERTY));
    }

    private void hiddenChanged(PropertyChangeEvent event) {
        if (chipsHidden()) {
            chips.clear();
        }
    }

    private void refresh() {
        boolean shown = preferences.showUsedShortcuts();
        toggle.putValue(Action.SELECTED_KEY, shown);
        if (!shown) {
            chips.clear();
        }
    }

    private List<JMenu> topMenus() {
        List<JMenu> menus = new ArrayList<>();
        for (int index = 0; index < view.menuBar().getMenuCount(); index++) {
            menus.add(view.menuBar().getMenu(index));
        }
        return menus;
    }

    /** Listens to every menu item and toolbar button not yet listened to (menus fill in after the install). */
    private void hookAll() {
        for (JMenu menu : topMenus()) {
            hookMenu(menu);
        }
        for (Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button) {
                hook(button);
            }
        }
    }

    private void hookMenu(JMenu menu) {
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenu submenu) {
                hookMenu(submenu);
            } else if (child instanceof JMenuItem item) {
                hook(item);
            }
        }
    }

    private void hook(AbstractButton button) {
        if (button.getAction() != null && button.getAction() != toggle && hooked.add(button)) {
            button.addActionListener(buttonListener);
        }
    }

    private void buttonPressed(java.awt.event.ActionEvent event) {
        if (disposed || !preferences.showUsedShortcuts() || !(event.getSource() instanceof AbstractButton button)
                || button.getAction() == null || !button.getAction().isEnabled()) {
            return;
        }
        if (seams.currentEvent().get() instanceof KeyEvent && keyboardGoesElsewhere()) {
            return;
        }
        Action action = button.getAction();
        Optional<String> id = idOf(action);
        pushChip(id.flatMap(this::strokeOf), nameOf(action));
    }

    /** True while the keys belong to something that is not the circuit's shortcuts: a text field, a part's capture. */
    private boolean keyboardGoesElsewhere() {
        return seams.focusOwner().get() instanceof JTextComponent
                || view.keyboardCapture().captured().isPresent();
    }

    private boolean keyPressed(KeyEvent event) {
        if (disposed || event.getID() != KeyEvent.KEY_PRESSED || !preferences.showUsedShortcuts()
                || !(event.getSource() instanceof CircuitCanvas pane) || !SwingUtilities.isDescendingFrom(pane, view)
                || keyboardGoesElsewhere()) {
            return false;
        }
        Optional<String> bound = bindingOf(pane, event);
        if (bound.isEmpty()) {
            return false;
        }
        String name = bound.get();
        Action action = pane.getActionMap().get(name);
        if (action == null || !action.isEnabled()) {
            return false;
        }
        String id = name.substring(ShortcutsExtension.BINDING_PREFIX.length());
        Optional<KeyStroke> stroke = strokeOf(id);
        String actionName = nameOf(action);
        String text = stroke.map(found -> KeyNames.describe(found, seams.macNotation()) + " · " + actionName)
                .orElse(actionName);
        long now = seams.clock().getAsLong();
        boolean repeating = text.equals(lastKeyChip) && now - lastKeyChipTime < REPEAT_MILLISECONDS;
        lastKeyChip = text;
        lastKeyChipTime = now; // a key held down keeps repeating within the time: one chip for the whole hold
        if (!repeating) {
            pushChip(stroke, actionName);
        }
        return false;
    }

    /**
     * The name of the shortcut binding {@code event} runs on {@code pane}, looked up as Swing does (the extended key
     * code first: a key with no {@code VK_} constant, ç, is bound by it); for a key under Esc other than ` (º, ²…),
     * the lens's binding when it is on ` (what that key means for it, {@link KeyCodes#matches}); no other action's.
     */
    private static Optional<String> bindingOf(CircuitCanvas pane, KeyEvent event) {
        List<KeyStroke> strokes = new ArrayList<>();
        if (event.getKeyCode() != event.getExtendedKeyCode()) {
            strokes.add(KeyStroke.getKeyStroke(event.getExtendedKeyCode(), event.getModifiersEx(), false));
        }
        strokes.add(KeyStroke.getKeyStrokeForEvent(event));
        for (KeyStroke stroke : strokes) {
            if (pane.getInputMap(JComponent.WHEN_FOCUSED).get(stroke) instanceof String name
                    && name.startsWith(ShortcutsExtension.BINDING_PREFIX)) {
                return Optional.of(name);
            }
        }
        String lensBinding = ShortcutsExtension.BINDING_PREFIX + LENS_ID;
        boolean isAnotherKeyUnderEsc = KeyCodes.of(event) != KeyEvent.VK_BACK_QUOTE
                && (KeyCodes.isUnderEsc(KeyCodes.of(event)) || KeyCodes.isUnderEsc(event.getExtendedKeyCode()));
        if (isAnotherKeyUnderEsc && pane.getInputMap(JComponent.WHEN_FOCUSED).get(
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, event.getModifiersEx(), false)) instanceof String name
                && name.equals(lensBinding)) {
            return Optional.of(name);
        }
        return Optional.empty();
    }

    private Optional<String> idOf(Action action) {
        for (Map.Entry<String, Action> entry : ShortcutsExtension.actionsOf(view).entrySet()) {
            if (entry.getValue() == action) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private Optional<KeyStroke> strokeOf(String id) {
        return new KeyBindings(preferences).strokeFor(id);
    }

    private static String nameOf(Action action) {
        String name = String.valueOf(action.getValue(Action.NAME));
        return name.replaceAll("(…|\\.\\.\\.)\\s*$", "").strip();
    }

    private void pushChip(Optional<KeyStroke> stroke, String name) {
        if (chipsHidden()) {
            return;
        }
        chips.push(stroke.map(found -> KeyNames.keys(found, seams.macNotation())).orElse(List.of()),
                !seams.macNotation(), name);
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        seams.dispatchers().remove(keyDispatcher);
        preferences.removeListener(preferencesListener);
        view.removePropertyChangeListener(ShortcutEchoExtension.CHIPS_HIDDEN_PROPERTY, hiddenListener);
        menuListenerRemovers.forEach(Runnable::run);
        hooked.forEach(button -> button.removeActionListener(buttonListener));
        hooked.clear();
        chips.clear();
        shortcutRemover.run();
        viewMenu.remove(menuItem);
    }
}
