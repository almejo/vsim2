package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.theme.ReadableColors;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

public final class SwitchPainter implements ComponentPainter {

    /** WCAG's graphics contrast: the switch's text is a single small bold character. */
    private static final double MINIMUM_TEXT_CONTRAST = 3.0;

    /** The digits of a bit cell are smaller than the one-bit switch's: a cell is {@link MultiBitLayout#BIT_PITCH} pixels wide. */
    private static final float CELL_TEXT_SIZE = 9f;
    private static final double CELL_INSET = 0.5;
    private static final double CELL_CORNER = 4;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        if (MultiBitLayout.isMultiBit(type)) {
            paintBits(graphics, type, parameters, state, scheme);
            return;
        }
        Value value = state instanceof VisibleState.SwitchState switchState
                ? switchState.value()
                : fromParameter(parameters.get("value"));
        PainterSupport.lead(graphics, 27, 8, 32, 8, scheme);
        Color fill = SignalColors.valueColor(value, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(0, 1, 27, 14, 7, 7), scheme, fill);
        String text = value.equals(Value.ONE) ? "1" : value.equals(Value.ZERO) ? "0" : "Z";
        PainterSupport.centeredText(graphics, text, 13.5, 8, 10f, textColor(fill));
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The rows of bits: each cell filled with the colour of its 0 or 1 and its digit on top. */
    private static void paintBits(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                                  VisibleState state, ColorScheme scheme) {
        Value value = state instanceof VisibleState.SwitchState switchState
                ? switchState.value()
                : fromParameter(parameters.get("value"), MultiBitLayout.widthOf(type));
        MultiBitBody.paint(graphics, type, parameters, scheme, (cellGraphics, cell) -> {
            boolean on = value.bit(cell.bit()) == BitState.ONE;
            Color fill = SignalColors.valueColor(on ? Value.ONE : Value.ZERO, scheme);
            cellGraphics.setColor(fill);
            cellGraphics.fill(new RoundRectangle2D.Double(cell.x() + CELL_INSET, cell.y(),
                    cell.width() - 2 * CELL_INSET, cell.height(), CELL_CORNER, CELL_CORNER));
            PainterSupport.centeredText(cellGraphics, on ? "1" : "0", cell.x() + cell.width() / 2,
                    cell.y() + cell.height() / 2, CELL_TEXT_SIZE, textColor(fill));
        });
    }

    /**
     * The colour of the 0, 1 or Z written on a switch filled with {@code fill}: white, unless white falls below
     * {@link #MINIMUM_TEXT_CONTRAST}:1 there (a bright 1: the gold of «Placa», CSim's white, the greens of Claro and
     * Oscuro), then black.
     */
    static Color textColor(Color fill) {
        return ReadableColors.contrast(Color.WHITE, fill) >= MINIMUM_TEXT_CONTRAST ? Color.WHITE : Color.BLACK;
    }

    /** What a multi-bit switch holds before any snapshot: the number of its «value» parameter. */
    private static Value fromParameter(Object parameter, int width) {
        return Value.of(width, ConstantValue.bitsOf(String.valueOf(parameter), width).orElse(0L));
    }

    private static Value fromParameter(Object parameter) {
        return switch (String.valueOf(parameter)) {
            case "1" -> Value.ONE;
            case "0" -> Value.ZERO;
            default -> Value.HIGH_IMPEDANCE;
        };
    }
}
