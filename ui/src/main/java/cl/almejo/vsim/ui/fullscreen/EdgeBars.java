package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The bars of a window in presentation mode, hidden behind the screen edges (the approved mockup of 2026-11-07):
 * the menu bar, tabs and toolbar at the top, the left column on the left, the right panel with its strip on the
 * right, the waveform panel at the bottom when open. A bar opens only after the pointer stays {@link #DWELL} at its
 * edge (crossing an edge on the way to a second monitor opens nothing), sliding in over {@link #SLIDE} <em>over</em>
 * the circuit, which never moves; it hides {@link #HIDE_DELAY} after the pointer leaves it, never while one of its
 * menus or popups is open or the focus is inside it (a field being typed in). The focus going into a hidden bar
 * (Alt+2 for the palette, Tab) or one of its menus opening (Alt+A) opens it at once.
 * <p>
 * A hidden bar stays visible, just placed beyond its edge: its menu accelerators and key bindings keep working, and a
 * picture of the window shows none of it. Each bar lives in a container without a layout (a layered pane) covering
 * the screen; this class places it there. A thin accent line grows on the edge while the pointer waits, as in the
 * mockup. Animations off: a bar appears and goes at once. EDT only.
 */
public final class EdgeBars {

    /** The four screen edges. */
    public enum Edge { TOP, LEFT, RIGHT, BOTTOM }

    /** How long the pointer stays at an edge before its bar opens (no accidental opening; the user, 2026-11-07). */
    public static final Duration DWELL = Duration.ofSeconds(1);
    /** How long a bar stays after the pointer left it (the mockup's 0.4 s). */
    public static final Duration HIDE_DELAY = Duration.ofMillis(400);
    /** How long a bar takes to slide all the way in or out (the mockup's 220 ms). */
    public static final Duration SLIDE = Duration.ofMillis(220);
    /** How long a {@linkplain #reveal revealed} bar stays before the hide delay starts (as long as a row's blink). */
    public static final Duration REVEAL_HOLD = Duration.ofMillis(1500);
    /**
     * How close to an edge (before UI scaling) the pointer counts as at it. In full screen the pointer stops at the
     * screen's edge, so a thin zone is enough and work near the edge of the circuit does not arm a bar.
     */
    static final int EDGE_ZONE = 4;
    /** The waiting line on the edge: its thickness and the share of the edge it reaches at the end of the dwell. */
    private static final int ARMING_LINE_THICKNESS = 3;
    private static final double ARMING_LINE_SHARE = 0.4;
    private static final int ARMING_LINE_ALPHA = 140;
    private static final Color FALLBACK_ACCENT = new Color(0x389FD6);

    private final JRootPane area;
    private final FrameTicker ticker;
    private final LongSupplier clock;
    private final BooleanSupplier animate;
    private final Map<Edge, EdgeState> states = new EnumMap<>(Edge.class);
    private final List<Registration> registrations = new ArrayList<>();
    private final ArmingLine armingLine = new ArmingLine();
    private Supplier<Component> focusOwner = () -> KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .getFocusOwner();
    /** Where the pointer is, in the area's coordinates; empty while it is outside the window. */
    private Optional<Point> pointer = Optional.empty();
    private Runnable stopFollowing = () -> {
    };

    /** One edge: waiting for the dwell, shown (fully or sliding), and since when the pointer is away from its bar. */
    private static final class EdgeState {
        private long armedSince = -1;
        private boolean open;
        private double shownFraction;
        private double slideFrom;
        private long slideStart = -1;
        private long awaySince = -1;
    }

    /** A bar on an edge, the condition that keeps it open, and what stops following its container's size. */
    private record Registration(Edge edge, JComponent bar, BooleanSupplier keepOpen, Container parent,
                                ComponentListener parentListener) {
    }

    /**
     * @param area    the window's root pane: its edges are the screen's in presentation mode, the pointer is measured
     *                in its coordinates and the waiting line goes on its layered pane
     * @param ticker  the frames of the dwell, hide and slide timings (a Swing timer, or a hand ticker in tests)
     * @param clock   the milliseconds those frames read
     * @param animate whether the bars slide ({@code Animations.enabled}), read at every slide
     */
    EdgeBars(JRootPane area, FrameTicker ticker, LongSupplier clock, BooleanSupplier animate) {
        this.area = Objects.requireNonNull(area, "area");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.animate = Objects.requireNonNull(animate, "animate");
        for (Edge edge : Edge.values()) {
            states.put(edge, new EdgeState());
        }
    }

    /**
     * Puts {@code bar} behind {@code edge}: it is placed beyond the edge in its parent (a container without a layout
     * covering the screen, which it must already be in) and slides in from there. {@code keepOpen} keeps it open
     * besides its own menus, popups and focus (the waveform panel's pin, for instance). Several bars may share an
     * edge (each tab's left column): only those whose tab shows open. Returns what takes the bar back (it stays
     * where it was placed; the caller puts it back in its usual place).
     */
    public Runnable register(Edge edge, JComponent bar, BooleanSupplier keepOpen) {
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(bar, "bar");
        Objects.requireNonNull(keepOpen, "keepOpen");
        Container parent = Objects.requireNonNull(bar.getParent(), "the bar must be in its container");
        ComponentListener parentListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                relayout();
            }
        };
        parent.addComponentListener(parentListener);
        Registration registration = new Registration(edge, bar, keepOpen, parent, parentListener);
        registrations.add(registration);
        place(registration);
        return () -> {
            if (registrations.remove(registration)) {
                parent.removeComponentListener(parentListener);
            }
        };
    }

    /**
     * Opens {@code edge}'s bars now, without the dwell, sliding in, and keeps them {@link #REVEAL_HOLD} before the
     * usual hiding: something the user did elsewhere put a bar there (Ver ▸ Formas de onda, a click on a probe
     * marker), and they see where it went.
     */
    public void reveal(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        open(edge);
        EdgeState state = states.get(edge);
        if (state.awaySince >= 0) {
            state.awaySince = clock.getAsLong() + REVEAL_HOLD.toMillis();
        }
        frame();
    }

    /**
     * Shows {@code edge}'s bars fully at once, with no slide: a bar that was on screen another way (the waveform panel
     * docked until its pin was removed) stays where it is, and hides as usual once the pointer leaves it.
     */
    public void showAtOnce(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        open(edge);
        EdgeState state = states.get(edge);
        state.shownFraction = 1;
        state.slideStart = -1;
        registrations.stream().filter(registration -> registration.edge() == edge).forEach(this::place);
        frame();
    }

    /** Whether {@code edge}'s bars are open or opening (sliding in). */
    public boolean isShown(Edge edge) {
        return states.get(edge).open;
    }

    /** Places every bar again (its container or its own wanted size changed: the right panel opened a section). */
    public void relayout() {
        List.copyOf(registrations).forEach(this::place);
    }

    /**
     * Starts following the pointer over the window, the focus and the menus (entering presentation mode); stopped by
     * {@link #stopFollowing()}. Pointer events of other windows (a dialog, a second monitor) count as outside.
     */
    void startFollowing() {
        stopFollowing.run();
        AWTEventListener pointerListener = event -> {
            if (event instanceof MouseEvent mouse) {
                pointerEvent(mouse);
            }
        };
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        toolkit.addAWTEventListener(pointerListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        PropertyChangeListener focusListener = event -> focusChanged();
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addPropertyChangeListener("focusOwner", focusListener);
        ChangeListener menuListener = event -> menusChanged();
        MenuSelectionManager.defaultManager().addChangeListener(menuListener);
        area.getLayeredPane().add(armingLine, JLayeredPane.DRAG_LAYER);
        stopFollowing = () -> {
            toolkit.removeAWTEventListener(pointerListener);
            focusManager.removePropertyChangeListener("focusOwner", focusListener);
            MenuSelectionManager.defaultManager().removeChangeListener(menuListener);
            if (armingLine.getParent() != null) {
                armingLine.getParent().remove(armingLine);
            }
        };
    }

    /** Stops what {@link #startFollowing()} started. */
    void stopFollowing() {
        stopFollowing.run();
        stopFollowing = () -> {
        };
    }

    private void pointerEvent(MouseEvent event) {
        Component source = event.getComponent();
        if (source == null || !SwingUtilities.isDescendingFrom(source, area)
                && SwingUtilities.getRoot(source) != SwingUtilities.getRoot(area)) {
            return; // another window: a dialog, a menu's own popup window
        }
        Point point = SwingUtilities.convertPoint(source, event.getPoint(), area);
        if (event.getID() == MouseEvent.MOUSE_EXITED && !new Rectangle(area.getSize()).contains(point)) {
            pointerLeft();
        } else if (event.getID() == MouseEvent.MOUSE_DRAGGED) {
            pointerDragged(point);
        } else {
            pointerAt(point);
        }
    }

    /** The pointer is at {@code point} of the area. */
    void pointerAt(Point point) {
        follow(point, true);
    }

    /**
     * The pointer drags to {@code point}: a drag that reaches an edge (a wire drawn to the border, the canvas
     * scrolling at its edge) never opens a bar; an open bar still hides when the drag leaves it.
     */
    void pointerDragged(Point point) {
        follow(point, false);
    }

    private void follow(Point point, boolean mayArm) {
        pointer = Optional.of(new Point(point));
        long now = clock.getAsLong();
        for (Edge edge : Edge.values()) {
            EdgeState state = states.get(edge);
            boolean atEdge = isAtEdge(edge, point) && hasShowingBar(edge);
            if (!state.open) {
                if (atEdge && mayArm && state.armedSince < 0) {
                    state.armedSince = now;
                } else if (!atEdge || !mayArm) {
                    state.armedSince = -1;
                }
            } else if (atEdge || isOverBar(edge, point)) {
                state.awaySince = -1;
            } else if (state.awaySince < 0) {
                state.awaySince = now;
            }
        }
        frame();
    }

    /** The pointer left the window (to a second monitor, or over another window). */
    void pointerLeft() {
        pointer = Optional.empty();
        long now = clock.getAsLong();
        for (EdgeState state : states.values()) {
            state.armedSince = -1;
            if (state.open && state.awaySince < 0) {
                state.awaySince = now;
            }
        }
        frame();
    }

    /** The focus moved: into a hidden bar it opens it at once (Alt+2, Tab). */
    void focusChanged() {
        Component owner = focusOwner.get();
        if (owner != null) {
            barHolding(owner).ifPresent(registration -> open(registration.edge()));
        }
        frame();
    }

    /** A menu opened or closed: one of a hidden bar's menus (Alt+A, F10) opens the bar at once. */
    private void menusChanged() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        if (path.length > 0 && path[0] instanceof Component root) {
            barHolding(root).ifPresent(registration -> open(registration.edge()));
        }
        frame();
    }

    /** Hides every bar at once (leaving the mode). */
    void hideAll() {
        for (EdgeState state : states.values()) {
            state.open = false;
            state.armedSince = -1;
            state.awaySince = -1;
            state.slideStart = -1;
            state.shownFraction = 0;
        }
        armingLine.setVisible(false);
        relayout();
        ticker.stop();
    }

    /** Where the focus is (the focus manager's owner; a fake in tests, where nothing has the focus). */
    void useFocusOwner(Supplier<Component> owner) {
        this.focusOwner = Objects.requireNonNull(owner, "owner");
    }

    /** Stops following and forgets every bar (the window is closing or leaving the mode). */
    void dispose() {
        stopFollowing();
        ticker.stop();
        for (Registration registration : List.copyOf(registrations)) {
            registration.parent().removeComponentListener(registration.parentListener());
        }
        registrations.clear();
    }

    private void open(Edge edge) {
        EdgeState state = states.get(edge);
        state.armedSince = -1;
        state.awaySince = isNear(edge) ? -1 : clock.getAsLong();
        if (!state.open) {
            state.open = true;
            startSlide(state);
        }
    }

    private void close(Edge edge) {
        EdgeState state = states.get(edge);
        state.open = false;
        state.awaySince = -1;
        startSlide(state);
    }

    private void startSlide(EdgeState state) {
        state.slideFrom = state.shownFraction;
        state.slideStart = clock.getAsLong();
    }

    /** One step of every timing: dwell, hide, slide; the ticker runs while one of them is pending. */
    private void frame() {
        long now = clock.getAsLong();
        boolean pending = false;
        for (Edge edge : Edge.values()) {
            EdgeState state = states.get(edge);
            if (!state.open && state.armedSince >= 0) {
                if (now - state.armedSince >= DWELL.toMillis()) {
                    open(edge);
                } else {
                    pending = true;
                }
            }
            if (state.open && isNear(edge)) {
                state.awaySince = -1; // resting at the edge, or on the bar once it slid in under the pointer
            }
            if (state.open && state.awaySince >= 0) {
                if (keepsOpen(edge)) {
                    state.awaySince = now; // re-checked every frame: a menu closed elsewhere lets it go later
                    pending = true;
                } else if (now - state.awaySince >= HIDE_DELAY.toMillis()) {
                    close(edge);
                } else {
                    pending = true;
                }
            }
            if (state.slideStart >= 0) {
                pending |= slide(edge, state, now);
            }
        }
        showArmingLine(now);
        if (pending) {
            ticker.start(this::frame);
        } else {
            ticker.stop();
        }
    }

    /** Moves {@code edge}'s bars one frame along their slide; true while it goes on. */
    private boolean slide(Edge edge, EdgeState state, long now) {
        double target = state.open ? 1 : 0;
        double distance = Math.abs(target - state.slideFrom);
        long duration = Math.round(SLIDE.toMillis() * distance);
        double progress = !animate.getAsBoolean() || duration <= 0 ? 1
                : Math.min(1, (double) (now - state.slideStart) / duration);
        state.shownFraction = state.slideFrom + (target - state.slideFrom) * easeOut(progress);
        if (progress >= 1) {
            state.shownFraction = target;
            state.slideStart = -1;
        }
        registrations.stream().filter(registration -> registration.edge() == edge).forEach(this::place);
        return state.slideStart >= 0;
    }

    /** Fast at first, settling at the end, close to the mockup's cubic-bezier(.2, .8, .2, 1). */
    private static double easeOut(double progress) {
        double remaining = 1 - progress;
        return 1 - remaining * remaining * remaining;
    }

    private boolean keepsOpen(Edge edge) {
        for (Registration registration : registrations) {
            if (registration.edge() == edge && isShowingInArea(registration.bar())
                    && (registration.keepOpen().getAsBoolean() || hasOpenMenu(registration.bar())
                    || hasFocus(registration.bar()) || hasOpenComboPopup(registration.bar()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFocus(JComponent bar) {
        Component owner = focusOwner.get();
        return owner != null && SwingUtilities.isDescendingFrom(owner, bar);
    }

    /** A menu of the bar's menu bar, or a popup whose invoker is in the bar (the split button's ▾), is open. */
    private static boolean hasOpenMenu(JComponent bar) {
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
            Component component = element.getComponent();
            if (component instanceof JPopupMenu popup && popup.getInvoker() != null
                    && SwingUtilities.isDescendingFrom(popup.getInvoker(), bar)) {
                return true;
            }
            if (component != null && SwingUtilities.isDescendingFrom(component, bar)) {
                return true;
            }
        }
        return false;
    }

    /** A combo box of the bar (the speed selector) shows its list. */
    private static boolean hasOpenComboPopup(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComboBox<?> combo && combo.isPopupVisible()) {
                return true;
            }
            if (child instanceof Container inner && hasOpenComboPopup(inner)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Registration> barHolding(Component component) {
        return registrations.stream()
                .filter(registration -> isShowingInArea(registration.bar())
                        && SwingUtilities.isDescendingFrom(component, registration.bar()))
                .findFirst();
    }

    private boolean hasShowingBar(Edge edge) {
        return registrations.stream()
                .anyMatch(registration -> registration.edge() == edge && isShowingInArea(registration.bar()));
    }

    /** Visible up to the area: a bar of a tab that is not shown (its card hidden) does not open. */
    private boolean isShowingInArea(Component component) {
        for (Component current = component; current != null && current != area; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return SwingUtilities.isDescendingFrom(component, area);
    }

    /** Whether the pointer is at {@code edge} or on one of its bars now. */
    private boolean isNear(Edge edge) {
        return pointer.isPresent() && (isAtEdge(edge, pointer.get()) || isOverBar(edge, pointer.get()));
    }

    private boolean isAtEdge(Edge edge, Point point) {
        int zone = UIScale.scale(EDGE_ZONE);
        return switch (edge) {
            case TOP -> point.y < zone;
            case LEFT -> point.x < zone;
            case RIGHT -> point.x >= area.getWidth() - zone;
            case BOTTOM -> point.y >= area.getHeight() - zone;
        };
    }

    private boolean isOverBar(Edge edge, Point point) {
        return registrations.stream()
                .filter(registration -> registration.edge() == edge && isShowingInArea(registration.bar()))
                .anyMatch(registration -> SwingUtilities.convertRectangle(registration.bar().getParent(),
                        registration.bar().getBounds(), area).contains(point));
    }

    /** Puts a bar where its edge's slide says, sized by its container (along the edge) and its own wanted size. */
    private void place(Registration registration) {
        JComponent bar = registration.bar();
        Container parent = bar.getParent();
        if (parent == null) {
            return;
        }
        double fraction = states.get(registration.edge()).shownFraction;
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        int wantedWidth = bar.getPreferredSize().width;
        int wantedHeight = bar.getPreferredSize().height;
        Rectangle bounds = switch (registration.edge()) {
            case TOP -> new Rectangle(0, (int) Math.round(-wantedHeight * (1 - fraction)), parentWidth,
                    wantedHeight);
            case BOTTOM -> new Rectangle(0, parentHeight - (int) Math.round(wantedHeight * fraction), parentWidth,
                    wantedHeight);
            case LEFT -> new Rectangle((int) Math.round(-wantedWidth * (1 - fraction)), 0, wantedWidth,
                    parentHeight);
            case RIGHT -> new Rectangle(parentWidth - (int) Math.round(wantedWidth * fraction), 0, wantedWidth,
                    parentHeight);
        };
        Rectangle before = bar.getBounds();
        if (bounds.equals(before)) {
            return;
        }
        bar.setBounds(bounds);
        if (bounds.width != before.width || bounds.height != before.height) {
            bar.validate(); // a slide only moves it; a new size lays its contents out again
        }
        Rectangle dirty = before.union(bounds);
        parent.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    /**
     * The line on the inner side of a bar behind {@code edge} (the bottom of the top bar, the right side of the left
     * one…), so it stands apart from the circuit it covers. Its colour is the theme's, read when it paints.
     */
    public static Border innerLine(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        return new Border() {
            @Override
            public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
                Color line = UIManager.getColor("Component.borderColor");
                graphics.setColor(line != null ? line : Color.GRAY);
                switch (edge) {
                    case TOP -> graphics.fillRect(x, y + height - 1, width, 1);
                    case BOTTOM -> graphics.fillRect(x, y, width, 1);
                    case LEFT -> graphics.fillRect(x + width - 1, y, 1, height);
                    case RIGHT -> graphics.fillRect(x, y, 1, height);
                }
            }

            @Override
            public Insets getBorderInsets(Component component) {
                return switch (edge) {
                    case TOP -> new Insets(0, 0, 1, 0);
                    case BOTTOM -> new Insets(1, 0, 0, 0);
                    case LEFT -> new Insets(0, 0, 0, 1);
                    case RIGHT -> new Insets(0, 1, 0, 0);
                };
            }

            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        };
    }

    /** The thin accent line that grows on an edge while the pointer waits there. */
    private void showArmingLine(long now) {
        for (Edge edge : Edge.values()) {
            EdgeState state = states.get(edge);
            if (!state.open && state.armedSince >= 0) {
                double progress = Math.min(1, (double) (now - state.armedSince) / DWELL.toMillis());
                armingLine.show(edge, progress, area.getWidth(), area.getHeight());
                return;
            }
        }
        armingLine.setVisible(false);
    }

    /** The waiting line; its colour is the theme's accent, read when it paints. */
    private static final class ArmingLine extends JComponent {

        ArmingLine() {
            setOpaque(false);
            setVisible(false);
        }

        void show(Edge edge, double progress, int width, int height) {
            int thickness = UIScale.scale(ARMING_LINE_THICKNESS);
            double share = ARMING_LINE_SHARE * progress;
            Rectangle bounds = switch (edge) {
                case TOP, BOTTOM -> {
                    int length = (int) Math.round(width * share);
                    yield new Rectangle((width - length) / 2, edge == Edge.TOP ? 0 : height - thickness, length,
                            thickness);
                }
                case LEFT, RIGHT -> {
                    int length = (int) Math.round(height * share);
                    yield new Rectangle(edge == Edge.LEFT ? 0 : width - thickness, (height - length) / 2, thickness,
                            length);
                }
            };
            setBounds(bounds);
            setVisible(true);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Color accent = UIManager.getColor("Component.accentColor");
            Color base = accent != null ? accent : FALLBACK_ACCENT;
            graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), ARMING_LINE_ALPHA));
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
