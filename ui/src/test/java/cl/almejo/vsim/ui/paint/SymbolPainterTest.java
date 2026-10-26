package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolPainterTest {
    private static final GridSize SIZE = new GridSize(6, 4);
    private static final int OFFSET = 64;

    @Test
    void figureOrderDeterminesWhetherAFilledFigureCoversALine() {
        SymbolPath line = SymbolTemplates.line(new SymbolPoint(0, 16), new SymbolPoint(48, 16), false);
        SymbolPath box = SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(40, 24), false);
        int center = OFFSET + 3 * GridPoint.PIXELS_PER_UNIT;
        int middle = OFFSET + 2 * GridPoint.PIXELS_PER_UNIT;
        BufferedImage covered = paint(new SymbolDrawing(SIZE, List.of(line, box), true), ColorSchemes.LIGHT, 0);
        BufferedImage exposed = paint(new SymbolDrawing(SIZE, List.of(box, line), true), ColorSchemes.LIGHT, 0);
        assertThat(new Color(covered.getRGB(center, middle), true)).isEqualTo(ColorSchemes.LIGHT.componentFill());
        assertThat(new Color(exposed.getRGB(center, middle), true)).isEqualTo(ColorSchemes.LIGHT.componentStroke());
    }

    @Test
    void everyTemplateRendersInBothSchemesAndAllRotations() {
        for (SymbolTemplates.Template template : SymbolTemplates.Template.values()) {
            if (template == SymbolTemplates.Template.DEFAULT_BOX) { continue; }
            SymbolDrawing drawing = SymbolTemplates.drawing(template, SIZE).orElseThrow();
            for (ColorScheme scheme : List.of(ColorSchemes.LIGHT, ColorSchemes.DARK)) {
                for (int rotation = 0; rotation < 4; rotation++) {
                    BufferedImage image = paint(drawing, scheme, rotation);
                    int changed = 0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            if (image.getRGB(x, y) != scheme.background().getRGB()) { changed++; }
                        }
                    }
                    assertThat(changed).as("%s at rotation %s", template, rotation).isGreaterThan(10);
                }
            }
        }
    }

    @Test
    void aShapeWithoutCustomDrawingStillUsesTheOriginalPainter() {
        ComponentType type = SubcircuitTypes.typeOf(definition());
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            new SubcircuitPainter().paint(graphics, type, Map.of(), VisibleState.NONE, ColorSchemes.LIGHT);
            assertThat(new Color(image.getRGB(2, 2), true).getAlpha()).isPositive();
        } finally { graphics.dispose(); }
    }

    @Test
    void aFigureOnTheBoxEdgeDoesNotSpillOutsideTheBox() {
        SymbolPath edge = SymbolTemplates.rectangle(new SymbolPoint(0, 0), new SymbolPoint(48, 32), false);
        BufferedImage image = paint(new SymbolDrawing(SIZE, List.of(edge), false), ColorSchemes.LIGHT, 0);
        int right = OFFSET + SIZE.width() * GridPoint.PIXELS_PER_UNIT;
        int bottom = OFFSET + SIZE.height() * GridPoint.PIXELS_PER_UNIT;
        int background = ColorSchemes.LIGHT.background().getRGB();
        for (int step = OFFSET; step <= bottom; step++) {
            assertThat(image.getRGB(OFFSET - 1, step)).as("left of row %s", step).isEqualTo(background);
            assertThat(image.getRGB(right, step)).as("right of row %s", step).isEqualTo(background);
        }
        for (int step = OFFSET; step <= right; step++) {
            assertThat(image.getRGB(step, OFFSET - 1)).as("above column %s", step).isEqualTo(background);
            assertThat(image.getRGB(step, bottom)).as("below column %s", step).isEqualTo(background);
        }
        assertThat(image.getRGB(OFFSET + 1, OFFSET + 20)).isNotEqualTo(background);
    }

    @Test
    void theDefaultBoxDoesNotNameThePinsOnItsTopAndBottomEdges() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        ComponentDefinition withPin = inverter.withGeometry(inverter.size(), List.of(
                new DefinitionPin(PinSpec.input("T", 1, 4), inverter.pinout().get(0).source()),
                inverter.pinout().get(1)), Optional.empty());
        BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.translate(OFFSET, OFFSET);
            new SubcircuitPainter().paint(graphics, SubcircuitTypes.typeOf(withPin), Map.of(), VisibleState.NONE,
                    ColorSchemes.LIGHT);
        } finally { graphics.dispose(); }
        int textColor = ColorSchemes.LIGHT.text().getRGB();
        int unit = GridPoint.PIXELS_PER_UNIT;
        for (int y = OFFSET + 3 * unit; y < OFFSET + 4 * unit - 2; y++) {
            for (int x = OFFSET + 2; x < OFFSET + 2 * unit; x++) {
                assertThat(image.getRGB(x, y)).as("pixel %s,%s", x, y).isNotEqualTo(textColor);
            }
        }
    }

    @Test
    void turnedAQuarterPinNamesShrinkToTheirSlotAndNeverRunIntoTheirNeighbours() {
        ComponentDefinition definition = new ComponentDefinition("shape", "Prueba", "", SIZE, List.of(
                new DefinitionPin(PinSpec.output("F[4]", 6, 1), new ElementId("f")),
                new DefinitionPin(PinSpec.output("Cout", 6, 2), new ElementId("cout"))),
                new CircuitFragment(List.of(
                        new Component(new ElementId("f"), "output-pin",
                                new GridPoint(0, 0), Rotation.R0, Map.of(), "f"),
                        new Component(new ElementId("cout"), "output-pin",
                                new GridPoint(0, 0), Rotation.R0, Map.of(), "cout")),
                        List.of(), List.of()));
        SymbolDrawing named = new SymbolDrawing(SIZE, List.of(), true);
        SymbolDrawing silent = new SymbolDrawing(SIZE, List.of(), false);
        boolean[] columns = new boolean[192];
        BufferedImage withNames = paintQuarterTurned(definition, named);
        BufferedImage without = paintQuarterTurned(definition, silent);
        for (int y = 0; y < withNames.getHeight(); y++) {
            for (int x = 0; x < withNames.getWidth(); x++) {
                columns[x] |= withNames.getRGB(x, y) != without.getRGB(x, y);
            }
        }
        int unit = GridPoint.PIXELS_PER_UNIT;
        int boundary = OFFSET + (SIZE.height() - 2) * unit + unit / 2;
        int slot = unit;
        assertThat(extent(columns, 0, boundary)).as("Cout").isBetween(1, slot);
        assertThat(extent(columns, boundary, columns.length)).as("F[4]").isBetween(1, slot);
    }

    private static int extent(boolean[] columns, int from, int until) {
        int first = -1;
        int last = -1;
        for (int column = from; column < until; column++) {
            if (columns[column]) {
                if (first < 0) { first = column; }
                last = column;
            }
        }
        return first < 0 ? 0 : last - first + 1;
    }

    @Test
    void aColouredFigureIsFilledAndOutlinedInThePaletteAndAPlainOneKeepsTheSchemesColours() {
        SymbolPath plain = SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(40, 24), false);
        SymbolPath coloured = plain.coloured(NoteColor.GREEN, NoteColor.PURPLE);
        int centerX = OFFSET + 3 * GridPoint.PIXELS_PER_UNIT;
        int centerY = OFFSET + 2 * GridPoint.PIXELS_PER_UNIT;
        int edgeY = OFFSET + GridPoint.PIXELS_PER_UNIT - 1;

        BufferedImage image = paint(new SymbolDrawing(SIZE, List.of(coloured), true), ColorSchemes.LIGHT, 0);
        BufferedImage untouched = paint(new SymbolDrawing(SIZE, List.of(plain), true), ColorSchemes.LIGHT, 0);

        assertThat(new Color(image.getRGB(centerX, centerY), true))
                .isEqualTo(ColorSchemes.LIGHT.noteBackground(NoteColor.GREEN).orElseThrow());
        assertThat(new Color(image.getRGB(centerX, edgeY), true))
                .isEqualTo(ColorSchemes.LIGHT.noteBorder(NoteColor.PURPLE).orElseThrow());
        assertThat(new Color(untouched.getRGB(centerX, centerY), true)).isEqualTo(ColorSchemes.LIGHT.componentFill());
    }

    @Test
    void whenPrintingInBlackAndWhiteTheColoursAreLeftOut() {
        SymbolPath coloured = SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(40, 24), false)
                .coloured(NoteColor.GREEN, NoteColor.PURPLE);
        SymbolText text = new SymbolText(new SymbolPoint(24, 16), Optional.of("Hola"), TextSize.MEDIUM,
                TextAlignment.CENTER, NoteColor.BLUE, NoteColor.NONE);
        ColorScheme paper = MonochromeScheme.of(ColorSchemes.LIGHT);
        SymbolDrawing painted = new SymbolDrawing(SIZE, List.of(coloured, text), true);
        SymbolDrawing plain = new SymbolDrawing(SIZE, List.of(
                SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(40, 24), false),
                new SymbolText(new SymbolPoint(24, 16), Optional.of("Hola"), TextSize.MEDIUM, TextAlignment.CENTER)),
                true);

        BufferedImage withColours = paint(painted, paper, 0);
        BufferedImage without = paint(plain, paper, 0);

        for (int y = 0; y < withColours.getHeight(); y++) {
            for (int x = 0; x < withColours.getWidth(); x++) {
                assertThat(withColours.getRGB(x, y)).as("pixel %d,%d", x, y).isEqualTo(without.getRGB(x, y));
            }
        }
    }

    @Test
    void aTextBorderIsDrawnAroundTheTextBlock() {
        SymbolText framed = new SymbolText(new SymbolPoint(24, 16), Optional.of("uno\ndos"), TextSize.SMALL,
                TextAlignment.CENTER, NoteColor.NONE, NoteColor.BLUE);
        SymbolText bare = new SymbolText(new SymbolPoint(24, 16), Optional.of("uno\ndos"), TextSize.SMALL,
                TextAlignment.CENTER);

        BufferedImage with = paint(new SymbolDrawing(SIZE, List.of(framed), true), ColorSchemes.LIGHT, 0);
        BufferedImage without = paint(new SymbolDrawing(SIZE, List.of(bare), true), ColorSchemes.LIGHT, 0);

        Color border = ColorSchemes.LIGHT.noteBorder(NoteColor.BLUE).orElseThrow();
        int bordered = 0;
        int plain = 0;
        for (int y = 0; y < with.getHeight(); y++) {
            for (int x = 0; x < with.getWidth(); x++) {
                bordered += with.getRGB(x, y) == border.getRGB() ? 1 : 0;
                plain += without.getRGB(x, y) == border.getRGB() ? 1 : 0;
            }
        }
        assertThat(bordered).isGreaterThan(20);
        assertThat(plain).isZero();
    }

    private static BufferedImage paintQuarterTurned(ComponentDefinition definition, SymbolDrawing drawing) {
        BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.translate(OFFSET + SIZE.height() * GridPoint.PIXELS_PER_UNIT, OFFSET);
            graphics.rotate(Math.PI / 2);
            SymbolPainter.paint(graphics, SubcircuitTypes.typeOf(definition), drawing, ColorSchemes.LIGHT, "Prueba");
            return image;
        } finally { graphics.dispose(); }
    }

    private static BufferedImage paint(SymbolDrawing drawing, ColorScheme scheme, int rotation) {
        BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(scheme.background()); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.translate(OFFSET, OFFSET); graphics.rotate(rotation * Math.PI / 2);
            SymbolPainter.paint(graphics, SubcircuitTypes.typeOf(definition()), drawing, scheme, "Prueba");
            return image;
        } finally { graphics.dispose(); }
    }

    /** The rows and columns, as {left, top, right, bottom}, holding pixels unlike the background. */
    private static int[] inkBounds(BufferedImage image) {
        int background = ColorSchemes.LIGHT.background().getRGB();
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background) {
                    bounds[0] = Math.min(bounds[0], x);
                    bounds[1] = Math.min(bounds[1], y);
                    bounds[2] = Math.max(bounds[2], x);
                    bounds[3] = Math.max(bounds[3], y);
                }
            }
        }
        return bounds;
    }

    private static int[] textInk(String content, TextSize size, GridSize figure) {
        SymbolText text = new SymbolText(new SymbolPoint(24, 16), Optional.of(content), size, TextAlignment.CENTER);
        BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(ColorSchemes.LIGHT.background());
            graphics.fillRect(0, 0, 192, 192);
            graphics.translate(OFFSET, OFFSET);
            SymbolPainter.paint(graphics, SubcircuitTypes.typeOf(definition()),
                    new SymbolDrawing(figure, List.of(text), true), ColorSchemes.LIGHT, "Prueba");
        } finally { graphics.dispose(); }
        return inkBounds(image);
    }

    @Test
    void anExplicitTextKeepsItsChosenSizeWhateverItsLengthOrTheFigure() {
        for (TextSize size : TextSize.values()) {
            int[] short1 = textInk("H", size, SIZE);
            int[] longer = textInk("HHHHHHHHHHHHHHHHHHHH", size, SIZE);
            int[] bigFigure = textInk("H", size, new GridSize(12, 8));
            int[] smallFigure = textInk("H", size, new GridSize(3, 2));

            assertThat(longer[3] - longer[1]).as("height with %s", size).isEqualTo(short1[3] - short1[1]);
            assertThat(bigFigure[3] - bigFigure[1]).isEqualTo(short1[3] - short1[1]);
            assertThat(smallFigure[3] - smallFigure[1]).isEqualTo(short1[3] - short1[1]);
        }
        assertThat(textInk("H", TextSize.SMALL, SIZE)[3] - textInk("H", TextSize.SMALL, SIZE)[1])
                .isLessThan(textInk("H", TextSize.MEDIUM, SIZE)[3] - textInk("H", TextSize.MEDIUM, SIZE)[1]);
        assertThat(textInk("H", TextSize.MEDIUM, SIZE)[3] - textInk("H", TextSize.MEDIUM, SIZE)[1])
                .isLessThan(textInk("H", TextSize.LARGE, SIZE)[3] - textInk("H", TextSize.LARGE, SIZE)[1]);
    }

    @Test
    void anExplicitTextLongerThanTheFigureOverflowsItInsteadOfShrinking() {
        int[] ink = textInk("HHHHHHHHHHHHHHHHHHHH", TextSize.MEDIUM, SIZE);

        assertThat(ink[0]).as("starts left of the figure").isLessThan(OFFSET);
        assertThat(ink[2]).as("ends right of the figure").isGreaterThan(OFFSET + SIZE.width() * GridPoint.PIXELS_PER_UNIT);
    }

    @Test
    void theComponentNameTextStillFitsItsFigure() {
        SymbolText follows = new SymbolText(new SymbolPoint(24, 16), Optional.empty(), TextSize.LARGE,
                TextAlignment.CENTER);
        SymbolPath box = SymbolTemplates.rectangle(new SymbolPoint(0, 0), new SymbolPoint(48, 32), false);
        BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(ColorSchemes.LIGHT.background());
            graphics.fillRect(0, 0, 192, 192);
            graphics.translate(OFFSET, OFFSET);
            SymbolPainter.paint(graphics, SubcircuitTypes.typeOf(definition()),
                    new SymbolDrawing(SIZE, List.of(box, follows), true), ColorSchemes.LIGHT,
                    "UnNombreMuyMuyLargoDeComponente");
        } finally { graphics.dispose(); }

        int[] ink = inkBounds(image);
        assertThat(ink[0]).isGreaterThanOrEqualTo(OFFSET - 2);
        assertThat(ink[2]).isLessThanOrEqualTo(OFFSET + 48 + 2);
    }

    private static ComponentDefinition definition() {
        return new ComponentDefinition("shape", "Prueba", "", SIZE, List.of(),
                new CircuitFragment(List.of(), List.of(), List.of()));
    }
}
