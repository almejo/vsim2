package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * A library component file ({@code <id>.vsimcomponent.json}, spec §5.8): the definition's fields at the top level plus
 * the definitions it nests in {@code "definitions"}.
 */
public final class DefinitionFormat {

    public static final String KIND_COMPONENT = "component";
    public static final String FILE_SUFFIX = ".vsimcomponent.json";

    record ComponentFile(String format, String savedWith, int formatVersion, String kind, String id, String name,
                         String description, int width, int height, List<DefinitionEntries.PinEntry> pinout,
                         List<CircuitFormat.ComponentEntry> components, List<CircuitFormat.WireEntry> wires,
                         List<CircuitFormat.LabelEntry> labels,
                         @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DefinitionEntries.DefinitionEntry> definitions) {
    }

    private final ComponentTypeRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public DefinitionFormat(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public String write(DefinitionBundle bundle) {
        DefinitionEntries.DefinitionEntry root = DefinitionEntries.entry(bundle.root());
        ComponentFile file = new ComponentFile(FileHeader.FORMAT, FileHeader.savedWith(), CircuitFormat.FORMAT_VERSION,
                KIND_COMPONENT, root.id(), root.name(), root.description(), root.width(), root.height(), root.pinout(),
                root.components(), root.wires(), root.labels(),
                bundle.dependencies().stream().map(DefinitionEntries::entry).toList());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize component " + bundle.root().id(), exception);
        }
    }

    public DefinitionBundle read(String json) throws CircuitFormatException {
        ComponentFile file = CircuitFormat.readFile(mapper, CircuitFormat.parse(mapper, json), ComponentFile.class,
                KIND_COMPONENT, ComponentFile::kind);
        CircuitDocument scratch = new CircuitDocument();
        ComponentTypeRegistry local = registry.copy();
        local.addResolver(SubcircuitTypes.resolverFor(scratch));
        List<DefinitionEntries.DefinitionEntry> entries =
                new ArrayList<>(CircuitFormat.entries(file.definitions()));
        entries.add(new DefinitionEntries.DefinitionEntry(file.id(), file.name(), file.description(), file.width(),
                file.height(), file.pinout(), file.components(), file.wires(), file.labels()));
        DefinitionEntries.readInto(scratch, entries, local);
        ComponentDefinition definition = scratch.definition(file.id()).orElseThrow();
        try {
            return DefinitionBundle.of(definition, scratch::definition);
        } catch (IllegalArgumentException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(),
                    exception);
        }
    }
}
