package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.whatsnew.NewsTargets.Side;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/** The bubbles of a release, one by one over a dimmed window, pointing at what is new. */
class BubbleTourTest {

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private final List<ManualFrameTicker> tickers = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<Component> focusRequests = new ArrayList<>();
    private boolean animations;
    private int ended;
    private LibraryService service;
    private OpenDocument openDocument;
    private EditorView view;
    private JRootPane root;
    private BubbleTours tours;

    @BeforeEach
    void aWindowWithEveryExtension() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, StandardComponentTypes.registry(), Runnable::run);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.useKeyboardFocus(new KeyboardFocus() {
            @Override
            public Optional<Component> owner() {
                return Optional.empty();
            }

            @Override
            public void request(Component target) {
                focusRequests.add(target);
            }
        });
        root = new JRootPane();
        root.setJMenuBar(view.menuBar());
        JPanel content = new JPanel(new BorderLayout());
        content.add(view.toolBar(), BorderLayout.NORTH);
        content.add(view, BorderLayout.CENTER);
        root.setContentPane(content);
        sized(1400, 1000);
        tours = new BubbleTours(new BubbleTours.Seams(new RecordingKeyDispatchers(dispatchers), () -> {
            ManualFrameTicker ticker = new ManualFrameTicker();
            tickers.add(ticker);
            return ticker;
        }, clock::get, candidate -> animations));
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
        service.close();
    }

    private void sized(int width, int height) {
        root.setSize(width, height);
        layOut(root);
    }

    private BubbleTour start() {
        return tours.start(view, NewsFiles.load("0.8").orElseThrow(), () -> ended++);
    }

    private boolean press(int keyCode) {
        KeyEvent event = new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (KeyEventDispatcher dispatcher : List.copyOf(dispatchers)) {
            if (dispatcher.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return false;
    }

    private List<TourOverlay> overlays() {
        List<TourOverlay> found = new ArrayList<>();
        for (Component child : root.getLayeredPane().getComponents()) {
            if (child instanceof TourOverlay overlay) {
                found.add(overlay);
            }
        }
        return found;
    }

    private JComponent target(String id) {
        return NewsTargets.find(view, id).orElseThrow();
    }

    private Rectangle inLayers(JComponent component) {
        return javax.swing.SwingUtilities.convertRectangle(component.getParent(), component.getBounds(),
                root.getLayeredPane());
    }

    @Test
    void theFirstBubbleShowsOverADimmedWindowAboveTheMenus() {
        BubbleTour tour = start();

        assertThat(tour.isOpen()).isTrue();
        assertThat(overlays()).hasSize(1);
        assertThat(JLayeredPane.getLayer(overlays().get(0)))
                .isGreaterThan(JLayeredPane.FRAME_CONTENT_LAYER).isLessThan(JLayeredPane.POPUP_LAYER);
        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.wires.title"));
        assertThat(tour.count()).isEqualTo("1 de 8");
        assertThat(tour.previousEnabled()).isFalse();
    }

    @Test
    void theArrowsAndEnterWalkTheBubblesAndTheLastEndsTheTour() {
        BubbleTour tour = start();

        assertThat(press(KeyEvent.VK_RIGHT)).isTrue();
        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.capture.title"));
        assertThat(tour.previousEnabled()).isTrue();
        press(KeyEvent.VK_LEFT);
        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.wires.title"));
        press(KeyEvent.VK_LEFT);
        assertThat(tour.count()).isEqualTo("1 de 8");
        for (int step = 0; step < 7; step++) {
            press(KeyEvent.VK_ENTER);
        }
        assertThat(tour.count()).isEqualTo("8 de 8");
        assertThat(ended).isZero();

        tour.next();

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isEqualTo(1);
        assertThat(overlays()).isEmpty();
    }

    @Test
    void escapeIsListoAndGivesTheKeyboardBackToTheCanvas() {
        BubbleTour tour = start();

        assertThat(press(KeyEvent.VK_ESCAPE)).isTrue();

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isEqualTo(1);
        assertThat(focusRequests).last().isInstanceOf(CircuitCanvas.class);
        assertThat(dispatchers).isEmpty();
    }

    @Test
    void listoEndsTheTour() {
        BubbleTour tour = start();

        tour.done();

        assertThat(ended).isEqualTo(1);
        assertThat(overlays()).isEmpty();
    }

    @Test
    void aHiddenTargetIsSkippedAndNotCounted() {
        target(NewsTargets.TOOLBAR_WAVEFORMS).setVisible(false);
        BubbleTour tour = start();

        assertThat(tour.count()).isEqualTo("1 de 7");
        tour.next();
        tour.next();

        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.presentationMode.title"));
        assertThat(tour.count()).isEqualTo("3 de 7");
    }

    @Test
    void aTargetThatVanishesMidTourPassesToTheNextOne() {
        BubbleTour tour = start();
        tour.next();
        tour.next();
        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.waveforms.title"));

        target(NewsTargets.TOOLBAR_WAVEFORMS).setVisible(false);
        tour.targetsChanged();

        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.presentationMode.title"));
        assertThat(tour.count()).isEqualTo("3 de 7");
    }

    @Test
    void withoutAnyShownTargetNothingOpens() {
        view.toolBar().setVisible(false);
        view.menuBar().setVisible(false);

        BubbleTour tour = start();

        assertThat(tour.isOpen()).isFalse();
        assertThat(overlays()).isEmpty();
        assertThat(ended).isZero();
    }

    @Test
    void editingTheCircuitEndsTheTourLikeListo() {
        int listeners = openDocument.changeListenerCount();
        BubbleTour tour = start();

        UiTestSupport.addWire(openDocument, 0, 0, 10, 0);

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isEqualTo(1);
        assertThat(openDocument.changeListenerCount()).isEqualTo(listeners);
    }

    @Test
    void startingAGestureOnTheCircuitEndsTheTour() {
        BubbleTour tour = start();

        UiTestSupport.press(view.canvas());

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isEqualTo(1);
    }

    @Test
    void closingTheWindowTakesEverythingAwayWithoutEndingTheTour() {
        int listeners = openDocument.changeListenerCount();
        int mouseListeners = view.canvas().getMouseListeners().length;
        BubbleTour tour = start();

        tour.close();

        assertThat(tour.isOpen()).isFalse();
        assertThat(ended).isZero();
        assertThat(overlays()).isEmpty();
        assertThat(dispatchers).isEmpty();
        assertThat(openDocument.changeListenerCount()).isEqualTo(listeners);
        assertThat(view.canvas().getMouseListeners()).hasSize(mouseListeners);
    }

    @Test
    void theCaptureBubbleShowsItsShortcutAsKeysAndFollowsARebinding() {
        BubbleTour tour = start();
        tour.next();

        KeyStroke capture = new KeyBindings(preferences).strokeFor("edit.copyCapture").orElseThrow();
        assertThat(tour.line()).contains(new BubbleLine.Shortcut(KeyNames.keys(capture, KeyNames.MAC_NOTATION)));

        KeyStroke rebound = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.ALT_DOWN_MASK);
        new KeyBindings(preferences).assign("edit.copyCapture", rebound);
        tour.previous();
        tour.next();

        assertThat(tour.line()).contains(new BubbleLine.Shortcut(KeyNames.keys(rebound, KeyNames.MAC_NOTATION)));
    }

    @Test
    void anActionWithoutAKeyShowsWhereItIsInTheMenus() {
        BubbleTour tour = start();
        for (int step = 0; step < 4; step++) {
            tour.next();
        }
        assertThat(tour.line()).contains(new BubbleLine.MenuPath("Ver ▸ Mostrar los atajos al usarlos"));

        tour.next();

        assertThat(tour.line()).contains(new BubbleLine.MenuPath("Archivo ▸ Preferencias ▸ General"));
    }

    @Test
    void aBubbleWithoutActionHasNoLineAndItsTextShowsTheGestureKeys() {
        BubbleTour tour = start();

        assertThat(tour.line()).isEmpty();
        assertThat(tour.textKeys()).containsExactly(List.of(Messages.text("keyName.space")),
                List.of(Messages.text("keyName.backSpace")), List.of(Messages.text("keyName.escape")));
    }

    @Test
    void everyBubbleGoesBelowItsToolbarButtonOrMenuInsideTheWindow() {
        BubbleTour tour = start();
        Rectangle frame = new Rectangle(root.getLayeredPane().getSize());
        for (int step = 0; step < 6; step++) {
            assertThat(tour.side()).as(tour.title()).contains(Side.BELOW);
            assertThat(frame.contains(tour.bubbleBounds())).as(tour.title()).isTrue();
            assertThat(tour.bubbleBounds().y).as(tour.title())
                    .isGreaterThanOrEqualTo(root.getJMenuBar().getHeight());
            tour.next();
        }
    }

    @Test
    void theBubblePointsUnderItsTarget() {
        BubbleTour tour = start();
        tour.next();

        Rectangle button = inLayers(target(NewsTargets.TOOLBAR_CAPTURE));
        assertThat(tour.bubbleBounds().y).isGreaterThan(button.y + button.height);
        assertThat(tour.bubbleBounds().x).isLessThanOrEqualTo((int) button.getCenterX());
        assertThat(tour.bubbleBounds().x + tour.bubbleBounds().width).isGreaterThanOrEqualTo((int) button.getCenterX());
    }

    @Test
    void inATinyWindowTheBubbleIsCentredWithoutArrowAndInside() {
        sized(320, 240);
        BubbleTour tour = start();

        assertThat(tour.side()).isEmpty();
        assertThat(new Rectangle(root.getLayeredPane().getSize()).contains(tour.bubbleBounds())).isTrue();
    }

    @Test
    void aResizedWindowMovesTheBubbleOfTheButtonAtTheRightEnd() {
        BubbleTour tour = start();
        tour.next();
        tour.next();
        tour.next();
        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.presentationMode.title"));
        Rectangle before = tour.bubbleBounds();

        sized(900, 700);
        tour.targetsChanged();

        assertThat(tour.bubbleBounds()).isNotEqualTo(before);
        assertThat(new Rectangle(root.getLayeredPane().getSize()).contains(tour.bubbleBounds())).isTrue();
    }

    @Test
    void withAnimationsTheBubbleSlidesToTheNextTarget() {
        animations = true;
        BubbleTour tour = start();
        Rectangle first = tour.bubbleBounds();

        tour.next();
        Rectangle destination = tour.destination();
        assertThat(tickers).isNotEmpty();
        assertThat(tour.bubbleBounds().getLocation()).isEqualTo(first.getLocation());
        clock.addAndGet(100);
        tickers.get(tickers.size() - 1).advance(16);
        assertThat(tour.bubbleBounds().getLocation()).isNotEqualTo(first.getLocation())
                .isNotEqualTo(destination.getLocation());
        clock.addAndGet(1_000);
        tickers.get(tickers.size() - 1).advance(16);

        assertThat(tour.bubbleBounds()).isEqualTo(destination);
        assertThat(tickers.get(tickers.size() - 1).isRunning()).isFalse();
    }

    @Test
    void withAnimationsOffTheBubbleJumps() {
        BubbleTour tour = start();

        tour.next();

        assertThat(tour.bubbleBounds()).isEqualTo(tour.destination());
        assertThat(tickers.stream().noneMatch(ManualFrameTicker::isRunning)).isTrue();
    }

    @Test
    void theBubblesButtonsNeverTakeTheKeyboard() {
        start();

        TourOverlay overlay = overlays().get(0);
        assertThat(overlay.buttons()).isNotEmpty().allSatisfy(button -> assertThat(button.isFocusable()).isFalse());
        assertThat(overlay.getMouseListeners()).isEmpty();
    }

    @Test
    void theHelpBubblesShowCtrlKAndTheFixedQuestionMarkOnTheSameMenuTarget() {
        BubbleTour tour = start();
        for (int step = 0; step < 6; step++) {
            tour.next();
        }
        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.findAction.title"));
        assertThat(tour.count()).isEqualTo("7 de 8");
        KeyStroke findAction = new KeyBindings(preferences).strokeFor("help.findAction").orElseThrow();
        assertThat(tour.line()).contains(new BubbleLine.Shortcut(KeyNames.keys(findAction, KeyNames.MAC_NOTATION)));

        tour.next();

        assertThat(tour.title()).isEqualTo(Messages.text("whatsNew.shortcutSheet.title"));
        assertThat(tour.count()).isEqualTo("8 de 8");
        assertThat(tour.line()).contains(new BubbleLine.Shortcut(
                KeyNames.keys(KeyStroke.getKeyStroke('?'), KeyNames.MAC_NOTATION)));
    }
}
