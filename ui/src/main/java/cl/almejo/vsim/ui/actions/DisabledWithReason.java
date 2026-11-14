package cl.almejo.vsim.ui.actions;

import javax.swing.Action;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;

/**
 * Holds an action disabled with the reason as its tooltip ({@link Action#SHORT_DESCRIPTION}, which its menu item, its
 * toolbar button and «Acciones» show), whatever its owner sets meanwhile: the owner keeps refreshing it as usual
 * ({@code EditorActions.refresh}, a feature following the selection, a rebound key rewriting the tooltip), and what it
 * set last comes back when the hold is released. A machine's tab holds the circuit actions this way (spec
 * 2026-11-14 §1: «lo que no aplica se deshabilita con un tooltip que dice por qué»). EDT only.
 */
public final class DisabledWithReason {

    private static final String ENABLED_PROPERTY = "enabled";

    private DisabledWithReason() {
    }

    /**
     * Disables {@code action} with {@code reason} as its tooltip until the returned runnable runs (calling it twice
     * does nothing more).
     */
    public static Runnable disable(Action action, String reason) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reason, "reason");
        Hold hold = new Hold(action, reason);
        hold.start();
        return hold::release;
    }

    /** One held action: what its owner wants, kept aside, and the listener that puts the hold back after a change. */
    private static final class Hold {
        private final Action action;
        private final String reason;
        private boolean wantedEnabled;
        private Object wantedDescription;
        private boolean released;
        private boolean asserting;
        private final PropertyChangeListener listener = this::changed;

        Hold(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        void start() {
            wantedEnabled = action.isEnabled();
            wantedDescription = action.getValue(Action.SHORT_DESCRIPTION);
            assertHold();
            action.addPropertyChangeListener(listener);
        }

        private void changed(PropertyChangeEvent event) {
            if (asserting) {
                return;
            }
            if (ENABLED_PROPERTY.equals(event.getPropertyName())) {
                wantedEnabled = Boolean.TRUE.equals(event.getNewValue());
            } else if (Action.SHORT_DESCRIPTION.equals(event.getPropertyName())) {
                wantedDescription = event.getNewValue();
            } else {
                return;
            }
            assertHold();
        }

        private void assertHold() {
            asserting = true;
            try {
                action.setEnabled(false);
                action.putValue(Action.SHORT_DESCRIPTION, reason);
            } finally {
                asserting = false;
            }
        }

        void release() {
            if (released) {
                return;
            }
            released = true;
            action.removePropertyChangeListener(listener);
            action.putValue(Action.SHORT_DESCRIPTION, wantedDescription);
            action.setEnabled(wantedEnabled);
        }
    }
}
