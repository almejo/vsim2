package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.DraftProblem;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.ui.appearance.ShapeCanvas.Coincidence;
import cl.almejo.vsim.ui.appearance.ShapeCanvas.HandleRef;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.tools.RubberBand;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.appearance.ShapeCanvas.BAND_FILL_ALPHA;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.BAND_WIDTH;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.BOX_HANDLE_OFFSET;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.UNIT;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.handlePoint;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.paintAppearance;

/** How the shape canvas paints its grid, box, drawing and overlays, reading the canvas's state at paint time. */
final class ShapeCanvasPainter {
    /** Figure strokes and pin markers keep this size on screen whatever the zoom. */
    private static final float SCREEN_STROKE_WIDTH = 1.6f;
    /** Pins are drawn larger than the figure's nodes (round, 4 px) and the edge notches, so they are never mistaken. */
    private static final double SCREEN_PIN_RADIUS = 6;
    private static final double PIN_RING_RADIUS = 10;
    private static final int NOTCH_LENGTH = 4;
    /** Handles and their lines, in screen pixels: smaller than a node, so the node stays the thing to grab. */
    private static final double HANDLE_RADIUS = 3.5;
    private static final double ACTIVE_HANDLE_RADIUS = 4.5;
    private static final double HANDLE_LINE_WIDTH = 1.2;
    /** A corner node's diamond, in screen pixels to each tip: about the size of a round node. */
    private static final double CORNER_MARKER_RADIUS = 5.5;
    private final ShapeCanvas canvas;

    ShapeCanvasPainter(ShapeCanvas canvas) {
        this.canvas = canvas;
    }

    private double zoom() {
        return canvas.zoom();
    }

    private Point screen(int x, int y) {
        return canvas.screen(x, y);
    }

    /** Everything under the callouts, in the box's local pixels, then the callouts on top in screen pixels. */
    void paint(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        try {
            ColorScheme scheme = canvas.colors.get();
            surface.setColor(scheme.background());
            surface.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintGrid(surface, scheme);
            ComponentAppearance appearance = canvas.current.get().appearance();
            surface.transform(canvas.viewport.documentTransform());
            paintAppearance(surface, canvas.definition, appearance, scheme, true,
                    (float) (SCREEN_STROKE_WIDTH / zoom()),
                    SCREEN_PIN_RADIUS / (PainterSupport.PIN_DOT_RADIUS * zoom()));
            paintGuides(surface, scheme, appearance.size());
            paintBox(surface, scheme, appearance);
            surface.setColor(scheme.selection());
            if (canvas.tool == ShapeTool.SELECT) {
                if (!canvas.onlyTextsSelected()) {
                    canvas.current.get().selectionBounds().ifPresent(box -> paintSelection(surface, box));
                }
                paintTextBlocks(surface, scheme);
            }
            paintPinRings(surface, scheme, appearance);
            surface.setColor(scheme.selection());
            paintCoincidences(surface, scheme);
            paintOffEdgePins(surface);
            if (canvas.tool == ShapeTool.NODES || canvas.isNodeTool()) {
                paintNodes(surface);
            }
            if (canvas.tool == ShapeTool.CURVE) {
                paintCurveTool(surface, scheme);
            }
            if (canvas.nodeAction() == ShapeTool.ADD_NODE) {
                paintInsertion(surface);
            } else if (canvas.nodeAction() == ShapeTool.REMOVE_NODE) {
                paintRemoval(surface, scheme);
            }
            if (canvas.bandShown()) {
                paintBand(surface, scheme);
            }
            paintPathInProgress(surface, scheme);
        } finally {
            surface.dispose();
        }
        Graphics2D overlay = (Graphics2D) graphics.create();
        try {
            overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            overlay.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintCallouts(overlay, canvas.colors.get());
        } finally {
            overlay.dispose();
        }
    }

    /** A dot on every grid point, when the zoom leaves them far enough apart. */
    private void paintGrid(Graphics2D surface, ColorScheme scheme) {
        double spacing = canvas.viewport.pixelsPerUnit();
        if (spacing >= 4) {
            surface.setColor(scheme.grid());
            Point2D origin = canvas.viewport.gridToScreen(0, 0);
            for (double x = origin.getX() % spacing; x < canvas.getWidth(); x += spacing) {
                for (double y = origin.getY() % spacing; y < canvas.getHeight(); y += spacing) {
                    surface.fill(new Ellipse2D.Double(x - 0.8, y - 0.8, 1.6, 1.6));
                }
            }
        }
    }

    /** The box's dashed outline, its label and, while the pins can move, its resize handle. */
    private void paintBox(Graphics2D surface, ColorScheme scheme, ComponentAppearance appearance) {
        surface.setColor(PainterSupport.withAlpha(scheme.text(), 130));
        surface.setStroke(new BasicStroke((float) (1 / zoom()), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{(float) (5 / zoom())}, 0));
        surface.draw(new Rectangle2D.Double(0, 0, appearance.size().width() * UNIT, appearance.size().height() * UNIT));
        surface.setFont(TextFonts.labelFont().deriveFont((float) (10 / zoom())));
        surface.drawString(Messages.text("shape.box.label"), 0, (float) (-10 / zoom()));
        if (!canvas.current.get().pinsLocked()) {
            double right = appearance.size().width() * UNIT;
            double bottom = appearance.size().height() * UNIT;
            double offset = BOX_HANDLE_OFFSET / zoom();
            surface.draw(new Line2D.Double(right, bottom, right + offset, bottom + offset));
            surface.fill(new Ellipse2D.Double(right + offset - 3 / zoom(),
                    bottom + offset - 3 / zoom(), 6 / zoom(), 6 / zoom()));
        }
    }

    /** A ring around the selected pin and, lighter, around the hovered one. */
    private void paintPinRings(Graphics2D surface, ColorScheme scheme, ComponentAppearance appearance) {
        for (PinPlacement placement : appearance.pins()) {
            boolean selected = canvas.pin.filter(placement.source()::equals).isPresent();
            boolean hovered = canvas.hoveredPin.filter(placement.source()::equals).isPresent();
            if (selected || hovered) {
                surface.setColor(hovered && !selected ? PainterSupport.withAlpha(scheme.selection(), 200)
                        : scheme.selection());
                surface.setStroke(new BasicStroke((float) ((selected ? 2 : 1.5) / zoom())));
                surface.draw(new Ellipse2D.Double(placement.offset().x() * UNIT - PIN_RING_RADIUS / zoom(),
                        placement.offset().y() * UNIT - PIN_RING_RADIUS / zoom(), 2 * PIN_RING_RADIUS / zoom(),
                        2 * PIN_RING_RADIUS / zoom()));
            }
        }
    }

    /** The rubber band from where the press started to the pointer, with its marching ants. */
    private void paintBand(Graphics2D surface, ColorScheme scheme) {
        RubberBand.paint(surface, new Rectangle2D.Double(
                Math.min(canvas.start.x(), canvas.bandEnd.x()) / 8.0 * UNIT,
                Math.min(canvas.start.y(), canvas.bandEnd.y()) / 8.0 * UNIT,
                Math.abs(canvas.start.x() - canvas.bandEnd.x()) / 8.0 * UNIT,
                Math.abs(canvas.start.y() - canvas.bandEnd.y()) / 8.0 * UNIT), scheme.selection(), BAND_FILL_ALPHA,
                BAND_WIDTH, canvas.marchingAnts.phase());
    }

    /** Notches on the box edge, and while a pin is dragged its free and occupied points; the colours are the scheme's. */
    private void paintGuides(Graphics2D surface, ColorScheme scheme, GridSize size) {
        Graphics2D guide = (Graphics2D) surface.create();
        try {
            guide.setStroke(new BasicStroke((float) (1.5 / zoom())));
            guide.setColor(PainterSupport.withAlpha(scheme.text(), 150));
            double notch = NOTCH_LENGTH / zoom();
            for (GridPoint point : canvas.edgeNotches()) {
                double x = point.x() * UNIT;
                double y = point.y() * UNIT;
                boolean onSide = point.x() == 0 || point.x() == size.width();
                boolean corner = onSide && (point.y() == 0 || point.y() == size.height());
                if (onSide || corner) {
                    guide.draw(new Line2D.Double(x - notch, y, x + notch, y));
                }
                if (!onSide || corner) {
                    guide.draw(new Line2D.Double(x, y - notch, x, y + notch));
                }
            }
            Optional<GridPoint> nearest = canvas.nearestFreePinPoint();
            for (GridPoint point : canvas.freePinPoints()) {
                boolean strong = nearest.filter(point::equals).isPresent();
                double radius = (strong ? 5 : 2) / zoom();
                guide.setColor(strong ? scheme.selection() : PainterSupport.withAlpha(scheme.selection(), 140));
                guide.setStroke(new BasicStroke((float) ((strong ? 2 : 1.2) / zoom())));
                Ellipse2D ring = new Ellipse2D.Double(point.x() * UNIT - radius, point.y() * UNIT - radius,
                        2 * radius, 2 * radius);
                if (strong) {
                    guide.draw(ring);
                } else {
                    guide.fill(ring);
                }
            }
            guide.setColor(scheme.wireConflict());
            guide.setStroke(new BasicStroke((float) (1.5 / zoom())));
            double radius = 5 / zoom();
            double slash = radius * 0.7;
            for (GridPoint point : canvas.occupiedPinPoints()) {
                double x = point.x() * UNIT;
                double y = point.y() * UNIT;
                guide.draw(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
                guide.draw(new Line2D.Double(x - slash, y + slash, x + slash, y - slash));
            }
        } finally {
            guide.dispose();
        }
    }

    /** A red ring and dot on every pin that shares its point: the icon and the callout carry the meaning too. */
    private void paintCoincidences(Graphics2D surface, ColorScheme scheme) {
        Map<GridPoint, Boolean> marked = new HashMap<>();
        for (Coincidence found : canvas.coincidences()) {
            if (marked.put(found.offset(), true) != null) {
                continue;
            }
            double x = found.offset().x() * UNIT;
            double y = found.offset().y() * UNIT;
            surface.setColor(scheme.wireConflict());
            surface.setStroke(new BasicStroke((float) (1.5 / zoom())));
            surface.draw(new Ellipse2D.Double(x - 8 / zoom(), y - 8 / zoom(), 16 / zoom(), 16 / zoom()));
            surface.fill(new Ellipse2D.Double(x - 4 / zoom(), y - 4 / zoom(), 8 / zoom(), 8 / zoom()));
        }
    }

    /** An amber ring on every pin that is not on the box edge: the row's triangle and the warning line say the rest. */
    private void paintOffEdgePins(Graphics2D surface) {
        Set<String> offEdge = new HashSet<>();
        for (DraftProblem problem : canvas.current.get().problems()) {
            if (problem.kind() == DraftProblem.Kind.PIN_OFF_EDGE) {
                offEdge.addAll(problem.pins());
            }
        }
        surface.setColor(ReadableColors.warningText());
        surface.setStroke(new BasicStroke((float) (1.5 / zoom())));
        for (PinPlacement placement : canvas.current.get().appearance().pins()) {
            if (offEdge.contains(canvas.pinName(placement.source()))) {
                surface.draw(new Ellipse2D.Double(placement.offset().x() * UNIT - 9 / zoom(),
                        placement.offset().y() * UNIT - 9 / zoom(), 18 / zoom(), 18 / zoom()));
            }
        }
    }

    private void paintCallouts(Graphics2D overlay, ColorScheme scheme) {
        Optional<String> coincidence = canvas.coincidenceCallout();
        if (coincidence.isPresent()) {
            PinPlacement selected = canvas.current.get().appearance().pins().stream()
                    .filter(placement -> canvas.pin.filter(placement.source()::equals).isPresent()).findFirst()
                    .orElseThrow();
            paintCallout(overlay, screen(selected.offset().x() * 8, selected.offset().y() * 8),
                    new String[]{coincidence.get()}, scheme.wireConflict(), true, true);
        } else if (canvas.pinCallout().isPresent()) {
            PinPlacement selected = canvas.current.get().appearance().pins().stream()
                    .filter(placement -> canvas.pin.filter(placement.source()::equals).isPresent()).findFirst()
                    .orElseThrow();
            paintCallout(overlay, screen(selected.offset().x() * 8, selected.offset().y() * 8),
                    new String[]{canvas.pinCallout().get()}, scheme.selection(), false, true);
        }
        canvas.dragTip.ifPresent(tip -> paintCallout(overlay, canvas.pointer,
                tip.split("\n"), scheme.selection(), false, false));
        // While a handle is dragged the label next to its node would cover it; the drag tip speaks instead.
        canvas.nodeLabel().filter(label -> canvas.grabbedHandle.isEmpty()).ifPresent(label -> {
            SymbolPoint point = canvas.chosenPoint((SymbolPath) canvas.current.get().elements().get(canvas.pathIndex));
            Point at = screen(point.x(), point.y());
            overlay.setFont(TextFonts.labelFont().deriveFont(Font.BOLD, 13f));
            FontMetrics metrics = overlay.getFontMetrics();
            int width = metrics.stringWidth(label) + 10;
            int x = Math.min(at.x + 12, Math.max(0, canvas.getWidth() - width - 2));
            int y = Math.max(metrics.getHeight() + 2, at.y - 10);
            overlay.setColor(PainterSupport.withAlpha(scheme.background(), 220));
            overlay.fill(new RoundRectangle2D.Double(x - 4, y - metrics.getAscent() - 1, width, metrics.getHeight() + 2, 6, 6));
            overlay.setColor(scheme.text());
            overlay.drawString(label, x, y);
        });
    }

    /**
     * A rounded label next to {@code anchor}, kept inside the canvas. With {@code warning} it starts with a drawn
     * «no entry» sign, so the meaning never depends on its colour alone.
     */
    private void paintCallout(Graphics2D overlay, Point anchor, String[] lines, Color fill, boolean warning,
                              boolean above) {
        overlay.setFont(TextFonts.labelFont().deriveFont(Font.BOLD, 13f));
        FontMetrics metrics = overlay.getFontMetrics();
        int iconSize = warning ? 14 : 0;
        int iconGap = warning ? 6 : 0;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line));
        }
        int width = textWidth + iconSize + iconGap + 16;
        int height = metrics.getHeight() * lines.length + 10;
        int x = Math.clamp(anchor.x + 16, 2, Math.max(2, canvas.getWidth() - width - 2));
        int y = Math.clamp(above ? anchor.y - 18 - height : anchor.y + 18, 2,
                Math.max(2, canvas.getHeight() - height - 2));
        overlay.setColor(fill);
        overlay.fill(new RoundRectangle2D.Double(x, y, width, height, 8, 8));
        Color ink = readableOn(fill);
        overlay.setColor(ink);
        int textX = x + 8;
        if (warning) {
            int iconY = y + (height - iconSize) / 2;
            overlay.setStroke(new BasicStroke(2f));
            overlay.draw(new Ellipse2D.Double(textX + 1, iconY + 1, iconSize - 2, iconSize - 2));
            overlay.draw(new Line2D.Double(textX + 4, iconY + iconSize / 2.0, textX + iconSize - 4, iconY + iconSize / 2.0));
            textX += iconSize + iconGap;
        }
        for (int line = 0; line < lines.length; line++) {
            overlay.drawString(lines[line], textX, y + 5 + metrics.getAscent() + line * metrics.getHeight());
        }
    }

    private static Color readableOn(Color fill) {
        double luminance = (0.2126 * fill.getRed() + 0.7152 * fill.getGreen() + 0.0722 * fill.getBlue()) / 255;
        return luminance < 0.6 ? Color.WHITE : Color.BLACK;
    }

    /** Every selected text in a rectangle around its whole block; the hovered one in a lighter outline. */
    private void paintTextBlocks(Graphics2D surface, ColorScheme scheme) {
        List<SymbolElement> elements = canvas.current.get().elements();
        if (canvas.hoveredText >= 0 && canvas.hoveredText < elements.size()
                && !canvas.current.get().selection().contains(canvas.hoveredText)
                && elements.get(canvas.hoveredText) instanceof SymbolText hovered) {
            surface.setColor(PainterSupport.withAlpha(scheme.selection(), 110));
            surface.setStroke(new BasicStroke((float) (1.2 / zoom())));
            surface.draw(canvas.paddedTextBlock(hovered));
        }
        surface.setColor(scheme.selection());
        surface.setStroke(new BasicStroke((float) (1.6 / zoom())));
        for (int index : canvas.current.get().selection()) {
            if (index < elements.size() && elements.get(index) instanceof SymbolText selected) {
                surface.draw(canvas.paddedTextBlock(selected));
            }
        }
    }

    private void paintSelection(Graphics2D surface, SymbolDraft.Box box) {
        surface.draw(new Rectangle2D.Double(box.left() / 8.0 * UNIT, box.top() / 8.0 * UNIT,
                box.width() / 8.0 * UNIT, box.height() / 8.0 * UNIT));
        for (SymbolDraft.Handle handle : SymbolDraft.Handle.values()) {
            SymbolPoint point = handlePoint(handle, box);
            surface.fill(new Rectangle2D.Double(point.x() / 8.0 * UNIT - 3 / zoom(),
                    point.y() / 8.0 * UNIT - 3 / zoom(), 6 / zoom(), 6 / zoom()));
        }
    }

    /**
     * Every node as a round point (the chosen one filled), a corner between two curves as a diamond; every curve
     * handle joined to its node by a thin line (straight through a smooth node), drawn over the nodes so it can be
     * grabbed.
     */
    private void paintNodes(Graphics2D surface) {
        SymbolDraft view = canvas.current.get();
        List<SymbolElement> elements = view.elements();
        List<HandleRef> handles = canvas.curveHandles();
        paintHandleLines(surface, handles);
        surface.setStroke(new BasicStroke((float) (1.5 / zoom())));
        for (int index = 0; index < elements.size(); index++) {
            if (!(elements.get(index) instanceof SymbolPath path)) {
                continue;
            }
            for (int nodeNumber = 0; nodeNumber < path.nodes().size(); nodeNumber++) {
                SymbolPoint point = path.nodes().get(nodeNumber).point();
                boolean chosen = index == canvas.pathIndex && nodeNumber == canvas.nodeIndex;
                Color fill = chosen ? canvas.colors.get().selection() : canvas.colors.get().background();
                if (path.hasTwoHandles(nodeNumber) && !view.smooth(index, nodeNumber)) {
                    paintDiamond(surface, point, CORNER_MARKER_RADIUS, fill, canvas.colors.get().componentStroke());
                } else {
                    double x = point.x() / 8.0 * UNIT;
                    double y = point.y() / 8.0 * UNIT;
                    Shape marker = new Ellipse2D.Double(x - 4 / zoom(), y - 4 / zoom(), 8 / zoom(), 8 / zoom());
                    surface.setColor(fill);
                    surface.fill(marker);
                    surface.setColor(canvas.colors.get().componentStroke());
                    surface.draw(marker);
                }
            }
        }
        paintHandles(surface, handles);
    }

    private void paintHandleLines(Graphics2D surface, List<HandleRef> handles) {
        handles.forEach(reference -> paintHandleLine(surface, canvas.handleNode(reference),
                canvas.handleLocation(reference)));
    }

    /** The handle being dragged, else the one under the pointer, is drawn larger. */
    private void paintHandles(Graphics2D surface, List<HandleRef> handles) {
        Optional<HandleRef> active = canvas.grabbedHandle.or(() -> canvas.hoveredHandle);
        handles.forEach(reference -> paintHandle(surface, canvas.handleLocation(reference),
                active.filter(reference::equals).isPresent()));
    }

    /** The thin line from a node to one of its handles, in the selection colour. */
    private void paintHandleLine(Graphics2D surface, SymbolPoint node, SymbolPoint handle) {
        surface.setColor(canvas.colors.get().selection());
        surface.setStroke(new BasicStroke((float) (HANDLE_LINE_WIDTH / zoom())));
        surface.draw(new Line2D.Double(node.x() / 8.0 * UNIT, node.y() / 8.0 * UNIT, handle.x() / 8.0 * UNIT,
                handle.y() / 8.0 * UNIT));
    }

    /**
     * A handle: a dot in the selection colour with a thin ring of the background around it, so it stands out on the
     * figure's stroke in both themes; larger under the pointer and while dragged.
     */
    private void paintHandle(Graphics2D surface, SymbolPoint handle, boolean active) {
        double radius = (active ? ACTIVE_HANDLE_RADIUS : HANDLE_RADIUS) / zoom();
        Shape dot = new Ellipse2D.Double(handle.x() / 8.0 * UNIT - radius, handle.y() / 8.0 * UNIT - radius,
                2 * radius, 2 * radius);
        surface.setColor(canvas.colors.get().selection());
        surface.fill(dot);
        surface.setColor(canvas.colors.get().background());
        surface.setStroke(new BasicStroke((float) (1 / zoom())));
        surface.draw(dot);
    }

    private void paintInsertion(Graphics2D surface) {
        canvas.insertion.ifPresent(hit -> {
            SymbolPath path = (SymbolPath) canvas.current.get().elements().get(hit.pathIndex());
            Shape segment = AffineTransform.getScaleInstance(UNIT / 8, UNIT / 8)
                    .createTransformedShape(ShapeSegments.shape(path, hit.segmentIndex()));
            surface.setColor(canvas.colors.get().selection());
            surface.setStroke(new BasicStroke((float) (3 / zoom()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            surface.draw(segment);
            surface.fill(new Ellipse2D.Double(hit.point().x() / 8.0 * UNIT - 3 / zoom(),
                    hit.point().y() / 8.0 * UNIT - 3 / zoom(), 6 / zoom(), 6 / zoom()));
        });
    }

    /**
     * The multiline or polygon being drawn: its placed segments and nodes, the rubber band from the last node to the
     * pointer (and, for a polygon, back to the first node), and a ring on the first node when a click there closes it.
     */
    private void paintPathInProgress(Graphics2D surface, ColorScheme scheme) {
        List<SymbolPoint> pathNodes = canvas.pathDrawing.pathNodes;
        if (pathNodes.isEmpty()) {
            return;
        }
        Path2D placed = new Path2D.Double();
        placed.moveTo(pathNodes.getFirst().x() / 8.0 * UNIT, pathNodes.getFirst().y() / 8.0 * UNIT);
        for (SymbolPoint node : pathNodes.subList(1, pathNodes.size())) {
            placed.lineTo(node.x() / 8.0 * UNIT, node.y() / 8.0 * UNIT);
        }
        surface.setColor(scheme.componentStroke());
        surface.setStroke(new BasicStroke((float) (SCREEN_STROKE_WIDTH / zoom()), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        surface.draw(placed);
        boolean closing = canvas.pathDrawing.closesOnFirstNode(canvas.pointer);
        SymbolPoint last = pathNodes.getLast();
        SymbolPoint target = closing ? pathNodes.getFirst() : canvas.point(canvas.pointer);
        surface.setColor(scheme.selection());
        surface.setStroke(new BasicStroke((float) (1.2 / zoom()), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                new float[]{(float) (4 / zoom()), (float) (3 / zoom())}, 0));
        surface.draw(new Line2D.Double(last.x() / 8.0 * UNIT, last.y() / 8.0 * UNIT, target.x() / 8.0 * UNIT,
                target.y() / 8.0 * UNIT));
        if (canvas.tool == ShapeTool.POLYGON && pathNodes.size() >= 2 && !closing) {
            SymbolPoint first = pathNodes.getFirst();
            surface.setColor(PainterSupport.withAlpha(scheme.selection(), 110));
            surface.draw(new Line2D.Double(target.x() / 8.0 * UNIT, target.y() / 8.0 * UNIT, first.x() / 8.0 * UNIT,
                    first.y() / 8.0 * UNIT));
        }
        surface.setStroke(new BasicStroke((float) (1.5 / zoom())));
        for (SymbolPoint node : pathNodes) {
            Shape marker = new Ellipse2D.Double(node.x() / 8.0 * UNIT - 3 / zoom(), node.y() / 8.0 * UNIT - 3 / zoom(),
                    6 / zoom(), 6 / zoom());
            surface.setColor(scheme.background());
            surface.fill(marker);
            surface.setColor(scheme.componentStroke());
            surface.draw(marker);
        }
        if (closing) {
            SymbolPoint first = pathNodes.getFirst();
            surface.setColor(scheme.selection());
            surface.setStroke(new BasicStroke((float) (2 / zoom())));
            surface.draw(new Ellipse2D.Double(first.x() / 8.0 * UNIT - 7 / zoom(), first.y() / 8.0 * UNIT - 7 / zoom(),
                    14 / zoom(), 14 / zoom()));
        }
    }

    /**
     * «Curvar tramo»: the segment under the pointer in the selection colour (unless one is being bent) and the handles
     * of every curved segment, each joined to its node.
     */
    private void paintCurveTool(Graphics2D surface, ColorScheme scheme) {
        List<SymbolElement> elements = canvas.current.get().elements();
        if (canvas.curving.isEmpty()) {
            canvas.curveTarget.filter(target -> target.pathIndex() < elements.size()
                    && elements.get(target.pathIndex()) instanceof SymbolPath).ifPresent(target -> {
                SymbolPath path = (SymbolPath) elements.get(target.pathIndex());
                Shape segment = AffineTransform.getScaleInstance(UNIT / 8, UNIT / 8)
                        .createTransformedShape(ShapeSegments.shape(path, target.segment()));
                surface.setColor(scheme.selection());
                surface.setStroke(new BasicStroke((float) (3 / zoom()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                surface.draw(segment);
            });
        }
        List<HandleRef> handles = canvas.curveHandles();
        paintHandleLines(surface, handles);
        paintHandles(surface, handles);
    }

    /** A corner node: a diamond centred on {@code point} (eighths), {@code radius} screen pixels to each tip. */
    private void paintDiamond(Graphics2D surface, SymbolPoint point, double radius, Color fill, Color outline) {
        double x = point.x() / 8.0 * UNIT;
        double y = point.y() / 8.0 * UNIT;
        Path2D diamond = new Path2D.Double();
        diamond.moveTo(x, y - radius / zoom());
        diamond.lineTo(x + radius / zoom(), y);
        diamond.lineTo(x, y + radius / zoom());
        diamond.lineTo(x - radius / zoom(), y);
        diamond.closePath();
        surface.setColor(fill);
        surface.fill(diamond);
        surface.setColor(outline);
        surface.draw(diamond);
    }

    /** The node a click would remove: filled and ringed in the problem colour, larger than the other nodes. */
    private void paintRemoval(Graphics2D surface, ColorScheme scheme) {
        canvas.removal.ifPresent(hit -> {
            SymbolPoint point = ((SymbolPath) canvas.current.get().elements().get(hit.pathIndex())).nodes()
                    .get(hit.nodeIndex()).point();
            double x = point.x() / 8.0 * UNIT;
            double y = point.y() / 8.0 * UNIT;
            surface.setColor(scheme.wireConflict());
            surface.setStroke(new BasicStroke((float) (1.5 / zoom())));
            surface.fill(new Ellipse2D.Double(x - 5 / zoom(), y - 5 / zoom(), 10 / zoom(), 10 / zoom()));
            surface.draw(new Ellipse2D.Double(x - 9 / zoom(), y - 9 / zoom(), 18 / zoom(), 18 / zoom()));
        });
    }
}
