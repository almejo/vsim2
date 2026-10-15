package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The «Constante»: a box with its value written in the chosen format and a lead to the output pin, thick when the pin
 * is a bus. The box is as wide as the shape says, which depends on the width and the format only.
 */
public final class ConstantPainter implements ComponentPainter {

    /** Size of the value text; the shape's box is wide enough for the longest text of a width and format at this size. */
    static final float TEXT_SIZE = 9f;
    /** Length of the lead between the box and the pin, in pixels. */
    static final double LEAD = 5;
    /** Space kept free on each side of the text inside the box. */
    static final double TEXT_PADDING = 3;
    private static final double BOX_TOP = 1;
    private static final double BOX_HEIGHT = 14;
    private static final BasicStroke BUS_STROKE = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final String HEX_CHARACTERS = "0123456789ABCDEF";

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        ComponentType shaped = type.specializedFor(parameters);
        int width = (Integer) resolved.get("width");
        double pixelWidth = shaped.size().width() * UNIT;
        double boxWidth = pixelWidth - LEAD;
        double middle = shaped.pin(0).position().y() * UNIT;
        if (width > 1) {
            graphics.setColor(scheme.componentStroke());
            graphics.setStroke(BUS_STROKE);
            graphics.draw(new Line2D.Double(boxWidth, middle, pixelWidth, middle));
        } else {
            PainterSupport.lead(graphics, boxWidth, middle, pixelWidth, middle, scheme);
        }
        PainterSupport.body(graphics, new RoundRectangle2D.Double(0, BOX_TOP, boxWidth, BOX_HEIGHT, 5, 5), scheme);
        String text = shownText(resolved);
        float size = (float) Math.min(TEXT_SIZE,
                TEXT_SIZE * (boxWidth - 2 * TEXT_PADDING) / textWidth(text, TEXT_SIZE));
        PainterSupport.centeredText(graphics, text, boxWidth / 2, middle, size, scheme.text());
        PainterSupport.pinDots(graphics, shaped, scheme);
    }

    /** The text written in the box for the (resolved) parameters. */
    static String shownText(Map<String, Object> resolved) {
        return ConstantValue.display(ConstantValue.parse((String) resolved.get("value")),
                (Integer) resolved.get("width"), (String) resolved.get("format"));
    }

    /** The width in pixels of {@code text} drawn at {@code size}. */
    private static double textWidth(String text, float size) {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size)).stringWidth(text);
        } finally {
            graphics.dispose();
        }
    }

    /** The width in pixels of the widest text the box can ever show at this width and format. */
    static double widestTextWidth(int width, String format) {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            graphics.setFont(PainterSupport.BASE_FONT.deriveFont(TEXT_SIZE));
            FontMetrics metrics = graphics.getFontMetrics();
            double widestCharacter = 0;
            for (char character : HEX_CHARACTERS.toCharArray()) {
                widestCharacter = Math.max(widestCharacter, metrics.charWidth(character));
            }
            String resolvedFormat = ConstantValue.resolveFormat(format, width);
            int characters = ConstantValue.longestDisplay(width, resolvedFormat);
            return resolvedFormat.equals(ConstantValue.HEX)
                    ? metrics.charWidth('0') + metrics.charWidth('x') + (characters - 2) * widestCharacter
                    : characters * widestCharacter;
        } finally {
            graphics.dispose();
        }
    }
}
