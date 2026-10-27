package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The painter keeps the decoded contents of the last few memories, so a circuit with several never decodes per frame. */
class MemoryPainterCacheTest {

    @Test
    void twoMemoriesWithDifferentContentsDecodeOnceEachAcrossManyPaints() {
        MemoryPainter painter = new MemoryPainter();
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.ROM);
        Map<String, Object> first = Map.of(MemoryTypes.CONTENTS, MemoryContents.empty().with(0, 1).encode());
        Map<String, Object> second = Map.of(MemoryTypes.CONTENTS, MemoryContents.empty().with(3, 7).encode());
        BufferedImage image = PaintersTest.blankImage(300, 300);
        Graphics2D graphics = image.createGraphics();

        for (int frame = 0; frame < 100; frame++) {
            painter.paint(graphics, type, first, null, ColorScheme.LIGHT);
            painter.paint(graphics, type, second, null, ColorScheme.LIGHT);
        }
        graphics.dispose();

        assertThat(painter.decodeCount()).isEqualTo(2);
    }
}
