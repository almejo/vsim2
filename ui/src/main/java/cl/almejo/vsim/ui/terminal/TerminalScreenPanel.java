package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.simulation.TerminalWindowFrame;
import cl.almejo.vsim.core.terminal.CellPosition;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The «Pantalla» tab: the screen of the latest frame with a vertical scrollbar over its history (spec §9). The text
 * grows with the window (rows × columns stay); the scrollbar follows the newest line unless the user scrolled up. A
 * written cell of the screen (not of the history above it) has the tooltip of the canvas ({@link TerminalCellToolTip}). A bell's «Destello» inverts the screen
 * for {@value #FLASH_MILLISECONDS} ms. The cells are drawn by a {@link TerminalScreenRenderer}. EDT only.
 */
final class TerminalScreenPanel extends JPanel {

    static final int FLASH_MILLISECONDS = 120;
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    /** The screen's size before the window is laid out: 80 × 24 cells of 9 × 18 pixels. */
    private static final Dimension PREFERRED_SCREEN = new Dimension(720, 432);
    private static final double MINIMUM_CELL = 2;

    private final TerminalScreenRenderer renderer;
    private final LongSupplier nanoClock;
    private final Supplier<ColorScheme> scheme;
    private final ScreenView view = new ScreenView();
    private final JScrollBar scrollBar = new JScrollBar(JScrollBar.VERTICAL);
    private TerminalWindowFrame frame;
    private Map<String, Object> resolved = Map.of();
    private long flashUntilNanos = Long.MIN_VALUE;
    private boolean flashing;

    TerminalScreenPanel(TerminalScreenRenderer renderer, LongSupplier nanoClock, Supplier<ColorScheme> scheme) {
        super(new BorderLayout());
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        add(view, BorderLayout.CENTER);
        add(scrollBar, BorderLayout.EAST);
        scrollBar.setValues(0, 1, 0, 1);
        scrollBar.addAdjustmentListener(event -> view.repaint());
        view.addMouseWheelListener(event -> scrollBar.setValue(scrollBar.getValue()
                + event.getUnitsToScroll()));
    }

    /** The component that takes the typed keys (focusable). */
    JComponent typingTarget() {
        return view;
    }

    /** Shows {@code newFrame} with the part's resolved parameters. */
    void show(TerminalWindowFrame newFrame, Map<String, Object> parameters) {
        boolean following = frame == null
                || scrollBar.getValue() + scrollBar.getVisibleAmount() >= scrollBar.getMaximum();
        frame = newFrame;
        resolved = parameters;
        int rows = newFrame.frame().rows();
        int total = newFrame.historyLines() + rows;
        int top = following ? newFrame.historyLines() : Math.min(scrollBar.getValue(), newFrame.historyLines());
        scrollBar.setValues(top, rows, 0, total);
        scrollBar.setBlockIncrement(rows);
        view.repaint();
    }

    /** New parameters (font, look) for the same frame. */
    void parametersChanged(Map<String, Object> parameters) {
        resolved = parameters;
        view.repaint();
    }

    /** History rows the latest frame holds. */
    int historyLines() {
        return frame == null ? 0 : frame.historyLines();
    }

    /** The rows shown now, as text with trailing spaces trimmed. */
    String visibleText() {
        return frame == null ? "" : visibleFrame().text();
    }

    /** The history and the screen as text (Copiar texto, Guardar .txt): trailing blank lines dropped. */
    String allText() {
        if (frame == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (int line = 0; line < frame.historyLines(); line++) {
            lines.add(rowText(historyRow(line, frame.historyColumns(line))));
        }
        for (int row = 0; row < frame.frame().rows(); row++) {
            lines.add(frame.frame().rowText(row));
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return String.join("\n", lines);
    }

    /** «Destello»: the screen inverts for a moment. */
    void flash() {
        flashUntilNanos = nanoClock.getAsLong() + FLASH_MILLISECONDS * NANOSECONDS_PER_MILLISECOND;
        flashing = true;
        view.repaint();
    }

    /** Once per frame: ends a flash whose time is up. */
    void tick() {
        if (flashing && nanoClock.getAsLong() >= flashUntilNanos) {
            flashing = false;
            view.repaint();
        }
    }

    /** The visible rows (from the scrollbar's top line) as one frame; the cursor only when its row is shown. */
    private TerminalFrame visibleFrame() {
        TerminalFrame screen = frame.frame();
        int rows = screen.rows();
        int columns = screen.columns();
        int top = Math.max(0, Math.min(scrollBar.getValue(), frame.historyLines()));
        List<TerminalCell[]> cells = new ArrayList<>(rows);
        for (int index = 0; index < rows; index++) {
            int line = top + index;
            cells.add(line < frame.historyLines() ? historyRow(line, columns)
                    : rowOf(screen, line - frame.historyLines()));
        }
        int cursorRow = screen.cursorRow() + frame.historyLines() - top;
        boolean cursorShown = screen.cursorVisible() && cursorRow >= 0 && cursorRow < rows;
        return new TerminalFrame(rows, columns, cells, cursorShown ? cursorRow : 0,
                cursorShown ? screen.cursorColumn() : 0, cursorShown, Optional.empty(), screen.lastWrittenSerial(),
                screen.version(), screen.bellCount(), screen.printedCount(), screen.scrollOrClearCount());
    }

    /** History row {@code line} cut or padded to {@code columns} (the screen may have been resized since). */
    private TerminalCell[] historyRow(int line, int columns) {
        TerminalCell[] row = new TerminalCell[columns];
        int kept = Math.min(columns, frame.historyColumns(line));
        for (int column = 0; column < columns; column++) {
            row[column] = column < kept ? frame.historyCell(line, column) : TerminalCell.BLANK;
        }
        return row;
    }

    private static TerminalCell[] rowOf(TerminalFrame screen, int row) {
        TerminalCell[] cells = new TerminalCell[screen.columns()];
        for (int column = 0; column < cells.length; column++) {
            cells[column] = screen.cell(row, column);
        }
        return cells;
    }

    private static String rowText(TerminalCell[] row) {
        StringBuilder text = new StringBuilder(row.length);
        Arrays.stream(row).forEach(cell -> text.appendCodePoint(cell.codePoint()));
        return text.toString().stripTrailing();
    }

    /** The drawing surface: whole-pixel cells filling the space, the screen centred. */
    private final class ScreenView extends JComponent {

        private ScreenView() {
            setFocusable(true);
            setPreferredSize(PREFERRED_SCREEN);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setColor(Color.BLACK);
                canvas.fillRect(0, 0, getWidth(), getHeight());
                if (frame == null) {
                    return;
                }
                TerminalFrame visible = visibleFrame();
                Rectangle2D screen = screenArea(visible);
                renderer.paint(canvas, screen, visible, frame.glyphs(), resolved, scheme.get());
                if (flashing) {
                    canvas.setXORMode(Color.WHITE);
                    canvas.fill(screen);
                    canvas.setPaintMode();
                }
            } finally {
                canvas.dispose();
            }
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            if (frame == null) {
                return null;
            }
            TerminalFrame visible = visibleFrame();
            Rectangle2D screen = screenArea(visible);
            if (!screen.contains(event.getX(), event.getY())) {
                return null;
            }
            double cellWidth = screen.getWidth() / visible.columns();
            double cellHeight = screen.getHeight() / visible.rows();
            int row = (int) ((event.getY() - screen.getY()) / cellHeight);
            int column = (int) ((event.getX() - screen.getX()) / cellWidth);
            if (row >= visible.rows() || column >= visible.columns()) {
                return null;
            }
            int screenRow = Math.max(0, Math.min(scrollBar.getValue(), frame.historyLines())) + row
                    - frame.historyLines();
            if (screenRow < 0) {
                return null;
            }
            return TerminalCellToolTip.forCell(frame.frame(), new CellPosition(screenRow, column)).orElse(null);
        }

        /** Cells of whole pixels, as large as the space allows, keeping the font's cell proportions. */
        private Rectangle2D screenArea(TerminalFrame visible) {
            TerminalFontSize fontSize = TerminalTypes.fontSize(resolved);
            double ratio = (double) fontSize.cellHeight() / fontSize.cellWidth();
            double cellWidth = Math.floor(Math.min((double) getWidth() / visible.columns(),
                    getHeight() / (visible.rows() * ratio)));
            cellWidth = Math.max(MINIMUM_CELL, cellWidth);
            double cellHeight = Math.max(MINIMUM_CELL, Math.floor(cellWidth * ratio));
            double width = cellWidth * visible.columns();
            double height = cellHeight * visible.rows();
            return new Rectangle2D.Double((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        }
    }
}
