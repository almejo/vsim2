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
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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

    /** The case that failed on screen: the bold real font is wider than the core's estimate (ALU with Op[2] below, F dragged off the edge, the template laid out for a short placeholder name). */
    @Test
    void theRenderedNameOfTheAluStaysOffEveryPinNameAndInsideItsBody() {
        for (int zoom : new int[]{1, 6}) {
            assertRenderedNameClear(zoom);
        }
    }

    private static void assertRenderedNameClear(int zoom) {
        ComponentDefinition definition = aluDefinition(5);
        for (SymbolTemplates.Template template : List.of(SymbolTemplates.Template.ALU,
                SymbolTemplates.Template.MUX, SymbolTemplates.Template.RECTANGLE)) {
            SymbolDrawing full = templateForAnotherName(aluDefinition(6), template);
            List<SymbolElement> withoutName = full.elements().stream()
                    .filter(element -> !(element instanceof SymbolText)).toList();
            SymbolDrawing nameless = new SymbolDrawing(SIZE, withoutName, true);
            SymbolDrawing bare = new SymbolDrawing(SIZE, withoutName, false);
            boolean[] nameInk = difference(paint(definition, full, zoom), paint(definition, nameless, zoom));
            boolean[] pinNameInk = difference(paint(definition, nameless, zoom), paint(definition, bare, zoom));
            assertThat(count(nameInk)).as("%s at %dx: the name is drawn", template, zoom).isPositive();
            assertThat(overlap(nameInk, pinNameInk)).as("%s at %dx: name over pin names", template, zoom).isZero();
        }
    }

    @Test
    void aNameTooWideForItsFreeAreaIsElidedNotDrawnPastTheBox() {
        ComponentDefinition definition = aluDefinition(6);
        String longName = "A very long component name that never fits";
        SymbolDrawing drawing = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .withDefinitionName(longName).applyTemplate(SymbolTemplates.Template.ALU).appearance().drawing()
                .orElseThrow();
        BufferedImage painted = paint(definition, drawing);
        int rightEdge = OFFSET + SIZE.width() * 8;
        for (int y = 0; y < painted.getHeight(); y++) {
            for (int x = rightEdge + 3; x < painted.getWidth(); x++) {
                assertThat(painted.getRGB(x, y) >>> 24).as("nothing past the box at %d,%d", x, y).isZero();
            }
        }
    }

    @Test
    void everyTextOfADrawingIsBoldLikeTheComponentName() {
        ComponentDefinition definition = definition(1, 2);
        SymbolText follows = new SymbolText(new SymbolPoint(24, 32), Optional.empty(), TextSize.SMALL,
                TextAlignment.CENTER);
        SymbolText typed = new SymbolText(new SymbolPoint(24, 32), Optional.of(NAME), TextSize.SMALL,
                TextAlignment.CENTER);
        int nameInk = count(ink(definition, only(follows)));
        int typedInk = count(ink(definition, only(typed)));
        assertThat((double) typedInk).isCloseTo(nameInk, Percentage.withPercentage(5));
    }

    /** The MUX of the editor render: S on the bottom edge, between the slanted outline and D3's name. */
    @Test
    void theNameOfABottomPinNeverOverlapsTheOutlineOrAnotherPinName() {
        ComponentDefinition withSelect = muxDefinition(true);
        ComponentDefinition withoutSelect = muxDefinition(false);
        SymbolDrawing shape = SymbolDraft.of(ComponentAppearance.of(withSelect), withSelect.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.MUX).appearance().drawing().orElseThrow();
        List<SymbolElement> outlineOnly = shape.elements().stream()
                .filter(element -> !(element instanceof SymbolText)).toList();
        SymbolDrawing named = new SymbolDrawing(SIZE, outlineOnly, true);
        SymbolDrawing silent = new SymbolDrawing(SIZE, outlineOnly, false);
        for (int zoom : new int[]{1, 4}) {
            boolean[] selectNameAndStub = difference(paint(withSelect, named, zoom), paint(withoutSelect, named, zoom));
            boolean[] selectStub = difference(paint(withSelect, silent, zoom), paint(withoutSelect, silent, zoom));
            boolean[] selectName = subtract(selectNameAndStub, selectStub);
            boolean[] otherNames = difference(paint(withoutSelect, named, zoom), paint(withoutSelect, silent, zoom));
            boolean[] outline = difference(paint(withoutSelect, silent, zoom),
                    paint(withoutSelect, new SymbolDrawing(SIZE, List.of(), false), zoom));
            assertThat(overlap(selectName, outline)).as("S over the outline at %dx", zoom).isZero();
            assertThat(overlap(selectName, otherNames)).as("S over another pin name at %dx", zoom).isZero();
        }
    }

    private static boolean[] subtract(boolean[] first, boolean[] second) {
        boolean[] result = new boolean[first.length];
        for (int index = 0; index < first.length; index++) {
            result[index] = first[index] && !second[index];
        }
        return result;
    }

    private static ComponentDefinition muxDefinition(boolean withSelect) {
        List<DefinitionPin> pins = new ArrayList<>(List.of(
                new DefinitionPin(PinSpec.input("D0", 0, 1, 4), new ElementId("d0")),
                new DefinitionPin(PinSpec.input("D1", 0, 3, 4), new ElementId("d1")),
                new DefinitionPin(PinSpec.input("D2", 0, 5, 4), new ElementId("d2")),
                new DefinitionPin(PinSpec.input("D3", 0, 7, 4), new ElementId("d3")),
                new DefinitionPin(PinSpec.output("Y", 6, 4, 4), new ElementId("y"))));
        List<Component> interfaces = new ArrayList<>(List.of(
                interfacePin("d0", InterfacePins.INPUT_PIN, 4), interfacePin("d1", InterfacePins.INPUT_PIN, 4),
                interfacePin("d2", InterfacePins.INPUT_PIN, 4), interfacePin("d3", InterfacePins.INPUT_PIN, 4),
                interfacePin("y", InterfacePins.OUTPUT_PIN, 4)));
        if (withSelect) {
            pins.add(new DefinitionPin(PinSpec.input("S", 3, 8, 2), new ElementId("s")));
            interfaces.add(interfacePin("s", InterfacePins.INPUT_PIN, 2));
        }
        return new ComponentDefinition("mux", "Selector", "", SIZE, pins,
                new CircuitFragment(interfaces, List.of(), List.of()));
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

    /** The template as laid out for a short placeholder name, painted later with the real, longer one. */
    private static SymbolDrawing templateForAnotherName(ComponentDefinition definition,
                                                        SymbolTemplates.Template template) {
        return SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(template).appearance().drawing().orElseThrow();
    }

    private static boolean[] difference(BufferedImage first, BufferedImage second) {
        boolean[] ink = new boolean[first.getWidth() * first.getHeight()];
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                ink[y * first.getWidth() + x] = first.getRGB(x, y) != second.getRGB(x, y);
            }
        }
        return ink;
    }

    private static BufferedImage paint(ComponentDefinition definition, SymbolDrawing drawing) {
        return paint(definition, drawing, 1);
    }

    /** Painted under a zoom, as the editor does: the text must fit whatever the transform. */
    private static BufferedImage paint(ComponentDefinition definition, SymbolDrawing drawing, int zoom) {
        ComponentType type = SubcircuitTypes.typeOf(definition);
        BufferedImage image = new BufferedImage(IMAGE_SIZE * 2 * zoom, IMAGE_SIZE * 2 * zoom,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.scale(zoom, zoom);
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

    /** The ALU of the editor render: A, B, Op on the left and F (in {@code fColumn}), Cout on the right. */
    private static ComponentDefinition aluDefinition(int fColumn) {
        return new ComponentDefinition("alu", NAME, "", SIZE, List.of(
                new DefinitionPin(PinSpec.input("A", 0, 2, 4), new ElementId("a")),
                new DefinitionPin(PinSpec.input("B", 0, 4, 4), new ElementId("b")),
                new DefinitionPin(PinSpec.input("Op", 0, 6, 2), new ElementId("op")),
                new DefinitionPin(PinSpec.output("F", fColumn, 2, 4), new ElementId("f")),
                new DefinitionPin(PinSpec.output("Cout", 6, 4), new ElementId("cout"))),
                new CircuitFragment(List.of(interfacePin("a", InterfacePins.INPUT_PIN, 4),
                        interfacePin("b", InterfacePins.INPUT_PIN, 4), interfacePin("op", InterfacePins.INPUT_PIN, 2),
                        interfacePin("f", InterfacePins.OUTPUT_PIN, 4),
                        interfacePin("cout", InterfacePins.OUTPUT_PIN, 1)), List.of(), List.of()));
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
