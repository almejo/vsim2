package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.preferences.PreferencesDialog;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutEchoExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The sheet of shortcuts of one editor view: a card on a dimmed circuit area (not the menus) with a selection of the
 * shortcuts the user has now ({@link SheetSelection}), the phrase of the gesture under way, the switch «Mostrar los
 * atajos al usarlos» (the same preference as the Ver item) and a link to Preferencias ▸ Atajos de teclado.
 * <p>
 * «?» opens and closes it: the typed character, so it is the same key on a Spanish (Mayús+') and a US (Mayús+/)
 * keyboard, and never while a text field or a table has the keyboard (there it is typed) or a part of the circuit has
 * captured it (the Terminal). It is fixed like Esc, not a catalog shortcut: a catalog stroke is a key press, which
 * differs from layout to layout. Esc closes it before anything else sees the Esc (in presentation mode too, which then
 * stays on: every pane claims the Esc while the sheet is open). Holding «?» toggles it once ({@link HeldKey}); a click
 * on the dimmed circuit around the card closes it. Ayuda ▸ «Atajos de teclado» shows «?» and does the same.
 * The canvas keeps the keyboard while the sheet shows, and any other key closes the sheet and then acts as usual
 * ({@link #closesTheSheetFirst}); on closing the canvas gets the keyboard back if it had it. While open, the chips of
 * «Mostrar los atajos al usarlos» are hidden.
 */
final class ShortcutSheetFeatures {

    static final KeyStroke QUESTION_MARK = KeyStroke.getKeyStroke('?');
    private static final String FIND_ACTION_ID = "help.findAction";
    private static final String PREFERENCES_ID = "file.preferences";
    private static final long FADE_MILLISECONDS = 150;

    private final EditorView view;
    private final ShortcutSheetExtension.Seams seams;
    private final AppPreferences preferences;
    private final Action toggle;
    private final ShownAcceleratorItem item;
    private final KeyEventDispatcher keyHook = this::keyEvent;
    private final Runnable removeEscapeClaims;
    private final List<Runnable> whileOpen = new ArrayList<>();
    private final List<SheetSelection.Item> selection = SheetSelection.load();
    private ShortcutSheetPanel panel;
    private final HeldKey heldKey = new HeldKey();
    /** Whether closing gives the keyboard to the canvas: it had it (or nothing had it) when the sheet opened. */
    private boolean canvasHadTheKeyboard = true;
    private boolean disposed;

    ShortcutSheetFeatures(EditorView view, ShortcutSheetExtension.Seams seams) {
        this.view = view;
        this.seams = seams;
        this.preferences = view.services().preferences();
        this.toggle = Actions.of(Messages.text("action.shortcutSheet"), this::toggle);
        this.item = new ShownAcceleratorItem(toggle, QUESTION_MARK);
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text("shortcutSheet.searchTerms"));
        JMenu help = view.menu(MenuId.HELP);
        help.add(item, helpItemIndex(help));
        seams.dispatchers().add(keyHook);
        removeEscapeClaims = view.forEachPane(pane -> pane.addEscapeClaim(this::isOpen));
    }

    /** Right after «Buscar una acción…» when the palette put it there, first otherwise. */
    private int helpItemIndex(JMenu help) {
        Action findAction = view.shortcutActions().get(FIND_ACTION_ID);
        return findAction == null ? 0 : Menus.indexOf(help, findAction) + 1;
    }

    boolean isOpen() {
        return panel != null;
    }

    private void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    private boolean keyEvent(KeyEvent event) {
        Component source = event.getComponent();
        heldKey.see(event);
        if (disposed || source == null || MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return false;
        }
        if (isOpen() && isPlainEscape(event) && SwingUtilities.getRootPane(source) == SwingUtilities.getRootPane(view)) {
            close();
            event.consume();
            return true;
        }
        if (isOpen() && closesTheSheetFirst(event)
                && SwingUtilities.getRootPane(source) == SwingUtilities.getRootPane(view)) {
            close(); // and the key goes on to do what it does: the user sees its effect with the sheet gone
            return false;
        }
        if (isQuestionMark(event) && SwingUtilities.isDescendingFrom(source, view) && !typesText(source)
                && view.keyboardCapture().captured().isEmpty()) {
            if (!heldKey.isRepeat()) { // holding «?» toggles once, it does not flicker
                toggle();
                heldKey.used();
            }
            event.consume();
            return true;
        }
        return false;
    }

    /**
     * A key pressed while the sheet shows closes it and then acts as usual (the user's choice, 2026-11-09: «Cerrar y
     * actuar»): Supr deletes, an arrow moves, Espacio flips the elbow. Not Esc (it only closes), not a modifier alone,
     * and not the press that types «?», whose typed character toggles the sheet.
     */
    private static boolean closesTheSheetFirst(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED || event.getKeyChar() == QUESTION_MARK.getKeyChar()) {
            return false;
        }
        return switch (event.getKeyCode()) {
            case KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_META,
                 KeyEvent.VK_WINDOWS, KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_NUM_LOCK, KeyEvent.VK_UNDEFINED -> false;
            default -> true;
        };
    }

    private static boolean isPlainEscape(KeyEvent event) {
        int modifiers = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
        return event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE
                && (event.getModifiersEx() & modifiers) == 0;
    }

    /**
     * «?» typed with nothing but Mayús or AltGr (Ctrl+Alt on Windows) held: Ctrl+? or ⌘? is another shortcut, not
     * the sheet.
     */
    private static boolean isQuestionMark(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_TYPED || event.getKeyChar() != QUESTION_MARK.getKeyChar()) {
            return false;
        }
        boolean altGraph = event.isAltGraphDown() || (event.isControlDown() && event.isAltDown());
        return !event.isMetaDown() && (altGraph || (!event.isControlDown() && !event.isAltDown()));
    }

    /** Where «?» is a character the user is typing: a text field, or a table that starts editing a cell with it. */
    private static boolean typesText(Component source) {
        return source instanceof JTextComponent || source instanceof JTable;
    }

    private void open() {
        JRootPane rootPane = SwingUtilities.getRootPane(view);
        if (disposed || isOpen() || rootPane == null) {
            return;
        }
        canvasHadTheKeyboard = view.keyboardFocus().owner()
                .map(owner -> owner instanceof CircuitCanvas && SwingUtilities.isDescendingFrom(owner, view))
                .orElse(true);
        ShortcutSheetPanel opened = new ShortcutSheetPanel(preferences::setShowUsedShortcuts, this::showAllShortcuts,
                this::close);
        panel = opened;
        JLayeredPane layers = rootPane.getLayeredPane();
        layers.add(opened, JLayeredPane.POPUP_LAYER);
        whileOpen.add(() -> {
            Rectangle bounds = opened.getBounds();
            layers.remove(opened);
            layers.repaint(bounds);
        });
        refresh();
        place(layers);
        follow(layers);
        view.putClientProperty(ShortcutEchoExtension.CHIPS_HIDDEN_PROPERTY, Boolean.TRUE);
        whileOpen.add(() -> view.putClientProperty(ShortcutEchoExtension.CHIPS_HIDDEN_PROPERTY, null));
        fadeIn(opened);
    }

    /** Keeps the sheet up to date while it shows: the keys, the checkbox, the gesture, the size of the area. */
    private void follow(JLayeredPane layers) {
        Runnable refresher = this::refresh;
        preferences.addListener(refresher);
        whileOpen.add(() -> preferences.removeListener(refresher));
        Runnable removePhraseListener = view.addGesturePhraseListener(this::showGesturePhrase);
        whileOpen.add(removePhraseListener);
        ComponentListener areaListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                place(layers);
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                place(layers);
            }
        };
        view.canvasPanes().addComponentListener(areaListener);
        whileOpen.add(() -> view.canvasPanes().removeComponentListener(areaListener));
        ComponentListener hiddenListener = new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent event) {
                close(); // another tab of the window came to the front
            }
        };
        view.addComponentListener(hiddenListener);
        whileOpen.add(() -> view.removeComponentListener(hiddenListener));
    }

    /** Over the circuit area, a margin inside its edges. */
    private void place(JLayeredPane layers) {
        if (panel == null) {
            return;
        }
        Rectangle area = SwingUtilities.convertRectangle(view.canvasPanes(),
                new Rectangle(view.canvasPanes().getSize()), layers);
        panel.setBounds(area);
        panel.doLayout();
    }

    private void refresh() {
        if (panel == null) {
            return;
        }
        panel.setColumns(SheetSelection.groups(selection, new KeyBindings(preferences)));
        panel.setShowUsedShortcuts(preferences.showUsedShortcuts());
        panel.setAllShortcutsAvailable(shortcutsPreferences().isPresent());
        showGesturePhrase();
    }

    private void showGesturePhrase() {
        if (panel != null) {
            panel.setGesturePhrase(view.canvas().gesturePhrase());
        }
    }

    /** Fades in over {@value #FADE_MILLISECONDS} ms with animations on; whole at once otherwise. */
    private void fadeIn(ShortcutSheetPanel opened) {
        if (!seams.animations().test(preferences)) {
            opened.setOpacity(1f);
            return;
        }
        opened.setOpacity(0f);
        FrameTicker ticker = seams.tickers().get();
        long start = seams.clock().getAsLong();
        ticker.start(() -> {
            long elapsed = seams.clock().getAsLong() - start;
            boolean whole = elapsed >= FADE_MILLISECONDS || !seams.animations().test(preferences);
            opened.setOpacity(whole ? 1f : (float) elapsed / FADE_MILLISECONDS);
            if (whole) {
                ticker.stop();
            }
        });
        whileOpen.add(ticker::stop);
    }

    /** The link: the sheet closes, then Preferencias opens on «Atajos de teclado». */
    private void showAllShortcuts() {
        Optional<Action> shortcutsTab = shortcutsPreferences();
        close();
        shortcutsTab.ifPresent(action -> seams.later().accept(() -> {
            if (!disposed) {
                action.actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED,
                        String.valueOf(action.getValue(Action.NAME))));
            }
        }));
    }

    /** The command of Preferencias that opens it on «Atajos de teclado» (one per tab, as the palette lists them). */
    private Optional<Action> shortcutsPreferences() {
        Action preferencesAction = view.shortcutActions().get(PREFERENCES_ID);
        if (preferencesAction == null || !(preferencesAction.getValue(Actions.PALETTE_ENTRIES) instanceof List<?> tabs)
                || tabs.size() <= PreferencesDialog.SHORTCUTS_TAB
                || !(tabs.get(PreferencesDialog.SHORTCUTS_TAB) instanceof Action shortcutsTab)) {
            return Optional.empty();
        }
        return Optional.of(shortcutsTab);
    }

    /** Takes the sheet away and gives the canvas the keyboard back. */
    private void close() {
        if (panel == null) {
            return;
        }
        panel = null;
        List<Runnable> undo = new ArrayList<>(whileOpen);
        whileOpen.clear();
        for (int index = undo.size() - 1; index >= 0; index--) {
            undo.get(index).run();
        }
        if (!disposed && canvasHadTheKeyboard) {
            view.keyboardFocus().request(view.canvas());
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        close();
        seams.dispatchers().remove(keyHook);
        removeEscapeClaims.run();
        view.menu(MenuId.HELP).remove(item);
    }
}
