package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.machine.MachineColors;
import cl.almejo.vsim.ui.machine.MachineCreation;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.paint.FaceMachines.arrow;
import static cl.almejo.vsim.ui.paint.FaceMachines.withStates;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The face of a state machine: up to four states its miniature with the active state lit and the next arrow in the
 * active colour, «S0 → S1» below; from five the current state's name, big, and «próximo → S5».
 */
class MachinePainterTest {

    private static final ComponentDefinition DEFINITION = MachineDefinitions.create("maquina-1", "Máquina 1");
    private static final ComponentType TYPE = SubcircuitTypes.typeOf(DEFINITION);

    /** Four states in a ring, S0 → S1 → S2 → S3 → S0. */
    private static final StateMachine SQUARE = withStates(4, arrow("t1", "s0", "s1"), arrow("t2", "s1", "s2"),
            arrow("t3", "s2", "s3"), arrow("t4", "s3", "s0"));
    private static final int IMAGE_SIZE = 130;
    private static final int MARGIN = 10;

    private static BufferedImage render(ComponentType type, VisibleState state, ColorScheme scheme) {
        return render(type, state, scheme, false);
    }

    /** {@code type} drawn at 100 %, turned a quarter (R90) when {@code turned}. */
    private static BufferedImage render(ComponentType type, VisibleState state, ColorScheme scheme, boolean turned) {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        if (turned) {
            graphics.translate(MARGIN + type.size().height() * PainterSupport.UNIT, MARGIN);
            graphics.rotate(Math.PI / 2);
        } else {
            graphics.translate(MARGIN, MARGIN);
        }
        PainterRegistry.standard().painterFor(type.id()).paint(graphics, type, Map.of(), state, scheme);
        graphics.dispose();
        return image;
    }

    private static ComponentType typeOf(StateMachine machine) {
        return SubcircuitTypes.typeOf(DEFINITION.withMachine(machine));
    }

    private static VisibleState inState(String stateId, Optional<String> nextStateId,
                                        Optional<String> nextTransitionId) {
        return new VisibleState.MachineVisibleState(stateId, stateId.toUpperCase(), nextStateId, nextTransitionId);
    }

    /** Pixels close to the scheme's orange (the text is anti-aliased, so an exact match would miss it). */
    private static int orangePixels(BufferedImage image, ColorScheme scheme) {
        Color orange = MachineColors.active(scheme);
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
    void theNextArrowIsPaintedInTheActiveColour() {
        ComponentType type = typeOf(SQUARE);
        BufferedImage held = render(type, inState("s0", Optional.empty(), Optional.empty()), ColorSchemes.LIGHT);
        BufferedImage going = render(type, inState("s0", Optional.of("s1"), Optional.of("t1")), ColorSchemes.LIGHT);

        assertThat(orangePixels(held, ColorSchemes.LIGHT)).as("the lit state").isGreaterThan(5);
        assertThat(orangePixels(going, ColorSchemes.LIGHT)).as("the lit state and the next arrow")
                .isGreaterThan(orangePixels(held, ColorSchemes.LIGHT) + 10);
    }

    @Test
    void theLitStateIsTheActiveOne() {
        ComponentType type = typeOf(SQUARE);

        assertThat(PainterTesting.differentPixels(
                render(type, inState("s0", Optional.empty(), Optional.empty()), ColorSchemes.LIGHT),
                render(type, inState("s2", Optional.empty(), Optional.empty()), ColorSchemes.LIGHT)))
                .isGreaterThan(30);
    }

    @Test
    void fromFiveStatesTheCurrentNameIsWrittenBigInTheActiveColour() {
        ComponentType four = typeOf(SQUARE);
        ComponentType five = typeOf(withStates(5));
        VisibleState state = inState("s3", Optional.of("s4"), Optional.empty());

        BufferedImage bigName = render(five, state, ColorSchemes.LIGHT);

        assertThat(orangePixels(bigName, ColorSchemes.LIGHT)).isGreaterThan(30);
        assertThat(PainterTesting.differentPixels(render(four, state, ColorSchemes.LIGHT), bigName))
                .isGreaterThan(30);
    }

    @Test
    void thePieceHasTheSameSizeWithFourStatesAndWithFive() {
        assertThat(typeOf(withStates(5)).size()).isEqualTo(typeOf(SQUARE).size());
    }

    @Test
    void withNoStateNothingIsLit() {
        assertThat(orangePixels(render(typeOf(SQUARE), VisibleState.NONE, ColorSchemes.LIGHT), ColorSchemes.LIGHT))
                .isZero();
        assertThat(orangePixels(render(typeOf(withStates(5)), VisibleState.NONE, ColorSchemes.LIGHT),
                ColorSchemes.LIGHT)).isZero();
    }

    @Test
    void aTurnedPieceStillDrawsItsFaceInsideItsBox() {
        ComponentType type = typeOf(SQUARE);
        VisibleState state = inState("s0", Optional.of("s1"), Optional.of("t1"));

        BufferedImage turned = render(type, state, ColorSchemes.LIGHT, true);

        assertThat(orangePixels(turned, ColorSchemes.LIGHT)).isGreaterThan(15);
        assertThat(PainterTesting.differentPixels(render(type, state, ColorSchemes.LIGHT), turned)).isGreaterThan(30);
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

    @Test
    void aBusPinIsWrittenWithItsBitsAndEveryOtherPinAsBefore() {
        StateMachine machine = StateMachine.initial().withPinNames(List.of("x", "y"), List.of("Z", "W", "V"))
                .withBus(true, true);
        List<PinSpec> pins = MachineDefinitions.create("m", "M").withMachine(machine).pinout().stream()
                .map(DefinitionPin::spec).toList();

        assertThat(pins).extracting(pin -> MachinePainter.pinText(machine, pin))
                .containsExactly("in[1:0]", "RST", "CLK", "out[2:0]");
        StateMachine one = machine.withPinNames(List.of("x"), List.of("Z"));
        assertThat(MachinePainter.pinText(one, PinSpec.input(MachineBody.BUS_INPUT_NAME, 0, 2, 1)))
                .isEqualTo("in[0]");
        StateMachine plain = one.withBus(false, false);
        assertThat(MachinePainter.pinText(plain, PinSpec.input("x", 0, 2, 1))).isEqualTo("x");
        assertThat(SubcircuitPainter.pinText(PinSpec.input("dato", 0, 2, 8))).as("other components")
                .isEqualTo("dato[8]");
    }

    @Test
    void aTrafficLightWithItsOutputsAsABusHasAMiniatureAsBigAsWithoutTheBus() {
        StateMachine lights = withStates(3, arrow("t1", "s0", "s1"), arrow("t2", "s1", "s2"),
                arrow("t3", "s2", "s0")).withPinNames(List.of(), List.of("V", "A", "R"));
        StateMachine asBus = lights.withBus(false, true);
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            Rectangle2D plain = MachinePainter.miniatureArea(graphics, typeOf(lights), lights);
            Rectangle2D bus = MachinePainter.miniatureArea(graphics, typeOf(asBus), asBus);

            assertThat(typeOf(asBus).size()).isEqualTo(typeOf(lights).size());
            assertThat(bus).as("«out[2:0]» is wider than «V», but it sits beside the title, clear of the states")
                    .isEqualTo(plain);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void aStateWithAColourIsFilledWithItInTheMiniature() {
        StateMachine coloured = SQUARE.withState("s2",
                state -> state.withColor(Optional.of(cl.almejo.vsim.core.machine.StateColor.BLUE)));

        assertThat(PainterTesting.differentPixels(render(typeOf(SQUARE), VisibleState.NONE, ColorSchemes.LIGHT),
                render(typeOf(coloured), VisibleState.NONE, ColorSchemes.LIGHT))).as("the blue circle")
                .isGreaterThan(20);
    }
}
