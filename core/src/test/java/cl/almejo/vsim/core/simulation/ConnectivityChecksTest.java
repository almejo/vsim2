package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.command.ReplaceDefinitionCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.Disassembly;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ConnectivityChecksTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    ConnectivityChecksTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @Test
    void placingAHealthyComponentDoesNotWarnAboutItsUnconnectedExternalTerminals() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.add(component(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), "outer", 0, 0));
        document.add(component(StandardComponentTypes.BUFFER, "loose-top-level-gate", 20, 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void anInterfaceElementConnectedToNothingMarksTheOuterTerminalNotTheElement() {
        Component input = component(InterfacePins.INPUT_PIN, "interface", 0, 0);
        document.putDefinition(new ComponentDefinition("interface-only", "Interface", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 1), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of())));
        Component outer = component(SubcircuitTypes.typeIdOf("interface-only"), "outer", 7, 3);
        document.add(outer);

        assertThat(problems()).singleElement().satisfies(problem -> {
            assertThat(problem.cause()).isEqualTo(ConnectivityProblem.Cause.UNCONNECTED_INTERFACE);
            assertThat(problem.elementId()).isEqualTo("outer");
            assertThat(problem.pinIndex()).isZero();
            assertThat(problem.position()).isEqualTo(registry.typeOf(outer).pinLocation(outer, 0));
        });
    }

    @Test
    void aWiredInterfaceMarksNoTerminalAndFreeOuterTerminalsStayQuiet() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.add(component(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), "outer", 0, 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void anInterfaceElementLooseInsideANestedPieceMarksThatPiecesTerminalWithItsPath() {
        Component input = component(InterfacePins.INPUT_PIN, "interface", 0, 0);
        document.putDefinition(new ComponentDefinition("interface-only", "Interface", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 1), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of())));
        document.putDefinition(new ComponentDefinition("parent", "Parent", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(SubcircuitTypes.typeIdOf("interface-only"), "child", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("parent"), "outer", 0, 0));

        assertThat(problems()).filteredOn(problem ->
                        problem.cause() == ConnectivityProblem.Cause.UNCONNECTED_INTERFACE)
                .singleElement().satisfies(problem -> assertThat(problem.path())
                        .containsExactly(new ElementId("outer"), new ElementId("child")));
    }

    @Test
    void anIsolatedInternalOutputIsReportedAsWellAsItsInput() {
        Component inverter = component(StandardComponentTypes.BUFFER, "gate", 0, 0);
        document.putDefinition(new ComponentDefinition("broken", "Broken", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(inverter), List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("broken"), "outer", 0, 0));

        assertThat(looseEnds()).filteredOn(ConnectivityProblem::isPin).extracting(ConnectivityProblem::elementId)
                .containsExactly("outer/gate", "outer/gate");
        assertThat(looseEnds()).filteredOn(ConnectivityProblem::isPin).extracting(ConnectivityProblem::pinIndex)
                .containsExactly(0, 1);
    }

    @Test
    void everyInstanceGetsItsOwnPathThroughMultipleLevels() {
        document.putDefinition(new ComponentDefinition("broken", "Broken", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(StandardComponentTypes.LED, "lamp", 0, 0)),
                        List.of(), List.of())));
        document.putDefinition(new ComponentDefinition("parent", "Parent", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(SubcircuitTypes.typeIdOf("broken"), "child", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("parent"), "first", 0, 0));
        document.add(component(SubcircuitTypes.typeIdOf("parent"), "second", 10, 0));

        assertThat(looseEnds()).extracting(ConnectivityProblem::elementId)
                .containsExactly("first/child/lamp", "second/child/lamp");
    }

    @Test
    void theProblemKeepsTheWholePathFromTheVisiblePieceDownToTheFaultyPart() {
        document.putDefinition(new ComponentDefinition("broken", "Broken", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(StandardComponentTypes.LED, "lamp", 0, 0)),
                        List.of(), List.of())));
        document.putDefinition(new ComponentDefinition("parent", "Parent", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(SubcircuitTypes.typeIdOf("broken"), "child", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("parent"), "first", 0, 0));

        assertThat(looseEnds()).singleElement().satisfies(problem -> {
            assertThat(problem.cause()).isEqualTo(ConnectivityProblem.Cause.INNER_PIN);
            assertThat(problem.path()).containsExactly(new ElementId("first"), new ElementId("child"),
                    new ElementId("lamp"));
        });
    }

    @Test
    void aLoosePinOfAPartInsideABodyIsNoProblemAsOnTheCanvas() {
        document.putDefinition(new ComponentDefinition("loose", "Loose", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(StandardComponentTypes.LED, "lamp", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("loose"), "outer", 0, 0));

        assertThat(problems()).isEmpty();
        assertThat(looseEnds()).singleElement().extracting(ConnectivityProblem::cause)
                .isEqualTo(ConnectivityProblem.Cause.INNER_PIN);
    }

    @Test
    void aLooseWireEndInsideABodyIsNoProblemAsOnTheCanvas() {
        Wire dangling = new Wire(new ElementId("dangling"), new GridPoint(0, 0), new GridPoint(5, 0));
        document.putDefinition(new ComponentDefinition("dangling", "Dangling", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(), List.of(dangling), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("dangling"), "outer", 0, 0));

        assertThat(problems()).isEmpty();
        assertThat(looseEnds()).hasSize(2).allMatch(problem -> !problem.isPin());
    }

    @Test
    void aPinWithOnlyALooseWireIsStillDisconnected() {
        Component source = component(StandardComponentTypes.SWITCH, "source", 0, 0);
        document.add(source);
        GridPoint output = registry.typeOf(source).pinLocation(source, 0);
        document.add(new Wire(new ElementId("wire"), output, output.plus(new GridPoint(5, 0))));

        assertThat(internalProblems()).filteredOn(ConnectivityProblem::isPin).hasSize(1);
        assertThat(internalProblems()).filteredOn(problem -> !problem.isPin()).hasSize(1);
    }

    @Test
    void aDanglingWireInTheCircuitItselfIsNoProblem() {
        document.add(new Wire(new ElementId("dangling"), new GridPoint(0, 0), new GridPoint(5, 0)));

        assertThat(problems()).isEmpty();
    }

    @Test
    void aLooseBranchInsideABodyIsReportedEvenWhenTheNetConnectsTwoPins() {
        Component source = component(StandardComponentTypes.SWITCH, "source", 0, 0);
        GridPoint output = registry.typeOf(source).pinLocation(source, 0);
        Component sink = component(StandardComponentTypes.LED, "sink", output.x() + 4, output.y() - 3);
        document.add(source);
        document.add(sink);
        GridPoint input = registry.typeOf(sink).pinLocation(sink, 0);
        document.add(new Wire(new ElementId("wire"), output, input));
        GridPoint junction = output.plus(new GridPoint(2, 0));
        document.add(new Wire(new ElementId("branch"), junction, junction.plus(new GridPoint(0, 3))));

        assertThat(internalProblems()).filteredOn(ConnectivityProblem::isPin).isEmpty();
        assertThat(internalProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.elementId()).isEqualTo("wrapper/branch");
            assertThat(problem.position()).isEqualTo(junction.plus(new GridPoint(0, 3)));
        });
    }

    @Test
    void validInterfaceWiringInsideAnInverterIsNotReported() {
        document.putDefinition(ExampleDefinitions.inverter());
        Component instance = component(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), "outer", 0, 0);
        document.add(instance);

        assertThat(problems()).noneMatch(problem -> InstancePath.isNested(problem.elementId()));
    }

    @Test
    void overlappingWiresDoNotDisguiseALooseEnd() {
        document.add(new Wire(new ElementId("first"), new GridPoint(0, 0), new GridPoint(5, 0)));
        document.add(new Wire(new ElementId("second"), new GridPoint(0, 0), new GridPoint(3, 0)));

        assertThat(internalProblems()).extracting(ConnectivityProblem::position)
                .containsExactlyInAnyOrder(new GridPoint(0, 0), new GridPoint(5, 0));
    }

    @Test
    void reconnectingAndRecheckingClearsOnlyTheRepairedWarnings() {
        Component source = component(StandardComponentTypes.SWITCH, "source", 0, 0);
        document.add(source);
        GridPoint output = registry.typeOf(source).pinLocation(source, 0);
        assertThat(internalProblems()).hasSize(1);

        document.add(component(StandardComponentTypes.LED, "sink", output.x() - 1, output.y() - 3));

        assertThat(internalProblems()).isEmpty();
    }

    @Test
    void anEightBitParentChildInterfaceIsAValidConnection() {
        document.putDefinition(ExampleDefinitions.byteBuffer());
        document.add(component(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), "outer", 0, 0));

        assertThat(problems()).noneMatch(problem -> InstancePath.isNested(problem.elementId()));
    }

    @Test
    void tunnelNamesConnectPhysicalPinsAcrossTheCircuit() {
        Component source = component(StandardComponentTypes.SWITCH, "source", 0, 0);
        Component entrance = component(StandardComponentTypes.TUNNEL_ENTRANCE, "entrance", 4, 0);
        Component exit = component(StandardComponentTypes.TUNNEL_EXIT, "exit", 20, 0);
        GridPoint exitPin = registry.typeOf(exit).pinLocation(exit, 0);
        Component sink = component(StandardComponentTypes.LED, "sink", exitPin.x() - 1, exitPin.y() - 3);
        List.of(source, entrance, exit, sink).forEach(document::add);

        assertThat(problems()).isEmpty();
    }

    @Test
    void aSharedTunnelNameDoesNotHideItsPhysicallyDisconnectedPins() {
        document.add(component(StandardComponentTypes.TUNNEL_ENTRANCE, "entrance", 0, 0));
        document.add(component(StandardComponentTypes.TUNNEL_EXIT, "exit", 20, 0));

        assertThat(internalProblems()).hasSize(2).allMatch(ConnectivityProblem::isPin);
    }

    @Test
    void crossingWiresHaveSeparateLooseEndsAndRemainSeparateNets() {
        document.add(new Wire(new ElementId("horizontal"), new GridPoint(-5, 0), new GridPoint(5, 0)));
        document.add(new Wire(new ElementId("vertical"), new GridPoint(0, -5), new GridPoint(0, 5)));

        assertThat(internalProblems()).hasSize(4);
        assertThat(internalProblems()).extracting(ConnectivityProblem::netId)
                .containsOnly("wrapper/n:w:horizontal", "wrapper/n:w:vertical");
    }

    @Test
    void warningsAreRecomputedFromTheEmbeddedCopyAfterSavingAndReopening() throws Exception {
        putInterfaceOnlyDefinition();
        document.add(component(SubcircuitTypes.typeIdOf("interface-only"), "outer", 0, 0));
        CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

        CircuitDocument reopened = format.read(format.write(document));
        ComponentTypeRegistry reopenedRegistry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(reopened));

        assertThat(ConnectivityChecks.problems(reopened, reopenedRegistry)).isEqualTo(problems());
    }

    @Test
    void theEnginePublishesStructuralWarningsWithoutAdvancingSimulationTime() {
        putInterfaceOnlyDefinition();
        document.add(component(SubcircuitTypes.typeIdOf("interface-only"), "outer", 0, 0));
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document,
                new NetlistCompiler(registry).compile(document)));

        assertThat(engine.snapshot().time()).isZero();
        assertThat(problems()).hasSize(1);
        assertThat(engine.snapshot().problems()).containsExactlyElementsOf(problems());
    }

    @Test
    void disassemblingRepairingAndUpdatingAChildClearsItsInternalFaultInTheParent() {
        CircuitDocument workspace = new CircuitDocument();
        ComponentTypeRegistry workspaceRegistry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(workspace));
        ExampleDefinitions.inverter().body().components().forEach(component -> workspace.add(
                component.typeId().equals(StandardComponentTypes.BUFFER) ? component.translate(1, 0) : component));
        List<ElementId> selected = workspace.components().stream().map(Component::id).toList();
        DefinitionBundle broken = DefinitionBuilder.fromSelection(workspace, selected, "inverter", "Inverter",
                DefinitionBuilder.defaultPinOrder(workspace, selected), workspaceRegistry);
        document.putDefinition(broken.root());
        document.putDefinition(new ComponentDefinition("parent", "Parent", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(SubcircuitTypes.typeIdOf("inverter"), "child", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("parent"), "outer", 0, 0));
        assertThat(looseEnds()).anyMatch(problem -> problem.elementId().equals("outer/child/c2"));

        CircuitDocument repair = new CircuitDocument();
        repair.putDefinition(broken.root());
        ComponentTypeRegistry repairRegistry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(repair));
        Component instance = repairRegistry.require(SubcircuitTypes.typeIdOf("inverter"))
                .newComponent(new ElementId("instance"), new GridPoint(20, 20), Rotation.R0, Map.of(), "");
        repair.add(instance);
        Disassembly.Plan plan = Disassembly.plan(repair, instance, broken.root(), repairRegistry);
        assertThat(plan.blocked()).isFalse();
        repair.remove(instance.id());
        PasteCommand paste = new PasteCommand(plan.body(), plan.offset());
        paste.apply(repair);
        Component gate = repair.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.BUFFER)).findFirst().orElseThrow();
        repair.replace(gate.translate(-1, 0));
        DefinitionBundle fixed = DefinitionBuilder.fromSelection(repair, paste.pastedIds(), "inverter", "Inverter",
                DefinitionBuilder.defaultPinOrder(repair, paste.pastedIds()), repairRegistry);

        ReplaceDefinitionCommand update = ReplaceDefinitionCommand.prepare(document, registry, fixed);
        update.apply(document);

        assertThat(looseEnds()).noneMatch(problem -> problem.elementId().equals("outer/child/" + gate.id().value()));
        update.revert(document);
        assertThat(looseEnds()).anyMatch(problem -> problem.elementId().equals("outer/child/c2"));
    }

    // M6 (fix round E): a wire left loose by a removed pin is found from the circuit itself, so it is listed again
    // after reopening: a wire end that reaches a user component's box edge from outside where no pin is.

    /** A placed inverter «outer» (box 10,10 – 14,14; pins a at 10,12 and y at 14,12), rotated by {@code rotation}. */
    private Component placedInverter(Rotation rotation) {
        document.putDefinition(ExampleDefinitions.inverter());
        Component outer = registry.require(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER)).newComponent(
                new ElementId("outer"), new GridPoint(10, 10), rotation, Map.of(), "");
        document.add(outer);
        return outer;
    }

    private void wire(String id, int fromX, int fromY, int toX, int toY) {
        document.add(new Wire(new ElementId(id), new GridPoint(fromX, fromY), new GridPoint(toX, toY)));
    }

    private List<ConnectivityProblem> edgeWires() {
        return problems().stream()
                .filter(problem -> problem.cause() == ConnectivityProblem.Cause.WIRE_AT_PIECE_EDGE).toList();
    }

    @Test
    void aWireEndingOnAPiecesEdgeWhereNoPinIsIsAProblemAtTheWireEnd() {
        placedInverter(Rotation.R0);
        wire("w1", 6, 11, 10, 11);

        assertThat(problems()).singleElement().satisfies(problem -> {
            assertThat(problem.cause()).isEqualTo(ConnectivityProblem.Cause.WIRE_AT_PIECE_EDGE);
            assertThat(problem.elementId()).isEqualTo("outer");
            assertThat(problem.pinIndex()).isEqualTo(-1);
            assertThat(problem.position()).isEqualTo(new GridPoint(10, 11));
            assertThat(problem.netId()).startsWith(ConnectivityProblem.EDGE_WIRE_PREFIX);
        });
    }

    @Test
    void wiresAtPinsNearAPieceAlongItsEdgeOrAtItsCornersAreNoProblem() {
        placedInverter(Rotation.R0);
        wire("at-pin-a", 6, 12, 10, 12);
        wire("at-pin-y", 14, 12, 18, 12);
        wire("passing-above", 6, 8, 18, 8);
        wire("one-short", 6, 13, 9, 13);
        wire("along-the-top-edge", 11, 10, 13, 10);
        wire("along-the-left-edge-from-the-corner", 10, 10, 10, 11);
        wire("at-the-corner", 14, 14, 18, 14);
        wire("passing-through", 12, 6, 12, 18);

        assertThat(edgeWires()).isEmpty();
    }

    @Test
    void aWireEndingOnAnotherWiresJunctionAtTheEdgeIsNoProblem() {
        placedInverter(Rotation.R0);
        wire("from-the-left", 6, 13, 10, 13);
        wire("down-to-the-same-point", 10, 16, 10, 13);
        wire("into-a-wire", 18, 11, 14, 11);
        wire("through-that-end", 14, 6, 14, 13);

        assertThat(edgeWires()).isEmpty();
    }

    @Test
    void aRotatedPieceIsJudgedByItsRotatedBoxAndPins() {
        Component outer = placedInverter(Rotation.R90);
        GridPoint edge = outer.position().plus(Rotation.R90.apply(new GridPoint(0, 1)));
        GridPoint outside = outer.position().plus(Rotation.R90.apply(new GridPoint(-3, 1)));
        GridPoint pin = outer.position().plus(Rotation.R90.apply(new GridPoint(0, 2)));
        GridPoint outsidePin = outer.position().plus(Rotation.R90.apply(new GridPoint(-3, 2)));
        wire("loose", outside.x(), outside.y(), edge.x(), edge.y());
        wire("connected", outsidePin.x(), outsidePin.y(), pin.x(), pin.y());

        assertThat(edgeWires()).singleElement().satisfies(problem -> {
            assertThat(problem.elementId()).isEqualTo("outer");
            assertThat(problem.position()).isEqualTo(edge);
        });
    }

    @Test
    void aStandardPartsEdgeIsNoProblem() {
        Component gate = component(StandardComponentTypes.AND, "gate", 10, 10);
        document.add(gate);
        Bounds bounds = registry.typeOf(gate).bounds(gate);
        wire("to-the-top", bounds.x() + 1, bounds.y() - 4, bounds.x() + 1, bounds.y());

        assertThat(edgeWires()).isEmpty();
    }

    @Test
    void aLooseWireAtAnEdgeInsideAComponentIsListedForEveryPlaceItIsUsed() {
        document.putDefinition(ExampleDefinitions.inverter());
        Component inner = registry.require(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER)).newComponent(
                new ElementId("c5"), new GridPoint(10, 10), Rotation.R0, Map.of(), "");
        Wire loose = new Wire(new ElementId("w1"), new GridPoint(6, 11), new GridPoint(10, 11));
        document.putDefinition(new ComponentDefinition("holder", "Holder", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(inner), List.of(loose), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("holder"), "h1", 40, 0));
        document.add(component(SubcircuitTypes.typeIdOf("holder"), "h2", 40, 20));

        assertThat(edgeWires()).extracting(ConnectivityProblem::elementId, ConnectivityProblem::position)
                .containsExactlyInAnyOrder(tuple("h1/c5", new GridPoint(10, 11)),
                        tuple("h2/c5", new GridPoint(10, 11)));
    }

    /** A component whose only input reaches nothing inside: its outer terminal is a problem. */
    private void putInterfaceOnlyDefinition() {
        Component input = component(InterfacePins.INPUT_PIN, "interface", 0, 0);
        document.putDefinition(new ComponentDefinition("interface-only", "Interface", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 1), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of())));
    }

    private Component component(String typeId, String id, int x, int y) {
        return registry.require(typeId).newComponent(new ElementId(id), new GridPoint(x, y), Rotation.R0, Map.of(), "");
    }

    private List<ConnectivityProblem> problems() {
        return ConnectivityChecks.problems(document, registry);
    }

    private List<ConnectivityProblem> looseEnds() {
        return ConnectivityChecks.everyLooseEnd(document, registry);
    }

    private List<ConnectivityProblem> internalProblems() {
        CircuitDocument parent = new CircuitDocument();
        document.definitions().forEach(parent::putDefinition);
        parent.putDefinition(new ComponentDefinition("test-wrapper", "Wrapper", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(document.components(), document.wires(), List.of())));
        ComponentTypeRegistry wrappedRegistry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(parent));
        parent.add(wrappedRegistry.require(SubcircuitTypes.typeIdOf("test-wrapper"))
                .newComponent(new ElementId("wrapper"), GridPoint.ORIGIN, Rotation.R0, Map.of(), ""));
        return ConnectivityChecks.everyLooseEnd(parent, wrappedRegistry);
    }
}
