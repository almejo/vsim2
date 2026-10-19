package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * The import dialog's preview (page «Datos»): a read-only table over a {@link TablePreviewModel}, with a bar above the
 * names that groups the columns into «Entradas» and «Salidas». Inputs and outputs get two soft tints of the theme and
 * a thicker line between them; cells with a problem the error tint. Before anything is read it shows a muted hint.
 * Colours are read at paint time (the theme changes live).
 */
final class TablePreview extends JPanel {

    private static final String HINT = "hint";
    private static final String TABLE = "table";
    private static final int ROW_NUMBER_WIDTH = 52;
    private static final int MINIMUM_COLUMN_WIDTH = 44;
    private static final int DIVIDER = 2;
    /** The most rows the preview asks room for; a longer table scrolls (the user can still make it taller). */
    static final int VISIBLE_ROWS = 20;
    /** The fewest rows it keeps when the dialog is made small. */
    private static final int MINIMUM_ROWS = 3;
    /** The widest it asks to be; a wider table scrolls sideways. */
    private static final int MAXIMUM_PREFERRED_WIDTH = 900;
    /** The height of the hint shown before anything is read. */
    private static final int HINT_HEIGHT = 140;

    private final TablePreviewModel model;
    private final CardLayout cards = new CardLayout();
    private final PreviewTable table;
    private final GroupBar groupBar = new GroupBar();
    /** The column header: the bar of «Entradas» · «Salidas» over the names. */
    private final JPanel names = new JPanel(new BorderLayout());
    private final JScrollPane scroll;
    private boolean showsHint = true;

    TablePreview(TablePreviewModel model) {
        this.model = model;
        setLayout(cards);
        setOpaque(false);
        table = new PreviewTable(model);
        JLabel hint = new JLabel(Messages.text("tableImport.preview.hint"), SwingConstants.CENTER) {
            @Override
            public Color getForeground() {
                return WizardColors.muted();
            }
        };
        JPanel hintPanel = new JPanel(new BorderLayout()) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(BorderFactory.createDashedBorder(null, 4, 4));
            }

            /** The dashed border is drawn in this colour. */
            @Override
            public Color getForeground() {
                return WizardColors.gridLine();
            }
        };
        hintPanel.setOpaque(false);
        hintPanel.add(hint, BorderLayout.CENTER);
        names.setOpaque(false);
        names.add(groupBar, BorderLayout.NORTH);
        names.add(table.getTableHeader(), BorderLayout.CENTER);
        scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(names); // set again when the table is shown: JTable puts its bare header there
        add(hintPanel, HINT);
        add(scroll, TABLE);
        cards.show(this, HINT);
    }

    /** The model changed: the hint or the table, its column widths, and the first problem scrolled into view. */
    void refresh() {
        showsHint = model.isEmpty();
        cards.show(this, showsHint ? HINT : TABLE);
        revalidate(); // its size follows the rows
        if (showsHint) {
            return;
        }
        FontMetrics metrics = table.getFontMetrics(table.getFont().deriveFont(Font.BOLD));
        for (int column = 0; column < table.getColumnCount(); column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int width = column == 0 ? ROW_NUMBER_WIDTH
                    : Math.max(MINIMUM_COLUMN_WIDTH, metrics.stringWidth(model.getColumnName(column)) + 24);
            tableColumn.setPreferredWidth(width);
            tableColumn.setWidth(width);
        }
        groupBar.revalidate();
        groupBar.repaint();
        int problemRow = model.firstProblemRow();
        if (problemRow >= 0) {
            table.scrollRectToVisible(table.getCellRect(problemRow, 0, true));
        }
    }

    boolean showsHint() {
        return showsHint;
    }

    /**
     * As tall as the names and the rows, up to {@value #VISIBLE_ROWS} rows (then it scrolls); as wide as the columns,
     * up to {@value #MAXIMUM_PREFERRED_WIDTH} pixels (then it scrolls sideways). The hint asks for a fixed height.
     */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        if (showsHint) {
            return new Dimension(super.getPreferredSize().width, HINT_HEIGHT);
        }
        return sizeFor(Math.min(model.getRowCount(), VISIBLE_ROWS));
    }

    /**
     * Never larger than the whole table (every row, every column and room for a scroll bar): past that there would be
     * only empty space beside or under it.
     */
    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet() || showsHint) {
            return super.getMaximumSize();
        }
        Insets insets = scroll.getInsets();
        return new Dimension(table.getColumnModel().getTotalColumnWidth() + insets.left + insets.right
                + scroll.getVerticalScrollBar().getPreferredSize().width, sizeFor(model.getRowCount()).height);
    }

    /** A small table keeps its own size at the top left instead of stretching over the space it was given. */
    @Override
    public void doLayout() {
        super.doLayout();
        if (!showsHint) {
            Dimension largest = getMaximumSize();
            scroll.setSize(Math.min(getWidth(), largest.width), Math.min(getHeight(), largest.height));
            scroll.validate();
        }
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        if (showsHint) {
            return new Dimension(0, HINT_HEIGHT / 2);
        }
        return new Dimension(0, sizeFor(Math.min(model.getRowCount(), MINIMUM_ROWS)).height);
    }

    /** The scroll pane showing {@code rows} rows, with room for the scroll bars it will need. */
    private Dimension sizeFor(int rows) {
        Insets insets = scroll.getInsets();
        int contentWidth = table.getColumnModel().getTotalColumnWidth();
        boolean scrollsDown = model.getRowCount() > rows;
        int width = contentWidth + insets.left + insets.right
                + (scrollsDown ? scroll.getVerticalScrollBar().getPreferredSize().width : 0);
        boolean scrollsSideways = width > MAXIMUM_PREFERRED_WIDTH;
        int height = names.getPreferredSize().height + rows * table.getRowHeight() + insets.top + insets.bottom
                + (scrollsSideways ? scroll.getHorizontalScrollBar().getPreferredSize().height : 0);
        return new Dimension(Math.min(width, MAXIMUM_PREFERRED_WIDTH), height);
    }

    JTable table() {
        return table;
    }

    /** The background of a cell (or a name, with {@code header}) of preview column {@code column}. */
    private Color backgroundOf(int column, boolean problem) {
        if (problem) {
            return WizardColors.previewProblem();
        }
        if (model.isInput(column)) {
            return WizardColors.previewInput();
        }
        return model.isOutput(column) ? WizardColors.previewOutput() : WizardColors.cellBackground();
    }

    private Border cellBorder(int column) {
        Border padding = BorderFactory.createEmptyBorder(0, 6, 0, 6);
        if (!model.isFirstOutput(column)) {
            return padding;
        }
        return BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, DIVIDER, 0, 0,
                WizardColors.foreground()), padding);
    }

    /** The table: no selection, no editing, fixed column widths (a wide table scrolls sideways). */
    private final class PreviewTable extends JTable {

        PreviewTable(TablePreviewModel model) {
            super(model);
            setAutoResizeMode(AUTO_RESIZE_OFF);
            setRowSelectionAllowed(false);
            setColumnSelectionAllowed(false);
            setCellSelectionEnabled(false);
            getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            setFillsViewportHeight(true);
            setShowGrid(true);
            getTableHeader().setReorderingAllowed(false);
            getTableHeader().setResizingAllowed(true);
            setDefaultRenderer(Object.class, new CellRenderer());
            setDefaultRenderer(Integer.class, new CellRenderer());
            setDefaultRenderer(String.class, new CellRenderer());
            getTableHeader().setDefaultRenderer(new HeaderRenderer());
            getColumnModel().addColumnModelListener(new TableColumnModelListener() {
                @Override
                public void columnAdded(TableColumnModelEvent event) {
                    groupBar.revalidate();
                }

                @Override
                public void columnRemoved(TableColumnModelEvent event) {
                    groupBar.revalidate();
                }

                @Override
                public void columnMoved(TableColumnModelEvent event) {
                    groupBar.repaint();
                }

                @Override
                public void columnMarginChanged(ChangeEvent event) {
                    groupBar.revalidate();
                    groupBar.repaint();
                }

                @Override
                public void columnSelectionChanged(ListSelectionEvent event) {
                }
            });
        }

        @Override
        public Color getGridColor() {
            return WizardColors.gridLine();
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        /** The names go under the bar of «Entradas» · «Salidas». */
        @Override
        protected void configureEnclosingScrollPane() {
            super.configureEnclosingScrollPane();
            Container viewport = getParent();
            if (viewport != null && viewport.getParent() instanceof JScrollPane scroll) {
                // super moved the header into the scroll pane (a component has one parent): take it back.
                names.add(getTableHeader(), BorderLayout.CENTER);
                scroll.setColumnHeaderView(names);
            }
        }
    }

    private final class CellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(owner, value, false, false, row, column);
            boolean problem = model.isProblem(row, column);
            setHorizontalAlignment(column == 0 ? SwingConstants.RIGHT : SwingConstants.CENTER);
            setBackground(backgroundOf(column, problem));
            setForeground(column == 0 || model.isBlankCell(row, column) && !problem ? WizardColors.muted()
                    : WizardColors.foreground());
            setFont(owner.getFont());
            setBorder(cellBorder(column));
            setToolTipText(model.tooltip(row, column));
            return this;
        }
    }

    private final class HeaderRenderer extends DefaultTableCellRenderer {

        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(owner, value, false, false, row, column);
            setBackground(backgroundOf(column, model.isHeaderProblem(column)));
            setForeground(column == 0 ? WizardColors.muted() : WizardColors.foreground());
            setFont(owner.getFont().deriveFont(column == 0 ? Font.PLAIN : Font.BOLD));
            Border line = BorderFactory.createMatteBorder(0, 0, 1, 1, WizardColors.gridLine());
            if (model.isFirstOutput(column)) {
                line = BorderFactory.createCompoundBorder(line,
                        BorderFactory.createMatteBorder(0, DIVIDER, 0, 0, WizardColors.foreground()));
            }
            setBorder(BorderFactory.createCompoundBorder(line, BorderFactory.createEmptyBorder(3, 6, 3, 6)));
            setToolTipText(model.headerTooltip(column));
            return this;
        }
    }

    /** «Entradas» over the input columns and «Salidas» over the outputs, tinted like them. */
    private final class GroupBar extends JComponent {

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(table.getFont());
            return new Dimension(table.getColumnModel().getTotalColumnWidth(), metrics.getHeight() + 6);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (model.getColumnCount() == 0) {
                return;
            }
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int inputs = model.inputCount();
                int columns = model.getColumnCount();
                int left = width(0, 1);
                int inputWidth = width(1, inputs + 1);
                int outputWidth = width(inputs + 1, columns);
                graphics2D.setColor(WizardColors.cellBackground());
                graphics2D.fillRect(0, 0, left, getHeight());
                paintGroup(graphics2D, left, inputWidth, WizardColors.previewInput(),
                        Messages.text("tableImport.preview.inputs"));
                paintGroup(graphics2D, left + inputWidth, outputWidth, WizardColors.previewOutput(),
                        Messages.text("tableImport.preview.outputs"));
                graphics2D.setColor(WizardColors.gridLine());
                graphics2D.drawLine(0, getHeight() - 1, left + inputWidth + outputWidth, getHeight() - 1);
                graphics2D.setColor(WizardColors.foreground());
                graphics2D.fillRect(left + inputWidth, 0, DIVIDER, getHeight());
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintGroup(Graphics2D graphics2D, int x, int width, Color tint, String text) {
            if (width <= 0) {
                return;
            }
            graphics2D.setColor(tint);
            graphics2D.fillRect(x, 0, width, getHeight());
            graphics2D.setFont(table.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = graphics2D.getFontMetrics();
            String shown = metrics.stringWidth(text) <= width - 8 ? text : "";
            graphics2D.setColor(WizardColors.foreground());
            graphics2D.drawString(shown, x + (width - metrics.stringWidth(shown)) / 2,
                    (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        }

        private int width(int from, int to) {
            int width = 0;
            for (int column = from; column < Math.min(to, table.getColumnCount()); column++) {
                width += table.getColumnModel().getColumn(column).getWidth();
            }
            return width;
        }
    }
}
