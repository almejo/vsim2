package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/**
 * The Karnaugh map of one output (spec §3 steps 2 and 3): the variables in the corner, the Gray codes on the headers,
 * each cell's value large and its table row small. Hovering a cell lights its table row (through {@link HoverState})
 * and the row under the mouse in the table lights its cell. With groups on, the minimal cover is drawn as rounded
 * translucent rectangles in the palette colours, the explained group stronger; hovering a group explains it.
 * Colours are read from the theme at paint time.
 */
final class KarnaughMapView extends JComponent {

    static final int MINIMUM_CELL = 36;
    static final int MAXIMUM_CELL = 72;
    private static final int MARGIN = 8;
    private static final int HEADER = 46;
    private static final int BASE_INSET = 4;
    private static final int INSET_STEP = 4;

    private final KarnaughModel model;
    private final HoverState hover;
    private final int output;
    private final boolean showGroups;

    KarnaughMapView(KarnaughModel model, HoverState hover, int output, boolean showGroups) {
        this.model = model;
        this.hover = hover;
        this.output = output;
        this.showGroups = showGroups;
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                pointAt(event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover.setRow(-1);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setOpaque(false);
    }

    int output() {
        return output;
    }

    private KarnaughLayout karnaughLayout() {
        return KarnaughLayout.of(model.inputCount());
    }

    /** The groups drawn, in legend order (empty without groups or for a constant 0). */
    List<KarnaughGroup> groups() {
        if (!showGroups) {
            return List.of();
        }
        List<KarnaughGroup> groups = new ArrayList<>();
        KarnaughLayout layout = karnaughLayout();
        for (Term term : model.minimization().outputs().get(output).terms()) {
            groups.add(layout.group(term));
        }
        return groups;
    }

    /** The table row lit on the map (the row under the mouse here or in the table), if any. */
    OptionalInt highlightedRow() {
        return hover.row() >= 0 && hover.row() < model.rowCount() ? OptionalInt.of(hover.row()) : OptionalInt.empty();
    }

    /** Where the cells are for the current size: square cells, as large as fit, after the headers. */
    MapGeometry geometry() {
        KarnaughLayout layout = karnaughLayout();
        int header = headerSize();
        int available = Math.min((getWidth() - header - 2 * MARGIN) / layout.columns(),
                (getHeight() - header - 2 * MARGIN) / layout.rows());
        int cell = Math.clamp(available, MINIMUM_CELL, MAXIMUM_CELL);
        int left = MARGIN + header;
        int top = MARGIN + header;
        return new MapGeometry(layout, left, top, cell);
    }

    /** Room for the Gray codes and the corner with the variable names. */
    private static int headerSize() {
        return HEADER;
    }

    @Override
    public Dimension getPreferredSize() {
        KarnaughLayout layout = karnaughLayout();
        int cell = (MINIMUM_CELL + MAXIMUM_CELL) / 2;
        return new Dimension(2 * MARGIN + headerSize() + layout.columns() * cell + cell,
                2 * MARGIN + headerSize() + layout.rows() * cell + cell);
    }

    /** The mouse at {@code point}: lights its cell's row and, with groups, explains the group under it. */
    void pointAt(Point point) {
        MapGeometry geometry = geometry();
        hover.setRow(geometry.tableRowAt(point).orElse(-1));
        if (showGroups) {
            groupAt(geometry, point).ifPresent(hover::setGroup);
        }
    }

    /** The topmost group under {@code point}: groups are painted largest first, so the smallest one wins. */
    private OptionalInt groupAt(MapGeometry geometry, Point point) {
        if (!geometry.grid().contains(point)) {
            return OptionalInt.empty();
        }
        List<KarnaughGroup> groups = groups();
        List<Integer> order = paintOrder(groups);
        for (int position = order.size() - 1; position >= 0; position--) {
            int index = order.get(position);
            for (Rectangle2D piece : geometry.pieces(groups.get(index), inset(position))) {
                if (piece.contains(point)) {
                    return OptionalInt.of(index);
                }
            }
        }
        return OptionalInt.empty();
    }

    /** Group indices in painting order: largest first, ties by index. */
    private static List<Integer> paintOrder(List<KarnaughGroup> groups) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingInt((Integer index) -> -groups.get(index).cells().size())
                .thenComparingInt(index -> index));
        return order;
    }

    /** Overlapping groups sit at different insets so every outline stays visible. */
    private static int inset(int paintPosition) {
        return BASE_INSET + (paintPosition % 3) * INSET_STEP;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            MapGeometry geometry = geometry();
            paintHeaders(graphics2D, geometry);
            paintCellBackgrounds(graphics2D, geometry);
            if (showGroups) {
                paintGroups(graphics2D, geometry);
            }
            paintCellTexts(graphics2D, geometry);
            paintHighlight(graphics2D, geometry);
        } finally {
            graphics2D.dispose();
        }
    }

    private void paintHeaders(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = karnaughLayout();
        Rectangle grid = geometry.grid();
        int cell = geometry.cellSize();
        Font base = getFont() != null ? getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
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
            String name = model.inputNames().get(input).strip();
            names.add(name);
            allShort &= name.length() <= 1;
        }
        return String.join(allShort ? "" : ",", names);
    }

    private void paintCellBackgrounds(Graphics2D graphics, MapGeometry geometry) {
        KarnaughLayout layout = karnaughLayout();
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
        KarnaughLayout layout = karnaughLayout();
        boolean sumOfProducts = model.options().form() == Minimization.Form.SUM_OF_PRODUCTS;
        LogicValue neverGrouped = sumOfProducts ? LogicValue.ZERO : LogicValue.ONE;
        Font base = getFont() != null ? getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
        Font value = base.deriveFont(Font.BOLD, geometry.cellSize() * 0.38f);
        Font small = base.deriveFont(Font.PLAIN, Math.max(9f, geometry.cellSize() * 0.2f));
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                Rectangle cell = geometry.cell(row, column);
                int tableRow = layout.tableRow(row, column);
                LogicValue cellValue = model.value(output, tableRow);
                graphics.setColor(showGroups && cellValue == neverGrouped ? WizardColors.muted()
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

    private void paintGroups(Graphics2D graphics, MapGeometry geometry) {
        List<KarnaughGroup> groups = groups();
        List<Integer> order = paintOrder(groups);
        boolean dark = WizardColors.dark();
        Shape clip = graphics.getClip();
        graphics.clip(geometry.grid());
        float arc = geometry.cellSize() * 0.45f;
        for (int position = 0; position < order.size(); position++) {
            int index = order.get(position);
            boolean explained = index == hover.group();
            int colour = model.colorIndex(output, index);
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

    private void paintHighlight(Graphics2D graphics, MapGeometry geometry) {
        OptionalInt row = highlightedRow();
        if (row.isEmpty()) {
            return;
        }
        KarnaughLayout.GridCell gridCell = karnaughLayout().cell(row.getAsInt());
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
