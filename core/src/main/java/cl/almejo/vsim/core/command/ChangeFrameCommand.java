package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Frame;

import java.util.Objects;

/** Replaces a frame by a changed version of itself (title, tint, lock or rectangle); revert restores it exactly. */
public final class ChangeFrameCommand implements Command {

    private final Frame before;
    private final Frame after;

    public ChangeFrameCommand(Frame before, Frame after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (!before.id().equals(after.id())) {
            throw new IllegalArgumentException("frames differ in id: " + before.id() + " and " + after.id());
        }
        this.before = before;
        this.after = after;
    }

    @Override
    public void apply(CircuitDocument document) {
        document.replace(after);
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(before);
    }

    @Override
    public String description() {
        if (!before.title().equals(after.title())) {
            return "command.renameFrame";
        }
        if (before.tint() != after.tint()) {
            return "command.tintFrame";
        }
        if (before.locked() != after.locked()) {
            return after.locked() ? "command.lockFrame" : "command.unlockFrame";
        }
        return "command.resizeFrame";
    }
}
