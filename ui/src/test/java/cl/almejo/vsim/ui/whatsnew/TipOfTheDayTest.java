package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.shortcuts.ShortcutEchoExtension;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.shortcuts.sheet.ShortcutSheetExtension;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.panes.SplitViewsExtension;
import cl.almejo.vsim.ui.wiring.TidyWiresExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The tip of the day: a card in the corner that never blocks, at most once a day, with the user's real keys. */
class TipOfTheDayTest {

    private static final String LOOK_INSIDE = "tip.lookInside.title";
    private static final String TIDY_CIRCUIT = "tip.tidyCircuit.title";
    private static final String SPLIT_VIEW = "tip.splitView.title";

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Map<EditorView, Runnable> newsReady = new HashMap<>();
    private final Map<EditorView, Runnable> tipReady = new HashMap<>();
    private final List<Runnable> aiListeners = new ArrayList<>();
    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private final List<ManualFrameTicker> tickers = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<Component> focusRequests = new ArrayList<>();
    private final List<String> toursOpened = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final List<EditorView> openedByTrying = new ArrayList<>();
    private Optional<Component> focusOwner = Optional.empty();
    private boolean aiWorking;
    private boolean newsInThisVersion = true;
    private boolean animations;
    private StartupNotices.TipRule tipRule = StartupNotices.TipRule.ONCE_PER_DAY;
    private LocalDate today = LocalDate.of(2026, 11, 10);
    private List<Tip> tips = TipsFile.load();
    private WhatsNewExtension whatsNew;
    private TipOfTheDayExtension tipOfTheDay;

    private final WhatsNewExtension.AiActivity ai = new WhatsNewExtension.AiActivity() {
        @Override
        public boolean working() {
            return aiWorking;
        }

        @Override
        public Runnable addListener(Runnable listener) {
            aiListeners.add(listener);
            return () -> aiListeners.remove(listener);
        }
    };

    /** «Probarlo» opens the circuit in a new tab: a view of its own over a new document. */
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public Optional<EditorView> showUntitled(CircuitDocument document, String windowName) {
            OpenDocument opened = new OpenDocument(document, StandardComponentTypes.registry(), null, false);
            documents.add(opened);
            EditorView tab = new EditorView(opened, services(), UiTestSupport.NO_WINDOW);
            views.add(tab);
            openedByTrying.add(tab);
            return Optional.of(tab);
        }
    };

    @BeforeEach
    void extensionsOverFakeSeams() {
        Messages.initialize(Messages.SPANISH);
        newExtensions();
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        documents.forEach(OpenDocument::close);
    }

    /** A new application run: its own startup notices over the same preferences. */
    private void newExtensions() {
        whatsNew = new WhatsNewExtension(new WhatsNewExtension.Seams("0.8",
                version -> newsInThisVersion ? Optional.of(new NewsFile(version, List.of())) : Optional.empty(),
                StartupNotices.NewsRule.ONCE_PER_VERSION,
                (view, ready) -> {
                    newsReady.put(view, ready);
                    return () -> newsReady.remove(view);
                }, ai, (view, news, ended) -> {
                    toursOpened.add(news.version());
                    return () -> { };
                }, tipRule, () -> today));
        tipOfTheDay = new TipOfTheDayExtension(whatsNew, new TipOfTheDayExtension.Seams(() -> tips,
                (view, ready) -> {
                    tipReady.put(view, ready);
                    return () -> tipReady.remove(view);
                }, ai, new RecordingKeyDispatchers(dispatchers), () -> {
                    ManualFrameTicker ticker = new ManualFrameTicker();
                    tickers.add(ticker);
                    return ticker;
                }, clock::get, candidate -> animations, Runnable::run));
    }

    private EditorServices services() {
        RecordingKeyDispatchers keyHooks = new RecordingKeyDispatchers();
        ShortcutSheetExtension sheet = new ShortcutSheetExtension(new ShortcutSheetExtension.Seams(keyHooks,
                ManualFrameTicker::new, () -> 0L, candidate -> false, Runnable::run));
        ShortcutEchoExtension echo = new ShortcutEchoExtension(new ShortcutEchoExtension.Seams(
                ManualFrameTicker::new, () -> 0L, candidate -> false, keyHooks, () -> null, () -> null, false));
        // «Ordenar circuito» and «Dividir» give the tips' actions their menu items
        List<EditorExtension> extensions = List.of(new TidyWiresExtension(), new SplitViewsExtension(), sheet,
                whatsNew, tipOfTheDay, echo, new ShortcutsExtension());
        return new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), extensions);
    }

    /** A view in a root pane of 1200 × 800, as in a window. */
    private EditorView newView() {
        OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(openDocument);
        EditorView view = new EditorView(openDocument, services(), window);
        views.add(view);
        view.useKeyboardFocus(new KeyboardFocus() {
            @Override
            public Optional<Component> owner() {
                return focusOwner;
            }

            @Override
            public void request(Component target) {
                focusRequests.add(target);
            }
        });
        JRootPane root = new JRootPane();
        root.setJMenuBar(view.menuBar());
        JPanel content = new JPanel(new BorderLayout());
        content.add(view, BorderLayout.CENTER);
        root.setContentPane(content);
        root.setSize(1200, 800);
        UiTestSupport.layOut(root);
        return view;
    }

    private static JMenuItem tipItem(EditorView view) {
        JMenu help = view.menu(MenuId.HELP);
        for (int index = 0; index < help.getItemCount(); index++) {
            JMenuItem item = help.getItem(index);
            if (item != null && Messages.text("action.tipOfTheDay").equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no «Consejo del día» in Ayuda: " + UiTestSupport.texts(help));
    }

    private static Optional<TipCard> card(EditorView view) {
        JRootPane root = javax.swing.SwingUtilities.getRootPane(view);
        for (Component child : root.getLayeredPane().getComponents()) {
            if (child instanceof TipCard card) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    private static TipCard openCard(EditorView view) {
        return card(view).orElseThrow(() -> new AssertionError("no tip card"));
    }

    private boolean press(Component source, int keyCode) {
        KeyEvent event = new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (KeyEventDispatcher dispatcher : List.copyOf(dispatchers)) {
            if (dispatcher.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> keysOf(KeyStroke stroke) {
        return KeyNames.keys(stroke, KeyNames.MAC_NOTATION);
    }

    // --- Ayuda ▸ «Consejo del día» ---

    @Test
    void ayudaListsTheTipRightAfterWhatsNewAndThePaletteFindsItByOtherWords() {
        EditorView view = newView();

        List<String> help = UiTestSupport.texts(view.menu(MenuId.HELP));
        int whatsNewIndex = help.indexOf(Messages.text("action.whatsNew"));
        assertThat(help.get(whatsNewIndex + 1)).isEqualTo(Messages.text("action.tipOfTheDay"));
        assertThat(tipItem(view).getClientProperty(Actions.SEARCH_TERMS))
                .isEqualTo(Messages.text("tip.searchTerms"));
    }

    @Test
    void theMenuItemOpensTheFirstUnseenTipAndCountsItAsSeen() {
        preferences.setSeenTips(java.util.Set.of("look-inside"));
        EditorView view = newView();

        tipItem(view).doClick(0);

        assertThat(openCard(view).title()).isEqualTo(Messages.text(TIDY_CIRCUIT));
        assertThat(preferences.seenTips()).containsExactlyInAnyOrder("look-inside", "tidy-circuit");
    }

    @Test
    void theArrowsWalkTheTipsAndWrapAroundAndNothingCountsThem() {
        EditorView view = newView();
        tipItem(view).doClick(0);
        TipCard card = openCard(view);

        card.previousButton().doClick(0);
        assertThat(card.title()).isEqualTo(Messages.text(SPLIT_VIEW));
        card.nextButton().doClick(0);
        assertThat(card.title()).isEqualTo(Messages.text(LOOK_INSIDE));
        card.nextButton().doClick(0);
        assertThat(card.title()).isEqualTo(Messages.text(TIDY_CIRCUIT));

        assertThat(card.visibleTexts()).noneMatch(text -> text.matches(".*\\d+\\s+(de|of)\\s+\\d+.*"));
        assertThat(preferences.seenTips()).containsExactlyInAnyOrder("look-inside", "tidy-circuit", "split-view");
    }

    @Test
    void probarloShowsOnlyWhenTheTipOpensSomething() {
        tips = List.of(TipsFile.load().get(0), new Tip("nothing-to-open", TipKind.IMAGE, SPLIT_VIEW,
                "tip.splitView.text", List.of("nothing.png"), Optional.empty(), Optional.empty()));
        newExtensions();
        EditorView view = newView();
        tipItem(view).doClick(0);
        TipCard card = openCard(view);

        assertThat(card.tryButton().isVisible()).isTrue();
        card.nextButton().doClick(0);
        assertThat(card.title()).isEqualTo(Messages.text(SPLIT_VIEW));
        assertThat(card.tryButton().isVisible()).isFalse();
    }

    @Test
    void theSplitViewTipsProbarloOpensItsCircuitAlreadySplit() {
        tips = TipsFile.load().stream().filter(tip -> tip.id().equals("split-view")).toList();
        newExtensions();
        EditorView view = newView();
        tipItem(view).doClick(0);
        assertThat(openCard(view).title()).isEqualTo(Messages.text(SPLIT_VIEW));

        openCard(view).tryButton().doClick(0);

        assertThat(openedByTrying).hasSize(1);
        assertThat(openedByTrying.get(0).panes()).hasSize(2);
        assertThat(view.panes()).hasSize(1);
    }

    @Test
    void mostrarAlIniciarIsThePreferenceBothWays() {
        EditorView view = newView();
        tipItem(view).doClick(0);
        TipCard card = openCard(view);
        assertThat(card.showAtStartBox().isSelected()).isTrue();

        card.showAtStartBox().doClick(0);
        assertThat(preferences.showTipAtStart()).isFalse();

        preferences.setShowTipAtStart(true); // Preferencias ▸ General, with the card open
        assertThat(card.showAtStartBox().isSelected()).isTrue();
    }

    // --- when it comes by itself ---

    @Test
    void theFirstWindowShowsTheTipOnALaterLaunchAndRemembersTheDay() {
        preferences.setNewsShownVersion("0.8");
        EditorView first = newView();
        EditorView second = newView();

        newsReady.get(first).run();
        tipReady.get(first).run();
        tipReady.get(second).run();

        assertThat(card(first)).isPresent();
        assertThat(card(second)).isEmpty();
        assertThat(preferences.tipShownDay()).contains(today);
        assertThat(toursOpened).isEmpty();
    }

    @Test
    void neverOnAFirstLaunch() {
        EditorView view = newView();
        preferences.setShowTipAtStart(true);

        tipReady.get(view).run();
        newsReady.get(view).run();

        assertThat(card(view)).isEmpty();
    }

    @Test
    void neverOnAStartWithTheBubbles() {
        preferences.setNewsShownVersion("0.7");
        EditorView view = newView();

        tipReady.get(view).run();
        newsReady.get(view).run();

        assertThat(toursOpened).containsExactly("0.8");
        assertThat(card(view)).isEmpty();
    }

    @Test
    void notTwiceTheSameDayButAgainTheNextDay() {
        preferences.setNewsShownVersion("0.8");
        EditorView morning = newView();
        tipReady.get(morning).run();
        assertThat(card(morning)).isPresent();

        newExtensions();
        EditorView afternoon = newView();
        tipReady.get(afternoon).run();
        assertThat(card(afternoon)).isEmpty();

        today = today.plusDays(1);
        newExtensions();
        EditorView tomorrow = newView();
        tipReady.get(tomorrow).run();
        assertThat(card(tomorrow)).isPresent();
    }

    @Test
    void theAiWorkingHoldsItBackUntilItStops() {
        preferences.setNewsShownVersion("0.8");
        aiWorking = true;
        EditorView view = newView();

        tipReady.get(view).run();
        assertThat(card(view)).isEmpty();
        aiWorking = false;
        List.copyOf(aiListeners).forEach(Runnable::run);

        assertThat(card(view)).isPresent();
    }

    @Test
    void theDevelopmentSwitchShowsItAlsoOnAFirstLaunchAndTwiceADay() {
        newsInThisVersion = false; // a first launch with news would show the bubbles: they win
        tipRule = StartupNotices.TipRule.EVERY_LAUNCH;
        newExtensions();
        EditorView first = newView();
        tipReady.get(first).run();
        assertThat(card(first)).isPresent();

        newExtensions();
        EditorView again = newView();
        tipReady.get(again).run();
        assertThat(card(again)).isPresent();
    }

    // --- keys ---

    @Test
    void aReboundShortcutShowsAtOnceInTheTextAndInTheBigKey() {
        EditorView view = newView();
        tipItem(view).doClick(0);
        TipCard card = openCard(view);
        card.nextButton().doClick(0);
        assertThat(card.title()).isEqualTo(Messages.text(TIDY_CIRCUIT));
        KeyStroke original = new KeyBindings(preferences).strokeFor("edit.tidyWires").orElseThrow();
        assertThat(card.textKeys()).contains(keysOf(original));
        assertThat(card.pictureKey()).contains(keysOf(original));

        KeyStroke rebound = KeyStroke.getKeyStroke(KeyEvent.VK_T,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        new KeyBindings(preferences).assign("edit.tidyWires", rebound);

        assertThat(card.textKeys()).contains(keysOf(rebound)).doesNotContain(keysOf(original));
        assertThat(card.pictureKey()).contains(keysOf(rebound));
    }

    @Test
    void anActionWithoutAKeyIsNamedByItsPlaceInTheMenus() {
        new KeyBindings(preferences).clear("edit.tidyWires");
        EditorView view = newView();
        tipItem(view).doClick(0);
        TipCard card = openCard(view);
        card.nextButton().doClick(0);

        assertThat(card.textWords()).contains(Messages.format("commandPalette.path",
                Messages.text("menu.edit"), Messages.text("action.tidyWires")));
        assertThat(card.pictureKey()).isEmpty();
    }

    @Test
    void theAltOfLookingInsideIsAFixedKey() {
        EditorView view = newView();
        tipItem(view).doClick(0);

        assertThat(openCard(view).textKeys()).contains(List.of(Messages.text("keyName.alt")));
        assertThat(openCard(view).pictureKey()).contains(List.of(Messages.text("keyName.alt")));
    }

    // --- it never blocks ---

    @Test
    void itDoesNotTakeTheFocusWhenItOpensAndClosingGivesItBackToTheCanvas() {
        EditorView view = newView();
        tipItem(view).doClick(0);
        TipCard card = openCard(view);
        assertThat(focusRequests).isEmpty();

        focusOwner = Optional.of(card.nextButton());
        card.closeButton().doClick(0);

        assertThat(card(view)).isEmpty();
        assertThat(focusRequests).containsExactly(view.canvas());
    }

    @Test
    void escapeClosesItButNotWhileTypingInATextField() {
        EditorView view = newView();
        tipItem(view).doClick(0);
        JTextField field = new JTextField();
        ((JComponent) view.canvasPanes()).add(field);

        assertThat(press(field, KeyEvent.VK_ESCAPE)).isFalse();
        assertThat(card(view)).isPresent();
        assertThat(press(view.canvas(), KeyEvent.VK_ESCAPE)).isTrue();
        assertThat(card(view)).isEmpty();
    }

    @Test
    void changingTheCircuitClosesIt() {
        EditorView view = newView();
        tipItem(view).doClick(0);

        UiTestSupport.place(view.openDocument(), StandardComponentTypes.SWITCH, 2, 2, Map.of());

        assertThat(card(view)).isEmpty();
    }

    @Test
    void aPressOnTheCircuitClosesIt() {
        EditorView view = newView();
        tipItem(view).doClick(0);

        UiTestSupport.press(view.canvas());

        assertThat(card(view)).isEmpty();
    }

    @Test
    void itFadesInWithAnimationsAndShowsWholeWithout() {
        EditorView view = newView();
        tipItem(view).doClick(0);
        assertThat(openCard(view).opacity()).isEqualTo(1f);
        openCard(view).closeButton().doClick(0);

        animations = true;
        tipItem(view).doClick(0);
        assertThat(openCard(view).opacity()).isZero();
        clock.addAndGet(10_000);
        tickers.get(tickers.size() - 1).advance(16);
        assertThat(openCard(view).opacity()).isEqualTo(1f);
    }

    // --- «Probarlo» ---

    @Test
    void probarloOpensTheCircuitInANewTabAndRunsTheNamedActionThereLeavingTheOpenCircuitAlone() {
        tips = List.of(new Tip("trial", TipKind.CIRCUIT, LOOK_INSIDE, "tip.lookInside.text",
                List.of("tidy-circuit-before.vsim"), Optional.empty(),
                Optional.of(new Tip.Trial(Optional.of("edit.selectAll")))));
        newExtensions();
        EditorView view = newView();
        tipItem(view).doClick(0);

        openCard(view).tryButton().doClick(0);

        assertThat(openedByTrying).hasSize(1);
        EditorView tab = openedByTrying.get(0);
        assertThat(tab.openDocument().document().components()).isNotEmpty();
        assertThat(tab.selection().isEmpty()).isFalse();
        assertThat(view.openDocument().document().components()).isEmpty();
        assertThat(view.openDocument().isModified()).isFalse();
    }

    // --- closing the window ---

    @Test
    void closingTheWindowTakesBackTheCardTheItemAndEveryListener() {
        int preferenceListeners = preferences.listenerCount();
        EditorView view = newView();
        int withTheView = preferences.listenerCount();
        tipItem(view).doClick(0);

        view.dispose();
        views.remove(view);

        assertThat(card(view)).isEmpty();
        assertThat(UiTestSupport.texts(view.menu(MenuId.HELP))).doesNotContain(Messages.text("action.tipOfTheDay"));
        assertThat(dispatchers).isEmpty();
        assertThat(tipReady).doesNotContainKey(view);
        assertThat(preferences.listenerCount()).isLessThanOrEqualTo(withTheView).isEqualTo(preferenceListeners);
    }
}
