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
import com.formdev.flatlaf.util.UIScale;

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
 * wrong gets the fail tint too. Every colour is read from the scheme given now (never cached). Texts keep at least
 * {@link ReadableColors#MINIMUM_TEXT_CONTRAST}:1 on what they are drawn over, tints included (the tint is never
 * changed, the text colour is: {@link #readableOn}); sizes come from the table's {@link MachineTestLayout} and the
 * letter's own size, never from pixel counts.
 */
final class MachineTestCellPainter {

    /** 18 % of 255: the cells' tint. */
    static final int TINT_ALPHA = 46;
    private static final int CHOSEN_COLUMN_ALPHA = 55;
    private static final int BLOCK_ALPHA = 70;
    /** The sizes of the letters, as a share of the table's own letter. */
    private static final float VALUE_SHARE = 1f;
    private static final float VERDICT_SHARE = 1.15f;
    /** «·» is a small glyph: drawn larger and bold so «no importa» is seen. */
    private static final float DOT_SHARE = 1.4f;
    /** How much of the text colour is given up per step towards black or white when a text does not read. */
    private static final double READABLE_STEP = 0.04;
    private static final BasicStroke LINE = new BasicStroke(1f);

    /** The cells a key changes: editable rows {@code firstRow..lastRow} (row indices) and a column range. */
    record Block(int firstRow, int lastRow, int firstColumn, int lastColumn) {

        boolean contains(int row, int column) {
            return row >= firstRow && row <= lastRow && column >= firstColumn && column <= lastColumn;
        }
    }

    private MachineTestCellPainter() {
    }

    /** The ✓ colour: the scheme's own green (the one the user chose, review finding 7: never a second copy here). */
    static Color pass(ColorScheme scheme) {
        return scheme.testPass();
    }

    /** The ✗ colour: the scheme's error red, as a conflict is drawn. */
    static Color fail(ColorScheme scheme) {
        return scheme.testFail();
    }

    /** The ✓ as a text over its tint: the scheme's green, darkened (or lightened) only as far as it must read. */
    static Color passText(ColorScheme scheme) {
        return readableOn(pass(scheme), tinted(pass(scheme), scheme));
    }

    /** The ✗ as a text over its tint, as {@link #passText}. */
    static Color failText(ColorScheme scheme) {
        return readableOn(fail(scheme), tinted(fail(scheme), scheme));
    }

    /**
     * «no importa», the ghost column and the other secondary texts: the theme's muted grey
     * ({@link ReadableColors#mutedText()}), pushed further only when the scheme's diagram background is one it does not
     * read on.
     */
    static Color quiet(ColorScheme scheme) {
        return readableOn(ReadableColors.mutedText(), scheme.diagramBackground());
    }

    /** {@code colour} over the diagram background as the tint of a ✓ or ✗ cell shows. */
    private static Color tinted(Color colour, ColorScheme scheme) {
        return ReadableColors.over(PainterSupport.withAlpha(colour, TINT_ALPHA), scheme.diagramBackground());
    }

    /**
     * {@code text} when it reads on {@code background} (at least 4.5:1); otherwise {@code text} mixed with black over a
     * light background, or with white over a dark one, just enough to read.
     */
    static Color readableOn(Color text, Color background) {
        if (ReadableColors.contrast(text, background) >= ReadableColors.MINIMUM_TEXT_CONTRAST) {
            return text;
        }
        Color target = ReadableColors.perceivedBrightness(background) < 0.5 ? Color.WHITE : Color.BLACK;
        Color mixed = text;
        for (double share = READABLE_STEP; share <= 1 + 1e-9; share += READABLE_STEP) {
            mixed = new Color((int) Math.round(text.getRed() + (target.getRed() - text.getRed()) * share),
                    (int) Math.round(text.getGreen() + (target.getGreen() - text.getGreen()) * share),
                    (int) Math.round(text.getBlue() + (target.getBlue() - text.getBlue()) * share));
            if (ReadableColors.contrast(mixed, background) >= ReadableColors.MINIMUM_TEXT_CONTRAST) {
                return mixed;
            }
        }
        return mixed;
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
        Font letter = graphics.getFont(); // the table's own: every size below is a share of it
        int columns = model.columnCount();
        int columnWidth = model.layout().columnWidth();
        graphics.setColor(PainterSupport.withAlpha(scheme.selection(), CHOSEN_COLUMN_ALPHA));
        graphics.fillRect(model.chosenColumn() * columnWidth, 0, columnWidth, model.height());
        List<MachineTestTableModel.Row> rows = model.rows();
        for (int index = 0; index < rows.size(); index++) {
            paintRow(graphics, model, scheme, index, rows.get(index), letter);
        }
        paintGhost(graphics, model, scheme, letter);
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
            graphics.setStroke(new BasicStroke(UIScale.scale(2f)));
            graphics.drawRect(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2);
        }
    }

    private static void paintRow(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme, int index,
                                 MachineTestTableModel.Row row, Font letter) {
        int columns = model.columnCount();
        switch (row.kind()) {
            case GROUP, BUS -> {
                // the names column writes them; the grid only draws their divider (paintLines)
            }
            case INPUT -> {
                List<Optional<Value>> values = new ArrayList<>();
                for (int column = 0; column < columns; column++) {
                    values.add(Optional.of(Value.of(model.input(column, row.pinId().orElseThrow()))));
                }
                paintSteps(graphics, model, values, row, scheme, null, null);
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
                    paintSteps(graphics, model, values, row, scheme, null, null);
                }
            }
            case EXPECTED -> {
                for (int column = 0; column < columns; column++) {
                    LogicValue expected = model.expected(column, row.pinId().orElseThrow());
                    boolean nothing = expected == LogicValue.DONT_CARE;
                    centered(graphics, symbol(expected), model.cell(index, column),
                            nothing ? quiet(scheme) : scheme.diagramTrace(), letter,
                            nothing ? DOT_SHARE : VALUE_SHARE, nothing ? Font.BOLD : Font.PLAIN);
                }
            }
            case VERDICT -> {
                for (int column = 0; column < columns; column++) {
                    Optional<MachineTestRun.Verdict> verdict = model.columnResult(column)
                            .map(MachineTestRun.ColumnResult::verdict);
                    Rectangle cell = model.cell(index, column);
                    if (verdict.filter(MachineTestRun.Verdict.PASS::equals).isPresent()) {
                        tint(graphics, cell, pass(scheme));
                        centered(graphics, "✓", cell, passText(scheme), letter, VERDICT_SHARE, Font.BOLD);
                    } else if (verdict.filter(MachineTestRun.Verdict.FAIL::equals).isPresent()) {
                        tint(graphics, cell, fail(scheme));
                        centered(graphics, "✗", cell, failText(scheme), letter, VERDICT_SHARE, Font.BOLD);
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
        paintSteps(graphics, model, values, row, scheme, names, colours);
    }

    /**
     * One row of the strip's own drawing ({@link WaveformPainter#paintSteps}, made for
     * {@link WaveformPainter#rowHeight()} rows), scaled to the row this table gives it when the letter asks for a
     * taller one, so the drawing keeps its proportions.
     */
    private static void paintSteps(Graphics2D graphics, MachineTestTableModel model, List<Optional<Value>> values,
                                   MachineTestTableModel.Row row, ColorScheme scheme, LongFunction<String> names,
                                   LongFunction<Optional<StateColor>> colours) {
        MachineTestLayout layout = model.layout();
        double scale = layout.waveScale();
        Graphics2D wave = (Graphics2D) graphics.create();
        try {
            wave.translate(0, row.top());
            wave.scale(scale, scale);
            WaveformPainter.paintSteps(wave, values, 0, layout.columnWidth() / scale, 0, scheme, names, colours);
        } finally {
            wave.dispose();
        }
    }

    /** The dimmed ghost column after the last edge: «→ S2» in the state row. */
    private static void paintGhost(Graphics2D graphics, MachineTestTableModel model, ColorScheme scheme,
                                   Font letter) {
        Optional<String> text = model.ghostText();
        if (text.isEmpty()) {
            return;
        }
        List<MachineTestTableModel.Row> rows = model.rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).kind() == MachineTestTableModel.Kind.STATE) {
                Rectangle cell = model.cell(index, model.columnCount());
                centered(graphics, text.get(), cell, quiet(scheme), letter, VALUE_SHARE, Font.PLAIN);
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
            int x = column * model.layout().columnWidth();
            graphics.drawLine(x, 0, x, height);
        }
        for (MachineTestTableModel.Row row : model.rows()) {
            int bottom = row.top() + row.height() - 1;
            graphics.drawLine(0, bottom, width, bottom);
            if (row.kind() == MachineTestTableModel.Kind.GROUP && row.top() > 0) {
                graphics.setStroke(new BasicStroke(UIScale.scale(2f)));
                graphics.drawLine(0, row.top(), width, row.top());
                graphics.setStroke(LINE);
            }
        }
    }

    private static void tint(Graphics2D graphics, Rectangle cell, Color colour) {
        graphics.setColor(PainterSupport.withAlpha(colour, TINT_ALPHA));
        graphics.fill(cell);
    }

    /** {@code text} centered in {@code cell}, in {@code letter}'s family at {@code share} of its size. */
    private static void centered(Graphics2D graphics, String text, Rectangle cell, Color colour, Font letter,
                                 float share, int style) {
        Font font = letter.deriveFont(style, letter.getSize2D() * share);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        String fitted = PainterSupport.ellipsized(text, metrics, cell.width - 2 * UIScale.scale(2));
        int x = cell.x + (cell.width - metrics.stringWidth(fitted)) / 2;
        int baseline = cell.y + (cell.height + metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.setColor(colour);
        graphics.drawString(fitted, x, baseline);
    }
}
