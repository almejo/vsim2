package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The badges of the monitored nets on the canvas (M6.1-E): a rounded rectangle of 1.5 × 1 grid units with a tiny
 * square wave, in the scheme's selection color at 80 % opacity, on the outer side of the anchor pin: half a unit
 * sideways and one unit up from it, mirrored to the left for a pin on the left edge of its component and below for a
 * pin on the bottom edge, so it never covers the pin or the component's body (a press on the body still reaches the
 * tool). Painted after the wires, in document coordinates; the marker list is cached per document, revision, probes
 * and netlist.
 */
final class ProbeMarkerOverlay implements CanvasOverlay {

    /** Sideways distance between the anchor and the near edge of the badge, in grid units. */
    static final double OFFSET = 0.5;
    static final double LIFT = 1;
    static final double WIDTH = 1.5;
    static final double HEIGHT = 1;
    static final int ALPHA = 204;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double CORNER = 4;
    private static final double GLYPH_MARGIN = 2;
    private static final BasicStroke GLYPH_STROKE = new BasicStroke(1f);

    private final OpenDocument openDocument;
    private final Supplier<ColorScheme> scheme;
    private CacheKey cacheKey;
    private List<ProbeMarker> cached = List.of();

    /** {@code scheme} is read on every paint. */
    ProbeMarkerOverlay(OpenDocument openDocument, Supplier<ColorScheme> scheme) {
        this.openDocument = openDocument;
        this.scheme = scheme;
    }

    /** The markers of the document as it is now (the same list until something they depend on changes). */
    List<ProbeMarker> markers() {
        return markers(openDocument.document(), openDocument.netlist(), openDocument.netlistToken());
    }

    /** The marker whose badge contains {@code gridPoint} (grid units), if any. */
    Optional<ProbeMarker> markerAt(Point2D gridPoint) {
        return markers().stream().filter(marker -> badge(marker.anchor(), marker.side()).contains(gridPoint))
                .findFirst();
    }

    /**
     * The badge of a marker hanging from {@code anchor} on a pin of the {@code side} edge, in grid units: right and up
     * for {@code RIGHT}/{@code TOP}, left and up for {@code LEFT}, right and down for {@code BOTTOM}.
     */
    static Rectangle2D badge(GridPoint anchor, ProbeMarker.Side side) {
        double left = side == ProbeMarker.Side.LEFT ? anchor.x() - OFFSET - WIDTH : anchor.x() + OFFSET;
        double top = side == ProbeMarker.Side.BOTTOM ? anchor.y() + LIFT : anchor.y() - LIFT - HEIGHT;
        return new Rectangle2D.Double(left, top, WIDTH, HEIGHT);
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        List<ProbeMarker> markers = markers(state.document(), state.netlist(), state.netlistToken());
        if (markers.isEmpty()) {
            return;
        }
        ColorScheme colors = scheme.get();
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setStroke(GLYPH_STROKE);
            for (ProbeMarker marker : markers) {
                Rectangle2D box = badge(marker.anchor(), marker.side());
                double left = box.getX() * UNIT;
                double top = box.getY() * UNIT;
                double width = box.getWidth() * UNIT;
                double height = box.getHeight() * UNIT;
                local.setColor(PainterSupport.withAlpha(colors.selection(), ALPHA));
                local.fill(new RoundRectangle2D.Double(left, top, width, height, CORNER, CORNER));
                local.setColor(colors.background());
                local.draw(squareWave(left + GLYPH_MARGIN, top + GLYPH_MARGIN, width - 2 * GLYPH_MARGIN,
                        height - 2 * GLYPH_MARGIN));
            }
        } finally {
            local.dispose();
        }
    }

    /** Low, high, low across the box: the waveform glyph. */
    private static Path2D squareWave(double left, double top, double width, double height) {
        double bottom = top + height;
        Path2D wave = new Path2D.Double();
        wave.moveTo(left, bottom);
        wave.lineTo(left + width * 0.25, bottom);
        wave.lineTo(left + width * 0.25, top);
        wave.lineTo(left + width * 0.625, top);
        wave.lineTo(left + width * 0.625, bottom);
        wave.lineTo(left + width, bottom);
        return wave;
    }

    private List<ProbeMarker> markers(CircuitDocument document, Netlist netlist, long netlistToken) {
        CacheKey key = new CacheKey(document, document.revision(), document.probes(), netlistToken);
        if (!key.equals(cacheKey)) {
            cacheKey = key;
            cached = ProbeMarkers.of(document, netlist, openDocument.registry(),
                    probe -> ProbeNames.displayName(probe, document, netlist, openDocument.registry()));
        }
        return cached;
    }

    /** What the markers depend on; the document by identity. */
    private record CacheKey(CircuitDocument document, long revision, List<Probe> probes, long netlistToken) {
    }
}
