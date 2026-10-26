package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

/** A command that swaps one value of the document for another: apply sets {@code after}, revert sets {@code before}. */
abstract class BeforeAfterCommand<T> implements Command {

    protected final T before;
    protected final T after;

    BeforeAfterCommand(T before, T after) {
        this.before = before;
        this.after = after;
    }

    /** Puts {@code value} in the document. */
    protected abstract void set(CircuitDocument document, T value);

    @Override
    public void apply(CircuitDocument document) {
        set(document, after);
    }

    @Override
    public void revert(CircuitDocument document) {
        set(document, before);
    }
}
