package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Deleting what a waveform probe watches re-anchors the probe on what survives of its net, or removes it. */
class RemoveElementsProbesTest {

    private static final int AND_OUTPUT = 2;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType and = registry.require(StandardComponentTypes.AND);
    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);

    private Component place(GridPoint position) {
        Component component = and.newComponent(document.newComponentId(), position, Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    /** A gate whose first input pin is at {@code point}. */
    private Component placeWithInputAt(GridPoint point) {
        Component probe = and.newComponent(new ElementId("c999"), new GridPoint(0, 0), Rotation.R0, Map.of(), "");
        GridPoint offset = and.pinLocation(probe, 0);
        return place(new GridPoint(point.x() - offset.x(), point.y() - offset.y()));
    }

    private Wire wire(GridPoint start, GridPoint end) {
        Wire wire = new Wire(document.newWireId(), start, end);
        document.add(wire);
        return wire;
    }

    private Probe probeOnWire(String id, Wire wire, String name, String format) {
        ProbeTarget target = ProbeResolver.anchor(wire.id(), document, new NetlistCompiler(registry).compile(document),
                registry).orElseThrow();
        return new Probe(id, target, name, format);
    }

    private void setProbes(Probe... probes) {
        history.execute(new SetProbesCommand(document.probes(), List.of(probes), "command.addProbes"));
    }

    @Test
    void deletingTheDriverOfAProbedWireKeepsTheChannelOnWhatSurvivesOfItsNet() {
        Component driver = place(new GridPoint(0, 0));
        GridPoint output = and.pinLocation(driver, AND_OUTPUT);
        GridPoint far = new GridPoint(output.x() + 6, output.y());
        wire(output, far);
        Component reader = placeWithInputAt(far);
        Probe probe = probeOnWire("p1", document.wires().getFirst(), "salida", Probe.FORMAT_BINARY);
        assertThat(probe.target()).as("anchored on the driver").isEqualTo(new ProbeTarget.PinTarget(driver.id(),
                AND_OUTPUT));
        setProbes(probe);

        history.execute(new RemoveElementsCommand(List.of(driver.id()), registry));

        assertThat(document.probes()).containsExactly(
                new Probe("p1", new ProbeTarget.PinTarget(reader.id(), 0), "salida", Probe.FORMAT_BINARY));
        history.undo();
        assertThat(document.probes()).containsExactly(probe);
        assertThat(document.component(driver.id())).isPresent();
        history.redo();
        assertThat(document.probes()).extracting(Probe::target)
                .containsExactly(new ProbeTarget.PinTarget(reader.id(), 0));
    }

    @Test
    void aSurvivingWireTakesTheProbeWhenNoPinOfTheNetSurvives() {
        Component driver = place(new GridPoint(0, 0));
        GridPoint output = and.pinLocation(driver, AND_OUTPUT);
        Wire wire = wire(output, new GridPoint(output.x() + 6, output.y()));
        Probe probe = probeOnWire("p1", wire, "", Probe.FORMAT_HEX);
        setProbes(probe);

        history.execute(new RemoveElementsCommand(List.of(driver.id()), registry));

        assertThat(document.probes()).containsExactly(new Probe("p1", new ProbeTarget.WireTarget(wire.id()), "",
                Probe.FORMAT_HEX));
    }

    @Test
    void aProbeWhoseWholeNetIsDeletedIsRemovedAndUndoBringsItBack() {
        Component lonely = place(new GridPoint(0, 0));
        Component other = place(new GridPoint(20, 0));
        Probe gone = new Probe("p1", new ProbeTarget.PinTarget(lonely.id(), AND_OUTPUT), "q");
        Probe kept = new Probe("p2", new ProbeTarget.PinTarget(other.id(), AND_OUTPUT), "r");
        setProbes(gone, kept);

        history.execute(new RemoveElementsCommand(List.of(lonely.id()), registry));
        assertThat(document.probes()).containsExactly(kept);

        history.undo();
        assertThat(document.probes()).containsExactly(gone, kept);
        history.redo();
        assertThat(document.probes()).containsExactly(kept);
    }

    @Test
    void aDeletedWireOfAPinlessNetHandsItsProbeToAnotherWireOfTheNet() {
        Wire first = wire(new GridPoint(0, 0), new GridPoint(4, 0));
        Wire second = wire(new GridPoint(4, 0), new GridPoint(8, 0));
        setProbes(new Probe("p1", new ProbeTarget.WireTarget(first.id()), ""));

        history.execute(new RemoveElementsCommand(List.of(first.id()), registry));

        assertThat(document.probes()).containsExactly(new Probe("p1", new ProbeTarget.WireTarget(second.id()), ""));
    }

    @Test
    void deletingEverythingAProbeCouldMoveToRemovesIt() {
        Component driver = place(new GridPoint(0, 0));
        GridPoint output = and.pinLocation(driver, AND_OUTPUT);
        Wire wire = wire(output, new GridPoint(output.x() + 6, output.y()));
        setProbes(probeOnWire("p1", wire, "", Probe.FORMAT_HEX));

        history.execute(new RemoveElementsCommand(List.of(driver.id(), wire.id()), registry));

        assertThat(document.probes()).isEmpty();
    }

    @Test
    void probesThatWatchNothingRemovedAreLeftAlone() {
        Component gate = place(new GridPoint(0, 0));
        Probe dangling = new Probe("p1", new ProbeTarget.PinTarget(new ElementId("c77"), 0), "");
        Probe watching = new Probe("p2", new ProbeTarget.PinTarget(gate.id(), AND_OUTPUT), "");
        setProbes(dangling, watching);
        Component unrelated = place(new GridPoint(30, 30));

        history.execute(new RemoveElementsCommand(List.of(unrelated.id()), registry));

        assertThat(document.probes()).containsExactly(dangling, watching);
    }
}
