package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowTabs;

import javax.swing.JLayeredPane;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * «Modo presentación (pantalla completa)» of one circuit window (F11; the code says «full screen» because
 * {@code ai.present} already names the AI's «Presentar»): the window takes its whole monitor without the system's
 * title bar, and its bars go behind the screen edges ({@link EdgeBars}): the menu bar, the tab strip and the toolbar
 * at the top, each tab's left column on the left and its right panel with the section strip on the right; the status
 * bar never shows. On entering a notice says for 2 s how to leave; F11, Esc ({@link EscapeToLeave}), the Ver item and
 * the toolbar toggle leave. Leaving puts every bar back where it was and the window back where and how it was. The
 * circuit stays where it is on screen both ways ({@link CircuitAnchor}). Not remembered across restarts. EDT only.
 * <p>
 * The window's frame routes its menu bar through {@link #showMenuBar} (in the mode it hangs in the top bar) and hands
 * its views this object through {@code WindowCommands.fullScreen()}.
 */
public final class FullScreenFeatures {

    /** How long the notice on entering stays (the user, 2026-11-07). */
    static final Duration NOTICE_DURATION = Duration.ofSeconds(2);
    /** The bars' frames: as often as the canvas animates. */
    private static final int FRAME_MILLISECONDS = 16;
    /** The top bar sits just under the AI's floating «Presentar» panel (palette layer), which stays visible. */
    private static final Integer TOP_BAR_LAYER = JLayeredPane.PALETTE_LAYER - 1;

    private final JRootPane rootPane;
    private final WindowTabs tabs;
    private final ScreenMode screenMode;
    private final AppPreferences preferences;
    private final FrameTicker noticeTicker;
    private final LongSupplier clock;
    private final EdgeBars edgeBars;
    private final EscapeToLeave escape;
    private final KeyDispatchers keyHooks;
    private final CircuitAnchor anchor;
    private final JPanel topBar = new JPanel(new BorderLayout());
    private final Listeners<Runnable> activeListeners = new Listeners<>();
    /** The views laid out for the mode (the active one when entering, and each tab shown since), with their undoing. */
    private final Map<EditorView, Runnable> laidOutViews = new IdentityHashMap<>();
    private final Runnable stopFollowingTheTabs;
    /** The active tab's menu bar, on the frame or in the top bar. */
    private JMenuBar menuBar;
    private boolean active;
    private boolean disposed;
    private Runnable hideNotice = () -> {
    };
    private Runnable unregisterTopBar = () -> {
    };
    private Consumer<Component> focusMover = Component::requestFocusInWindow;

    /** Presentation mode of the window whose root pane is {@code rootPane}, with real timers. */
    public FullScreenFeatures(JRootPane rootPane, WindowTabs tabs, ScreenMode screenMode, AppPreferences preferences) {
        this(rootPane, tabs, screenMode, preferences, FrameTicker.swing(FRAME_MILLISECONDS),
                FrameTicker.swing(FRAME_MILLISECONDS), System::currentTimeMillis, KeyDispatchers.focusManager());
    }

    /**
     * @param barTicker    the frames of the bars' dwell, hide and slide
     * @param noticeTicker the frames of the notice on entering
     * @param clock        the milliseconds both read
     * @param keyHooks     where Esc's dispatcher and post-processor go while the mode is on
     */
    FullScreenFeatures(JRootPane rootPane, WindowTabs tabs, ScreenMode screenMode, AppPreferences preferences,
                       FrameTicker barTicker, FrameTicker noticeTicker, LongSupplier clock, KeyDispatchers keyHooks) {
        this.keyHooks = Objects.requireNonNull(keyHooks, "keyHooks");
        this.rootPane = Objects.requireNonNull(rootPane, "rootPane");
        this.tabs = Objects.requireNonNull(tabs, "tabs");
        this.screenMode = Objects.requireNonNull(screenMode, "screenMode");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.noticeTicker = Objects.requireNonNull(noticeTicker, "noticeTicker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.edgeBars = new EdgeBars(rootPane, barTicker, clock, () -> Animations.enabled(preferences));
        this.escape = new EscapeToLeave(rootPane, this::leave);
        this.anchor = new CircuitAnchor(rootPane, clock);
        this.menuBar = rootPane.getJMenuBar();
        topBar.setOpaque(true);
        topBar.setBorder(EdgeBars.innerLine(EdgeBars.Edge.TOP));
        this.stopFollowingTheTabs = tabs.model().addListener(this::activeTabChanged);
    }

    /** Whether the window is in presentation mode. */
    public boolean isActive() {
        return active;
    }

    /** {@code listener} hears the window entering and leaving the mode. Returns what stops it. */
    public Runnable addActiveListener(Runnable listener) {
        return activeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The bars behind this window's edges (task 5 puts the waveform panel on the bottom one). */
    public EdgeBars edgeBars() {
        return edgeBars;
    }

    /** F11, the Ver item, the toolbar toggle: in or out. */
    public void toggle() {
        if (active) {
            leave();
        } else {
            enter();
        }
    }

    /** Takes the whole monitor and puts the bars behind its edges, with the notice that says how to leave. */
    public void enter() {
        if (active || disposed || tabs.isEmpty()) {
            return;
        }
        active = true;
        EditorView view = tabs.activeView();
        anchor.hold(view.panes());
        screenMode.enter();
        rootPane.setJMenuBar(null);
        if (menuBar != null) {
            topBar.add(menuBar, BorderLayout.NORTH);
        }
        topBar.add(tabs.lendTopBars(), BorderLayout.CENTER);
        rootPane.getLayeredPane().add(topBar, TOP_BAR_LAYER);
        unregisterTopBar = edgeBars.register(EdgeBars.Edge.TOP, topBar, () -> false);
        layOut(view);
        edgeBars.startFollowing();
        keyHooks.add(escape);
        keyHooks.addPostProcessor(escape);
        settle();
        // the keys go to the circuit, not to what had them in a bar that just went behind its edge
        focusMover.accept(view.canvas());
        hideNotice = CanvasNotice.show(view.bottomDock(), noticeText(preferences), CanvasNotice.Position.TOP,
                NOTICE_DURATION, noticeTicker, clock, Animations.enabled(preferences));
        activeListeners.fire(Runnable::run);
    }

    /** Puts every bar back where it was and the window back where and how it was. */
    public void leave() {
        leave(false);
    }

    /**
     * Puts every bar back where it was; the window goes back where and how it was, or, when it is {@code closing},
     * only out of the full screen (it is going away: nothing to restore, no timer on it afterwards).
     */
    private void leave(boolean closing) {
        if (!active) {
            return;
        }
        active = false;
        hideNotice.run();
        keyHooks.remove(escape);
        keyHooks.removePostProcessor(escape);
        edgeBars.stopFollowing();
        edgeBars.hideAll();
        if (!tabs.isEmpty()) {
            anchor.hold(tabs.activeView().panes());
        }
        unregisterTopBar.run();
        rootPane.getLayeredPane().remove(topBar);
        topBar.removeAll();
        tabs.returnTopBars();
        rootPane.setJMenuBar(menuBar);
        List.copyOf(laidOutViews.values()).forEach(Runnable::run);
        laidOutViews.clear();
        if (!closing) {
            screenMode.leave();
        }
        settle();
        activeListeners.fire(Runnable::run);
    }

    /**
     * Hangs the active tab's menu bar: on the frame, or in the top bar while in the mode (the frame's tabs call this
     * instead of setting it on the frame themselves).
     */
    public void showMenuBar(JMenuBar shown) {
        if (active && menuBar != null) {
            topBar.remove(menuBar);
        }
        menuBar = shown;
        if (active) {
            if (shown != null) {
                topBar.add(shown, BorderLayout.NORTH);
            }
            topBar.revalidate();
            edgeBars.relayout();
        } else {
            rootPane.setJMenuBar(shown);
        }
        rootPane.revalidate();
        rootPane.repaint();
    }

    /**
     * The window is closing: its bars go back, it leaves the full screen without restoring its bounds (the screen
     * mode's timers and listeners go with it) and stops following its tabs.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        leave(true);
        screenMode.close();
        disposed = true;
        stopFollowingTheTabs.run();
        anchor.release();
        edgeBars.dispose();
    }

    /** How the focus is moved to the canvas on entering (tests record it; nothing has the focus headless). */
    void useFocusMover(Consumer<Component> mover) {
        this.focusMover = Objects.requireNonNull(mover, "mover");
    }

    /** What decides on Esc while the mode is on (tests hand it keys as the focus manager would). */
    EscapeToLeave escape() {
        return escape;
    }

    /** «Modo presentación · Esc o F11 para salir · …», with the shortcut the user gave the mode, if any. */
    static String noticeText(AppPreferences preferences) {
        Optional<KeyStroke> stroke = new KeyBindings(preferences).strokeFor(FullScreenExtension.SHORTCUT_ID);
        return stroke.map(shortcut -> Messages.format("fullScreen.notice", KeyBindings.describe(shortcut)))
                .orElseGet(() -> Messages.text("fullScreen.notice.noShortcut"));
    }

    /** A tab shown during the mode (switched to, opened, entered) is laid out for it too. */
    private void activeTabChanged() {
        if (!active || tabs.isEmpty()) {
            return;
        }
        forgetClosedViews();
        layOut(tabs.activeView());
        settle();
    }

    /** A tab closed during the mode: its bars leave the edges (its view is going away). */
    private void forgetClosedViews() {
        List<EditorView> open = tabs.views();
        for (EditorView view : List.copyOf(laidOutViews.keySet())) {
            if (!open.contains(view)) {
                laidOutViews.remove(view).run();
            }
        }
    }

    private void layOut(EditorView view) {
        if (!laidOutViews.containsKey(view)) {
            laidOutViews.put(view, view.layOutForFullScreen(edgeBars));
        }
    }

    /** Lays the window out now and keeps the circuit where it was on screen. */
    private void settle() {
        rootPane.revalidate();
        layOutNow(rootPane);
        edgeBars.relayout();
        anchor.follow();
        rootPane.repaint();
    }

    /**
     * Lays {@code component} and everything in it out at once, so the panes are already at their new places when the
     * anchor measures them (validate would wait for a shown frame; Swing validates as usual afterwards).
     */
    private static void layOutNow(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOutNow(child);
            }
        }
    }
}
