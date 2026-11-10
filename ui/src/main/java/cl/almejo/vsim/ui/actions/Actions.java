package cl.almejo.vsim.ui.actions;

import java.awt.event.ActionEvent;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;

/** Swing {@link Action}s that just run a {@link Runnable}, so a feature never writes an anonymous class for one. */
public final class Actions {

    /**
     * The key of a {@code List<Action>} an action may carry: entries the command palette lists right after the
     * action's own menu item, each under its own {@link Action#NAME} (Archivo ▸ Preferencias… offers one per tab,
     * «Preferencias ▸ Atajos de teclado»).
     */
    public static final String PALETTE_ENTRIES = "vsim.paletteEntries";

    /**
     * The key of a {@code String} a menu item may carry as a client property: words the command palette also finds
     * it by, though they are not shown (an example's own name, «full adder», besides «Sumador completo»).
     */
    public static final String SEARCH_TERMS = "vsim.searchTerms";

    private Actions() {
    }

    /** A named action ({@link Action#NAME} is {@code name}, already translated). */
    public static Action of(String name, Runnable behavior) {
        return of(name, null, behavior);
    }

    /** A named action with an {@link Action#ACCELERATOR_KEY} when {@code accelerator} is not null. */
    public static Action of(String name, KeyStroke accelerator, Runnable behavior) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
        if (accelerator != null) {
            action.putValue(Action.ACCELERATOR_KEY, accelerator);
        }
        return action;
    }

    /** An action without {@link Action#NAME}, for key bindings (a name would show as text on a button). */
    public static Action unnamed(Runnable behavior) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
    }

    /** Like {@link #unnamed(Runnable)}, but {@code isEnabled()} asks {@code condition} each time. */
    public static Action unnamed(BooleanSupplier condition, Runnable behavior) {
        return new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return condition.getAsBoolean();
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                behavior.run();
            }
        };
    }
}
