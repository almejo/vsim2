package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
     * The key of a {@code String} an entry of {@link #PALETTE_ENTRIES} may carry: its stable id, so a reference to the
     * entry (a news bubble) does not depend on its position.
     */
    public static final String PALETTE_ENTRY_ID = "vsim.paletteEntryId";

    /**
     * The key of a {@code String} a menu item may carry as a client property: words the command palette also finds
     * it by, though they are not shown (an example's own name, «full adder», besides «Sumador completo»).
     */
    public static final String SEARCH_TERMS = "vsim.searchTerms";

    /**
     * The key of the {@link KeyStroke} a canvas key gives its action (Supr for «Borrar», R for «Rotar ↻»): shown on its
     * menu items ({@link ShownAcceleratorItem}) and in its tooltip ({@link AcceleratorTooltip}), never bound as a menu
     * accelerator, so it never fires while the user types in a text field. {@code ShortcutsExtension} keeps it in step
     * with Preferencias ▸ Atajos.
     */
    public static final String SHOWN_ACCELERATOR = "vsim.shownAccelerator";

    /**
     * The key of a {@code Supplier<String>} an action may carry: why it is disabled right now, in the user's language
     * («Selecciona algo primero»). Its tooltip ends with it while disabled, and the command palette says it (I15).
     */
    public static final String DISABLED_REASON = "vsim.disabledReason";

    /**
     * The client property of a toolbar button that is off for a reason its action does not know (the step buttons
     * outside Paso a paso): a {@code String}, already translated, that its tooltip ends with after the key
     * («Avanzar (F7) — Activa Simulación ▸ Paso a paso»); null or absent for none.
     */
    public static final String TOOLBAR_NOTE = "vsim.toolbarNote";

    private Actions() {
    }

    /**
     * Gives {@code action} the reason it is disabled, asked each time it turns off: its tooltip says «text (key) —
     * reason» while disabled. An action without a tooltip of its own shows only the reason, and nothing while enabled.
     */
    public static void disabledBecause(Action action, Supplier<String> reason) {
        Objects.requireNonNull(reason, "reason");
        action.putValue(DISABLED_REASON, reason);
        AcceleratorTooltip.followReason(action);
    }

    /** {@code tooltip} followed by a {@link #TOOLBAR_NOTE} ({@code note}: a String or null) when there is one. */
    public static String withNote(String tooltip, Object note) {
        return note instanceof String text && !text.isBlank() ? Messages.format("tooltip.withReason", tooltip, text)
                : tooltip;
    }

    /** Why {@code action} is disabled, when it is and it says; empty while enabled or without a reason. */
    public static Optional<String> disabledReason(Action action) {
        if (action == null || action.isEnabled()
                || !(action.getValue(DISABLED_REASON) instanceof Supplier<?> reason)) {
            return Optional.empty();
        }
        return reason.get() instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
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
