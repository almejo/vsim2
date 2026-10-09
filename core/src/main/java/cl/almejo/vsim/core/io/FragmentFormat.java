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
 * Clipboard form of a {@link CircuitFragment}: compact JSON {@code {"formatVersion":2,"kind":"fragment",...}} with the
 * same component, wire and label entries as the circuit file format.
 */
public final class FragmentFormat {

    static final String KIND_FRAGMENT = "fragment";

    record FragmentFile(int formatVersion, String kind, List<CircuitFormat.ComponentEntry> components,
                        List<CircuitFormat.WireEntry> wires, List<CircuitFormat.LabelEntry> labels,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DefinitionEntries.DefinitionEntry> definitions) {
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
        FragmentFile file = new FragmentFile(CircuitFormat.FORMAT_VERSION, KIND_FRAGMENT,
                fragment.components().stream().map(CircuitFormat::componentEntry).toList(),
                fragment.wires().stream().map(CircuitFormat::wireEntry).toList(),
                fragment.labels().stream().map(CircuitFormat::labelEntry).toList(),
                fragment.definitions().stream().map(DefinitionEntries::entry).toList());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize fragment", exception);
        }
    }

    /** Reads a fragment, validating it like a circuit file, and moves its bounds to start at (0,0). */
    public CircuitFragment read(String json) throws CircuitFormatException {
        if (json == null) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, "json is null");
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getMessage(),
                    exception);
        }
        if (!(root instanceof ObjectNode object)) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT,
                    "root must be a JSON object");
        }
        JsonNode kind = object.path("kind");
        if (!kind.isTextual() || !KIND_FRAGMENT.equals(kind.textValue())) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "kind " + kind);
        }
        // Reuse CircuitFormat.read for validation and element parsing: it only accepts a circuit file, so the fragment
        // is presented as one (kind rewritten to "circuit", and the circuit-only "name" dropped).
        ObjectNode asCircuit = object.deepCopy();
        asCircuit.put("kind", CircuitFormat.KIND_CIRCUIT);
        asCircuit.remove("name");
        CircuitDocument document = circuitFormat.read(asCircuit);
        CircuitFragment fragment = new CircuitFragment(document.components(), document.wires(), document.labels(),
                document.usedDefinitions());
        Bounds bounds = fragment.bounds(fragment.registryFor(registry));
        return fragment.translate(-bounds.x(), -bounds.y());
    }
}
