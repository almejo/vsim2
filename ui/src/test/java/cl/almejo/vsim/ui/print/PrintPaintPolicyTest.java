package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.MonochromeScheme;
import cl.almejo.vsim.ui.paint.WireState;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.frame;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The print paint policy: frame titles always, no frame buttons, and «Blanco y negro» without any colour. */
class PrintPaintPolicyTest {

    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;
    /** Antialiasing between two greys can round one channel apart from the others by a unit or two. */
    private static final int GREY_TOLERANCE = 2;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Map<String, Value> netValues = new HashMap<>();
    private final Map<String, VisibleState> componentStates = new HashMap<>();

    private Component add(String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.add(component);
        return component;
    }

    private BufferedImage render(ColorScheme scheme, PaintPolicy policy, double scale, int width, int height,
                                 boolean withValues) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        long snapshotToken = withValues ? document.revision() : document.revision() + 1;
        SimulationSnapshot snapshot = new SimulationSnapshot(1, snapshotToken, 0, true, netValues, componentStates,
                List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(policy.monochrome() ? Color.WHITE : scheme.background());
        graphics.fillRect(0, 0, width, height);
        graphics.scale(scale, scale);
        new CircuitRenderer(registry, PainterRegistry.standard(), scheme).paint(graphics,
                new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                        RenderState.NO_PROBLEM_GLOW, Set.of(), policy));
        graphics.dispose();
        return image;
    }

    private void value(Wire wire, Value value) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        netValues.put(netlist.netForWire(wire.id()).orElseThrow().id().value(), value);
    }

    private static int darkPixels(BufferedImage image, int fromX, int fromY, int toX, int toY) {
        int count = 0;
        for (int x = fromX; x < toX; x++) {
            for (int y = fromY; y < toY; y++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() + color.getGreen() + color.getBlue() < 3 * 128) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isGrey(int rgb) {
        Color color = new Color(rgb, true);
        return Math.abs(color.getRed() - color.getGreen()) <= GREY_TOLERANCE
                && Math.abs(color.getGreen() - color.getBlue()) <= GREY_TOLERANCE
                && Math.abs(color.getRed() - color.getBlue()) <= GREY_TOLERANCE;
    }

    private static int colouredPixels(BufferedImage image) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (!isGrey(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void everyPolicyPaintsTheBusValuesUntilToldNotTo() {
        assertThat(PaintPolicy.CANVAS.paintBusValues()).isTrue();
        assertThat(PaintPolicy.IMAGE.paintBusValues()).isTrue();
        assertThat(PaintPolicy.print(false).paintBusValues()).isTrue();
        assertThat(new PaintPolicy(false, true, false, 0).paintBusValues()).isTrue();
        PaintPolicy hidden = PaintPolicy.print(true).withBusValues(false).atPageScale(2).withStickyNotes(true);
        assertThat(hidden.paintBusValues()).isFalse();
        assertThat(hidden.withBusValues(true).paintBusValues()).isTrue();
    }

    @Test
    void theCanvasPolicyIsTodaysBehaviourAndEveryOldConstructorUsesIt() {
        assertThat(PaintPolicy.CANVAS.alwaysShowFrameTitles()).isFalse();
        assertThat(PaintPolicy.CANVAS.paintFrameButtons()).isTrue();
        assertThat(PaintPolicy.CANVAS.monochrome()).isFalse();
        assertThat(PaintPolicy.CANVAS.minimumStrokePixels()).isZero();
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        assertThat(new RenderState(document, netlist, 0, SimulationSnapshot.EMPTY, Set.of(), 0).policy())
                .isEqualTo(PaintPolicy.CANVAS);
        assertThat(new RenderState(document, netlist, 0, SimulationSnapshot.EMPTY, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW).policy()).isEqualTo(PaintPolicy.CANVAS);
        assertThat(new RenderState(document, netlist, 0, SimulationSnapshot.EMPTY, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of()).policy()).isEqualTo(PaintPolicy.CANVAS);
    }

    @Test
    void thePrintPolicyShowsTitlesNeverButtonsAndScalesItsMinimumStrokeWithThePage() {
        PaintPolicy colour = PaintPolicy.print(false);
        PaintPolicy blackAndWhite = PaintPolicy.print(true);
        assertThat(colour.alwaysShowFrameTitles()).isTrue();
        assertThat(colour.paintFrameButtons()).isFalse();
        assertThat(colour.monochrome()).isFalse();
        assertThat(blackAndWhite.monochrome()).isTrue();
        assertThat(colour.minimumStrokePixels()).isEqualTo(PaintPolicy.PAPER_MINIMUM_STROKE_POINTS);
        assertThat(colour.atPageScale(0.25).minimumStrokePixels())
                .isEqualTo(PaintPolicy.PAPER_MINIMUM_STROKE_POINTS / 0.25);
        assertThatThrownBy(() -> colour.atPageScale(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaintPolicy(true, false, false, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePrintPolicyPaintsAFrameTitleAtAScaleWhereTheCanvasHidesIt() {
        frame(document, 0, 0, 60, 20, "WWWWWWWWWW", FrameTint.NONE);
        // 2 screen pixels per grid unit: below the canvas' 3 px threshold for titles
        double scale = 2.0 / UNIT;
        BufferedImage canvas = render(ColorScheme.LIGHT, PaintPolicy.CANVAS, scale, 130, 50, true);
        BufferedImage print = render(ColorScheme.LIGHT, PaintPolicy.print(false), scale, 130, 50, true);
        // the title band is 2 grid units high: 4 pixels; skip the border column and row
        assertThat(darkPixels(canvas, 2, 1, 100, 3)).as("canvas hides the title").isZero();
        assertThat(darkPixels(print, 2, 1, 100, 3)).as("print paints the title").isPositive();
    }

    @Test
    void onPaperTheTitleTakesTheRoomTheButtonsWouldHaveKept() {
        // 6 grid units = 48 px: 38 px for the title without the buttons, 8 px with them
        frame(document, 1, 1, 6, 6, "WW", FrameTint.NONE);
        BufferedImage canvas = render(ColorScheme.LIGHT, PaintPolicy.CANVAS, 1, 80, 80, true);
        BufferedImage print = render(ColorScheme.LIGHT, PaintPolicy.print(false), 1, 80, 80, true);
        assertThat(darkPixels(canvas, UNIT + 2, UNIT + 2, 7 * UNIT - 2, 3 * UNIT - 2)).isZero();
        assertThat(darkPixels(print, UNIT + 2, UNIT + 2, 7 * UNIT - 2, 3 * UNIT - 2)).isPositive();
    }

    /** Tinted frames, both tunnels, a display, a lit LED, a switch, a bus and a wire in every state. */
    private void colourfulCircuit() {
        frame(document, 0, 0, 70, 40, "Rojo", FrameTint.RED);
        frame(document, 2, 4, 30, 30, "Azul", FrameTint.BLUE);
        frame(document, 36, 4, 30, 30, "", FrameTint.YELLOW);
        add(StandardComponentTypes.SWITCH, 4, 6, Map.of("value", "1"));
        Component led = add(StandardComponentTypes.LED, 12, 6, Map.of("color", "green"));
        componentStates.put(led.id().value(), new VisibleState.LedState(Value.ONE));
        Component display = add(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 6, Map.of());
        componentStates.put(display.id().value(), new VisibleState.SevenSegmentsState(Value.of(4, 8)));
        add(TunnelKind.ENTRANCE.typeId(), 40, 6, Map.of(Tunnels.NAME, "a", Tunnels.COLOR, 1));
        add(TunnelKind.EXIT.typeId(), 52, 6, Map.of(Tunnels.NAME, "a"));
        value(wire(document, 4, 20, 20, 20), Value.ONE);
        value(wire(document, 4, 22, 20, 22), Value.ZERO);
        value(wire(document, 4, 24, 20, 24), Value.HIGH_IMPEDANCE);
        value(wire(document, 4, 26, 20, 26), new Value(2, 1, 2, false));
        value(wire(document, 4, 28, 20, 28), Value.ONE.withConflict());
        value(wire(document, 40, 20, 60, 20), Value.of(8, 0x5A));
    }

    @Test
    void blackAndWhiteLeavesNoColourWithTintedFramesTunnelsAndDisplays() {
        colourfulCircuit();
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            BufferedImage colour = render(scheme, PaintPolicy.print(false), 1, 600, 340, true);
            BufferedImage blackAndWhite = render(scheme, PaintPolicy.print(true), 1, 600, 340, true);
            assertThat(colouredPixels(colour)).as("the colour print of %s has colour", id).isPositive();
            assertThat(colouredPixels(blackAndWhite)).as("no colour in black and white (%s)", id).isZero();
        }
    }

    @Test
    void inBlackAndWhiteALitLedIsFilledDarkAndAnUnlitOneIsOnlyItsOutline() {
        for (String colour : List.of("green", "red", "yellow", "white", "blue")) {
            Component lit = add(StandardComponentTypes.LED, 2, 2, Map.of("color", colour));
            componentStates.put(lit.id().value(), new VisibleState.LedState(Value.ONE));
            Component unlit = add(StandardComponentTypes.LED, 8, 2, Map.of("color", colour));
            componentStates.put(unlit.id().value(), new VisibleState.LedState(Value.ZERO));
            BufferedImage image = render(ColorScheme.LIGHT, PaintPolicy.print(true), 1, 120, 60, true);
            // the middle of the lens, below its highlight
            int litLevel = new Color(image.getRGB(2 * UNIT + 8, 2 * UNIT + 11)).getRed();
            int unlitLevel = new Color(image.getRGB(8 * UNIT + 8, 2 * UNIT + 11)).getRed();
            assertThat(litLevel).as("lit %s", colour).isLessThan(60);
            assertThat(unlitLevel).as("unlit %s", colour).isGreaterThan(200);
            document.remove(lit.id());
            document.remove(unlit.id());
        }
    }

    @Test
    void inBlackAndWhiteAFrameHasNoFillOnlyItsBorderAndTitle() {
        frame(document, 0, 0, 40, 30, "Marco", FrameTint.PURPLE);
        BufferedImage image = render(ColorScheme.LIGHT, PaintPolicy.print(true), 1, 340, 260, true);
        assertThat(image.getRGB(20 * UNIT, 15 * UNIT)).as("interior").isEqualTo(Color.WHITE.getRGB());
        assertThat(image.getRGB(30 * UNIT, UNIT - 3)).as("title band, right of the title")
                .isEqualTo(Color.WHITE.getRGB());
        // the 1 px border straddles two pixel rows; together they hold its dark grey
        int left = new Color(image.getRGB(20 * UNIT, 30 * UNIT - 1)).getRed();
        int right = new Color(image.getRGB(20 * UNIT, 30 * UNIT)).getRed();
        assertThat((255 - left) + (255 - right)).as("border ink")
                .isGreaterThan(255 - MonochromeScheme.FRAME_BORDER.getRed() - 20);
        assertThat(darkPixels(image, 4, 2, 10 * UNIT, 2 * UNIT - 2)).as("title").isPositive();
    }

    @Test
    void wireStatesAreToldApartByGreyLevelAndPattern() {
        MonochromeScheme.WireLook one = MonochromeScheme.monochromeLook(WireState.ONE, false);
        MonochromeScheme.WireLook zero = MonochromeScheme.monochromeLook(WireState.ZERO, false);
        MonochromeScheme.WireLook highImpedance = MonochromeScheme.monochromeLook(WireState.HIGH_IMPEDANCE, false);
        MonochromeScheme.WireLook unknown = MonochromeScheme.monochromeLook(WireState.UNKNOWN, false);
        MonochromeScheme.WireLook conflict = MonochromeScheme.monochromeLook(WireState.CONFLICT, false);
        assertThat(one.color()).isEqualTo(Color.BLACK);
        assertThat(one.stroke().getDashArray()).isNull();
        assertThat(zero.color().getRed()).isBetween(100, 160);
        assertThat(zero.stroke().getDashArray()).isNull();
        assertThat(highImpedance.color().getRed()).isGreaterThan(zero.color().getRed());
        assertThat(highImpedance.stroke().getDashArray()).isNotNull();
        assertThat(unknown.color()).isEqualTo(highImpedance.color());
        assertThat(unknown.stroke().getDashArray()).isNotNull().isNotEqualTo(highImpedance.stroke().getDashArray());
        assertThat(conflict.color()).isEqualTo(Color.BLACK);
        assertThat(conflict.stroke().getDashArray()).isNotNull();
        assertThat(conflict.stroke().getLineWidth()).isGreaterThan(one.stroke().getLineWidth());
        for (WireState state : WireState.values()) {
            BasicStroke single = MonochromeScheme.monochromeLook(state, false).stroke();
            BasicStroke bus = MonochromeScheme.monochromeLook(state, true).stroke();
            assertThat(bus.getLineWidth()).as("%s bus", state).isGreaterThanOrEqualTo(single.getLineWidth());
            assertThat(isGrey(MonochromeScheme.monochromeLook(state, false).color().getRGB())).isTrue();
        }
        assertThat(MonochromeScheme.monochromeLook(WireState.ONE, true).stroke().getLineWidth())
                .isGreaterThan(one.stroke().getLineWidth());
    }

    @Test
    void aOneWireIsBlackAndAZeroWireMidGreyOnPaper() {
        value(wire(document, 2, 4, 20, 4), Value.ONE);
        value(wire(document, 2, 8, 20, 8), Value.ZERO);
        BufferedImage image = render(ColorScheme.LIGHT, PaintPolicy.print(true), 1, 200, 100, true);
        assertThat(new Color(image.getRGB(10 * UNIT, 4 * UNIT))).isEqualTo(Color.BLACK);
        assertThat(new Color(image.getRGB(10 * UNIT, 8 * UNIT)))
                .isEqualTo(MonochromeScheme.monochromeLook(WireState.ZERO, false).color());
    }

    @Test
    void withoutValuesBlackAndWhiteWiresArePlainBlackLines() {
        wire(document, 2, 4, 20, 4);
        BufferedImage image = render(ColorScheme.LIGHT, PaintPolicy.print(true), 1, 200, 100, false);
        for (int x = 3 * UNIT; x < 19 * UNIT; x++) {
            assertThat(new Color(image.getRGB(x, 4 * UNIT))).as("x %d", x).isEqualTo(Color.BLACK);
        }
    }

    @Test
    void theMinimumStrokeWidensThinWires() {
        value(wire(document, 2, 4, 20, 4), Value.ONE);
        PaintPolicy wide = new PaintPolicy(true, false, false, 8);
        BufferedImage canvas = render(ColorScheme.LIGHT, PaintPolicy.CANVAS, 1, 200, 100, true);
        BufferedImage widened = render(ColorScheme.LIGHT, wide, 1, 200, 100, true);
        int pixelOffWire = 4 * UNIT + 3;
        assertThat(canvas.getRGB(10 * UNIT, pixelOffWire)).isEqualTo(ColorScheme.LIGHT.background().getRGB());
        assertThat(widened.getRGB(10 * UNIT, pixelOffWire)).isEqualTo(ColorScheme.LIGHT.wireOne().getRGB());
    }

    private static BufferedImage paintImage(CircuitImage image, Optional<PaintPolicy> policy) {
        BufferedImage result = new BufferedImage(image.width(1), image.height(1), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        if (policy.isPresent()) {
            image.paint(graphics, 1, policy.get());
        } else {
            image.paint(graphics, 1);
        }
        graphics.dispose();
        return result;
    }

    @Test
    void theImageExportKeepsTheCanvasPolicyAndBlackAndWhitePaintsOnWhite() {
        colourfulCircuit();
        ColorScheme dark = ColorSchemes.ids().stream().map(id -> ColorSchemes.byId(id).orElseThrow())
                .filter(ColorScheme::isDark).findFirst().orElseThrow();
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, netValues,
                componentStates, List.of(), Optional.empty());
        CircuitImage image = CircuitImage.of(document, netlist, document.revision(), snapshot, registry,
                PainterRegistry.standard(), dark, Optional.empty()).orElseThrow();
        BufferedImage plain = paintImage(image, Optional.empty());
        BufferedImage canvas = paintImage(image, Optional.of(PaintPolicy.CANVAS));
        for (int x = 0; x < plain.getWidth(); x++) {
            for (int y = 0; y < plain.getHeight(); y++) {
                assertThat(canvas.getRGB(x, y)).isEqualTo(plain.getRGB(x, y));
            }
        }
        BufferedImage blackAndWhite = paintImage(image, Optional.of(PaintPolicy.print(true)));
        assertThat(blackAndWhite.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
        assertThat(colouredPixels(blackAndWhite)).isZero();
    }

    @Test
    void theMonochromeSchemeIsGreyOnWhiteWhateverTheScreenScheme() {
        for (String id : ColorSchemes.ids()) {
            ColorScheme scheme = MonochromeScheme.of(ColorSchemes.byId(id).orElseThrow());
            assertThat(scheme.background()).isEqualTo(Color.WHITE);
            assertThat(scheme.isDark()).isFalse();
            for (cl.almejo.vsim.ui.paint.ColorRole role : cl.almejo.vsim.ui.paint.ColorRole.values()) {
                assertThat(isGrey(role.get(scheme).getRGB())).as("%s in %s", role, id).isTrue();
            }
        }
    }
}
