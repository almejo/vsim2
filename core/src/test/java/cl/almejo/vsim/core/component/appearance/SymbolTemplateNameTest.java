package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Where a template puts the component's name: large, in the free area, never over the pin names. */
class SymbolTemplateNameTest {
    private static final GridSize SIZE = new GridSize(6, 8);
    private static final List<SymbolTemplates.PinLabel> ALU_PINS = List.of(
            new SymbolTemplates.PinLabel("A[4]", new GridPoint(0, 1)),
            new SymbolTemplates.PinLabel("B[4]", new GridPoint(0, 2)),
            new SymbolTemplates.PinLabel("F[4]", new GridPoint(6, 1)),
            new SymbolTemplates.PinLabel("Cout", new GridPoint(6, 2)));

    @Test
    void theNameIsLargeWhenTheBoxHasRoomForIt() {
        for (SymbolTemplates.Template template : List.of(SymbolTemplates.Template.RECTANGLE,
                SymbolTemplates.Template.ALU, SymbolTemplates.Template.MUX)) {
            assertThat(name(template, "ALU", ALU_PINS).size()).as(template.name()).isEqualTo(TextSize.LARGE);
        }
    }

    @Test
    void theNameLeavesEveryPinRowFree() {
        for (SymbolTemplates.Template template : List.of(SymbolTemplates.Template.RECTANGLE,
                SymbolTemplates.Template.ALU, SymbolTemplates.Template.MUX)) {
            SymbolText text = name(template, "ALU 4 bits", ALU_PINS);
            for (SymbolTemplates.PinLabel pin : ALU_PINS) {
                assertThat(Math.abs(text.anchor().y() - pin.offset().y() * SymbolPoint.EIGHTHS))
                        .as("%s against %s", template, pin.text()).isGreaterThanOrEqualTo(12);
            }
        }
    }

    @Test
    void theNameStaysInsideTheBox() {
        SymbolText text = name(SymbolTemplates.Template.RECTANGLE, "ALU 4 bits", ALU_PINS);
        assertThat(text.anchor().x()).isBetween(0, SIZE.width() * SymbolPoint.EIGHTHS);
        assertThat(text.anchor().y()).isBetween(0, SIZE.height() * SymbolPoint.EIGHTHS);
    }

    @Test
    void withoutPinsTheNameIsCentred() {
        SymbolText text = name(SymbolTemplates.Template.RECTANGLE, "ALU", List.of());
        assertThat(text.anchor()).isEqualTo(new SymbolPoint(24, 32));
        assertThat(text.alignment()).isEqualTo(TextAlignment.CENTER);
    }

    @Test
    void aBoxWithNoFreeAreaStillGetsAShrunkenName() {
        List<SymbolTemplates.PinLabel> crowded = List.of(
                new SymbolTemplates.PinLabel("Dato[8]", new GridPoint(0, 1)),
                new SymbolTemplates.PinLabel("Dato[8]", new GridPoint(0, 2)),
                new SymbolTemplates.PinLabel("Dato[8]", new GridPoint(0, 3)),
                new SymbolTemplates.PinLabel("Salida", new GridPoint(3, 1)),
                new SymbolTemplates.PinLabel("Salida", new GridPoint(3, 2)),
                new SymbolTemplates.PinLabel("Salida", new GridPoint(3, 3)));
        SymbolDrawing drawing = SymbolTemplates.drawing(SymbolTemplates.Template.RECTANGLE, new GridSize(3, 4),
                "Registro de datos", crowded).orElseThrow();
        assertThat(drawing.texts()).hasSize(1);
        assertThat(drawing.texts().getFirst().size()).isNotEqualTo(TextSize.LARGE);
    }

    @Test
    void theAluAndMuxWriteTheirFixedTextWhateverTheComponentIsCalled() {
        assertThat(name(SymbolTemplates.Template.ALU, "Sumador de 4 bits", ALU_PINS).text()).contains("ALU");
        assertThat(name(SymbolTemplates.Template.MUX, "Selector", ALU_PINS).text()).contains("MUX");
    }

    @Test
    void theOtherNamedTemplatesFollowTheComponentName() {
        for (SymbolTemplates.Template template : List.of(SymbolTemplates.Template.RECTANGLE,
                SymbolTemplates.Template.SQUARE, SymbolTemplates.Template.CIRCLE)) {
            assertThat(name(template, "Sumador", ALU_PINS).text()).as(template.name()).isEmpty();
        }
    }

    private static SymbolText name(SymbolTemplates.Template template, String name,
                                   List<SymbolTemplates.PinLabel> pins) {
        return SymbolTemplates.drawing(template, SIZE, name, pins).orElseThrow().texts().getFirst();
    }
}
