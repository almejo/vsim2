package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProbeMarkersTest {

    private OpenDocument openDocument;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private void setProbes(Probe... probes) {
        openDocument.execute(new SetProbesCommand(openDocument.document().probes(), List.of(probes),
                "command.addProbes"));
    }

    private List<ProbeMarker> markers() {
        return ProbeMarkers.of(openDocument.document(), openDocument.netlist(), openDocument.registry(),
                probe -> ProbeNames.displayName(probe, openDocument.document(), openDocument.netlist(),
                        openDocument.registry()));
    }

    private static Probe pin(String id, Component component, int pinIndex, String name) {
        return new Probe(id, new ProbeTarget.PinTarget(component.id(), pinIndex), name);
    }

    @Test
    void aPinChannelHangsFromThePinAndAWireChannelFromTheMiddleOfTheWire() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true)); // out (8,1)
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(20, 20), new GridPoint(25, 20));
        openDocument.execute(new AddElementsCommand(List.of(wire)));
        setProbes(new Probe("p1", new ProbeTarget.WireTarget(wire.id()), ""), pin("p2", gate, 1, ""));

        assertThat(markers()).containsExactly(
                new ProbeMarker(new GridPoint(22, 20), ProbeMarker.Side.RIGHT, List.of("p1"),
                        List.of("cable " + wire.id().value())),
                new ProbeMarker(new GridPoint(8, 1), ProbeMarker.Side.RIGHT, List.of("p2"), List.of("c1.out")));
    }

    @Test
    void eachPinTakesTheSideOfTheBoxItIsOn() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true)); // box x 4–8, y -1–3
        Component flipFlop = place(openDocument, StandardComponentTypes.FLIP_FLOP_DATA, 20, 0, Map.of());
        setProbes(pin("p1", gate, 0, ""), pin("p2", gate, 1, ""), pin("p3", flipFlop, 2, "reloj"));

        assertThat(markers()).extracting(ProbeMarker::anchor, ProbeMarker::side).containsExactly(
                org.assertj.core.groups.Tuple.tuple(new GridPoint(4, 1), ProbeMarker.Side.LEFT),
                org.assertj.core.groups.Tuple.tuple(new GridPoint(8, 1), ProbeMarker.Side.RIGHT),
                org.assertj.core.groups.Tuple.tuple(new GridPoint(22, 4), ProbeMarker.Side.BOTTOM));
    }

    @Test
    void aRotatedComponentsPinMovesItsMarker() {
        ComponentType not = openDocument.registry().require(StandardComponentTypes.BUFFER);
        Component turned = not.newComponent(openDocument.document().newComponentId(), new GridPoint(10, 10),
                Rotation.R90, Map.of(), "");
        openDocument.execute(new AddElementsCommand(List.of(turned)));
        setProbes(pin("p1", turned, 1, "salida"));

        GridPoint pinLocation = not.pinLocation(turned, 1);
        assertThat(pinLocation).isNotEqualTo(new GridPoint(14, 12)); // where the R0 output would be
        // R90: the output (4, 2) turns to (-2, 4), so the pin (8, 14) is on the bottom edge of the box x 6–10, y 10–14
        assertThat(markers()).containsExactly(
                new ProbeMarker(pinLocation, ProbeMarker.Side.BOTTOM, List.of("p1"), List.of("salida")));
    }

    @Test
    void channelsWhoseTargetIsGoneOrDoesNotResolveHaveNoMarker() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));
        setProbes(pin("p1", gate, 1, ""), new Probe("p2", new ProbeTarget.PinTarget(new ElementId("c9"), 0), ""),
                new Probe("p3", new ProbeTarget.WireTarget(new ElementId("w9")), ""),
                pin("p4", gate, 7, "no existe"));
        assertThat(markers()).extracting(ProbeMarker::probeIds).containsExactly(List.of("p1"));

        openDocument.execute(new RemoveElementsCommand(List.of(gate.id())));
        assertThat(markers()).isEmpty();
        openDocument.history().undo();
        assertThat(markers()).extracting(ProbeMarker::probeIds).containsExactly(List.of("p1"));
    }

    @Test
    void twoChannelsOnOneAnchorShareOneMarkerWithBothNames() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));
        setProbes(pin("p1", gate, 1, ""), pin("p2", gate, 0, ""), pin("p3", gate, 1, "salida"));

        assertThat(markers()).containsExactly(
                new ProbeMarker(new GridPoint(8, 1), ProbeMarker.Side.RIGHT, List.of("p1", "p3"),
                        List.of("c1.out", "salida")),
                new ProbeMarker(new GridPoint(4, 1), ProbeMarker.Side.LEFT, List.of("p2"), List.of("c1.in")));
    }
}
