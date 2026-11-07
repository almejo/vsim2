package cl.almejo.vsim.ui.window.tabs;

import cl.almejo.vsim.ui.ai.AiRobotIcon;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.theme.ThemeColors;
import com.formdev.flatlaf.util.UIScale;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The strip of tabs of one window (spec §4.2–4.3), drawn by hand on a {@link TabStripModel}: groups in order, each
 * with its colour band on top; a main tab shows a document icon, its title, «●» when modified and ×; a component tab a
 * chip icon, a dashed band and ×; a folded main tab its «⬚ N» badge; the AI's robot ({@link TabMark}) takes the icon's
 * place on the tab where the AI works. When the tabs do not fit, ‹ › scroll them and
 * «▾» asks for the list of every tab. A press activates a tab, the middle button or × closes it, a double click on a
 * main tab folds its group, a click on the badge unfolds it. Dragging any tab drags its whole group: the drop lands in
 * this strip, in another window's strip, or outside every strip. Proportions follow the decided mockup (30 px tabs,
 * 2 px band); colours come from the FlatLaf theme and {@link GroupColors}, read at paint time. EDT only.
 */
public final class TabStrip extends JComponent implements Accessible {

    /** Where a dragged group was dropped. */
    public record GroupDrop(Object document, DropTarget target) {

        public GroupDrop {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(target, "target");
        }
    }

    /** The three places a group can be dropped. */
    public sealed interface DropTarget permits InStrip, OtherStrip, Outside {
    }

    /** In the same strip, so that the group ends at {@code groupIndex}. */
    public record InStrip(int groupIndex) implements DropTarget {
    }

    /** In another window's strip, at {@code groupIndex} among its groups. */
    public record OtherStrip(TabStrip strip, int groupIndex) implements DropTarget {
    }

    /** Outside every strip, at {@code screenPoint}: a new window. */
    public record Outside(Point screenPoint) implements DropTarget {
    }

    /** The strip's height: a 6 px margin above the 30 px tabs and the 1 px line below them. */
    public static final int HEIGHT = 37;
    private static final int TOP_PADDING = 6;
    private static final int TAB_HEIGHT = 30;
    private static final int LEFT_PADDING = 8;
    private static final int TAB_GAP = 3;
    private static final int GROUP_GAP = 10;
    private static final int TAB_PADDING_LEFT = 9;
    private static final int TAB_PADDING_RIGHT = 6;
    private static final int ICON_SIZE = 14;
    private static final int INNER_GAP = 6;
    private static final int CLOSE_SIZE = 14;
    private static final int DOT_SIZE = 7;
    private static final int BADGE_HEIGHT = 16;
    private static final int BADGE_PADDING = 6;
    private static final int MAXIMUM_TAB_WIDTH = 220;
    private static final int CORNER = 7;
    /** The group colour band on top of a tab: thick enough to tell the groups apart at a glance. */
    private static final int BAND = 4;
    /** How much of the group's colour the body of a tab takes (the active one more), so a group reads as one. */
    private static final double GROUP_TINT = 0.16;
    private static final double ACTIVE_GROUP_TINT = 0.24;
    private static final int BUTTON_WIDTH = 20;
    /** Scroll left, scroll right, «+» and the list: the buttons pinned at the right while the tabs overflow. */
    private static final int BUTTON_COUNT = 4;
    private static final int NEW_TAB_BUTTON_INDEX = 2;
    private static final int LIST_BUTTON_INDEX = 3;
    /** The «+» after the last tab (UI scale applied when used), and the side of its hover square and its arms. */
    private static final int NEW_TAB_WIDTH = 28;
    private static final int NEW_TAB_SIDE = 22;
    private static final int NEW_TAB_ARM = 5;
    private static final double NEW_TAB_HOVER_SHARE = 0.16;
    private static final double NEW_TAB_PRESSED_SHARE = 0.3;
    private static final int SCROLL_STEP = 120;
    /** Pixels the mouse moves with the button down before a press becomes a drag (spec §4.3 «6 px»). */
    private static final int DRAG_THRESHOLD = 6;
    /** A drop this close above or below the strip still counts as on the strip. */
    private static final int DROP_TOLERANCE = 12;
    /** How long folding and unfolding animate (the plan's resolution of spec §15). */
    private static final long FOLD_MILLISECONDS = 150;
    private static final int FRAME_MILLISECONDS = 15;
    private static final float GHOST_OPACITY = 0.7f;
    /** The working robot's bob (decided mockup 2026-11-06): one gentle rise and fall every 1.4 s, 1.5 px high. */
    private static final long BOB_PERIOD_MILLISECONDS = 1_400;
    private static final double BOB_HEIGHT = 1.5;
    private static final String ELLIPSIS = "…";

    /** The strips on screen, so a drag from one window finds the strip under the mouse in another. */
    private static final Set<TabStrip> SHOWING = Collections.newSetFromMap(new WeakHashMap<>());

    private record TabBox(TabStripModel.Tab tab, Rectangle bounds, Rectangle close, Optional<Rectangle> badge,
                          String shownTitle, int groupIndex) {
    }

    private record GroupSpan(Object document, int start, int end) {
        int center() {
            return (start + end) / 2;
        }
    }

    private record FoldMotion(boolean folding, long startMilliseconds) {
    }

    private sealed interface Hit permits TabHit, CloseHit, BadgeHit, ScrollLeft, ScrollRight, NewTabButton, ListButton, Nothing {
    }

    private record TabHit(TabBox box) implements Hit {
    }

    private record CloseHit(TabBox box) implements Hit {
    }

    private record BadgeHit(TabBox box) implements Hit {
    }

    private record ScrollLeft() implements Hit {
    }

    private record ScrollRight() implements Hit {
    }

    private record NewTabButton() implements Hit {
    }

    private record ListButton() implements Hit {
    }

    private record Nothing() implements Hit {
    }

    private final TabStripModel model;
    private final BooleanSupplier animate;
    private final FrameTicker ticker;
    private final LongSupplier clock;
    private Function<TabStripModel.Tab, Optional<TabMark>> marks = tab -> Optional.empty();
    private final Icon workingRobot = new AiRobotIcon(TabMark.Phase.WORKING, ICON_SIZE);
    private final Icon finishedRobot = new AiRobotIcon(TabMark.Phase.FINISHED, ICON_SIZE);
    private final Map<Object, Boolean> foldedSeen = new HashMap<>();
    private final Map<Object, FoldMotion> motions = new HashMap<>();
    private List<TabBox> boxes = List.of();
    private List<GroupSpan> spans = List.of();
    private boolean overflowing;
    private int scrollOffset;
    private boolean revealActive = true;
    private String lastActiveId;
    private Consumer<String> activateListener = tabId -> {
    };
    private Consumer<String> closeListener = tabId -> {
    };
    private Consumer<Object> foldListener = document -> {
    };
    private Consumer<GroupDrop> dropListener = drop -> {
    };
    private Runnable listListener = () -> {
    };
    private Runnable newTabListener = () -> {
    };
    private Supplier<Optional<KeyStroke>> newTabShortcut = Optional::empty;
    private Rectangle newTabBounds = new Rectangle();
    private boolean hoveredNewTab;
    private Hit pressed = new Nothing();
    private Point pressPoint;
    private TabTransfer drag;
    private JWindow ghost;
    private Point ghostOffset = new Point();
    private TabStrip indicatedStrip;
    private int dropSlot = -1;
    private Object dropExcluded;
    private String hoveredTabId;
    private boolean hoveredClose;
    private final Accessible newTabAccessible = new NewTabAccessible();

    /** @param animate whether folding animates now (the user's «Animación» preference, read each time) */
    public TabStrip(TabStripModel model, BooleanSupplier animate) {
        this(model, animate, FrameTicker.swing(FRAME_MILLISECONDS), () -> System.nanoTime() / 1_000_000);
    }

    /** With the animation's ticker and millisecond clock injected (tests drive them by hand). */
    TabStrip(TabStripModel model, BooleanSupplier animate, FrameTicker ticker, LongSupplier clock) {
        this.model = Objects.requireNonNull(model, "model");
        this.animate = animate;
        this.ticker = ticker;
        this.clock = clock;
        model.groups().forEach(group -> foldedSeen.put(group.document(), group.folded()));
        lastActiveId = model.active().map(TabStripModel.Tab::id).orElse(null);
        model.addListener(this::modelChanged);
        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
        setToolTipText("");
        setOpaque(true);
    }

    public void onActivate(Consumer<String> listener) {
        activateListener = Objects.requireNonNull(listener);
    }

    public void onClose(Consumer<String> listener) {
        closeListener = Objects.requireNonNull(listener);
    }

    public void onFoldToggle(Consumer<Object> listener) {
        foldListener = Objects.requireNonNull(listener);
    }

    public void onGroupDropped(Consumer<GroupDrop> listener) {
        dropListener = Objects.requireNonNull(listener);
    }

    public void onListRequested(Runnable listener) {
        listListener = Objects.requireNonNull(listener);
    }

    /** The «+» (or a double click on the empty strip) asks for a new circuit in a tab of this window. */
    public void onNewTabRequested(Runnable listener) {
        newTabListener = Objects.requireNonNull(listener);
    }

    /** Where the «+» tooltip reads the current shortcut of «Nuevo» (read at each tooltip, so a changed one shows). */
    public void setNewTabShortcut(Supplier<Optional<KeyStroke>> shortcut) {
        newTabShortcut = Objects.requireNonNull(shortcut, "shortcut");
    }

    /**
     * Where the strip reads the AI's mark of each tab (fix round D, item 5n); none until set. Call {@link #marksChanged}
     * whenever what it answers changes.
     */
    public void setMarks(Function<TabStripModel.Tab, Optional<TabMark>> marks) {
        this.marks = Objects.requireNonNull(marks, "marks");
        marksChanged();
    }

    /** The AI's marks changed: the robots are repainted, and bob while one works (and «Animación» is on). */
    public void marksChanged() {
        animateMarks();
        repaint();
    }

    /** The showing strip under {@code screenPoint} (with a few pixels of tolerance above and below), if any. */
    public static Optional<TabStrip> at(Point screenPoint) {
        for (TabStrip strip : List.copyOf(SHOWING)) {
            if (strip.isShowing() && strip.screenDropArea().contains(screenPoint)) {
                return Optional.of(strip);
            }
        }
        return Optional.empty();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SHOWING.add(this);
        animateMarks();
    }

    @Override
    public void removeNotify() {
        SHOWING.remove(this);
        endDrag();
        ticker.stop();
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.max(contentWidth() + newTabWidth(), LEFT_PADDING), HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(BUTTON_WIDTH * BUTTON_COUNT, HEIGHT);
    }

    @Override
    public void doLayout() {
        computeLayout();
    }

    // --- what tests and the window read ---

    Optional<Rectangle> boundsOf(String tabId) {
        return box(tabId).map(box -> new Rectangle(box.bounds()));
    }

    Optional<Rectangle> closeBoundsOf(String tabId) {
        return box(tabId).map(box -> new Rectangle(box.close()));
    }

    Optional<Rectangle> badgeBoundsOf(String tabId) {
        return box(tabId).flatMap(TabBox::badge).map(Rectangle::new);
    }

    Optional<Rectangle> listButtonBounds() {
        return overflowing ? Optional.of(buttonBounds(LIST_BUTTON_INDEX)) : Optional.empty();
    }

    /** Where the «+» is: after the last tab, or pinned next to the list button while the tabs overflow. */
    public Optional<Rectangle> newTabButtonBounds() {
        return Optional.of(new Rectangle(newTabBounds));
    }

    /** The part of the strip the tabs scroll in (all of it unless the scroll buttons show). */
    Rectangle tabArea() {
        return new Rectangle(0, 0, Math.max(0, getWidth() - (overflowing ? BUTTON_WIDTH * BUTTON_COUNT : 0)),
                getHeight());
    }

    boolean isOverflowing() {
        return overflowing;
    }

    Optional<TabTransfer> currentDrag() {
        return Optional.ofNullable(drag);
    }

    /** The AI's mark {@code tabId} shows now; empty for a hidden tab. */
    Optional<TabMark> shownMarkOf(String tabId) {
        return model.visibleTabs().stream().filter(tab -> tab.id().equals(tabId)).findFirst()
                .flatMap(this::shownMark);
    }

    /** How many pixels above its place the robot of {@code tabId} is drawn now (0 when it does not bob). */
    double markLift(String tabId) {
        return shownMarkOf(tabId).map(this::lift).orElse(0.0);
    }

    // --- layout ---

    private void modelChanged() {
        String activeId = model.active().map(TabStripModel.Tab::id).orElse(null);
        if (!Objects.equals(activeId, lastActiveId)) {
            lastActiveId = activeId;
            revealActive = true;
        }
        Map<Object, Boolean> foldedNow = new HashMap<>();
        for (TabStripModel.Group group : model.groups()) {
            foldedNow.put(group.document(), group.folded());
            Boolean before = foldedSeen.get(group.document());
            if (before != null && before != group.folded() && !group.componentTabs().isEmpty()
                    && animate.getAsBoolean()) {
                motions.put(group.document(), new FoldMotion(group.folded(), clock.getAsLong()));
                ticker.start(this::animationFrame);
            }
        }
        foldedSeen.clear();
        foldedSeen.putAll(foldedNow);
        motions.keySet().retainAll(foldedNow.keySet());
        computeLayout();
        revalidate();
        repaint();
        animateMarks();
    }

    /** One frame of the fold motions and of the working robots' bob; the ticker stops when neither moves. */
    private void animationFrame() {
        long now = clock.getAsLong();
        boolean folding = !motions.isEmpty();
        motions.values().removeIf(motion -> now - motion.startMilliseconds() >= FOLD_MILLISECONDS);
        if (motions.isEmpty() && !bobs()) {
            ticker.stop();
        }
        if (folding) {
            computeLayout();
            repaint();
            return;
        }
        boxes.stream().filter(box -> shownMark(box.tab()).filter(TabMark::isWorking).isPresent())
                .forEach(box -> repaint(box.bounds()));
    }

    // --- the AI's mark ---

    /** Starts the frames while a working robot shows and «Animación» is on; the frames stop themselves after. */
    private void animateMarks() {
        if (bobs()) {
            ticker.start(this::animationFrame);
        }
    }

    private boolean bobs() {
        return animate.getAsBoolean()
                && model.visibleTabs().stream().anyMatch(tab -> shownMark(tab).filter(TabMark::isWorking).isPresent());
    }

    /**
     * The mark {@code tab} shows: its own; a folded group's circuit tab also shows the strongest mark of the component
     * tabs it hides (the mark moves up to it).
     */
    private Optional<TabMark> shownMark(TabStripModel.Tab tab) {
        Optional<TabMark> own = marks.apply(tab);
        Optional<TabStripModel.Group> group = model.group(tab.document());
        if (group.isEmpty() || !group.get().folded() || !group.get().mainTabs().get(0).id().equals(tab.id())) {
            return own;
        }
        return TabMark.strongest(Stream.concat(Stream.of(own),
                group.get().componentTabs().stream().map(marks)));
    }

    /** Pixels above its place: a working robot rises and falls smoothly while «Animación» is on. */
    private double lift(TabMark mark) {
        if (!mark.isWorking() || !animate.getAsBoolean()) {
            return 0;
        }
        double phase = (clock.getAsLong() % BOB_PERIOD_MILLISECONDS) / (double) BOB_PERIOD_MILLISECONDS;
        return BOB_HEIGHT * (1 - Math.cos(2 * Math.PI * phase)) / 2;
    }

    /** How much of a component tab's width shows: 1 normally, less while its group folds or unfolds. */
    private double widthFactor(TabStripModel.Group group, TabStripModel.Tab tab) {
        FoldMotion motion = motions.get(group.document());
        if (motion == null || tab.isMain()) {
            return 1;
        }
        double progress = Math.min(1, Math.max(0,
                (clock.getAsLong() - motion.startMilliseconds()) / (double) FOLD_MILLISECONDS));
        double eased = progress * progress * (3 - 2 * progress);
        return motion.folding() ? 1 - eased : eased;
    }

    private List<TabStripModel.Tab> shownTabs(TabStripModel.Group group) {
        return motions.containsKey(group.document()) ? group.tabs() : group.visibleTabs();
    }

    private Font tabFont() {
        Font font = getFont();
        return font != null ? font : UIManager.getFont("Label.font");
    }

    private int contentWidth() {
        FontMetrics metrics = getFontMetrics(tabFont());
        int x = LEFT_PADDING;
        List<TabStripModel.Group> groups = model.groups();
        for (int index = 0; index < groups.size(); index++) {
            if (index > 0) {
                x += GROUP_GAP - TAB_GAP;
            }
            for (TabStripModel.Tab tab : shownTabs(groups.get(index))) {
                x += (int) Math.round(naturalWidth(tab, groups.get(index), metrics)
                        * widthFactor(groups.get(index), tab)) + TAB_GAP;
            }
        }
        return x;
    }

    private void computeLayout() {
        FontMetrics metrics = getFontMetrics(tabFont());
        int content = contentWidth();
        overflowing = content + newTabWidth() > getWidth();
        Rectangle area = tabArea();
        scrollOffset = Math.max(0, Math.min(scrollOffset, content - area.width));
        List<TabBox> laidOut = new ArrayList<>();
        List<GroupSpan> groupSpans = new ArrayList<>();
        List<TabStripModel.Group> groups = model.groups();
        int x = LEFT_PADDING;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            TabStripModel.Group group = groups.get(groupIndex);
            if (groupIndex > 0) {
                x += GROUP_GAP - TAB_GAP;
            }
            int start = x;
            for (TabStripModel.Tab tab : shownTabs(group)) {
                int natural = naturalWidth(tab, group, metrics);
                int width = (int) Math.round(natural * widthFactor(group, tab));
                if (width > 0) {
                    laidOut.add(box(tab, group, groupIndex, x, natural, width, metrics));
                }
                x += width + TAB_GAP;
            }
            groupSpans.add(new GroupSpan(group.document(), start, x - TAB_GAP));
        }
        if (revealActive) {
            revealActive = false;
            scrollToActive(laidOut, area, content);
        }
        newTabBounds = overflowing ? buttonBounds(NEW_TAB_BUTTON_INDEX)
                : new Rectangle(content, TOP_PADDING, newTabWidth(), TAB_HEIGHT);
        boxes = laidOut.stream().map(this::scrolled).toList();
        spans = groupSpans.stream()
                .map(span -> new GroupSpan(span.document(), span.start() - scrollOffset, span.end() - scrollOffset))
                .toList();
    }

    private void scrollToActive(List<TabBox> unscrolled, Rectangle area, int content) {
        Optional<String> activeId = model.active().map(TabStripModel.Tab::id);
        if (activeId.isEmpty() || area.width <= 0) {
            return;
        }
        for (TabBox box : unscrolled) {
            if (box.tab().id().equals(activeId.get())) {
                int left = box.bounds().x - LEFT_PADDING;
                int right = box.bounds().x + box.bounds().width + LEFT_PADDING;
                if (left < scrollOffset) {
                    scrollOffset = left;
                } else if (right > scrollOffset + area.width) {
                    scrollOffset = right - area.width;
                }
                scrollOffset = Math.max(0, Math.min(scrollOffset, content - area.width));
                return;
            }
        }
    }

    private TabBox scrolled(TabBox box) {
        return new TabBox(box.tab(), shifted(box.bounds()), shifted(box.close()), box.badge().map(this::shifted),
                box.shownTitle(), box.groupIndex());
    }

    private Rectangle shifted(Rectangle rectangle) {
        return new Rectangle(rectangle.x - scrollOffset, rectangle.y, rectangle.width, rectangle.height);
    }

    private int naturalWidth(TabStripModel.Tab tab, TabStripModel.Group group, FontMetrics metrics) {
        int fixed = fixedWidth(tab, group, metrics);
        return Math.min(MAXIMUM_TAB_WIDTH, fixed + metrics.stringWidth(tab.title()));
    }

    /** Everything of a tab but its title: paddings, icon, dot, badge and ×. */
    private int fixedWidth(TabStripModel.Tab tab, TabStripModel.Group group, FontMetrics metrics) {
        int width = TAB_PADDING_LEFT + ICON_SIZE + INNER_GAP + INNER_GAP + CLOSE_SIZE + TAB_PADDING_RIGHT;
        if (tab.isMain() && tab.modified()) {
            width += INNER_GAP + DOT_SIZE;
        }
        if (showsBadge(tab, group)) {
            width += INNER_GAP + badgeWidth(group, metrics);
        }
        return width;
    }

    private boolean showsBadge(TabStripModel.Tab tab, TabStripModel.Group group) {
        return tab.isMain() && group.folded() && !group.componentTabs().isEmpty()
                && group.mainTabs().get(0).id().equals(tab.id());
    }

    private static String badgeText(TabStripModel.Group group) {
        return Messages.format("tab.foldBadge", group.componentTabs().size());
    }

    private int badgeWidth(TabStripModel.Group group, FontMetrics metrics) {
        return metrics.stringWidth(badgeText(group)) + 2 * BADGE_PADDING;
    }

    private TabBox box(TabStripModel.Tab tab, TabStripModel.Group group, int groupIndex, int x, int natural,
                       int width, FontMetrics metrics) {
        Rectangle bounds = new Rectangle(x, TOP_PADDING, width, TAB_HEIGHT);
        int right = x + natural - TAB_PADDING_RIGHT;
        Rectangle close = new Rectangle(right - CLOSE_SIZE, TOP_PADDING + (TAB_HEIGHT - CLOSE_SIZE) / 2,
                CLOSE_SIZE, CLOSE_SIZE);
        Optional<Rectangle> badge = Optional.empty();
        if (showsBadge(tab, group)) {
            int badgeWidth = badgeWidth(group, metrics);
            badge = Optional.of(new Rectangle(close.x - INNER_GAP - badgeWidth,
                    TOP_PADDING + (TAB_HEIGHT - BADGE_HEIGHT) / 2, badgeWidth, BADGE_HEIGHT));
        }
        int titleRoom = natural - fixedWidth(tab, group, metrics);
        return new TabBox(tab, bounds, close, badge, fitted(tab.title(), titleRoom, metrics), groupIndex);
    }

    /** {@code title} cut with «…» to {@code room} pixels. */
    private static String fitted(String title, int room, FontMetrics metrics) {
        if (metrics.stringWidth(title) <= room) {
            return title;
        }
        for (int length = title.length() - 1; length > 0; length--) {
            String candidate = title.substring(0, length) + ELLIPSIS;
            if (metrics.stringWidth(candidate) <= room) {
                return candidate;
            }
        }
        return ELLIPSIS;
    }

    private Optional<TabBox> box(String tabId) {
        return boxes.stream().filter(box -> box.tab().id().equals(tabId)).findFirst();
    }

    private static int newTabWidth() {
        return UIScale.scale(NEW_TAB_WIDTH);
    }

    private Rectangle buttonBounds(int index) {
        int x = getWidth() - BUTTON_WIDTH * (BUTTON_COUNT - index);
        return new Rectangle(x, TOP_PADDING, BUTTON_WIDTH, TAB_HEIGHT);
    }

    private Hit hit(Point point) {
        if (overflowing) {
            if (buttonBounds(0).contains(point)) {
                return new ScrollLeft();
            }
            if (buttonBounds(1).contains(point)) {
                return new ScrollRight();
            }
            if (buttonBounds(NEW_TAB_BUTTON_INDEX).contains(point)) {
                return new NewTabButton();
            }
            if (buttonBounds(LIST_BUTTON_INDEX).contains(point)) {
                return new ListButton();
            }
        } else if (newTabBounds.contains(point)) {
            return new NewTabButton();
        }
        if (!tabArea().contains(point)) {
            return new Nothing();
        }
        for (TabBox box : boxes) {
            if (!box.bounds().contains(point)) {
                continue;
            }
            if (box.close().contains(point)) {
                return new CloseHit(box);
            }
            if (box.badge().filter(badge -> badge.contains(point)).isPresent()) {
                return new BadgeHit(box);
            }
            return new TabHit(box);
        }
        return new Nothing();
    }

    private void scrollBy(int pixels) {
        scrollOffset += pixels;
        computeLayout();
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return switch (hit(event.getPoint())) {
            case TabHit tabHit -> tooltipOf(tabHit.box().tab());
            case CloseHit closeHit -> tooltipOf(closeHit.box().tab());
            case BadgeHit badgeHit -> tooltipOf(badgeHit.box().tab());
            case ListButton listButton -> Messages.text("tab.list");
            case NewTabButton newTab -> newTabTooltip();
            default -> null;
        };
    }

    private String newTabTooltip() {
        return newTabShortcut.get().map(stroke -> Messages.format("tab.new.tooltip", KeyBindings.describe(stroke)))
                .orElse(Messages.text("tab.new"));
    }

    private String tooltipOf(TabStripModel.Tab tab) {
        Optional<TabMark> mark = shownMark(tab);
        if (mark.isPresent()) {
            return mark.get().tooltip();
        }
        if (tab.isMain()) {
            return Messages.format("tab.main.tooltip", tab.title());
        }
        String circuit = model.group(tab.document())
                .map(group -> group.mainTabs().get(0).title()).orElse("");
        return Messages.format("tab.component.tooltip", tab.title(), circuit);
    }

    // --- drop targets ---

    private Rectangle screenDropArea() {
        Point origin = getLocationOnScreen();
        return new Rectangle(origin.x, origin.y - DROP_TOLERANCE, getWidth(), getHeight() + 2 * DROP_TOLERANCE);
    }

    private boolean isOnThisStrip(Point point) {
        return point.x >= 0 && point.x < getWidth() && point.y >= -DROP_TOLERANCE
                && point.y < getHeight() + DROP_TOLERANCE;
    }

    /** The group index a drop at {@code x} gives, among the groups other than {@code excluded}. */
    private int slotAt(int x, Object excluded) {
        int slot = 0;
        for (GroupSpan span : spans) {
            if (!span.document().equals(excluded) && span.center() < x) {
                slot++;
            }
        }
        return slot;
    }

    private int slotX(int slot, Object excluded) {
        List<GroupSpan> others = spans.stream().filter(span -> !span.document().equals(excluded)).toList();
        if (others.isEmpty()) {
            return LEFT_PADDING / 2;
        }
        if (slot <= 0) {
            return others.get(0).start() - GROUP_GAP / 2;
        }
        return others.get(Math.min(slot, others.size()) - 1).end() + GROUP_GAP / 2;
    }

    private DropTarget dropTarget(MouseEvent event) {
        if (isOnThisStrip(event.getPoint())) {
            return new InStrip(slotAt(event.getX(), drag.document()));
        }
        Point screen = event.getLocationOnScreen();
        Optional<TabStrip> other = at(screen).filter(strip -> strip != this);
        if (other.isPresent()) {
            Point local = new Point(screen);
            SwingUtilities.convertPointFromScreen(local, other.get());
            return new OtherStrip(other.get(), other.get().slotAt(local.x, null));
        }
        return new Outside(screen);
    }

    private void showDropIndicator(DropTarget target) {
        TabStrip strip = switch (target) {
            case InStrip inStrip -> this;
            case OtherStrip otherStrip -> otherStrip.strip();
            case Outside outside -> null;
        };
        int slot = switch (target) {
            case InStrip inStrip -> inStrip.groupIndex();
            case OtherStrip otherStrip -> otherStrip.groupIndex();
            case Outside outside -> -1;
        };
        if (indicatedStrip != null && indicatedStrip != strip) {
            indicatedStrip.indicate(-1, null);
        }
        indicatedStrip = strip;
        if (strip != null) {
            strip.indicate(slot, strip == this ? drag.document() : null);
        }
    }

    private void indicate(int slot, Object excluded) {
        if (dropSlot != slot || !Objects.equals(dropExcluded, excluded)) {
            dropSlot = slot;
            dropExcluded = excluded;
            repaint();
        }
    }

    // --- drag ---

    private void startDrag(TabBox box, MouseEvent event) {
        drag = new TabTransfer(box.tab().document(), this);
        if (!GraphicsEnvironment.isHeadless()) {
            showGhost(box.tab().document(), event);
        }
    }

    private void endDrag() {
        drag = null;
        if (indicatedStrip != null) {
            indicatedStrip.indicate(-1, null);
            indicatedStrip = null;
        }
        if (ghost != null) {
            ghost.dispose();
            ghost = null;
        }
    }

    /** A translucent picture of the dragged group that follows the mouse (spec §4.3). */
    private void showGhost(Object document, MouseEvent event) {
        Optional<GroupSpan> span = spans.stream().filter(candidate -> candidate.document().equals(document))
                .findFirst();
        if (span.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int x = Math.max(0, span.get().start());
        int width = Math.min(getWidth(), span.get().end()) - x;
        if (width <= 0) {
            return;
        }
        BufferedImage strip = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = strip.createGraphics();
        paintTabs(graphics, Optional.of(document));
        graphics.dispose();
        BufferedImage picture = strip.getSubimage(x, TOP_PADDING, width, TAB_HEIGHT);
        ghost = new JWindow(SwingUtilities.getWindowAncestor(this));
        ghost.setFocusableWindowState(false);
        ghost.setContentPane(new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.drawImage(picture, 0, 0, null);
            }
        });
        ghost.setSize(width, TAB_HEIGHT);
        GraphicsDevice device = getGraphicsConfiguration() == null ? null : getGraphicsConfiguration().getDevice();
        if (device != null && device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            ghost.setOpacity(GHOST_OPACITY);
        }
        ghostOffset = new Point(pressPoint.x - x, pressPoint.y - TOP_PADDING);
        moveGhost(event);
        ghost.setVisible(true);
    }

    private void moveGhost(MouseEvent event) {
        if (ghost != null) {
            Point screen = event.getLocationOnScreen();
            ghost.setLocation(screen.x - ghostOffset.x, screen.y - ghostOffset.y);
        }
    }

    private final class MouseHandler extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent event) {
            pressed = hit(event.getPoint());
            pressPoint = event.getPoint();
            if (pressed instanceof NewTabButton) {
                hoveredNewTab = true;
                repaint(newTabBounds);
            }
            if (SwingUtilities.isLeftMouseButton(event) && pressed instanceof TabHit tabHit) {
                activateListener.accept(tabHit.box().tab().id());
            }
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (drag == null && pressed instanceof TabHit tabHit && SwingUtilities.isLeftMouseButton(event)
                    && pressPoint.distance(event.getPoint()) >= DRAG_THRESHOLD) {
                startDrag(tabHit.box(), event);
            }
            if (drag != null) {
                moveGhost(event);
                showDropIndicator(dropTarget(event));
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            Hit pressedHit = pressed;
            pressed = new Nothing();
            repaint(newTabBounds);
            if (drag != null) {
                Object document = drag.document();
                DropTarget target = dropTarget(event);
                endDrag();
                dropListener.accept(new GroupDrop(document, target));
                return;
            }
            Hit released = hit(event.getPoint());
            if (event.getButton() == MouseEvent.BUTTON2) {
                tabOf(released).filter(tab -> sameTab(pressedHit, released))
                        .ifPresent(tab -> closeListener.accept(tab.id()));
                return;
            }
            if (event.getButton() != MouseEvent.BUTTON1 || !sameTarget(pressedHit, released)) {
                return;
            }
            switch (released) {
                case CloseHit closeHit -> closeListener.accept(closeHit.box().tab().id());
                case BadgeHit badgeHit -> foldListener.accept(badgeHit.box().tab().document());
                case ScrollLeft left -> scrollBy(-SCROLL_STEP);
                case ScrollRight right -> scrollBy(SCROLL_STEP);
                case ListButton list -> listListener.run();
                case NewTabButton newTab -> newTabListener.run();
                default -> {
                }
            }
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2
                    && hit(event.getPoint()) instanceof TabHit tabHit && tabHit.box().tab().isMain()) {
                foldListener.accept(tabHit.box().tab().document());
            } else if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2
                    && hit(event.getPoint()) instanceof Nothing) {
                newTabListener.run();
            }
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            Hit hit = hit(event.getPoint());
            String tabId = tabOf(hit).map(TabStripModel.Tab::id).orElse(null);
            boolean close = hit instanceof CloseHit;
            boolean overNewTab = hit instanceof NewTabButton;
            if (!Objects.equals(tabId, hoveredTabId) || close != hoveredClose || overNewTab != hoveredNewTab) {
                hoveredTabId = tabId;
                hoveredClose = close;
                hoveredNewTab = overNewTab;
                repaint();
            }
        }

        @Override
        public void mouseExited(MouseEvent event) {
            if (hoveredTabId != null || hoveredNewTab) {
                hoveredTabId = null;
                hoveredClose = false;
                hoveredNewTab = false;
                repaint();
            }
        }
    }

    private static Optional<TabStripModel.Tab> tabOf(Hit hit) {
        return switch (hit) {
            case TabHit tabHit -> Optional.of(tabHit.box().tab());
            case CloseHit closeHit -> Optional.of(closeHit.box().tab());
            case BadgeHit badgeHit -> Optional.of(badgeHit.box().tab());
            default -> Optional.empty();
        };
    }

    private static boolean sameTab(Hit first, Hit second) {
        Optional<TabStripModel.Tab> one = tabOf(first);
        return one.isPresent() && one.map(TabStripModel.Tab::id).equals(tabOf(second).map(TabStripModel.Tab::id));
    }

    /** A click counts when the press and the release are on the same thing (the same tab's × or badge, a button). */
    private static boolean sameTarget(Hit first, Hit second) {
        return first.getClass() == second.getClass() && (tabOf(first).isEmpty() || sameTab(first, second));
    }

    // --- painting ---

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setColor(stripBackground());
            canvas.fillRect(0, 0, getWidth(), getHeight());
            paintTabs(canvas, Optional.empty());
            paintBottomLine(canvas);
            paintDropIndicator(canvas);
            if (overflowing) {
                paintButtons(canvas);
            } else {
                paintNewTab(canvas);
            }
        } finally {
            canvas.dispose();
        }
    }

    /** Every tab, or only those of {@code onlyDocument} (the drag's ghost). */
    private void paintTabs(Graphics2D graphics, Optional<Object> onlyDocument) {
        Shape previousClip = graphics.getClip();
        Rectangle area = tabArea();
        graphics.setFont(tabFont());
        String activeId = model.active().map(TabStripModel.Tab::id).orElse("");
        for (TabBox box : boxes) {
            if (onlyDocument.isPresent() && !onlyDocument.get().equals(box.tab().document())) {
                continue;
            }
            Rectangle clip = box.bounds().intersection(area);
            if (clip.isEmpty()) {
                continue;
            }
            graphics.setClip(previousClip);
            graphics.clip(clip);
            paintTab(graphics, box, box.tab().id().equals(activeId));
        }
        graphics.setClip(previousClip);
    }

    private void paintTab(Graphics2D graphics, TabBox box, boolean active) {
        Rectangle bounds = box.bounds();
        TabStripModel.Tab tab = box.tab();
        Color group = GroupColors.colorOf(tab.document());
        int naturalWidth = box.close().x + CLOSE_SIZE + TAB_PADDING_RIGHT - bounds.x;
        RoundRectangle2D shape = new RoundRectangle2D.Float(bounds.x, bounds.y, naturalWidth - 1,
                bounds.height + CORNER, 2 * CORNER, 2 * CORNER);
        graphics.setColor(mix(active ? activeFill() : inactiveFill(), group, active ? ACTIVE_GROUP_TINT : GROUP_TINT));
        graphics.fill(shape);
        graphics.setColor(border());
        graphics.draw(shape);
        graphics.setColor(group);
        int bandStart = bounds.x + CORNER / 2;
        int bandEnd = bounds.x + naturalWidth - 1 - CORNER / 2;
        if (tab.isMain()) {
            graphics.fillRect(bandStart, bounds.y + 1, bandEnd - bandStart, BAND);
        } else {
            for (int dash = bandStart; dash < bandEnd; dash += 7) { // dashed: a component, not the circuit itself
                graphics.fillRect(dash, bounds.y + 1, Math.min(4, bandEnd - dash), BAND);
            }
        }
        graphics.setStroke(new BasicStroke(1));
        Color text = active ? textColor() : mutedText();
        int iconX = bounds.x + TAB_PADDING_LEFT;
        int middle = bounds.y + bounds.height / 2;
        Optional<TabMark> mark = shownMark(tab);
        if (mark.isPresent()) {
            paintRobot(graphics, mark.get(), iconX, middle);
        } else if (tab.isMain()) {
            paintDocumentIcon(graphics, iconX, middle, text);
        } else {
            paintChipIcon(graphics, iconX, middle, group);
        }
        FontMetrics metrics = graphics.getFontMetrics();
        int textX = iconX + ICON_SIZE + INNER_GAP;
        int baseline = middle + (metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.setColor(text);
        graphics.drawString(box.shownTitle(), textX, baseline);
        if (tab.isMain() && tab.modified()) {
            int dotX = textX + metrics.stringWidth(box.shownTitle()) + INNER_GAP;
            graphics.setColor(group);
            graphics.fillOval(dotX, middle - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);
        }
        box.badge().ifPresent(badge -> paintBadge(graphics, badge, group, model.group(tab.document())));
        paintClose(graphics, box.close(), tab.id().equals(hoveredTabId) && hoveredClose, text);
    }

    /** The AI's robot in the place of the tab's icon (the tab keeps its width), lifted while it bobs. */
    private void paintRobot(Graphics2D graphics, TabMark mark, int x, int middle) {
        Graphics2D lifted = (Graphics2D) graphics.create();
        try {
            lifted.translate(0, -lift(mark));
            (mark.isWorking() ? workingRobot : finishedRobot).paintIcon(this, lifted, x, middle - ICON_SIZE / 2);
        } finally {
            lifted.dispose();
        }
    }

    private static void paintDocumentIcon(Graphics2D graphics, int x, int middle, Color color) {
        int top = middle - 6;
        Path2D page = new Path2D.Float();
        page.moveTo(x + 2, top);
        page.lineTo(x + 8, top);
        page.lineTo(x + 11, top + 3);
        page.lineTo(x + 11, top + 12);
        page.lineTo(x + 2, top + 12);
        page.closePath();
        graphics.setColor(color);
        graphics.draw(page);
        graphics.drawLine(x + 8, top, x + 8, top + 3);
        graphics.drawLine(x + 8, top + 3, x + 11, top + 3);
    }

    private static void paintChipIcon(Graphics2D graphics, int x, int middle, Color color) {
        graphics.setColor(color);
        graphics.drawRect(x + 2, middle - 4, 10, 8);
        for (int pin = 0; pin < 3; pin++) {
            int pinX = x + 4 + pin * 3;
            graphics.drawLine(pinX, middle - 6, pinX, middle - 5);
            graphics.drawLine(pinX, middle + 5, pinX, middle + 6);
        }
    }

    private void paintBadge(Graphics2D graphics, Rectangle badge, Color group,
                            Optional<TabStripModel.Group> tabGroup) {
        if (tabGroup.isEmpty()) {
            return;
        }
        graphics.setColor(group);
        graphics.fillRoundRect(badge.x, badge.y, badge.width, badge.height, badge.height, badge.height);
        graphics.setColor(activeFill());
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(badgeText(tabGroup.get()), badge.x + BADGE_PADDING,
                badge.y + (badge.height + metrics.getAscent() - metrics.getDescent()) / 2);
    }

    private void paintClose(Graphics2D graphics, Rectangle close, boolean hovered, Color text) {
        if (hovered) {
            graphics.setColor(border());
            graphics.fillRoundRect(close.x, close.y, close.width, close.height, 4, 4);
        }
        graphics.setColor(hovered ? textColor() : text);
        int inset = 4;
        graphics.drawLine(close.x + inset, close.y + inset, close.x + close.width - inset,
                close.y + close.height - inset);
        graphics.drawLine(close.x + close.width - inset, close.y + inset, close.x + inset,
                close.y + close.height - inset);
    }

    /** The line under the tabs, open under the active tab so it joins the editor below. */
    private void paintBottomLine(Graphics2D graphics) {
        int y = getHeight() - 1;
        graphics.setColor(border());
        Optional<Rectangle> active = model.active().flatMap(tab -> box(tab.id())).map(TabBox::bounds);
        if (active.isEmpty()) {
            graphics.drawLine(0, y, getWidth(), y);
            return;
        }
        graphics.drawLine(0, y, active.get().x, y);
        graphics.drawLine(active.get().x + active.get().width - 1, y, getWidth(), y);
    }

    private void paintDropIndicator(Graphics2D graphics) {
        if (dropSlot < 0) {
            return;
        }
        int x = slotX(dropSlot, dropExcluded);
        graphics.setColor(accent());
        graphics.fillRect(x - 1, TOP_PADDING, 2, TAB_HEIGHT);
    }

    private void paintButtons(Graphics2D graphics) {
        Rectangle first = buttonBounds(0);
        graphics.setColor(stripBackground());
        graphics.fillRect(first.x, 0, getWidth() - first.x, getHeight() - 1);
        graphics.setColor(border());
        graphics.drawLine(first.x, TOP_PADDING, first.x, TOP_PADDING + TAB_HEIGHT);
        graphics.setColor(textColor());
        graphics.setStroke(new BasicStroke(1.5f));
        Rectangle left = buttonBounds(0);
        int middle = left.y + left.height / 2;
        int leftCenter = left.x + left.width / 2;
        graphics.drawPolyline(new int[]{leftCenter + 2, leftCenter - 2, leftCenter + 2},
                new int[]{middle - 4, middle, middle + 4}, 3);
        Rectangle right = buttonBounds(1);
        int rightCenter = right.x + right.width / 2;
        graphics.drawPolyline(new int[]{rightCenter - 2, rightCenter + 2, rightCenter - 2},
                new int[]{middle - 4, middle, middle + 4}, 3);
        paintNewTab(graphics);
        graphics.setColor(textColor());
        graphics.setStroke(new BasicStroke(1.5f));
        Rectangle list = buttonBounds(LIST_BUTTON_INDEX);
        int listCenter = list.x + list.width / 2;
        graphics.fillPolygon(new int[]{listCenter - 4, listCenter + 4, listCenter},
                new int[]{middle - 2, middle - 2, middle + 3}, 3);
        graphics.setStroke(new BasicStroke(1));
    }

    /** The «+»: a plus in a square that lights on hover and darkens while pressed; colours read at paint time. */
    private void paintNewTab(Graphics2D graphics) {
        Rectangle bounds = newTabBounds;
        int side = Math.min(bounds.width - 2, UIScale.scale(NEW_TAB_SIDE));
        int squareX = bounds.x + (bounds.width - side) / 2;
        int squareY = bounds.y + (bounds.height - side) / 2;
        boolean isPressed = pressed instanceof NewTabButton && hoveredNewTab;
        if (hoveredNewTab || isPressed) {
            graphics.setColor(mix(stripBackground(), textColor(),
                    isPressed ? NEW_TAB_PRESSED_SHARE : NEW_TAB_HOVER_SHARE));
            graphics.fillRoundRect(squareX, squareY, side, side, UIScale.scale(CORNER), UIScale.scale(CORNER));
        }
        graphics.setColor(hoveredNewTab ? textColor() : mutedText());
        graphics.setStroke(new BasicStroke(UIScale.scale(1.5f)));
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        int arm = UIScale.scale(NEW_TAB_ARM);
        graphics.drawLine(centerX - arm, centerY, centerX + arm, centerY);
        graphics.drawLine(centerX, centerY - arm, centerX, centerY + arm);
        graphics.setStroke(new BasicStroke(1));
    }

    // --- accessibility: the «+» is the strip's one accessible child ---

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public int getAccessibleChildrenCount() {
                    return 1;
                }

                @Override
                public Accessible getAccessibleChild(int index) {
                    return index == 0 ? newTabAccessible : null;
                }
            };
        }
        return accessibleContext;
    }

    /** The «+» as a screen reader sees it: a push button named «Nuevo circuito» that does the same as a click. */
    private final class NewTabAccessible extends AccessibleContext implements Accessible, AccessibleAction {

        @Override
        public AccessibleContext getAccessibleContext() {
            return this;
        }

        @Override
        public String getAccessibleName() {
            return Messages.text("tab.new");
        }

        @Override
        public String getAccessibleDescription() {
            return newTabTooltip();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PUSH_BUTTON;
        }

        @Override
        public AccessibleStateSet getAccessibleStateSet() {
            return new AccessibleStateSet();
        }

        @Override
        public Accessible getAccessibleParent() {
            return TabStrip.this;
        }

        @Override
        public int getAccessibleIndexInParent() {
            return 0;
        }

        @Override
        public int getAccessibleChildrenCount() {
            return 0;
        }

        @Override
        public Accessible getAccessibleChild(int index) {
            return null;
        }

        @Override
        public Locale getLocale() {
            return TabStrip.this.getLocale();
        }

        @Override
        public AccessibleAction getAccessibleAction() {
            return this;
        }

        @Override
        public int getAccessibleActionCount() {
            return 1;
        }

        @Override
        public String getAccessibleActionDescription(int index) {
            return index == 0 ? Messages.text("tab.new") : null;
        }

        @Override
        public boolean doAccessibleAction(int index) {
            if (index != 0) {
                return false;
            }
            newTabListener.run();
            return true;
        }
    }

    // --- colours, read at paint time from the FlatLaf theme ---


    private static Color activeFill() {
        return ThemeColors.ui("Panel.background", Color.WHITE);
    }

    private static Color stripBackground() {
        Color panel = activeFill();
        return mix(panel, GroupColors.chromeIsDark() ? Color.BLACK : Color.GRAY, 0.18);
    }

    private static Color inactiveFill() {
        return mix(activeFill(), stripBackground(), 0.5);
    }

    private static Color border() {
        return ThemeColors.ui("Component.borderColor", ThemeColors.ui("Separator.foreground", Color.GRAY));
    }

    private static Color textColor() {
        return ThemeColors.ui("Label.foreground", Color.BLACK);
    }

    private static Color mutedText() {
        return ThemeColors.ui("Label.disabledForeground", mix(textColor(), activeFill(), 0.4));
    }

    private static Color accent() {
        return ThemeColors.ui("Component.focusColor", textColor());
    }

    private static Color mix(Color from, Color towards, double share) {
        return new Color((int) Math.round(from.getRed() + (towards.getRed() - from.getRed()) * share),
                (int) Math.round(from.getGreen() + (towards.getGreen() - from.getGreen()) * share),
                (int) Math.round(from.getBlue() + (towards.getBlue() - from.getBlue()) * share));
    }
}
