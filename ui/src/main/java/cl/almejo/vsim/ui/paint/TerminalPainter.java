package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.core.terminal.TerminalGeometry;
import cl.almejo.vsim.ui.i18n.Messages;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * «Terminal»: a title bar (the part's name on the left, a chip with its size on the right) over a dark screen, and the
 * pin stubs with their names. While simulating the screen shows the frame of {@link VisibleState.TerminalState} (its
 * characters and a still block cursor); without a simulation (palette, ghost) a prompt and a block cursor after it. The screen keeps its own
 * colours in every theme; border and title bar come from the scheme at paint time.
 */
public final class TerminalPainter implements ComponentPainter {

    /** The screen's colours, fixed by design (they do not follow the canvas theme). */
    static final Color SCREEN_BACKGROUND = new Color(0x0B0F0C);
    static final Color SCREEN_TEXT = new Color(0xCFE8D4);

    /** What the first row shows: the prompt, then the cursor's cell. */
    static final String PROMPT = ">";
    static final int CURSOR_COLUMN = 2;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double CORNER = 4;
    private static final double TITLE_HEIGHT = TerminalGeometry.TITLE_BAR_UNITS * UNIT;
    private static final double MARGIN = TerminalGeometry.MARGIN_UNITS * UNIT;
    private static final double TITLE_PADDING = 6;
    private static final float TITLE_TEXT_SIZE = 8f;
    private static final float PIN_NAME_SIZE = 5f;
    private static final double PIN_NAME_INSET = 4.5;
    private static final double CELL_WIDTH = TerminalFontSize.NORMAL.cellWidth();
    private static final double CELL_HEIGHT = TerminalFontSize.NORMAL.cellHeight();
    private static final Font SCREEN_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 1);
    private static final double FITTING_SIZE = 100;
    /** The glyph is fitted so its advance is the cell's width, and never taller than this share of the cell. */
    private static final double HEIGHT_SHARE = 0.85;
    private static final BasicStroke DIVIDER = new BasicStroke(1f);

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        Shape outline = new RoundRectangle2D.Double(0, 0, width, height, CORNER, CORNER);
        PainterSupport.body(graphics, outline, scheme);
        paintTitleBar(graphics, outline, width, resolved, type, scheme);
        Rectangle2D screen = new Rectangle2D.Double(MARGIN, TITLE_HEIGHT + MARGIN, width - 2 * MARGIN,
                height - TITLE_HEIGHT - 2 * MARGIN);
        if (state instanceof VisibleState.TerminalState terminal) {
            paintFrame(graphics, screen, terminal.frame(), resolved);
        } else {
            paintScreen(graphics, screen);
        }
        PainterSupport.pinDots(graphics, type, scheme);
        paintPinNames(graphics, type, scheme);
    }

    /** The chip's text, «24×80». */
    static String chip(Map<String, Object> resolved) {
        return resolved.get(TerminalTypes.ROWS) + "×" + resolved.get(TerminalTypes.COLUMNS);
    }

    private static void paintTitleBar(Graphics2D graphics, Shape outline, double width,
                                      Map<String, Object> resolved, ComponentType type, ColorScheme scheme) {
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(outline);
            clipped.setColor(scheme.componentShade());
            clipped.fill(new Rectangle2D.Double(0, 0, width, TITLE_HEIGHT));
            clipped.setColor(scheme.componentStroke());
            clipped.setStroke(DIVIDER);
            clipped.draw(new Line2D.Double(0, TITLE_HEIGHT, width, TITLE_HEIGHT));
        } finally {
            clipped.dispose();
        }
        if (PainterSupport.turnedSideways(graphics)) {
            // upright text cannot fit the title bar turned into a narrow vertical strip
            return;
        }
        double middle = TITLE_HEIGHT / 2;
        // turned upside down the part's left is the screen's right: the texts keep their side of the bar
        boolean upsideDown = Math.abs(PainterSupport.quarterTurns(graphics)) % 4 == 2;
        PainterSupport.uprightText(graphics, Messages.componentName(type.id()), TITLE_PADDING, middle,
                TITLE_TEXT_SIZE, scheme.text(), upsideDown ? PainterSupport.ALIGN_RIGHT : PainterSupport.ALIGN_LEFT);
        PainterSupport.uprightText(graphics, chip(resolved), width - TITLE_PADDING, middle, TITLE_TEXT_SIZE,
                scheme.text(), upsideDown ? PainterSupport.ALIGN_LEFT : PainterSupport.ALIGN_RIGHT);
    }

    /** The dark screen with the prompt in its first cell and a block cursor in the third. */
    private static void paintScreen(Graphics2D graphics, Rectangle2D screen) {
        graphics.setColor(SCREEN_BACKGROUND);
        graphics.fill(screen);
        double cellTop = screen.getY();
        double promptCentreX = screen.getX() + CELL_WIDTH / 2;
        PainterSupport.uprightText(graphics, PROMPT, promptCentreX, cellTop + CELL_HEIGHT / 2, fittedSize(),
                SCREEN_TEXT, PainterSupport.ALIGN_CENTER, SCREEN_FONT);
        graphics.setColor(SCREEN_TEXT);
        graphics.fill(new Rectangle2D.Double(screen.getX() + CURSOR_COLUMN * CELL_WIDTH, cellTop, CELL_WIDTH,
                CELL_HEIGHT));
    }

    /**
     * A simulated screen: every written cell's character in the chosen font, fitted to the cell of the chosen size and
     * kept upright like every text of a part (each glyph turns about its cell's centre), in
     * the screen's default text colour (styles and colours come later), and a still block cursor at the frame's cursor
     * unless the screen hid it. Only the cells the part and the frame both have are drawn (a frame may be one step
     * behind a resize).
     */
    private static void paintFrame(Graphics2D graphics, Rectangle2D screen, TerminalFrame frame,
                                   Map<String, Object> resolved) {
        graphics.setColor(SCREEN_BACKGROUND);
        graphics.fill(screen);
        TerminalFontSize fontSize = TerminalTypes.fontSize(resolved);
        double cellWidth = fontSize.cellWidth();
        double cellHeight = fontSize.cellHeight();
        int rows = Math.min(frame.rows(), (Integer) resolved.get(TerminalTypes.ROWS));
        int columns = Math.min(frame.columns(), (Integer) resolved.get(TerminalTypes.COLUMNS));
        Font font = TerminalFonts.fitted(fontFamily(resolved), false, cellWidth, cellHeight,
                graphics.getFontRenderContext());
        float size = font.getSize2D();
        for (int row = 0; row < rows; row++) {
            double middle = screen.getY() + (row + 0.5) * cellHeight;
            for (int column = 0; column < columns; column++) {
                int codePoint = frame.cell(row, column).codePoint();
                if (codePoint != ' ') {
                    PainterSupport.uprightText(graphics, Character.toString(codePoint),
                            screen.getX() + (column + 0.5) * cellWidth, middle, size, SCREEN_TEXT,
                            PainterSupport.ALIGN_CENTER, font);
                }
            }
        }
        if (frame.cursorVisible() && frame.cursorRow() < rows && frame.cursorColumn() < columns) {
            graphics.setColor(SCREEN_TEXT);
            graphics.fill(new Rectangle2D.Double(screen.getX() + frame.cursorColumn() * cellWidth,
                    screen.getY() + frame.cursorRow() * cellHeight, cellWidth, cellHeight));
        }
    }

    /** The family «Fuente» chooses. */
    static TerminalFontFamily fontFamily(Map<String, Object> resolved) {
        return switch (String.valueOf(resolved.get(TerminalTypes.FONT))) {
            case TerminalTypes.FONT_VT323 -> TerminalFontFamily.VT323;
            case TerminalTypes.FONT_SYSTEM -> TerminalFontFamily.SYSTEM;
            default -> TerminalFontFamily.PLEX_MONO;
        };
    }

    /** The font size at which a monospaced glyph is as wide as a cell (and fits its height). */
    static float fittedSize() {
        double advance = SCREEN_FONT.deriveFont((float) FITTING_SIZE)
                .getStringBounds("M", new FontRenderContext(null, true, true)).getWidth() / FITTING_SIZE;
        return (float) Math.min(CELL_WIDTH / advance, CELL_HEIGHT * HEIGHT_SHARE);
    }

    /** Each pin's name inside the part, next to its stub: D beside the left edge, the others above the bottom edge. */
    private static void paintPinNames(Graphics2D graphics, ComponentType type, ColorScheme scheme) {
        for (var pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            boolean onLeftEdge = pin.position().x() == 0;
            if (onLeftEdge) {
                PainterSupport.uprightText(graphics, pin.name(), pinX + PIN_NAME_INSET, pinY, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_CENTER);
            } else {
                PainterSupport.uprightText(graphics, pin.name(), pinX, pinY - PIN_NAME_INSET, PIN_NAME_SIZE,
                        scheme.text(), PainterSupport.ALIGN_CENTER);
            }
        }
    }
}
