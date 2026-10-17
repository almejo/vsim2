package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AluAndComparatorPaintersTest {

    private final PainterRegistry painters = PainterRegistry.standard();

    @Test
    void theAluShowsEachOperationAQuestionMarkWhenUndefinedAndAdditionInThePalette() {
        List<String> texts = new ArrayList<>();
        for (int operation = 0; operation < 8; operation++) {
            texts.add(PainterRegistry.aluOperation(new VisibleState.AluState(operation)).orElseThrow());
        }
        assertThat(texts).containsExactly("A+B", "A−B", "AND", "OR", "XOR", "NOT A", "A<<1", "A>>1");
        assertThat(PainterRegistry.aluOperation(new VisibleState.AluState(-1))).contains("?");
        assertThat(PainterRegistry.aluOperation(VisibleState.NONE)).contains("A+B");
        assertThat(PainterRegistry.aluOperation(new VisibleState.LedState(Value.ONE)))
                .isEqualTo(Optional.empty());
    }

    @Test
    void theAluPainterDrawsTheOperationItIsDoing() {
        ComponentType alu = StandardComponentTypes.registry().require(StandardComponentTypes.ALU);
        assertThat(painters.painterFor(StandardComponentTypes.ALU)).isInstanceOf(BlockPainter.class);
        int[] adding = pixels(alu, new VisibleState.AluState(0));
        int[] subtracting = pixels(alu, new VisibleState.AluState(1));
        int[] undefined = pixels(alu, new VisibleState.AluState(-1));
        assertThat(Arrays.equals(adding, subtracting)).isFalse();
        assertThat(Arrays.equals(adding, undefined)).isFalse();
        assertThat(pixels(alu, VisibleState.NONE)).isEqualTo(adding);
    }

    @Test
    void theComparatorIsABlock() {
        assertThat(painters.painterFor(StandardComponentTypes.COMPARATOR)).isInstanceOf(BlockPainter.class);
    }

    private int[] pixels(ComponentType type, VisibleState state) {
        int scale = 3;
        BufferedImage image = PaintersTest.blankImage(type.size().width() * 8 * scale + 20,
                type.size().height() * 8 * scale + 20);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(10, 10);
        graphics.scale(scale, scale);
        painters.painterFor(type.id()).paint(graphics, type, Map.of(), state, PaintersTest.SCHEME);
        graphics.dispose();
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
