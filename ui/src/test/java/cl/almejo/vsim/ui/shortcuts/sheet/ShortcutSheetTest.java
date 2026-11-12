package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.preferences.PreferencesDialog;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEchoExtension;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/** «?» shows a sheet of the main shortcuts over the circuit area (Ayuda ▸ «Atajos de teclado»). */
class ShortcutSheetTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private final List<ManualFrameTicker> tickers = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<Component> focusRequests = new ArrayList<>();
    private boolean animations = true;
    private Component focusOwner;
    private OpenDocument openDocument;
    private EditorView view;
    private JRootPane root;

    @BeforeEach
    void aViewInARootPane() {
        Messages.initialize(Messages.SPANISH);
        openDocument = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null, false);
        RecordingKeyDispatchers keyHooks = new RecordingKeyDispatchers(dispatchers);
        ShortcutEchoExtension echo = new ShortcutEchoExtension(new ShortcutEchoExtension.Seams(
                ManualFrameTicker::new, clock::get, candidate -> false, keyHooks, () -> null, () -> null, false));
        ShortcutSheetExtension sheet = new ShortcutSheetExtension(new ShortcutSheetExtension.Seams(keyHooks,
                () -> {
                    ManualFrameTicker ticker = new ManualFrameTicker();
                    tickers.add(ticker);
                    return ticker;
                }, clock::get, candidate -> animations, Runnable::run));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(sheet, echo, new ShortcutsExtension()));
        view = new EditorView(openDocument, services, UiTestSupport.NO_WINDOW);
        view.useKeyboardFocus(new KeyboardFocus() {
            @Override
            public Optional<Component> owner() {
                return Optional.ofNullable(focusOwner);
            }

            @Override
            public void request(Component target) {
                focusRequests.add(target);
            }
        });
        root = new JRootPane();
        root.setJMenuBar(view.menuBar());
        root.setContentPane(view);
        root.setSize(1400, 1000);
        layOut(root);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private boolean dispatch(KeyEvent event) {
        for (KeyEventDispatcher dispatcher : List.copyOf(dispatchers)) {
            if (dispatcher.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return false;
    }

    private static KeyEvent typed(Component source, char character, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, character);
    }

    private static KeyEvent escape(Component source) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
    }

    /** «?» as a Spanish keyboard types it (Mayús+'): only the typed character matters. */
    private boolean questionMarkOn(Component source) {
        return dispatch(typed(source, '?', InputEvent.SHIFT_DOWN_MASK));
    }

    private Optional<ShortcutSheetPanel> sheet() {
        return find(root.getLayeredPane(), ShortcutSheetPanel.class).stream().findFirst();
    }

    private <T> List<T> find(Component component, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(component, type, found);
        return found;
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

    private ShortcutSheetPanel openSheet() {
        assertThat(questionMarkOn(view.canvas())).isTrue();
        return sheet().orElseThrow();
    }

    private JMenuItem helpItem() {
        JMenu help = view.menu(MenuId.HELP);
        for (Component child : help.getMenuComponents()) {
            if (child instanceof JMenuItem item && Messages.text("action.shortcutSheet").equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no Ayuda ▸ Atajos de teclado");
    }

    private List<String> chips() {
        return find(root.getLayeredPane(), CanvasNotice.class).stream().map(CanvasNotice::text).toList();
    }

    @Test
    void aTypedQuestionMarkOnTheCanvasOpensTheSheetAndAnotherClosesIt() {
        assertThat(questionMarkOn(view.canvas())).as("the key is taken").isTrue();
        assertThat(sheet()).isPresent();

        assertThat(questionMarkOn(view.canvas())).isTrue();
        assertThat(sheet()).isEmpty();
        assertThat(focusRequests).as("the canvas gets the keyboard back").contains(view.canvas());
    }

    @Test
    void theQuestionMarkOfAUsKeyboardOpensItToo() {
        dispatch(new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SLASH,
                '?'));
        assertThat(sheet()).as("the key press does nothing, the typed character does").isEmpty();

        questionMarkOn(view.canvas());

        assertThat(sheet()).isPresent();
    }

    @Test
    void escClosesTheSheetBeforeAnythingElseUsesIt() {
        openSheet();

        assertThat(dispatch(escape(view.canvas()))).isTrue();

        assertThat(sheet()).isEmpty();
        assertThat(dispatch(escape(view.canvas()))).as("closed, Esc is the canvas's again").isFalse();
    }

    @Test
    void whileOpenEveryPaneClaimsEscSoPresentationModeIsNotLeft() {
        CircuitCanvas canvas = view.canvas();
        assertThat(canvas.isEscapeClaimed()).isFalse();

        openSheet();

        assertThat(canvas.isEscapeClaimed()).isTrue();
    }

    @Test
    void aQuestionMarkTypedInATextFieldIsTyped() {
        JTextField field = new JTextField();
        view.add(field);

        assertThat(questionMarkOn(field)).isFalse();

        assertThat(sheet()).isEmpty();
    }

    @Test
    void ctrlQuestionMarkIsNotTheSheet() {
        assertThat(dispatch(typed(view.canvas(), '?', InputEvent.CTRL_DOWN_MASK))).isFalse();
        assertThat(sheet()).isEmpty();
    }

    @Test
    void theHelpItemComesRightAfterFindAnActionAndShowsTheQuestionMark() {
        JMenuItem item = helpItem();

        assertThat(item.getAccelerator()).isEqualTo(KeyStroke.getKeyStroke('?'));
        item.doClick();
        assertThat(sheet()).isPresent();
        item.doClick();
        assertThat(sheet()).isEmpty();
    }

    @Test
    void theHelpItemOnlyShowsTheQuestionMarkTheDispatcherOwnsTheKey() {
        JMenuItem item = helpItem();
        KeyStroke questionMark = KeyStroke.getKeyStroke('?');

        boolean fired = ((ShownAcceleratorItem) item).processKeyBinding(questionMark,
                typed(item, '?', 0), JMenuItem.WHEN_IN_FOCUSED_WINDOW, false);

        assertThat(fired).as("a «?» a text field did not take must not open the sheet").isFalse();
        assertThat(sheet()).isEmpty();
    }

    @Test
    void everyIdOfTheSelectionIsAShortcutOfTheCatalogOrTheSheetItself() {
        List<SheetSelection.Item> items = SheetSelection.load();

        assertThat(items).hasSizeBetween(28, 32);
        assertThat(items).filteredOn(item -> !item.id().equals(SheetSelection.THE_SHEET))
                .allSatisfy(item -> assertThat(ShortcutCatalog.find(item.id())).isPresent());
        assertThat(items).extracting(SheetSelection.Item::group).containsSequence(SheetSelection.Group.FILE)
                .contains(SheetSelection.Group.EDIT, SheetSelection.Group.VIEW, SheetSelection.Group.SIMULATE,
                        SheetSelection.Group.HELP);
    }

    @Test
    void theSheetShowsTheGroupsWithTheCurrentKeys() {
        ShortcutSheetPanel panel = openSheet();

        assertThat(panel.groupTitles()).containsExactly("Archivo", "Editar", "Ver", "Simular", "Ayuda");
        assertThat(panel.rows()).contains("Ctrl+N · Nuevo", "Ctrl+Z · Deshacer", "Supr · Borrar",
                "Ctrl+Mayús+Z · Rehacer", "F6 · Avanzar un paso fijo", "? · Atajos de teclado");
        assertThat(panel.rows()).as("the waveforms toggle has no key by default").noneMatch(
                row -> row.endsWith("· Formas de onda"));
    }

    @Test
    void holdingTheQuestionMarkTogglesOnceWhereTheSystemRepeatsWithoutReleases() {
        dispatch(pressed(KeyEvent.VK_QUOTE, 1));
        questionMarkOn(view.canvas());
        for (int repeat = 0; repeat < 5; repeat++) {
            dispatch(pressed(KeyEvent.VK_QUOTE, 2 + repeat));
            questionMarkOn(view.canvas());
            assertThat(sheet()).as("repeat %d", repeat).isPresent();
        }
        dispatch(released(KeyEvent.VK_QUOTE, 10));

        dispatch(pressed(KeyEvent.VK_QUOTE, 20));
        questionMarkOn(view.canvas());
        assertThat(sheet()).as("a new press closes it").isEmpty();
    }

    @Test
    void holdingTheQuestionMarkTogglesOnceWhereTheSystemRepeatsWithReleaseAndPressPairs() {
        dispatch(pressed(KeyEvent.VK_SLASH, 1));
        questionMarkOn(view.canvas());
        for (int repeat = 0; repeat < 5; repeat++) {
            long when = 2 + repeat;
            dispatch(released(KeyEvent.VK_SLASH, when)); // X11: each repeat is a release and a press at one instant
            dispatch(pressed(KeyEvent.VK_SLASH, when));
            questionMarkOn(view.canvas());
            assertThat(sheet()).as("repeat %d", repeat).isPresent();
        }
        dispatch(released(KeyEvent.VK_SLASH, 10));

        dispatch(pressed(KeyEvent.VK_SLASH, 20));
        questionMarkOn(view.canvas());
        assertThat(sheet()).isEmpty();
    }

    private KeyEvent pressed(int keyCode, long when) {
        return new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, when, InputEvent.SHIFT_DOWN_MASK, keyCode, '?');
    }

    private KeyEvent released(int keyCode, long when) {
        return new KeyEvent(view.canvas(), KeyEvent.KEY_RELEASED, when, InputEvent.SHIFT_DOWN_MASK, keyCode, '?');
    }

    @Test
    void theOptionIsTheSwitchOfVSimWithItsLabel() {
        ShortcutSheetPanel panel = openSheet();

        assertThat(panel.showUsedShortcuts()).isInstanceOf(cl.almejo.vsim.ui.theme.SwitchBox.class);
        assertThat(panel.showUsedShortcutsLabel().getText()).isEqualTo("Mostrar los atajos al usarlos");
        assertThat(panel.showUsedShortcutsLabel().getLabelFor()).isSameAs(panel.showUsedShortcuts());
    }

    @Test
    void theCardIsCentredOnTheDimmedAreaAtMostAboutNineHundredWide() {
        ShortcutSheetPanel panel = openSheet();
        java.awt.Rectangle card = panel.card().getBounds();

        assertThat(card.width).isPositive().isLessThanOrEqualTo(ShortcutSheetPanel.MAXIMUM_CARD_WIDTH);
        assertThat(Math.abs(card.x + card.width / 2 - panel.getWidth() / 2)).isLessThanOrEqualTo(1);
        assertThat(card.y).as("the circuit shows around it").isPositive();
    }

    @Test
    void theWholeCardFitsAWindowNineHundredPixelsHigh() {
        root.setSize(1400, 900);
        layOut(root);
        ShortcutSheetPanel panel = openSheet();

        assertThat(panel.card().getHeight()).as("nothing cut off")
                .isEqualTo(panel.card().getPreferredSize().height);
        assertThat(panel.card().getY()).isPositive();
        javax.swing.JViewport viewport = panel.groupsScroll().getViewport();
        assertThat(viewport.getExtentSize().height).as("no scroll")
                .isGreaterThanOrEqualTo(viewport.getView().getPreferredSize().height);
    }

    @Test
    void aClickOnTheDimmedAreaClosesTheSheetAndOneOnTheCardDoesNot() {
        ShortcutSheetPanel panel = openSheet();
        java.awt.Rectangle card = panel.card().getBounds();

        click(panel, card.x + 5, card.y + 5);
        assertThat(sheet()).isPresent();
        click(panel, 2, 2);
        assertThat(sheet()).isEmpty();
    }

    private static void click(Component target, int x, int y) {
        java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(target,
                java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener listener : target.getMouseListeners()) {
            listener.mouseClicked(event);
        }
    }

    @Test
    void aReboundShortcutShowsAtOnceAndAnUnboundOneLeavesTheSheet() {
        ShortcutSheetPanel panel = openSheet();
        KeyBindings bindings = new KeyBindings(preferences);

        bindings.assign("edit.undo", KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
        bindings.clear("edit.delete");

        assertThat(panel.rows()).contains("Ctrl+J · Deshacer").noneMatch(row -> row.endsWith("· Borrar"));
    }

    @Test
    void theCheckboxAndTheViewItemAreTheSameStateBothWays() {
        ShortcutSheetPanel panel = openSheet();
        assertThat(panel.showUsedShortcuts().isSelected()).isFalse();

        panel.showUsedShortcuts().doClick();
        assertThat(preferences.showUsedShortcuts()).isTrue();
        assertThat(sheet()).as("clicking it never closes the sheet").isPresent();

        preferences.setShowUsedShortcuts(false);
        assertThat(panel.showUsedShortcuts().isSelected()).isFalse();
    }

    @Test
    void theChipsHideWhileTheSheetIsOpen() {
        preferences.setShowUsedShortcuts(true);
        Action centerView = view.actions().centerView();
        clickViewItem(centerView);
        assertThat(chips()).hasSize(1);

        openSheet();
        assertThat(chips()).as("the chip shown goes").isEmpty();
        clickViewItem(centerView);
        assertThat(chips()).as("no new chip while open").isEmpty();

        questionMarkOn(view.canvas());
        clickViewItem(centerView);
        assertThat(chips()).hasSize(1);
    }

    private void clickViewItem(Action action) {
        for (Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenuItem item && item.getAction() == action) {
                item.doClick();
                return;
            }
        }
        throw new AssertionError("no Ver item for " + action.getValue(Action.NAME));
    }

    @Test
    void theLinkClosesTheSheetAndOpensPreferencesOnTheShortcutsGroup() {
        List<String> opened = new ArrayList<>();
        Action preferencesAction = Actions.of("Preferencias…", () -> opened.add("last seen"));
        List<Action> groups = new ArrayList<>();
        for (String groupId : PreferencesDialog.GROUP_IDS) {
            groups.add(Actions.of("group " + groupId, () -> opened.add(groupId)));
        }
        preferencesAction.putValue(Actions.PALETTE_ENTRIES, List.copyOf(groups));
        view.registerShortcutAction("file.preferences", preferencesAction);
        ShortcutSheetPanel panel = openSheet();

        panel.allShortcutsLink().doClick();

        assertThat(sheet()).isEmpty();
        assertThat(opened).containsExactly("shortcuts");
    }

    @Test
    void theStripShowsThePhraseOfTheGestureAndHidesWithoutOne() {
        ShortcutSheetPanel panel = openSheet();
        assertThat(panel.gestureShown()).isFalse();

        view.canvas().setTool(new PhraseTool("Dibujando un cable: clic: esquina"));
        assertThat(panel.gestureShown()).isTrue();
        assertThat(panel.gestureText()).contains("Dibujando un cable: clic: esquina");

        view.canvas().useDefaultTool();
        assertThat(panel.gestureShown()).isFalse();
    }

    @Test
    void itFadesInWithAnimationsAndAppearsWholeWithout() {
        ShortcutSheetPanel faded = openSheet();
        assertThat(faded.opacity()).isLessThan(1f);
        clock.addAndGet(500);
        tickers.forEach(ticker -> ticker.advance(16));
        assertThat(faded.opacity()).isEqualTo(1f);
        questionMarkOn(view.canvas());

        animations = false;
        assertThat(openSheet().opacity()).isEqualTo(1f);
    }

    @Test
    void itCoversTheCircuitAreaNotTheMenus() {
        ShortcutSheetPanel panel = openSheet();

        assertThat(view.canvasPanes().getWidth()).isPositive();
        java.awt.Rectangle area = javax.swing.SwingUtilities.convertRectangle(view.canvasPanes(),
                new java.awt.Rectangle(view.canvasPanes().getSize()), root.getLayeredPane());
        assertThat(panel.getBounds()).as("the whole circuit area, no band of live canvas").isEqualTo(area);
    }

    @Test
    void anArrowClosesTheSheetAndMovesTheSelectionOnce() {
        cl.almejo.vsim.core.document.Component gate = UiTestSupport.place(openDocument,
                StandardComponentTypes.BUFFER, 5, 5, java.util.Map.of());
        view.canvas().selection().select(gate.id());
        openSheet();

        keyOnCanvas(KeyEvent.VK_RIGHT, 0, KeyEvent.CHAR_UNDEFINED);

        assertThat(sheet()).isEmpty();
        assertThat(openDocument.document().component(gate.id())
                .orElseThrow().position()).isEqualTo(new cl.almejo.vsim.core.geometry.GridPoint(6, 5));
    }

    @Test
    void aModifierAloneKeepsTheSheetOpen() {
        openSheet();

        keyOnCanvas(KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK, KeyEvent.CHAR_UNDEFINED);
        keyOnCanvas(KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK, KeyEvent.CHAR_UNDEFINED);

        assertThat(sheet()).isPresent();
    }

    @Test
    void thePressOfTheQuestionMarkKeyLeavesTheClosingToItsTypedCharacter() {
        openSheet();

        keyOnCanvas(KeyEvent.VK_QUOTE, InputEvent.SHIFT_DOWN_MASK, '?');
        assertThat(sheet()).isPresent();
        questionMarkOn(view.canvas());

        assertThat(sheet()).isEmpty();
    }

    @Test
    void spaceDuringAWireClosesTheSheetAndFlipsTheElbow() {
        int[] flips = new int[1];
        view.canvas().setTool(new Tool() {
            @Override
            public boolean flipElbow(ToolContext context) {
                flips[0]++;
                return true;
            }
        });
        openSheet();

        keyOnCanvas(KeyEvent.VK_SPACE, 0, ' ');
        KeyEvent release = new KeyEvent(view.canvas(), KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_SPACE, ' ');
        dispatch(release);
        for (java.awt.event.KeyListener listener : view.canvas().getKeyListeners()) {
            listener.keyReleased(release);
        }

        assertThat(sheet()).isEmpty();
        assertThat(flips[0]).isEqualTo(1);
    }

    @Test
    void closingGivesTheKeyboardBackToTheCanvasOnlyWhenItHadIt() {
        JTextField field = new JTextField();
        view.add(field);
        focusOwner = field;
        openSheet();

        dispatch(escape(view.canvas()));

        assertThat(focusRequests).doesNotContain(view.canvas());
    }

    /** A key pressed on the canvas as Swing delivers it: the key hooks, then the canvas's listeners and bindings. */
    private void keyOnCanvas(int keyCode, int modifiers, char character) {
        KeyEvent event = new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, character);
        if (dispatch(event)) {
            return;
        }
        for (java.awt.event.KeyListener listener : view.canvas().getKeyListeners()) {
            listener.keyPressed(event);
        }
        if (!event.isConsumed()) {
            javax.swing.SwingUtilities.processKeyBindings(event);
        }
    }

    @Test
    void inAShortAreaTheGroupsScrollAndTheFootStaysVisible() {
        root.setSize(1280, 650);
        layOut(root);
        ShortcutSheetPanel panel = openSheet();
        javax.swing.JScrollPane groups = panel.groupsScroll();

        assertThat(groups.getViewport().getExtentSize().height)
                .isLessThan(groups.getViewport().getView().getPreferredSize().height);
        java.awt.Rectangle foot = javax.swing.SwingUtilities.convertRectangle(panel.foot().getParent(),
                panel.foot().getBounds(), panel.card());
        assertThat(new java.awt.Rectangle(panel.card().getSize()).contains(foot)).isTrue();
        assertThat(panel.card().getHeight()).isLessThanOrEqualTo(panel.getHeight());
        assertThat(groups.isFocusable()).isFalse();
    }

    @Test
    void disposingTheViewClosesTheSheetAndTakesEveryHookBack() {
        int dispatchersBefore = dispatchers.size();
        int preferencesListeners = preferences.listenerCount();
        openSheet();
        JMenu help = view.menu(MenuId.HELP);
        int helpItems = help.getMenuComponentCount();

        view.dispose();

        assertThat(sheet()).isEmpty();
        assertThat(dispatchers).hasSizeLessThan(dispatchersBefore);
        assertThat(preferences.listenerCount()).isLessThan(preferencesListeners);
        assertThat(help.getMenuComponentCount()).isLessThan(helpItems);
        assertThat(view.canvas().isEscapeClaimed()).isFalse();
    }

    /** A tool that always has a gesture phrase to show. */
    private static final class PhraseTool implements Tool {
        private final String phrase;

        PhraseTool(String phrase) {
            this.phrase = phrase;
        }

        @Override
        public String gesturePhrase(ToolContext context) {
            return phrase;
        }
    }
}
