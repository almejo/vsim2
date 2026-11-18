package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.Listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The {@link DraftGuard}s of one view, in the order they were added: the window asks them before saving the view's
 * circuit, closing the view or leaving its tab. A view keeps its own (they move with it), and the window's paths
 * (closing a tab, a group, the components, the window, exiting, saving, switching tabs) ask the views they touch.
 * EDT only.
 */
public final class DraftGuards {

    private final List<DraftGuard> guards = new ArrayList<>();
    private final Listeners<Runnable> listeners = new Listeners<>();

    /** Adds {@code guard}; returns what takes it away again (running it twice does nothing more). */
    public Runnable add(DraftGuard guard) {
        Objects.requireNonNull(guard, "guard");
        guards.add(guard);
        listeners.fire(Runnable::run);
        return () -> {
            if (guards.remove(guard)) {
                listeners.fire(Runnable::run);
            }
        };
    }

    /** Whether no guard is on. */
    public boolean isEmpty() {
        return guards.isEmpty();
    }

    /**
     * Asks every guard, in order, to settle before {@code reason}; stops at the first that cancels (the guards asked
     * before it keep what they did).
     */
    public DraftGuard.Answer settle(DraftGuard.Reason reason) {
        for (DraftGuard guard : List.copyOf(guards)) {
            if (guard.settle(reason) == DraftGuard.Answer.CANCEL) {
                return DraftGuard.Answer.CANCEL;
            }
        }
        return DraftGuard.Answer.GO_ON;
    }

    /** As {@link #settle(DraftGuard.Reason)} over the guards of every view of {@code all}, in order. */
    public static DraftGuard.Answer settle(Collection<DraftGuards> all, DraftGuard.Reason reason) {
        for (DraftGuards each : List.copyOf(all)) {
            if (each.settle(reason) == DraftGuard.Answer.CANCEL) {
                return DraftGuard.Answer.CANCEL;
            }
        }
        return DraftGuard.Answer.GO_ON;
    }

    /**
     * Asks every guard of every view of {@code all}, in order, what to do on a close, doing nothing yet
     * ({@link DraftGuard#decideClosing()}); empty when one was cancelled (the ones asked before did nothing either).
     */
    public static Optional<List<DraftGuard.Closing>> decideClosing(Collection<DraftGuards> all) {
        List<DraftGuard.Closing> closings = new ArrayList<>();
        for (DraftGuards each : List.copyOf(all)) {
            for (DraftGuard guard : List.copyOf(each.guards)) {
                Optional<DraftGuard.Closing> closing = guard.decideClosing();
                if (closing.isEmpty()) {
                    return Optional.empty();
                }
                closings.add(closing.get());
            }
        }
        return Optional.of(closings);
    }

    /**
     * Before the view's tab moves to another window: the guards whose draft cannot travel are settled as a close, in
     * order; CANCEL when one was cancelled (the move then does not happen).
     */
    public DraftGuard.Answer settleThoseThatStay() {
        for (DraftGuard guard : List.copyOf(guards)) {
            if (guard.traveller().isEmpty() && guard.settle(DraftGuard.Reason.CLOSE) == DraftGuard.Answer.CANCEL) {
                return DraftGuard.Answer.CANCEL;
            }
        }
        return DraftGuard.Answer.GO_ON;
    }

    /** What takes each travelling draft to the tab's new view in another window ({@link DraftGuard#traveller()}). */
    public List<Consumer<EditorView>> travellers() {
        return guards.stream().map(DraftGuard::traveller).flatMap(Optional::stream).toList();
    }

    /** What the tab's title adds now: the first guard's note; empty without one. */
    public Optional<String> titleNote() {
        return guards.stream().map(DraftGuard::titleNote).flatMap(Optional::stream).findFirst();
    }

    /** What the status line says when the view goes without being asked: the first guard's; empty without one. */
    public Optional<String> discardNotice() {
        return guards.stream().map(DraftGuard::discardNotice).flatMap(Optional::stream).findFirst();
    }

    /** Runs {@code listener} after a guard is added or taken away (the tab's title follows); returns its removal. */
    public Runnable addListener(Runnable listener) {
        return listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
