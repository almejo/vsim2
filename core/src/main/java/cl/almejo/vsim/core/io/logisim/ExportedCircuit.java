package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.Map;
import java.util.Objects;

/**
 * One circuit of an export, for checking it: the VSim circuit ({@code source}, with the {@code registry} that
 * resolves its parts), the Logisim circuit it became and where the Logisim end of each VSim pin that has one is.
 */
record ExportedCircuit(CircuitDocument source, ComponentTypeRegistry registry, LogisimCircuit circuit,
                       Map<PinRef, LogisimPoint> ends) {

    ExportedCircuit {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(circuit, "circuit");
        ends = Map.copyOf(ends);
    }
}
