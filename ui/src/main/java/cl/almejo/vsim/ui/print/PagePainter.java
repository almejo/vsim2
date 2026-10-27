package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.AbstractList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Paints the sheets of «Exportar a PDF…» from a frozen {@link PrintInput}, one at a time, into any {@link Graphics2D}
 * in points with the origin at the sheet's top-left corner: the PDF page, the dialog's enlarged sheet and its
 * thumbnails all go through {@link #paint}, so they show the same thing (spec §3.2).
 *
 * <p>A sheet is white; the circuit is cut to the sheet's {@link PrintPage#paperRectangle()} (and to a frame's crop)
 * and painted by {@link CircuitImage} with the print policy at the layout's scale, never at the destination's: frame
 * titles always, no frame buttons, black and white when chosen. Values only when «Con valores» is on and the input
 * has them; otherwise it looks like a circuit that was never simulated. Tiled sheets get cut marks and «Fila ·
 * Columna — Hoja n de N»; the header and footer go in their bands when chosen; the legend of the wire states goes in
 * the first sheet's footer in black and white with values. The frozen text font is used for everything, whatever the
 * app's font is now.
 *
 * <p>Immutable once built and only reads the frozen input, so the export thread may paint while the dialog paints its
 * preview.
 */
public final class PagePainter {

    /** One sheet of the document: its size and which sheet of the layout it is. */
    public record Page(PdfPages.PageSize size, int layoutIndex) {
    }

    private static final String SHEET_POSITION = "print.sheet.position";
    private static final String SHEET_NUMBER = "print.sheet.number";
    /**
     * How far, in grid units, around a sheet's part of the circuit an element may be and still paint on it: shadows,
     * strokes, junction and open-pin dots, value tags.
     */
    static final int SHEET_REACH = 4;
    /**
     * Text smaller than this many canvas pixels (a long value squeezed into a block) is already unreadable at 100 %,
     * and dividing into sheets does not help it, so the small-text warning does not count it.
     */
    static final double SMALLEST_COUNTED_TEXT_PIXELS = 4;

    private final PrintInput input;
    private final PrintArea area;
    private final PrintOptions options;
    private final PageLayout layout;
    private final boolean showsValues;
    private final PaintPolicy policy;
    private final Optional<CircuitImage> image;
    /** Where each element of {@link #image} is drawn, measured under the frozen font; empty without an image. */
    private final Map<ElementId, Bounds> drawnBounds;
    /** Wires of nets wider than a bit: every sheet keeps them, so each bus writes its value on its longest wire. */
    private final Set<ElementId> busWires;
    /**
     * For each vertical wire, the wires that cross or touch it and the components with a pin on it: where they are
     * decides the wire's gaps under crossings, and a dashed wire restarts its dashes after each gap.
     */
    private final Map<ElementId, Set<ElementId>> verticalWireCompanions;
    private final boolean cutsToSheet;
    private final PageDecorations decorations;
    private final PdfPages.PageSize pageSize;
    private final String title;
    private final String date;

    /**
     * The sheets of {@code layout}, laid out for {@code area} of {@code input} with {@code options}.
     *
     * @throws IllegalArgumentException when the options ask for a header and footer the layout has no band for
     */
    public PagePainter(PrintInput input, PrintArea area, PrintOptions options, PageLayout layout) {
        this(input, area, options, layout, true);
    }

    /**
     * Like the public constructor; {@code cutsToSheet} false paints the whole circuit on every sheet and lets the
     * clip cut it (only for tests that check that leaving the other sheets' elements out changes nothing).
     */
    PagePainter(PrintInput input, PrintArea area, PrintOptions options, PageLayout layout, boolean cutsToSheet) {
        this.cutsToSheet = cutsToSheet;
        this.input = Objects.requireNonNull(input, "input");
        this.area = Objects.requireNonNull(area, "area");
        this.options = Objects.requireNonNull(options, "options");
        this.layout = Objects.requireNonNull(layout, "layout");
        if (options.headerFooter() && layout.geometry().headerBandPoints() <= 0) {
            throw new IllegalArgumentException("a header and footer need a band in the page geometry");
        }
        this.showsValues = options.withValues() && input.hasValues();
        this.policy = PaintPolicy.print(options.blackAndWhite()).withStickyNotes(options.includeStickyNotes())
                .atPageScale(layout.scale());
        // the circuit's bounds depend on the label font: measure them with the frozen one
        this.image = TextFonts.withFont(input.textFont(), () -> circuitImage(input, area, showsValues,
                options.includeStickyNotes()));
        this.drawnBounds = image.map(present -> TextFonts.withFont(input.textFont(), present::drawnBounds))
                .orElse(Map.of());
        this.busWires = busWires(input);
        this.verticalWireCompanions = verticalWireCompanions(input, drawnBounds.keySet());
        this.decorations = new PageDecorations(input.textFont().installedFamily());
        this.pageSize = new PdfPages.PageSize(layout.geometry().paperWidthPoints(),
                layout.geometry().paperHeightPoints());
        this.title = options.title().isBlank() ? input.circuitName() : options.title().strip();
        this.date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Messages.locale())
                .format(input.date());
    }

    private static Optional<CircuitImage> circuitImage(PrintInput input, PrintArea area, boolean showsValues,
                                                       boolean includeStickyNotes) {
        Set<ElementId> elements = area.elements();
        Optional<Set<ElementId>> only = elements.size() == input.document().elements().size()
                ? Optional.empty() : Optional.of(elements);
        SimulationSnapshot snapshot = showsValues ? input.snapshot() : withoutValues(input.netlistToken());
        return CircuitImage.of(input.document(), input.netlist(), input.netlistToken(), snapshot, input.registry(),
                PainterRegistry.standard(), paperScheme(input.scheme()), only, includeStickyNotes);
    }

    /**
     * The size of the smallest text VSim paints for {@code area} with {@code options} (labels, pin names, values,
     * frame titles; not the sheet's own decorations), in canvas pixels at 100 %, under the frozen font; empty when it
     * paints no text of at least {@link #SMALLEST_COUNTED_TEXT_PIXELS}. Found by painting the circuit once into a
     * one-pixel image through a graphics that notes every text's size.
     */
    static OptionalDouble smallestTextPixels(PrintInput input, PrintArea area, PrintOptions options) {
        boolean showsValues = options.withValues() && input.hasValues();
        PaintPolicy policy = PaintPolicy.print(options.blackAndWhite()).withStickyNotes(options.includeStickyNotes());
        return TextFonts.withFont(input.textFont(), () -> {
            Optional<CircuitImage> circuit = circuitImage(input, area, showsValues, options.includeStickyNotes());
            if (circuit.isEmpty()) {
                return OptionalDouble.empty();
            }
            double[] smallest = {Double.POSITIVE_INFINITY};
            BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D target = scratch.createGraphics();
            try {
                circuit.get().paint(SharedStreamGraphics.measuring(target, size -> {
                    if (size >= SMALLEST_COUNTED_TEXT_PIXELS) {
                        smallest[0] = Math.min(smallest[0], size);
                    }
                }), 1, policy);
            } finally {
                target.dispose();
            }
            return Double.isInfinite(smallest[0]) ? OptionalDouble.empty() : OptionalDouble.of(smallest[0]);
        });
    }

    private static Set<ElementId> busWires(PrintInput input) {
        Set<ElementId> result = new HashSet<>();
        for (Wire wire : input.document().wires()) {
            input.netlist().netForWire(wire.id()).filter(net -> net.width() > 1)
                    .ifPresent(net -> result.add(wire.id()));
        }
        return Set.copyOf(result);
    }

    private static Map<ElementId, Set<ElementId>> verticalWireCompanions(PrintInput input, Set<ElementId> painted) {
        List<Wire> wires = input.document().wires().stream().filter(wire -> painted.contains(wire.id())).toList();
        List<Component> components = input.document().components().stream()
                .filter(component -> painted.contains(component.id())).toList();
        Map<ElementId, Set<ElementId>> result = new HashMap<>();
        for (Wire vertical : wires) {
            if (vertical.isHorizontal()) {
                continue;
            }
            Set<ElementId> companions = new HashSet<>();
            for (Wire other : wires) {
                if (other != vertical && touches(vertical, other)) {
                    companions.add(other.id());
                }
            }
            for (Component component : components) {
                input.registry().findTypeOf(component).ifPresent(type -> {
                    for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                        if (contains(vertical, type.pinLocation(component, pinIndex))) {
                            companions.add(component.id());
                            return;
                        }
                    }
                });
            }
            if (!companions.isEmpty()) {
                result.put(vertical.id(), Set.copyOf(companions));
            }
        }
        return Map.copyOf(result);
    }

    /** Whether the axis-aligned wires {@code first} and {@code second} share a point. */
    private static boolean touches(Wire first, Wire second) {
        return Math.min(first.start().x(), first.end().x()) <= Math.max(second.start().x(), second.end().x())
                && Math.min(second.start().x(), second.end().x()) <= Math.max(first.start().x(), first.end().x())
                && Math.min(first.start().y(), first.end().y()) <= Math.max(second.start().y(), second.end().y())
                && Math.min(second.start().y(), second.end().y()) <= Math.max(first.start().y(), first.end().y());
    }

    private static boolean contains(Wire wire, GridPoint point) {
        return point.x() >= Math.min(wire.start().x(), wire.end().x())
                && point.x() <= Math.max(wire.start().x(), wire.end().x())
                && point.y() >= Math.min(wire.start().y(), wire.end().y())
                && point.y() <= Math.max(wire.start().y(), wire.end().y());
    }

    /**
     * What the canvas shows before the simulation publishes anything: no net values, no component states, and a
     * token of another netlist, so wires paint as having no value (displays and LEDs off).
     */
    private static SimulationSnapshot withoutValues(long netlistToken) {
        return new SimulationSnapshot(0, netlistToken + 1, 0, false, Map.of(), Map.of(), List.of(), Optional.empty());
    }

    /**
     * The colours on white like «Fondo blanco para imprimir»: a light scheme keeps its colours, a dark one prints
     * with the light scheme (its light strokes would vanish on white). Black and white greys them later anyway.
     */
    private static ColorScheme paperScheme(ColorScheme scheme) {
        return CircuitImage.printScheme(scheme.isDark() ? ColorScheme.LIGHT : scheme);
    }

    /**
     * The sheets in order: the circuit's tiles in reading order (later, annexes after them). Counted, not stored, so
     * a huge layout costs nothing until a sheet is painted; empty when the layout has no room.
     */
    public List<Page> pages() {
        return new AbstractList<>() {
            @Override
            public Page get(int index) {
                Objects.checkIndex(index, size());
                return new Page(pageSize, index);
            }

            @Override
            public int size() {
                return layout.pageCount();
            }
        };
    }

    /** Paints sheet {@code pageIndex} of {@link #pages()} into {@code graphics}, in points. */
    public void paint(Graphics2D graphics, int pageIndex) {
        int pageCount = layout.pageCount();
        Objects.checkIndex(pageIndex, pageCount);
        PrintPage page = layout.page(pageIndex);
        Graphics2D sheet = (Graphics2D) graphics.create();
        try {
            sheet.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sheet.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            sheet.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            sheet.setColor(Color.WHITE);
            sheet.fill(new Rectangle2D.Double(0, 0, pageSize.widthPoints(), pageSize.heightPoints()));
            TextFonts.runWithFont(input.textFont(), () -> paintCircuit(sheet, page));
            paintDecorations(sheet, page, pageCount);
        } finally {
            sheet.dispose();
        }
    }

    /** The area to print, in grid units: what {@link #paintOverview} covers. */
    public Rectangle2D gridArea() {
        return layout.gridArea();
    }

    /**
     * Paints the whole area to print on white, at the layout's scale, in points with the origin at the area's
     * top-left corner and no sheet decorations: the dialog's page map. Same content decisions as {@link #paint} (the
     * print policy at the layout's scale, the frozen font, values only when shown), so the map and the sheets agree.
     */
    public void paintOverview(Graphics2D graphics) {
        Rectangle2D gridArea = layout.gridArea();
        double pointsPerGridUnit = GridPoint.PIXELS_PER_UNIT * layout.scale();
        Rectangle2D printed = new Rectangle2D.Double(0, 0, gridArea.getWidth() * pointsPerGridUnit,
                gridArea.getHeight() * pointsPerGridUnit);
        Graphics2D overview = (Graphics2D) graphics.create();
        try {
            overview.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            overview.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            overview.setColor(Color.WHITE);
            overview.fill(printed);
            if (image.isEmpty()) {
                return;
            }
            overview.clip(printed);
            area.clip().ifPresent(crop -> overview.clip(new Rectangle2D.Double(
                    (crop.getX() - gridArea.getX()) * pointsPerGridUnit,
                    (crop.getY() - gridArea.getY()) * pointsPerGridUnit,
                    crop.getWidth() * pointsPerGridUnit, crop.getHeight() * pointsPerGridUnit)));
            Bounds imageBounds = image.get().gridBounds();
            overview.translate((imageBounds.x() - gridArea.getX()) * pointsPerGridUnit,
                    (imageBounds.y() - gridArea.getY()) * pointsPerGridUnit);
            TextFonts.runWithFont(input.textFont(), () -> image.get().paint(overview, layout.scale(), policy));
        } finally {
            overview.dispose();
        }
    }

    /** The part of the circuit on {@code page}, cut to its paper rectangle and, for a frame, to the frame's crop. */
    private void paintCircuit(Graphics2D sheet, PrintPage page) {
        if (image.isEmpty()) {
            return;
        }
        Rectangle2D paper = page.paperRectangle();
        Rectangle2D grid = page.gridRectangle();
        double pointsPerGridUnit = GridPoint.PIXELS_PER_UNIT * layout.scale();
        Graphics2D circuit = (Graphics2D) sheet.create();
        try {
            circuit.clip(paper);
            area.clip().ifPresent(crop -> circuit.clip(new Rectangle2D.Double(
                    paper.getX() + (crop.getX() - grid.getX()) * pointsPerGridUnit,
                    paper.getY() + (crop.getY() - grid.getY()) * pointsPerGridUnit,
                    crop.getWidth() * pointsPerGridUnit, crop.getHeight() * pointsPerGridUnit)));
            Bounds imageBounds = image.get().gridBounds();
            circuit.translate(paper.getX() + (imageBounds.x() - grid.getX()) * pointsPerGridUnit,
                    paper.getY() + (imageBounds.y() - grid.getY()) * pointsPerGridUnit);
            sheetShare(grid).paint(circuit, layout.scale(), policy);
        } finally {
            circuit.dispose();
        }
    }

    /**
     * The elements drawn within {@link #SHEET_REACH} of the sheet's part of the circuit, wires crossing it included,
     * plus the bus wires and what shapes the gaps of the vertical wires kept, so the sheet looks exactly as when the
     * clip cuts the whole circuit: the PDF keeps every element painted on a page, clipped or not, so a tiled PDF would
     * otherwise carry the whole circuit once per sheet.
     */
    private CircuitImage sheetShare(Rectangle2D grid) {
        CircuitImage whole = image.orElseThrow();
        if (!cutsToSheet) {
            return whole;
        }
        int fromX = (int) Math.floor(grid.getMinX()) - SHEET_REACH;
        int fromY = (int) Math.floor(grid.getMinY()) - SHEET_REACH;
        Bounds reach = new Bounds(fromX, fromY, (int) Math.ceil(grid.getMaxX()) + SHEET_REACH - fromX,
                (int) Math.ceil(grid.getMaxY()) + SHEET_REACH - fromY);
        Set<ElementId> share = new HashSet<>();
        drawnBounds.forEach((id, bounds) -> {
            if (bounds.intersects(reach) || busWires.contains(id)) {
                share.add(id);
            }
        });
        List<Set<ElementId>> companions = share.stream().map(verticalWireCompanions::get)
                .filter(Objects::nonNull).toList();
        companions.forEach(share::addAll);
        return share.size() == drawnBounds.size() ? whole : whole.only(share);
    }

    private void paintDecorations(Graphics2D sheet, PrintPage page, int pageCount) {
        int sheetNumber = page.index() + 1;
        if (layout.tiled()) {
            decorations.paintMarks(sheet, layout, page, Messages.format(SHEET_POSITION, page.row() + 1,
                    page.column() + 1, sheetNumber, pageCount));
        }
        boolean legend = showsValues && options.blackAndWhite() && page.index() == 0;
        if (options.headerFooter()) {
            decorations.paintHeader(sheet, layout.geometry(), title, input.fileName());
            decorations.paintFooter(sheet, layout.geometry(), date,
                    Messages.format(SHEET_NUMBER, sheetNumber, pageCount), legend);
        } else if (legend) {
            PageGeometry geometry = layout.geometry();
            decorations.paintLegendBelow(sheet, geometry, geometry.paperHeightPoints() - geometry.marginPoints());
        }
    }
}
