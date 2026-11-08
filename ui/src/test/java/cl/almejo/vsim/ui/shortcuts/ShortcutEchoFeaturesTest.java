package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.capture.CaptureButtonExtension;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/** Ver ▸ «Mostrar los atajos que se usan»: a chip per action run by key, menu item or toolbar button. */
class ShortcutEchoFeaturesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<ManualFrameTicker> tickers = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<KeyEventDispatcher> dispatchers = new ArrayList<>();
    private Component focusOwner;
    private AWTEvent currentEvent;
    private boolean mac;
    private OpenDocument openDocument;
    private EditorView view;
    private JRootPane root;

    @BeforeEach
    void aViewInAWindow() {
        Messages.initialize(Messages.SPANISH);
        preferences.setShowUsedShortcuts(true);
        openDocument = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null, false);
        buildView();
    }

    private void buildView() {
        ShortcutEchoExtension extension = new ShortcutEchoExtension(new ShortcutEchoExtension.Seams(() -> {
            ManualFrameTicker ticker = new ManualFrameTicker();
            tickers.add(ticker);
            return ticker;
        }, clock::get, candidate -> true, new CaptureButtonExtension.Dispatchers() {
            @Override
            public void add(KeyEventDispatcher dispatcher) {
                dispatchers.add(dispatcher);
            }

            @Override
            public void remove(KeyEventDispatcher dispatcher) {
                dispatchers.remove(dispatcher);
            }
        }, () -> focusOwner, () -> currentEvent, mac));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(extension, new ShortcutsExtension()));
        view = new EditorView(openDocument, services, UiTestSupport.NO_WINDOW);
        root = new JRootPane();
        root.setJMenuBar(view.menuBar());
        root.setContentPane(view);
        root.setSize(900, 600);
        layOut(root);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private List<String> chips() {
        List<CanvasNotice> found = new ArrayList<>();
        collect(root.getLayeredPane(), found);
        return found.stream().map(CanvasNotice::text).toList();
    }

    private static void collect(Component component, List<CanvasNotice> found) {
        if (component instanceof CanvasNotice notice) {
            found.add(notice);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, found);
            }
        }
    }

    private JMenuItem menuItem(MenuId menu, Action action) {
        int index = Menus.indexOf(view.menu(menu), action);
        assertThat(index).isGreaterThanOrEqualTo(0);
        return (JMenuItem) view.menu(menu).getMenuComponent(index);
    }

    private JMenuItem viewItemNamed(String text) {
        for (Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no Ver item " + text);
    }

    private boolean keyOnCanvas(int keyCode, int modifiers) {
        KeyEvent event = new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, modifiers, keyCode,
                KeyEvent.CHAR_UNDEFINED);
        boolean taken = false;
        for (KeyEventDispatcher dispatcher : dispatchers) {
            taken |= dispatcher.dispatchKeyEvent(event);
        }
        return taken;
    }

    private AbstractButton toolbarButtonOf(Action action) {
        for (Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() == action) {
                return button;
            }
        }
        throw new AssertionError("no toolbar button for " + action.getValue(Action.NAME));
    }

    @Test
    void aMenuItemWithAShortcutShowsItsShortcutAndName() {
        menuItem(MenuId.EDIT, view.selectionActions().selectAll()).doClick();

        assertThat(chips()).containsExactly("Ctrl+A · Seleccionar todo");
    }

    @Test
    void anActionWithoutShortcutShowsItsNameOnly() {
        menuItem(MenuId.VIEW, view.actions().centerView()).doClick();

        assertThat(chips()).containsExactly("Centrar circuito");
    }

    @Test
    void aToolbarButtonShowsTheShortcutOfItsAction() {
        toolbarButtonOf(view.actions().newDocument()).doClick();

        assertThat(chips()).singleElement().asString().startsWith("Ctrl+N · ");
    }

    @Test
    void aCanvasKeyShowsItsChipAndIsNotConsumed() {
        boolean taken = keyOnCanvas(KeyEvent.VK_1, InputEvent.SHIFT_DOWN_MASK);

        assertThat(taken).as("the echo never takes the key").isFalse();
        assertThat(chips()).singleElement().asString().endsWith("1 · Ver todo el circuito");
    }

    @Test
    void aKeyWithNoBindingShowsNothing() {
        keyOnCanvas(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);

        assertThat(chips()).isEmpty();
    }

    @Test
    void typingInATextFieldShowsNothing() {
        JTextField field = new JTextField();
        focusOwner = field;
        currentEvent = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_A,
                KeyEvent.CHAR_UNDEFINED);

        menuItem(MenuId.EDIT, view.selectionActions().selectAll()).doClick();
        KeyEvent typed = new KeyEvent(field, KeyEvent.KEY_PRESSED, 0, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_1, '!');
        dispatchers.forEach(dispatcher -> dispatcher.dispatchKeyEvent(typed));

        assertThat(chips()).isEmpty();
    }

    @Test
    void aMouseClickOnAMenuItemShowsEvenWithAFieldFocused() {
        focusOwner = new JTextField();
        currentEvent = new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0, 1, 1, 1, false);

        menuItem(MenuId.EDIT, view.selectionActions().selectAll()).doClick();

        assertThat(chips()).hasSize(1);
    }

    @Test
    void withTheOptionOffNothingShowsAndTurningItOnStartsShowing() {
        preferences.setShowUsedShortcuts(false);
        menuItem(MenuId.VIEW, view.actions().centerView()).doClick();
        assertThat(chips()).isEmpty();

        preferences.setShowUsedShortcuts(true);
        menuItem(MenuId.VIEW, view.actions().centerView()).doClick();
        assertThat(chips()).hasSize(1);
    }

    @Test
    void theOptionIsOffByDefaultAndTheViewItemFollowsIt() {
        assertThat(AppPreferences.inMemory().showUsedShortcuts()).isFalse();
        JMenuItem item = viewItemNamed(Messages.text("action.showUsedShortcuts"));
        assertThat(item.isSelected()).isTrue();

        item.doClick();

        assertThat(preferences.showUsedShortcuts()).isFalse();
        assertThat(item.isSelected()).isFalse();
    }

    @Test
    void atMostThreeStackAndTheyExpireAfterAboutASecondAndAHalf() {
        for (int count = 0; count < 4; count++) {
            menuItem(MenuId.VIEW, view.actions().centerView()).doClick();
        }
        assertThat(chips()).hasSize(3);

        clock.addAndGet(1_600);
        tickers.forEach(ticker -> ticker.advance(16));

        assertThat(chips()).isEmpty();
    }

    @Test
    void theShortcutIsInThePlatformNotation() {
        view.dispose();
        mac = true;
        buildView();

        menuItem(MenuId.EDIT, view.selectionActions().selectAll()).doClick();

        assertThat(chips()).singleElement().asString().matches("\\S+A · Seleccionar todo").doesNotContain("+");
        assertThat(ShortcutEchoFeatures.describe(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true)).isEqualTo("⇧⌘Z");
    }

    @Test
    void theShortcutFollowsTheUsersBindings() {
        new KeyBindings(preferences).assign("view.centerView", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));

        menuItem(MenuId.VIEW, view.actions().centerView()).doClick();

        assertThat(chips()).containsExactly("F7 · Centrar circuito");
    }
}
