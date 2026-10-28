package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
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
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProbeNamesTest {

    private OpenDocument openDocument;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Wire addWire(int x1, int y1, int x2, int y2) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(x1, y1), new GridPoint(x2, y2));
        openDocument.execute(new AddElementsCommand(List.of(wire)));
        return wire;
    }

    private String defaultName(ProbeTarget target) {
        return ProbeNames.defaultName(target, openDocument.document(), openDocument.netlist(),
                openDocument.registry());
    }

    private String displayName(Probe probe) {
        return ProbeNames.displayName(probe, openDocument.document(), openDocument.netlist(),
                openDocument.registry());
    }

    @Test
    void aPinIsNamedAfterItsOwnerAndAMissingOneAfterItsIds() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));

        assertThat(defaultName(new ProbeTarget.PinTarget(gate.id(), 1))).isEqualTo("c1.out");
        assertThat(defaultName(new ProbeTarget.PinTarget(new ElementId("c9"), 3))).isEqualTo("c9.3");
    }

    @Test
    void aWireUsesThePinItsNetIsAnchoredToNowElseItsOwnId() {
        Wire wire = addWire(4, 1, 8, 1);
        ProbeTarget target = new ProbeTarget.WireTarget(wire.id());
        assertThat(defaultName(target)).isEqualTo("cable " + wire.id().value());

        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of()); // out (4,1)
        assertThat(defaultName(target)).as("anchored to the switch now").isEqualTo(toggle.id().value() + ".out");
    }

    @Test
    void theDefaultNameFollowsALabelChange() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));
        Probe probe = new Probe("p1", new ProbeTarget.PinTarget(gate.id(), 1), "");
        assertThat(displayName(probe)).isEqualTo("c1.out");

        openDocument.execute(new SetLabelCommand(gate.id(), "A"));

        assertThat(displayName(probe)).isEqualTo("A.out");
    }

    @Test
    void aStoredNameIsShownAsItIsAndABlankOneIsTheDefault() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));
        ProbeTarget target = new ProbeTarget.PinTarget(gate.id(), 1);

        assertThat(displayName(new Probe("p1", target, "suma"))).isEqualTo("suma");
        assertThat(displayName(new Probe("p1", target, "  "))).isEqualTo("c1.out");
    }

    @Test
    void forgettingDefaultNamesClearsOnlyTheNamesAnOlderVsimStoredByDefault() {
        CircuitDocument document = new CircuitDocument();
        Component gate = StandardComponentTypes.registry().require(StandardComponentTypes.BUFFER)
                .newComponent(document.newComponentId(), new GridPoint(4, -1),
                        Rotation.R0, Map.of(), "A");
        document.add(gate);
        Wire wire = new Wire(document.newWireId(), new GridPoint(20, 20), new GridPoint(24, 20));
        document.add(wire);
        document.setProbes(List.of(
                new Probe("p1", new ProbeTarget.PinTarget(gate.id(), 1), "A.out"),
                new Probe("p2", new ProbeTarget.WireTarget(wire.id()), "cable " + wire.id().value()),
                new Probe("p3", new ProbeTarget.PinTarget(gate.id(), 0), "c1.in"),
                new Probe("p4", new ProbeTarget.PinTarget(gate.id(), 0), "reloj"),
                new Probe("p5", new ProbeTarget.PinTarget(new ElementId("c9"), 3), "c9.3"),
                new Probe("p6", new ProbeTarget.WireTarget(wire.id()), "wire " + wire.id().value()),
                new Probe("p7", new ProbeTarget.WireTarget(wire.id()), "cable " + wire.id().value() + " bis")));
        long revision = document.revision();

        ProbeNames.forgetDefaultNames(document, StandardComponentTypes.registry());

        assertThat(document.probes()).extracting(Probe::name)
                .as("c1.in was the default before the label A: now it is a user name; «wire wN» was the default of"
                        + " an M6 running in English")
                .containsExactly("", "", "c1.in", "reloj", "", "", "cable " + wire.id().value() + " bis");
        assertThat(document.revision()).isEqualTo(revision);
    }

    @Test
    void forgettingNothingDoesNotNotifyTheProbeListeners() {
        CircuitDocument document = new CircuitDocument();
        document.setProbes(List.of(new Probe("p1", new ProbeTarget.WireTarget(new ElementId("w1")), "")));
        AtomicInteger notifications = new AtomicInteger();
        document.addProbeListener(notifications::incrementAndGet);

        ProbeNames.forgetDefaultNames(document, StandardComponentTypes.registry());

        assertThat(notifications).hasValue(0);
    }
}
