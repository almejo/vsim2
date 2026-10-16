package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Undo/redo stacks for one document, with a "saved" marker. */
public final class History {

    private static final int DEFAULT_LIMIT = 500;
    private static final Command UNREACHABLE = new CompositeCommand("unreachable", List.of());

    private final CircuitDocument document;
    private final int limit;
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final List<HistoryListener> listeners = new CopyOnWriteArrayList<>();
    private Command savedTop;
    /** The command the last {@link #execute} left on top; null after an undo, a redo or a clear, so a run never resumes. */
    private Command lastExecuted;

    public History(CircuitDocument document) {
        this(document, DEFAULT_LIMIT);
    }

    public History(CircuitDocument document, int limit) {
        this.document = document;
        this.limit = limit;
    }

    /**
     * Applies {@code command} and pushes it, or, when the command on top was the last one executed and says
     * {@code command} continues it ({@link Command#mergedWith}), reverts the top and applies the merged command
     * instead (in one document batch), so the merged command always starts from the state before the run.
     */
    public void execute(Command command) {
        Command top = undoStack.peek();
        if (top != null && top == lastExecuted && top != savedTop) {
            Optional<Command> merged = top.mergedWith(command);
            if (merged.isPresent()) {
                Command replacement = merged.get();
                document.batch(() -> {
                    top.revert(document);
                    replacement.apply(document);
                });
                undoStack.pop();
                undoStack.push(replacement);
                lastExecuted = replacement;
                notifyListeners();
                return;
            }
        }
        command.apply(document);
        lastExecuted = command;
        undoStack.push(command);
        if (redoStack.contains(savedTop)) {
            savedTop = UNREACHABLE;
        }
        redoStack.clear();
        while (undoStack.size() > limit) {
            Command dropped = undoStack.removeLast();
            if (savedTop == null) {
                savedTop = UNREACHABLE;
            } else if (dropped == savedTop) {
                // The saved state is the one right after the dropped command: the new empty-stack base.
                savedTop = null;
            }
        }
        notifyListeners();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        lastExecuted = null;
        Command command = undoStack.peek();
        command.revert(document);
        undoStack.pop();
        redoStack.push(command);
        notifyListeners();
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        lastExecuted = null;
        Command command = redoStack.peek();
        command.apply(document);
        redoStack.pop();
        undoStack.push(command);
        notifyListeners();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public Optional<String> undoDescription() {
        return Optional.ofNullable(undoStack.peek()).map(Command::description);
    }

    public Optional<String> redoDescription() {
        return Optional.ofNullable(redoStack.peek()).map(Command::description);
    }

    public void markSaved() {
        savedTop = undoStack.peek();
        notifyListeners();
    }

    /**
     * A recovered copy (M6-B): the document differs from its file in an unknown way, so it reports modified until the
     * next {@link #markSaved()}; no undo or redo reaches the saved state before that.
     */
    public void markUnsaved() {
        savedTop = UNREACHABLE;
        notifyListeners();
    }

    public boolean isModified() {
        return undoStack.peek() != savedTop;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        savedTop = null;
        lastExecuted = null;
        notifyListeners();
    }

    public void addListener(HistoryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HistoryListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        listeners.forEach(listener -> listener.historyChanged(this));
    }
}
