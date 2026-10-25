package cl.almejo.vsim.core.component.appearance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The bounded undo history owned by one open shape editor; never persisted or shared with the circuit. */
public final class DraftHistory {
    private static final int LIMIT = 200;
    private final List<SymbolDraft> states = new ArrayList<>();
    private int index;

    public DraftHistory(SymbolDraft initial) {
        states.add(Objects.requireNonNull(initial, "initial"));
    }

    public SymbolDraft current() {
        return states.get(index);
    }

    public boolean canUndo() {
        return index > 0;
    }

    public boolean canRedo() {
        return index + 1 < states.size();
    }

    public void update(SymbolDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (current().sameEdit(draft)) {
            states.set(index, draft);
            return;
        }
        states.subList(index + 1, states.size()).clear();
        states.add(draft);
        if (states.size() > LIMIT) {
            states.removeFirst();
        }
        index = states.size() - 1;
    }

    public void undo() {
        if (canUndo()) {
            index--;
        }
    }

    public void redo() {
        if (canRedo()) {
            index++;
        }
    }
}
