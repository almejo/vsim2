package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * «Ordenar circuito» (spec 2026-11-06 §4): separates parts, labels and notes that cover each other, keeping what the
 * circuit means, then runs the wire rules. Pure: it works on a copy and never changes {@code document}.
 */
public final class CircuitTidy {

    private CircuitTidy() {
    }

    /**
     * @param scope ids of the elements allowed to change (components, texts, notes, wires)
     * @return empty when {@code cancelled} said yes
     */
    public static Optional<CircuitTidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                                 Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                                 BooleanSupplier cancelled, IntConsumer progress) {
        throw new UnsupportedOperationException("Task 2");
    }
}
