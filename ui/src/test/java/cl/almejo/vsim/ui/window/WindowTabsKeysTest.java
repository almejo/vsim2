package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ctrl+Tab and Ctrl+Mayús+Tab change tab wherever the focus is in the window (round C, item 2): the split panes and the
 * inspector's own tabs bind those keys for themselves (Swing's «focusOutForward», FlatLaf's «navigateNext»), so the
 * window takes them before any component sees them.
 */
class WindowTabsKeysTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        tabs = new WindowTabs(host,
                place -> new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    private EditorView newTab() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        return tabs.addTab(document, Optional.empty());
    }

    private static <T extends Component> T first(Container container, Class<T> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container inner) {
                T found = first(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static KeyEvent pressed(Component source, int keyCode, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent ctrlTab(Component source) {
        return pressed(source, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
    }

    private static KeyEvent ctrlShiftTab(Component source) {
        return pressed(source, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @Test
    void aSplitPaneOfTheViewWouldTakeCtrlTabForItself() {
        EditorView view = newTab();
        JSplitPane split = first(view, JSplitPane.class);

        assertThat(split.getInputMap(JSplitPane.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)))
                .as("why the window must take the key first").isNotNull();
    }

    @Test
    void ctrlTabFromTheInspectorChangesTheWindowsTab() {
        EditorView first = newTab();
        EditorView second = newTab();
        java.awt.Component inspector = second.inspector();

        assertThat(tabs.tabKeys().dispatchKeyEvent(ctrlTab(inspector))).isTrue();

        assertThat(tabs.activeView()).isSameAs(first);
    }

    @Test
    void ctrlShiftTabFromTheCanvasGoesToThePreviousTab() {
        EditorView first = newTab();
        EditorView second = newTab();
        tabs.activate(first);

        assertThat(tabs.tabKeys().dispatchKeyEvent(ctrlShiftTab(first.canvas()))).isTrue();

        assertThat(tabs.activeView()).isSameAs(second);
    }

    @Test
    void ctrlPageDownAndPageUpChangeTabFromTheInspectorToo() {
        EditorView first = newTab();
        EditorView second = newTab();
        java.awt.Component inspector = second.inspector();

        assertThat(tabs.tabKeys().dispatchKeyEvent(pressed(inspector, KeyEvent.VK_PAGE_DOWN,
                InputEvent.CTRL_DOWN_MASK))).isTrue();
        assertThat(tabs.activeView()).isSameAs(first);
        assertThat(tabs.tabKeys().dispatchKeyEvent(pressed(first.canvas(), KeyEvent.VK_PAGE_UP,
                InputEvent.CTRL_DOWN_MASK))).isTrue();
        assertThat(tabs.activeView()).isSameAs(second);
    }

    @Test
    void keysOutsideTheWindowAndOtherKeysAreLeftAlone() {
        EditorView first = newTab();
        newTab();
        tabs.activate(first);

        assertThat(tabs.tabKeys().dispatchKeyEvent(ctrlTab(new JLabel("elsewhere")))).isFalse();
        assertThat(tabs.tabKeys().dispatchKeyEvent(pressed(first.canvas(), KeyEvent.VK_TAB, 0))).isFalse();
        assertThat(tabs.tabKeys().dispatchKeyEvent(new KeyEvent(first.canvas(), KeyEvent.KEY_RELEASED, 0,
                InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED))).isFalse();

        assertThat(tabs.activeView()).isSameAs(first);
    }

    @Test
    void aRebindingOfPestanaSiguienteIsWhatTheWindowTakes() {
        EditorView first = newTab();
        EditorView second = newTab();
        KeyStroke rebound = KeyStroke.getKeyStroke(KeyEvent.VK_F12, InputEvent.ALT_DOWN_MASK);
        second.actions().nextTab().putValue(Action.ACCELERATOR_KEY, rebound);

        assertThat(tabs.tabKeys().dispatchKeyEvent(ctrlTab(second.canvas()))).isFalse();
        assertThat(tabs.tabKeys().dispatchKeyEvent(pressed(second.canvas(), KeyEvent.VK_F12,
                InputEvent.ALT_DOWN_MASK))).isTrue();

        assertThat(tabs.activeView()).isSameAs(first);
        assertThat(SwingUtilities.isDescendingFrom(second.canvas(), tabs)).isTrue();
    }
}
