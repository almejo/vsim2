package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.edit.PreparedEdit;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.HistoryListener;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The one step of a task that waits for «Siguiente» as a ghost (spec §5.1): the edit planned against the version the
 * user sees, its preview, the answer of the edit call that proposed it (a retry gets it again) and the outcome
 * {@code await_step} waits for. It watches the circuit's history while it waits: any change of its content (the
 * user's edit, an undo, a redo) makes it stale; saving (the history's saved mark moving) does not (IA.3), as the
 * circuit's version does not move then either.
 */
final class PendingStep {

    private final PreparedEdit prepared;
    private final StepPreview preview;
    private final PresentedEdit answer;
    private final CompletableFuture<StepOutcome> outcome = new CompletableFuture<>();
    private final History history;
    private final HistoryListener watcher;

    /** Editor thread: starts watching {@code history}; {@code changed} hears its first change. */
    PendingStep(PreparedEdit prepared, StepPreview preview, History history, Consumer<PendingStep> changed) {
        this.prepared = Objects.requireNonNull(prepared, "prepared");
        this.preview = Objects.requireNonNull(preview, "preview");
        this.answer = new PresentedEdit(prepared.proposal(), Optional.of(StepStatus.PROPOSED));
        this.history = Objects.requireNonNull(history, "history");
        Command topWhenProposed = history.undoCommand().orElse(null);
        // Every edit, undo, redo or clear changes the command on top of the undo stack; a save never does.
        this.watcher = changedHistory -> {
            if (changedHistory.undoCommand().orElse(null) != topWhenProposed) {
                changed.accept(this);
            }
        };
        history.addListener(watcher);
    }

    PreparedEdit prepared() {
        return prepared;
    }

    StepPreview preview() {
        return preview;
    }

    PresentedEdit answer() {
        return answer;
    }

    CompletableFuture<StepOutcome> outcome() {
        return outcome;
    }

    /** Stops watching the history (its listener list is thread-safe) and answers {@code await_step}. */
    void resolve(StepOutcome result) {
        history.removeListener(watcher);
        outcome.complete(result);
    }

    /** Stops watching the history before the step itself is written to it. */
    void stopWatching() {
        history.removeListener(watcher);
    }
}
