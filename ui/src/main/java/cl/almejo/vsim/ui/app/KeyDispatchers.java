package cl.almejo.vsim.ui.app;

import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;

/**
 * Where a feature hooks into every key of the application, before or after the components get it: the keyboard focus
 * manager in the editor ({@link #focusManager()}), a recording list in tests, which can then hand keys to the hooks
 * and count what is left installed.
 */
public interface KeyDispatchers {

    void add(KeyEventDispatcher dispatcher);

    void remove(KeyEventDispatcher dispatcher);

    void addPostProcessor(KeyEventPostProcessor postProcessor);

    void removePostProcessor(KeyEventPostProcessor postProcessor);

    /** The real thing: the current keyboard focus manager, asked at each call. */
    static KeyDispatchers focusManager() {
        return new KeyDispatchers() {
            @Override
            public void add(KeyEventDispatcher dispatcher) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
            }

            @Override
            public void remove(KeyEventDispatcher dispatcher) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
            }

            @Override
            public void addPostProcessor(KeyEventPostProcessor postProcessor) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(postProcessor);
            }

            @Override
            public void removePostProcessor(KeyEventPostProcessor postProcessor) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventPostProcessor(postProcessor);
            }
        };
    }
}
