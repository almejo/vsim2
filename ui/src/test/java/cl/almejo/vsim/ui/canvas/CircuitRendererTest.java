package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
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
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paint(graphics, new RenderState(document, netlist, token, snapshot, Set.of(), 0));
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
    void theSelectionOutlineOfAComponentMovesWithThePhase() {
        Component gate = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
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

    @Test
    void componentLabelIsDrawnAboveTheComponent() {
        Component gate = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(2, 6), Rotation.R0, Map.of(), "");
        document.add(gate);
        Rectangle2D area = LabelLayout.componentLabel(registry.require(StandardComponentTypes.NOT),
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
}
