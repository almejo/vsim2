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
        document.add(component(StandardComponentTypes.NOT, "loose-top-level-gate", 20, 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void anUnusedInterfaceTerminalIsNotAnInternalBrokenPin() {
        Component input = component(InterfacePins.INPUT_PIN, "interface", 0, 0);
        document.putDefinition(new ComponentDefinition("interface-only", "Interface", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 1), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("interface-only"), "outer", 0, 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void anIsolatedInternalOutputIsReportedAsWellAsItsInput() {
        Component inverter = component(StandardComponentTypes.NOT, "gate", 0, 0);
        document.putDefinition(new ComponentDefinition("broken", "Broken", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(inverter), List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("broken"), "outer", 0, 0));

        assertThat(problems()).filteredOn(ConnectivityProblem::isPin).extracting(ConnectivityProblem::elementId)
                .containsExactly("outer/gate", "outer/gate");
        assertThat(problems()).filteredOn(ConnectivityProblem::isPin).extracting(ConnectivityProblem::pinIndex)
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

        assertThat(problems()).extracting(ConnectivityProblem::elementId)
                .containsExactly("first/child/lamp", "second/child/lamp");
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
        document.putDefinition(new ComponentDefinition("broken", "Broken", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(StandardComponentTypes.LED, "lamp", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("broken"), "outer", 0, 0));
        CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

        CircuitDocument reopened = format.read(format.write(document));
        ComponentTypeRegistry reopenedRegistry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(reopened));

        assertThat(ConnectivityChecks.problems(reopened, reopenedRegistry)).isEqualTo(problems());
    }

    @Test
    void theEnginePublishesStructuralWarningsWithoutAdvancingSimulationTime() {
        document.putDefinition(new ComponentDefinition("broken", "Broken", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(StandardComponentTypes.LED, "lamp", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("broken"), "outer", 0, 0));
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
                component.typeId().equals(StandardComponentTypes.NOT) ? component.translate(1, 0) : component));
        List<ElementId> selected = workspace.components().stream().map(Component::id).toList();
        DefinitionBundle broken = DefinitionBuilder.fromSelection(workspace, selected, "inverter", "Inverter",
                DefinitionBuilder.defaultPinOrder(workspace, selected), workspaceRegistry);
        document.putDefinition(broken.root());
        document.putDefinition(new ComponentDefinition("parent", "Parent", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(component(SubcircuitTypes.typeIdOf("inverter"), "child", 0, 0)),
                        List.of(), List.of())));
        document.add(component(SubcircuitTypes.typeIdOf("parent"), "outer", 0, 0));
        assertThat(problems()).anyMatch(problem -> problem.elementId().equals("outer/child/c2"));

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
                .filter(component -> component.typeId().equals(StandardComponentTypes.NOT)).findFirst().orElseThrow();
        repair.replace(gate.translate(-1, 0));
        DefinitionBundle fixed = DefinitionBuilder.fromSelection(repair, paste.pastedIds(), "inverter", "Inverter",
                DefinitionBuilder.defaultPinOrder(repair, paste.pastedIds()), repairRegistry);

        ReplaceDefinitionCommand update = ReplaceDefinitionCommand.prepare(document, registry, fixed);
        update.apply(document);

        assertThat(problems()).noneMatch(problem -> problem.elementId().equals("outer/child/" + gate.id().value()));
        update.revert(document);
        assertThat(problems()).anyMatch(problem -> problem.elementId().equals("outer/child/c2"));
    }

    private Component component(String typeId, String id, int x, int y) {
        return registry.require(typeId).newComponent(new ElementId(id), new GridPoint(x, y), Rotation.R0, Map.of(), "");
    }

    private List<ConnectivityProblem> problems() {
        return ConnectivityChecks.problems(document, registry);
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
        return ConnectivityChecks.problems(parent, wrappedRegistry);
    }
}
