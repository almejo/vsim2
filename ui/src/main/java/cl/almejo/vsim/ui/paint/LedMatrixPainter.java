package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.LedMatrixTypes;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The Matriz LED: a dark panel of one-bit LEDs in one of four looks («Aspecto»): round lenses, flat square pixels,
 * the packed dots of an 8 x 8 module (with the seam of each module) and the spaced, glowing dots of a sign. Each LED
 * is {@code UNIT} pixels apart. A bit that is 1 is lit, 0 is off; Z or a conflicted row is off with a ring in the
 * scheme's unknown colour (the matrix never hides an input that is not driven). On paper (black and white) a lit LED
 * is black and an off one an empty outline.
 *
 * <p>{@link #paintWithLevels} takes how lit each LED is (0 to 1) for «Brillo que se apaga»; {@link #paint} shows every
 * known 1 fully lit. The painter reads the scheme on every call and keeps nothing.
 */
public final class LedMatrixPainter implements ComponentPainter {

    private static final double PITCH = UNIT;
    private static final double FRAME = PITCH / 2;
    private static final double BODY_CORNER = 6;
    private static final double MODULE_CORNER = 2;
    private static final double SEAM_INSET = 2;
    private static final int MODULE_SIZE = 8;

    private static final double ROUND_RADIUS = 3.0;
    private static final double MODULE_RADIUS = 3.2;
    private static final double SIGN_RADIUS = 2.2;
    private static final double PIXEL_SIDE = 7;
    private static final double GLOW_REACH = 2.2;
    private static final int GLOW_ALPHA = 80;
    private static final double GLINT_SHIFT = 0.3;
    private static final double GLINT_SHARE = 0.32;
    private static final int GLINT_ALPHA = 150;
    private static final double SIGN_UNLIT_SHARE = 0.55;
    private static final double RING_PADDING = 0.6;
    private static final BasicStroke RING_STROKE = new BasicStroke(0.8f);
    private static final BasicStroke SEAM_STROKE = new BasicStroke(0.8f);
    private static final Color SEAM_COLOR = new Color(0x2A2B30);
    private static final Color MONOCHROME_OFF_RING = new Color(0x909090);

    /** The dark panel behind the LEDs of each style (an LED panel is dark on any board). */
    private static final Map<String, Color> BODY_COLORS = Map.of("round", new Color(0x16171B),
            "pixel", new Color(0x1B1C20), "module", new Color(0x0B0B0D), "sign", new Color(0x101113));

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        paintWithLevels(graphics, type, parameters, state, scheme, null);
    }

    /**
     * Paints the matrix with {@code levels[row][column]} (0 off to 1 fully lit) for the LEDs that are 1; {@code null}
     * lights every 1 fully. A {@code levels} table of another size than the matrix is ignored.
     */
    public void paintWithLevels(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                                VisibleState state, ColorScheme scheme, double[][] levels) {
        Look look = Look.of(parameters);
        List<Value> shown = shownRows(state, look);
        double[][] usable = levels != null && levels.length == look.rows && levels[0].length == look.columns
                ? levels : null;
        double bodyHeight = type.size().height() * PITCH;
        double top = (bodyHeight - look.rows * PITCH) / 2;
        for (PinSpec pin : type.pins()) {
            double y = pin.position().y() * PITCH;
            PainterSupport.pinLead(graphics, pin, 0, y, UNIT, y, scheme);
        }
        paintBody(graphics, look, scheme, type.size().width() * PITCH, bodyHeight);
        for (int row = 0; row < look.rows; row++) {
            for (int column = 0; column < look.columns; column++) {
                double centerX = UNIT + FRAME + (column + 0.5) * PITCH;
                double centerY = top + (row + 0.5) * PITCH;
                Cell cell = cellOf(shown, row, column, usable);
                paintLed(graphics, look, scheme, cell, centerX, centerY);
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** One LED as the state gives it: how lit it is and whether its bit is unknown. */
    private record Cell(double level, boolean unknown) {
    }

    /** The settings read from the parameters (a missing one is its default, so the palette icon paints too). */
    private record Look(int rows, int columns, String style, String color, boolean showUnlit) {

        static Look of(Map<String, Object> parameters) {
            String style = String.valueOf(parameters.getOrDefault(LedMatrixTypes.STYLE, LedMatrixTypes.STYLES.get(0)));
            return new Look(integer(parameters, LedMatrixTypes.ROWS), integer(parameters, LedMatrixTypes.COLUMNS),
                    BODY_COLORS.containsKey(style) ? style : LedMatrixTypes.STYLES.get(0),
                    String.valueOf(parameters.getOrDefault(LedMatrixTypes.COLOR, "red")),
                    !Boolean.FALSE.equals(parameters.get(LedMatrixTypes.SHOW_UNLIT)));
        }

        private static int integer(Map<String, Object> parameters, String name) {
            return parameters.get(name) instanceof Integer number ? number : LedMatrixTypes.DEFAULT_SIZE;
        }
    }

    /** The rows to show; none when the state is not a matrix of this size (a snapshot older than the parameters). */
    private static List<Value> shownRows(VisibleState state, Look look) {
        if (state instanceof VisibleState.LedMatrixState matrix && matrix.rows().size() == look.rows
                && matrix.rows().stream().allMatch(row -> row.width() == look.columns)) {
            return matrix.rows();
        }
        return List.of();
    }

    private static Cell cellOf(List<Value> shown, int row, int column, double[][] levels) {
        if (shown.isEmpty()) {
            return new Cell(0, false);
        }
        Value value = shown.get(row);
        int bit = value.width() - 1 - column;
        if (value.conflict() || value.bit(bit) == BitState.HIGH_IMPEDANCE) {
            return new Cell(0, true);
        }
        boolean lit = value.bit(bit) == BitState.ONE;
        double level = levels == null ? (lit ? 1 : 0) : Math.max(levels[row][column], lit ? 1 : 0);
        return new Cell(level, false);
    }

    private static void paintBody(Graphics2D graphics, Look look, ColorScheme scheme, double width, double height) {
        boolean paper = MonochromeScheme.isMonochrome(scheme);
        Color fill = paper ? Color.WHITE : BODY_COLORS.get(look.style);
        double corner = "module".equals(look.style) ? MODULE_CORNER : BODY_CORNER;
        Shape body = new RoundRectangle2D.Double(UNIT, 0, width - UNIT, height, corner, corner);
        PainterSupport.body(graphics, body, scheme, fill);
        if ("module".equals(look.style) && !paper) {
            paintSeams(graphics, look, height);
        }
    }

    /** The line between two 8 x 8 modules, inside the panel, both ways. */
    private static void paintSeams(Graphics2D graphics, Look look, double height) {
        graphics.setColor(SEAM_COLOR);
        graphics.setStroke(SEAM_STROKE);
        double top = (height - look.rows * PITCH) / 2;
        for (int column = MODULE_SIZE; column < look.columns; column += MODULE_SIZE) {
            double x = UNIT + FRAME + column * PITCH;
            graphics.draw(new Line2D.Double(x, SEAM_INSET, x, height - SEAM_INSET));
        }
        for (int row = MODULE_SIZE; row < look.rows; row += MODULE_SIZE) {
            double y = top + row * PITCH;
            graphics.draw(new Line2D.Double(UNIT + SEAM_INSET, y, UNIT + 2 * FRAME + look.columns * PITCH - SEAM_INSET,
                    y));
        }
    }

    private static void paintLed(Graphics2D graphics, Look look, ColorScheme scheme, Cell cell, double centerX,
                                 double centerY) {
        boolean lit = cell.level > 0;
        if (!lit && !look.showUnlit && !cell.unknown) {
            return;
        }
        Shape shape = shapeOf(look.style, centerX, centerY);
        if (MonochromeScheme.isMonochrome(scheme)) {
            paintOnPaper(graphics, shape, lit);
        } else {
            paintOnScreen(graphics, look, shape, cell, centerX, centerY);
        }
        if (cell.unknown) {
            paintUnknownRing(graphics, shape, scheme);
        }
    }

    private static Shape shapeOf(String style, double centerX, double centerY) {
        if ("pixel".equals(style)) {
            return new Rectangle2D.Double(centerX - PIXEL_SIDE / 2, centerY - PIXEL_SIDE / 2, PIXEL_SIDE, PIXEL_SIDE);
        }
        double radius = switch (style) {
            case "module" -> MODULE_RADIUS;
            case "sign" -> SIGN_RADIUS;
            default -> ROUND_RADIUS;
        };
        return new Ellipse2D.Double(centerX - radius, centerY - radius, 2 * radius, 2 * radius);
    }

    private static void paintOnPaper(Graphics2D graphics, Shape shape, boolean lit) {
        graphics.setColor(lit ? MonochromeScheme.BLACK : Color.WHITE);
        graphics.fill(shape);
        graphics.setColor(lit ? MonochromeScheme.BLACK : MONOCHROME_OFF_RING);
        graphics.setStroke(RING_STROKE);
        graphics.draw(shape);
    }

    private static void paintOnScreen(Graphics2D graphics, Look look, Shape shape, Cell cell, double centerX,
                                      double centerY) {
        Color litColor = LedPainter.colorFor(look.color);
        Color unlitColor = unlit(look);
        Color current = blend(unlitColor, litColor, cell.level);
        if (cell.level > 0 && !"pixel".equals(look.style)) {
            paintGlow(graphics, look, litColor, cell.level, centerX, centerY);
        }
        graphics.setColor(current);
        graphics.fill(shape);
        if (cell.level > 0 && "round".equals(look.style)) {
            paintGlint(graphics, centerX, centerY, cell.level);
        }
    }

    private static Color unlit(Look look) {
        Color unlit = LedPainter.unlitColorFor(look.color);
        if (!"sign".equals(look.style)) {
            return unlit;
        }
        return blend(BODY_COLORS.get("sign"), unlit, SIGN_UNLIT_SHARE);
    }

    private static void paintGlow(Graphics2D graphics, Look look, Color litColor, double level, double centerX,
                                  double centerY) {
        double radius = switch (look.style) {
            case "sign" -> SIGN_RADIUS + GLOW_REACH;
            case "module" -> MODULE_RADIUS + GLOW_REACH / 2;
            default -> ROUND_RADIUS + GLOW_REACH / 2;
        };
        Color center = PainterSupport.withAlpha(litColor, (int) Math.round(GLOW_ALPHA * level));
        Color edge = PainterSupport.withAlpha(litColor, 0);
        graphics.setPaint(new RadialGradientPaint((float) centerX, (float) centerY, (float) radius,
                new float[]{0f, 1f}, new Color[]{center, edge}));
        graphics.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, 2 * radius, 2 * radius));
    }

    private static void paintGlint(Graphics2D graphics, double centerX, double centerY, double level) {
        double glint = ROUND_RADIUS * GLINT_SHARE;
        graphics.setColor(new Color(255, 255, 255, (int) Math.round(GLINT_ALPHA * level)));
        graphics.fill(new Ellipse2D.Double(centerX - ROUND_RADIUS * GLINT_SHIFT - glint,
                centerY - ROUND_RADIUS * GLINT_SHIFT - glint, 2 * glint, 2 * glint));
    }

    private static void paintUnknownRing(Graphics2D graphics, Shape shape, ColorScheme scheme) {
        Rectangle2D box = shape.getBounds2D();
        double padding = RING_PADDING;
        Shape ring = shape instanceof Ellipse2D
                ? new Ellipse2D.Double(box.getX() - padding, box.getY() - padding, box.getWidth() + 2 * padding,
                box.getHeight() + 2 * padding)
                : new Rectangle2D.Double(box.getX() - padding, box.getY() - padding, box.getWidth() + 2 * padding,
                box.getHeight() + 2 * padding);
        graphics.setColor(scheme.wireUnknown());
        graphics.setStroke(RING_STROKE);
        graphics.draw(ring);
    }

    /** {@code from} towards {@code to} by {@code share} (0 is {@code from}, 1 is {@code to}). */
    private static Color blend(Color from, Color to, double share) {
        double clamped = Math.max(0, Math.min(1, share));
        return new Color(channel(from.getRed(), to.getRed(), clamped), channel(from.getGreen(), to.getGreen(), clamped),
                channel(from.getBlue(), to.getBlue(), clamped));
    }

    private static int channel(int from, int to, double share) {
        return (int) Math.round(from + (to - from) * share);
    }
}
