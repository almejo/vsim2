package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.document.Component;
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
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gap in the vertical wire of a crossing. A horizontal wire at y = 5 (40 px) crosses a vertical wire at x = 7
 * (56 px). With 2.5 px strokes the horizontal one covers y 38.75 … 41.25 and the vertical one stops at 36.75 and
 * restarts at 43.25, so pixel rows 37 and 42 of column 56 are clear.
 */
class CircuitRendererCrossingsTest {

    private static final int COLUMN = 56;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private BufferedImage render(ColorScheme scheme, Set<ElementId> selection) {
        return render(scheme, selection, new NetlistCompiler(registry).compile(document), SimulationSnapshot.EMPTY);
    }

    private BufferedImage render(ColorScheme scheme, Set<ElementId> selection, Netlist netlist,
                                 SimulationSnapshot snapshot) {
        CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), scheme);
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, selection, 0));
        graphics.dispose();
        return image;
    }

    private Component place(String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.add(component);
        return component;
    }

    private static boolean conflictColor(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y)).equals(ColorScheme.LIGHT.wireConflict());
    }

    private BufferedImage render() {
        return render(ColorScheme.LIGHT, Set.of());
    }

    private static boolean background(BufferedImage image, int x, int y, ColorScheme scheme) {
        return image.getRGB(x, y) == scheme.background().getRGB();
    }

    @Test
    void theVerticalWireOfACrossingHasAGapAndTheHorizontalOneIsContinuous() {
        wire(document, 1, 5, 13, 5);
        wire(document, 7, 1, 7, 13);

        BufferedImage image = render();

        assertThat(background(image, COLUMN, 37, ColorScheme.LIGHT)).as("clear above the horizontal wire").isTrue();
        assertThat(background(image, COLUMN, 42, ColorScheme.LIGHT)).as("clear below the horizontal wire").isTrue();
        assertThat(background(image, COLUMN, 30, ColorScheme.LIGHT)).as("vertical wire above the gap").isFalse();
        assertThat(background(image, COLUMN, 50, ColorScheme.LIGHT)).as("vertical wire below the gap").isFalse();
        for (int x = 48; x <= 64; x++) {
            assertThat(background(image, x, 40, ColorScheme.LIGHT)).as("horizontal wire at x = " + x).isFalse();
        }
    }

    @Test
    void aBusAcrossMakesTheGapWider() {
        wire(document, 1, 5, 13, 5);
        wire(document, 7, 1, 7, 13);
        BufferedImage thin = render();
        // the 2.5 px vertical wire's round caps reach y 36.75 and 43.25: rows 36 and 43 still get some paint
        assertThat(background(thin, COLUMN, 36, ColorScheme.LIGHT)).isFalse();
        assertThat(background(thin, COLUMN, 43, ColorScheme.LIGHT)).isFalse();

        // a 4-bit splitter's bus pin is at its position: it ends the horizontal wire and makes it a bus
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(document.newComponentId(),
                new GridPoint(13, 5), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), ""));
        BufferedImage bus = render();

        // a 4.5 px bus covers y 37.75 … 42.25; the vertical wire stops at 35.75 and restarts at 44.25
        assertThat(background(bus, COLUMN, 36, ColorScheme.LIGHT)).isTrue();
        assertThat(background(bus, COLUMN, 43, ColorScheme.LIGHT)).isTrue();
    }

    @Test
    void junctionsAndTeesKeepTheirWiresWhole() {
        wire(document, 1, 5, 13, 5);
        wire(document, 7, 5, 7, 8); // a T at (7, 5)
        wire(document, 1, 10, 13, 10);
        wire(document, 3, 1, 3, 10);
        wire(document, 3, 10, 3, 13); // two vertical wires meeting on the horizontal one: a junction at (3, 10)

        BufferedImage image = render();

        assertThat(new Color(image.getRGB(COLUMN, 40))).as("the T's dot").isEqualTo(ColorScheme.LIGHT.componentStroke());
        assertThat(background(image, COLUMN, 44, ColorScheme.LIGHT)).as("the T's vertical wire").isFalse();
        assertThat(new Color(image.getRGB(24, 80))).as("the junction's dot")
                .isEqualTo(ColorScheme.LIGHT.componentStroke());
        assertThat(background(image, 24, 77, ColorScheme.LIGHT)).isFalse();
        assertThat(background(image, 24, 83, ColorScheme.LIGHT)).isFalse();
    }

    @Test
    void theSelectionHaloOfTheVerticalWireStaysContinuousOverTheGap() {
        wire(document, 1, 5, 13, 5);
        Wire vertical = wire(document, 7, 1, 7, 13);

        BufferedImage image = render(ColorScheme.LIGHT, Set.of(vertical.id()));

        assertThat(background(image, COLUMN, 37, ColorScheme.LIGHT)).isFalse();
        assertThat(background(image, COLUMN, 42, ColorScheme.LIGHT)).isFalse();
    }

    @Test
    void aDashedMismatchedVerticalStopsTwoPixelsFromTheHorizontal() {
        wire(document, 1, 5, 13, 5);
        wire(document, 7, 1, 7, 13);
        place(StandardComponentTypes.LED, 6, -2, Map.of()); // its 1-bit input (offset 1, 3) ends the vertical at (7, 1)
        place(StandardComponentTypes.BUS_SPLITTER, 7, 13, Map.of(StandardComponentTypes.WIDTH, 4)); // 4-bit pin at (7, 13)

        BufferedImage image = render();

        // the dashed mismatch stroke is butt-capped: no cap term, so the lower span starts at 41.25 + 2 = 43.25 and
        // its first dash (6 px from the span's start) covers row 44 fully; with a round-cap term (2.25) it would
        // start at 45.5 and row 44 would show only the problem glow
        assertThat(conflictColor(image, COLUMN, 44)).as("the dashed vertical right below the gap").isTrue();
        // inside the gap only the problem glow (translucent, painted under every wire) is left
        assertThat(conflictColor(image, COLUMN, 37)).as("gap above the horizontal wire").isFalse();
        assertThat(conflictColor(image, COLUMN, 42)).as("gap below the horizontal wire").isFalse();
    }

    @Test
    void aShortCircuitedVerticalKeepsItsRoundCapsOutOfTheGap() {
        wire(document, 1, 5, 13, 5);
        Wire vertical = wire(document, 7, 1, 7, 13);
        Component high = place(StandardComponentTypes.SWITCH, 3, 12, Map.of("value", "1")); // output (4, 1) at (7, 13)
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(vertical.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        SimulationSnapshot shorted = new SimulationSnapshot(1, document.revision(), 0, true,
                Map.of(netId, Value.ONE.withConflict()), Map.of(), List.of(shortCircuit), Optional.empty());

        BufferedImage image = render(ColorScheme.LIGHT, Set.of(), netlist, shorted);

        // 5 px round caps: h = 1.25 + 2 + 2.5 = 5.75, so the caps end at 36.75 and restart at 43.25
        assertThat(conflictColor(image, COLUMN, 30)).as("thick conflict wire above the gap").isTrue();
        assertThat(conflictColor(image, COLUMN, 50)).as("thick conflict wire below the gap").isTrue();
        assertThat(conflictColor(image, COLUMN, 37)).as("gap above the horizontal wire").isFalse();
        assertThat(conflictColor(image, COLUMN, 42)).as("gap below the horizontal wire").isFalse();
    }

    @Test
    void theGapShowsTheDarkSchemesOwnBackground() {
        wire(document, 1, 5, 13, 5);
        wire(document, 7, 1, 7, 13);

        BufferedImage image = render(ColorSchemes.DARK, Set.of());

        assertThat(background(image, COLUMN, 37, ColorSchemes.DARK)).isTrue();
        assertThat(background(image, COLUMN, 42, ColorSchemes.DARK)).isTrue();
        assertThat(background(image, COLUMN, 30, ColorSchemes.DARK)).isFalse();
    }
}
