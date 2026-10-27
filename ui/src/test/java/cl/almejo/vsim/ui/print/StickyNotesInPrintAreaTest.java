package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A sticky note far from the circuit neither grows the printed area nor is painted, unless asked. */
class StickyNotesInPrintAreaTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private StickyNote farNote;

    private PrintInput input() {
        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(10, 0)));
        farNote = new StickyNote(document.newStickyNoteId(), new GridPoint(200, 150), 8, 6, StickyColor.YELLOW,
                new StickyText("lejos"), false);
        document.add(farNote);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        return PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist, document.revision(),
                snapshot, "c", Optional.empty()), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void theWholeCircuitAreaLeavesTheNoteOutUnlessAsked() {
        PrintInput input = input();
        PrintArea without = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        assertThat(without.gridArea().getWidth()).isLessThan(40);
        assertThat(without.elements()).doesNotContain(farNote.id());
        PrintArea with = PrintScopes.area(input, new PrintScope.WholeCircuit(), true).orElseThrow();
        assertThat(with.gridArea().getWidth()).isGreaterThan(200);
        assertThat(with.elements()).contains(farNote.id());
    }

    @Test
    void aSelectionThatIsOnlyANoteHasNoAreaUnlessAsked() {
        PrintInput input = input();
        PrintScope onlyTheNote = new PrintScope.Selection(Set.of(farNote.id()));
        assertThat(PrintScopes.area(input, onlyTheNote)).isEmpty();
        assertThat(PrintScopes.area(input, onlyTheNote, true)).isPresent();
    }

    @Test
    void theOptionsDefaultToLeavingNotesOut() {
        assertThat(PrintOptions.defaults("t").includeStickyNotes()).isFalse();
    }
}
