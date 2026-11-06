package cl.almejo.vsim.ui.actions;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.Optional;

/**
 * Where the keyboard focus is, and asking for it. The editor's panels go through this instead of Swing directly so the
 * keyboard moves between them (Alt+1, the arrows of a tree, Esc) can be tested headless, where nothing ever gets the
 * focus; tests give a recording one.
 */
public interface KeyboardFocus {

    /**
     * The client property a panel sets to the component that takes the focus when the panel is focused as a whole (the
     * tree of «Circuito» or «Paleta», not its search box).
     */
    String TARGET_PROPERTY = "vsim.keyboardFocus.target";

    /** Swing's: the window's focus owner, and {@link Component#requestFocusInWindow()}. */
    KeyboardFocus SWING = new KeyboardFocus() {
        @Override
        public Optional<Component> owner() {
            return Optional.ofNullable(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        }

        @Override
        public void request(Component target) {
            target.requestFocusInWindow();
        }
    };

    /** The component that has the keyboard focus now; empty when none of this application's has it. */
    Optional<Component> owner();

    /** Asks for the focus to go to {@code target}. */
    void request(Component target);
}
