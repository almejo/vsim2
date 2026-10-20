package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;
import java.util.Set;

/** What part of the circuit goes on paper. */
public sealed interface PrintScope permits PrintScope.WholeCircuit, PrintScope.Selection, PrintScope.Frame {

    record WholeCircuit() implements PrintScope {
    }

    /** The selected elements of the view that opened the dialog (locked frames expand to their contents). */
    record Selection(Set<ElementId> ids) implements PrintScope {

        public Selection {
            ids = Set.copyOf(ids);
        }
    }

    /** One frame's rectangle, as a visual crop. */
    record Frame(ElementId frameId) implements PrintScope {

        public Frame {
            Objects.requireNonNull(frameId, "frameId");
        }
    }
}
