package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.Objects;

/**
 * What the readers see of one open circuit, given by the editor on its own thread ({@code ReadHost#reading}): valid
 * only during that call, because the document keeps changing afterwards. {@code registry} resolves the circuit's own
 * user components; {@code netlist} is compiled from the document as it is now ({@code netlistToken} is the document
 * revision it was compiled from); {@code snapshot} is the latest published simulation, which may lag behind.
 */
public record ReadSource(DocumentId id, DocumentVersion version, String name, CircuitDocument document,
                         ComponentTypeRegistry registry, Netlist netlist, long netlistToken,
                         SimulationSnapshot snapshot, ComponentNames names) {

    public ReadSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(netlist, "netlist");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(names, "names");
    }
}
