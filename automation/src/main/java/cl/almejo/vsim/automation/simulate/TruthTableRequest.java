package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;

import java.util.Objects;
import java.util.Set;

/**
 * A truth table of the circuit (empty {@code elementIds}), of some of its elements, or of one user-component instance's
 * body, as Herramientas ▸ Tabla de verdad makes it (the limits of {@code TruthTableScope}: combinational only, at most
 * 16 input bits). It is evaluated on a copy: the live simulation is never touched. At most {@code maximumRows} rows
 * (the first ones) and {@code eventBudget} events in all.
 */
public record TruthTableRequest(DocumentId document, Set<String> elementIds, int maximumRows, int eventBudget) {

    public TruthTableRequest {
        Objects.requireNonNull(document, "document");
        elementIds = Set.copyOf(elementIds);
    }
}
