package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which sheets of a tiled layout go out with «Saltar hojas vacías»: a sheet is empty when nothing drawn of the printed
 * area reaches into its whole window (the overlap strips included); the others keep their reading order.
 */
class PrintedSheetsTest {

    /** 3 × 3 sheets of A4 portrait at 150 %, as {@code PageDecorationsTest} lays them out. */
    private static SheetGrid threeByThree() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);
        PageLayout layout = PageLayout.of(new Rectangle2D.Double(0, 0, 105, 125), geometry,
                new ScaleMode.Percentage(150));
        SheetGrid grid = SheetGrid.of(layout);
        assertThat(grid.columns()).isEqualTo(3);
        assertThat(grid.rows()).isEqualTo(3);
        return grid;
    }

    /** A one-unit element at the centre of sheet {@code (column, row)}, clear of every overlap strip. */
    private static Bounds inTheMiddleOf(SheetGrid grid, int column, int row) {
        Rectangle2D sheet = grid.sheetRectangle(column, row);
        return new Bounds((int) Math.floor(sheet.getCenterX()), (int) Math.floor(sheet.getCenterY()), 1, 1);
    }

    private static List<Integer> printedLayoutIndices(PrintedSheets printed) {
        List<Integer> indices = new ArrayList<>();
        for (int position = 0; position < printed.count(); position++) {
            indices.add(printed.layoutIndex(position));
        }
        return indices;
    }

    @Test
    void aSheetWithNothingOnItIsSkippedAndTheOthersKeepTheirReadingOrder() {
        SheetGrid grid = threeByThree();

        PrintedSheets printed = PrintedSheets.of(grid, List.of(inTheMiddleOf(grid, 2, 2), inTheMiddleOf(grid, 0, 0)),
                Optional.empty(), true);

        assertThat(printedLayoutIndices(printed)).containsExactly(0, 8);
        assertThat(printed.skippedCount()).isEqualTo(7);
        assertThat(printed.isSkipped(4)).isTrue();
        assertThat(printed.isSkipped(1, 1)).isTrue();
        assertThat(printed.isSkipped(8)).isFalse();
        assertThat(printed.positionOf(8)).hasValue(1);
        assertThat(printed.positionOf(4)).isEmpty();
    }

    @Test
    void anElementInTheOverlapStripCountsForBothSheets() {
        SheetGrid grid = threeByThree();
        Rectangle2D strip = grid.overlapBands(0, 0).get(0);
        Rectangle2D first = grid.sheetRectangle(0, 0);
        Bounds inTheStrip = new Bounds((int) Math.floor(strip.getCenterX()), (int) Math.floor(first.getCenterY()),
                0, 1);

        PrintedSheets printed = PrintedSheets.of(grid, List.of(inTheStrip), Optional.empty(), true);

        assertThat(printedLayoutIndices(printed)).containsExactly(0, 1);
    }

    @Test
    void aSheetWithOnlyAWireIsNotEmpty() {
        SheetGrid grid = threeByThree();
        Bounds middle = inTheMiddleOf(grid, 1, 1);
        Bounds horizontalWire = new Bounds(middle.x() - 2, middle.y(), 4, 0);

        PrintedSheets printed = PrintedSheets.of(grid, List.of(inTheMiddleOf(grid, 0, 0), horizontalWire),
                Optional.empty(), true);

        assertThat(printedLayoutIndices(printed)).containsExactly(0, 4);
    }

    @Test
    void whenEverySheetIsEmptyEverySheetIsPrinted() {
        PrintedSheets printed = PrintedSheets.of(threeByThree(), List.of(), Optional.empty(), true);

        assertThat(printed.count()).isEqualTo(9);
        assertThat(printed.skippedCount()).isZero();
    }

    @Test
    void withTheOptionOffEverySheetIsPrinted() {
        SheetGrid grid = threeByThree();

        PrintedSheets printed = PrintedSheets.of(grid, List.of(inTheMiddleOf(grid, 0, 0)), Optional.empty(), false);

        assertThat(printedLayoutIndices(printed)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(printed.isSkipped(4)).isFalse();
    }

    @Test
    void aSingleSheetIsNeverSkipped() {
        PageGeometry geometry = PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false);
        SheetGrid single = SheetGrid.of(PageLayout.of(new Rectangle2D.Double(0, 0, 10, 10), geometry,
                new ScaleMode.FitToOnePage()));

        PrintedSheets printed = PrintedSheets.of(single, List.of(), Optional.empty(), true);

        assertThat(printed.count()).isEqualTo(1);
    }

    @Test
    void whatAFrameCropCutsAwayDoesNotCount() {
        SheetGrid grid = threeByThree();
        Rectangle2D crop = grid.sheetRectangle(0, 0);

        PrintedSheets printed = PrintedSheets.of(grid, List.of(inTheMiddleOf(grid, 0, 0), inTheMiddleOf(grid, 2, 2)),
                Optional.of(crop), true);

        assertThat(printedLayoutIndices(printed)).containsExactly(0);
    }

    @Test
    void stickyNotesCountOnlyWhenTheyAreIncluded() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument document = new CircuitDocument();
        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(4, 0)));
        document.add(new Wire(document.newWireId(), new GridPoint(196, 150), new GridPoint(200, 150)));
        document.add(new StickyNote(document.newStickyNoteId(), new GridPoint(98, 72), 4, 4, StickyColor.YELLOW,
                new StickyText("en medio"), false));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        PrintInput input = PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist,
                document.revision(), snapshot, "c", Optional.empty()), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        List<Integer> counts = new ArrayList<>();
        for (boolean includeStickyNotes : new boolean[] {false, true}) {
            PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4,
                    PageOrientation.PORTRAIT, new ScaleMode.Percentage(100), true, false, false, "", includeStickyNotes,
                    true, true, true, true, true);
            PrintArea area = PrintScopes.area(input, options.scope(), includeStickyNotes).orElseThrow();
            PageLayout layout = PageLayout.of(area.gridArea(), PageGeometry.standard(options,
                    PageGeometry.CENTIMETRE_POINTS), options.scaleMode());
            assertThat(layout.pageCount()).as("several sheets").isGreaterThan(2);
            counts.add(PrintedSheets.of(input, area, options, layout).count());
        }

        assertThat(counts.get(0)).as("without the note, only the two corners").isEqualTo(2);
        assertThat(counts.get(1)).as("the note's sheet goes out too").isGreaterThan(2);
    }
}
