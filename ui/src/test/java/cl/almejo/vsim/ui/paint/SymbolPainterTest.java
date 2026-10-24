package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

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

    private static ComponentDefinition definition() {
        return new ComponentDefinition("shape", "Prueba", "", SIZE, List.of(),
                new CircuitFragment(List.of(), List.of(), List.of()));
    }
}
