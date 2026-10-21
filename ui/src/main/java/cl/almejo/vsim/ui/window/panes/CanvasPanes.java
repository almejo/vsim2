package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The canvas area of a window: 1, 2 or 4 panes (canvases of one circuit, each with its own view) and the dividers
 * between them, at most one per {@link Orientation}. The active pane is the one last pressed or focused; while there
 * are two or more panes it has a thin accent border (FlatLaf's focus colour, read at paint time). Removing a pane
 * here disposes it after the panes listeners heard of it. Changes of the split never touch the document. EDT only.
 */
public final class CanvasPanes extends JComponent {

    /** Which way a divider runs: {@link #HORIZONTAL} splits top and bottom, {@link #VERTICAL} side by side. */
    public enum Orientation { HORIZONTAL, VERTICAL }

    /** The side of a divider: {@link #BEFORE} is the top (or left) one, {@link #AFTER} the bottom (or right) one. */
    public enum Side { BEFORE, AFTER }

    /** A divider (or a grip's preview) released this close to an edge of the area goes away (does nothing). */
    static final int EDGE_MARGIN = 24;
    /** A dragged divider stays this far inside the area (as a fraction), so it is never quite at an edge. */
    private static final double MINIMUM_FRACTION = 0.001;

    /** The divider a grip is pulling, its middle at {@code position} (area pixels across the divider). */
    record SplitPreview(Orientation orientation, int position) {
    }

    private static final Border ACTIVE_BORDER = new ActivePaneBorder();

    private final Supplier<CircuitCanvas> newPane;
    private final Map<PanePosition, CircuitCanvas> panesByPosition = new EnumMap<>(PanePosition.class);
    private final Map<CircuitCanvas, Runnable> paneListenerRemovals = new IdentityHashMap<>();
    /** Views to show in panes that have no size yet (or must be shown again at their new size). */
    private final Map<CircuitCanvas, ViewState> pendingViews = new IdentityHashMap<>();
    private final List<Consumer<List<CircuitCanvas>>> panesListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CircuitCanvas>> activeListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> layoutListeners = new CopyOnWriteArrayList<>();
    private final PaneDivider horizontalDivider = new PaneDivider(Orientation.HORIZONTAL);
    private final PaneDivider verticalDivider = new PaneDivider(Orientation.VERTICAL);
    private OptionalDouble horizontalFraction = OptionalDouble.empty();
    private OptionalDouble verticalFraction = OptionalDouble.empty();
    private CircuitCanvas active;
    private SplitPreview splitPreview;
    private boolean disposed;

    /**
     * @param newPane makes the canvas of a new pane (the window configures it when its panes listener hears of it)
     * @param first   the only pane at the start
     */
    public CanvasPanes(Supplier<CircuitCanvas> newPane, CircuitCanvas first) {
        this.newPane = Objects.requireNonNull(newPane, "newPane");
        setLayout(null);
        panesByPosition.put(PanePosition.TOP_LEFT, first);
        active = first;
        add(first);
        attach(first);
        for (PaneDivider divider : List.of(horizontalDivider, verticalDivider)) {
            DividerDrag drag = new DividerDrag(divider);
            divider.addMouseListener(drag);
            divider.addMouseMotionListener(drag);
        }
    }

    /** Every pane in reading order (left to right, then top to bottom). */
    public List<CircuitCanvas> panes() {
        return List.copyOf(panesByPosition.values());
    }

    public CircuitCanvas active() {
        return active;
    }

    public Optional<CircuitCanvas> paneAt(PanePosition position) {
        return Optional.ofNullable(panesByPosition.get(position));
    }

    public Optional<PanePosition> positionOf(CircuitCanvas pane) {
        return panesByPosition.entrySet().stream().filter(entry -> entry.getValue() == pane)
                .map(Map.Entry::getKey).findFirst();
    }

    /** Where the divider of {@code orientation} is, as a fraction of the area; empty without it. */
    public OptionalDouble dividerFraction(Orientation orientation) {
        return orientation == Orientation.HORIZONTAL ? horizontalFraction : verticalFraction;
    }

    /** The divider component of {@code orientation} while the area has that divider. */
    public Optional<PaneDivider> divider(Orientation orientation) {
        return dividerFraction(orientation).isPresent() ? Optional.of(dividerOf(orientation)) : Optional.empty();
    }

    /** Makes {@code pane} the active one; nothing happens when it is not a pane of this area or already active. */
    public void activate(CircuitCanvas pane) {
        if (pane == active || positionOf(pane).isEmpty()) {
            return;
        }
        active = pane;
        updateBorders();
        activeListeners.forEach(listener -> listener.accept(pane));
    }

    /**
     * Adds the divider of {@code orientation} at {@code fraction} (0..1, exclusive) of the area; every pane it cuts in
     * two gets a new pane next to it showing the same view (zoom and centre). With the divider already there it just
     * moves. The active pane stays active.
     */
    public void split(Orientation orientation, double fraction) {
        checkFraction(fraction);
        if (dividerFraction(orientation).isPresent()) {
            moveDivider(orientation, fraction);
            return;
        }
        for (PanePosition source : List.copyOf(panesByPosition.keySet())) {
            CircuitCanvas sourcePane = panesByPosition.get(source);
            ViewState view = viewOf(sourcePane);
            CircuitCanvas pane = newPane.get();
            panesByPosition.put(across(source, orientation), pane);
            pendingViews.put(pane, view);
            pendingViews.put(sourcePane, view); // shown again at its new size, so both show the same centre
            add(pane);
            attach(pane);
        }
        setFraction(orientation, OptionalDouble.of(fraction));
        changed(List.of(), false);
    }

    /** Removes the divider of {@code orientation}, closing the panes after it (below it or right of it). */
    public void removeDivider(Orientation orientation) {
        removeDivider(orientation, Side.AFTER);
    }

    /**
     * Removes the divider of {@code orientation}, closing the panes on the {@code closing} side; the panes on the
     * other side take the whole space. The active pane stays active if it survives, else its neighbour across the
     * divider becomes active. Nothing happens without that divider.
     */
    public void removeDivider(Orientation orientation, Side closing) {
        if (dividerFraction(orientation).isEmpty()) {
            return;
        }
        Map<PanePosition, CircuitCanvas> before = new EnumMap<>(panesByPosition);
        List<CircuitCanvas> removed = new ArrayList<>();
        panesByPosition.clear();
        CircuitCanvas replacement = null;
        for (Map.Entry<PanePosition, CircuitCanvas> entry : before.entrySet()) {
            PanePosition position = entry.getKey();
            boolean afterSide = isAfter(position, orientation);
            boolean closes = (closing == Side.AFTER) == afterSide;
            if (closes) {
                removed.add(entry.getValue());
                if (entry.getValue() == active) {
                    replacement = before.get(across(position, orientation));
                }
            } else {
                panesByPosition.put(afterSide ? across(position, orientation) : position, entry.getValue());
            }
        }
        setFraction(orientation, OptionalDouble.empty());
        boolean activeChanged = replacement != null;
        if (activeChanged) {
            active = replacement;
        }
        changed(removed, activeChanged);
    }

    /** Removes both dividers; the active pane stays, alone, and the others close. */
    public void removeAll() {
        if (panesByPosition.size() == 1) {
            return;
        }
        List<CircuitCanvas> removed = panes().stream().filter(pane -> pane != active).toList();
        panesByPosition.clear();
        panesByPosition.put(PanePosition.TOP_LEFT, active);
        horizontalFraction = OptionalDouble.empty();
        verticalFraction = OptionalDouble.empty();
        changed(removed, false);
    }

    /** Moves the divider of {@code orientation} to {@code fraction} (0..1, exclusive) of the area. */
    public void moveDivider(Orientation orientation, double fraction) {
        checkFraction(fraction);
        if (dividerFraction(orientation).isEmpty()) {
            throw new IllegalStateException("there is no " + orientation + " divider to move");
        }
        setFraction(orientation, OptionalDouble.of(fraction));
        layOutNow();
        layoutListeners.forEach(Runnable::run);
    }

    /** The dividers and the view of every pane, in reading order. */
    public PaneLayout paneLayout() {
        return new PaneLayout(horizontalFraction, verticalFraction, panes().stream().map(this::viewOf).toList());
    }

    /**
     * Splits the area as {@code layout} says and shows its views (in panes that have no size yet, once they get one).
     * The current panes are kept in reading order as far as they go; the active pane stays active if it is kept,
     * else the first pane becomes active.
     */
    public void apply(PaneLayout layout) {
        List<PanePosition> positions = positionsFor(layout.horizontalDivider().isPresent(),
                layout.verticalDivider().isPresent());
        List<CircuitCanvas> current = panes();
        List<CircuitCanvas> removed = new ArrayList<>(current.subList(Math.min(positions.size(), current.size()),
                current.size()));
        panesByPosition.clear();
        for (int index = 0; index < positions.size(); index++) {
            CircuitCanvas pane;
            if (index < current.size()) {
                pane = current.get(index);
            } else {
                pane = newPane.get();
                add(pane);
                attach(pane);
            }
            panesByPosition.put(positions.get(index), pane);
            pendingViews.put(pane, layout.panes().get(index));
        }
        horizontalFraction = layout.horizontalDivider();
        verticalFraction = layout.verticalDivider();
        boolean activeChanged = removed.contains(active);
        if (activeChanged) {
            active = panesByPosition.get(PanePosition.TOP_LEFT);
        }
        changed(removed, activeChanged);
    }

    /** Heard with the new list of panes whenever panes are added or removed. Returns its removal. */
    public Runnable addPanesListener(Consumer<List<CircuitCanvas>> listener) {
        panesListeners.add(listener);
        return () -> panesListeners.remove(listener);
    }

    /** Heard with the new active pane whenever another pane becomes active. Returns its removal. */
    public Runnable addActiveListener(Consumer<CircuitCanvas> listener) {
        activeListeners.add(listener);
        return () -> activeListeners.remove(listener);
    }

    /** Heard whenever the split changes: panes added or removed, a divider moved. Returns its removal. */
    public Runnable addLayoutListener(Runnable listener) {
        layoutListeners.add(listener);
        return () -> layoutListeners.remove(listener);
    }

    /** Disposes every pane; the area is not used afterwards. Calling it again does nothing. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (CircuitCanvas pane : panes()) {
            detach(pane);
            pane.dispose();
        }
        pendingViews.clear();
    }

    // --- dragging: the grips' preview, the dividers --------------------------------------------------------------

    /** The divider a grip is pulling, while it is pulled. */
    Optional<SplitPreview> splitPreview() {
        return Optional.ofNullable(splitPreview);
    }

    /** Shows the bar a grip pulls, its middle at {@code position} of the area across it; it spans the whole area. */
    void showSplitPreview(Orientation orientation, int position) {
        splitPreview = new SplitPreview(orientation, position);
        repaint();
    }

    void hideSplitPreview() {
        if (splitPreview != null) {
            splitPreview = null;
            repaint();
        }
    }

    /** The area's length across a divider of {@code orientation}: its height for a horizontal one, else its width. */
    int lengthAcross(Orientation orientation) {
        return orientation == Orientation.HORIZONTAL ? getHeight() : getWidth();
    }

    /** True when {@code position} (across {@code orientation}) is within {@link #EDGE_MARGIN} of an edge. */
    boolean isNearAnEdge(Orientation orientation, int position) {
        return position < EDGE_MARGIN || position > lengthAcross(orientation) - EDGE_MARGIN;
    }

    /** The fraction that puts the middle of a divider of {@code orientation} at {@code position}, inside (0, 1). */
    double fractionFor(Orientation orientation, int position) {
        int thickness = PaneDivider.thickness();
        int available = Math.max(1, lengthAcross(orientation) - thickness);
        double fraction = (position - thickness / 2.0) / available;
        return Math.max(MINIMUM_FRACTION, Math.min(1 - MINIMUM_FRACTION, fraction));
    }

    /** While a grip pulls a preview the children are painted under it, so a pane repainting alone must not skip it. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return splitPreview == null && super.isOptimizedDrawingEnabled();
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);
        SplitPreview preview = splitPreview;
        if (preview == null) {
            return;
        }
        int thickness = PaneDivider.thickness();
        int start = preview.position() - thickness / 2;
        Rectangle bar = preview.orientation() == Orientation.HORIZONTAL
                ? new Rectangle(0, start, getWidth(), thickness)
                : new Rectangle(start, 0, thickness, getHeight());
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            PaneDivider.paintBar(canvas, preview.orientation(), bar, getBackground());
        } finally {
            canvas.dispose();
        }
    }

    /**
     * Drags a divider (canvas-edges §1, spec §2): continuously, the panes follow; at the crossing point both dividers
     * move; released within {@link #EDGE_MARGIN} of an edge the divider goes, closing the panes on that side; a
     * double-click removes it (both at the crossing point), closing the panes after it.
     */
    private final class DividerDrag extends MouseAdapter {

        private final PaneDivider divider;
        /** The dividers being dragged, and how far from each one's middle the pointer took it. */
        private final Map<Orientation, Integer> grabOffsets = new EnumMap<>(Orientation.class);

        DividerDrag(PaneDivider divider) {
            this.divider = divider;
        }

        /** {@code event}'s point in the area. */
        private Point inArea(MouseEvent event) {
            return new Point(divider.getX() + event.getX(), divider.getY() + event.getY());
        }

        private int along(Orientation orientation, Point point) {
            return orientation == Orientation.HORIZONTAL ? point.y : point.x;
        }

        /** The dividers under {@code point}: this one, and the other one too at the crossing point. */
        private List<Orientation> dividersAt(Point point) {
            Orientation other = divider.orientation() == Orientation.HORIZONTAL
                    ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            boolean crossing = divider(other).filter(found -> found.getBounds().contains(point)).isPresent();
            return crossing ? List.of(divider.orientation(), other) : List.of(divider.orientation());
        }

        private int middleOf(Orientation orientation) {
            Rectangle bounds = dividerOf(orientation).getBounds();
            return orientation == Orientation.HORIZONTAL ? bounds.y + bounds.height / 2 : bounds.x + bounds.width / 2;
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            divider.setCursor(dividersAt(inArea(event)).size() > 1
                    ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : PaneDivider.resizeCursor(divider.orientation()));
        }

        @Override
        public void mousePressed(MouseEvent event) {
            grabOffsets.clear();
            if (!SwingUtilities.isLeftMouseButton(event) || divider.getParent() != CanvasPanes.this) {
                return;
            }
            Point point = inArea(event);
            for (Orientation orientation : dividersAt(point)) {
                grabOffsets.put(orientation, along(orientation, point) - middleOf(orientation));
            }
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            Point point = inArea(event);
            grabOffsets.forEach((orientation, offset) -> {
                if (dividerFraction(orientation).isPresent()) {
                    moveDivider(orientation, fractionFor(orientation, along(orientation, point) - offset));
                }
            });
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (grabOffsets.isEmpty()) {
                return;
            }
            Point point = inArea(event);
            List<Orientation> dragged = List.copyOf(grabOffsets.keySet());
            grabOffsets.clear();
            for (Orientation orientation : dragged) {
                int position = along(orientation, point);
                if (dividerFraction(orientation).isPresent() && isNearAnEdge(orientation, position)) {
                    removeDivider(orientation, position < EDGE_MARGIN ? Side.BEFORE : Side.AFTER);
                }
            }
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1
                    && divider.getParent() == CanvasPanes.this) {
                dividersAt(inArea(event)).forEach(CanvasPanes.this::removeDivider);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        return panesByPosition.get(PanePosition.TOP_LEFT).getPreferredSize();
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        int thickness = PaneDivider.thickness();
        int leftWidth = verticalFraction.isPresent() ? before(verticalFraction.getAsDouble(), width, thickness) : width;
        int topHeight = horizontalFraction.isPresent()
                ? before(horizontalFraction.getAsDouble(), height, thickness) : height;
        int rightX = leftWidth + thickness;
        int bottomY = topHeight + thickness;
        for (Map.Entry<PanePosition, CircuitCanvas> entry : panesByPosition.entrySet()) {
            boolean right = entry.getKey() == PanePosition.TOP_RIGHT || entry.getKey() == PanePosition.BOTTOM_RIGHT;
            boolean bottom = entry.getKey() == PanePosition.BOTTOM_LEFT
                    || entry.getKey() == PanePosition.BOTTOM_RIGHT;
            int x = right ? rightX : 0;
            int y = bottom ? bottomY : 0;
            int paneWidth = right ? Math.max(0, width - rightX) : leftWidth;
            int paneHeight = bottom ? Math.max(0, height - bottomY) : topHeight;
            entry.getValue().setBounds(x, y, paneWidth, paneHeight);
        }
        if (horizontalFraction.isPresent()) {
            horizontalDivider.setBounds(0, topHeight, width, thickness);
        }
        if (verticalFraction.isPresent()) {
            verticalDivider.setBounds(leftWidth, 0, thickness, height);
        }
        showPendingViews();
    }

    /** The size of the part before a divider at {@code fraction} of {@code length}, leaving room for the divider. */
    private static int before(double fraction, int length, int thickness) {
        int available = Math.max(0, length - thickness);
        return (int) Math.max(0, Math.min(available, Math.round(fraction * available)));
    }

    private void showPendingViews() {
        for (CircuitCanvas pane : List.copyOf(pendingViews.keySet())) {
            if (pane.getWidth() > 0 && pane.getHeight() > 0) {
                pane.applyViewState(pendingViews.remove(pane));
            }
        }
    }

    /** The view {@code pane} shows, or will show once it has a size. */
    private ViewState viewOf(CircuitCanvas pane) {
        ViewState pending = pendingViews.get(pane);
        return pending != null ? pending : pane.viewState();
    }

    /**
     * After a change of the panes: puts the components in, lays them out, updates the borders and tells the
     * listeners; the removed panes are disposed last.
     */
    private void changed(List<CircuitCanvas> removed, boolean activeChanged) {
        boolean hadFocus = removed.stream().anyMatch(CircuitCanvas::isFocusOwner);
        for (CircuitCanvas pane : removed) {
            detach(pane);
            remove(pane);
            pendingViews.remove(pane);
        }
        syncDividers();
        layOutNow();
        updateBorders();
        if (activeChanged) {
            CircuitCanvas now = active;
            activeListeners.forEach(listener -> listener.accept(now));
        }
        layoutListeners.forEach(Runnable::run);
        List<CircuitCanvas> panes = panes();
        panesListeners.forEach(listener -> listener.accept(panes));
        removed.forEach(CircuitCanvas::dispose);
        if (hadFocus) {
            active.requestFocusInWindow();
        }
    }

    private void syncDividers() {
        for (Orientation orientation : Orientation.values()) {
            PaneDivider divider = dividerOf(orientation);
            boolean present = dividerFraction(orientation).isPresent();
            if (present && divider.getParent() != this) {
                add(divider, 0);
            } else if (!present && divider.getParent() == this) {
                remove(divider);
            }
        }
    }

    private void layOutNow() {
        revalidate();
        if (getWidth() > 0 && getHeight() > 0) {
            doLayout();
        }
        repaint();
    }

    private void updateBorders() {
        boolean split = panesByPosition.size() > 1;
        for (CircuitCanvas pane : panesByPosition.values()) {
            Border wanted = split && pane == active ? ACTIVE_BORDER : null;
            if (pane.getBorder() != wanted) {
                pane.setBorder(wanted);
            }
        }
    }

    private void attach(CircuitCanvas pane) {
        Runnable removePressObserver = pane.addPressObserver(() -> activate(pane));
        Runnable removeOtherPanes = pane.useOtherPanes(
                () -> panes().stream().filter(other -> other != pane).toList());
        FocusAdapter focusListener = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                activate(pane);
            }
        };
        pane.addFocusListener(focusListener);
        paneListenerRemovals.put(pane, () -> {
            removePressObserver.run();
            removeOtherPanes.run();
            pane.removeFocusListener(focusListener);
        });
    }

    private void detach(CircuitCanvas pane) {
        Runnable removal = paneListenerRemovals.remove(pane);
        if (removal != null) {
            removal.run();
        }
        if (pane.getBorder() != null) {
            pane.setBorder(null);
        }
    }

    private PaneDivider dividerOf(Orientation orientation) {
        return orientation == Orientation.HORIZONTAL ? horizontalDivider : verticalDivider;
    }

    private void setFraction(Orientation orientation, OptionalDouble fraction) {
        if (orientation == Orientation.HORIZONTAL) {
            horizontalFraction = fraction;
        } else {
            verticalFraction = fraction;
        }
    }

    /** True when {@code position} is after a divider of {@code orientation} (below it, or right of it). */
    private static boolean isAfter(PanePosition position, Orientation orientation) {
        return orientation == Orientation.HORIZONTAL
                ? position == PanePosition.BOTTOM_LEFT || position == PanePosition.BOTTOM_RIGHT
                : position == PanePosition.TOP_RIGHT || position == PanePosition.BOTTOM_RIGHT;
    }

    /** The position on the other side of a divider of {@code orientation}. */
    private static PanePosition across(PanePosition position, Orientation orientation) {
        return switch (position) {
            case TOP_LEFT -> orientation == Orientation.HORIZONTAL ? PanePosition.BOTTOM_LEFT : PanePosition.TOP_RIGHT;
            case TOP_RIGHT -> orientation == Orientation.HORIZONTAL ? PanePosition.BOTTOM_RIGHT : PanePosition.TOP_LEFT;
            case BOTTOM_LEFT -> orientation == Orientation.HORIZONTAL ? PanePosition.TOP_LEFT
                    : PanePosition.BOTTOM_RIGHT;
            case BOTTOM_RIGHT -> orientation == Orientation.HORIZONTAL ? PanePosition.TOP_RIGHT
                    : PanePosition.BOTTOM_LEFT;
        };
    }

    private static List<PanePosition> positionsFor(boolean horizontal, boolean vertical) {
        if (horizontal && vertical) {
            return List.of(PanePosition.values());
        }
        if (horizontal) {
            return List.of(PanePosition.TOP_LEFT, PanePosition.BOTTOM_LEFT);
        }
        if (vertical) {
            return List.of(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT);
        }
        return List.of(PanePosition.TOP_LEFT);
    }

    private static void checkFraction(double fraction) {
        if (!(fraction > 0 && fraction < 1)) {
            throw new IllegalArgumentException("a divider sits strictly inside the area: " + fraction);
        }
    }

    /** One pixel in FlatLaf's focus colour around the active pane, read at paint time (the theme changes live). */
    private static final class ActivePaneBorder implements Border {

        private static final Insets NONE = new Insets(0, 0, 0, 0);

        @Override
        public void paintBorder(java.awt.Component component, Graphics graphics, int x, int y, int width,
                                int height) {
            Color color = UIManager.getColor("Component.focusColor");
            graphics.setColor(color != null ? color : new Color(0x1E88E5));
            graphics.drawRect(x, y, width - 1, height - 1);
        }

        @Override
        public Insets getBorderInsets(java.awt.Component component) {
            return (Insets) NONE.clone();
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
