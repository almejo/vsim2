package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The cells of the test table (hito 4, task 5), one column per edge and a dimmed ghost column after them, with its
 * edge header ({@link Header}) and its names column ({@link Names}) for the scroll pane's borders, as the wave strip
 * has them. A click on a column chooses it (the diagram shows its state); a click on an input toggles it and on an
 * «esperado» cell cycles · → 0 → 1 → ·; Mayús+click and Mayús+arrows stretch a block that a typed value fills
 * ({@link MachineTestKeyboard}). It only paints and points: every change goes through the {@link MachineTestTable}.
 */
final class MachineTestTableGrid extends JComponent {

    private static final int CHOSEN_HEADER_ALPHA = 90;
    private static final int DISABLED_PLUS_ALPHA = 90;

    private final MachineTestTable table;
    private final transient Supplier<ColorScheme> scheme;
    private final Header header = new Header();
    private final Names names = new Names();
    private final Corner corner = new Corner();
    private MachineTestTableModel model;
    /** The keyboard's cursor row (a row index of an editable row) and the block's other corner. */
    private int cursorRow;
    private int anchorRow;
    private int anchorColumn;

    MachineTestTableGrid(MachineTestTable table, Supplier<ColorScheme> scheme, MachineTestTableModel model) {
        this.table = table;
        this.scheme = scheme;
        this.model = model.withLayout(layoutFor(model.machine()));
        this.cursorRow = firstEditableRow();
        this.anchorRow = cursorRow;
        this.anchorColumn = this.model.chosenColumn();
        setOpaque(true);
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().registerComponent(names);
        ToolTipManager.sharedInstance().registerComponent(header);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                pressed(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                popupIfTrigger(event, MachineTestTableGrid.this, model.columnAt(event.getX()));
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                repaint();
            }
        });
        new MachineTestKeyboard(this, table).bindKeys();
    }

    MachineTestTableModel model() {
        return model;
    }

    /** The sizes of the table's pieces for its font, the interface scale and the states' names. */
    private MachineTestLayout layoutFor(StateMachine machine) {
        return MachineTestLayout.of(font(this), this::getFontMetrics, machine);
    }

    /** A new look and feel, font or interface scale: the pieces are sized again. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (model != null) {
            setModel(model);
            header.revalidate();
            names.revalidate();
        }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.TABLE;
                }
            };
            accessibleContext.setAccessibleName(Messages.text("machine.test.table.name"));
        }
        return accessibleContext;
    }

    Header header() {
        return header;
    }

    Names names() {
        return names;
    }

    Corner corner() {
        return corner;
    }

    /** Shows {@code newModel}, keeping the cursor and the block where they still fit. */
    void setModel(MachineTestTableModel newModel) {
        MachineTestTableModel sized = newModel.withLayout(layoutFor(newModel.machine()));
        boolean resized = sized.width() != model.width() || sized.height() != model.height()
                || !sized.layout().equals(model.layout());
        model = sized;
        List<MachineTestTableModel.Row> rows = model.rows();
        if (cursorRow >= rows.size() || !rows.get(cursorRow).kind().editable()) {
            cursorRow = firstEditableRow();
            anchorRow = cursorRow;
        }
        if (anchorRow >= rows.size() || !rows.get(anchorRow).kind().editable()) {
            anchorRow = cursorRow;
        }
        anchorColumn = Math.min(anchorColumn, model.columnCount() - 1);
        if (resized) {
            revalidate();
            header.revalidate();
            names.revalidate();
        }
        repaint();
        header.repaint();
        names.repaint();
    }

    private int firstEditableRow() {
        List<MachineTestTableModel.Row> rows = model.rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).kind().editable()) {
                return index;
            }
        }
        return -1;
    }

    /** The cells a typed value changes: the block between the cursor and its anchor. */
    MachineTestCellPainter.Block block() {
        if (cursorRow < 0) {
            return new MachineTestCellPainter.Block(0, -1, 0, -1);
        }
        int chosen = model.chosenColumn();
        return new MachineTestCellPainter.Block(Math.min(anchorRow, cursorRow), Math.max(anchorRow, cursorRow),
                Math.min(anchorColumn, chosen), Math.max(anchorColumn, chosen));
    }

    /** Chooses {@code column}; without {@code extend} the block shrinks to the cursor's cell. */
    void moveTo(int column, boolean extend) {
        int fitted = Math.max(0, Math.min(column, model.columnCount() - 1));
        if (!extend) {
            anchorColumn = fitted;
            anchorRow = cursorRow;
        }
        table.chooseColumn(fitted);
        repaint();
    }

    /** Moves the cursor {@code delta} editable rows; without {@code extend} the block shrinks to its cell. */
    void moveRow(int delta, boolean extend) {
        List<MachineTestTableModel.Row> rows = model.rows();
        List<Integer> editable = IntStream.range(0, rows.size())
                .filter(index -> rows.get(index).kind().editable()).boxed().toList();
        if (editable.isEmpty()) {
            return;
        }
        int position = Math.max(0, editable.indexOf(cursorRow));
        cursorRow = editable.get(Math.max(0, Math.min(position + delta, editable.size() - 1)));
        if (!extend) {
            anchorRow = cursorRow;
            anchorColumn = model.chosenColumn();
        }
        repaint();
    }

    private void pressed(MouseEvent event) {
        requestFocusInWindow();
        if (popupIfTrigger(event, this, model.columnAt(event.getX()))) {
            return;
        }
        OptionalInt column = model.columnAt(event.getX());
        OptionalInt row = model.rowAt(event.getY());
        if (event.getButton() != MouseEvent.BUTTON1 || column.isEmpty() || row.isEmpty()) {
            return;
        }
        MachineTestTableModel.Row found = model.rows().get(row.getAsInt());
        boolean editable = found.kind().editable();
        if (editable) {
            cursorRow = row.getAsInt();
        }
        if (event.isShiftDown()) {
            table.chooseColumn(column.getAsInt());
            repaint();
            return;
        }
        anchorRow = cursorRow;
        anchorColumn = column.getAsInt();
        table.chooseColumn(column.getAsInt());
        if (editable) {
            table.toggle(found, column.getAsInt());
        }
    }

    /** Opens the column menu of a column header or cell for a popup trigger; true when it was one. */
    private boolean popupIfTrigger(MouseEvent event, JComponent source, OptionalInt column) {
        if (!event.isPopupTrigger() || column.isEmpty()) {
            return false;
        }
        moveTo(column.getAsInt(), false);
        if (source.isShowing()) {
            table.columnMenu(column.getAsInt()).show(source, event.getX(), event.getY());
        }
        return true;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        OptionalInt row = model.rowAt(event.getY());
        int column = model.columnOrGhostAt(event.getX());
        if (row.isEmpty() || column < 0 || column > model.columnCount()) {
            return null;
        }
        return model.tooltip(row.getAsInt(), column).orElse(null);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(model.width(), model.height());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            MachineTestCellPainter.paint(canvas, model, scheme.get(), getWidth(), getHeight(), block(), cursorRow,
                    isFocusOwner());
        } finally {
            canvas.dispose();
        }
    }

    private Font font(JComponent component) {
        return component.getFont() != null ? component.getFont() : UIManager.getFont("Label.font");
    }

    /** «1 2 3…» over the columns, the edge's arrow (↓ or ↑) where each edge falls, and nothing over the ghost. */
    final class Header extends JComponent {

        Header() {
            setOpaque(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    OptionalInt column = model.columnAt(event.getX());
                    if (popupIfTrigger(event, Header.this, column)) {
                        return;
                    }
                    if (event.getButton() == MouseEvent.BUTTON1 && model.plusAt(event.getX())) {
                        MachineTestTableGrid.this.requestFocusInWindow();
                        table.addColumn();
                    } else if (event.getButton() == MouseEvent.BUTTON1 && column.isPresent()) {
                        MachineTestTableGrid.this.requestFocusInWindow();
                        moveTo(column.getAsInt(), event.isShiftDown());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    popupIfTrigger(event, Header.this, model.columnAt(event.getX()));
                }
            });
        }

        /** Over the «+» after the last column: what it does, or why it cannot. */
        @Override
        public String getToolTipText(MouseEvent event) {
            if (!model.plusAt(event.getX())) {
                return null;
            }
            return model.canAddColumn() ? Messages.text("machine.test.column.addTip")
                    : Messages.format("machine.test.column.full", MachineTests.MAXIMUM_COLUMNS);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(model.width(), model.layout().headerHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                ColorScheme colours = scheme.get();
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setColor(colours.diagramBackground());
                canvas.fillRect(0, 0, getWidth(), getHeight());
                Font plain = font(this);
                Font bold = plain.deriveFont(Font.BOLD);
                String arrow = StateMachine.EDGE_RISING.equals(model.machine().edge()) ? "↑" : "↓";
                int width = model.layout().columnWidth();
                int headerHeight = model.layout().headerHeight();
                int inset = model.layout().padding() / 2;
                for (int column = 0; column < model.columnCount(); column++) {
                    boolean chosen = column == model.chosenColumn();
                    canvas.setFont(chosen ? bold : plain);
                    FontMetrics metrics = canvas.getFontMetrics();
                    int baseline = (headerHeight + metrics.getAscent() - metrics.getDescent()) / 2;
                    if (chosen) {
                        canvas.setColor(PainterSupport.withAlpha(colours.selection(), CHOSEN_HEADER_ALPHA));
                        canvas.fillRect(column * width, 0, width, headerHeight);
                    }
                    String number = String.valueOf(column + 1);
                    canvas.setColor(colours.diagramTrace());
                    canvas.drawString(number, column * width + (width - metrics.stringWidth(number)) / 2 - inset,
                            baseline);
                    canvas.setFont(plain);
                    metrics = canvas.getFontMetrics();
                    canvas.setColor(MachineTestCellPainter.quiet(colours));
                    canvas.drawString(arrow, (column + 1) * width - metrics.stringWidth(arrow) - inset, baseline);
                }
                paintPlus(canvas, plain, baselineOf(canvas, plain));
                canvas.setColor(colours.diagramGrid());
                canvas.drawLine(0, headerHeight - 1, getWidth(), headerHeight - 1);
            } finally {
                canvas.dispose();
            }
        }
    }

    /** The «+» header after the last column, greyed (fainter still when no more columns fit). */
    private void paintPlus(Graphics2D canvas, Font plain, int baseline) {
        canvas.setFont(plain.deriveFont(Font.BOLD));
        FontMetrics metrics = canvas.getFontMetrics();
        Color grey = MachineTestCellPainter.quiet(scheme.get());
        canvas.setColor(model.canAddColumn() ? grey : PainterSupport.withAlpha(grey, DISABLED_PLUS_ALPHA));
        String plus = Messages.text("machine.test.column.add");
        canvas.drawString(plus, model.columnCount() * model.layout().columnWidth()
                + (model.layout().ghostWidth() - metrics.stringWidth(plus)) / 2, baseline);
    }

    private int baselineOf(Graphics2D canvas, Font font) {
        FontMetrics metrics = canvas.getFontMetrics(font);
        return (model.layout().headerHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
    }

    /** The rows' names, as the wave strip's names column writes them; a name cut with «…» shows whole on hover. */
    final class Names extends JComponent {

        Names() {
            setOpaque(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(model.layout().namesWidth(), model.height());
        }

        private String shown(MachineTestTableModel.Row row, FontMetrics metrics) {
            return PainterSupport.ellipsized(row.label(), metrics, getWidth() - 2 * model.layout().padding());
        }

        private Font fontOf(MachineTestTableModel.Row row) {
            Font font = font(this);
            return switch (row.kind()) {
                case GROUP -> MachineTestLayout.groupFont(font).deriveFont(Font.BOLD);
                case BUS -> MachineTestLayout.groupFont(font);
                default -> font;
            };
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            OptionalInt row = model.rowAt(event.getY());
            if (row.isEmpty()) {
                return null;
            }
            MachineTestTableModel.Row found = model.rows().get(row.getAsInt());
            return shown(found, getFontMetrics(fontOf(found))).equals(found.label()) ? null : found.label();
        }

        // CPD-OFF state-machine branch: the test table's three headers paint their background alike
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                ColorScheme colours = scheme.get();
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setColor(colours.diagramBackground());
                canvas.fillRect(0, 0, getWidth(), getHeight());
                for (MachineTestTableModel.Row row : model.rows()) {
                    canvas.setFont(fontOf(row));
                    FontMetrics metrics = canvas.getFontMetrics();
                    int baseline = row.top() + (row.height() + metrics.getAscent() - metrics.getDescent()) / 2;
                    boolean quiet = row.kind() == MachineTestTableModel.Kind.GROUP
                            || row.kind() == MachineTestTableModel.Kind.BUS;
                    canvas.setColor(quiet ? MachineTestCellPainter.quiet(colours) : colours.diagramTrace());
                    canvas.drawString(shown(row, metrics), model.layout().padding(), baseline);
                    canvas.setColor(colours.diagramGrid());
                    canvas.drawLine(0, row.top() + row.height() - 1, getWidth(), row.top() + row.height() - 1);
                }
            } finally {
                canvas.dispose();
            }
        }
        // CPD-ON
    }

    /** «flanco», over the names and beside the edge numbers. */
    final class Corner extends JComponent {

        Corner() {
            setOpaque(true);
        }

        // CPD-OFF state-machine branch: the test table's three headers paint their background alike
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                ColorScheme colours = scheme.get();
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setColor(colours.diagramBackground());
                canvas.fillRect(0, 0, getWidth(), getHeight());
                canvas.setFont(font(this));
                FontMetrics metrics = canvas.getFontMetrics();
                canvas.setColor(MachineTestCellPainter.quiet(colours));
                canvas.drawString(Messages.text("machine.test.header.edge"), model.layout().padding(),
                        (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
                canvas.setColor(colours.diagramGrid());
                canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            } finally {
                canvas.dispose();
            }
        }
        // CPD-ON
    }
}
