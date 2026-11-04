package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.bundled.ExampleSimulation;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.ArithmeticTypes;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.examples.ExampleCircuits;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LogisimExporterTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    private static final Map<String, Object> ONE_BIT = Map.of(StandardComponentTypes.WIDTH, 1);
    private static final Map<String, Object> EIGHT_BITS = Map.of(StandardComponentTypes.WIDTH, 8);
    private static final int WIRE = 3;
    /** A wire on every row and column of a square, each its own net (they only cross). */
    private static final int MESH_LINES = 60;
    /** Long enough for the gates to settle and shorter than the clock's first half period. */
    private static final long SETTLING_TIME = 100;

    @Test
    void aGateBetweenPinsKeepsEveryNetInLogisim() {
        CircuitBuilder builder = nandBuilder();
        List<ExportedCircuit> exported = new ArrayList<>();

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY, exported::add);

        assertThat(exported).singleElement().satisfies(circuit -> {
            assertThat(circuit.ends()).as("three pins and the gate's three").hasSize(6);
            assertThat(ExportEquivalence.mismatches(circuit, result.project())).isEmpty();
        });
        assertThat(result.notes()).isEmpty();
        assertThat(result.project().circuits()).singleElement().satisfies(circuit -> assertThat(circuit.components())
                .extracting(LogisimComponent::name).containsExactlyInAnyOrder("Pin", "Pin", "NAND Gate", "Pin"));
    }

    @Test
    void everyExampleCircuitExportsWithoutShortsOrOpens() {
        for (Map.Entry<String, Supplier<CircuitBuilder>> example : ExampleCircuits.builders().entrySet()) {
            List<ExportedCircuit> exported = new ArrayList<>();
            ExportResult result = LogisimExporter.exportDocument(example.getValue().get().document(), REGISTRY,
                    exported::add);

            assertThat(exported).as(example.getKey()).isNotEmpty();
            for (ExportedCircuit circuit : exported) {
                assertThat(ExportEquivalence.mismatches(circuit, result.project())).as(example.getKey()).isEmpty();
            }
        }
    }

    @Test
    void theWrittenFileReadsBackAsTheSameProject() throws Exception {
        CircuitDocument document = mixedBuilder().document();
        LogisimProject exported = LogisimExporter.exportDocument(document, REGISTRY).project();

        LogisimProject read = read(LogisimWriter.toText(exported));

        assertThat(read.notes()).isEmpty();
        assertThat(read.mainCircuit()).isEqualTo(exported.mainCircuit());
        assertThat(read.circuits()).extracting(LogisimCircuit::name)
                .containsExactlyElementsOf(exported.circuits().stream().map(LogisimCircuit::name).toList());
        for (LogisimCircuit circuit : exported.circuits()) {
            LogisimCircuit again = read.circuit(circuit.name()).orElseThrow();
            assertThat(again.components()).containsExactlyElementsOf(circuit.components());
            assertThat(again.wires()).containsExactlyElementsOf(circuit.wires());
            assertThat(again.appearance()).isEqualTo(circuit.appearance());
        }
    }

    @Test
    void aVsimCircuitComesBackFromLogisimWithTheSamePartsAndNets() throws Exception {
        CircuitDocument document = mixedBuilder().document();

        CircuitDocument again = RoundTrip.throughLogisim(document, REGISTRY);

        assertThat(RoundTrip.describe(again, REGISTRY)).isEqualTo(RoundTrip.describe(document, REGISTRY));
    }

    @Test
    void aVsimCircuitExportsWithoutShortsOrOpensInEveryCircuit() {
        List<ExportedCircuit> exported = new ArrayList<>();

        ExportResult result = LogisimExporter.exportDocument(mixedBuilder().document(), REGISTRY, exported::add);

        assertThat(exported).extracting(circuit -> circuit.circuit().name())
                .containsExactlyInAnyOrder("Inversor", "mezcla");
        for (ExportedCircuit circuit : exported) {
            assertThat(ExportEquivalence.mismatches(circuit, result.project())).isEmpty();
        }
    }

    @Test
    void theCircuitBackFromLogisimComputesTheSame() throws Exception {
        CircuitDocument again = RoundTrip.throughLogisim(mixedBuilder().document(), REGISTRY);

        for (boolean inputA : List.of(false, true)) {
            for (boolean inputB : List.of(false, true)) {
                ExampleSimulation simulation = new ExampleSimulation(again.copy(), Map.of());
                simulation.setInput("A", Value.of(inputA)).setInput("B", Value.of(inputB))
                        .setInput("X", Value.of(inputA)).runUntil(SETTLING_TIME);

                assertThat(simulation.outputPin("Y")).isEqualTo(Value.of(!(inputA && inputB)));
                assertThat(simulation.outputPin("NX")).isEqualTo(Value.of(!inputA));
            }
        }
    }

    @Test
    void logisimFixturesComeBackWithTheSamePartsAndNets() throws Exception {
        for (String fixture : List.of("importer.circ", "nets.circ", "layout.circ", "subcircuits.circ")) {
            CircuitDocument imported = LogisimImporter.importProject(LogisimTestFiles.read(fixture), REGISTRY)
                    .document();

            CircuitDocument again = RoundTrip.throughLogisim(imported, REGISTRY);

            assertThat(RoundTrip.describe(again, REGISTRY)).as(fixture)
                    .isEqualTo(RoundTrip.describe(imported, REGISTRY));
        }
    }

    @Test
    void aCustomComponentBecomesACircuitWhoseInstanceEndsAreItsPins() {
        CircuitBuilder builder = mixedBuilder();
        CircuitDocument document = builder.document();
        Component instance = document.components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER)))
                .findFirst().orElseThrow();

        LogisimProject project = LogisimExporter.exportDocument(document, REGISTRY).project();

        LogisimCircuit inverter = project.circuit("Inversor").orElseThrow();
        assertThat(inverter.appearance().shapes()).filteredOn(LogisimAppearance.Port.class::isInstance).hasSize(2);
        assertThat(inverter.appearance().shapes()).filteredOn(LogisimAppearance.Anchor.class::isInstance).hasSize(1);
        LogisimComponent exportedInstance = project.circuit(project.mainCircuit()).orElseThrow().components().stream()
                .filter(LogisimComponent::isSubcircuit).findFirst().orElseThrow();
        assertThat(LogisimEnds.of(exportedInstance, project)).extracting(LogisimEnd::point)
                .containsExactlyInAnyOrder(tenTimes(builder.pin(instance, 0)), tenTimes(builder.pin(instance, 1)));
    }

    @Test
    void aSplittersGroupsBecomeItsBitAttributes() {
        CircuitBuilder builder = new CircuitBuilder("separador");
        builder.placeByBox(StandardComponentTypes.BUS_SPLITTER, 10, 10, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-2,6|3-5"), "");

        LogisimComponent splitter = onlyComponent(LogisimExporter.exportDocument(builder.document(), REGISTRY));

        assertThat(splitter.name()).isEqualTo("Splitter");
        assertThat(splitter.attributes().asMap()).containsEntry("fanout", "2").containsEntry("incoming", "8")
                .containsEntry("bit0", "0").containsEntry("bit1", "0").containsEntry("bit2", "0")
                .containsEntry("bit3", "1").containsEntry("bit4", "1").containsEntry("bit5", "1")
                .containsEntry("bit6", "0").containsEntry("bit7", "none");
    }

    @Test
    void aRegisterKeepsItsLatchTrigger() {
        CircuitBuilder builder = new CircuitBuilder("registro");
        builder.placeByBox(StandardComponentTypes.REGISTER, 10, 10, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 16, StandardComponentTypes.TRIGGER,
                        StandardComponentTypes.TRIGGER_HIGH), "R");

        LogisimComponent register = onlyComponent(LogisimExporter.exportDocument(builder.document(), REGISTRY));

        assertThat(register.name()).isEqualTo("Register");
        assertThat(register.attributes().asMap()).containsEntry("trigger", "high").containsEntry("width", "16")
                .containsEntry("label", "R");
    }

    @Test
    void everyPinOfAnExportedPartHasItsLogisimEndExceptThoseLogisimLacks() {
        Map<String, List<String>> lacking = Map.of(ArithmeticTypes.DIVIDER, List.of("E"),
                StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, List.of("in0", "in1", "in2", "in3"));
        for (ComponentType type : REGISTRY.all()) {
            CircuitBuilder builder = new CircuitBuilder(type.id());
            Component part = builder.placeByBox(type.id(), 10, 10, Rotation.R0, Map.of(), "");
            List<ExportedCircuit> exported = new ArrayList<>();
            ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY, exported::add);
            if (result.project().circuits().getFirst().components().isEmpty()) {
                continue;
            }
            ComponentType specialized = REGISTRY.typeOf(part).forComponent(part);
            List<String> withoutEnd = new ArrayList<>();
            for (int pin = 0; pin < specialized.pinCount(); pin++) {
                if (!exported.getFirst().ends().containsKey(new PinRef(part.id(), pin))) {
                    withoutEnd.add(specialized.pin(pin).name());
                }
            }
            assertThat(withoutEnd).as(type.id())
                    .containsExactlyInAnyOrderElementsOf(lacking.getOrDefault(type.id(), List.of()));
        }
    }

    @Test
    void aSwitchInTheMainCircuitIsALogisimInputPinWiredLikeIt() {
        CircuitBuilder builder = new CircuitBuilder("interruptor");
        Component switchPart = builder.placeByBox(StandardComponentTypes.SWITCH, 10, 10, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 1, StandardComponentTypes.VALUE, "0"), "S");
        builder.attach(switchPart, 0, StandardComponentTypes.OUTPUT_PIN, ONE_BIT, "Y", WIRE);
        List<ExportedCircuit> exported = new ArrayList<>();

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY, exported::add);

        assertThat(result.notes()).isEmpty();
        assertThat(exported.getFirst().ends()).hasSize(2);
        assertThat(ExportEquivalence.mismatches(exported.getFirst(), result.project())).isEmpty();
        assertThat(result.project().circuits().getFirst().components()).extracting(LogisimComponent::name)
                .containsExactlyInAnyOrder("Pin", "Pin");
    }

    @Test
    void aPartWithoutALogisimEquivalentIsLeftOutAndNoted() {
        CircuitBuilder builder = new CircuitBuilder("alu");
        builder.placeByBox(StandardComponentTypes.ALU, 10, 10, Rotation.R0, Map.of(), "U1");

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY);

        assertThat(result.project().circuits().getFirst().components()).isEmpty();
        assertThat(result.notes()).singleElement().satisfies(note -> {
            assertThat(note.kind()).isEqualTo(ImportNote.Kind.UNSUPPORTED);
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.EXPORT_UNSUPPORTED_PART);
            assertThat(note.circuit()).isEqualTo("alu");
            assertThat(note.arguments()).containsExactly(StandardComponentTypes.ALU, "U1");
        });
    }

    @Test
    void aNoteNamesItsPartOrTheInstanceOfTheComponentThatHoldsIt() {
        CircuitBuilder builder = new CircuitBuilder("principal");
        Component innerAlu = REGISTRY.require(StandardComponentTypes.ALU).newComponent(new ElementId("inner"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        builder.define(new ComponentDefinition("caja", "Caja", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(innerAlu), List.of(), List.of())));
        Component alu = builder.placeByBox(StandardComponentTypes.ALU, 10, 10, Rotation.R0, Map.of(), "U1");
        Component box = builder.placeByBox(SubcircuitTypes.typeIdOf("caja"), 40, 10, Rotation.R0, Map.of(), "K1");

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY);

        assertThat(result.notes()).extracting(ImportNote::circuit, ImportNote::element).containsExactlyInAnyOrder(
                tuple("principal", Optional.of(alu.id())),
                tuple("Caja", Optional.of(box.id())));
    }

    @Test
    void aTerminalOrKeyboardBiggerThanLogisimsIsClampedWithANote() {
        CircuitBuilder builder = new CircuitBuilder("consola");
        builder.placeByBox(StandardComponentTypes.TERMINAL, 10, 10, Rotation.R0,
                Map.of(TerminalTypes.ROWS, 60, TerminalTypes.COLUMNS, 160), "");
        builder.placeByBox(StandardComponentTypes.KEYBOARD, 10, 80, Rotation.R0,
                Map.of(KeyboardTypes.CAPACITY, 1024), "");

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY);

        assertThat(result.notes()).extracting(ImportNote::messageKey, note -> note.arguments().subList(2,
                note.arguments().size())).contains(
                tuple(LogisimNotes.EXPORT_TERMINAL_SIZE, List.of("60", "160", "48", "120")),
                tuple(LogisimNotes.EXPORT_KEYBOARD_BUFFER, List.of("1024", "256")));
    }

    @Test
    void aSwitchInTheMainCircuitThatIsOnIsNotedSinceLogisimsPinStartsAtZero() {
        CircuitBuilder builder = new CircuitBuilder("interruptor");
        builder.placeByBox(StandardComponentTypes.SWITCH, 10, 10, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 1, StandardComponentTypes.VALUE, "1"), "S");

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY);

        assertThat(result.notes()).extracting(ImportNote::messageKey)
                .containsExactly(LogisimNotes.EXPORT_SWITCH_VALUE);
    }

    @Test
    void writingAFileReplacesItWholeAndLeavesNothingElseInItsFolder(@TempDir Path folder) throws Exception {
        LogisimProject project = LogisimExporter.exportDocument(nandBuilder().document(), REGISTRY).project();
        Path target = folder.resolve("nand.circ");
        Files.writeString(target, "an older file, longer than nothing at all");

        LogisimWriter.write(project, target);

        assertThat(Files.readString(target)).isEqualTo(LogisimWriter.toText(project));
        try (Stream<Path> files = Files.list(folder)) {
            assertThat(files).containsExactly(target);
        }
    }

    @Test
    void aRamWithItsWriteEnableLooseNeverWritesInVsimButWouldInLogisim() {
        CircuitBuilder builder = new CircuitBuilder("memoria");
        builder.placeByBox(StandardComponentTypes.RAM, 10, 10, Rotation.R0, Map.of(), "M");

        assertThat(differences(builder)).containsExactly(LogisimNotes.EXPORT_LOOSE_WRITE_ENABLE);
    }

    @Test
    void aRamWithSharedDataWritesWithWeInVsimAndWithOeInLogisim() {
        CircuitBuilder builder = new CircuitBuilder("memoria");
        Component ram = builder.placeByBox(StandardComponentTypes.RAM, 10, 10, Rotation.R0,
                Map.of(MemoryTypes.DATA, MemoryTypes.DATA_SHARED), "M");
        builder.attach(ram, writeEnable(ram), StandardComponentTypes.SWITCH, ONE_BIT, "W", WIRE);

        assertThat(differences(builder)).containsExactly(LogisimNotes.EXPORT_RAM_WITHOUT_WRITE_ENABLE);
    }

    @Test
    void aCounterWithItsLoadWiredCountsDownInLogisim() {
        CircuitBuilder builder = new CircuitBuilder("contador");
        Component counter = builder.placeByBox(StandardComponentTypes.COUNTER, 10, 10, Rotation.R0, EIGHT_BITS, "");
        builder.attach(counter, 1, StandardComponentTypes.CONSTANT, Map.of(StandardComponentTypes.WIDTH, 1,
                StandardComponentTypes.VALUE, "1"), "", WIRE);
        builder.placeByBox(StandardComponentTypes.COUNTER, 10, 60, Rotation.R0, EIGHT_BITS, "");

        assertThat(differences(builder)).containsExactly(LogisimNotes.EXPORT_COUNTER_LOAD);
    }

    @Test
    void aTriStateBufferWithItsControlLooseIsOffInVsimAndAnErrorInLogisim() {
        CircuitBuilder builder = new CircuitBuilder("tercer estado");
        builder.placeByBox(StandardComponentTypes.TRI_STATE, 10, 10, Rotation.R0, ONE_BIT, "");
        Component wired = builder.placeByBox(StandardComponentTypes.TRI_STATE, 10, 40, Rotation.R0, ONE_BIT, "");
        builder.attach(wired, 2, StandardComponentTypes.SWITCH, ONE_BIT, "E", WIRE);

        assertThat(differences(builder)).containsExactly(LogisimNotes.EXPORT_LOOSE_BUFFER_CONTROL);
    }

    @Test
    void aPartWithNoFreePlaceNearItsOwnGoesApartAndJoinsNoOtherNet() {
        CircuitBuilder builder = new CircuitBuilder("malla");
        Component gate = builder.placeByBox(StandardComponentTypes.AND, MESH_LINES / 2, MESH_LINES / 2, Rotation.R0,
                Map.of(), "");
        // Wires on every row and column but the gate's own pins': its pins stay loose in VSim, while Logisim's gate,
        // whose ends lie otherwise, has no spot near it where no end lands on a wire.
        Set<Integer> pinRows = new HashSet<>();
        Set<Integer> pinColumns = new HashSet<>();
        for (int pin = 0; pin < REGISTRY.typeOf(gate).pinCount(); pin++) {
            pinRows.add(builder.pin(gate, pin).y());
            pinColumns.add(builder.pin(gate, pin).x());
        }
        for (int line = 0; line <= MESH_LINES; line++) {
            if (!pinRows.contains(line)) {
                builder.route(new GridPoint(-1, line), new GridPoint(MESH_LINES + 1, line));
            }
            if (!pinColumns.contains(line)) {
                builder.route(new GridPoint(line, -1), new GridPoint(line, MESH_LINES + 1));
            }
        }
        List<ExportedCircuit> exported = new ArrayList<>();

        ExportResult result = LogisimExporter.exportDocument(builder.document(), REGISTRY, exported::add);

        assertThat(ExportEquivalence.mismatches(exported.getFirst(), result.project())).isEmpty();
        assertThat(result.notes()).extracting(ImportNote::messageKey).contains(LogisimNotes.EXPORT_CROWDED_PART);
    }

    @Test
    void aNoteAboutAnInstanceNamesItsComponentNotItsTypeId() {
        CircuitBuilder builder = new CircuitBuilder("principal");
        builder.define(ExampleDefinitions.inverter());
        Component instance = builder.placeByBox(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 10, 10,
                Rotation.R0, Map.of(), "");

        assertThat(ExportLayout.partKind(instance, builder.document())).isEqualTo("Inversor");
        assertThat(ExportLayout.partKind(builder.placeByBox(StandardComponentTypes.ALU, 40, 10, Rotation.R0,
                Map.of(), ""), builder.document())).isEqualTo(StandardComponentTypes.ALU);
    }

    @Test
    void onlyAttributesThatDifferFromLogisimsDefaultsAreWritten() throws Exception {
        String text = LogisimWriter.toText(LogisimExporter.exportDocument(nandBuilder().document(), REGISTRY)
                .project());
        Element project = parse(text).getDocumentElement();

        assertThat(project.getAttribute("source")).isEqualTo("2.7.1");
        assertThat(children(project, "lib")).extracting(library -> library.getAttribute("desc"))
                .containsExactly("#Wiring", "#Gates", "#Plexers", "#Arithmetic", "#Memory", "#I/O", "#Base");
        assertThat(children(project, "main")).singleElement()
                .satisfies(main -> assertThat(main.getAttribute("name")).isEqualTo("nand"));
        Element gate = children(children(project, "circuit").getFirst(), "comp").stream()
                .filter(component -> component.getAttribute("name").equals("NAND Gate")).findFirst().orElseThrow();
        assertThat(gate.getAttribute("lib")).isEqualTo("1");
        assertThat(children(gate, "a")).extracting(attribute -> attribute.getAttribute("name"))
                .containsExactly("inputs");
    }

    @Test
    void theProjectHoldsOnlyElementsWithNoTextBetweenThem() throws Exception {
        String text = LogisimWriter.toText(LogisimExporter.exportDocument(nandBuilder().document(), REGISTRY)
                .project());
        NodeList nodes = parse(text).getDocumentElement().getChildNodes();

        // Logisim reads only the elements of <project>; a sentence among them is not ours to write
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index).getNodeType() == Node.TEXT_NODE) {
                assertThat(nodes.item(index).getTextContent()).isBlank();
            }
        }
        assertThat(text).doesNotContain("Logisim (http");
    }

    // --- Circuits ---

    /** The keys of the export's «Diferencias reales» notes. */
    private static List<String> differences(CircuitBuilder builder) {
        return LogisimExporter.exportDocument(builder.document(), REGISTRY).notes().stream()
                .filter(note -> note.kind() == ImportNote.Kind.DIFFERENT).map(ImportNote::messageKey).toList();
    }

    private static int writeEnable(Component ram) {
        ComponentType type = REGISTRY.typeOf(ram).forComponent(ram);
        for (int pin = 0; pin < type.pinCount(); pin++) {
            if (type.pin(pin).name().equals("WE")) {
                return pin;
            }
        }
        throw new AssertionError("a RAM has WE");
    }

    /** A, B → NAND → Y. */
    private static CircuitBuilder nandBuilder() {
        CircuitBuilder builder = new CircuitBuilder("nand");
        addNand(builder, 10, 10);
        return builder;
    }

    private static void addNand(CircuitBuilder builder, int left, int top) {
        Component gate = builder.placeByBox(StandardComponentTypes.AND, left, top, Rotation.R0,
                Map.of(StandardComponentTypes.NEGATE, true), "");
        builder.attach(gate, 0, StandardComponentTypes.INPUT_PIN, ONE_BIT, "A", WIRE);
        builder.attach(gate, 1, StandardComponentTypes.INPUT_PIN, ONE_BIT, "B", WIRE);
        builder.attach(gate, 2, StandardComponentTypes.OUTPUT_PIN, ONE_BIT, "Y", WIRE);
    }

    /**
     * Gates, a register with a latch trigger, a multiplexer, a splitter with groups and a joiner, a tunnel and a custom
     * component, each block apart from the others.
     */
    private static CircuitBuilder mixedBuilder() {
        CircuitBuilder builder = new CircuitBuilder("mezcla");
        addNand(builder, 10, 10);

        Component register = builder.placeByBox(StandardComponentTypes.REGISTER, 40, 10, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.TRIGGER,
                        StandardComponentTypes.TRIGGER_HIGH), "R");
        builder.attach(register, 0, StandardComponentTypes.INPUT_PIN, EIGHT_BITS, "D", WIRE);
        builder.attach(register, 3, StandardComponentTypes.CLOCK, Map.of(), "", WIRE);
        builder.attach(register, 4, StandardComponentTypes.OUTPUT_PIN, EIGHT_BITS, "Q", WIRE);

        Component mux = builder.placeByBox(StandardComponentTypes.MUX, 10, 40, Rotation.R0, EIGHT_BITS, "");
        builder.attach(mux, 0, StandardComponentTypes.INPUT_PIN, EIGHT_BITS, "M0", WIRE);
        builder.attach(mux, 1, StandardComponentTypes.CONSTANT, Map.of(StandardComponentTypes.WIDTH, 8,
                StandardComponentTypes.VALUE, "0x12"), "", WIRE);
        builder.attach(mux, 2, StandardComponentTypes.INPUT_PIN, ONE_BIT, "SEL", WIRE);
        builder.attach(mux, 3, StandardComponentTypes.OUTPUT_PIN, EIGHT_BITS, "MY", WIRE);

        Component splitter = builder.placeByBox(StandardComponentTypes.BUS_SPLITTER, 40, 40, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-2,6|3-5"), "");
        builder.attach(splitter, 0, StandardComponentTypes.INPUT_PIN, EIGHT_BITS, "S", WIRE);
        builder.attach(splitter, 1, StandardComponentTypes.OUTPUT_PIN, Map.of(StandardComponentTypes.WIDTH, 4),
                "S0", WIRE);
        builder.attach(splitter, 2, StandardComponentTypes.OUTPUT_PIN, Map.of(StandardComponentTypes.WIDTH, 3),
                "S1", 6);

        Component joiner = builder.placeByBox(StandardComponentTypes.BUS_JOINER, 70, 40, Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"), "");
        builder.attach(joiner, 0, StandardComponentTypes.OUTPUT_PIN, EIGHT_BITS, "J", WIRE);
        builder.attach(joiner, 1, StandardComponentTypes.INPUT_PIN, Map.of(StandardComponentTypes.WIDTH, 4),
                "J0", WIRE);
        builder.attach(joiner, 2, StandardComponentTypes.INPUT_PIN, Map.of(StandardComponentTypes.WIDTH, 4),
                "J1", 6);

        Component tunnelIn = builder.placeByBox(StandardComponentTypes.TUNNEL_ENTRANCE, 10, 70, Rotation.R0,
                Map.of("name", "T"), "");
        builder.attach(tunnelIn, 0, StandardComponentTypes.INPUT_PIN, ONE_BIT, "TI", WIRE);
        Component tunnelOut = builder.placeByBox(StandardComponentTypes.TUNNEL_EXIT, 30, 70, Rotation.R0,
                Map.of("name", "T"), "");
        builder.attach(tunnelOut, 0, StandardComponentTypes.OUTPUT_PIN, ONE_BIT, "TO", WIRE);

        builder.define(ExampleDefinitions.inverter());
        Component inverter = builder.placeByBox(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 50, 70,
                Rotation.R0, Map.of(), "");
        builder.attach(inverter, 0, StandardComponentTypes.INPUT_PIN, ONE_BIT, "X", WIRE);
        builder.attach(inverter, 1, StandardComponentTypes.OUTPUT_PIN, ONE_BIT, "NX", WIRE);
        return builder;
    }

    // --- Helpers ---

    private static LogisimComponent onlyComponent(ExportResult result) {
        assertThat(result.project().circuits()).hasSize(1);
        assertThat(result.project().circuits().getFirst().components()).hasSize(1);
        return result.project().circuits().getFirst().components().getFirst();
    }

    private static LogisimPoint tenTimes(GridPoint point) {
        return new LogisimPoint(point.x() * 10, point.y() * 10);
    }

    private static LogisimProject read(String text) throws Exception {
        try (InputStream input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            return new LogisimReader().read(input);
        }
    }

    private static Document parse(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element && element.getTagName().equals(tag)) {
                children.add(element);
            }
        }
        return children;
    }
}
