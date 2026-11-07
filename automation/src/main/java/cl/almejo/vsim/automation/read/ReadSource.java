package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.memory.LinkedFileChecks;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.ListedProblems;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the readers see of one open circuit, given by the editor on its own thread ({@code ReadHost#reading}): valid
 * only during that call, because the document keeps changing afterwards. {@code registry} resolves the circuit's own
 * user components; {@code netlist} is compiled from the document as it is now ({@code netlistToken} is the document
 * revision it was compiled from); {@code snapshot} is the latest published simulation, which may lag behind.
 * {@code library} is the user's component library («Mis componentes»), whose components the AI may also place;
 * {@code textMeasure} says where the editor draws texts (for the layout checks); {@code circuitFile} is where the
 * circuit is saved, empty when it is not (what the links of its memories are relative to). {@code pieceMemory} gives the
 * contents a piece of a component stores for one of its memories, by the memory's id, when the source is that piece's
 * body (spec §6.6b, §12); empty for the circuit itself. {@code problemSources} is what the editor makes the rows of
 * its Problemas list from ({@link ListedProblems}), so the AI reads every row the user sees; {@code libraryProblems}
 * are the component library's load problems, which that list shows under every circuit.
 */
public record ReadSource(DocumentId id, DocumentVersion version, String name, CircuitDocument document,
                         ComponentTypeRegistry registry, Netlist netlist, long netlistToken,
                         SimulationSnapshot snapshot, ComponentNames names, List<DefinitionBundle> library,
                         TextMeasure textMeasure, Optional<Path> circuitFile,
                         Function<String, Optional<MemoryContents>> pieceMemory, ListedProblems.Sources problemSources,
                         List<LibraryProblem> libraryProblems) {

    public ReadSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(netlist, "netlist");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(names, "names");
        library = List.copyOf(library);
        Objects.requireNonNull(textMeasure, "textMeasure");
        Objects.requireNonNull(circuitFile, "circuitFile");
        Objects.requireNonNull(pieceMemory, "pieceMemory");
        Objects.requireNonNull(problemSources, "problemSources");
        libraryProblems = List.copyOf(libraryProblems);
    }

    /**
     * A source of the circuit itself, saved at {@code circuitFile} when it is, that no editor lists: its problems are
     * what {@code snapshot} published and the linked files it finds missing, and there is no library to load.
     */
    public ReadSource(DocumentId id, DocumentVersion version, String name, CircuitDocument document,
                      ComponentTypeRegistry registry, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                      ComponentNames names, List<DefinitionBundle> library, TextMeasure textMeasure,
                      Optional<Path> circuitFile) {
        this(id, version, name, document, registry, netlist, netlistToken, snapshot, names, library, textMeasure,
                circuitFile, memory -> Optional.empty(), ListedProblems.Sources.published(snapshot,
                        LinkedFileChecks.missing(document, circuitFile)), List.of());
    }

    /** A source of a circuit that is not saved anywhere. */
    public ReadSource(DocumentId id, DocumentVersion version, String name, CircuitDocument document,
                      ComponentTypeRegistry registry, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                      ComponentNames names, List<DefinitionBundle> library, TextMeasure textMeasure) {
        this(id, version, name, document, registry, netlist, netlistToken, snapshot, names, library, textMeasure,
                Optional.empty());
    }

    /** A source without a library, whose texts are measured approximately. */
    public ReadSource(DocumentId id, DocumentVersion version, String name, CircuitDocument document,
                      ComponentTypeRegistry registry, Netlist netlist, long netlistToken, SimulationSnapshot snapshot,
                      ComponentNames names) {
        this(id, version, name, document, registry, netlist, netlistToken, snapshot, names, List.of(),
                TextMeasure.APPROXIMATE, Optional.empty());
    }
}
