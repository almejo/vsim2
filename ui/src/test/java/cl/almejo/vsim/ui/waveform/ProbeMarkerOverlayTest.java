package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProbeMarkerOverlayTest {

    private OpenDocument openDocument;
    private ColorScheme scheme = ColorScheme.LIGHT;
    private ProbeMarkerOverlay overlay;
    private Component gate;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        overlay = new ProbeMarkerOverlay(openDocument, () -> scheme);
        gate = place(openDocument, StandardComponentTypes.BUFFER, 4, 9, Map.of(StandardComponentTypes.NEGATE, true)); // out (8,11): badge x 68–80, y 72–80
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private void probeTheOutput(String name) {
        openDocument.execute(new SetProbesCommand(openDocument.document().probes(),
                List.of(new Probe("p1", new ProbeTarget.PinTarget(gate.id(), 1), name)), "command.addProbes"));
    }

    /** The input (4, 11) is on the left edge of the NOT's box x 4–8: its badge goes left, grid x 2–3.5, y 9–10. */
    private void probeTheInput() {
        openDocument.execute(new SetProbesCommand(openDocument.document().probes(),
                List.of(new Probe("p2", new ProbeTarget.PinTarget(gate.id(), 0), "")), "command.addProbes"));
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 120, 120);
        overlay.paint(graphics, new RenderState(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return image;
    }

    private static Color blend(Color over, int alpha, Color under) {
        double opacity = alpha / 255.0;
        return new Color((int) Math.round(over.getRed() * opacity + under.getRed() * (1 - opacity)),
                (int) Math.round(over.getGreen() * opacity + under.getGreen() * (1 - opacity)),
                (int) Math.round(over.getBlue() * opacity + under.getBlue() * (1 - opacity)));
    }

    private void assertBadgeDrawnBesideThePin() {
        BufferedImage image = paint();
        Color fill = blend(scheme.selection(), ProbeMarkerOverlay.ALPHA, scheme.background());
        Color inside = new Color(image.getRGB(69, 76));
        assertThat(inside.getRed()).isCloseTo(fill.getRed(), within(2));
        assertThat(inside.getGreen()).isCloseTo(fill.getGreen(), within(2));
        assertThat(inside.getBlue()).isCloseTo(fill.getBlue(), within(2));
        int background = scheme.background().getRGB();
        assertThat(image.getRGB(64, 88)).as("the pin stays uncovered").isEqualTo(background);
        assertThat(image.getRGB(66, 82)).isEqualTo(background);
        assertThat(image.getRGB(74, 84)).as("below the badge").isEqualTo(background);
        int glyph = 0;
        for (int x = 70; x < 78; x++) {
            for (int y = 74; y < 79; y++) {
                if (image.getRGB(x, y) != new Color(image.getRGB(69, 76)).getRGB()) {
                    glyph++;
                }
            }
        }
        assertThat(glyph).as("the square-wave glyph").isGreaterThan(8);
    }

    @Test
    void aMonitoredPinGetsABadgeInTheLightScheme() {
        probeTheOutput("");
        assertBadgeDrawnBesideThePin();
    }

    @Test
    void theBadgeFollowsTheDarkScheme() {
        probeTheOutput("");
        scheme = ColorSchemes.DARK;
        assertBadgeDrawnBesideThePin();
    }

    @Test
    void anInputPinsBadgeGoesOutsideTheComponentNotOverItsBody() {
        probeTheInput();
        BufferedImage image = paint();
        Color fill = blend(scheme.selection(), ProbeMarkerOverlay.ALPHA, scheme.background());
        Color outside = new Color(image.getRGB(17, 76)); // screen x 16–28, y 72–80
        assertThat(outside.getRed()).isCloseTo(fill.getRed(), within(2));
        assertThat(outside.getGreen()).isCloseTo(fill.getGreen(), within(2));
        assertThat(outside.getBlue()).isCloseTo(fill.getBlue(), within(2));
        int background = scheme.background().getRGB();
        assertThat(image.getRGB(40, 76)).as("nothing over the gate body (x 32–64)").isEqualTo(background);
        assertThat(image.getRGB(32, 88)).as("the pin stays uncovered").isEqualTo(background);
    }

    @Test
    void nothingIsDrawnWithoutChannels() {
        BufferedImage image = paint();
        assertThat(image.getRGB(69, 76)).isEqualTo(scheme.background().getRGB());
    }

    @Test
    void theMarkerListIsKeptUntilTheCircuitOrTheChannelsChange() {
        probeTheOutput("");
        List<ProbeMarker> markers = overlay.markers();
        assertThat(overlay.markers()).isSameAs(markers);
        paint();
        assertThat(overlay.markers()).isSameAs(markers);

        place(openDocument, StandardComponentTypes.SWITCH, 30, 30, Map.of());
        assertThat(overlay.markers()).isNotSameAs(markers).isEqualTo(markers);
        List<ProbeMarker> afterEdit = overlay.markers();
        probeTheOutput("salida");
        assertThat(overlay.markers()).isNotSameAs(afterEdit);
        assertThat(overlay.markers().get(0).names()).containsExactly("salida");
    }

    @Test
    void markerAtFindsTheBadgeAndNotThePin() {
        probeTheOutput("");
        assertThat(overlay.markerAt(new Point2D.Double(9, 9.5))).map(ProbeMarker::probeIds)
                .contains(List.of("p1"));
        assertThat(overlay.markerAt(new Point2D.Double(8, 11))).isEmpty();
        assertThat(overlay.markerAt(new Point2D.Double(11, 9.5))).isEmpty();
        assertThat(ProbeMarkerOverlay.badge(new GridPoint(8, 11), ProbeMarker.Side.RIGHT).getBounds2D())
                .isEqualTo(new java.awt.geom.Rectangle2D.Double(8.5, 9, 1.5, 1));
    }

    @Test
    void theBadgeIsMirroredAwayFromTheBodyOnEachSide() {
        GridPoint pin = new GridPoint(8, 11);
        assertThat(ProbeMarkerOverlay.badge(pin, ProbeMarker.Side.TOP).getBounds2D())
                .isEqualTo(new java.awt.geom.Rectangle2D.Double(8.5, 9, 1.5, 1));
        assertThat(ProbeMarkerOverlay.badge(pin, ProbeMarker.Side.LEFT).getBounds2D())
                .isEqualTo(new java.awt.geom.Rectangle2D.Double(6, 9, 1.5, 1));
        assertThat(ProbeMarkerOverlay.badge(pin, ProbeMarker.Side.BOTTOM).getBounds2D())
                .isEqualTo(new java.awt.geom.Rectangle2D.Double(8.5, 12, 1.5, 1));
    }

    @Test
    void aPressOnTheGateBodyIsNotAMarker() {
        probeTheInput();
        assertThat(overlay.markerAt(new Point2D.Double(2.5, 9.5))).map(ProbeMarker::probeIds)
                .contains(List.of("p2"));
        assertThat(overlay.markerAt(new Point2D.Double(5, 9.5))).as("where the old badge would have been").isEmpty();
    }
}
