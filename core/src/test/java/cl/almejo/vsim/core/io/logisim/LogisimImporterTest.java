package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.bundled.ExampleSimulation;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.ConnectivityChecks;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.tunnel.TunnelChecks;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LogisimImporterTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();

    @Test
    void theMainCircuitIsTheDocumentAndEveryOtherCircuitADefinitionInIt() throws Exception {
        ImportResult result = importFixture();
        CircuitDocument document = result.document();

        assertThat(document.name()).isEqualTo("top");
        assertThat(document.definitions()).extracting(ComponentDefinition::name)
                .containsExactlyInAnyOrder("nand", "inverter");
        assertThat(document.components()).extracting(Component::typeId)
                .contains(SubcircuitTypes.typeIdOf(definition(document, "nand").id()),
                        SubcircuitTypes.typeIdOf(definition(document, "inverter").id()));
        assertThat(definition(document, "nand").body().components()).extracting(Component::typeId)
                .contains(SubcircuitTypes.typeIdOf(definition(document, "inverter").id()));
    }

    @Test
    void theInterfacePinsKeepTheirLabelsAndTheBodyPinsCarryThem() throws Exception {
        CircuitDocument document = importFixture().document();
        ComponentDefinition nand = definition(document, "nand");

        assertThat(nand.pinout()).extracting(pin -> pin.spec().name()).containsExactly("a", "b", "y");
        for (DefinitionPin pin : nand.pinout()) {
            Component source = nand.body().components().stream()
                    .filter(component -> component.id().equals(pin.source())).findFirst().orElseThrow();
            assertThat(source.label()).isEqualTo(pin.spec().name());
        }
    }

    @Test
    void blankPinNamesBecomeUniqueOnes() throws Exception {
        ComponentDefinition inverter = definition(importFixture().document(), "inverter");

        assertThat(inverter.pinout()).extracting(pin -> pin.spec().name()).containsExactly("in1", "out1");
        assertThat(inverter.body().components()).filteredOn(component -> component.typeId().endsWith("-pin"))
                .extracting(Component::label).containsExactlyInAnyOrder("in1", "out1");
    }

    @Test
    void aCircuitWithoutAppearanceGetsLogisimsDefaultBoxWithItsLabel() throws Exception {
        ComponentDefinition inverter = definition(importFixture().document(), "inverter");

        assertThat(inverter.appearance()).isPresent();
        assertThat(inverter.appearance().orElseThrow().texts()).extracting(SymbolText::text)
                .anySatisfy(text -> assertThat(text).contains("NOT"));
        assertThat(inverter.pinout()).extracting(DefinitionPin::spec).extracting(PinSpec::isInput)
                .containsExactly(true, false);
    }

    @Test
    void theImportSavesAndReopensIdentical() throws Exception {
        CircuitDocument document = importFixture().document();
        CircuitFormat format = new CircuitFormat(REGISTRY);

        String saved = format.write(document);
        CircuitDocument reopened = format.read(saved);

        assertThat(format.write(reopened)).isEqualTo(saved);
        assertThat(reopened.definitions()).hasSize(2);
    }

    @Test
    void theReopenedImportSimulatesThroughBothLevels() throws Exception {
        CircuitFormat format = new CircuitFormat(REGISTRY);
        CircuitDocument reopened = format.read(format.write(importFixture().document()));

        for (boolean inputA : List.of(false, true)) {
            for (boolean inputB : List.of(false, true)) {
                ExampleSimulation simulation = new ExampleSimulation(reopened.copy(), Map.of());
                simulation.setInput("A", Value.of(inputA)).setInput("B", Value.of(inputB)).settle();

                assertThat(simulation.outputPin("Y")).as("Y = not (%s and %s)", inputA, inputB)
                        .isEqualTo(Value.of(!(inputA && inputB)));
                assertThat(simulation.outputPin("Z")).as("Z = not %s", inputA).isEqualTo(Value.of(!inputA));
            }
        }
    }

    @Test
    void anInstanceKeepsItsDrawingsSizeWhileTheCircuitSpreads() throws Exception {
        LogisimProject project = LogisimTestFiles.read("importer.circ");
        CircuitDocument document = LogisimImporter.importProject(project, REGISTRY).document();
        LogisimComponent nand = project.circuit("top").orElseThrow().components().stream()
                .filter(component -> component.name().equals("nand")).findFirst().orElseThrow();

        assertThat(fromTheirCorner(pinPoints(document, definition(document, "nand").id())))
                .containsExactlyInAnyOrderElementsOf(fromTheirCorner(drawnEnds(nand, project)));
    }

    @Test
    void everyCircuitKeepsLogisimsNets() throws Exception {
        LogisimProject project = LogisimTestFiles.read("importer.circ");
        List<String> mismatches = new ArrayList<>();
        List<String> circuits = new ArrayList<>();

        LogisimImporter.importProject(project, REGISTRY, (analysis, laidOut, registry) -> {
            circuits.add(analysis.circuit().name());
            mismatches.addAll(NetEquivalence.mismatches(laidOut, registry));
        });

        assertThat(circuits).containsExactlyInAnyOrder("top", "nand", "inverter");
        assertThat(mismatches).isEmpty();
    }

    @Test
    void anUnconnectedInstanceTurnsWithItsFacingAndSitsOnItsAnchor() throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <main name="main"/>
                  <circuit name="main">
                    <comp loc="(300,200)" name="box">
                      <a name="facing" val="north"/>
                    </comp>
                  </circuit>
                  <circuit name="box">
                    <comp lib="0" loc="(100,100)" name="Pin"/>
                    <comp lib="0" loc="(100,150)" name="Pin"/>
                    <comp lib="0" loc="(300,100)" name="Pin">
                      <a name="facing" val="west"/>
                      <a name="output" val="true"/>
                    </comp>
                  </circuit>
                </project>
                """;
        LogisimProject project = new LogisimReader().read(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)));
        CircuitDocument document = LogisimImporter.importProject(project, REGISTRY).document();
        LogisimComponent box = project.circuit("main").orElseThrow().components().getFirst();

        assertThat(document.components()).singleElement().extracting(Component::rotation)
                .isEqualTo(Rotation.R270);
        assertThat(pinPoints(document, definition(document, "box").id()))
                .containsExactlyInAnyOrderElementsOf(drawnEnds(box, project));
    }

    private static List<GridPoint> pinPoints(CircuitDocument document, String definitionId) {
        ComponentTypeRegistry registry = REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));
        Component instance = document.components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(definitionId)))
                .findFirst().orElseThrow();
        return registry.typeOf(instance).forComponent(instance).pins().stream()
                .map(pin -> instance.position().plus(instance.rotation().apply(pin.position())))
                .toList();
    }

    /**
     * Where an instance's pins are when its location sits on its spread place: the drawing keeps its size (Logisim's
     * 10 pixels are 1 unit around the location) while the circuit around it spreads.
     */
    private static List<GridPoint> drawnEnds(LogisimComponent instance, LogisimProject project) {
        GridPoint location = LogisimLayout.scaled(instance.location());
        return LogisimEnds.of(instance, project).stream()
                .map(end -> location.translate((end.point().x() - instance.location().x()) / 10,
                        (end.point().y() - instance.location().y()) / 10))
                .toList();
    }

    /** The points from the corner of their bounding box, to compare shapes wherever they are. */
    private static List<GridPoint> fromTheirCorner(List<GridPoint> points) {
        int left = points.stream().mapToInt(GridPoint::x).min().orElse(0);
        int top = points.stream().mapToInt(GridPoint::y).min().orElse(0);
        return points.stream().map(point -> point.translate(-left, -top)).toList();
    }

    @Test
    void anInstanceOfACircuitTheFileLacksIsNotedAndTheRestImports() throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <main name="main"/>
                  <circuit name="main">
                    <comp lib="0" loc="(100,100)" name="Pin"/>
                    <comp loc="(300,100)" name="looping"/>
                  </circuit>
                  <circuit name="looping">
                    <comp loc="(300,100)" name="looping"/>
                    <comp lib="0" loc="(100,100)" name="Pin"/>
                  </circuit>
                </project>
                """;
        ImportResult result = LogisimImporter.importFrom(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);

        assertThat(result.document().name()).isEqualTo("main");
        assertThat(result.document().components()).extracting(Component::typeId)
                .containsExactly(StandardComponentTypes.INPUT_PIN);
        assertThat(result.notes()).extracting(ImportNote::messageKey)
                .contains(LogisimNotes.RECURSIVE_CIRCUIT, LogisimNotes.MISSING_CIRCUIT);
    }

    @Test
    void theNotesAreSortedByCircuitInFileOrderThenByKind() throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <lib desc="#Gates" name="1"/>
                  <main name="second"/>
                  <circuit name="first">
                    <comp lib="1" loc="(200,100)" name="AND Gate">
                      <a name="inputs" val="12"/>
                    </comp>
                    <comp lib="0" loc="(400,100)" name="Clock"/>
                  </circuit>
                  <circuit name="second">
                    <comp lib="0" loc="(400,100)" name="Clock"/>
                    <comp loc="(300,300)" name="first"/>
                    <comp loc="(300,500)" name="missing"/>
                  </circuit>
                </project>
                """;
        ImportResult result = LogisimImporter.importFrom(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);

        assertThat(result.notes()).extracting(ImportNote::circuit, ImportNote::kind).containsExactly(
                tuple("first", ImportNote.Kind.UNSUPPORTED),
                tuple("first", ImportNote.Kind.APPROXIMATED),
                tuple("second", ImportNote.Kind.APPROXIMATED),
                tuple("second", ImportNote.Kind.INVALID));
    }

    @Test
    void aNoteNamesItsPartInTheMainCircuitOrTheMainCircuitsInstanceThatHoldsIt() throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <main name="top"/>
                  <circuit name="top">
                    <comp lib="0" loc="(400,100)" name="Clock"/>
                    <comp loc="(300,300)" name="middle"/>
                  </circuit>
                  <circuit name="middle">
                    <comp loc="(300,100)" name="leaf"/>
                  </circuit>
                  <circuit name="leaf">
                    <comp lib="0" loc="(400,100)" name="Clock"/>
                  </circuit>
                  <circuit name="spare">
                    <comp lib="0" loc="(400,100)" name="Clock"/>
                  </circuit>
                </project>
                """;
        ImportResult result = LogisimImporter.importFrom(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);
        CircuitDocument document = result.document();
        Component clock = document.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.CLOCK)).findFirst().orElseThrow();
        Component middle = document.components().stream()
                .filter(component -> component.typeId().equals(
                        SubcircuitTypes.typeIdOf(definition(document, "middle").id()))).findFirst().orElseThrow();

        assertThat(result.notes()).filteredOn(note -> note.messageKey().equals(LogisimNotes.CLOCK_PERIOD))
                .extracting(ImportNote::circuit, ImportNote::element).containsExactlyInAnyOrder(
                        tuple("top", Optional.of(clock.id())),
                        tuple("leaf", Optional.of(middle.id())),
                        tuple("spare", Optional.empty()));
    }

    @Test
    void aWireEndWhosePartsPinLandedElsewhereIsTrimmedSoNoBodyHasLooseWireEnds() throws Exception {
        String file = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <project source="2.7.1" version="1.0">
                  <lib desc="#Wiring" name="0"/>
                  <lib desc="#Plexers" name="2"/>
                  <main name="main"/>
                  <circuit name="main">
                    <comp loc="(300,200)" name="selector"/>
                  </circuit>
                  <circuit name="selector">
                    <wire from="(100,90)" to="(170,90)"/>
                    <wire from="(100,110)" to="(170,110)"/>
                    <wire from="(180,40)" to="(180,80)"/>
                    <wire from="(200,100)" to="(300,100)"/>
                    <comp lib="0" loc="(100,90)" name="Pin"/>
                    <comp lib="0" loc="(100,110)" name="Pin"/>
                    <comp lib="0" loc="(180,40)" name="Pin"><a name="facing" val="south"/></comp>
                    <comp lib="2" loc="(200,100)" name="Multiplexer">
                      <a name="selloc" val="tr"/><a name="enable" val="false"/>
                    </comp>
                    <comp lib="0" loc="(300,100)" name="Pin">
                      <a name="facing" val="west"/><a name="output" val="true"/>
                    </comp>
                  </circuit>
                </project>
                """;
        CircuitDocument document = LogisimImporter.importFrom(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY).document();

        assertThat(ConnectivityChecks.everyLooseEnd(document,
                REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document))))
                .extracting(ConnectivityProblem::cause)
                .doesNotContain(ConnectivityProblem.Cause.LOOSE_WIRE_END);
    }

    @Test
    void everyTunnelNameGetsItsOwnColourAndItsEntranceKeepsItPerCircuit() throws Exception {
        StringBuilder circuit = new StringBuilder();
        for (String name : List.of("a", "b", "c")) {
            int row = (name.charAt(0) - 'a' + 1) * 100;
            circuit.append("<wire from=\"(100,%d)\" to=\"(150,%d)\"/>\n".formatted(row, row));
            circuit.append("<comp lib=\"0\" loc=\"(100,%d)\" name=\"Pin\"/>\n".formatted(row));
            circuit.append(("<comp lib=\"0\" loc=\"(150,%d)\" name=\"Tunnel\"><a name=\"facing\" val=\"west\"/>"
                    + "<a name=\"label\" val=\"%s\"/></comp>\n").formatted(row, name));
            circuit.append(("<comp lib=\"0\" loc=\"(300,%d)\" name=\"Tunnel\"><a name=\"label\" val=\"%s\"/></comp>\n")
                    .formatted(row, name));
            circuit.append(("<comp lib=\"0\" loc=\"(350,%d)\" name=\"Pin\"><a name=\"facing\" val=\"west\"/>"
                    + "<a name=\"output\" val=\"true\"/></comp>\n").formatted(row));
            circuit.append("<wire from=\"(300,%d)\" to=\"(350,%d)\"/>\n".formatted(row, row));
        }
        String file = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<project source=\"2.7.1\" version=\"1.0\"><lib desc=\"#Wiring\" name=\"0\"/><main name=\"main\"/>"
                + "<circuit name=\"main\">" + circuit + "</circuit></project>";
        CircuitDocument document = LogisimImporter.importFrom(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY).document();

        Map<String, Integer> colors = Tunnels.colorsByName(document);
        assertThat(colors).containsOnlyKeys("a", "b", "c");
        assertThat(colors.values()).doesNotHaveDuplicates();
        List<Component> tunnels = document.components().stream()
                .filter(component -> Tunnels.isTunnel(component.typeId())).toList();
        assertThat(tunnels).hasSize(6).allSatisfy(tunnel -> {
            assertThat(Tunnels.isUndirected(tunnel)).isTrue();
            assertThat(Tunnels.colorParameterOf(tunnel)).isEqualTo(colors.get(Tunnels.nameOf(tunnel)));
        });
    }

    @Test
    void aNameDrivenFromTwoPlacesOrFromNoneHasNoTunnelProblem() throws Exception {
        // «a» is driven by two pins (Logisim allows it while they agree), «b» by none; «c» is alone.
        String file = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<project source=\"2.7.1\" version=\"1.0\"><lib desc=\"#Wiring\" name=\"0\"/><main name=\"main\"/>"
                + "<circuit name=\"main\">"
                + "<wire from=\"(100,100)\" to=\"(150,100)\"/><comp lib=\"0\" loc=\"(100,100)\" name=\"Pin\"/>"
                + "<comp lib=\"0\" loc=\"(150,100)\" name=\"Tunnel\"><a name=\"label\" val=\"a\"/></comp>"
                + "<wire from=\"(100,200)\" to=\"(150,200)\"/><comp lib=\"0\" loc=\"(100,200)\" name=\"Pin\"/>"
                + "<comp lib=\"0\" loc=\"(150,200)\" name=\"Tunnel\"><a name=\"label\" val=\"a\"/></comp>"
                + "<comp lib=\"0\" loc=\"(300,300)\" name=\"Tunnel\"><a name=\"label\" val=\"b\"/></comp>"
                + "<comp lib=\"0\" loc=\"(300,400)\" name=\"Tunnel\"><a name=\"label\" val=\"b\"/></comp>"
                + "<comp lib=\"0\" loc=\"(300,500)\" name=\"Tunnel\"><a name=\"label\" val=\"c\"/></comp>"
                + "</circuit></project>";
        ImportResult result = LogisimImporter.importFrom(
                new ByteArrayInputStream(file.getBytes(StandardCharsets.UTF_8)), REGISTRY);
        CircuitDocument document = result.document();
        ComponentTypeRegistry registry = REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));

        List<TunnelProblem> problems = TunnelChecks.problems(document,
                new NetlistCompiler(registry).compile(document), registry);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(TunnelProblem.Kind.LONE_TUNNEL);
            assertThat(problem.name()).isEqualTo("c");
        });
        assertThat(result.notes()).extracting(ImportNote::messageKey)
                .noneMatch(key -> key.startsWith("logisimImport.note.tunnel"));
    }

    private static ImportResult importFixture() throws Exception {
        try (InputStream input = LogisimImporterTest.class.getResourceAsStream("importer.circ")) {
            return LogisimImporter.importFrom(input, REGISTRY);
        }
    }

    private static ComponentDefinition definition(CircuitDocument document, String name) {
        List<ComponentDefinition> named = document.definitions().stream()
                .filter(definition -> definition.name().equals(name)).toList();
        assertThat(named).as("one definition named %s", name).hasSize(1);
        return named.getFirst();
    }
}
