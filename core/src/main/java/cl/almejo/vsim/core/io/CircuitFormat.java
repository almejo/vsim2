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
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyContent;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
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
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/** Reads formats 2 to 5 and writes format 5 (JSON; first keys in {@link FileHeader}). */
public final class CircuitFormat {

    /**
     * 4: parameterized gates ({@link LegacyGateMigration}); 5: the thin register ({@link RegisterShapeMigration}) and
     * the D flip-flop's Q̅, off on old parts ({@link FlipFlopNotOutputMigration}).
     */
    public static final int FORMAT_VERSION = 5;
    static final String KIND_CIRCUIT = "circuit";
    private static final String STICKY_KIND_TEXT = "text";
    private static final String STICKY_KIND_LIST = "list";

    /** A component; {@code labelOffset} is written only when the user moved the label (M6.8). */
    record ComponentEntry(String id, String type, int x, int y, int rotation, String label,
                          Map<String, Object> parameters,
                          @JsonInclude(JsonInclude.Include.NON_NULL) LabelOffsetEntry labelOffset,
                          @JsonInclude(JsonInclude.Include.NON_NULL) AppearanceEntries.AppearanceEntry appearance) {
    }

    record LabelOffsetEntry(int x, int y) {
    }

    record WireEntry(String id, int x1, int y1, int x2, int y2) {
    }

    /** A note; {@code style} is written only when the note is not plain, so a plain note keeps its bytes. */
    record LabelEntry(String id, int x, int y, String text,
                      @JsonInclude(JsonInclude.Include.NON_NULL) StyleEntry style) {
    }

    /** A note's colours by {@link NoteColor} name; each one is written only when it is not {@code NONE}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StyleEntry(String background, String text, String border) {
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

    /**
     * A sticky note; {@code kind} is {@code "text"} (then {@code text} is set) or {@code "list"} (then {@code items}
     * is set). {@code folded} is written only when true, so a note nobody folded keeps its bytes. An unknown colour
     * reads as yellow and a size under the minimum as the minimum.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StickyNoteEntry(String id, int x, int y, int width, int height, String color, String kind, String text,
                           List<StickyItemEntry> items, Boolean folded) {
    }

    record StickyItemEntry(String text, boolean done) {
    }

    static StickyNoteEntry stickyNoteEntry(StickyNote note) {
        Boolean folded = note.folded() ? Boolean.TRUE : null;
        return switch (note.content()) {
            case StickyText content -> new StickyNoteEntry(note.id().value(), note.position().x(),
                    note.position().y(), note.width(), note.height(), note.color().name(), STICKY_KIND_TEXT,
                    content.text(), null, folded);
            case StickyChecklist content -> new StickyNoteEntry(note.id().value(), note.position().x(),
                    note.position().y(), note.width(), note.height(), note.color().name(), STICKY_KIND_LIST, null,
                    content.items().stream().map(item -> new StickyItemEntry(item.text(), item.done())).toList(),
                    folded);
        };
    }

    static StickyNote stickyNote(StickyNoteEntry entry) {
        StickyColor color = StickyColor.YELLOW;
        if (entry.color() != null) {
            try {
                color = StickyColor.valueOf(entry.color());
            } catch (IllegalArgumentException exception) {
                color = StickyColor.YELLOW;
            }
        }
        return new StickyNote(new ElementId(entry.id()), new GridPoint(entry.x(), entry.y()), entry.width(),
                entry.height(), color, stickyContent(entry), Boolean.TRUE.equals(entry.folded()));
    }

    private static StickyContent stickyContent(StickyNoteEntry entry) {
        if (STICKY_KIND_LIST.equals(entry.kind())) {
            return new StickyChecklist(entries(entry.items()).stream()
                    .map(item -> new StickyItem(item.text(), item.done())).toList());
        }
        return new StickyText(entry.text());
    }

    static ComponentEntry componentEntry(Component component) {
        return new ComponentEntry(component.id().value(), component.typeId(), component.position().x(),
                component.position().y(), component.rotation().quarterTurns(), component.label(),
                writtenParameters(component),
                component.labelOffset().map(offset -> new LabelOffsetEntry(offset.x(), offset.y())).orElse(null),
                component.appearance().map(AppearanceEntries::entry).orElse(null));
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
        return new LabelEntry(label.id().value(), label.position().x(), label.position().y(), label.text(),
                styleEntry(label.style()));
    }

    /** The entry of a note's style; {@code null} (no key) for a plain note. */
    private static StyleEntry styleEntry(NoteStyle style) {
        if (style.isPlain()) {
            return null;
        }
        return new StyleEntry(colorName(style.background()), colorName(style.text()), colorName(style.border()));
    }

    private static String colorName(NoteColor color) {
        return color == NoteColor.NONE ? null : color.name();
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
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<GuideEntries.GuideEntry> guides,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<StickyNoteEntry> stickyNotes) {
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
        return write(document, UnaryOperator.identity());
    }

    /**
     * Like {@link #write(CircuitDocument)}, with each of the circuit's own components written as {@code written}
     * gives it (the document is not touched): the editor writes a memory's linked file relative to where the circuit
     * is saved.
     */
    public String write(CircuitDocument document, UnaryOperator<Component> written) {
        List<ComponentDefinition> used;
        try {
            used = DefinitionGraph.closure(SubcircuitTypes.definitionIds(document.components()), document::definition);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("the circuit cannot be saved: " + exception.getMessage(), exception);
        }
        CircuitFile file = new CircuitFile(FileHeader.FORMAT, FileHeader.savedWith(), FORMAT_VERSION, KIND_CIRCUIT,
                document.name(),
                document.components().stream().map(written).map(CircuitFormat::componentEntry).toList(),
                document.wires().stream().map(CircuitFormat::wireEntry).toList(),
                document.labels().stream().map(CircuitFormat::labelEntry).toList(),
                used.stream().map(DefinitionEntries::entry).toList(),
                document.probes().stream().map(CircuitFormat::probeEntry).toList(),
                document.frames().stream().map(CircuitFormat::frameEntry).toList(),
                document.guides().stream().map(GuideEntries::entry).toList(),
                document.stickyNotes().stream().map(CircuitFormat::stickyNoteEntry).toList());
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
            add(document, () -> validateAppearance(component(entry, type.resolveParameters(entry.parameters())), type));
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
        for (StickyNoteEntry entry : entries(file.stickyNotes())) {
            requireEntry(entry, "stickyNotes");
            add(document, () -> stickyNote(entry));
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
            file = mapper.treeToValue(migrated(root), fileType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalid(exception.getMessage(), exception);
        }
        String fileKind = kindOf.apply(file);
        if (!kind.equals(fileKind)) {
            throw invalid("kind " + fileKind);
        }
        return file;
    }

    /**
     * {@code root} with an old file's gates renamed ({@link LegacyGateMigration}), its D flip-flops without Q̅
     * ({@link FlipFlopNotOutputMigration}), then its registers' moved pins wired to where they were
     * ({@link RegisterShapeMigration}); the caller's tree is not touched.
     */
    private static JsonNode migrated(JsonNode root) {
        if (!(root instanceof ObjectNode object)
                || !RegisterShapeMigration.needsMigration(root.path(FileHeader.FORMAT_VERSION_KEY).intValue())) {
            return root;
        }
        ObjectNode copy = object.deepCopy();
        LegacyGateMigration.migrate(copy);
        FlipFlopNotOutputMigration.migrate(copy);
        RegisterShapeMigration.migrate(copy);
        return copy;
    }

    /** {@code entries}, or an empty list when the key was absent. */
    static <T> List<T> entries(List<T> entries) {
        return Optional.ofNullable(entries).orElse(List.of());
    }

    static <T> T requireEntry(T entry, String key) throws CircuitFormatException {
        if (entry == null) {
            throw invalid("null entry in " + key);
        }
        return entry;
    }

    static void requireRotation(ComponentEntry entry) throws CircuitFormatException {
        if (entry.rotation() < 0 || entry.rotation() > 3) {
            throw invalid("rotation must be 0..3 for " + entry.id() + ": " + entry.rotation());
        }
    }

    /** The component {@code entry} describes, with {@code parameters} (call {@link #requireRotation} first). */
    static Component component(ComponentEntry entry, Map<String, Object> parameters) {
        return new Component(new ElementId(entry.id()), entry.type(), new GridPoint(entry.x(), entry.y()),
                Rotation.fromQuarterTurns(entry.rotation()), parameters, entry.label(),
                Optional.ofNullable(entry.labelOffset()).map(offset -> new LabelOffset(offset.x(), offset.y())),
                Optional.ofNullable(entry.appearance()).map(AppearanceEntries::appearance));
    }

    /** Invalid persisted pin maps must fail rather than silently change an instance's electrical connections. */
    static Component validateAppearance(Component component, ComponentType type) {
        if (component.appearance().isEmpty()) {
            return component;
        }
        ComponentDefinition definition = SubcircuitTypes.definitionOf(type).orElseThrow(() ->
                new IllegalArgumentException("appearance is only allowed on user components: " + component.id()));
        try {
            return component.withAppearance(Optional.of(component.appearance().orElseThrow()
                    .canonical(definition.pinout())));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("appearance of " + component.id() + ": " + exception.getMessage(),
                    exception);
        }
    }

    static Wire wire(WireEntry entry) {
        return new Wire(new ElementId(entry.id()), new GridPoint(entry.x1(), entry.y1()),
                new GridPoint(entry.x2(), entry.y2()));
    }

    static TextLabel label(LabelEntry entry) {
        return new TextLabel(new ElementId(entry.id()), new GridPoint(entry.x(), entry.y()), entry.text(),
                style(entry));
    }

    /**
     * The style of a note entry. A colour name VSim does not know fails the load (IllegalArgumentException, which the
     * readers turn into {@code INVALID_ELEMENT}) with a message that names the note, the part and the name.
     */
    private static NoteStyle style(LabelEntry entry) {
        StyleEntry style = entry.style();
        if (style == null) {
            return NoteStyle.PLAIN;
        }
        return new NoteStyle(noteColor(style.background(), "background", entry),
                noteColor(style.text(), "text", entry), noteColor(style.border(), "border", entry));
    }

    private static NoteColor noteColor(String name, String role, LabelEntry entry) {
        if (name == null) {
            return NoteColor.NONE;
        }
        try {
            return NoteColor.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("note " + entry.id() + ": unknown " + role + " colour " + name
                    + "; the colours are " + List.of(NoteColor.values()), exception);
        }
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
                throw invalid(where + "null entry");
            }
            if (entry.id() == null || entry.id().isBlank()) {
                throw invalid(where + "missing id");
            }
            if (!ids.add(entry.id())) {
                throw invalid(where + "duplicate id " + entry.id());
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
                    throw invalid(where + "negative pin " + entry.pin());
                }
                return new ProbeTarget.PinTarget(new ElementId(entry.component()), entry.pin());
            }
            if (wireShape) {
                return new ProbeTarget.WireTarget(new ElementId(entry.wire()));
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(where + exception.getMessage(), exception);
        }
        throw invalid(where + "a probe needs either component and pin, or wire");
    }

    private interface ElementSupplier {
        DocumentElement create();
    }

    private static void add(CircuitDocument document, ElementSupplier supplier) throws CircuitFormatException {
        try {
            document.add(supplier.create());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }
}
