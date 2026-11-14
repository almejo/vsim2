package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.ui.machine.MachineCreation;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The simple face of a state machine: current state in the middle, «S0 → S1» below, in the scheme's orange. */
class MachinePainterTest {

    private static final ComponentDefinition DEFINITION = MachineDefinitions.create("maquina-1", "Máquina 1");
    private static final ComponentType TYPE = SubcircuitTypes.typeOf(DEFINITION);

    private static BufferedImage render(ComponentType type, VisibleState state, ColorScheme scheme) {
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 120, 90);
        graphics.translate(10, 10);
        PainterRegistry.standard().painterFor(type.id()).paint(graphics, type, Map.of(), state, scheme);
        graphics.dispose();
        return image;
    }

    /** Pixels close to the scheme's orange (the text is anti-aliased, so an exact match would miss it). */
    private static int orangePixels(BufferedImage image, ColorScheme scheme) {
        Color orange = scheme.memoryWrite();
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                Color pixel = new Color(image.getRGB(x, y));
                int distance = Math.abs(pixel.getRed() - orange.getRed()) + Math.abs(pixel.getGreen() - orange.getGreen())
                        + Math.abs(pixel.getBlue() - orange.getBlue());
                if (distance < 60) {
                    count++;
                }
            }
        }
        return count;
    }

    private static VisibleState inState(String name, String next) {
        return new VisibleState.MachineVisibleState(name.toLowerCase(), name, Optional.of(next), Optional.empty());
    }

    @Test
    void theUserComponentPainterIsStillTheOneRegisteredForAMachineInstance() {
        assertThat(PainterRegistry.standard().painterFor(TYPE.id())).isInstanceOf(SubcircuitPainter.class);
    }

    @Test
    void thePaletteTypeHasItsOwnIconPainter() {
        assertThat(PainterRegistry.standard().painterFor(MachineCreation.PALETTE_ID))
                .isInstanceOf(MachinePainter.class);
    }

    @Test
    void theCurrentStateIsWrittenInOrangeAndTheNextOneBelow() {
        ColorScheme scheme = ColorSchemes.LIGHT;
        BufferedImage idle = render(TYPE, VisibleState.NONE, scheme);
        BufferedImage running = render(TYPE, inState("S0", "S1"), scheme);
        BufferedImage staying = render(TYPE, inState("S0", "S0"), scheme);

        assertThat(orangePixels(idle, scheme)).isZero();
        assertThat(orangePixels(running, scheme)).isGreaterThan(5);
        assertThat(PainterTesting.differentPixels(idle, running)).isGreaterThan(30);
        assertThat(PainterTesting.differentPixels(running, staying)).isGreaterThan(0);
    }

    @Test
    void theStateNameChangesWhatIsDrawn() {
        ColorScheme scheme = ColorSchemes.LIGHT;

        assertThat(PainterTesting.differentPixels(render(TYPE, inState("Reposo", "Reposo"), scheme),
                render(TYPE, inState("Cuenta", "Cuenta"), scheme))).isGreaterThan(10);
    }

    @Test
    void theOrangeIsReadFromTheSchemeAtPaintTime() {
        VisibleState state = inState("S0", "S1");

        assertThat(orangePixels(render(TYPE, state, ColorSchemes.LIGHT), ColorSchemes.LIGHT)).isGreaterThan(5);
        assertThat(orangePixels(render(TYPE, state, ColorSchemes.DARK), ColorSchemes.DARK)).isGreaterThan(5);
    }

    @Test
    void aDrawnAppearanceStillWins() {
        SymbolDrawing drawing = SymbolTemplates.drawing(SymbolTemplates.Template.RECTANGLE, DEFINITION.size())
                .orElseThrow();
        ComponentType drawn = SubcircuitTypes.typeOf(DEFINITION.withAppearance(Optional.of(drawing)));

        assertThat(orangePixels(render(drawn, inState("S0", "S1"), ColorSchemes.LIGHT), ColorSchemes.LIGHT)).isZero();
    }
}
