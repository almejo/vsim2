package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;

/** A reversible edit. {@link #description()} is an i18n key such as "command.move". */
public interface Command {
    void apply(CircuitDocument document);

    void revert(CircuitDocument document);

    String description();
}
