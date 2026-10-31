package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.terminal.CellPosition;
import cl.almejo.vsim.core.terminal.CellStyle;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.paint.TerminalPalettes.AnsiPalette;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The screen of a Terminal (spec §8.1–§8.5): its background, each cell's colours resolved from its references at paint
 * time (bold brightens 0–7, reverse swaps, faint dims, underline), the glyphs in the chosen font fitted to the cell,
 * the cursor, and on the canvas the last character's glow, the old picture fading after a scroll or a clear, and the
 * screen effect (glow around the glyphs, scanlines, vignette). Glyphs stay upright whatever the part's rotation, each
 * turned about its cell's centre. Only the cells the clip shows are drawn, so repainting the cursor's cell is cheap.
 *
 * <p>In black and white (printing, monochrome export) the screen is the page's paper with ink text; a cell with a
 * colour of its own behind it turns to ink with paper text, so coloured text stays readable as a shape.
 */
final class TerminalScreenPainter {

    /** The mockup's last-character glow, {@code rgba(255, 255, 160, …)}. */
    private static final Color LAST_GLOW = new Color(255, 255, 160);
    private static final int OPAQUE = 255;
    /** Faint text is this far from the text colour towards the background. */
    private static final double FAINT_SHARE = 0.5;
    /** The underline sits this far below the baseline. */
    private static final double UNDERLINE_DROP = 1;
    private static final double UNDERLINE_THICKNESS = 1;
    /** The glow around the glyphs: one ring of copies 1 px away, a second one 1.5 px away on the diagonals. */
    private static final double[][] GLOW_RINGS = {{1, 0, -1, 0, 0, 1, 0, -1}, {1.5, 1.5, -1.5, 1.5, 1.5, -1.5, -1.5,
            -1.5}};
    private static final float[] GLOW_RING_ALPHAS = {0.22f, 0.12f};
    /** A dark 1 px line every 3 px. */
    private static final double SCANLINE_PERIOD = 3;
    private static final double SCANLINE_HEIGHT = 1;
    /** The vignette: clear inside 55 % of the way to the corners, half dark at them (the mockup's gradient). */
    private static final float VIGNETTE_CLEAR_SHARE = 0.55f;
    private static final int VIGNETTE_ALPHA = 128;

    private TerminalScreenPainter() {
    }

    /**
     * The colours of one paint.
     *
     * @param text       the default text, also the cursor's colour
     * @param background the screen's background
     * @param palette    what the cells' ANSI colours resolve to
     * @param monochrome black and white: no colour of a cell's own, no effects
     */
    record Colors(Color text, Color background, AnsiPalette palette, boolean monochrome) {
    }

    /** One cell's colours after its style: {@code background} empty for the screen's own. */
    private record CellColors(Color foreground, Optional<Color> background) {
    }

    /** What every glyph of one paint shares. */
    private record GlyphSetting(Font plain, Font bold, double baselineOffset, double advance, int quarterTurns) {
    }

    /**
     * Paints {@code frame} (empty: a screen without a simulation, its cursor at home) in {@code screen}, showing at most
     * {@code rows} × {@code columns} cells of {@code size}.
     */
    static void paint(Graphics2D graphics, Rectangle2D screen, Optional<TerminalFrame> frame, int rows, int columns,
                      TerminalFontSize size, TerminalLook look, Colors colors, TerminalMoment moment) {
        graphics.setColor(colors.background());
        graphics.fill(screen);
        Graphics2D cells = (Graphics2D) graphics.create();
        try {
            cells.clip(screen);
            ScreenGrid grid = ScreenGrid.of(cells, screen, size, frame.map(TerminalFrame::rows).orElse(rows),
                    frame.map(TerminalFrame::columns).orElse(columns), rows, columns);
            GlyphSetting glyphs = glyphSetting(cells, size, look);
            boolean effects = moment.effects() && !colors.monochrome() && look.effect() != TerminalEffect.NONE;
            frame.ifPresent(shown -> paintCells(cells, grid, shown, glyphs, look, colors, moment, effects));
            paintCursor(cells, grid, frame, glyphs, look, colors, moment);
            if (effects) {
                paintScanlines(cells, screen, look.effect(), glyphs.quarterTurns());
                if (look.effect().vignette()) {
                    paintVignette(cells, screen);
                }
            }
        } finally {
            cells.dispose();
        }
    }

    /** The cell at {@code row}, {@code column} of a screen drawn in {@code screen} with cells of {@code size}. */
    static Rectangle2D cellBounds(Rectangle2D screen, TerminalFontSize size, int row, int column) {
        return new Rectangle2D.Double(screen.getX() + column * size.cellWidth(), screen.getY() + row * size.cellHeight(),
                size.cellWidth(), size.cellHeight());
    }

    private static void paintCells(Graphics2D graphics, ScreenGrid grid, TerminalFrame frame, GlyphSetting glyphs,
                                   TerminalLook look, Colors colors, TerminalMoment moment, boolean effects) {
        for (int row = grid.firstRow(); row < grid.endRow(); row++) {
            for (int column = grid.firstColumn(); column < grid.endColumn(); column++) {
                Optional<Color> background = colorsOf(frame.cell(row, column).style(), colors).background();
                if (background.isPresent()) {
                    graphics.setColor(background.get());
                    graphics.fill(grid.cell(row, column));
                }
            }
        }
        if (moment.lastGlowAlpha() > 0) {
            frame.lastWritten().filter(grid::shows).ifPresent(last -> {
                graphics.setColor(PainterSupport.withAlpha(LAST_GLOW, (int) Math.round(moment.lastGlowAlpha()
                        * OPAQUE)));
                graphics.fill(grid.cell(last.row(), last.column()));
            });
        }
        moment.ghost().ifPresent(ghost -> paintGhost(graphics, grid, frame, ghost, glyphs, colors,
                moment.ghostOpacity()));
        List<GlyphRun> runs = GlyphRun.of(graphics, grid, frame, glyphs, position -> true,
                cell -> colorsOf(cell.style(), colors).foreground());
        if (effects) {
            paintGlow(graphics, runs, glyphs, look.effect());
        }
        for (GlyphRun run : runs) {
            run.draw(graphics, run.color(), glyphs, 0, 0);
        }
        paintUnderlines(graphics, grid, frame, glyphs, colors);
    }

    /** The old picture's characters where the new one differs, fading. */
    private static void paintGhost(Graphics2D graphics, ScreenGrid grid, TerminalFrame frame, TerminalFrame ghost,
                                   GlyphSetting glyphs, Colors colors, double opacity) {
        if (ghost.rows() < grid.endRow() || ghost.columns() < grid.endColumn()) {
            return; // a picture from before a resize does not line up with this screen
        }
        int alpha = (int) Math.round(opacity * OPAQUE);
        List<GlyphRun> runs = GlyphRun.of(graphics, grid, ghost, glyphs,
                position -> !ghost.cell(position.row(), position.column())
                        .equals(frame.cell(position.row(), position.column())),
                cell -> PainterSupport.withAlpha(colorsOf(cell.style(), colors).foreground(), alpha));
        for (GlyphRun run : runs) {
            run.draw(graphics, run.color(), glyphs, 0, 0);
        }
    }

    /** Low-alpha copies of every glyph around it: the soft halo of a phosphor screen. */
    private static void paintGlow(Graphics2D graphics, List<GlyphRun> runs, GlyphSetting glyphs,
                                  TerminalEffect effect) {
        for (int ring = 0; ring < effect.glowRings(); ring++) {
            double[] offsets = GLOW_RINGS[ring];
            int alpha = Math.round(GLOW_RING_ALPHAS[ring] * OPAQUE);
            for (GlyphRun run : runs) {
                Color color = PainterSupport.withAlpha(run.color(), alpha);
                for (int offset = 0; offset < offsets.length; offset += 2) {
                    run.draw(graphics, color, glyphs, offsets[offset], offsets[offset + 1]);
                }
            }
        }
    }

    private static void paintUnderlines(Graphics2D graphics, ScreenGrid grid, TerminalFrame frame,
                                        GlyphSetting glyphs, Colors colors) {
        for (int row = grid.firstRow(); row < grid.endRow(); row++) {
            for (int column = grid.firstColumn(); column < grid.endColumn(); column++) {
                TerminalCell cell = frame.cell(row, column);
                if (cell.style().underline()) {
                    Rectangle2D bounds = grid.cell(row, column);
                    graphics.setColor(colorsOf(cell.style(), colors).foreground());
                    graphics.fill(new Rectangle2D.Double(bounds.getX(), bounds.getCenterY() + glyphs.baselineOffset()
                            + UNDERLINE_DROP, bounds.getWidth(), UNDERLINE_THICKNESS));
                }
            }
        }
    }

    /** The cursor at the frame's cursor (at home without a frame), unless hidden or in a blink's hidden half. */
    private static void paintCursor(Graphics2D graphics, ScreenGrid grid, Optional<TerminalFrame> frame,
                                    GlyphSetting glyphs, TerminalLook look, Colors colors, TerminalMoment moment) {
        boolean visible = frame.map(TerminalFrame::cursorVisible).orElse(true);
        if (!visible || !moment.cursorOn() || look.cursor() == TerminalCursorShape.NONE) {
            return;
        }
        int row = frame.map(TerminalFrame::cursorRow).orElse(0);
        int column = frame.map(TerminalFrame::cursorColumn).orElse(0);
        if (row >= grid.rows() || column >= grid.columns()) {
            return;
        }
        Rectangle2D cell = grid.cell(row, column);
        TerminalCursorPainter.paint(graphics, look.cursor(), cell, colors.text(), moment.automanSeconds());
        if (look.cursor() == TerminalCursorShape.BLOCK && frame.isPresent()) {
            TerminalCell under = frame.get().cell(row, column);
            if (under.codePoint() != ' ') {
                Font font = under.style().bold() ? glyphs.bold() : glyphs.plain();
                GlyphVector vector = font.createGlyphVector(graphics.getFontRenderContext(),
                        Character.toString(under.codePoint()));
                new GlyphRun(vector, cell, colors.background()).draw(graphics, colors.background(), glyphs, 0, 0);
            }
        }
    }

    private static void paintScanlines(Graphics2D graphics, Rectangle2D screen, TerminalEffect effect,
                                       int quarterTurns) {
        graphics.setColor(new Color(0, 0, 0, Math.round(effect.scanlineAlpha() * OPAQUE)));
        // turned sideways the screen's rows run along local y: the lines stay horizontal on screen
        boolean sideways = Math.abs(quarterTurns) % 2 == 1;
        double length = sideways ? screen.getWidth() : screen.getHeight();
        for (double offset = 0; offset < length; offset += SCANLINE_PERIOD) {
            graphics.fill(sideways
                    ? new Rectangle2D.Double(screen.getX() + offset, screen.getY(), SCANLINE_HEIGHT, screen.getHeight())
                    : new Rectangle2D.Double(screen.getX(), screen.getY() + offset, screen.getWidth(), SCANLINE_HEIGHT));
        }
    }

    private static void paintVignette(Graphics2D graphics, Rectangle2D screen) {
        double halfWidth = screen.getWidth() / 2;
        double halfHeight = screen.getHeight() / 2;
        Point2D centre = new Point2D.Double(screen.getCenterX(), screen.getCenterY());
        // a circle of radius 1 stretched to the ellipse through the corners
        AffineTransform stretch = new AffineTransform();
        stretch.translate(centre.getX(), centre.getY());
        stretch.scale(halfWidth * Math.sqrt(2), halfHeight * Math.sqrt(2));
        graphics.setPaint(new RadialGradientPaint(new Point2D.Double(), 1f, new Point2D.Double(),
                new float[] {0f, VIGNETTE_CLEAR_SHARE, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, VIGNETTE_ALPHA)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, stretch));
        graphics.fill(screen);
    }

    private static GlyphSetting glyphSetting(Graphics2D graphics, TerminalFontSize size, TerminalLook look) {
        FontRenderContext context = graphics.getFontRenderContext();
        Font plain = TerminalFonts.fitted(look.font(), false, size.cellWidth(), size.cellHeight(), context);
        Font bold = TerminalFonts.fitted(look.font(), true, size.cellWidth(), size.cellHeight(), context);
        LineMetrics metrics = plain.getLineMetrics(TerminalFonts.TALL_SAMPLE, context);
        double advance = plain.getStringBounds(TerminalFonts.WIDEST_LETTER, context).getWidth();
        return new GlyphSetting(plain, bold, (metrics.getAscent() - metrics.getDescent()) / 2, advance,
                PainterSupport.quarterTurns(graphics));
    }

    /**
     * A cell's colours: its own or the screen's, bold brightening 0–7, reverse swapping them, faint dimming the text
     * towards the background. In black and white the text is ink, and a cell with a background of its own (or reverse)
     * is ink with paper text.
     */
    private static CellColors colorsOf(CellStyle style, Colors colors) {
        if (colors.monochrome()) {
            boolean inverted = style.reverse() || style.background().isPresent();
            return inverted ? new CellColors(colors.background(), Optional.of(colors.text()))
                    : new CellColors(colors.text(), Optional.empty());
        }
        Color foreground = style.foreground()
                .map(reference -> TerminalPalettes.resolve(reference, colors.palette(), style.bold()))
                .orElse(colors.text());
        Optional<Color> background = style.background()
                .map(reference -> TerminalPalettes.resolve(reference, colors.palette(), false));
        if (style.reverse()) {
            Color swapped = background.orElse(colors.background());
            background = Optional.of(foreground);
            foreground = swapped;
        }
        if (style.faint()) {
            foreground = blend(foreground, background.orElse(colors.background()), FAINT_SHARE);
        }
        return new CellColors(foreground, background);
    }

    private static Color blend(Color from, Color towards, double share) {
        return new Color(mix(from.getRed(), towards.getRed(), share), mix(from.getGreen(), towards.getGreen(), share),
                mix(from.getBlue(), towards.getBlue(), share));
    }

    private static int mix(int from, int towards, double share) {
        return (int) Math.round(from + (towards - from) * share);
    }

    /**
     * Neighbouring characters of one row with the same colour and weight, drawn with one glyph vector whose glyphs sit
     * on their cells: far cheaper than a call per character, and the glow draws each run again a few times. A turned
     * part keeps a run per character, each turned upright about its own cell.
     */
    private record GlyphRun(GlyphVector vector, Rectangle2D firstCell, Color color) {

        /**
         * The runs of the cells of {@code frame} the grid shows that are not blank and that {@code wanted} keeps, each
         * in the colour {@code colorOf} gives.
         */
        static List<GlyphRun> of(Graphics2D graphics, ScreenGrid grid, TerminalFrame frame, GlyphSetting glyphs,
                                 Predicate<CellPosition> wanted, Function<TerminalCell, Color> colorOf) {
            RunBuilder builder = new RunBuilder(graphics.getFontRenderContext(), grid, glyphs);
            for (int row = grid.firstRow(); row < grid.endRow(); row++) {
                for (int column = grid.firstColumn(); column < grid.endColumn(); column++) {
                    TerminalCell cell = frame.cell(row, column);
                    if (cell.codePoint() == ' ' || !wanted.test(new CellPosition(row, column))) {
                        builder.end();
                    } else {
                        builder.add(row, column, cell, colorOf.apply(cell));
                    }
                }
                builder.end();
            }
            return builder.runs;
        }

        /** Draws the run in {@code drawColor}, moved by (offsetX, offsetY) on screen. */
        void draw(Graphics2D graphics, Color drawColor, GlyphSetting glyphs, double offsetX, double offsetY) {
            graphics.setColor(drawColor);
            PainterSupport.uprightGlyphs(graphics, vector,
                    (float) (firstCell.getCenterX() - glyphs.advance() / 2 + offsetX),
                    (float) (firstCell.getCenterY() + glyphs.baselineOffset() + offsetY), firstCell.getCenterX(),
                    firstCell.getCenterY());
        }
    }

    /** Gathers the cells of a row into {@link GlyphRun}s. */
    private static final class RunBuilder {
        private final FontRenderContext context;
        private final ScreenGrid grid;
        private final GlyphSetting glyphs;
        private final List<GlyphRun> runs = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private int row;
        private int firstColumn = -1;
        private Color color;
        private boolean bold;

        RunBuilder(FontRenderContext context, ScreenGrid grid, GlyphSetting glyphs) {
            this.context = context;
            this.grid = grid;
            this.glyphs = glyphs;
        }

        /** Adds {@code cell} to the run going on, or ends it and starts another when the cell does not fit it. */
        void add(int cellRow, int column, TerminalCell cell, Color cellColor) {
            boolean fits = firstColumn >= 0 && glyphs.quarterTurns() == 0 && cellColor.equals(color)
                    && cell.style().bold() == bold && Character.isBmpCodePoint(cell.codePoint());
            if (!fits) {
                end();
                row = cellRow;
                firstColumn = column;
                color = cellColor;
                bold = cell.style().bold();
            }
            text.appendCodePoint(cell.codePoint());
        }

        /** Ends the run going on, if any: its glyphs placed on their cells. */
        void end() {
            if (firstColumn < 0) {
                return;
            }
            Font font = bold ? glyphs.bold() : glyphs.plain();
            GlyphVector vector = font.createGlyphVector(context, text.toString());
            if (vector.getNumGlyphs() == text.length()) {
                for (int index = 1; index < text.length(); index++) {
                    vector.setGlyphPosition(index, new Point2D.Double(index * grid.size().cellWidth(), 0));
                }
            }
            runs.add(new GlyphRun(vector, grid.cell(row, firstColumn), color));
            firstColumn = -1;
            text.setLength(0);
        }
    }

    /**
     * The cells of one paint: the shown ones are those the part and the frame both have (a frame may be one step
     * behind a resize), and of them the ones the clip touches.
     */
    private record ScreenGrid(Rectangle2D screen, TerminalFontSize size, int rows, int columns, int firstRow,
                              int endRow, int firstColumn, int endColumn) {

        static ScreenGrid of(Graphics2D graphics, Rectangle2D screen, TerminalFontSize size, int frameRows,
                             int frameColumns, int partRows, int partColumns) {
            int rows = Math.min(frameRows, partRows);
            int columns = Math.min(frameColumns, partColumns);
            Rectangle2D clip = graphics.getClipBounds();
            if (clip == null) {
                return new ScreenGrid(screen, size, rows, columns, 0, rows, 0, columns);
            }
            int firstColumn = clamp((int) Math.floor((clip.getMinX() - screen.getX()) / size.cellWidth()), columns);
            int endColumn = clamp((int) Math.ceil((clip.getMaxX() - screen.getX()) / size.cellWidth()), columns);
            int firstRow = clamp((int) Math.floor((clip.getMinY() - screen.getY()) / size.cellHeight()), rows);
            int endRow = clamp((int) Math.ceil((clip.getMaxY() - screen.getY()) / size.cellHeight()), rows);
            return new ScreenGrid(screen, size, rows, columns, firstRow, endRow, firstColumn, endColumn);
        }

        private static int clamp(int value, int limit) {
            return Math.max(0, Math.min(limit, value));
        }

        Rectangle2D cell(int row, int column) {
            return cellBounds(screen, size, row, column);
        }

        boolean shows(CellPosition position) {
            return position.row() >= firstRow && position.row() < endRow && position.column() >= firstColumn
                    && position.column() < endColumn;
        }
    }
}
