package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
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

/**
 * A frame counts for the empty-sheet rule only where it paints: its border and its title bar, and its inside only
 * when a tint is painted (a tint, in colour). Two short wires in opposite corners sit in a big frame.
 */
class PrintedSheetsFramesTest {

    private record Outcome(int layoutCount, int printedCount) {
    }

    private static Outcome run(FrameTint tint, boolean blackAndWhite, boolean wholeCircuit) {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument document = new CircuitDocument();
        document.add(new Wire(document.newWireId(), new GridPoint(2, 2), new GridPoint(6, 2)));
        document.add(new Wire(document.newWireId(), new GridPoint(594, 446), new GridPoint(598, 446)));
        Frame frame = new Frame(document.newFrameId(), new GridPoint(0, 0), 600, 450, "Bloque", tint, false);
        document.add(frame);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        PrintInput input = PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist,
                document.revision(), snapshot, "c", Optional.empty()), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        PrintScope scope = wholeCircuit ? new PrintScope.WholeCircuit() : new PrintScope.Frame(frame.id());
        PrintOptions options = new PrintOptions(scope, Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.Percentage(100), true, blackAndWhite, false, "", false, true, true, true, true, true);
        PrintArea area = PrintScopes.area(input, scope, false).orElseThrow();
        PageLayout layout = PageLayout.of(area.gridArea(), PageGeometry.standard(options,
                PageGeometry.CENTIMETRE_POINTS), options.scaleMode());
        return new Outcome(layout.pageCount(), PrintedSheets.of(input, area, options, layout).count());
    }

    @Test
    void anUntintedFrameInBlackAndWhiteDoesNotFillItsInside() {
        for (boolean wholeCircuit : new boolean[] {true, false}) {
            Outcome outcome = run(FrameTint.NONE, true, wholeCircuit);
            assertThat(outcome.printedCount()).as("whole circuit %s", wholeCircuit)
                    .isLessThan(outcome.layoutCount());
        }
    }

    @Test
    void anUntintedFrameInColourDoesNotFillItsInside() {
        for (boolean wholeCircuit : new boolean[] {true, false}) {
            Outcome outcome = run(FrameTint.NONE, false, wholeCircuit);
            assertThat(outcome.printedCount()).as("whole circuit %s", wholeCircuit)
                    .isLessThan(outcome.layoutCount());
        }
    }

    @Test
    void aTintedFrameInColourFillsEverySheetItReaches() {
        for (boolean wholeCircuit : new boolean[] {true, false}) {
            Outcome outcome = run(FrameTint.BLUE, false, wholeCircuit);
            assertThat(outcome.layoutCount()).isGreaterThan(2);
            assertThat(outcome.printedCount()).isEqualTo(outcome.layoutCount());
        }
    }

    @Test
    void aTintedFrameInBlackAndWhiteIsStillOnlyItsOutline() {
        for (boolean wholeCircuit : new boolean[] {true, false}) {
            Outcome outcome = run(FrameTint.BLUE, true, wholeCircuit);
            assertThat(outcome.printedCount()).as("whole circuit %s", wholeCircuit)
                    .isLessThan(outcome.layoutCount());
        }
    }

    @Test
    void theOutlineKeepsTheSheetsItRunsAcross() {
        // the border crosses the sheets between the two corners' wires, so more than those two sheets go out
        assertThat(run(FrameTint.NONE, true, true).printedCount()).isGreaterThan(2);
    }
}
