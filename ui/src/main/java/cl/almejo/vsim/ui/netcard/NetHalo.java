package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.theme.ThemeColors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The halo of the hovered net, over the circuit in document coordinates (so the Lupa shows it too and no export
 * does): a band in the theme's accent on both sides of every wire of the net and around its junctions, hollow along
 * the wire so the wire's own value colour stays readable, and a ring on each driver and reader pin (a driver's ring
 * filled). It fades with the card's grace. The accent is read at paint time.
 */
final class NetHalo implements CanvasOverlay {

    /** What is lit and how strongly (1 = fully, 0 = gone). */
    record Shown(NetSummary summary, float opacity) {
        Shown {
            Objects.requireNonNull(summary, "summary");
        }
    }

    /** Document pixels per grid unit. */
    private static final double UNIT = 8;
    /** The outer width of the band: wider than a selected wire's halo (8 px), so the two read apart. */
    private static final float BAND_WIDTH = 13f;
    /** The hollow along the wire: a bus wire is 4.5 px wide, a shorted one 5 px. */
    private static final float HOLLOW_WIDTH = 6f;
    private static final double JUNCTION_RADIUS = 6.5;
    private static final double RING_RADIUS = 5.5;
    private static final BasicStroke RING = new BasicStroke(1.75f);
    /** Alpha of the band and the filled ring at full strength: strong enough on both light and dark boards. */
    private static final int BAND_ALPHA = 120;
    private static final int RING_ALPHA = 230;

    private final Supplier<Optional<Shown>> shown;

    NetHalo(Supplier<Optional<Shown>> shown) {
        this.shown = Objects.requireNonNull(shown, "shown");
    }

    /** The band around the wires and junctions of {@code summary}'s net, without the wires themselves. */
    static Area area(NetSummary summary, CircuitDocument document) {
        BasicStroke band = new BasicStroke(BAND_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke hollow = new BasicStroke(HOLLOW_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Area outer = new Area();
        Area inner = new Area();
        for (ElementId wireId : summary.wires()) {
            document.wire(wireId).ifPresent(wire -> {
                Shape line = new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                        wire.end().x() * UNIT, wire.end().y() * UNIT);
                outer.add(new Area(band.createStrokedShape(line)));
                inner.add(new Area(hollow.createStrokedShape(line)));
            });
        }
        for (GridPoint point : summary.points()) {
            outer.add(new Area(circle(point, JUNCTION_RADIUS)));
        }
        outer.subtract(inner);
        return outer;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Optional<Shown> lit = shown.get();
        if (lit.isEmpty() || lit.get().opacity() <= 0f) {
            return;
        }
        Graphics2D halo = (Graphics2D) graphics.create();
        try {
            halo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color accent = ThemeColors.accent();
            float opacity = lit.get().opacity();
            NetSummary summary = lit.get().summary();
            halo.setColor(PainterSupport.withAlpha(accent, Math.round(BAND_ALPHA * opacity)));
            halo.fill(area(summary, state.document()));
            paintRings(halo, summary, accent, opacity);
        } finally {
            halo.dispose();
        }
    }

    private static void paintRings(Graphics2D graphics, NetSummary summary, Color accent, float opacity) {
        Color ring = PainterSupport.withAlpha(accent, Math.round(RING_ALPHA * opacity));
        graphics.setStroke(RING);
        for (NetEnd driver : summary.drivers()) {
            Shape circle = circle(driver.position(), RING_RADIUS);
            graphics.setColor(PainterSupport.withAlpha(accent, Math.round(BAND_ALPHA * opacity)));
            graphics.fill(circle);
            graphics.setColor(ring);
            graphics.draw(circle);
        }
        graphics.setColor(ring);
        for (NetEnd reader : summary.readers()) {
            graphics.draw(circle(reader.position(), RING_RADIUS));
        }
    }

    private static Shape circle(GridPoint point, double radius) {
        return new Ellipse2D.Double(point.x() * UNIT - radius, point.y() * UNIT - radius, 2 * radius, 2 * radius);
    }
}
