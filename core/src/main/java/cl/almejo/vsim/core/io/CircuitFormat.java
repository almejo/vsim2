package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionGraph;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** Reads and writes the VSim 2 circuit file format (JSON, formatVersion 2; first keys in {@link FileHeader}). */
public final class CircuitFormat {

    public static final int FORMAT_VERSION = 2;
    static final String KIND_CIRCUIT = "circuit";

    /** A component; {@code labelOffset} is written only when the user moved the label (M6.8). */
    record ComponentEntry(String id, String type, int x, int y, int rotation, String label,
                          Map<String, Object> parameters,
                          @JsonInclude(JsonInclude.Include.NON_NULL) LabelOffsetEntry labelOffset) {
    }

    record LabelOffsetEntry(int x, int y) {
    }

    record WireEntry(String id, int x1, int y1, int x2, int y2) {
    }

    record LabelEntry(String id, int x, int y, String text) {
    }

    /**
     * A frame; {@code tint} is the {@link FrameTint} name, and an unknown name reads as {@code NONE}; a size under the
     * minimum reads as the minimum.
     */
    record FrameEntry(String id, int x, int y, int width, int height, String title, String tint, boolean locked) {
    }

    static FrameEntry frameEntry(Frame frame) {
        return new FrameEntry(frame.id().value(), frame.topLeft().x(), frame.topLeft().y(), frame.width(),
                frame.height(), frame.title(), frame.tint().name(), frame.locked());
    }

    static Frame frame(FrameEntry entry) {
        FrameTint tint = FrameTint.NONE;
        if (entry.tint() != null) {
            try {
                tint = FrameTint.valueOf(entry.tint());
            } catch (IllegalArgumentException exception) {
                tint = FrameTint.NONE;
            }
        }
        // a hand-edited size under the minimum is clamped, so one bad frame never fails the whole file
        return new Frame(new ElementId(entry.id()), new GridPoint(entry.x(), entry.y()),
                Math.max(Frame.MINIMUM_WIDTH, entry.width()), Math.max(Frame.MINIMUM_HEIGHT, entry.height()),
                entry.title(), tint, entry.locked());
    }

    static ComponentEntry componentEntry(Component component) {
        return new ComponentEntry(component.id().value(), component.typeId(), component.position().x(),
                component.position().y(), component.rotation().quarterTurns(), component.label(),
                writtenParameters(component),
                component.labelOffset().map(offset -> new LabelOffsetEntry(offset.x(), offset.y())).orElse(null));
    }

    /**
     * The parameters a file keeps, sorted. M7: an input or output pin's «Mostrar como» is left out while it is
     * «Automático», so pins written before it existed keep their bytes (reading fills it back in).
     */
    private static Map<String, Object> writtenParameters(Component component) {
        Map<String, Object> written = new TreeMap<>(component.parameters());
        if (InterfacePins.isInterface(component.typeId())
                && ConstantValue.AUTO.equals(written.get(ConstantValue.FORMAT))) {
            written.remove(ConstantValue.FORMAT);
        }
        return written;
    }

    static WireEntry wireEntry(Wire wire) {
        return new WireEntry(wire.id().value(), wire.start().x(), wire.start().y(), wire.end().x(), wire.end().y());
    }

    static LabelEntry labelEntry(TextLabel label) {
        return new LabelEntry(label.id().value(), label.position().x(), label.position().y(), label.text());
    }

    /**
     * One waveform channel: exactly one of {@code component} + {@code pin} or {@code wire}. An empty {@code name}
     * (the live default name, M6.1-E) is left out of the file.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProbeEntry(String id, @JsonInclude(JsonInclude.Include.NON_EMPTY) String name, String component,
                      Integer pin, String wire) {
    }

    static ProbeEntry probeEntry(Probe probe) {
        return switch (probe.target()) {
            case ProbeTarget.PinTarget pin -> new ProbeEntry(probe.id(), probe.name(), pin.componentId().value(),
                    pin.pinIndex(), null);
            case ProbeTarget.WireTarget wire -> new ProbeEntry(probe.id(), probe.name(), null, null,
                    wire.wireId().value());
        };
    }

    record CircuitFile(String format, String savedWith, int formatVersion, String kind, String name,
                       List<ComponentEntry> components, List<WireEntry> wires, List<LabelEntry> labels,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DefinitionEntries.DefinitionEntry> definitions,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ProbeEntry> probes,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FrameEntry> frames,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<GuideEntries.GuideEntry> guides) {
    }

    private final ComponentTypeRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public CircuitFormat(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Serializes {@code document} with the definitions its components use. Throws IllegalStateException, writing
     * nothing, when a used definition is missing from the document: such a file could not be reopened.
     */
    public String write(CircuitDocument document) {
        List<ComponentDefinition> used;
        try {
            used = DefinitionGraph.closure(SubcircuitTypes.definitionIds(document.components()), document::definition);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("the circuit cannot be saved: " + exception.getMessage(), exception);
        }
        CircuitFile file = new CircuitFile(FileHeader.FORMAT, FileHeader.savedWith(), FORMAT_VERSION, KIND_CIRCUIT,
                document.name(),
                document.components().stream().map(CircuitFormat::componentEntry).toList(),
                document.wires().stream().map(CircuitFormat::wireEntry).toList(),
                document.labels().stream().map(CircuitFormat::labelEntry).toList(),
                used.stream().map(DefinitionEntries::entry).toList(),
                document.probes().stream().map(CircuitFormat::probeEntry).toList(),
                document.frames().stream().map(CircuitFormat::frameEntry).toList(),
                document.guides().stream().map(GuideEntries::entry).toList());
        try {
            return mapper.writeValueAsString(file);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize circuit", exception);
        }
    }

    public CircuitDocument read(String json) throws CircuitFormatException {
        JsonNode root = parse(mapper, json);
        try {
            return read(root);
        } catch (IllegalArgumentException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getMessage(), exception);
        }
    }

    public CircuitDocument read(JsonNode root) throws CircuitFormatException {
        CircuitFile file = readFile(mapper, GuideEntries.withoutGuides(root), CircuitFile.class, KIND_CIRCUIT,
                CircuitFile::kind);
        List<Guide> guides = GuideEntries.read(root);
        CircuitDocument document = new CircuitDocument();
        document.setName(file.name());
        // The file's own definitions resolve its subcircuit:<id> types; the caller's resolvers are not consulted.
        ComponentTypeRegistry local = registry.copy();
        local.addResolver(SubcircuitTypes.resolverFor(document));
        DefinitionEntries.readInto(document, entries(file.definitions()), local);
        for (ComponentEntry entry : entries(file.components())) {
            requireEntry(entry, "components");
            ComponentType type = local.find(entry.type()).orElseThrow(() -> new CircuitFormatException(
                    CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE, String.valueOf(entry.type())));
            requireRotation(entry);
            add(document, () -> component(entry, type.resolveParameters(entry.parameters())));
        }
        for (WireEntry entry : entries(file.wires())) {
            requireEntry(entry, "wires");
            add(document, () -> wire(entry));
        }
        for (LabelEntry entry : entries(file.labels())) {
            requireEntry(entry, "labels");
            add(document, () -> label(entry));
        }
        for (FrameEntry entry : entries(file.frames())) {
            requireEntry(entry, "frames");
            add(document, () -> frame(entry));
        }
        document.setProbes(probes(entries(file.probes())));
        document.setGuides(guides);
        return document;
    }

    /** The JSON tree of {@code json}; {@code null} or malformed text is {@code INVALID_JSON}. */
    static JsonNode parse(ObjectMapper mapper, String json) throws CircuitFormatException {
        if (json == null) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, "json is null");
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_JSON, exception.getMessage(), exception);
        }
    }

    /**
     * {@code root} as a {@code fileType} record, after the checks every VSim 2 file shares: the header
     * ({@link FileHeader#check}), a well-formed record and the expected {@code kind} (both {@code INVALID_ELEMENT}).
     */
    static <T> T readFile(ObjectMapper mapper, JsonNode root, Class<T> fileType, String kind,
                          Function<T, String> kindOf) throws CircuitFormatException {
        FileHeader.check(root);
        T file;
        try {
            file = mapper.treeToValue(root, fileType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
        }
        String fileKind = kindOf.apply(file);
        if (!kind.equals(fileKind)) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "kind " + fileKind);
        }
        return file;
    }

    /** {@code entries}, or an empty list when the key was absent. */
    static <T> List<T> entries(List<T> entries) {
        return Optional.ofNullable(entries).orElse(List.of());
    }

    static <T> T requireEntry(T entry, String key) throws CircuitFormatException {
        if (entry == null) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, "null entry in " + key);
        }
        return entry;
    }

    static void requireRotation(ComponentEntry entry) throws CircuitFormatException {
        if (entry.rotation() < 0 || entry.rotation() > 3) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT,
                    "rotation must be 0..3 for " + entry.id() + ": " + entry.rotation());
        }
    }

    /** The component {@code entry} describes, with {@code parameters} (call {@link #requireRotation} first). */
    static Component component(ComponentEntry entry, Map<String, Object> parameters) {
        return new Component(new ElementId(entry.id()), entry.type(), new GridPoint(entry.x(), entry.y()),
                Rotation.fromQuarterTurns(entry.rotation()), parameters, entry.label(),
                Optional.ofNullable(entry.labelOffset()).map(offset -> new LabelOffset(offset.x(), offset.y())));
    }

    static Wire wire(WireEntry entry) {
        return new Wire(new ElementId(entry.id()), new GridPoint(entry.x1(), entry.y1()),
                new GridPoint(entry.x2(), entry.y2()));
    }

    static TextLabel label(LabelEntry entry) {
        return new TextLabel(new ElementId(entry.id()), new GridPoint(entry.x(), entry.y()), entry.text());
    }

    /**
     * The probes of a file, in order. A malformed entry fails with {@code INVALID_ELEMENT} and a detail that starts
     * with {@code probes[<index>]: }; a probe on a component or wire that does not exist is kept (it shows as not
     * connected), since VSim itself writes these files.
     */
    static List<Probe> probes(List<ProbeEntry> entries) throws CircuitFormatException {
        List<Probe> probes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            ProbeEntry entry = entries.get(index);
            String where = "probes[" + index + "]: ";
            if (entry == null) {
                throw invalidProbe(where + "null entry");
            }
            if (entry.id() == null || entry.id().isBlank()) {
                throw invalidProbe(where + "missing id");
            }
            if (!ids.add(entry.id())) {
                throw invalidProbe(where + "duplicate id " + entry.id());
            }
            probes.add(new Probe(entry.id(), probeTarget(entry, where), entry.name()));
        }
        return probes;
    }

    private static ProbeTarget probeTarget(ProbeEntry entry, String where) throws CircuitFormatException {
        boolean pinShape = entry.component() != null && entry.pin() != null && entry.wire() == null;
        boolean wireShape = entry.wire() != null && entry.component() == null && entry.pin() == null;
        try {
            if (pinShape) {
                if (entry.pin() < 0) {
                    throw invalidProbe(where + "negative pin " + entry.pin());
                }
                return new ProbeTarget.PinTarget(new ElementId(entry.component()), entry.pin());
            }
            if (wireShape) {
                return new ProbeTarget.WireTarget(new ElementId(entry.wire()));
            }
        } catch (IllegalArgumentException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT,
                    where + exception.getMessage(), exception);
        }
        throw invalidProbe(where + "a probe needs either component and pin, or wire");
    }

    private static CircuitFormatException invalidProbe(String detail) {
        return new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, detail);
    }

    private interface ElementSupplier {
        DocumentElement create();
    }

    private static void add(CircuitDocument document, ElementSupplier supplier) throws CircuitFormatException {
        try {
            document.add(supplier.create());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.INVALID_ELEMENT, exception.getMessage(), exception);
        }
    }
}
