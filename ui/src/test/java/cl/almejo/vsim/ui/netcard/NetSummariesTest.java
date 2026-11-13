package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.PassThroughCircuit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.netcard.NetCardCircuits.netOf;
import static cl.almejo.vsim.ui.netcard.NetCardCircuits.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

class NetSummariesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void closeTheDocument() {
        openDocument.close();
    }

    private NetSummary summaryOf(Wire wire, SimulationSnapshot snapshot) {
        return NetSummaries.forWire(openDocument, wire.id(), snapshot).orElseThrow();
    }

    private static List<String> texts(List<NetEnd> ends) {
        return ends.stream().map(end -> end.partText() + " · " + end.pinText()).toList();
    }

    @Test
    void theOutputOfAnAndHasOneDriverAndTwoReaders() {
        NetCardCircuits.AndExample example = NetCardCircuits.andExample(openDocument);

        NetSummary summary = summaryOf(example.andOutput(), SimulationSnapshot.EMPTY);

        assertThat(summary.width()).isEqualTo(1);
        assertThat(summary.wires()).hasSize(4).contains(example.andOutput().id(), example.notInput().id());
        assertThat(summary.drivers()).extracting(NetEnd::componentId).containsExactly(example.and().id());
        assertThat(texts(summary.drivers())).containsExactly("AND U1 · out");
        assertThat(summary.readers()).extracting(NetEnd::componentId)
                .containsExactlyInAnyOrder(example.ledL1().id(), example.not().id());
        assertThat(texts(summary.readers())).containsExactlyInAnyOrder("LED L1 · in", "NOT U2 · in");
        assertThat(summary.tunnelNames()).isEmpty();
        assertThat(summary.problem()).isEmpty();
    }

    @Test
    void aCurrentSnapshotGivesTheValueOfTheNet() {
        NetCardCircuits.AndExample example = NetCardCircuits.andExample(openDocument);
        SimulationSnapshot snapshot = snapshot(openDocument, Map.of(netOf(openDocument, example.andOutput()),
                Value.ONE));

        NetSummary summary = summaryOf(example.andOutput(), snapshot);

        assertThat(summary.value()).isEqualTo(new NetValue.Simulated(Value.ONE));
    }

    @Test
    void aStaleSnapshotIsNotSimulatingNeverAWrongValue() {
        NetCardCircuits.AndExample example = NetCardCircuits.andExample(openDocument);
        SimulationSnapshot stale = new SimulationSnapshot(1, openDocument.netlistToken() + 1, 10, true,
                Map.of(netOf(openDocument, example.andOutput()), Value.ONE), Map.of(), List.of(), Optional.empty());

        assertThat(summaryOf(example.andOutput(), stale).value()).isEqualTo(new NetValue.NotSimulating());
        assertThat(summaryOf(example.andOutput(), SimulationSnapshot.EMPTY).value())
                .isEqualTo(new NetValue.NotSimulating());
    }

    @Test
    void aNetThroughATunnelHasTheWiresOfBothSidesTheFarReadersAndTheTunnelName() {
        NetCardCircuits.TunnelExample example = NetCardCircuits.tunnelExample(openDocument);

        NetSummary summary = summaryOf(example.nearWire(), SimulationSnapshot.EMPTY);

        assertThat(summary.wires()).containsExactlyInAnyOrder(example.nearWire().id(), example.farWire().id());
        assertThat(summary.readers()).extracting(NetEnd::componentId).containsExactly(example.ledL3().id());
        assertThat(summary.drivers()).extracting(NetEnd::componentId).containsExactly(example.and().id());
        assertThat(summary.tunnelNames()).containsExactly("LISTO");
    }

    @Test
    void aShortCircuitIsTheProblemWithTheValueOfEachDriver() {
        Component high = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        Component low = place(openDocument, StandardComponentTypes.SWITCH, 0, 6, Map.of("value", "0"));
        Wire joint = addWire(openDocument, 4, 1, 4, 7);
        String netId = netOf(openDocument, joint);
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE),
                new DriverRef(low.id().value(), 0, Value.ZERO)), 5);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, openDocument.netlistToken(), 10, true,
                Map.of(netId, Value.ONE.withConflict()), Map.of(), List.of(shortCircuit), Optional.empty());

        NetSummary summary = summaryOf(joint, snapshot);

        assertThat(summary.problem()).containsInstanceOf(NetProblem.ShortCircuit.class);
        NetProblem.ShortCircuit problem = (NetProblem.ShortCircuit) summary.problem().orElseThrow();
        assertThat(problem.drivers()).extracting(NetEnd::componentId).containsExactly(high.id(), low.id());
        assertThat(problem.drivers()).extracting(NetEnd::valueText)
                .containsExactly(Optional.of("1"), Optional.of("0"));
        assertThat(summary.drivers()).extracting(NetEnd::valueText)
                .containsExactly(Optional.of("1"), Optional.of("0"));
    }

    @Test
    void aBusKeepsTheFormatOfThePartThatDrivesIt() {
        NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.CONSTANT,
                Map.of(StandardComponentTypes.WIDTH, 8, ConstantValue.FORMAT, ConstantValue.FIXED,
                        ConstantValue.FRACTION, 3), "", 0, 10, 40);
        Wire bus = addWire(openDocument, 10, 40, 20, 40);

        assertThat(summaryOf(bus, SimulationSnapshot.EMPTY).partFormat()).contains(Probe.fixedFormat(3));
    }

    @Test
    void aCustomComponentIsNotEnteredItsOwnPinIsTheEnd() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);

        NetSummary summary = summaryOf(circuit.left(), SimulationSnapshot.EMPTY);

        assertThat(summary.wires()).containsExactly(circuit.left().id());
        assertThat(summary.drivers()).extracting(NetEnd::componentId).containsExactly(circuit.high().id());
        assertThat(summary.readers()).extracting(NetEnd::componentId).containsExactly(circuit.instance().id());
    }

    @Test
    void theDriversOfAShortCircuitThroughACustomComponentAreAllNamedWithTheirValues() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);
        String inner = InstancePath.child(circuit.instance().id().value(), "c1");
        ShortCircuit shortCircuit = new ShortCircuit(circuit.rootNetId(openDocument), List.of(
                new DriverRef(circuit.high().id().value(), 0, Value.ONE), new DriverRef(inner, 0, Value.ZERO)), 5);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(), List.of(shortCircuit), Optional.empty());

        NetSummary summary = summaryOf(circuit.left(), snapshot);

        assertThat(NetCardText.drivers(summary)).hasSize(2);
        NetEnd fromInside = NetCardText.drivers(summary).get(1);
        assertThat(fromInside.componentId()).isEqualTo(circuit.instance().id());
        assertThat(fromInside.valueText()).contains("0");
    }

    @Test
    void pinsOfDifferentWidthsOnOneNetAreAWidthMismatch() {
        NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.CONSTANT,
                Map.of(StandardComponentTypes.WIDTH, 4), "", 0, 10, 40);
        NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.LED, Map.of(), "L1", 0, 20, 40);
        Wire joint = addWire(openDocument, 10, 40, 20, 40);

        NetSummary summary = summaryOf(joint, SimulationSnapshot.EMPTY);

        assertThat(summary.problem()).containsInstanceOf(NetProblem.WidthMismatch.class);
        assertThat(NetCardText.problem(summary.problem().orElseThrow())).isNotBlank();
    }

    @Test
    void aNetNothingDrivesIsUndriven() {
        Component led = NetCardCircuits.placeWithPinAt(openDocument, StandardComponentTypes.LED, Map.of(), "L1", 0,
                20, 10);
        Wire loose = addWire(openDocument, 10, 10, 20, 10);

        NetSummary summary = summaryOf(loose, SimulationSnapshot.EMPTY);

        assertThat(summary.readers()).extracting(NetEnd::componentId).containsExactly(led.id());
        assertThat(summary.drivers()).isEmpty();
        assertThat(summary.problem()).contains(new NetProblem.Undriven());
    }

    @Test
    void aWireThatIsGoneHasNoSummary() {
        assertThat(NetSummaries.forWire(openDocument, new ElementId("w99"), SimulationSnapshot.EMPTY)).isEmpty();
    }
}
