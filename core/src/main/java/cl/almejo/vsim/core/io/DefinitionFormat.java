package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

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
                         @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CircuitFormat.FrameEntry> frames,
                         @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CircuitFormat.StickyNoteEntry> stickyNotes,
                         @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DefinitionEntries.DefinitionEntry> definitions,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Long revision,
                         @JsonInclude(JsonInclude.Include.NON_NULL) AppearanceEntries.DrawingEntry appearance,
                         @JsonInclude(JsonInclude.Include.NON_NULL) MachineEntries.MachineEntry machine) {
    }

    private final ComponentTypeRegistry registry;
    private final ObjectMapper mapper = CircuitFormat.fileMapper();

    public DefinitionFormat(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public String write(DefinitionBundle bundle) {
        DefinitionEntries.DefinitionEntry root = DefinitionEntries.entry(bundle.root());
        ComponentFile file = new ComponentFile(FileHeader.FORMAT, FileHeader.savedWith(), CircuitFormat.FORMAT_VERSION,
                KIND_COMPONENT, root.id(), root.name(), root.description(), root.width(), root.height(), root.pinout(),
                root.components(), root.wires(), root.labels(), root.frames(), root.stickyNotes(),
                bundle.dependencies().stream().map(DefinitionEntries::entry).toList(), root.revision(),
                root.appearance(), root.machine());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize component " + bundle.root().id(), exception);
        }
    }

    /** A component file read: the bundle, and the names of its definitions whose drawing could not be read. */
    public record Read(DefinitionBundle bundle, List<String> unsupportedDrawingNames) {

        public Read {
            unsupportedDrawingNames = List.copyOf(unsupportedDrawingNames);
        }
    }

    public DefinitionBundle read(String json) throws CircuitFormatException {
        return readNoting(json).bundle();
    }

    /**
     * Like {@link #read}; a definition whose drawing has a path VSim cannot read loads with the default box and is
     * named in the result (the user, 2026-11-20).
     */
    public Read readNoting(String json) throws CircuitFormatException {
        ComponentFile file = CircuitFormat.readFile(mapper, CircuitFormat.parse(mapper, json), ComponentFile.class,
                KIND_COMPONENT, ComponentFile::kind);
        CircuitDocument scratch = new CircuitDocument();
        ComponentTypeRegistry local = registry.copy();
        local.addResolver(SubcircuitTypes.resolverFor(scratch));
        List<DefinitionEntries.DefinitionEntry> entries =
                new ArrayList<>(CircuitFormat.entries(file.definitions()));
        entries.add(new DefinitionEntries.DefinitionEntry(file.id(), file.name(), file.description(), file.width(),
                file.height(), file.pinout(), file.components(), file.wires(), file.labels(), file.frames(),
                file.stickyNotes(), file.revision(), file.appearance(), file.machine()));
        DefinitionEntries.readInto(scratch, entries, local);
        ComponentDefinition definition = scratch.definition(file.id()).orElseThrow();
        try {
            DefinitionBundle bundle = DefinitionBundle.of(definition, scratch::definition);
            List<String> unsupported = bundle.all().stream()
                    .filter(each -> scratch.unsupportedDrawings().contains(each.id()))
                    .map(ComponentDefinition::name).toList();
            return new Read(bundle, unsupported);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }
}
