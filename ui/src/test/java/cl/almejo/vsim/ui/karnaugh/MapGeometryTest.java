package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.Term;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class MapGeometryTest {

    private static final int LEFT = 40;
    private static final int TOP = 30;
    private static final int CELL = 50;
    private static final int INSET = 4;

    private final KarnaughLayout layout = KarnaughLayout.of(4);
    private final MapGeometry geometry = new MapGeometry(layout, LEFT, TOP, CELL);

    @Test
    void cellsSitInAGridAfterTheHeaders() {
        assertThat(geometry.grid()).isEqualTo(new Rectangle(LEFT, TOP, 4 * CELL, 4 * CELL));
        assertThat(geometry.cell(1, 2)).isEqualTo(new Rectangle(LEFT + 2 * CELL, TOP + CELL, CELL, CELL));
        assertThat(geometry.tableRowAt(new Point(LEFT + 2 * CELL + 10, TOP + CELL + 10)))
                .isEqualTo(OptionalInt.of(layout.tableRow(1, 2)));
        assertThat(geometry.tableRowAt(new Point(LEFT - 1, TOP + 1))).isEmpty();
    }

    @Test
    void anInnerGroupIsOneInsetRectangleOverItsCells() {
        // BD: rows AB = 01, 11 (map rows 1, 2) and columns CD = 01, 11 (map columns 1, 2).
        List<Rectangle2D> pieces = geometry.pieces(layout.group(new Term(0b0101, 0b0101)), INSET);
        assertThat(pieces).containsExactly(new Rectangle2D.Double(LEFT + CELL + INSET, TOP + CELL + INSET,
                2 * CELL - 2 * INSET, 2 * CELL - 2 * INSET));
    }

    @Test
    void aWrappingGroupIsTwoPiecesThatRunPastTheEdges() {
        // A'D': map rows 0, 1 and columns 0 and 3.
        List<Rectangle2D> pieces = geometry.pieces(layout.group(new Term(0b1001, 0b0000)), INSET);
        assertThat(pieces).hasSize(2);
        Rectangle grid = geometry.grid();
        assertThat(pieces.get(0).getMinX()).isLessThan(grid.getMinX());
        assertThat(pieces.get(0).getMaxX()).isEqualTo(LEFT + CELL - INSET);
        assertThat(pieces.get(1).getMinX()).isEqualTo(LEFT + 3 * CELL + INSET);
        assertThat(pieces.get(1).getMaxX()).isGreaterThan(grid.getMaxX());
        assertThat(pieces.get(0).getMinY()).isEqualTo(TOP + INSET);
        assertThat(pieces.get(0).getMaxY()).isEqualTo(TOP + 2 * CELL - INSET);
    }

    @Test
    void theFourCornersAreFourQuartersOpenOutward() {
        // B'D': map rows 0 and 3, columns 0 and 3.
        List<Rectangle2D> pieces = geometry.pieces(layout.group(new Term(0b0101, 0b0000)), INSET);
        assertThat(pieces).hasSize(4);
        Rectangle grid = geometry.grid();
        Rectangle2D topLeft = pieces.get(0);
        assertThat(topLeft.getMinX()).isLessThan(grid.getMinX());
        assertThat(topLeft.getMinY()).isLessThan(grid.getMinY());
        assertThat(topLeft.getMaxX()).isEqualTo(LEFT + CELL - INSET);
        assertThat(topLeft.getMaxY()).isEqualTo(TOP + CELL - INSET);
        Rectangle2D bottomRight = pieces.get(3);
        assertThat(bottomRight.getMaxX()).isGreaterThan(grid.getMaxX());
        assertThat(bottomRight.getMaxY()).isGreaterThan(grid.getMaxY());
    }

    @Test
    void thePointerCellIsCountedPastTheEdgesToo() {
        assertThat(geometry.columnAt(LEFT + 2 * CELL + 1)).isEqualTo(2);
        assertThat(geometry.columnAt(LEFT - 1)).isEqualTo(-1);
        assertThat(geometry.columnAt(LEFT + 4 * CELL + CELL + 1)).isEqualTo(5);
        assertThat(geometry.rowAt(TOP - CELL - 1)).isEqualTo(-2);
        assertThat(geometry.rowAt(TOP)).isZero();
    }

    @Test
    void aDrawnGroupIsDrawnLikeTheSameKarnaughGroup() {
        KarnaughGroup corners = layout.group(new Term(0b0101, 0b0000));
        DrawnGroup drawn = layout.corners().orElseThrow();
        assertThat(geometry.pieces(drawn, INSET)).isEqualTo(geometry.pieces(corners, INSET));
    }

    @Test
    void theClosedSidesOfAnInnerGroupAreItsWholeOutline() {
        DrawnGroup inner = layout.drawn(1, 1, 2, 2);
        Rectangle2D bounds = geometry.closedSides(inner.rectangles(), INSET).getBounds2D();
        assertThat(bounds).isEqualTo(geometry.pieces(inner, INSET).getFirst());
    }

    @Test
    void theClosedSidesOfAWrappedGroupLeaveTheOpenEdgesOut() {
        DrawnGroup wrapped = layout.drawn(1, 3, 1, 2); // columns 3 and 0 of row 1
        Path2D sides = geometry.closedSides(wrapped.rectangles(), INSET);
        Rectangle grid = geometry.grid();
        assertThat(sides.getBounds2D().getMinX()).isEqualTo(LEFT);
        assertThat(sides.getBounds2D().getMaxX()).isEqualTo(grid.getMaxX());
        int segments = 0;
        for (PathIterator iterator = sides.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            if (iterator.currentSegment(new double[6]) == PathIterator.SEG_LINETO) {
                segments++;
            }
        }
        assertThat(segments).isEqualTo(6); // each half: top, bottom and its inner side
    }

    @Test
    void thePaletteIsFixedAndHasALighterVariantForDarkThemes() {
        assertThat(GroupPalette.size()).isGreaterThanOrEqualTo(8);
        for (int index = 0; index < GroupPalette.size(); index++) {
            Color light = GroupPalette.color(index, false);
            Color dark = GroupPalette.color(index, true);
            assertThat(light).isNotEqualTo(dark);
            assertThat(luminance(dark)).as("index " + index).isGreaterThan(luminance(light));
        }
        assertThat(GroupPalette.color(GroupPalette.size(), false)).isEqualTo(GroupPalette.color(0, false));
        assertThat(GroupPalette.fill(0, false, false).getAlpha()).isLessThan(GroupPalette.fill(0, false, true).getAlpha());
    }

    private static double luminance(Color color) {
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }
}
