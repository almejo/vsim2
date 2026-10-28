package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The rows of bits of a multi-bit Switch: each cell in the colour of its bit, the body upright. */
class MultiBitSwitchPainterTest {

    private static final int ORIGIN_UNITS = 4;
    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final int IMAGE_SIZE = 200;

    private final PainterRegistry painters = PainterRegistry.standard();

    private BufferedImage draw(Map<String, Object> parameters, VisibleState state, ColorScheme scheme) {
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.SWITCH)
                .specializedFor(parameters);
        Component component = type.newComponent(new ElementId("c1"), new GridPoint(ORIGIN_UNITS, ORIGIN_UNITS),
                Rotation.R0, parameters, "");
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        ComponentDrawing.paint(graphics, type, component, state, scheme, painters);
        graphics.dispose();
        return image;
    }

    /** A pixel just above the digit of {@code bit}'s cell, with the body's corner at {@code bodyX}, {@code bodyY}. */
    private static Color cellColor(BufferedImage image, int width, int bit, int bodyX, int bodyY) {
        MultiBitLayout.BitCell cell = MultiBitLayout.cells(width).stream()
                .filter(candidate -> candidate.bit() == bit).findFirst().orElseThrow();
        return new Color(image.getRGB((int) (bodyX + cell.x() + cell.width() / 2),
                (int) (bodyY + cell.y() + 1.5)));
    }

    @Test
    void everyCellTakesTheColourOfItsBitReadAtPaintTime() {
        for (String schemeId : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(schemeId).orElseThrow();
            BufferedImage image = draw(Map.of("width", 4), new VisibleState.SwitchState(Value.of(4, 0b1010)), scheme);

            int corner = ORIGIN_UNITS * UNIT;
            assertThat(cellColor(image, 4, 3, corner, corner)).as(schemeId)
                    .isEqualTo(SignalColors.valueColor(Value.ONE, scheme));
            assertThat(cellColor(image, 4, 2, corner, corner)).as(schemeId)
                    .isEqualTo(SignalColors.valueColor(Value.ZERO, scheme));
            assertThat(cellColor(image, 4, 1, corner, corner)).as(schemeId)
                    .isEqualTo(SignalColors.valueColor(Value.ONE, scheme));
            assertThat(cellColor(image, 4, 0, corner, corner)).as(schemeId)
                    .isEqualTo(SignalColors.valueColor(Value.ZERO, scheme));
        }
    }

    @Test
    void withoutASnapshotTheCellsShowTheValueParameter() {
        BufferedImage image = draw(Map.of("width", 4, "value", "0b1000"), VisibleState.NONE, ColorScheme.LIGHT);

        int corner = ORIGIN_UNITS * UNIT;
        assertThat(cellColor(image, 4, 3, corner, corner))
                .isEqualTo(SignalColors.valueColor(Value.ONE, ColorScheme.LIGHT));
        assertThat(cellColor(image, 4, 0, corner, corner))
                .isEqualTo(SignalColors.valueColor(Value.ZERO, ColorScheme.LIGHT));
    }

    @Test
    void theBodyStaysWhereTheLeadLeavesRoomForItOnTheLeftSide() {
        BufferedImage image = draw(Map.of("width", 4, "side", "left"), new VisibleState.SwitchState(Value.of(4, 0b1000)),
                ColorScheme.LIGHT);

        int bodyX = (ORIGIN_UNITS + MultiBitLayout.LEAD_UNITS) * UNIT;
        assertThat(cellColor(image, 4, 3, bodyX, ORIGIN_UNITS * UNIT))
                .isEqualTo(SignalColors.valueColor(Value.ONE, ColorScheme.LIGHT));
    }

    @Test
    void theLeadReachesThePinOnTheRight() {
        BufferedImage image = draw(Map.of("width", 4), new VisibleState.SwitchState(Value.of(4, 0)), ColorScheme.LIGHT);

        GridPoint pin = MultiBitLayout.pinFor(4, "right");
        int pixelX = (ORIGIN_UNITS + pin.x()) * UNIT - UNIT;
        int pixelY = (ORIGIN_UNITS + pin.y()) * UNIT;
        assertThat(new Color(image.getRGB(pixelX, pixelY))).isNotEqualTo(ColorScheme.LIGHT.background());
    }
}
