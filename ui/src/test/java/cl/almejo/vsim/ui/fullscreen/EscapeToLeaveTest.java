package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.search.FindBar;
import cl.almejo.vsim.ui.search.FindFeatures;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Esc leaves presentation mode only when nothing else wants it: not from a tool at work, the find bar, an open menu
 * or a dialog; with a part that has the keyboard (the Terminal, a Teclado) Esc still leaves and ends the capture as
 * today, and F11 goes to the part when it sends F keys. Keys are resolved as Swing does, without a shown window.
 */
class EscapeToLeaveTest {

    private FullScreenFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new FullScreenExtension()));
        view = fixture.addTab();
        fixture.features.enter();
        fixture.layOut();
    }

    @AfterEach
    void close() {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        fixture.close();
    }

    private static KeyEvent pressOn(java.awt.Component source, int keyCode, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    /** What the focus manager does with a key: the dispatcher first, then the component, then the post-processor. */
    private void dispatch(KeyEvent event) {
        EscapeToLeave escape = fixture.features.escape();
        if (escape.dispatchKeyEvent(event)) {
            return;
        }
        if (event.getComponent() instanceof CircuitCanvas canvas) {
            for (KeyListener listener : canvas.getKeyListeners()) {
                listener.keyPressed(event);
            }
        }
        if (!event.isConsumed()) {
            SwingUtilities.processKeyBindings(event);
        }
        escape.postProcessKeyEvent(event);
    }

    @Test
    void escapeOnTheCanvasWithNothingGoingOnLeaves() {
        dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).isFalse();
    }

    @Test
    void escapeLeavesTheSelectionAsItWas() {
        Component lamp = UiTestSupport.place(view.openDocument(), StandardComponentTypes.SWITCH, 5, 5, Map.of());
        view.selection().select(lamp.id());
        dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).isFalse();
        assertThat(view.selection().ids()).containsExactly(lamp.id());
    }

    @Test
    void escapeGoesToAToolAtWorkFirst() {
        view.actions().panTool().actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, ""));
        dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).as("Esc ended the tool, not the mode").isTrue();
        assertThat(view.canvas().tool()).isSameAs(view.canvas().defaultTool());
        dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).isFalse();
    }

    @Test
    void escapeInTheFindBarClosesOnlyTheFindBar() {
        FindFeatures find = new FindFeatures(view);
        try {
            find.findAction().actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, ""));
            FindBar bar = findBar(view).orElseThrow();
            dispatch(pressOn(bar.field(), KeyEvent.VK_ESCAPE, 0));
            assertThat(find.isOpen()).isFalse();
            assertThat(fixture.features.isActive()).isTrue();
        } finally {
            find.dispose();
        }
    }

    @Test
    void escapeWithAMenuOpenOnlyClosesTheMenu() {
        JMenu viewMenu = (JMenu) view.menuBar().getComponent(0);
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{view.menuBar(), viewMenu});
        KeyEvent escape = pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0);
        assertThat(fixture.features.escape().dispatchKeyEvent(escape)).isFalse();
        MenuSelectionManager.defaultManager().clearSelectedPath(); // the menu took the key and closed
        fixture.features.escape().postProcessKeyEvent(escape);
        assertThat(fixture.features.isActive()).isTrue();
    }

    @Test
    void escapeElsewhereInTheWindowThatNobodyTakesLeaves() {
        dispatch(pressOn(view.inspector(), KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).isFalse();
    }

    @Test
    void escapeInADialogNeverLeaves() {
        JRootPane dialog = new JRootPane();
        JPanel content = new JPanel();
        dialog.setContentPane(content);
        dispatch(pressOn(content, KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).isTrue();
    }

    @Test
    void shiftEscapeIsNotTheWayOut() {
        dispatch(pressOn(view.inspector(), KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK));
        assertThat(fixture.features.isActive()).isTrue();
    }

    @Test
    void withAPartThatHasTheKeyboardEscapeStillLeavesAndEndsTheCapture() {
        Component keyboard = UiTestSupport.place(view.openDocument(), StandardComponentTypes.KEYBOARD, 10, 10,
                Map.of());
        view.keyboardCapture().capture(keyboard.id().value());
        dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
        assertThat(fixture.features.isActive()).isFalse();
        assertThat(view.keyboardCapture().captured()).isEmpty();
    }

    @Test
    void withAPartThatSendsFKeysF11GoesToThePart() {
        Component keyboard = UiTestSupport.place(view.openDocument(), StandardComponentTypes.KEYBOARD, 10, 10,
                Map.of(KeyEncoding.ARROWS_AS_SEQUENCES, true));
        view.keyboardCapture().capture(keyboard.id().value());
        KeyEvent f11 = pressOn(view.canvas(), KeyEvent.VK_F11, 0);
        dispatch(f11);
        assertThat(f11.isConsumed()).as("the part took it: no menu accelerator sees it").isTrue();
        assertThat(fixture.features.isActive()).isTrue();
    }

    /** The final review's M2: something shown over a pane that closes on Esc (the «Mirar adentro» magnifier). */
    @Test
    void escapeGoesFirstToWhatAPaneShowsThatWantsIt() {
        boolean[] magnifierShown = {true};
        Runnable removeClaim = view.canvas().addEscapeClaim(() -> magnifierShown[0]);
        try {
            view.canvas().addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        magnifierShown[0] = false; // the magnifier goes, without consuming the key
                    }
                }
            });
            dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
            assertThat(magnifierShown[0]).as("the magnifier got the key").isFalse();
            assertThat(fixture.features.isActive()).as("and the mode stays").isTrue();
            dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
            assertThat(fixture.features.isActive()).isFalse();
        } finally {
            removeClaim.run();
        }
    }

    /** The final review's M3: the speed list opened with the mouse while the canvas keeps the focus. */
    @Test
    void escapeWithAComboListOpenClosesTheListFirst() {
        boolean[] listShown = {true};
        JComboBox<String> combo = new JComboBox<>() {
            @Override
            public boolean isPopupVisible() {
                return listShown[0];
            }

            @Override
            public void setPopupVisible(boolean visible) {
                listShown[0] = visible;
            }
        };
        view.toolBar().add(combo);
        try {
            dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
            assertThat(listShown[0]).as("the list closed").isFalse();
            assertThat(fixture.features.isActive()).isTrue();
            dispatch(pressOn(view.canvas(), KeyEvent.VK_ESCAPE, 0));
            assertThat(fixture.features.isActive()).isFalse();
        } finally {
            view.toolBar().remove(combo);
        }
    }

    private static Optional<FindBar> findBar(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof FindBar bar) {
                return Optional.of(bar);
            }
            if (child instanceof Container inner) {
                Optional<FindBar> found = findBar(inner);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
