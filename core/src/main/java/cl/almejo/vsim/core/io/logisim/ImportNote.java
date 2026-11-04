package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.document.ElementId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Something of a Logisim file the import could not take as it is, or of a VSim circuit the export could not: the
 * report shows it to the user. {@code circuit} is the circuit it is in ({@code ""} for the whole project),
 * {@code where} the Logisim point of the component (import only; empty when it is about no component), {@code element}
 * the VSim element the user can be taken to (below), {@code messageKey} one of {@link LogisimNotes} with its
 * {@code arguments}.
 * <p>
 * {@code element} is in the main document — the one the import made, or the one exported: the part itself when the
 * note is about a part of the main circuit; when it is inside a custom component's body (VSim cannot open a body yet),
 * the first instance of the main circuit that holds that component, directly or inside others; empty for a note about
 * the whole project, or about a circuit no instance of the main circuit holds.
 */
public record ImportNote(Kind kind, String circuit, Optional<LogisimPoint> where, Optional<ElementId> element,
                         String messageKey, List<String> arguments) {

    /** How serious a note is. */
    public enum Kind {
        /**
         * Imported or exported, but the circuit does not do the same: a pin left loose that reads otherwise, a
         * connection that could not be made, an input that means something else. Listed first.
         */
        DIFFERENT,
        /** Left out: VSim (or Logisim, on export) has no equivalent (a part, a library, a shape). */
        UNSUPPORTED,
        /** Imported, but it behaves or looks somewhat differently (a delay, an edge, an option VSim lacks). */
        APPROXIMATED,
        /** Left out: the file itself is wrong there (a missing library, an unreadable value). */
        INVALID
    }

    public ImportNote {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }

    /** A note about the component at {@code where} of {@code circuit}. */
    public static ImportNote at(Kind kind, String circuit, LogisimPoint where, String messageKey,
                                String... arguments) {
        return new ImportNote(kind, circuit, Optional.of(where), Optional.empty(), messageKey, List.of(arguments));
    }

    /** A note about a circuit, or about the whole project when {@code circuit} is {@code ""}. */
    public static ImportNote about(Kind kind, String circuit, String messageKey, String... arguments) {
        return new ImportNote(kind, circuit, Optional.empty(), Optional.empty(), messageKey, List.of(arguments));
    }

    /** This note about {@code newElement} (or about no element when empty). */
    public ImportNote withElement(Optional<ElementId> newElement) {
        return new ImportNote(kind, circuit, where, newElement, messageKey, arguments);
    }

    /** This note with other arguments (the report puts a part's name in place of its type id and label). */
    public ImportNote withArguments(List<String> newArguments) {
        return new ImportNote(kind, circuit, where, element, messageKey, newArguments);
    }
}
