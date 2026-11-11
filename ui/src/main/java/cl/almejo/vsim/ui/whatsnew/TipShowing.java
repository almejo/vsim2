package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorView;
import com.formdev.flatlaf.util.UIScale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One showing of the tip of the day's card over a view (mockup v11): in the bottom-right corner of the canvas area,
 * above the circuit but never blocking it. ‹ › walk the tips (each one shown counts as seen), × and Esc (while the
 * card has the focus) close it, and so do entering presentation mode and the view going away; editing the circuit,
 * pressing on it or the focus moving never close it. It never takes the focus; closing gives the canvas back the focus when
 * the card had it. It fades and slides in with «Animaciones» on. EDT only.
 */
final class TipShowing {

    private static final Logger LOGGER = LoggerFactory.getLogger(TipShowing.class);
    /** Above the content and the floating palettes; under the bubbles, modal dialogs and popups. */
    static final Integer LAYER = JLayeredPane.PALETTE_LAYER + 40;
    /** The card's distance from the corner of the canvas area (the mockup's 12 px). */
    private static final int MARGIN = 12;
    private static final long FADE_MILLISECONDS = 220;
    /** How far below its place the card starts its slide in. */
    private static final int SLIDE = 16;

    private final EditorView view;
    private final List<Tip> tips;
    private final TipOrder order;
    private final TipOfTheDayExtension.Seams seams;
    private final AppPreferences preferences;
    private final KeyEventDispatcher keyHook = this::keyEvent;
    private final List<Runnable> whileOpen = new ArrayList<>();
    private final Map<String, Optional<TipPicture.Slot>> slots = new HashMap<>();
    private TipCard card;
    private JLayeredPane layers;
    private int current;
    private float appearing = 1f;

    TipShowing(EditorView view, List<Tip> tips, TipOrder order, TipOfTheDayExtension.Seams seams) {
        this.view = view;
        this.tips = List.copyOf(tips);
        this.order = order;
        this.seams = seams;
        this.preferences = view.services().preferences();
    }

    boolean isOpen() {
        return card != null;
    }

    /** Shows the card with the tip at {@code index}; false (nothing shown) when the view is in no window. */
    boolean open(int index) {
        JRootPane root = SwingUtilities.getRootPane(view);
        if (isOpen() || root == null) {
            return false;
        }
        layers = root.getLayeredPane();
        card = new TipCard(view.services().painters(), () -> view.services().appearance().scheme(),
                new TipCard.Handlers(this::closeGivingBackTheFocus, this::previous, this::next, this::tryIt,
                        preferences::setShowTipAtStart));
        card.whenResized(this::place);
        show(index);
        layers.add(card, LAYER);
        whileOpen.add(() -> {
            layers.remove(card);
            layers.repaint();
        });
        listen();
        appear();
        return true;
    }

    // --- walking the tips ---

    void next() {
        if (isOpen()) {
            show(order.after(current));
        }
    }

    void previous() {
        if (isOpen()) {
            show(order.before(current));
        }
    }

    private void show(int index) {
        current = index;
        order.shown(index);
        refresh();
    }

    /** Shows the current tip again as things are now: the user's keys, «Mostrar al iniciar». */
    private void refresh() {
        if (!isOpen()) {
            return;
        }
        Tip tip = tips.get(current);
        card.show(new TipCard.Content(Messages.text(tip.titleKey()),
                new FlowText(TipKeys.items(Messages.text(tip.textKey()), view)), picture(tip),
                tip.trial().isPresent(), preferences.showTipAtStart()));
        place();
    }

    private TipPicture.Content picture(Tip tip) {
        return new TipPicture.Content(tip.kind(), tip.pictures().stream().map(this::slot).toList(),
                tip.pictureKey().flatMap(name -> TipKeys.capsOf(name, view)));
    }

    /** The picture {@code name}, read once per showing; empty (and logged) when it cannot be read. */
    private Optional<TipPicture.Slot> slot(String name) {
        return slots.computeIfAbsent(name, TipShowing::read);
    }

    private static Optional<TipPicture.Slot> read(String name) {
        try {
            if (TipKind.isCircuit(name)) {
                return Optional.of(new TipPicture.CircuitSlot(TipCircuit.load(name)));
            }
            return readImage(name).map(TipPicture.ImageSlot::new);
        } catch (CircuitFormatException | IOException | RuntimeException exception) {
            // a bundled picture that does not read is a build defect (TipsFileTest), never the user's doing
            LOGGER.error("Could not read the tip picture {}", name, exception);
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> readImage(String name) throws IOException {
        try (InputStream stream = TipShowing.class.getResourceAsStream(TipsFile.FOLDER + name)) {
            return stream == null ? Optional.empty() : Optional.ofNullable(ImageIO.read(stream));
        }
    }

    // --- «Probarlo» ---

    /**
     * Opens the tip's circuit (a fresh copy, or a new empty circuit), named after the tip, in a new tab of the window,
     * and runs the tip's action there once the tab shows (the dialog the tip is about). The card goes: the user is
     * trying now.
     */
    private void tryIt() {
        Tip tip = tips.get(current);
        if (tip.trial().isEmpty()) {
            return;
        }
        Optional<String> circuit = tip.trial().get().circuit();
        CircuitDocument document;
        try {
            document = circuit.isPresent() ? TipCircuit.read(circuit.get()) : new CircuitDocument();
        } catch (CircuitFormatException exception) {
            LOGGER.error("Could not open the circuit of the tip {}", tip.id(), exception);
            return;
        }
        // Not a user edit: the document is not wrapped in an OpenDocument yet, so it has no history to record it.
        document.setName(Messages.text(tip.titleKey()));
        close();
        Optional<EditorView> tab = view.windowCommands().showUntitled(document, "");
        tip.trial().get().actionId().ifPresent(actionId -> tab.ifPresent(opened ->
                seams.later().execute(() -> run(opened, actionId))));
    }

    private static void run(EditorView tab, String actionId) {
        Action action = ShortcutsExtension.actionsOf(tab).get(actionId);
        if (action != null && action.isEnabled()) {
            action.actionPerformed(new ActionEvent(tab.canvas(), ActionEvent.ACTION_PERFORMED, actionId));
        }
    }

    // --- closing ---

    /** ×, Esc: the card goes, and the canvas gets the focus back when the card had it. */
    private void closeGivingBackTheFocus() {
        boolean cardHadTheFocus = isOpen() && view.keyboardFocus().owner()
                .filter(owner -> SwingUtilities.isDescendingFrom(owner, card)).isPresent();
        close();
        if (cardHadTheFocus) {
            view.keyboardFocus().request(view.canvas());
        }
    }

    /** Takes the card away and stops listening; harmless when it is already gone. */
    void close() {
        if (!isOpen()) {
            return;
        }
        List<Runnable> undo = new ArrayList<>(whileOpen);
        whileOpen.clear();
        for (int index = undo.size() - 1; index >= 0; index--) {
            undo.get(index).run();
        }
        card = null;
    }

    // --- where it sits ---

    /** The bottom-right corner of the canvas area, {@value #MARGIN} px in, lower while it slides in. */
    private void place() {
        if (!isOpen() || layers == null) {
            return;
        }
        Dimension size = card.getPreferredSize();
        Component area = view.canvasPanes();
        Point corner = SwingUtilities.convertPoint(area, area.getWidth(), area.getHeight(), layers);
        int inset = UIScale.scale(MARGIN) - UIScale.scale(TipCard.SHADOW);
        int slide = Math.round((1f - appearing) * UIScale.scale(SLIDE));
        card.setBounds(corner.x - size.width - inset, corner.y - size.height - inset + slide, size.width,
                size.height);
        card.revalidate();
        layers.repaint();
    }

    /** Fades and slides in with «Animaciones» on; shows whole at once without. */
    private void appear() {
        if (!seams.animations().test(preferences)) {
            setAppearing(1f);
            return;
        }
        setAppearing(0f);
        FrameTicker ticker = seams.tickers().get();
        long start = seams.clock().getAsLong();
        whileOpen.add(ticker::stop);
        ticker.start(() -> {
            double progress = Math.min(1, (seams.clock().getAsLong() - start) / (double) FADE_MILLISECONDS);
            if (!isOpen() || progress >= 1 || !seams.animations().test(preferences)) {
                setAppearing(1f);
                ticker.stop();
                return;
            }
            setAppearing((float) (1 - Math.pow(1 - progress, 3)));
        });
    }

    private void setAppearing(float progress) {
        appearing = progress;
        if (isOpen()) {
            card.setOpacity(progress);
            place();
        }
    }

    // --- listening while open ---

    private void listen() {
        seams.dispatchers().add(keyHook);
        whileOpen.add(() -> seams.dispatchers().remove(keyHook));
        whileOpen.add(view.addFullScreenLayoutListener(() -> {
            if (view.fullScreenStage().isPresent()) {
                close(); // presentation mode: the screen belongs to the class now
            }
        }));
        Runnable preferencesChanged = this::refresh; // a rebound key shows at once
        preferences.addListener(preferencesChanged);
        whileOpen.add(() -> preferences.removeListener(preferencesChanged));
        ComponentListener moved = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                place();
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                place();
            }
        };
        layers.addComponentListener(moved);
        whileOpen.add(() -> layers.removeComponentListener(moved));
        Component area = view.canvasPanes();
        area.addComponentListener(moved);
        whileOpen.add(() -> area.removeComponentListener(moved));
        HierarchyListener hidden = event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !view.isShowing()) {
                close(); // another tab came to the front, or the window went away
            }
        };
        view.addHierarchyListener(hidden);
        whileOpen.add(() -> view.removeHierarchyListener(hidden));
    }

    /**
     * Esc closes the card only while the card has the focus (the user clicked into it): at any other time Esc belongs
     * to the circuit, and working on the circuit never closes the card (the user, 2026-11-11). Not with a menu open.
     */
    private boolean keyEvent(KeyEvent event) {
        Component source = event.getComponent();
        if (!isOpen() || view.keyboardFocus().owner().filter(owner -> SwingUtilities.isDescendingFrom(owner, card))
                .isEmpty() || event.getID() != KeyEvent.KEY_PRESSED || event.getKeyCode() != KeyEvent.VK_ESCAPE
                || source == null || source instanceof JTextComponent
                || SwingUtilities.getRootPane(source) != SwingUtilities.getRootPane(view)
                || MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return false;
        }
        closeGivingBackTheFocus();
        event.consume();
        return true;
    }
}
