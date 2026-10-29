package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitRendererTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private final CircuitDocument document = new CircuitDocument();
    private final Wire wire = new Wire(document.newWireId(), new GridPoint(1, 1), new GridPoint(5, 1));

    private BufferedImage render(SimulationSnapshot snapshot, Netlist netlist, long token) {
        return renderWith(renderer, snapshot, netlist, token);
    }

    private BufferedImage renderWith(CircuitRenderer painter, SimulationSnapshot snapshot, Netlist netlist,
                                     long token) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        painter.paint(graphics, new RenderState(document, netlist, token, snapshot, Set.of(), 0));
        graphics.dispose();
        return image;
    }

    private BufferedImage renderSelected(Set<ElementId> selection, double phase, SimulationSnapshot snapshot) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, selection, phase));
        graphics.dispose();
        return image;
    }

    private static boolean sameImage(BufferedImage first, BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void paintsOnlyTheComponentsTheClipCanShow() {
        PainterRegistry painters = PainterRegistry.standard();
        List<GridPoint> painted = new ArrayList<>();
        painters.register(StandardComponentTypes.BUFFER, (graphics, type, parameters, state, scheme) ->
                painted.add(new GridPoint((int) graphics.getTransform().getTranslateX(),
                        (int) graphics.getTransform().getTranslateY())));
        CircuitRenderer counting = new CircuitRenderer(registry, painters, SCHEME);
        document.add(registry.require(StandardComponentTypes.BUFFER).newComponent(document.newComponentId(),
                new GridPoint(3, 3), Rotation.R0, Map.of(), ""));
        document.add(registry.require(StandardComponentTypes.BUFFER).newComponent(document.newComponentId(),
                new GridPoint(300, 300), Rotation.R0, Map.of(), ""));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot empty = snapshot(document.revision(), Map.of(), List.of());

        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D unclipped = image.createGraphics();
        counting.paint(unclipped, new RenderState(document, netlist, document.revision(), empty, Set.of(), 0));
        unclipped.dispose();
        assertThat(painted).as("without a clip everything is painted").hasSize(2);

        painted.clear();
        Graphics2D clipped = image.createGraphics();
        clipped.setClip(0, 0, 120, 120);
        counting.paint(clipped, new RenderState(document, netlist, document.revision(), empty, Set.of(), 0));
        clipped.dispose();
        assertThat(painted).as("with a clip only what it can show").hasSize(1);
    }

    @Test
    void theSelectionOutlineOfAComponentMovesWithThePhase() {
        Component gate = registry.require(StandardComponentTypes.BUFFER).newComponent(document.newComponentId(),
                new GridPoint(3, 3), Rotation.R0, Map.of(), "");
        document.add(gate);
        SimulationSnapshot empty = snapshot(document.revision(), Map.of(), List.of());
        assertThat(sameImage(renderSelected(Set.of(gate.id()), 0, empty), renderSelected(Set.of(gate.id()), 3, empty)))
                .isFalse();
        assertThat(sameImage(renderSelected(Set.of(gate.id()), 0, empty), renderSelected(Set.of(gate.id()), 0, empty)))
                .isTrue();
    }

    @Test
    void theSelectionHighlightOfAWireMovesWithThePhase() {
        document.add(wire);
        SimulationSnapshot empty = snapshot(document.revision(), Map.of(), List.of());
        assertThat(sameImage(renderSelected(Set.of(wire.id()), 0, empty), renderSelected(Set.of(wire.id()), 3, empty)))
                .isFalse();
    }

    @Test
    void theShortCircuitOutlineDoesNotDependOnThePhase() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(3, 3), Rotation.R0, Map.of("value", "1"), "");
        document.add(high);
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        SimulationSnapshot shorted = snapshot(document.revision(), Map.of(netId, Value.ONE.withConflict()),
                List.of(shortCircuit));
        assertThat(sameImage(renderSelected(Set.of(), 0, shorted), renderSelected(Set.of(), 3, shorted))).isTrue();
    }

    private static SimulationSnapshot snapshot(long token, Map<String, Value> values, List<ShortCircuit> shorts) {
        return new SimulationSnapshot(1, token, 0, true, values, Map.of(), List.copyOf(shorts), Optional.empty());
    }

    @Test
    void wiresTakeTheColorOfTheirNetWhenTheSnapshotMatches() {
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        BufferedImage image = render(snapshot(document.revision(), Map.of(netId, Value.ONE), List.of()), netlist, document.revision());
        assertThat(new Color(image.getRGB(24, 8))).isEqualTo(SCHEME.wireOne());
    }

    @Test
    void staleSnapshotPaintsWiresInTheUnknownColor() {
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        BufferedImage image = render(snapshot(document.revision() - 1, Map.of(netId, Value.ONE), List.of()), netlist,
                document.revision());
        assertThat(new Color(image.getRGB(24, 8))).isEqualTo(SCHEME.wireUnknown());
    }

    @Test
    void shortCircuitThickensTheNetAndMarksItsDrivers() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(-3, 0), Rotation.R0, Map.of("value", "1"), "");
        document.add(high);
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        BufferedImage image = render(snapshot(document.revision(), Map.of(netId, Value.ONE.withConflict()),
                List.of(shortCircuit)), netlist, document.revision());
        assertThat(new Color(image.getRGB(24, 9))).as("thick conflict wire").isEqualTo(SCHEME.wireConflict());
        Color halo = new Color(image.getRGB(8, 3));
        assertThat(halo.getRed()).as("halo above the driver pin").isGreaterThan(halo.getGreen() + 40);
    }

    private BufferedImage renderPlain(Set<ElementId> selected) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paint(graphics, new RenderState(document, new NetlistCompiler(registry).compile(document),
                document.revision(), SimulationSnapshot.EMPTY, selected, 0));
        graphics.dispose();
        return image;
    }

    private static int inkInside(BufferedImage image, Rectangle2D area) {
        int count = 0;
        for (int x = Math.max(0, (int) Math.floor(area.getMinX())); x < Math.min(image.getWidth(), Math.ceil(area.getMaxX())); x++) {
            for (int y = Math.max(0, (int) Math.floor(area.getMinY())); y < Math.min(image.getHeight(), Math.ceil(area.getMaxY())); y++) {
                if (image.getRGB(x, y) != SCHEME.background().getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private BufferedImage renderWithPulse(SimulationSnapshot snapshot, double pulsePhase) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                pulsePhase));
        graphics.dispose();
        return image;
    }

    private static boolean reddish(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() > color.getGreen() + 30 && color.getRed() > color.getBlue() + 30;
    }

    @Test
    void aShortCircuitedWireGlowsBesideItAndTheGlowPulsesWithThePhase() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(-3, 0), Rotation.R0, Map.of("value", "1"), "");
        document.add(high);
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        SimulationSnapshot shorted = snapshot(document.revision(), Map.of(netId, Value.ONE.withConflict()),
                List.of(shortCircuit));

        BufferedImage low = renderWithPulse(shorted, ProblemPulse.LOW_PHASE);
        BufferedImage peak = renderWithPulse(shorted, ProblemPulse.HIGH_PHASE);

        assertThat(reddish(peak.getRGB(24, 12))).as("glow beside the wire at the high phase").isTrue();
        assertThat(peak.getRGB(24, 12)).isNotEqualTo(low.getRGB(24, 12));
        assertThat(new Color(peak.getRGB(24, 8))).as("the wire itself stays crisp").isEqualTo(SCHEME.wireConflict());
        assertThat(new Color(low.getRGB(24, 8))).isEqualTo(SCHEME.wireConflict());
    }

    @Test
    void withoutTheProblemGlowNothingIsPaintedBesideAProblemWire() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(-3, 0), Rotation.R0, Map.of("value", "1"), "");
        document.add(high);
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        SimulationSnapshot shorted = snapshot(document.revision(), Map.of(netId, Value.ONE.withConflict()),
                List.of(shortCircuit));

        BufferedImage image = renderWithPulse(shorted, RenderState.NO_PROBLEM_GLOW);

        assertThat(image.getRGB(24, 12)).as("nothing beside the wire").isEqualTo(SCHEME.background().getRGB());
        assertThat(new Color(image.getRGB(24, 8))).as("the wire keeps its color").isEqualTo(SCHEME.wireConflict());
    }

    @Test
    void aMismatchedWireGlowsBesideItAndTheGlowPulsesWithThePhase() {
        document.add(registry.require(StandardComponentTypes.LED).newComponent(document.newComponentId(),
                new GridPoint(0, -2), Rotation.R0, Map.of(), "")); // its pin is at (1, 1)
        document.add(wire);
        splitterAt(5, 1);

        BufferedImage low = renderWithPulse(SimulationSnapshot.EMPTY, ProblemPulse.LOW_PHASE);
        BufferedImage peak = renderWithPulse(SimulationSnapshot.EMPTY, ProblemPulse.HIGH_PHASE);

        assertThat(reddish(peak.getRGB(24, 12))).isTrue();
        assertThat(peak.getRGB(24, 12)).isNotEqualTo(low.getRGB(24, 12));
    }

    @Test
    void healthyWiresAndBusesGetNoGlow() {
        document.add(wire);
        SimulationSnapshot empty = snapshot(document.revision(), Map.of(), List.of());
        assertThat(renderWithPulse(empty, ProblemPulse.HIGH_PHASE).getRGB(24, 12))
                .isEqualTo(SCHEME.background().getRGB());

        splitterAt(5, 1); // a 4-bit bus pin ends the wire: a bus, and nothing else on it
        BufferedImage bus = renderWithPulse(empty, ProblemPulse.HIGH_PHASE);
        assertThat(bus.getRGB(24, 13)).isEqualTo(SCHEME.background().getRGB());
        assertThat(sameImage(bus, renderWithPulse(empty, ProblemPulse.LOW_PHASE))).isTrue();
    }

    @Test
    void componentLabelIsDrawnAboveTheComponent() {
        Component gate = registry.require(StandardComponentTypes.BUFFER).newComponent(document.newComponentId(),
                new GridPoint(2, 6), Rotation.R0, Map.of(), "");
        document.add(gate);
        Rectangle2D area = LabelLayout.componentLabel(registry.require(StandardComponentTypes.BUFFER),
                gate.withLabel("WW")).orElseThrow().bounds();
        assertThat(inkInside(renderPlain(Set.of()), area)).isZero();
        document.replace(gate.withLabel("WW"));
        assertThat(inkInside(renderPlain(Set.of()), area)).isPositive();
    }

    @Test
    void freeTextIsDrawnAndOutlinedWhenSelected() {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(2, 2), "nota");
        document.add(label);
        Rectangle2D text = LabelLayout.freeText(label).bounds();
        Rectangle2D around = new Rectangle2D.Double(text.getX() - 6, text.getY() - 6, text.getWidth() + 12,
                text.getHeight() + 12);
        BufferedImage plain = renderPlain(Set.of());
        assertThat(inkInside(plain, text)).isPositive();
        assertThat(inkInside(renderPlain(Set.of(label.id())), around)).isGreaterThan(inkInside(plain, around));
    }

    @Test
    void aStyledNoteIsPaintedOnItsBackgroundInsideItsBorderInItsTextColour() {
        NoteStyle style = new NoteStyle(NoteColor.YELLOW, NoteColor.BLUE, NoteColor.RED);
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(2, 2), "WW", style);
        document.add(note);
        Rectangle2D box = LabelLayout.noteBox(note);

        BufferedImage image = renderWithPolicy(PaintPolicy.CANVAS);

        int middle = (int) box.getCenterY();
        assertThat(new Color(image.getRGB((int) box.getMinX() + 2, middle)))
                .isEqualTo(SCHEME.noteBackground(NoteColor.YELLOW).orElseThrow());
        assertThat(reddish(image.getRGB((int) Math.floor(box.getMinX()), middle))).as("the red border").isTrue();
        assertThat(colours(image, LabelLayout.freeText(note).bounds())).as("blue ink on the yellow")
                .anyMatch(rgb -> (rgb & 0xFF) > ((rgb >> 16) & 0xFF) + 60);
    }

    /** A white note on a white board (Claro, or any «Fondo blanco para imprimir» export) still shows its box. */
    @Test
    void aNoteWhoseBackgroundIsTheBoardsColourGetsAQuietOutline() {
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(2, 2), "WW",
                new NoteStyle(NoteColor.WHITE, NoteColor.NONE, NoteColor.NONE));
        document.add(note);
        Rectangle2D box = LabelLayout.noteBox(note);

        BufferedImage image = renderWithPolicy(PaintPolicy.CANVAS);

        int middle = (int) box.getCenterY();
        assertThat(new Color(image.getRGB((int) Math.floor(box.getMinX()), middle))).as("the box's edge")
                .isNotEqualTo(new Color(image.getRGB((int) box.getMinX() + 2, middle)));
    }

    @Test
    void inBlackAndWhiteAStyledNoteKeepsABlackBorderAndBlackTextAndDropsItsBackground() {
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(2, 2), "WW",
                new NoteStyle(NoteColor.BLACK, NoteColor.WHITE, NoteColor.RED));
        document.add(note);
        Rectangle2D box = LabelLayout.noteBox(note);

        BufferedImage image = renderWithPolicy(PaintPolicy.print(true));

        int middle = (int) box.getCenterY();
        assertThat(new Color(image.getRGB((int) box.getMinX() + 2, middle))).as("no background on paper")
                .isEqualTo(SCHEME.background());
        assertThat(new Color(image.getRGB((int) Math.floor(box.getMinX()), middle)))
                .as("a dark (antialiased black) border, not the red chosen").satisfies(border -> {
                    assertThat(border.getRed()).isLessThan(200);
                    assertThat(reddish(border.getRGB())).isFalse();
                });
        assertThat(colours(image, LabelLayout.freeText(note).bounds())).as("black text, not the white chosen")
                .anyMatch(rgb -> (rgb & 0xFF) < 60);
    }

    private BufferedImage renderWithPolicy(PaintPolicy policy) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paint(graphics, new RenderState(document, new NetlistCompiler(registry).compile(document),
                document.revision(), SimulationSnapshot.EMPTY, Set.of(), 0, RenderState.NO_PROBLEM_GLOW, Set.of(),
                policy));
        graphics.dispose();
        return image;
    }

    private static Set<Integer> colours(BufferedImage image, Rectangle2D area) {
        Set<Integer> colours = new java.util.HashSet<>();
        for (int x = (int) area.getMinX(); x < (int) Math.ceil(area.getMaxX()); x++) {
            for (int y = (int) area.getMinY(); y < (int) Math.ceil(area.getMaxY()); y++) {
                colours.add(image.getRGB(x, y));
            }
        }
        return colours;
    }

    @Test
    void everyLineOfANoteIsPaintedOnItsOwnRowAlsoInTheGhost() {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(1, 1), "WW\nWW\nWW");
        LabelLayout.PlacedText placed = LabelLayout.freeText(label);
        document.add(label);
        BufferedImage plain = renderPlain(Set.of());
        BufferedImage ghost = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = ghost.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paintGhost(graphics, label);
        graphics.dispose();
        for (LabelLayout.Line line : placed.lines()) {
            Rectangle2D row = new Rectangle2D.Double(placed.left(), line.baselineY() - placed.ascent() + 1,
                    placed.width(), placed.ascent() - 1);
            assertThat(inkInside(plain, row)).as("line at %s", line.baselineY()).isPositive();
            assertThat(inkInside(ghost, row)).as("ghost line at %s", line.baselineY()).isPositive();
        }
        Rectangle2D belowTheText = new Rectangle2D.Double(placed.left(), placed.top() + placed.height() + 1,
                placed.width(), 10);
        assertThat(inkInside(plain, belowTheText)).isZero();
    }

    /** Review M4: a styled note's ghost is a preview like every other ghost, not a translucent copy of the note. */
    @Test
    void theGhostOfAStyledNoteIsAnOutlineAndTextInThePreviewColour() {
        TextLabel note = new TextLabel(new ElementId("l1"), new GridPoint(2, 2), "WW",
                new NoteStyle(NoteColor.RED, NoteColor.BLUE, NoteColor.GREEN));
        Rectangle2D box = LabelLayout.noteBox(note);

        BufferedImage image = ghostOf(note);

        int middle = (int) box.getCenterY();
        assertThat(new Color(image.getRGB((int) box.getMinX() + 2, middle))).as("no fill inside the box")
                .isEqualTo(SCHEME.background());
        assertThat(inkInside(image, new Rectangle2D.Double(Math.floor(box.getMinX()), middle, 1, 1)))
                .as("its outline").isPositive();
        assertThat(inkInside(image, LabelLayout.freeText(note).bounds())).as("its text").isPositive();
        assertThat(sameImage(image, ghostOf(note.withStyle(new NoteStyle(NoteColor.YELLOW, NoteColor.RED,
                NoteColor.BLUE))))).as("the note's own colours do not show").isTrue();
    }

    private BufferedImage ghostOf(TextLabel note) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paintGhost(graphics, note);
        graphics.dispose();
        return image;
    }

    @Test
    void freeTextGhostIsPainted() {
        TextLabel label = new TextLabel(new ElementId("l1"), new GridPoint(2, 2), "nota");
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paintGhost(graphics, label);
        graphics.dispose();
        assertThat(inkInside(image, LabelLayout.freeText(label).bounds())).isPositive();
    }

    @Test
    void ghostsResolveTypesWithTheRegistryTheyAreGiven() {
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                new GridPoint(2, 2), Rotation.R0, Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(instance), List.of(), List.of(),
                List.of(ExampleDefinitions.inverter()));
        ComponentTypeRegistry base = StandardComponentTypes.registry();
        CircuitRenderer ghostRenderer = new CircuitRenderer(base, PainterRegistry.standard(), ColorScheme.LIGHT);
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        ghostRenderer.paintGhost(graphics, instance, fragment.registryFor(base));
        graphics.dispose();

        int painted = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                }
            }
        }
        assertThat(painted).isGreaterThan(20);
    }

    private Component splitterAt(int x, int y) {
        Component splitter = registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(
                document.newComponentId(), new GridPoint(x, y), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4),
                "");
        document.add(splitter);
        return splitter;
    }

    @Test
    void busWiresAreThickerThanOneBitWires() {
        // the wire is at y = 8 px: row 9 is fully covered by a 4.5 px bus stroke, only partly by a 2.5 px wire
        document.add(wire);
        Netlist thin = new NetlistCompiler(registry).compile(document);
        String thinNet = thin.netForWire(wire.id()).orElseThrow().id().value();
        BufferedImage oneBit = render(snapshot(document.revision(), Map.of(thinNet, Value.ONE), List.of()), thin,
                document.revision());
        assertThat(new Color(oneBit.getRGB(24, 9))).isNotEqualTo(SCHEME.wireOne());

        splitterAt(5, 1); // its bus pin ends the wire at (5, 1)
        Netlist bus = new NetlistCompiler(registry).compile(document);
        String busNet = bus.netForWire(wire.id()).orElseThrow().id().value();
        BufferedImage fourBits = render(snapshot(document.revision(), Map.of(busNet, Value.of(4, 0x5)), List.of()),
                bus, document.revision());
        assertThat(new Color(fourBits.getRGB(24, 9))).isEqualTo(SCHEME.wireOne());
    }

    @Test
    void aBusWritesItsValueAboveItsWireOnlyWhileTheSnapshotIsCurrent() {
        document.add(wire);
        splitterAt(5, 1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String busNet = netlist.netForWire(wire.id()).orElseThrow().id().value();
        Map<String, Value> values = Map.of(busNet, Value.of(4, 0x5));
        BufferedImage current = render(snapshot(document.revision(), values, List.of()), netlist, document.revision());
        BufferedImage stale = render(snapshot(document.revision() - 1, values, List.of()), netlist,
                document.revision());
        int written = 0;
        for (int x = 10; x < 40; x++) {
            for (int y = 0; y < 5; y++) {
                if (current.getRGB(x, y) != stale.getRGB(x, y)) {
                    written++;
                }
            }
        }
        assertThat(written).isGreaterThan(5);
    }

    @Test
    void aBusPaintsNoValueWhenThePolicyHidesTheBusValues() {
        document.add(wire);
        splitterAt(5, 1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String busNet = netlist.netForWire(wire.id()).orElseThrow().id().value();
        SimulationSnapshot current = snapshot(document.revision(), Map.of(busNet, Value.of(4, 0x5)), List.of());

        BufferedImage shown = renderUnder(PaintPolicy.CANVAS, current, netlist);
        BufferedImage hidden = renderUnder(PaintPolicy.CANVAS.withBusValues(false), current, netlist);

        // only the label differs: above the wire (its row is y = 8), never on or below it
        int differing = 0;
        for (int y = 0; y < shown.getHeight(); y++) {
            for (int x = 0; x < shown.getWidth(); x++) {
                if (shown.getRGB(x, y) != hidden.getRGB(x, y)) {
                    differing++;
                    assertThat(y).as("row of a changed pixel").isLessThan(8);
                }
            }
        }
        assertThat(differing).isGreaterThan(5);
    }

    private BufferedImage renderUnder(PaintPolicy policy, SimulationSnapshot snapshot, Netlist netlist) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), policy));
        graphics.dispose();
        return image;
    }

    @Test
    void busJunctionsAreBigger() {
        // a T at (5, 1): screen (40, 8); pixel (40, 4) is inside a 5 px dot but outside a 3.5 px one and the wire
        Wire through = new Wire(document.newWireId(), new GridPoint(1, 1), new GridPoint(9, 1));
        Wire branch = new Wire(document.newWireId(), new GridPoint(5, 1), new GridPoint(5, 5));
        document.add(through);
        document.add(branch);
        Netlist thin = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot stale = snapshot(document.revision() - 1, Map.of(), List.of());
        assertThat(new Color(render(stale, thin, document.revision()).getRGB(40, 4)))
                .isNotEqualTo(SCHEME.componentStroke());

        splitterAt(9, 1);
        Netlist bus = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot staleBus = snapshot(document.revision() - 1, Map.of(), List.of());
        assertThat(new Color(render(staleBus, bus, document.revision()).getRGB(40, 4)))
                .isEqualTo(SCHEME.componentStroke());
    }

    @Test
    void aMismatchedNetIsDrawnDashedInTheConflictColorWithoutASimulation() {
        document.add(wire);
        splitterAt(5, 1); // 4-bit bus pin at (5, 1)
        document.add(registry.require(StandardComponentTypes.LED).newComponent(document.newComponentId(),
                new GridPoint(0, -2), Rotation.R0, Map.of(), "")); // 1-bit pin at (1, 1)
        Netlist netlist = new NetlistCompiler(registry).compile(document);

        BufferedImage image = render(SimulationSnapshot.EMPTY, netlist, document.revision());

        // the first dash covers x 8..14 px of the wire at y = 8 px
        assertThat(new Color(image.getRGB(10, 8))).isEqualTo(SCHEME.wireConflict());
    }

    private Component busComponentAt(String typeId, int x, int y) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), "");
        document.add(component);
        return component;
    }

    private BufferedImage renderCompiled() {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        return render(SimulationSnapshot.EMPTY, netlist, document.revision());
    }

    private Color pinCenter(BufferedImage image, Component component, int pinIndex) {
        GridPoint pin = registry.typeOf(component).pinLocation(component, pinIndex);
        return new Color(image.getRGB(pin.x() * 8, pin.y() * 8));
    }

    @Test
    void anOpenPinOfABusJoinerIsAHollowRingAndAWiredPinKeepsItsDot() {
        Component joiner = busComponentAt(StandardComponentTypes.BUS_JOINER, 3, 3);
        // bit 0 (pin 1) is at (3, 4); wire it to the left; the others stay open
        document.add(new Wire(document.newWireId(), new GridPoint(1, 4), new GridPoint(3, 4)));

        BufferedImage image = renderCompiled();

        assertThat(pinCenter(image, joiner, 1)).isEqualTo(SCHEME.componentStroke());
        assertThat(pinCenter(image, joiner, 2)).isEqualTo(SCHEME.background());
assertThat(pinCenter(image, joiner, 3)).isEqualTo(SCHEME.background());
        // the open output bus pin is a hollow square
        assertThat(pinCenter(image, joiner, 0)).isEqualTo(SCHEME.background());
    }

    @Test
    void aBusSplitterWithAllItsPinsWiredHasNoRing() {
        Component splitter = busComponentAt(StandardComponentTypes.BUS_SPLITTER, 3, 3);
        ComponentType type = registry.typeOf(splitter);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            GridPoint pin = type.pinLocation(splitter, pinIndex);
            document.add(new Wire(document.newWireId(), pin, new GridPoint(pin.x() + (pinIndex == 0 ? -2 : 2), pin.y())));
        }

        BufferedImage image = renderCompiled();

        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            assertThat(pinCenter(image, splitter, pinIndex)).isEqualTo(SCHEME.componentStroke());
        }
    }

    private Component componentAt(String typeId, int x, int y) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    @Test
    void anOpenInputOfAnyComponentIsAHollowRingAndAnOpenOutputKeepsItsDot() {
        Component gate = componentAt(StandardComponentTypes.BUFFER, 3, 3);          // in (3, 5), out (7, 5)
        Component led = componentAt(StandardComponentTypes.LED, 8, 3);           // in (9, 6)
        Component display = componentAt(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 3, 8); // in0 (7, 8)

        BufferedImage image = renderCompiled();

        assertThat(pinCenter(image, gate, 0)).isEqualTo(SCHEME.background());
        assertThat(pinCenter(image, led, 0)).isEqualTo(SCHEME.background());
        assertThat(pinCenter(image, display, 0)).isEqualTo(SCHEME.background());
        assertThat(pinCenter(image, gate, 1)).as("outputs get no ring").isEqualTo(SCHEME.componentStroke());
    }

    @Test
    void connectingAnInputByWireOrPinOnPinTakesItsRingAway() {
        Component wired = componentAt(StandardComponentTypes.BUFFER, 3, 3);          // in (3, 5)
        document.add(new Wire(document.newWireId(), new GridPoint(1, 5), new GridPoint(3, 5)));
        componentAt(StandardComponentTypes.SWITCH, 0, 10);                       // out (4, 11)
        Component touching = componentAt(StandardComponentTypes.BUFFER, 4, 9);       // in (4, 11): pin on pin

        BufferedImage image = renderCompiled();

        assertThat(pinCenter(image, wired, 0)).isEqualTo(SCHEME.componentStroke());
        assertThat(pinCenter(image, touching, 0)).isEqualTo(SCHEME.componentStroke());
    }

    @Test
    void anOpenInputRingUsesTheDarkSchemeToo() {
        CircuitRenderer dark = new CircuitRenderer(registry, PainterRegistry.standard(), ColorSchemes.DARK);
        Component gate = componentAt(StandardComponentTypes.BUFFER, 3, 3);
        Netlist netlist = new NetlistCompiler(registry).compile(document);

        BufferedImage image = renderWith(dark, SimulationSnapshot.EMPTY, netlist, document.revision());

        assertThat(pinCenter(image, gate, 0)).isEqualTo(ColorSchemes.DARK.background());
    }

    @Test
    void aStaleNetlistMarksNoPin() {
        Component joiner = busComponentAt(StandardComponentTypes.BUS_JOINER, 3, 3);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        document.add(new Wire(document.newWireId(), new GridPoint(1, 4), new GridPoint(3, 4)));

        BufferedImage image = render(SimulationSnapshot.EMPTY, netlist, document.revision() - 1);

        assertThat(pinCenter(image, joiner, 2)).isEqualTo(SCHEME.componentStroke());
    }

    @Test
    void cachedHighlightsPaintLikeAFreshRendererAndFollowANewNetlist() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(3, 3), Rotation.R0, Map.of("value", "1"), "");
        document.add(high);
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        SimulationSnapshot shorted = snapshot(document.revision(), Map.of(netId, Value.ONE.withConflict()),
                List.of(new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0)));

        BufferedImage first = render(shorted, netlist, document.revision());
        BufferedImage again = render(shorted, netlist, document.revision());

        assertThat(renderer.highlights().computations()).isEqualTo(1);
        assertThat(sameImage(first, again)).isTrue();
        assertThat(sameImage(again, renderWith(new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME),
                shorted, netlist, document.revision()))).isTrue();

        document.remove(high.id());
        Netlist next = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot healthy = snapshot(document.revision(), Map.of(), List.of());
        BufferedImage after = render(healthy, next, document.revision());

        assertThat(renderer.highlights().computations()).isEqualTo(2);
        assertThat(sameImage(after, first)).isFalse();
        assertThat(sameImage(after, renderWith(new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME),
                healthy, next, document.revision()))).isTrue();
    }
}
