package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.AppearanceTestSupport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PendingOutlineOverlayTest {

    private static final long TOKEN = 3;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Component first = place(4, 4);
    private final Component second = place(20, 4);
    private final AtomicInteger repaints = new AtomicInteger();

    private Component place(int x, int y) {
        Component component = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(x, y), Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    /** The color painted just inside the start of the top edge of {@code component}'s outline. */
    private Color onOutlineOf(PendingOutlineOverlay overlay, Component component, long token) {
        Rectangle2D bounds = ComponentDrawing.documentBounds(registry.typeOf(component), component);
        int x = (int) Math.floor(bounds.getX() - PendingOutlineOverlay.GAP) + 1;
        int y = (int) Math.floor(bounds.getY() - PendingOutlineOverlay.GAP);
        BufferedImage image = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(document, Netlist.EMPTY, token, SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return new Color(image.getRGB(x, y), true);
    }

    @Test
    void onlyTheListedComponentsAreOutlinedAndOnlyForTheirNetlist() {
        ColorScheme magenta = AppearanceTestSupport.withSelection(ColorScheme.LIGHT, Color.MAGENTA);
        PendingOutlineOverlay overlay = new PendingOutlineOverlay(() -> magenta, registry, repaints::incrementAndGet);
        assertThat(onOutlineOf(overlay, first, TOKEN).getAlpha()).isZero();

        overlay.show(new PendingOutline(Set.of(first.id()), TOKEN));

        assertThat(onOutlineOf(overlay, first, TOKEN)).isEqualTo(Color.MAGENTA);
        assertThat(onOutlineOf(overlay, second, TOKEN).getAlpha()).isZero();
        assertThat(onOutlineOf(overlay, first, TOKEN + 1).getAlpha()).as("another netlist").isZero();
    }

    @Test
    void eachPaintUsesTheSchemeInUseAtThatMoment() {
        AtomicReference<ColorScheme> scheme = new AtomicReference<>(
                AppearanceTestSupport.withSelection(ColorScheme.LIGHT, Color.MAGENTA));
        PendingOutlineOverlay overlay = new PendingOutlineOverlay(scheme::get, registry, repaints::incrementAndGet);
        overlay.show(new PendingOutline(Set.of(first.id()), TOKEN));
        assertThat(onOutlineOf(overlay, first, TOKEN)).isEqualTo(Color.MAGENTA);

        scheme.set(AppearanceTestSupport.withSelection(ColorScheme.LIGHT, Color.ORANGE));

        assertThat(onOutlineOf(overlay, first, TOKEN)).isEqualTo(Color.ORANGE);
    }

    @Test
    void onlyAChangeRepaintsAndNothingIsShownAfterDispose() {
        PendingOutlineOverlay overlay = new PendingOutlineOverlay(() -> ColorScheme.LIGHT, registry,
                repaints::incrementAndGet);
        PendingOutline outline = new PendingOutline(Set.of(first.id()), TOKEN);

        overlay.show(outline);
        overlay.show(new PendingOutline(Set.of(first.id()), TOKEN));
        assertThat(repaints).as("the same outline again").hasValue(1);
        overlay.clear();
        assertThat(overlay.current()).isEqualTo(PendingOutline.NONE);
        assertThat(repaints).hasValue(2);

        overlay.dispose();
        overlay.show(outline);

        assertThat(overlay.current()).isEqualTo(PendingOutline.NONE);
        assertThat(onOutlineOf(overlay, first, TOKEN).getAlpha()).isZero();
        assertThat(repaints).hasValue(2);
    }
}
