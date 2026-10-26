package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Frame;

import java.util.Objects;

/** Replaces a frame by a changed version of itself (title, tint, lock or rectangle); revert restores it exactly. */
public final class ChangeFrameCommand extends BeforeAfterCommand<Frame> {

    public ChangeFrameCommand(Frame before, Frame after) {
        super(Objects.requireNonNull(before, "before"), Objects.requireNonNull(after, "after"));
        if (!before.id().equals(after.id())) {
            throw new IllegalArgumentException("frames differ in id: " + before.id() + " and " + after.id());
        }
    }

    @Override
    protected void set(CircuitDocument document, Frame frame) {
        document.replace(frame);
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
