package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Frame;

import java.util.Objects;

/** Adds one new frame; revert removes it again. */
public final class CreateFrameCommand implements Command {

    private final Frame frame;

    public CreateFrameCommand(Frame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
    }

    @Override
    public void apply(CircuitDocument document) {
        document.add(frame);
    }

    @Override
    public void revert(CircuitDocument document) {
        document.remove(frame.id());
    }

    @Override
    public String description() {
        return "command.createFrame";
    }
}
