package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.waveform.WaveformPainter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * Paints the cells of the test table (hito 4, task 5) on the wave strip's background: inputs, the state band and the
 * outputs as waves ({@link WaveformPainter#paintSteps}, the strip's own drawing), «esperado» as 0, 1 or a grey «·»
 * («no importa»; never an «x», which reads as ✗), and ✓ or ✗ per column with an 18 % tint. A result that came out
 * wrong gets the fail tint too. Every colour is read from the scheme given now (never cached).
 */
final class MachineTestCellPainter {

    /** The ✓ green the user chose (2026-11-18): deep on a light surface, light on a dark one. */
    private static final Color PASS_ON_LIGHT = new Color(0x2E7D32);
    private static final Color PASS_ON_DARK = new Color(0x66BB6A);
    /** 18 % of 255: the cells' tint. */
    static final int TINT_ALPHA = 46;
    private static final int CHOSEN_COLUMN_ALPHA = 55;
    private static final int BLOCK_ALPHA = 70;
    private static final int GHOST_ALPHA = 130;
    private static final float VALUE_SIZE = 13f;
    private static final float VERDICT_SIZE = 15f;
    /** «·» is a small glyph: drawn larger and bold so «no importa» is seen. */
    private static final float DOT_SIZE = 18f;
    private static final BasicStroke LINE = new BasicStroke(1f);
    private static final BasicStroke DIVIDER = new BasicStroke(2f);
    private static final BasicStroke CURSOR = new BasicStroke(2f);

    /** The cells a key changes: editable rows {@code firstRow..lastRow} (row indices) and a column range. */
    record Block(int firstRow, int lastRow, int firstColumn, int lastColumn) {

        boolean contains(int row, int column) {
            return row >= firstRow && row <= lastRow && column >= firstColumn && column <= lastColumn;
        }
    }

    private MachineTestCellPainter() {
    }

    /** The ✓ colour on the table's surface (the scheme's wave background). */
    static Color pass(ColorScheme scheme) {
        return ReadableColors.perceivedBrightness(scheme.diagramBackground()) < 0.5 ? PASS_ON_DARK : PASS_ON_LIGHT;
    }

    /** The ✗ colour: the scheme's error red, as a conflict is drawn. */
    static Color fail(ColorScheme scheme) {
        return scheme.wireConflict();
    }

    /** «no importa» and the dimmed ghost column: the scheme's grey. */
    static Color quiet(ColorScheme scheme) {
        return scheme.wireUnknown();
    }

    /**
     * Paints the whole grid of {@code model}, {@code width} × {@code height}; {@code block} is the keyboard's block and
     * {@code cursorRow} its cursor row (drawn only when {@code focused}).
     */
    static void paint(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme, int width, int height,
                      Block block, int cursorRow, boolean focused) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(scheme.diagramBackground());
        graphics.fillRect(0, 0, width, height);
        int columns = model.columnCount();
        int chosenLeft = model.chosenColumn() * MachineTestTableModel.COLUMN_WIDTH;
        graphics.setColor(PainterSupport.withAlpha(scheme.selection(), CHOSEN_COLUMN_ALPHA));
        graphics.fillRect(chosenLeft, 0, MachineTestTableModel.COLUMN_WIDTH, model.height());
        List<MachineTestTableModel.Row> rows = model.rows();
        for (int index = 0; index < rows.size(); index++) {
            paintRow(graphics, model, scheme, index, rows.get(index));
        }
        paintGhost(graphics, model, scheme);
        paintLines(graphics, model, scheme, width);
        for (int row = block.firstRow(); row <= block.lastRow(); row++) {
            if (!rows.get(row).kind().editable()) {
                continue;
            }
            for (int column = block.firstColumn(); column <= block.lastColumn(); column++) {
                Rectangle cell = model.cell(row, column);
                graphics.setColor(PainterSupport.withAlpha(scheme.selection(), BLOCK_ALPHA));
                graphics.fill(cell);
            }
        }
        if (focused && cursorRow >= 0 && columns > 0) {
            Rectangle cell = model.cell(cursorRow, model.chosenColumn());
            graphics.setColor(scheme.selection());
            graphics.setStroke(CURSOR);
            graphics.drawRect(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2);
        }
    }

    private static void paintRow(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme, int index,
                                 MachineTestTableModel.Row row) {
        int columns = model.columnCount();
        double width = MachineTestTableModel.COLUMN_WIDTH;
        switch (row.kind()) {
            case GROUP, BUS -> {
                // the names column writes them; the grid only draws their divider (paintLines)
            }
            case INPUT -> {
                List<Optional<Value>> values = new ArrayList<>();
                for (int column = 0; column < columns; column++) {
                    values.add(Optional.of(Value.of(model.input(column, row.pinId().orElseThrow()))));
                }
                WaveformPainter.paintSteps(graphics, values, 0, width, row.top(), scheme, null, null);
            }
            case STATE -> paintStates(graphics, model, scheme, row);
            case OUTPUT -> {
                String pinId = row.pinId().orElseThrow();
                List<Optional<Value>> values = new ArrayList<>();
                for (int column = 0; column < columns; column++) {
                    if (model.mismatch(column, pinId)) {
                        tint(graphics, model.cell(index, column), fail(scheme));
                    }
                    values.add(model.columnResult(column)
                            .map(result -> Value.of(Boolean.TRUE.equals(result.outputs().get(pinId)))));
                }
                if (model.result().stop().isEmpty()) {
                    WaveformPainter.paintSteps(graphics, values, 0, width, row.top(), scheme, null, null);
                }
            }
            case EXPECTED -> {
                for (int column = 0; column < columns; column++) {
                    LogicValue expected = model.expected(column, row.pinId().orElseThrow());
                    boolean nothing = expected == LogicValue.DONT_CARE;
                    centered(graphics, symbol(expected), model.cell(index, column),
                            nothing ? quiet(scheme) : scheme.diagramTrace(), nothing ? DOT_SIZE : VALUE_SIZE,
                            nothing ? Font.BOLD : Font.PLAIN);
                }
            }
            case VERDICT -> {
                for (int column = 0; column < columns; column++) {
                    Optional<MachineTestRun.Verdict> verdict = model.columnResult(column)
                            .map(MachineTestRun.ColumnResult::verdict);
                    Rectangle cell = model.cell(index, column);
                    if (verdict.filter(MachineTestRun.Verdict.PASS::equals).isPresent()) {
                        tint(graphics, cell, pass(scheme));
                        centered(graphics, "✓", cell, pass(scheme), VERDICT_SIZE, Font.BOLD);
                    } else if (verdict.filter(MachineTestRun.Verdict.FAIL::equals).isPresent()) {
                        tint(graphics, cell, fail(scheme));
                        centered(graphics, "✗", cell, fail(scheme), VERDICT_SIZE, Font.BOLD);
                    }
                }
            }
        }
    }

    /** «0», «1» or «·». */
    static String symbol(LogicValue value) {
        return switch (value) {
            case ZERO -> "0";
            case ONE -> "1";
            case DONT_CARE -> "·";
        };
    }

    /** The state band: each column's state as a named band in its colour, joined while the state stays. */
    private static void paintStates(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme,
                                    MachineTestTableModel.Row row) {
        if (model.result().stop().isPresent()) {
            return;
        }
        List<MachineState> states = model.machine().states();
        List<Optional<Value>> values = new ArrayList<>();
        for (int column = 0; column < model.columnCount(); column++) {
            values.add(model.columnResult(column).map(result -> model.stateIndex(result.stateId()))
                    .filter(code -> code >= 0)
                    .map(code -> Value.of(MachineTestTableModel.STATE_CODE_WIDTH, code)));
        }
        LongFunction<String> names = code -> code < states.size() ? states.get((int) code).name() : "";
        LongFunction<Optional<StateColor>> colours = code -> code < states.size() ? states.get((int) code).color()
                : Optional.empty();
        WaveformPainter.paintSteps(graphics, values, 0, MachineTestTableModel.COLUMN_WIDTH, row.top(), scheme,
                names, colours);
    }

    /** The dimmed ghost column after the last edge: «→ S2» in the state row. */
    private static void paintGhost(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme) {
        Optional<String> text = model.ghostText();
        if (text.isEmpty()) {
            return;
        }
        List<MachineTestTableModel.Row> rows = model.rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).kind() == MachineTestTableModel.Kind.STATE) {
                Rectangle cell = model.cell(index, model.columnCount());
                centered(graphics, text.get(), cell, PainterSupport.withAlpha(scheme.diagramTrace(), GHOST_ALPHA),
                        VALUE_SIZE - 1, Font.PLAIN);
            }
        }
    }

    /** A line under each row, one between the columns, and a thicker one above each group (Karnaugh's divider). */
    private static void paintLines(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme,
                                   int width) {
        int height = model.height();
        graphics.setColor(scheme.diagramGrid());
        graphics.setStroke(LINE);
        for (int column = 1; column <= model.columnCount(); column++) {
            int x = column * MachineTestTableModel.COLUMN_WIDTH;
            graphics.drawLine(x, 0, x, height);
        }
        for (MachineTestTableModel.Row row : model.rows()) {
            int bottom = row.top() + row.height() - 1;
            graphics.drawLine(0, bottom, width, bottom);
            if (row.kind() == MachineTestTableModel.Kind.GROUP && row.top() > 0) {
                graphics.setStroke(DIVIDER);
                graphics.drawLine(0, row.top(), width, row.top());
                graphics.setStroke(LINE);
            }
        }
    }

    private static void tint(Graphics2D graphics, Rectangle cell, Color colour) {
        graphics.setColor(PainterSupport.withAlpha(colour, TINT_ALPHA));
        graphics.fill(cell);
    }

    private static void centered(Graphics2D graphics, String text, Rectangle cell, Color colour, float size,
                                 int style) {
        Font font = graphics.getFont().deriveFont(style, size);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        String fitted = PainterSupport.ellipsized(text, metrics, cell.width - 4);
        int x = cell.x + (cell.width - metrics.stringWidth(fitted)) / 2;
        int baseline = cell.y + (cell.height + metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.setColor(colour);
        graphics.drawString(fitted, x, baseline);
    }
}
