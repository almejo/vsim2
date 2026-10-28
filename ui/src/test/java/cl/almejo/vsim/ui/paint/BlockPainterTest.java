package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BlockPainterTest {

    private static final ColorScheme SCHEME = PaintersTest.SCHEME;
    private static final int SCALE = 4;
    private static final int OFFSET = 10;

    private final ComponentType adder = StandardComponentTypes.registry().require(StandardComponentTypes.ADDER);

    /** {@code painter} on {@code type}, drawn 4 times larger without antialiasing, the origin at (10, 10). */
    private static BufferedImage paint(BlockPainter painter, ComponentType type, Map<String, Object> parameters,
                                       VisibleState state) {
        BufferedImage image = PaintersTest.blankImage(OFFSET * 2 + type.size().width() * 8 * SCALE,
                OFFSET * 2 + type.size().height() * 8 * SCALE);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(OFFSET, OFFSET);
        graphics.scale(SCALE, SCALE);
        painter.paint(graphics, type, parameters, state, SCHEME);
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

    /** The screen pixel of local point (localX, localY). */
    private static int pixel(BufferedImage image, double localX, double localY) {
        return image.getRGB((int) (OFFSET + localX * SCALE), (int) (OFFSET + localY * SCALE));
    }

    @Test
    void busLeadsAreThickAndOneBitLeadsThin() {
        BlockPainter painter = new BlockPainter("Σ", BlockPainter.Body.CHEVRON);
        Map<String, Object> fourBits = Map.of(StandardComponentTypes.WIDTH, 4);
        Map<String, Object> oneBit = Map.of(StandardComponentTypes.WIDTH, 1);
        BufferedImage bus = paint(painter, adder, fourBits, VisibleState.NONE);
        BufferedImage wire = paint(painter, adder, oneBit, VisibleState.NONE);
        PinSpec first = adder.pin(0);
        double leadX = 2.6;
        double besideLead = first.position().y() * 8 + 1.25;
        assertThat(new Color(pixel(bus, leadX, besideLead))).isEqualTo(SCHEME.componentStroke());
        assertThat(new Color(pixel(wire, leadX, besideLead))).isEqualTo(SCHEME.background());
        assertThat(new Color(pixel(wire, leadX, first.position().y() * 8))).isEqualTo(SCHEME.componentStroke());
    }

    @Test
    void theLiveTextIsDrawnWhenTheStateGivesOne() {
        BlockPainter plain = new BlockPainter("ALU", BlockPainter.Body.CHEVRON);
        BlockPainter live = plain.withLiveText(state -> state instanceof VisibleState.AluState alu
                ? Optional.of(alu.operation() < 0 ? "?" : "A+B") : Optional.empty());
        VisibleState adding = new VisibleState.AluState(0);

        assertThat(differingPixels(paint(plain, adder, Map.of(), adding), paint(live, adder, Map.of(), adding)))
                .isGreaterThan(20);
        assertThat(differingPixels(paint(live, adder, Map.of(), new VisibleState.AluState(-1)),
                paint(live, adder, Map.of(), adding))).isGreaterThan(5);
        assertThat(differingPixels(paint(plain, adder, Map.of(), VisibleState.NONE),
                paint(live, adder, Map.of(), VisibleState.NONE))).isZero();
    }

    @Test
    void theLiveTextCanReadTheParameters() {
        BlockPainter live = new BlockPainter("REG", BlockPainter.Body.RECTANGLE).withLiveText(
                (state, parameters) -> state instanceof VisibleState.StoredValueState stored
                        ? Optional.of(parameters.get(StandardComponentTypes.WIDTH) + ":" + stored.value().hex())
                        : Optional.empty());
        VisibleState stored = new VisibleState.StoredValueState(Value.of(4, 0xA));
        assertThat(differingPixels(paint(live, adder, Map.of(StandardComponentTypes.WIDTH, 4), stored),
                paint(live, adder, Map.of(StandardComponentTypes.WIDTH, 8), stored))).isGreaterThan(5);
    }

    @Test
    void aPinNamedClkGetsTheClockTriangleInsteadOfItsName() {
        BlockPainter painter = new BlockPainter("REG", BlockPainter.Body.RECTANGLE);
        BufferedImage clocked = paint(painter, boxWithBottomPin("CLK"), Map.of(), VisibleState.NONE);
        BufferedImage unnamed = paint(painter, boxWithBottomPin(" "), Map.of(), VisibleState.NONE);
        // The triangle's apex points up into the body from the bottom edge, above the pin at local (16, 48).
        assertThat(new Color(pixel(clocked, 16, 48 - 4 - 4.5))).isEqualTo(SCHEME.componentStroke());
        assertThat(new Color(pixel(unnamed, 16, 48 - 4 - 4.5))).isNotEqualTo(SCHEME.componentStroke());
    }

    @Test
    void everyBodyShapeDrawsNearItsBoundsInEveryRotation() {
        PainterRegistry registry = new PainterRegistry();
        for (BlockPainter.Body body : BlockPainter.Body.values()) {
            registry.register(StandardComponentTypes.ADDER, new BlockPainter("Σ", body).withLiveText(
                    state -> Optional.of("0xFFFFFFFF")));
            for (Rotation rotation : Rotation.values()) {
                Component component = adder.newComponent(new ElementId("c1"), new GridPoint(12, 12), rotation,
                        Map.of(), "");
                BufferedImage image = PaintersTest.blankImage(320, 320);
                Graphics2D graphics = image.createGraphics();
                ComponentDrawing.paint(graphics, adder, component, VisibleState.NONE, SCHEME, registry);
                graphics.dispose();
                java.awt.geom.Rectangle2D allowed = ComponentDrawing.documentBounds(adder, component);
                allowed.setRect(allowed.getX() - 8, allowed.getY() - 8, allowed.getWidth() + 16,
                        allowed.getHeight() + 16);
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if (image.getRGB(x, y) != SCHEME.background().getRGB()) {
                            assertThat(allowed.contains(x, y)).as(body + " " + rotation + " " + x + "," + y).isTrue();
                        }
                    }
                }
            }
        }
    }

    /** A 4 x 6 block with a data pin on each side and one 1-bit pin named {@code name} in the middle of the bottom. */
    private static ComponentType boxWithBottomPin(String name) {
        return new ComponentType("test-block", ComponentCategory.ARITHMETIC, new GridSize(4, 6),
                List.of(PinSpec.input("D", 0, 3, 4), PinSpec.output("Q", 4, 3, 4), PinSpec.input(name, 2, 6)),
                List.of(), parameters -> (context, pinIndex) -> {
                });
    }
}
