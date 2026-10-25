package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InterfacePins;
import java.util.Map;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The templates' name text against the real painter: bold, large and never over a pin name. */
class TemplateNamesTest {
    private static final GridSize SIZE = new GridSize(6, 8);
    private static final String NAME = "ALU 4 bits";
    private static final int OFFSET = 8;
    private static final int IMAGE_SIZE = 64;

    @Test
    void theNameOfEveryTemplateTouchesNoPinName() {
        assertNoNameOverPinNames(definition(1, 2));
    }

    @Test
    void theNameKeepsClearOfPinNamesEvenInACrowdedBox() {
        assertNoNameOverPinNames(definition(2, 4));
    }

    private static void assertNoNameOverPinNames(ComponentDefinition definition) {
        for (SymbolTemplates.Template template : List.of(SymbolTemplates.Template.RECTANGLE,
                SymbolTemplates.Template.SQUARE, SymbolTemplates.Template.ALU, SymbolTemplates.Template.MUX)) {
            SymbolDrawing drawing = template(definition, template);
            SymbolText name = drawing.texts().getFirst();
            boolean[] nameInk = ink(definition, only(name));
            boolean[] pinNameInk = ink(definition, new SymbolDrawing(SIZE, List.of(), true));
            assertThat(overlap(nameInk, pinNameInk)).as("%s: name over pin names", template).isZero();
            assertThat(count(nameInk)).as("%s: the name is drawn", template).isPositive();
        }
    }

    @Test
    void theComponentNameIsDrawnBoldAndATextTheUserTypedIsNot() {
        ComponentDefinition definition = definition(1, 2);
        SymbolText follows = new SymbolText(new SymbolPoint(24, 32), Optional.empty(), TextSize.LARGE,
                TextAlignment.CENTER);
        SymbolText typed = new SymbolText(new SymbolPoint(24, 32), Optional.of(NAME), TextSize.LARGE,
                TextAlignment.CENTER);
        int boldInk = count(ink(definition, only(follows)));
        int regularInk = count(ink(definition, only(typed)));
        assertThat(boldInk).isGreaterThan(regularInk);
    }

    private static SymbolDrawing only(SymbolElement element) {
        return new SymbolDrawing(SIZE, List.of(element), false);
    }

    private static SymbolDrawing template(ComponentDefinition definition, SymbolTemplates.Template template) {
        return SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .withDefinitionName(NAME).applyTemplate(template).appearance().drawing().orElseThrow();
    }

    /** The pixels a drawing adds to a blank one (pin dots and stubs are in both and cancel out). */
    private static boolean[] ink(ComponentDefinition definition, SymbolDrawing drawing) {
        BufferedImage blank = paint(definition, new SymbolDrawing(SIZE, List.of(), false));
        BufferedImage painted = paint(definition, drawing);
        boolean[] ink = new boolean[IMAGE_SIZE * IMAGE_SIZE * 4];
        int width = painted.getWidth();
        for (int y = 0; y < painted.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                ink[y * width + x] = painted.getRGB(x, y) != blank.getRGB(x, y);
            }
        }
        return ink;
    }

    private static BufferedImage paint(ComponentDefinition definition, SymbolDrawing drawing) {
        ComponentType type = SubcircuitTypes.typeOf(definition);
        BufferedImage image = new BufferedImage(IMAGE_SIZE * 2, IMAGE_SIZE * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.translate(OFFSET, OFFSET);
            SymbolPainter.paint(graphics, type, drawing, ColorSchemes.LIGHT, NAME);
        } finally { graphics.dispose(); }
        return image;
    }

    private static int overlap(boolean[] first, boolean[] second) {
        int shared = 0;
        for (int index = 0; index < first.length; index++) {
            if (first[index] && second[index]) { shared++; }
        }
        return shared;
    }

    private static int count(boolean[] ink) {
        int total = 0;
        for (boolean pixel : ink) {
            if (pixel) { total++; }
        }
        return total;
    }

    /** A, B on the left and F, Cout on the right, {@code firstRow} and {@code secondRow} units from the top. */
    private static ComponentDefinition definition(int firstRow, int secondRow) {
        return new ComponentDefinition("alu", NAME, "", SIZE, List.of(
                new DefinitionPin(PinSpec.input("A", 0, firstRow, 4), new ElementId("a")),
                new DefinitionPin(PinSpec.input("B", 0, secondRow, 4), new ElementId("b")),
                new DefinitionPin(PinSpec.output("F", 6, firstRow, 4), new ElementId("f")),
                new DefinitionPin(PinSpec.output("Cout", 6, secondRow), new ElementId("cout"))),
                new CircuitFragment(List.of(interfacePin("a", InterfacePins.INPUT_PIN, 4),
                        interfacePin("b", InterfacePins.INPUT_PIN, 4), interfacePin("f", InterfacePins.OUTPUT_PIN, 4),
                        interfacePin("cout", InterfacePins.OUTPUT_PIN, 1)), List.of(), List.of()));
    }

    private static Component interfacePin(String id, String typeId, int width) {
        return new Component(new ElementId(id), typeId, new GridPoint(0, 0), Rotation.R0,
                width > 1 ? Map.of(InterfacePins.WIDTH, width) : Map.of(), id);
    }
}
