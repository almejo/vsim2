package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** The command the last notification applied, reverted or merged; null after a save, a clear or markUnsaved. */
    private Command lastChange;

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
     *
     * <p>The merge is failure-atomic: when reverting the top or applying the merged command throws, the elements are
     * put back exactly as they were (with the top applied, so the stacks still match the document) and
     * {@code command} is executed on its own as a new step instead.
     */
    public void execute(Command command) {
        Command top = undoStack.peek();
        if (top != null && top == lastExecuted && top != savedTop) {
            Optional<Command> merged = top.mergedWith(command);
            if (merged.isPresent() && executeMerged(top, merged.get())) {
                return;
            }
        }
        command.apply(document);
        lastExecuted = command;
        lastChange = command;
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

    /**
     * Replaces {@code top} by {@code replacement}; false, with the document as it was, when that throws. When the
     * replacement {@link Command#changesNothing() changes nothing} (a run that came back to where it started), the top is
     * reverted and popped and nothing is pushed: the run leaves no undo step, and the modified flag is what it was
     * before the run.
     */
    private boolean executeMerged(Command top, Command replacement) {
        boolean dropRun = replacement.changesNothing();
        Map<ElementId, DocumentElement> elementsBefore = new LinkedHashMap<>();
        Map<ElementId, Long> ordersBefore = new HashMap<>();
        for (DocumentElement element : document.elements()) {
            elementsBefore.put(element.id(), element);
            ordersBefore.put(element.id(), document.orderOf(element.id()));
        }
        try {
            document.batch(() -> {
                try {
                    top.revert(document);
                    if (!dropRun) {
                        replacement.apply(document);
                    }
                } catch (RuntimeException exception) {
                    putBack(elementsBefore, ordersBefore);
                    throw exception;
                }
            });
        } catch (RuntimeException exception) {
            return false;
        }
        undoStack.pop();
        lastChange = dropRun ? top : replacement;
        if (dropRun) {
            lastExecuted = null;
        } else {
            undoStack.push(replacement);
            lastExecuted = replacement;
        }
        notifyListeners();
        return true;
    }

    /** Makes the document's elements and draw orders exactly {@code elements} at {@code orders} again. */
    private void putBack(Map<ElementId, DocumentElement> elements, Map<ElementId, Long> orders) {
        for (DocumentElement element : document.elements()) {
            ElementId id = element.id();
            if (!element.equals(elements.get(id)) || document.orderOf(id) != orders.get(id)) {
                document.remove(id);
            }
        }
        elements.forEach((id, element) -> {
            if (document.element(id).isEmpty()) {
                document.restore(element, orders.get(id));
            }
        });
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        lastExecuted = null;
        Command command = undoStack.peek();
        command.revert(document);
        lastChange = command;
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
        lastChange = command;
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

    /**
     * The command an undo would revert now. After a merged run (see {@link #execute}) it is the merged command, not the
     * one last passed to {@code execute}.
     */
    public Optional<Command> undoCommand() {
        return Optional.ofNullable(undoStack.peek());
    }

    /**
     * The command the last {@link #execute} left on top (the merged one after a merged run); empty when that execute
     * dropped a run that came back to where it started, and after an undo, a redo or a clear.
     */
    public Optional<Command> lastExecuted() {
        return Optional.ofNullable(lastExecuted);
    }

    /**
     * The command the last history notification applied, reverted or merged (the merged one after a merged run, the
     * run's top when the run was dropped); empty when that notification was a save, a clear or {@link #markUnsaved}.
     * Lets a listener tell what kind of change it is told about (a switch's value, {@link SetSwitchValueCommand}).
     */
    public Optional<Command> lastChange() {
        return Optional.ofNullable(lastChange);
    }

    public Optional<String> undoDescription() {
        return Optional.ofNullable(undoStack.peek()).map(Command::description);
    }

    public Optional<String> redoDescription() {
        return Optional.ofNullable(redoStack.peek()).map(Command::description);
    }

    public void markSaved() {
        savedTop = undoStack.peek();
        lastChange = null;
        notifyListeners();
    }

    /**
     * A recovered copy (M6-B): the document differs from its file in an unknown way, so it reports modified until the
     * next {@link #markSaved()}; no undo or redo reaches the saved state before that.
     */
    public void markUnsaved() {
        savedTop = UNREACHABLE;
        lastChange = null;
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
        lastChange = null;
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
