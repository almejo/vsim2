package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.awt.KeyEventDispatcher;
import javax.swing.JComboBox;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** The screenshot split button and Editar ▸ «Copiar captura»: what is copied, the flash and the notice. */
class CaptureButtonFeaturesTest {

    private static final int WINDOW_WIDTH = 700;
    private static final int WINDOW_HEIGHT = 500;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<BufferedImage> copied = new ArrayList<>();
    private final List<ManualFrameTicker> tickers = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private Predicate<Component> inOwnedWindow = source -> false;
    private boolean animations = true;
    private boolean clipboardFails;
    private boolean viewDisposed;
    private OpenDocument openDocument;
    private EditorView view;
    private JRootPane root;

    @BeforeEach
    void aViewInAWindow() {
        Messages.initialize(Messages.SPANISH);
        openDocument = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null, false);
        ImageClipboard clipboard = image -> {
            if (clipboardFails) {
                throw new IllegalStateException("clipboard busy");
            }
            copied.add(image);
        };
        Predicate<AppPreferences> animationsOn = candidate -> animations;
        CaptureButtonExtension extension = new CaptureButtonExtension(clipboard, () -> {
            ManualFrameTicker ticker = new ManualFrameTicker();
            tickers.add(ticker);
            return ticker;
        }, clock::get, animationsOn, new RecordingKeyDispatchers(dispatchers),
                (owner, source) -> inOwnedWindow.test(source));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(extension));
        view = new EditorView(openDocument, services, UiTestSupport.NO_WINDOW);
        root = new JRootPane();
        root.setJMenuBar(view.menuBar());
        root.setContentPane(view);
        root.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        layOut(root);
    }

    @AfterEach
    void close() {
        if (!viewDisposed) {
            view.dispose();
        }
        openDocument.close();
    }

    private SplitButton button() {
        List<SplitButton> found = new ArrayList<>();
        collect(view.toolBar(), SplitButton.class, found);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private static <T> void collect(Component component, Class<T> type, List<T> found) {
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }

    private List<CanvasNotice> notices() {
        List<CanvasNotice> found = new ArrayList<>();
        collect(root.getLayeredPane(), CanvasNotice.class, found);
        return found;
    }

    private List<CaptureFlash> flashes() {
        List<CaptureFlash> found = new ArrayList<>();
        collect(root.getLayeredPane(), CaptureFlash.class, found);
        return found;
    }

    /** Moves the shared clock and runs one frame of every ticker (they all read it). */
    private void advance(long milliseconds) {
        clock.addAndGet(milliseconds);
        for (ManualFrameTicker ticker : tickers) {
            ticker.advance(16);
        }
    }

    private Action menuAction() {
        return view.shortcutActions().get("edit.copyCapture");
    }

    @Test
    void theToolbarHasTheSplitButtonInWindowModeByDefault() {
        SplitButton button = button();

        assertThat(button.mode().id()).isEqualTo("window");
        assertThat(button.mode().text()).isEqualTo("Ventana de VSim");
        assertThat(button.mainButton().getToolTipText()).isEqualTo("Copiar la ventana de VSim al portapapeles");
        assertThat(button.mode().icon()).isNotNull();
    }

    @Test
    void pressingInWindowModeCopiesTheWholeWindow() {
        button().mainButton().doClick();

        assertThat(copied).singleElement().satisfies(image -> {
            assertThat(image.getWidth()).isEqualTo(WINDOW_WIDTH);
            assertThat(image.getHeight()).isEqualTo(WINDOW_HEIGHT);
        });
    }

    @Test
    void pressingInVisibleCircuitModeCopiesOnlyTheCanvasPanes() {
        button().setMode("visibleCircuit");

        button().mainButton().doClick();

        assertThat(copied).singleElement().satisfies(image -> {
            assertThat(image.getWidth()).isEqualTo(view.canvasPanes().getWidth());
            assertThat(image.getHeight()).isEqualTo(view.canvasPanes().getHeight());
        });
        assertThat(view.canvasPanes().getWidth()).isLessThan(WINDOW_WIDTH);
    }

    @Test
    void theModeIsRememberedAndTheMenuItemUsesIt() {
        button().setMode("visibleCircuit");

        assertThat(preferences.splitButtonMode("capture")).contains("visibleCircuit");
        perform(menuAction());

        assertThat(copied).singleElement().satisfies(image ->
                assertThat(image.getWidth()).isEqualTo(view.canvasPanes().getWidth()));
        assertThat(notices()).singleElement().extracting(CanvasNotice::text)
                .isEqualTo("Circuito visible copiado al portapapeles");
    }

    @Test
    void theMenuItemIsRightAfterCopiarAndItsShortcutIsInTheCatalog() {
        List<String> edit = UiTestSupport.texts(view.menu(MenuId.EDIT));
        int copy = edit.indexOf("Copiar");

        assertThat(copy).isGreaterThanOrEqualTo(0);
        assertThat(edit.get(copy + 1)).isEqualTo("Copiar captura");
        assertThat(ShortcutCatalog.find("edit.copyCapture").orElseThrow().defaultStroke()).contains(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ShortcutCatalog.menuMask() | InputEvent.SHIFT_DOWN_MASK));
    }

    @Test
    void theNoticeSaysWhatWasCopied() {
        button().mainButton().doClick();

        assertThat(notices()).singleElement().extracting(CanvasNotice::text)
                .isEqualTo("Ventana copiada al portapapeles");
    }

    @Test
    void aWhiteFlashCoversTheCopiedAreaForAboutAThirdOfASecond() {
        button().mainButton().doClick();

        assertThat(flashes()).hasSize(1);
        assertThat(flashes().get(0).getWidth()).isEqualTo(WINDOW_WIDTH);
        assertThat(flashes().get(0).getHeight()).isEqualTo(WINDOW_HEIGHT);
        advance(200);
        assertThat(flashes()).hasSize(1);
        advance(200);
        assertThat(flashes()).isEmpty();
    }

    @Test
    void theFlashOverTheVisibleCircuitCoversOnlyThePanes() {
        button().setMode("visibleCircuit");

        button().mainButton().doClick();

        assertThat(flashes()).singleElement().satisfies(flash -> {
            assertThat(flash.getWidth()).isEqualTo(view.canvasPanes().getWidth());
            assertThat(flash.getHeight()).isEqualTo(view.canvasPanes().getHeight());
        });
    }

    @Test
    void withAnimationsOffThereIsNoFlashButStillTheCopyAndTheNotice() {
        animations = false;

        button().mainButton().doClick();

        assertThat(flashes()).isEmpty();
        assertThat(copied).hasSize(1);
        assertThat(notices()).hasSize(1);
    }

    @Test
    void theFlashAndTheNoticeOfThePreviousCopyAreNotInTheNextOne() {
        button().mainButton().doClick();
        button().mainButton().doClick();

        assertThat(flashes()).hasSize(1);
        assertThat(copied).hasSize(2);
    }

    @Test
    void aClipboardThatFailsIsToldInTheNoticeNotThrown() {
        clipboardFails = true;

        button().mainButton().doClick();

        assertThat(copied).isEmpty();
        assertThat(flashes()).isEmpty();
        assertThat(notices()).singleElement().extracting(CanvasNotice::text).asString()
                .startsWith("No se pudo copiar");
    }

    @Test
    void disposingTakesTheButtonTheItemAndTheShortcutBack() {
        view.dispose();
        viewDisposed = true;

        List<SplitButton> found = new ArrayList<>();
        collect(view.toolBar(), SplitButton.class, found);
        assertThat(found).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("edit.copyCapture");
        assertThat(dispatchers).isEmpty();
        assertThat(UiTestSupport.texts(view.menu(MenuId.EDIT))).doesNotContain("Copiar captura");
    }

    @Test
    void theToolbarNeverStretchesTheButtonAndPutsItAfterTheSpeedSelector() {
        JToolBar toolBar = view.toolBar();
        toolBar.setSize(1600, toolBar.getPreferredSize().height);
        layOut(toolBar);

        SplitButton button = button();
        assertThat(button.getMaximumSize()).isEqualTo(button.getPreferredSize());
        assertThat(button.getWidth()).isEqualTo(button.getPreferredSize().width);
        Component[] components = toolBar.getComponents();
        int index = java.util.Arrays.asList(components).indexOf(button);
        assertThat(components[index - 1]).isInstanceOf(JComboBox.class);
    }

    private KeyEvent captureKey(Component source) {
        // the shortcuts extension (not installed in this view) is what sets the accelerator: here the default
        KeyStroke stroke = ShortcutCatalog.find("edit.copyCapture").orElseThrow().defaultStroke().orElseThrow();
        menuAction().putValue(Action.ACCELERATOR_KEY, stroke);
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, stroke.getModifiers(), stroke.getKeyCode(),
                KeyEvent.CHAR_UNDEFINED);
    }

    private boolean dispatch(KeyEvent event) {
        return dispatchers.stream().anyMatch(dispatcher -> dispatcher.dispatchKeyEvent(event));
    }

    @Test
    void theShortcutAlsoWorksFromADialogOfTheWindowAndCapturesTheWindow() {
        JPanel inADialog = new JPanel();
        // headless: no dialog can exist, so the seam says which sources are in a window the view's window owns
        inOwnedWindow = source -> source == inADialog;

        boolean taken = dispatch(captureKey(inADialog));

        assertThat(taken).isTrue();
        assertThat(copied).hasSize(1);
        assertThat(copied.get(0).getWidth()).isEqualTo(WINDOW_WIDTH);
    }

    @Test
    void theShortcutFromAnUnrelatedWindowOrAnotherKeyDoesNothing() {
        inOwnedWindow = source -> false;

        assertThat(dispatch(captureKey(new JPanel()))).isFalse();
        inOwnedWindow = source -> true;
        KeyEvent other = new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a');
        assertThat(dispatch(other)).isFalse();
        assertThat(copied).isEmpty();
    }

    @Test
    void theShortcutFollowsTheUsersBindingAndIgnoresTheDefaultOnceChanged() {
        KeyStroke custom = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.ALT_DOWN_MASK);
        menuAction().putValue(Action.ACCELERATOR_KEY, custom);
        inOwnedWindow = source -> true;
        JPanel source = new JPanel();

        assertThat(dispatch(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, InputEvent.ALT_DOWN_MASK, KeyEvent.VK_K,
                KeyEvent.CHAR_UNDEFINED))).isTrue();
        assertThat(dispatch(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0,
                ShortcutCatalog.menuMask() | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_C,
                KeyEvent.CHAR_UNDEFINED))).isFalse();
        assertThat(copied).hasSize(1);
    }

    /**
     * The final review's I2: with two tabs, the shortcut from a dialog copies the tab that shows, with the mode chosen
     * in any tab's ▾, not the tab whose dispatcher was registered first.
     */
    @Test
    void theShortcutFromADialogCopiesTheShownTabWithTheModeChosenInAnotherTab() {
        EditorView shown = new EditorView(openDocument, view.services(), UiTestSupport.NO_WINDOW);
        try {
            java.awt.CardLayout cards = new java.awt.CardLayout();
            JPanel tabs = new JPanel(cards);
            tabs.add(view, "first");
            tabs.add(shown, "shown");
            root.setContentPane(tabs);
            cards.show(tabs, "shown");
            layOut(root);
            view.canvasPanes().setSize(100, 80); // the hidden tab's last layout, unlike the shown one's
            List<SplitButton> shownButtons = new ArrayList<>();
            collect(shown.toolBar(), SplitButton.class, shownButtons);
            shownButtons.get(0).setMode("visibleCircuit");
            assertThat(button().mode().id()).as("the first tab's ▾ follows").isEqualTo("visibleCircuit");
            inOwnedWindow = source -> true;
            KeyEvent key = captureKey(new JPanel());
            shown.shortcutActions().get("edit.copyCapture")
                    .putValue(Action.ACCELERATOR_KEY, menuAction().getValue(Action.ACCELERATOR_KEY));

            assertThat(dispatch(key)).isTrue();

            assertThat(copied).singleElement().satisfies(image -> {
                assertThat(image.getWidth()).isEqualTo(shown.canvasPanes().getWidth());
                assertThat(image.getHeight()).isEqualTo(shown.canvasPanes().getHeight());
            });
        } finally {
            shown.dispose();
        }
    }

    /** The final review's M7: a picture that cannot be painted does not blame the clipboard. */
    @Test
    void aPictureThatCannotBePaintedIsNotBlamedOnTheClipboard() {
        button().setMode("visibleCircuit");
        view.canvasPanes().setSize(0, 0);

        perform(menuAction());

        assertThat(copied).isEmpty();
        assertThat(notices()).singleElement().extracting(CanvasNotice::text).isEqualTo("No se pudo copiar la captura.");
    }

    @Test
    void theShortcutDoesNotFireWhileAKeyCapturingPartHasTheKeys() {
        inOwnedWindow = source -> true;
        view.keyboardCapture().capture("some-part");

        assertThat(dispatch(captureKey(new JPanel()))).isFalse();
        assertThat(copied).isEmpty();
    }
}
