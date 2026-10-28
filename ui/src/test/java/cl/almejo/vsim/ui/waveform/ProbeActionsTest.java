package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.PassThroughCircuit;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProbeActionsTest {

    private OpenDocument openDocument;
    private ProbeActions actions;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        actions = new ProbeActions(openDocument);
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private List<Probe> probes() {
        return openDocument.document().probes();
    }

    /** What the rows show: the stored name, else the live default. */
    private List<String> names() {
        return probes().stream().map(probe -> ProbeNames.displayName(probe, openDocument.document(),
                openDocument.netlist(), openDocument.registry())).toList();
    }

    private List<String> storedNames() {
        return probes().stream().map(Probe::name).toList();
    }

    private List<String> ids() {
        return probes().stream().map(Probe::id).toList();
    }

    /** Switch c1 (out at 4,1) → NOT c2 (4,1 → 8,1) → NOT c3 (8,1 → 12,1) → LED c4 (in at 12,1). */
    private List<ElementId> chain() {
        return List.of(place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of()).id(),
                place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true)).id(),
                place(openDocument, StandardComponentTypes.BUFFER, 8, -1, Map.of(StandardComponentTypes.NEGATE, true)).id(),
                place(openDocument, StandardComponentTypes.LED, 11, -2, Map.of()).id());
    }

    private Wire addWire(int x1, int y1, int x2, int y2) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(x1, y1), new GridPoint(x2, y2));
        openDocument.execute(new AddElementsCommand(List.of(wire)));
        return wire;
    }

    @Test
    void aSelectedChainGivesOneChannelPerNetInDocumentOrder() {
        List<ElementId> chain = chain();
        List<ElementId> reversed = new ArrayList<>(chain);
        Collections.reverse(reversed);

        ProbeActions.AddResult result = actions.addSelection(reversed);

        assertThat(names()).containsExactly("c1.out", "c2.out", "c3.out");
        assertThat(storedNames()).as("new channels store no name: it stays live").containsExactly("", "", "");
        assertThat(result.addedIds()).isEqualTo(ids());
        assertThat(result.existingId()).isEmpty();
        assertThat(result.capReached()).isFalse();
        assertThat(openDocument.history().undoDescription()).contains("command.addProbes");
        assertThat(Messages.commandName("command.addProbes")).isEqualTo("agregar canales");
        openDocument.history().undo();
        assertThat(probes()).isEmpty();
    }

    @Test
    void aComponentsPinsComeInputsFirstInPinOrder() {
        Component flipFlop = place(openDocument, StandardComponentTypes.FLIP_FLOP_DATA, 20, 0, Map.of());

        actions.addComponentPins(flipFlop.id());

        assertThat(probes()).extracting(Probe::target).containsExactly(
                new ProbeTarget.PinTarget(flipFlop.id(), 0), new ProbeTarget.PinTarget(flipFlop.id(), 2),
                new ProbeTarget.PinTarget(flipFlop.id(), 1), new ProbeTarget.PinTarget(flipFlop.id(), 3));
        assertThat(names()).containsExactly("c1.d", "c1.clock", "c1.q", "c1.nQ");
    }

    @Test
    void aNetAlreadyShownAddsNothingAndNamesTheChannelToBlink() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());                    // out (4,1)
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 8, -1, Map.of(StandardComponentTypes.NEGATE, true));     // in (8,1)
        Wire wire = addWire(4, 1, 8, 1);
        actions.addComponentPins(gate.id());
        String gateInput = probes().get(0).id();

        ProbeActions.AddResult again = actions.addWire(wire.id());

        assertThat(again.addedAny()).isFalse();
        assertThat(again.existingId()).contains(gateInput);
        assertThat(probes()).hasSize(2);
    }

    @Test
    void aWireProbeAnchorsToItsDriverAndAPinlessWireIsNamedAfterTheWire() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Wire driven = addWire(4, 1, 8, 1);
        Wire alone = addWire(20, 20, 24, 20);

        actions.addSelection(List.of(driven.id(), alone.id()));

        assertThat(probes()).extracting(Probe::target).containsExactly(new ProbeTarget.PinTarget(toggle.id(), 0),
                new ProbeTarget.WireTarget(alone.id()));
        assertThat(names()).containsExactly("c1.out", "cable " + alone.id().value());
    }

    @Test
    void theSixtyFifthChannelIsRefused() {
        List<ElementId> switches = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            switches.add(place(openDocument, StandardComponentTypes.SWITCH, index * 10, 0, Map.of()).id());
        }

        ProbeActions.AddResult result = actions.addSelection(switches);

        assertThat(probes()).hasSize(ProbeActions.MAXIMUM_CHANNELS);
        assertThat(result.capReached()).isTrue();
        assertThat(result.addedIds()).hasSize(ProbeActions.MAXIMUM_CHANNELS);
        assertThat(actions.addComponentPins(switches.get(69)).capReached()).isTrue();
        assertThat(Messages.format("waveform.capReached", ProbeActions.MAXIMUM_CHANNELS))
                .isEqualTo("Se alcanzó el máximo de 64 canales");
    }

    @Test
    void renamingMovingAndRemovingAreOneUndoStepEach() {
        actions.addSelection(chain());
        List<String> original = ids();
        String first = original.get(0);
        String second = original.get(1);

        actions.rename(first, "  reloj ");
        assertThat(names()).containsExactly("reloj", "c2.out", "c3.out");
        assertThat(openDocument.history().undoDescription()).contains("command.renameProbe");
        assertThat(storedNames()).containsExactly("reloj", "", "");
        actions.rename(first, " ");
        assertThat(names()).as("an empty name restores the default").containsExactly("c1.out", "c2.out", "c3.out");
        assertThat(storedNames()).containsExactly("", "", "");

        actions.moveDown(first);
        assertThat(ids()).containsExactly(second, first, original.get(2));
        assertThat(openDocument.history().undoDescription()).contains("command.moveProbe");
        actions.moveUp(second);
        assertThat(ids()).as("already first").containsExactly(second, first, original.get(2));
        openDocument.history().undo();
        assertThat(ids()).containsExactlyElementsOf(original);

        actions.remove(second);
        assertThat(ids()).containsExactly(first, original.get(2));
        assertThat(openDocument.history().undoDescription()).contains("command.removeProbe");
        actions.removeAll();
        assertThat(probes()).isEmpty();
        assertThat(openDocument.history().undoDescription()).contains("command.removeAllProbes");
        openDocument.history().undo();
        assertThat(ids()).containsExactly(first, original.get(2));
    }

    @Test
    void topLevelNetsJoinedThroughATemplateGetOneChannel() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);

        ProbeActions.AddResult first = actions.addWire(circuit.left().id());
        ProbeActions.AddResult second = actions.addWire(circuit.rightAcross().id());

        assertThat(first.addedIds()).hasSize(1);
        assertThat(second.addedAny()).isFalse();
        assertThat(second.existingId()).contains(first.addedIds().get(0));
        assertThat(probes()).hasSize(1);
    }
}
