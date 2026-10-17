package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StoredValuePainterTest {

    private static final List<String> SEQUENTIAL_BLOCKS = List.of(StandardComponentTypes.REGISTER,
            StandardComponentTypes.COUNTER);

    private static Map<String, Object> format(String format) {
        return Map.of(ConstantValue.FORMAT, format);
    }

    private static Optional<String> text(Value value, String format) {
        return PainterRegistry.storedValueText(new VisibleState.StoredValueState(value), format(format));
    }

    @Test
    void theStoredValueIsWrittenInTheChosenFormat() {
        assertThat(text(Value.of(4, 0xA), ConstantValue.AUTO)).contains("1010");
        assertThat(text(Value.of(8, 0xA), ConstantValue.AUTO)).contains("0xA");
        assertThat(text(Value.of(8, 0xA), ConstantValue.BINARY)).contains("00001010");
        assertThat(text(Value.of(8, 0xFF), ConstantValue.DECIMAL)).contains("255");
        assertThat(text(Value.of(8, 0xFF), ConstantValue.SIGNED)).contains("-1");
        assertThat(text(Value.of(32, 0x8000_0000L), ConstantValue.SIGNED)).contains("-2147483648");
        assertThat(text(Value.highImpedance(4), ConstantValue.BINARY)).contains("ZZZZ");
    }

    /** M1 (M7 fix round): like the ALU's A+B, the palette icon shows the power-on state, 0 in the block's format. */
    @Test
    void withoutAStateTheBlockShowsZeroInItsFormat() {
        assertThat(PainterRegistry.storedValueText(VisibleState.NONE,
                Map.of(StandardComponentTypes.WIDTH, 8, ConstantValue.FORMAT, ConstantValue.AUTO))).contains("0x0");
        assertThat(PainterRegistry.storedValueText(VisibleState.NONE,
                Map.of(StandardComponentTypes.WIDTH, 4, ConstantValue.FORMAT, ConstantValue.AUTO))).contains("0000");
    }

    /**
     * M2 (M7 fix round): turned sideways, a 32-bit register's 32 binary digits still fit across its body: both its
     * first and its last digit are drawn.
     */
    @Test
    void thirtyTwoBinaryDigitsFitSideways() {
        Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, 32, ConstantValue.FORMAT,
                ConstantValue.BINARY);
        for (String typeId : SEQUENTIAL_BLOCKS) {
            ComponentType type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
            ComponentPainter painter = PainterRegistry.standard().painterFor(typeId);
            BufferedImage ones = paintSideways(painter, type, parameters, Value.of(32, 0xFFFF_FFFFL));
            BufferedImage firstZero = paintSideways(painter, type, parameters, Value.of(32, 0x7FFF_FFFFL));
            BufferedImage lastZero = paintSideways(painter, type, parameters, Value.of(32, 0xFFFF_FFFEL));
            assertThat(differingPixels(ones, firstZero)).as(typeId + " first digit").isGreaterThan(0);
            assertThat(differingPixels(ones, lastZero)).as(typeId + " last digit").isGreaterThan(0);
        }
    }

    private static BufferedImage paintSideways(ComponentPainter painter, ComponentType type,
                                               Map<String, Object> parameters, Value value) {
        int width = type.size().width() * 8;
        int height = type.size().height() * 8;
        int side = Math.max(width, height) * 3 + 20;
        BufferedImage image = PaintersTest.blankImage(side, side);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(side / 2.0, side / 2.0);
        graphics.scale(3, 3);
        graphics.rotate(Math.PI / 2);
        graphics.translate(-width / 2.0, -height / 2.0);
        painter.paint(graphics, type, parameters, new VisibleState.StoredValueState(value), PaintersTest.SCHEME);
        graphics.dispose();
        return image;
    }

    @Test
    void theRegisterAndTheCounterDrawTheirValueInTheirFormat() {
        PainterRegistry registry = PainterRegistry.standard();
        VisibleState stored = new VisibleState.StoredValueState(Value.of(4, 0xA));
        for (String typeId : SEQUENTIAL_BLOCKS) {
            ComponentType type = StandardComponentTypes.registry().require(typeId);
            ComponentPainter painter = registry.painterFor(typeId);
            assertThat(painter).as(typeId).isInstanceOf(BlockPainter.class);
            BufferedImage binary = paint(painter, type, format(ConstantValue.BINARY), stored);
            BufferedImage hex = paint(painter, type, format(ConstantValue.HEX), stored);
            BufferedImage nothing = paint(painter, type, format(ConstantValue.HEX), VisibleState.NONE);
            assertThat(differingPixels(binary, hex)).as(typeId).isGreaterThan(10);
            assertThat(differingPixels(nothing, hex)).as(typeId).isGreaterThan(10);
        }
    }

    private static BufferedImage paint(ComponentPainter painter, ComponentType type, Map<String, Object> parameters,
                                       VisibleState state) {
        BufferedImage image = PaintersTest.blankImage(type.size().width() * 8 * 2 + 20,
                type.size().height() * 8 * 2 + 20);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(10, 10);
        graphics.scale(2, 2);
        painter.paint(graphics, type.specializedFor(parameters), parameters, state, PaintersTest.SCHEME);
        graphics.dispose();
        return image;
    }

    private static int differingPixels(BufferedImage first, BufferedImage second) {
        int differing = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        return differing;
    }
}
