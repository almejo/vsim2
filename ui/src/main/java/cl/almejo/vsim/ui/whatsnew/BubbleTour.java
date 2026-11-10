package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.whatsnew.NewsTargets.Side;
import cl.almejo.vsim.ui.window.EditorView;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One showing of a release's bubbles over a view's window (mockup v4): one bubble at a time, pointing at its target
 * over the dimmed window, in the file's order; a target not shown now is skipped and not counted («n de m» counts the
 * shown ones), and one that goes away while its bubble shows passes to the next. ‹ › (← →, Enter) walk them, › on the
 * last and «Listo» (Esc) end the tour, and so does starting to edit (a change of the circuit, a press on it) or
 * entering presentation mode; the window closing takes it away without ending it ({@link #close}). The bubble slides
 * from target to target, or jumps with «Animaciones: No». EDT only.
 */
final class BubbleTour {

    /** Above the menu bar, the content and the floating palettes; under modal dialogs, popups and drags. */
    static final Integer LAYER = JLayeredPane.PALETTE_LAYER + 50;
    /** The bubble's distance from its target: the arrow and a little air. */
    private static final int GAP = TourOverlay.ARROW + 4;
    private static final int MARGIN = 8;
    private static final long SLIDE_MILLISECONDS = 250;
    private static final int NO_MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private final EditorView view;
    private final List<NewsStep> steps;
    private final Runnable ended;
    private final BubbleTours.Seams seams;
    private final AppPreferences preferences;
    private final KeyEventDispatcher keyHook = this::keyEvent;
    private final List<Runnable> whileOpen = new ArrayList<>();
    private final Runnable targetsChanged = this::targetsChanged;
    private NewsBubble bubble;
    private TourOverlay overlay;
    private JLayeredPane layers;
    private int current = -1;
    private Runnable stopFollowingTheTarget = () -> {
    };
    private FrameTicker ticker;
    private Rectangle destination = new Rectangle();
    private boolean canvasHadTheKeyboard = true;

    BubbleTour(EditorView view, NewsFile news, Runnable ended, BubbleTours.Seams seams) {
        this.view = view;
        this.steps = news.steps();
        this.ended = ended;
        this.seams = seams;
        this.preferences = view.services().preferences();
    }

    boolean isOpen() {
        return overlay != null;
    }

    /** Shows the first bubble whose target is shown; nothing when none is. */
    void open() {
        JRootPane root = SwingUtilities.getRootPane(view);
        OptionalInt first = shownAfter(-1);
        if (isOpen() || root == null || first.isEmpty()) {
            return;
        }
        canvasHadTheKeyboard = view.keyboardFocus().owner()
                .map(owner -> SwingUtilities.isDescendingFrom(owner, view.canvas())).orElse(true);
        layers = root.getLayeredPane();
        bubble = new NewsBubble(this::done, this::previous, this::next);
        overlay = new TourOverlay(bubble);
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.add(overlay, LAYER);
        whileOpen.add(() -> {
            layers.remove(overlay);
            layers.repaint();
        });
        listen();
        show(first.getAsInt(), false);
    }

    // --- walking the bubbles ---

    void next() {
        if (!isOpen()) {
            return;
        }
        OptionalInt following = shownAfter(current);
        if (following.isPresent()) {
            show(following.getAsInt(), true);
        } else {
            finish();
        }
    }

    void previous() {
        if (isOpen()) {
            shownBefore(current).ifPresent(index -> show(index, true));
        }
    }

    /** «Listo»: the tour ends, as after the last bubble. */
    void done() {
        if (isOpen()) {
            finish();
        }
    }

    /** The window or a target moved, resized, appeared or went away: follow it, or pass to the next bubble. */
    void targetsChanged() {
        if (!isOpen()) {
            return;
        }
        if (view.fullScreenStage().isPresent()) {
            finish(); // presentation mode: the screen belongs to the class now
            return;
        }
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        if (isShown(current)) {
            show(current, false);
            return;
        }
        OptionalInt following = shownAfter(current);
        if (following.isPresent()) {
            show(following.getAsInt(), false);
        } else {
            finish();
        }
    }

    private void finish() {
        close();
        if (canvasHadTheKeyboard) {
            view.keyboardFocus().request(view.canvas());
        }
        ended.run();
    }

    /** Takes the tour away without ending it (the window closes; another tour replaces it). */
    void close() {
        if (!isOpen()) {
            return;
        }
        stopFollowingTheTarget.run();
        stopFollowingTheTarget = () -> {
        };
        List<Runnable> undo = new ArrayList<>(whileOpen);
        whileOpen.clear();
        for (int index = undo.size() - 1; index >= 0; index--) {
            undo.get(index).run();
        }
        overlay = null;
        bubble = null;
    }

    // --- what tests read ---

    String title() {
        return bubble == null ? "" : bubble.content().title();
    }

    String count() {
        return bubble == null ? "" : bubble.content().count();
    }

    boolean previousEnabled() {
        return bubble != null && bubble.content().previousEnabled();
    }

    Optional<BubbleLine> line() {
        return isOpen() ? BubbleLine.of(view, steps.get(current).action()) : Optional.empty();
    }

    /** The keys the current bubble's text draws as keycaps, in order. */
    List<List<String>> textKeys() {
        if (bubble == null) {
            return List.of();
        }
        return bubble.content().text().items().stream().filter(FlowText.Keys.class::isInstance)
                .map(item -> ((FlowText.Keys) item).keys()).toList();
    }

    Optional<Side> side() {
        return overlay == null ? Optional.empty() : overlay.side();
    }

    Rectangle bubbleBounds() {
        return overlay == null ? new Rectangle() : overlay.bubbleBounds();
    }

    /** Where the bubble is going (where it is, once a slide is over). */
    Rectangle destination() {
        return new Rectangle(destination);
    }

    // --- one bubble ---

    private void show(int index, boolean slide) {
        boolean sameBubble = index == current;
        current = index;
        JComponent target = target(index).orElseThrow();
        followTarget(target, sameBubble);
        List<Integer> shown = shownIndices();
        int position = shown.indexOf(index);
        bubble.show(new NewsBubble.Content(Messages.text(steps.get(index).titleKey()),
                new FlowText(textItems(steps.get(index))), lineFlow(steps.get(index)),
                Messages.format("whatsNew.count", position + 1, shown.size()), position > 0,
                position == shown.size() - 1));
        Rectangle targetBounds = SwingUtilities.convertRectangle(target, target.getVisibleRect(), overlay);
        int width = UIScale.scale(NewsBubble.WIDTH);
        Dimension size = new Dimension(width, bubble.heightFor(Math.min(width,
                layers.getWidth() - 2 * UIScale.scale(MARGIN))));
        BubblePlacement.Placement placement = BubblePlacement.place(targetBounds, size, freeArea(),
                NewsTargets.sideOf(target), UIScale.scale(GAP), UIScale.scale(MARGIN));
        overlay.pointAt(targetBounds, placement.side());
        destination = placement.bounds();
        if (slide && seams.animations().test(preferences) && !bubble.getBounds().isEmpty()) {
            slideTo(destination);
        } else {
            stopSliding();
            overlay.moveBubble(destination);
        }
    }

    /** The window under its menu bar: a bubble never covers the menus. */
    private Rectangle freeArea() {
        JRootPane root = SwingUtilities.getRootPane(view);
        int top = root != null && root.getJMenuBar() != null && root.getJMenuBar().isVisible()
                ? SwingUtilities.convertRectangle(root.getJMenuBar().getParent(), root.getJMenuBar().getBounds(),
                layers).y + root.getJMenuBar().getHeight() : 0;
        return new Rectangle(0, top, layers.getWidth(), Math.max(0, layers.getHeight() - top));
    }

    private void slideTo(Rectangle target) {
        Point from = bubble.getLocation();
        overlay.moveBubble(new Rectangle(from, target.getSize()));
        if (ticker == null) {
            ticker = seams.tickers().get();
        }
        long start = seams.clock().getAsLong();
        ticker.start(() -> {
            if (!isOpen()) {
                ticker.stop();
                return;
            }
            double progress = Math.min(1, (seams.clock().getAsLong() - start) / (double) SLIDE_MILLISECONDS);
            if (progress >= 1 || !seams.animations().test(preferences)) {
                overlay.moveBubble(target);
                ticker.stop();
                return;
            }
            double eased = 1 - Math.pow(1 - progress, 3);
            overlay.moveBubble(new Rectangle((int) Math.round(from.x + (target.x - from.x) * eased),
                    (int) Math.round(from.y + (target.y - from.y) * eased), target.width, target.height));
        });
    }

    private void stopSliding() {
        if (ticker != null) {
            ticker.stop();
        }
    }

    /** The text with its keys: a gesture key or an action's current key; an action without one, by its name. */
    private List<FlowText.Item> textItems(NewsStep step) {
        KeyBindings bindings = new KeyBindings(preferences);
        List<FlowText.Item> items = new ArrayList<>();
        for (NewsText.Part part : NewsText.parse(Messages.text(step.textKey()))) {
            switch (part) {
                case NewsText.Plain plain -> items.add(new FlowText.Words(plain.text()));
                case NewsText.Key key -> items.add(NewsText.strokeOf(key.name(), bindings)
                        .<FlowText.Item>map(stroke -> new FlowText.Keys(KeyNames.keys(stroke, KeyNames.MAC_NOTATION)))
                        .orElseGet(() -> new FlowText.Words(ShortcutCatalog.find(key.name())
                                .map(ShortcutEntry::nameKey).map(Messages::text).orElse(key.name()))));
            }
        }
        return items;
    }

    private Optional<FlowText> lineFlow(NewsStep step) {
        return BubbleLine.of(view, step.action()).map(line -> switch (line) {
            case BubbleLine.Shortcut shortcut -> new FlowText(List.of(
                    new FlowText.Words(Messages.text("whatsNew.shortcut") + " "), new FlowText.Keys(shortcut.keys())));
            case BubbleLine.MenuPath path -> new FlowText(List.of(
                    new FlowText.Words(Messages.text("whatsNew.inMenu") + " " + path.path())));
        });
    }

    // --- targets ---

    private Optional<JComponent> target(int index) {
        return index < 0 || index >= steps.size() ? Optional.empty()
                : NewsTargets.find(view, steps.get(index).targetId());
    }

    private boolean isShown(int index) {
        return target(index).filter(this::isShown).isPresent();
    }

    /**
     * Shown in this window now: inside its root pane, it and every container up to it visible, and part of it inside
     * them (a toolbar button pushed past a narrow window's edge is not).
     */
    private boolean isShown(JComponent target) {
        JRootPane root = SwingUtilities.getRootPane(view);
        if (root == null || SwingUtilities.getRootPane(target) != root) {
            return false;
        }
        for (Component component = target; component != null && component != root; component = component.getParent()) {
            if (!component.isVisible()) {
                return false;
            }
        }
        return !target.getVisibleRect().isEmpty();
    }

    private List<Integer> shownIndices() {
        List<Integer> shown = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            if (isShown(index)) {
                shown.add(index);
            }
        }
        return shown;
    }

    private OptionalInt shownAfter(int index) {
        for (int candidate = index + 1; candidate < steps.size(); candidate++) {
            if (isShown(candidate)) {
                return OptionalInt.of(candidate);
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt shownBefore(int index) {
        for (int candidate = index - 1; candidate >= 0; candidate--) {
            if (isShown(candidate)) {
                return OptionalInt.of(candidate);
            }
        }
        return OptionalInt.empty();
    }

    // --- listening while open ---

    private void listen() {
        seams.dispatchers().add(keyHook);
        whileOpen.add(() -> seams.dispatchers().remove(keyHook));
        whileOpen.add(view.forEachPane(pane -> pane.addEscapeClaim(this::isOpen)));
        whileOpen.add(view.forEachPane(pane -> {
            MouseListener startsEditing = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    done(); // a gesture on the circuit: the user is editing now
                }
            };
            pane.addMouseListener(startsEditing);
            return () -> pane.removeMouseListener(startsEditing);
        }));
        Runnable edited = this::done;
        view.openDocument().addChangeListener(edited);
        whileOpen.add(() -> view.openDocument().removeChangeListener(edited));
        whileOpen.add(view.addFullScreenLayoutListener(targetsChanged));
        preferences.addListener(targetsChanged); // a rebound key shows at once
        whileOpen.add(() -> preferences.removeListener(targetsChanged));
        ComponentListener windowListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                targetsChanged();
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                targetsChanged();
            }
        };
        layers.addComponentListener(windowListener);
        whileOpen.add(() -> layers.removeComponentListener(windowListener));
        view.addComponentListener(windowListener);
        whileOpen.add(() -> view.removeComponentListener(windowListener));
        whileOpen.add(this::stopSliding);
    }

    /** Hears the current target move, resize, hide or leave the window. */
    private void followTarget(JComponent target, boolean already) {
        if (already) {
            return;
        }
        stopFollowingTheTarget.run();
        ComponentListener moves = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                targetsChanged();
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                targetsChanged();
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                targetsChanged();
            }
        };
        HierarchyListener hierarchy = event -> {
            if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) {
                SwingUtilities.invokeLater(this::targetsChanged); // after the swap that caused it is complete
            }
        };
        target.addComponentListener(moves);
        target.addHierarchyListener(hierarchy);
        stopFollowingTheTarget = () -> {
            target.removeComponentListener(moves);
            target.removeHierarchyListener(hierarchy);
        };
    }

    // --- keys ---

    /** → or Enter: next; ←: previous; Esc: «Listo». Plain keys in this window, no menu open, not typing text. */
    private boolean keyEvent(KeyEvent event) {
        Component source = event.getComponent();
        if (!isOpen() || event.getID() != KeyEvent.KEY_PRESSED || source == null
                || (event.getModifiersEx() & NO_MODIFIERS) != 0
                || SwingUtilities.getRootPane(source) != SwingUtilities.getRootPane(view)
                || MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return false;
        }
        int key = event.getKeyCode();
        if (source instanceof JTextComponent && key != KeyEvent.VK_ESCAPE) {
            return false;
        }
        switch (key) {
            case KeyEvent.VK_RIGHT, KeyEvent.VK_ENTER -> next();
            case KeyEvent.VK_LEFT -> previous();
            case KeyEvent.VK_ESCAPE -> done();
            default -> {
                return false;
            }
        }
        event.consume();
        return true;
    }
}
