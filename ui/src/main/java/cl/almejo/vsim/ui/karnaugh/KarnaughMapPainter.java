package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.ui.canvas.MarchingAnts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static cl.almejo.vsim.ui.karnaugh.KarnaughMapView.BASE_INSET;
import static cl.almejo.vsim.ui.karnaugh.KarnaughMapView.inset;
import static cl.almejo.vsim.ui.karnaugh.KarnaughMapView.paintOrder;

/**
 * Paints a {@link KarnaughMapView}: headers, cells, groups, practice overlays and highlight, reading the view's live
 * state.
 */
final class KarnaughMapPainter {

    /** The same width as the canvas selection outline. */
    private static final float ANTS_WIDTH = 1.5f;
    private static final double RING_INSET = 0.18;

    private final KarnaughMapView view;

    KarnaughMapPainter(KarnaughMapView view) {
        this.view = view;
    }

    void paint(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            MapGeometry geometry = view.geometry();
            paintHeaders(graphics2D, geometry);
            paintCellBackgrounds(graphics2D, geometry);
            if (view.showGroups) {
                paintGroups(graphics2D, geometry);
            }
            paintCellTexts(graphics2D, geometry);
            if (view.practising() && view.model.problem().isEmpty()) {
                paintPracticeOverlays(graphics2D, geometry);
            }
            paintHighlight(graphics2D, geometry);
        } finally {
            graphics2D.dispose();
        }
    }

    private void paintHeaders(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = view.karnaughLayout();
        Rectangle grid = geometry.grid();
        int cell = geometry.cellSize();
        Font base = view.getFont() != null ? view.getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
        Font codes = base.deriveFont(Font.PLAIN, base.getSize2D());
        Font names = base.deriveFont(Font.BOLD, base.getSize2D());
        graphics.setColor(WizardColors.muted());
        graphics.setFont(codes);
        List<String> columnCodes = layout.columnCodes();
        for (int column = 0; column < columnCodes.size(); column++) {
            centered(graphics, columnCodes.get(column),
                    new Rectangle(grid.x + column * cell, grid.y - 20, cell, 18));
        }
        List<String> rowCodes = layout.rowCodes();
        for (int row = 0; row < rowCodes.size(); row++) {
            centered(graphics, rowCodes.get(row), new Rectangle(grid.x - 24, grid.y + row * cell, 22, cell));
        }
        // The corner: the column variables above the diagonal, the row variables below it.
        graphics.setColor(WizardColors.gridLine());
        graphics.drawLine(grid.x - 44, grid.y - 44, grid.x, grid.y);
        graphics.setColor(WizardColors.foreground());
        graphics.setFont(names);
        centered(graphics, variables(layout.columnVariables()), new Rectangle(grid.x - 22, grid.y - 44, 40, 20));
        centered(graphics, variables(layout.rowVariables()), new Rectangle(grid.x - 50, grid.y - 24, 28, 20));
    }

    private String variables(List<Integer> inputs) {
        List<String> names = new ArrayList<>();
        boolean allShort = true;
        for (int input : inputs) {
            String name = view.model.inputNames().get(input).strip();
            names.add(name);
            allShort &= name.length() <= 1;
        }
        return String.join(allShort ? "" : ",", names);
    }

    private void paintCellBackgrounds(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = view.karnaughLayout();
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Rectangle cell = geometry.cell(row, column);
                graphics.setColor(WizardColors.cellBackground());
                graphics.fill(cell);
                graphics.setColor(WizardColors.gridLine());
                graphics.draw(cell);
            }
        }
    }

    /** The values over the groups, so they stay readable; with groups, the values never grouped are muted. */
    private void paintCellTexts(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = view.karnaughLayout();
        boolean sumOfProducts = view.model.options().form() == Minimization.Form.SUM_OF_PRODUCTS;
        LogicValue neverGrouped = sumOfProducts ? LogicValue.ZERO : LogicValue.ONE;
        Font base = view.getFont() != null ? view.getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
        Font value = base.deriveFont(Font.BOLD, geometry.cellSize() * 0.38f);
        Font small = base.deriveFont(Font.PLAIN, Math.max(9f, geometry.cellSize() * 0.2f));
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Rectangle cell = geometry.cell(row, column);
                int tableRow = layout.tableRow(row, column);
                LogicValue cellValue = view.model.value(view.output, tableRow);
                graphics.setColor(view.showGroups && cellValue == neverGrouped ? WizardColors.muted()
                        : WizardColors.foreground());
                graphics.setFont(value);
                centered(graphics, valueText(cellValue), cell);
                graphics.setColor(WizardColors.muted());
                graphics.setFont(small);
                graphics.drawString(String.valueOf(tableRow), cell.x + 4, cell.y + 3
                        + graphics.getFontMetrics().getAscent());
            }
        }
    }

    static String valueText(LogicValue value) {
        return switch (value) {
            case ZERO -> "0";
            case ONE -> "1";
            case DONT_CARE -> "X";
        };
    }

    /**
     * The groups: VSim's (the explained one stronger, colours shared between maps like the legend's), or while
     * practising the student's in drawing-order colours (the selected one stronger).
     */
    private void paintGroups(Graphics2D graphics, MapGeometry geometry) {
        boolean practice = view.practising();
        List<DrawnGroup> groups = view.drawnGroups();
        OptionalInt selected = practice ? view.selectedHere() : OptionalInt.empty();
        List<Integer> order = paintOrder(groups);
        boolean dark = WizardColors.dark();
        Shape clip = graphics.getClip();
        graphics.clip(geometry.grid());
        float arc = geometry.cellSize() * 0.45f;
        for (int position = 0; position < order.size(); position++) {
            int index = order.get(position);
            boolean explained = practice ? selected.isPresent() && selected.getAsInt() == index
                    : view.explainedGroup().equals(OptionalInt.of(index));
            int colour = practice ? index : view.model.colorIndex(view.output, index);
            for (Rectangle2D piece : geometry.pieces(groups.get(index), inset(position))) {
                RoundRectangle2D shape = new RoundRectangle2D.Double(piece.getX(), piece.getY(), piece.getWidth(),
                        piece.getHeight(), arc, arc);
                graphics.setColor(GroupPalette.fill(colour, dark, explained));
                graphics.fill(shape);
                graphics.setColor(GroupPalette.color(colour, dark));
                graphics.setStroke(new BasicStroke(explained ? 3.5f : 2f));
                graphics.draw(shape);
            }
        }
        graphics.setClip(clip);
    }

    /**
     * The practice marks over the values: a dotted ring on each cell the form still needs, a strong ring on the hinted
     * cell and a dashed outline around the hinted group (shown output only), then the ants around the selected group,
     * the rectangle being drawn and the keyboard cursor.
     */
    private void paintPracticeOverlays(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = view.karnaughLayout();
        PracticeModel practice = view.model.practice();
        graphics.setColor(WizardColors.uncoveredRing());
        graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[]{1.5f, 3.5f}, 0f));
        for (int tableRow : practice.result(view.output, view.model.options().form()).uncoveredRows()) {
            graphics.draw(ring(geometry, layout.cell(tableRow)));
        }
        if (view.model.selectedOutput() == view.output) {
            OptionalInt hintRow = practice.hintRow();
            if (hintRow.isPresent()) {
                graphics.setColor(WizardColors.hintRing());
                graphics.setStroke(new BasicStroke(3f));
                graphics.draw(ring(geometry, layout.cell(hintRow.getAsInt())));
            }
            Optional<Term> hintGroup = practice.hintGroup();
            if (hintGroup.isPresent()) {
                graphics.setColor(WizardColors.hintRing());
                graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{7f, 4f}, 0f));
                graphics.draw(geometry.closedSides(layout.group(hintGroup.get()).rectangles(), BASE_INSET));
            }
        }
        Color antsColour = WizardColors.highlight();
        OptionalInt selected = view.selectedHere();
        if (selected.isPresent()) {
            List<DrawnGroup> groups = view.drawnGroups();
            int position = paintOrder(groups).indexOf(selected.getAsInt());
            MarchingAnts.draw(graphics, geometry.closedSides(groups.get(selected.getAsInt()).rectangles(),
                    inset(position)), antsColour, ANTS_WIDTH, view.antsPhase());
        }
        Optional<DrawnGroup> pending = view.pendingRectangle();
        if (pending.isPresent()) {
            MarchingAnts.draw(graphics, geometry.closedSides(pending.get().rectangles(), BASE_INSET), antsColour,
                    ANTS_WIDTH, view.antsPhase());
        } else if (view.keyboardActive) {
            Rectangle cell = geometry.cell(view.cursor.row(), view.cursor.column());
            MarchingAnts.draw(graphics, new Rectangle(cell.x + 2, cell.y + 2, cell.width - 4, cell.height - 4),
                    antsColour, ANTS_WIDTH, view.antsPhase());
        }
    }

    private static Ellipse2D ring(MapGeometry geometry, KarnaughLayout.GridCell cell) {
        Rectangle bounds = ringBounds(geometry.cell(cell.row(), cell.column()));
        return new Ellipse2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** Where the ring of an uncovered or hinted cell goes: a circle inside the cell. */
    static Rectangle ringBounds(Rectangle cell) {
        int inset = (int) Math.round(cell.width * RING_INSET);
        return new Rectangle(cell.x + inset, cell.y + inset, cell.width - 2 * inset, cell.height - 2 * inset);
    }

    private void paintHighlight(Graphics2D graphics, MapGeometry geometry) {
        OptionalInt row = view.highlightedRow();
        if (row.isEmpty()) {
            return;
        }
        KarnaughLayout.GridCell gridCell = view.karnaughLayout().cell(row.getAsInt());
        Rectangle cell = geometry.cell(gridCell.row(), gridCell.column());
        graphics.setColor(WizardColors.highlight());
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawRect(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2);
    }

    private static void centered(Graphics2D graphics, String text, Rectangle area) {
        FontMetrics metrics = graphics.getFontMetrics();
        int x = area.x + (area.width - metrics.stringWidth(text)) / 2;
        int y = area.y + (area.height - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(text, x, y);
    }
}
