package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CircleTemplateTest {
    @Test
    void theCircleIsTheLargestOneCentredInTheBox() {
        GridSize size = new GridSize(6, 8);
        SymbolDrawing drawing = SymbolTemplates.drawing(SymbolTemplates.Template.CIRCLE, size).orElseThrow();
        SymbolPath circle = (SymbolPath) drawing.elements().getFirst();
        List<Integer> xs = circle.nodes().stream().map(node -> node.point().x()).toList();
        List<Integer> ys = circle.nodes().stream().map(node -> node.point().y()).toList();
        // box is 48 x 64 eighths: the diameter is 48, centred vertically (8..56)
        assertThat(xs).contains(0, 48).allMatch(value -> value >= 0 && value <= 48);
        assertThat(ys).contains(8, 56).allMatch(value -> value >= 8 && value <= 56);
        assertThat(circle.closed()).isTrue();
    }

    @Test
    void theCircleOfASquareBoxFillsIt() {
        SymbolDrawing drawing = SymbolTemplates.drawing(SymbolTemplates.Template.CIRCLE, new GridSize(4, 4))
                .orElseThrow();
        SymbolPath circle = (SymbolPath) drawing.elements().getFirst();
        assertThat(circle.nodes()).extracting(node -> node.point().x()).contains(0, 32);
        assertThat(circle.nodes()).extracting(node -> node.point().y()).contains(0, 32);
    }
}
