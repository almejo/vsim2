package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.toolbar.SplitButtonMode;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The screenshot of one editor view: a split button at the end of the toolbar whose main part copies a PNG picture of
 * the VSim window or of the visible circuit (the mode chosen in its ▾ and remembered) to the clipboard, and Editar ▸
 * «Copiar captura» ({@code edit.copyCapture}, right after «Copiar») that copies with that same mode. Each copy
 * flashes white over what was copied (not with animations off) and tells what happened in a notice at the bottom of
 * the circuit area. The picture is painted on the EDT by {@link WindowCapture} and handed to the clipboard without
 * touching the disk. {@link #dispose()} takes it all back.
 */
final class CaptureButtonFeatures {

    static final String PREFERENCE_ID = "capture";
    static final String SHORTCUT_ID = "edit.copyCapture";
    static final String WINDOW_MODE = "window";
    static final String VISIBLE_CIRCUIT_MODE = "visibleCircuit";
    private static final Duration NOTICE_DURATION = Duration.ofMillis(1800);
    private static final int FRAME_MILLISECONDS = 16;
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureButtonFeatures.class);

    private final EditorView view;
    private final ImageClipboard clipboard;
    private final Supplier<FrameTicker> tickers;
    private final LongSupplier clock;
    private final Predicate<AppPreferences> animations;
    private final CaptureButtonExtension.Dispatchers dispatchers;
    private final Predicate<Component> inOwnedWindow;
    private final KeyEventDispatcher dialogKeys = this::takeKey;
    private final Action copyAction;
    private final SplitButton button;
    private final Runnable toolbarRemover;
    private final Runnable menuRemover;
    private final AppPreferences preferences;
    /** The mode chosen in another tab's or window's ▾ shows in this one too. */
    private final Runnable modeFollower = this::followTheChosenMode;
    private Runnable flashRemover = () -> {
    };
    private Runnable noticeRemover = () -> {
    };
    private boolean disposed;

    CaptureButtonFeatures(EditorView view, ImageClipboard clipboard, Supplier<FrameTicker> tickers, LongSupplier clock,
                          Predicate<AppPreferences> animations, CaptureButtonExtension.Dispatchers dispatchers,
                          Predicate<Component> inOwnedWindow) {
        this.view = view;
        this.clipboard = clipboard;
        this.tickers = tickers;
        this.clock = clock;
        this.animations = animations;
        this.dispatchers = dispatchers;
        this.inOwnedWindow = inOwnedWindow != null ? inOwnedWindow : this::inWindowOwnedByTheViewsWindow;
        AppPreferences preferences = view.services().preferences();
        this.preferences = preferences;
        List<SplitButtonMode> modes = List.of(
                new SplitButtonMode(WINDOW_MODE, Icons.get(CaptureIcons.WINDOW, Icons.TOOLBAR_SIZE),
                        Messages.text("capture.mode.window"), Messages.text("capture.mode.window.description")),
                new SplitButtonMode(VISIBLE_CIRCUIT_MODE, Icons.get(CaptureIcons.VISIBLE_CIRCUIT, Icons.TOOLBAR_SIZE),
                        Messages.text("capture.mode.visibleCircuit"),
                        Messages.text("capture.mode.visibleCircuit.description")));
        this.button = SplitButton.forAction(PREFERENCE_ID, Messages.text("capture.menuTitle"), modes, preferences,
                mode -> copy(kindOf(mode.id())));
        Action action = Actions.of(Messages.text("action.copyCapture"), () -> copy(kindOf(button.mode().id())));
        this.copyAction = action;
        action.putValue(Action.SMALL_ICON, Icons.get(CaptureIcons.WINDOW, Icons.MENU_SIZE));
        int index = Menus.indexOf(view.menu(MenuId.EDIT), view.selectionActions().copy());
        this.menuRemover = Menus.install(view, MenuId.EDIT, index < 0 ? -1 : index + 1, action, SHORTCUT_ID);
        Runnable added;
        try {
            added = view.addToolbarComponent(button);
        } catch (RuntimeException failure) {
            menuRemover.run();
            throw failure;
        }
        this.toolbarRemover = added;
        dispatchers.add(dialogKeys);
        preferences.addListener(modeFollower);
    }

    private void followTheChosenMode() {
        preferences.splitButtonMode(PREFERENCE_ID)
                .filter(chosen -> WINDOW_MODE.equals(chosen) || VISIBLE_CIRCUIT_MODE.equals(chosen))
                .filter(chosen -> !chosen.equals(button.mode().id()))
                .ifPresent(button::setMode);
    }

    private static CaptureKind kindOf(String modeId) {
        return VISIBLE_CIRCUIT_MODE.equals(modeId) ? CaptureKind.VISIBLE_CIRCUIT : CaptureKind.WINDOW;
    }

    /** Copies {@code kind} of the view; whatever goes wrong is told in the notice, details in the log. */
    private void copy(CaptureKind kind) {
        if (disposed) {
            return;
        }
        // what a previous copy left over must not be in this picture
        flashRemover.run();
        noticeRemover.run();
        BufferedImage image;
        try {
            image = WindowCapture.paint(view, kind, WindowCapture.screenScale(view));
        } catch (RuntimeException failure) {
            LOGGER.warn("The capture could not be painted", failure);
            showNotice(Messages.text("capture.copy.notPainted"));
            return;
        }
        try {
            clipboard.copy(image);
        } catch (RuntimeException failure) {
            LOGGER.warn("The capture could not be copied to the clipboard", failure);
            showNotice(Messages.text("capture.copy.failed"));
            return;
        }
        if (animations.test(view.services().preferences())) {
            flashRemover = CaptureFlash.show(areaOf(kind), tickers.get(), clock);
        }
        showNotice(Messages.text(kind == CaptureKind.WINDOW ? "capture.copied.window"
                : "capture.copied.visibleCircuit"));
    }

    private void showNotice(String text) {
        boolean fade = animations.test(view.services().preferences());
        noticeRemover = CanvasNotice.show(view.canvasPanes(), text, CanvasNotice.Position.BOTTOM, NOTICE_DURATION,
                tickers.get(), clock, fade);
    }

    /** What was copied, to flash over it: the whole window, or the panes. */
    private JComponent areaOf(CaptureKind kind) {
        if (kind == CaptureKind.VISIBLE_CIRCUIT) {
            return view.canvasPanes();
        }
        JComponent root = SwingUtilities.getRootPane(view);
        return root == null ? view : root;
    }

    /**
     * The shortcut from a dialog: menu accelerators only work in the editor's own window, so a key the user bound to
     * «Copiar captura» (its current accelerator, which follows the shortcut editor) pressed in a dialog owned by this
     * view's window copies the window, dialogs included. Each tab has its dispatcher: only the tab that shows in its
     * window copies. Not while a part of the circuit has the keys (the Terminal).
     */
    private boolean takeKey(KeyEvent event) {
        if (disposed || event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()
                || !(event.getSource() instanceof Component source)
                || !(copyAction.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke stroke)
                || !stroke.equals(KeyStroke.getKeyStrokeForEvent(event))
                || view.keyboardCapture().captured().isPresent() || !isTheShownTab()
                || !inOwnedWindow.test(source)) {
            return false;
        }
        event.consume();
        copy(kindOf(button.mode().id()));
        return true;
    }

    /** Visible all the way up to its root pane: the tab that shows in its window (the others' cards are hidden). */
    private boolean isTheShownTab() {
        JRootPane root = SwingUtilities.getRootPane(view);
        for (Component current = view; current != null && current != root; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return root != null;
    }

    private boolean inWindowOwnedByTheViewsWindow(Component source) {
        Window own = SwingUtilities.getWindowAncestor(view);
        Window window = source instanceof Window itself ? itself : SwingUtilities.getWindowAncestor(source);
        if (own == null || window == null || window == own) {
            return false;
        }
        for (Window owner = window.getOwner(); owner != null; owner = owner.getOwner()) {
            if (owner == own) {
                return true;
            }
        }
        return false;
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        preferences.removeListener(modeFollower);
        dispatchers.remove(dialogKeys);
        flashRemover.run();
        noticeRemover.run();
        toolbarRemover.run();
        menuRemover.run();
    }

    /** The frame rate of the flash and the notice. */
    static FrameTicker swingTicker() {
        return FrameTicker.swing(FRAME_MILLISECONDS);
    }
}
