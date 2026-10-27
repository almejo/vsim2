package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.Bounds;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Clipboard form of a {@link CircuitFragment}: compact JSON
 * {@code {"format":"vsim2","savedWith":...,"formatVersion":3,"kind":"fragment",...}} with the same component, wire
 * and label entries as the circuit file format. A fragment also carries the definitions its instances use, so it can
 * be pasted into a document that does not hold them.
 */
public final class FragmentFormat {

    static final String KIND_FRAGMENT = "fragment";

    record FragmentFile(String format, String savedWith, int formatVersion, String kind,
                        List<CircuitFormat.ComponentEntry> components,
                        List<CircuitFormat.WireEntry> wires, List<CircuitFormat.LabelEntry> labels,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DefinitionEntries.DefinitionEntry> definitions,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CircuitFormat.FrameEntry> frames,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CircuitFormat.StickyNoteEntry> stickyNotes) {
    }

    private final ComponentTypeRegistry registry;
    private final CircuitFormat circuitFormat;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FragmentFormat(ComponentTypeRegistry registry) {
        this.registry = registry;
        this.circuitFormat = new CircuitFormat(registry);
    }

    public String write(CircuitFragment fragment) {
        FragmentFile file = new FragmentFile(FileHeader.FORMAT, FileHeader.savedWith(), CircuitFormat.FORMAT_VERSION,
                KIND_FRAGMENT,
                fragment.components().stream().map(CircuitFormat::componentEntry).toList(),
                fragment.wires().stream().map(CircuitFormat::wireEntry).toList(),
                fragment.labels().stream().map(CircuitFormat::labelEntry).toList(),
                fragment.definitions().stream().map(DefinitionEntries::entry).toList(),
                fragment.frames().stream().map(CircuitFormat::frameEntry).toList(),
                fragment.stickyNotes().stream().map(CircuitFormat::stickyNoteEntry).toList());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize fragment", exception);
        }
    }

    /** Reads a fragment, validating it like a circuit file, and moves its bounds to start at (0,0). */
    public CircuitFragment read(String json) throws CircuitFormatException {
        JsonNode root = CircuitFormat.parse(mapper, json);
        // Called only for its checks (format version, kind, shape of the file); the parsed file itself is not used.
        CircuitFormat.readFile(mapper, root, FragmentFile.class, KIND_FRAGMENT, FragmentFile::kind);
        // Reuse CircuitFormat.read for validation and element parsing: it only accepts a circuit file, so the fragment
        // is presented as one (kind rewritten to "circuit", and the circuit-only "name" dropped).
        ObjectNode asCircuit = ((ObjectNode) root).deepCopy();
        asCircuit.put("kind", CircuitFormat.KIND_CIRCUIT);
        asCircuit.remove("name");
        CircuitDocument document = circuitFormat.read(asCircuit);
        CircuitFragment fragment = new CircuitFragment(document.components(), document.wires(), document.labels(),
                document.usedDefinitions(), document.frames(), document.stickyNotes());
        Bounds bounds = fragment.bounds(fragment.registryFor(registry));
        return fragment.translate(-bounds.x(), -bounds.y());
    }
}
