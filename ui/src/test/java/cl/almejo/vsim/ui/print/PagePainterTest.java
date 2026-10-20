package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One sheet of the PDF, painted into an image: what goes on it, where, and that the destination changes nothing. */
class PagePainterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);
    /** Antialiasing between two greys can round one channel apart from the others by a unit or two. */
    private static final int GREY_TOLERANCE = 2;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Map<String, Value> netValues = new HashMap<>();
    private final Map<String, VisibleState> componentStates = new HashMap<>();
    private Optional<Path> circuitPath = Optional.of(Path.of("sumador.vsim"));

    @AfterEach
    void restoreTheFont() {
        TextFonts.set(TextFont.DEFAULT);
    }

    // --- the circuit -------------------------------------------------------------------------------------------

    private Component add(String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.add(component);
        return component;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    private Frame frame(int x, int y, int width, int height, String title, FrameTint tint) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), width, height, title, tint, false);
        document.add(frame);
        return frame;
    }

    private void value(Wire wire, Value value) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        netValues.put(netlist.netForWire(wire.id()).orElseThrow().id().value(), value);
    }

    /** An AND gate at the top-left and a wire far enough to need 2 × 2 A4 portrait sheets at 100 %. */
    private void bigCircuit() {
        add(StandardComponentTypes.AND2, 4, 4, Map.of());
        wire(95, 125, 100, 125);
    }

    private void smallCircuit() {
        add(StandardComponentTypes.AND2, 4, 4, Map.of());
    }

    private void colourfulCircuit() {
        frame(0, 0, 70, 40, "Rojo", FrameTint.RED);
        frame(2, 4, 30, 30, "Azul", FrameTint.BLUE);
        add(StandardComponentTypes.SWITCH, 4, 6, Map.of("value", "1"));
        Component led = add(StandardComponentTypes.LED, 12, 6, Map.of("color", "green"));
        componentStates.put(led.id().value(), new VisibleState.LedState(Value.ONE));
        Component display = add(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 6, Map.of());
        componentStates.put(display.id().value(), new VisibleState.SevenSegmentsState(Value.of(4, 8)));
        add(TunnelKind.ENTRANCE.typeId(), 40, 6, Map.of(Tunnels.NAME, "a", Tunnels.COLOR, 1));
        add(TunnelKind.EXIT.typeId(), 52, 6, Map.of(Tunnels.NAME, "a"));
        value(wire(4, 20, 20, 20), Value.ONE);
        value(wire(4, 22, 20, 22), Value.ZERO);
        value(wire(4, 24, 20, 24), Value.HIGH_IMPEDANCE);
        value(wire(4, 28, 20, 28), Value.ONE.withConflict());
    }

    // --- the input, the options and the painter ----------------------------------------------------------------

    /** The circuit frozen with values of its own revision. */
    private PrintInput input() {
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, netValues,
                componentStates, List.of(), Optional.empty());
        return input(snapshot);
    }

    private PrintInput input(SimulationSnapshot snapshot) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        return PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist, document.revision(),
                snapshot, "Sumador", circuitPath), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
    }

    private static PrintOptions options(PrintScope scope, ScaleMode scaleMode, boolean withValues,
                                        boolean blackAndWhite, boolean headerFooter) {
        return new PrintOptions(scope, Paper.A4, PageOrientation.PORTRAIT, scaleMode, withValues, blackAndWhite,
                headerFooter, "Sumador de 4 bits");
    }

    private static PrintOptions fit() {
        return options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, false, false);
    }

    private static PrintOptions atFullSize() {
        return options(new PrintScope.WholeCircuit(), new ScaleMode.Percentage(100), true, false, false);
    }

    private static PagePainter painter(PrintInput input, PrintOptions options) {
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        return painter(input, area, options);
    }

    private static PagePainter painter(PrintInput input, PrintArea area, PrintOptions options) {
        PageGeometry geometry = PageGeometry.standard(options.paper(), options.orientation(), options.headerFooter());
        return new PagePainter(input, area, options, PageLayout.of(area.gridArea(), geometry, options.scaleMode()));
    }

    private static PageLayout layoutOf(PrintInput input, PrintOptions options) {
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        PageGeometry geometry = PageGeometry.standard(options.paper(), options.orientation(), options.headerFooter());
        return PageLayout.of(area.gridArea(), geometry, options.scaleMode());
    }

    // --- images ------------------------------------------------------------------------------------------------

    /**
     * The page at {@code pixelsPerPoint}, over a red background that the white sheet must cover; the image keeps only
     * whole pixels of the sheet (A4 is 595.28 pt wide).
     */
    private static BufferedImage render(PagePainter painter, int pageIndex, double pixelsPerPoint) {
        PdfPages.PageSize size = painter.pages().get(pageIndex).size();
        BufferedImage image = new BufferedImage((int) Math.floor(size.widthPoints() * pixelsPerPoint),
                (int) Math.floor(size.heightPoints() * pixelsPerPoint), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.scale(pixelsPerPoint, pixelsPerPoint);
        painter.paint(graphics, pageIndex);
        graphics.dispose();
        return image;
    }

    private static BufferedImage render(PagePainter painter, int pageIndex) {
        return render(painter, pageIndex, 1);
    }

    /** Pixels that are not (almost) white inside {@code region}, given in points. */
    private static int ink(BufferedImage image, Rectangle2D region) {
        return pixelsDarkerThan(image, region, 240);
    }

    /** Dark pixels (text, marks, a black wire) inside {@code region}: the light grey rules of the bands do not count. */
    private static int text(BufferedImage image, Rectangle2D region) {
        return pixelsDarkerThan(image, region, 128);
    }

    private static int pixelsDarkerThan(BufferedImage image, Rectangle2D region, int level) {
        int count = 0;
        int fromX = Math.max(0, (int) Math.floor(region.getMinX()));
        int fromY = Math.max(0, (int) Math.floor(region.getMinY()));
        int toX = Math.min(image.getWidth(), (int) Math.ceil(region.getMaxX()));
        int toY = Math.min(image.getHeight(), (int) Math.ceil(region.getMaxY()));
        for (int x = fromX; x < toX; x++) {
            for (int y = fromY; y < toY; y++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() + color.getGreen() + color.getBlue() < 3 * level) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Ink anywhere on the sheet but inside {@code kept} (grown by a point for antialiasing). */
    private static int inkOutside(BufferedImage image, Rectangle2D kept) {
        Rectangle2D grown = new Rectangle2D.Double(kept.getX() - 1, kept.getY() - 1, kept.getWidth() + 2,
                kept.getHeight() + 2);
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                Color color = new Color(image.getRGB(x, y));
                boolean inked = color.getRed() + color.getGreen() + color.getBlue() < 3 * 240;
                if (inked && !grown.contains(x + 0.5, y + 0.5)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int colouredPixels(BufferedImage image) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                Color color = new Color(image.getRGB(x, y));
                boolean grey = Math.abs(color.getRed() - color.getGreen()) <= GREY_TOLERANCE
                        && Math.abs(color.getGreen() - color.getBlue()) <= GREY_TOLERANCE
                        && Math.abs(color.getRed() - color.getBlue()) <= GREY_TOLERANCE;
                if (!grey) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Rectangle2D headerBand(PageGeometry geometry) {
        return new Rectangle2D.Double(geometry.marginPoints(), geometry.marginPoints(),
                geometry.paperWidthPoints() - 2 * geometry.marginPoints(), geometry.headerBandPoints());
    }

    private static Rectangle2D footerBand(PageGeometry geometry) {
        return new Rectangle2D.Double(geometry.marginPoints(),
                geometry.paperHeightPoints() - geometry.marginPoints() - geometry.headerBandPoints(),
                geometry.paperWidthPoints() - 2 * geometry.marginPoints(), geometry.headerBandPoints());
    }

    private static Rectangle2D bottomMargin(PageGeometry geometry) {
        return new Rectangle2D.Double(0, geometry.paperHeightPoints() - geometry.marginPoints(),
                geometry.paperWidthPoints(), geometry.marginPoints());
    }

    /** A slice of {@code band} from {@code fromFraction} to {@code toFraction} of its width. */
    private static Rectangle2D slice(Rectangle2D band, double fromFraction, double toFraction) {
        return new Rectangle2D.Double(band.getX() + band.getWidth() * fromFraction, band.getY(),
                band.getWidth() * (toFraction - fromFraction), band.getHeight());
    }

    // --- the pages ---------------------------------------------------------------------------------------------

    @Test
    void thePagesAreTheLayoutsSheetsInReadingOrderWithThePaperSize() {
        bigCircuit();
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.LANDSCAPE,
                new ScaleMode.Percentage(100), true, false, false, "");
        PagePainter painter = painter(input(), options);
        assertThat(painter.pages()).extracting(PagePainter.Page::layoutIndex).containsExactly(0, 1, 2, 3)
                .hasSize(layoutOf(input(), options).pageCount());
        assertThat(painter.pages()).allSatisfy(page ->
                assertThat(page.size()).isEqualTo(new PdfPages.PageSize(841.89, 595.28)));
    }

    @Test
    void aHugeLayoutIsListedWithoutMaterialisingItsPages() {
        wire(0, 0, 25000, 0);
        wire(0, 0, 0, 25000);
        PagePainter painter = painter(input(), atFullSize());
        int count = painter.pages().size();
        assertThat(count).isGreaterThan(100_000);
        assertThat(painter.pages().get(count - 1).layoutIndex()).isEqualTo(count - 1);
    }

    @Test
    void aLayoutWithoutRoomHasNoPagesToPaint() {
        smallCircuit();
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        PageGeometry noRoom = new PageGeometry(Paper.A4, PageOrientation.PORTRAIT, 400, 0, 12, 28);
        PagePainter painter = new PagePainter(input, area, fit(),
                PageLayout.of(area.gridArea(), noRoom, new ScaleMode.FitToOnePage()));
        assertThat(painter.pages()).isEmpty();
        Graphics2D graphics = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        assertThatThrownBy(() -> painter.paint(graphics, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        graphics.dispose();
    }

    @Test
    void aHeaderAndFooterNeedTheirBandInTheLayout() {
        smallCircuit();
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        PrintOptions withHeader = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, false,
                true);
        PageLayout withoutBand = PageLayout.of(area.gridArea(),
                PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false), new ScaleMode.FitToOnePage());
        assertThatThrownBy(() -> new PagePainter(input, area, withHeader, withoutBand))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- the sheet, the circuit and the marks ------------------------------------------------------------------

    @Test
    void theSheetIsWhiteWhateverIsUnderIt() {
        smallCircuit();
        BufferedImage page = render(painter(input(), fit()), 0);
        assertThat(page.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
        assertThat(page.getRGB(page.getWidth() - 1, page.getHeight() - 1)).isEqualTo(Color.WHITE.getRGB());
    }

    @Test
    void theFirstOfFourSheetsHoldsTheTopLeftPartAndTheMarks() {
        bigCircuit();
        PrintInput input = input();
        PageLayout layout = layoutOf(input, atFullSize());
        assertThat(layout.columns()).isEqualTo(2);
        assertThat(layout.rows()).isEqualTo(2);
        PagePainter painter = painter(input, atFullSize());
        BufferedImage first = render(painter, 0);
        BufferedImage last = render(painter, 3);
        Rectangle2D window = layout.window();
        Rectangle2D topLeftCorner = new Rectangle2D.Double(window.getX(), window.getY(), 80, 80);
        assertThat(ink(first, topLeftCorner)).as("the gate at the top-left").isPositive();
        assertThat(ink(last, topLeftCorner)).as("the last sheet starts further right and down").isZero();

        double strip = layout.geometry().marksStripPoints();
        double halfOverlap = layout.overlapPoints() / 2;
        Rectangle2D rightNeighbourMark = new Rectangle2D.Double(window.getMaxX() - halfOverlap - 2,
                window.getY() - strip, 4, strip);
        Rectangle2D leftNeighbourMark = new Rectangle2D.Double(window.getX() + halfOverlap - 2,
                window.getY() - strip, 4, strip);
        Rectangle2D lowerNeighbourMark = new Rectangle2D.Double(window.getX() - strip,
                window.getMaxY() - halfOverlap - 2, strip, 4);
        assertThat(ink(first, rightNeighbourMark)).as("a mark towards the sheet on the right").isPositive();
        assertThat(ink(first, lowerNeighbourMark)).as("a mark towards the sheet below").isPositive();
        assertThat(ink(first, leftNeighbourMark)).as("no sheet on the left, no mark").isZero();
        assertThat(ink(last, leftNeighbourMark)).as("the last sheet has a neighbour on its left").isPositive();
        Rectangle2D labelStrip = new Rectangle2D.Double(window.getCenterX() - 60, window.getMaxY(), 120, strip);
        assertThat(ink(first, labelStrip)).as("«Fila 1 · Columna 1 — Hoja 1 de 4»").isPositive();
    }

    @Test
    void aSinglePageHasNoMarksNorLabelOnlyTheCircuit() {
        bigCircuit();
        PrintInput input = input();
        PagePainter painter = painter(input, fit());
        BufferedImage page = render(painter, 0);
        Rectangle2D paper = layoutOf(input, fit()).page(0).paperRectangle();
        assertThat(ink(page, paper)).isPositive();
        assertThat(inkOutside(page, paper)).isZero();
    }

    @Test
    void theCircuitIsCutAtThePagesRectangle() {
        bigCircuit();
        wire(0, 60, 100, 60);
        PrintInput input = input();
        PageLayout layout = layoutOf(input, atFullSize());
        BufferedImage first = render(painter(input, atFullSize()), 0);
        Rectangle2D paper = layout.page(0).paperRectangle();
        double wireY = paper.getY() + (60 - layout.page(0).gridRectangle().getY()) * GridPoint.PIXELS_PER_UNIT;
        Rectangle2D justInside = new Rectangle2D.Double(paper.getMaxX() - 6, wireY - 2, 5, 4);
        Rectangle2D inTheRightStrip = new Rectangle2D.Double(paper.getMaxX() + 1, wireY - 2,
                layout.geometry().marksStripPoints() - 1, 4);
        assertThat(ink(first, justInside)).isPositive();
        assertThat(ink(first, inTheRightStrip)).isZero();
    }

    @Test
    void aFramePageCutsAWireThatCrossesItsBorder() {
        frame(10, 10, 20, 10, "Bloque", FrameTint.GREEN);
        wire(20, 12, 50, 12);
        wire(60, 60, 70, 60);
        PrintInput input = input();
        Frame frame = input.document().frames().iterator().next();
        PrintOptions options = options(new PrintScope.Frame(frame.id()), new ScaleMode.FitToOnePage(), true, false,
                false);
        PrintArea area = PrintScopes.area(input, options.scope()).orElseThrow();
        PagePainter painter = painter(input, area, options);
        PageLayout layout = PageLayout.of(area.gridArea(), PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT,
                false), options.scaleMode());
        Rectangle2D paper = layout.page(0).paperRectangle();
        double pointsPerGridUnit = GridPoint.PIXELS_PER_UNIT * layout.scale();
        double wireY = paper.getY() + (12 - area.gridArea().getY()) * pointsPerGridUnit;
        BufferedImage page = render(painter, 0);
        Rectangle2D insideTheBorder = new Rectangle2D.Double(paper.getMaxX() - 8, wireY - 2, 5, 4);
        Rectangle2D beyondTheBorder = new Rectangle2D.Double(paper.getMaxX() + 1, wireY - 3, 60, 6);
        assertThat(ink(page, insideTheBorder)).isPositive();
        assertThat(ink(page, beyondTheBorder)).isZero();
        assertThat(inkOutside(page, paper)).isZero();
    }

    // --- header, footer and legend -----------------------------------------------------------------------------

    @Test
    void theHeaderAndFooterArePaintedOnlyWhenChosen() {
        smallCircuit();
        PrintInput input = input();
        PrintOptions withHeader = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, false,
                true);
        PageGeometry geometry = layoutOf(input, withHeader).geometry();
        BufferedImage on = render(painter(input, withHeader), 0);
        assertThat(text(on, slice(headerBand(geometry), 0, 0.3))).as("the title").isPositive();
        assertThat(text(on, slice(headerBand(geometry), 0.7, 1))).as("the file name").isPositive();
        assertThat(text(on, slice(footerBand(geometry), 0, 0.3))).as("the date").isPositive();
        assertThat(text(on, slice(footerBand(geometry), 0.7, 1))).as("«Hoja 1 de 1»").isPositive();

        BufferedImage off = render(painter(input, fit()), 0);
        assertThat(inkOutside(off, layoutOf(input, fit()).page(0).paperRectangle())).isZero();
    }

    @Test
    void aCircuitNeverSavedHasNoFileNameInTheHeader() {
        smallCircuit();
        circuitPath = Optional.empty();
        PrintOptions withHeader = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, false,
                true);
        PrintInput input = input();
        BufferedImage page = render(painter(input, withHeader), 0);
        PageGeometry geometry = layoutOf(input, withHeader).geometry();
        assertThat(text(page, slice(headerBand(geometry), 0, 0.3))).isPositive();
        assertThat(text(page, slice(headerBand(geometry), 0.7, 1))).isZero();
    }

    @Test
    void vsimsOwnTextsKeepTheirSizeWhateverTheScaleOfTheCircuit() {
        assertThat(PageDecorations.MARK_TEXT_POINTS).isGreaterThanOrEqualTo(PageDecorations.MINIMUM_TEXT_POINTS);
        assertThat(PageDecorations.HEADER_TEXT_POINTS).isGreaterThanOrEqualTo(PageDecorations.MINIMUM_TEXT_POINTS);
        assertThat(PageDecorations.LEGEND_TEXT_POINTS).isGreaterThanOrEqualTo(PageDecorations.MINIMUM_TEXT_POINTS);
        PrintOptions withHeader = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, false,
                true);
        smallCircuit();
        PrintInput small = input();
        BufferedImage smallPage = render(painter(small, withHeader), 0);
        wire(0, 0, 3000, 0);
        PrintInput huge = input();
        assertThat(layoutOf(huge, withHeader).scale()).isLessThan(0.05);
        BufferedImage hugePage = render(painter(huge, withHeader), 0);
        PageGeometry geometry = layoutOf(small, withHeader).geometry();
        Rectangle2D header = headerBand(geometry);
        assertThat(samePixels(smallPage.getSubimage((int) header.getX(), (int) header.getY(),
                        (int) header.getWidth(), (int) header.getHeight()),
                hugePage.getSubimage((int) header.getX(), (int) header.getY(), (int) header.getWidth(),
                        (int) header.getHeight()))).isTrue();
    }

    @Test
    void theLegendGoesInTheFirstFooterOnlyInBlackAndWhiteWithValues() {
        bigCircuit();
        PrintInput input = input();
        ScaleMode full = new ScaleMode.Percentage(100);
        PrintOptions legend = options(new PrintScope.WholeCircuit(), full, true, true, true);
        PageGeometry geometry = layoutOf(input, legend).geometry();
        Rectangle2D footerMiddle = slice(footerBand(geometry), 0.4, 0.6);
        PagePainter withLegend = painter(input, legend);
        assertThat(text(render(withLegend, 0), footerMiddle)).as("the legend").isPositive();
        assertThat(text(render(withLegend, 1), footerMiddle)).as("only on the first sheet").isZero();
        assertThat(text(render(painter(input, options(new PrintScope.WholeCircuit(), full, true, false, true)), 0),
                footerMiddle)).as("not in colour").isZero();
        assertThat(text(render(painter(input, options(new PrintScope.WholeCircuit(), full, false, true, true)), 0),
                footerMiddle)).as("not without values").isZero();
        PrintInput unsimulated = input(SimulationSnapshot.EMPTY);
        assertThat(unsimulated.hasValues()).isFalse();
        assertThat(text(render(painter(unsimulated, legend), 0), footerMiddle)).as("not «sin valores»").isZero();
    }

    @Test
    void withoutAFooterTheLegendGoesUnderTheDrawing() {
        smallCircuit();
        PrintInput input = input();
        PrintOptions legend = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, true,
                false);
        PageGeometry geometry = layoutOf(input, legend).geometry();
        assertThat(ink(render(painter(input, legend), 0), bottomMargin(geometry))).isPositive();
        PrintOptions colour = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), true, false,
                false);
        assertThat(ink(render(painter(input, colour), 0), bottomMargin(geometry))).isZero();
    }

    // --- values, black and white, and the same decisions everywhere --------------------------------------------

    @Test
    void withoutValuesTheSheetLooksLikeAnUnsimulatedCircuit() {
        Component led = add(StandardComponentTypes.LED, 12, 6, Map.of("color", "green"));
        componentStates.put(led.id().value(), new VisibleState.LedState(Value.ONE));
        value(wire(4, 20, 20, 20), Value.ONE);
        PrintInput simulated = input();
        assertThat(simulated.hasValues()).isTrue();
        PrintOptions withoutValues = options(new PrintScope.WholeCircuit(), new ScaleMode.FitToOnePage(), false,
                false, false);
        BufferedImage clean = render(painter(simulated, withoutValues), 0);
        BufferedImage unsimulated = render(painter(input(SimulationSnapshot.EMPTY), fit()), 0);
        BufferedImage withValues = render(painter(simulated, fit()), 0);
        assertThat(samePixels(clean, unsimulated)).isTrue();
        assertThat(samePixels(clean, withValues)).isFalse();
    }

    @Test
    void blackAndWhiteLeavesNoColourOnTheSheetWithItsDecorations() {
        colourfulCircuit();
        PrintInput input = input();
        PrintOptions blackAndWhite = options(new PrintScope.WholeCircuit(), new ScaleMode.Percentage(400), true,
                true, true);
        PrintOptions colour = options(new PrintScope.WholeCircuit(), new ScaleMode.Percentage(400), true, false,
                true);
        assertThat(layoutOf(input, blackAndWhite).tiled()).isTrue();
        assertThat(colouredPixels(render(painter(input, colour), 0))).isPositive();
        assertThat(colouredPixels(render(painter(input, blackAndWhite), 0))).isZero();
    }

    @Test
    void aDarkSchemePrintsInLightColoursOnWhite() {
        smallCircuit();
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, netValues,
                componentStates, List.of(), Optional.empty());
        PrintInput dark = PrintInput.capture(new PrintInput.LiveCircuit(document, registry, netlist,
                document.revision(), snapshot, "Sumador", circuitPath), Set.of(),
                ColorSchemes.DARK, TextFont.DEFAULT, CLOCK);
        assertThat(samePixels(render(painter(dark, fit()), 0), render(painter(input(), fit()), 0))).isTrue();
    }

    @Test
    void theSameSheetMakesTheSameDecisionsAtAnyDestinationScale() {
        frame(0, 0, 40, 20, "MMMMMMMMMMMMMMMM", FrameTint.BLUE);
        wire(190, 0, 195, 0);
        PrintInput titled = input();
        assertThat(layoutOf(titled, fit()).scale() * GridPoint.PIXELS_PER_UNIT).as("the canvas hides titles here")
                .isLessThan(3);
        document.replace(document.frames().iterator().next().withTitle(""));
        PrintInput untitled = input();
        for (double pixelsPerPoint : new double[] {0.5, 4}) {
            BufferedImage withTitle = render(painter(titled, fit()), 0, pixelsPerPoint);
            BufferedImage withoutTitle = render(painter(untitled, fit()), 0, pixelsPerPoint);
            assertThat(samePixels(withTitle, withoutTitle)).as("title painted at %s px/pt", pixelsPerPoint)
                    .isFalse();
        }
    }

    @Test
    void aFontChosenWhileTheDialogIsOpenDoesNotChangeTheSheet() {
        frame(0, 0, 30, 12, "Sumador", FrameTint.BLUE);
        add(StandardComponentTypes.AND2, 4, 4, Map.of());
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        PagePainter before = painter(input, area, fit());
        BufferedImage first = render(before, 0);
        TextFonts.set(new TextFont(Font.MONOSPACED, 24));
        assertThat(samePixels(render(before, 0), first)).isTrue();
        assertThat(samePixels(render(painter(input, area, fit()), 0), first)).isTrue();
    }

    // --- into a PDF --------------------------------------------------------------------------------------------

    @Test
    void theSheetsGoIntoAVectorPdfAsPainted(@TempDir Path folder) throws IOException {
        bigCircuit();
        PrintInput input = input();
        PagePainter painter = painter(input, atFullSize());
        Path file = folder.resolve("circuito.pdf");
        try (OutputStream output = Files.newOutputStream(file)) {
            PdfPages.write(output, painter.pages().stream().map(PagePainter.Page::size).toList(),
                    (pageIndex, graphics) -> painter.paint(graphics, pageIndex));
        }
        assertThat(PdfReading.pageCount(file)).isEqualTo(4);
        assertThat(PdfReading.hasImages(file, 0)).isFalse();
        assertThat(PdfReading.pathOperatorCount(file, 0)).isPositive();
        PageLayout layout = layoutOf(input, atFullSize());
        Rectangle2D window = layout.window();
        BufferedImage first = PdfReading.render(file, 0, 1);
        assertThat(ink(first, new Rectangle2D.Double(window.getX(), window.getY(), 80, 80))).isPositive();
        Rectangle2D rightNeighbourMark = new Rectangle2D.Double(window.getMaxX() - layout.overlapPoints() / 2 - 2,
                window.getY() - layout.geometry().marksStripPoints(), 4, layout.geometry().marksStripPoints());
        assertThat(ink(first, rightNeighbourMark)).isPositive();
        assertThat(ink(PdfReading.render(file, 3, 1), new Rectangle2D.Double(window.getX(), window.getY(), 80, 80)))
                .isZero();
    }

    /**
     * A switch (its value text is white), labelled components and free text: what JFreePDF's shared stream used to
     * lose. The PDF of the sheet, read back by PDFBox, must look like the preview's image of the same sheet.
     */
    private void labelledCircuit() {
        Component input = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(4, 6), Rotation.R0, Map.of("value", "1"), "Cin");
        document.add(input);
        Component gate = registry.require(StandardComponentTypes.AND2).newComponent(document.newComponentId(),
                new GridPoint(14, 6), Rotation.R0, Map.of(), "resultado");
        document.add(gate);
        Component led = registry.require(StandardComponentTypes.LED).newComponent(document.newComponentId(),
                new GridPoint(26, 6), Rotation.R0, Map.of("color", "green"), "Cero");
        document.add(led);
        componentStates.put(led.id().value(), new VisibleState.LedState(Value.ONE));
        document.add(new TextLabel(document.newLabelId(), new GridPoint(4, 16), "ALU de 4 bits — señal"));
        value(wire(7, 6, 14, 6), Value.ONE);
    }

    /** The PDF's page {@code pageIndex} and the preview's image of it, at the same resolution, side by side. */
    private static void assertThePdfLooksLikeThePreview(PagePainter painter, Path folder) throws IOException {
        Path file = folder.resolve("vista.pdf");
        try (OutputStream output = Files.newOutputStream(file)) {
            PdfPages.write(output, painter.pages().stream().map(PagePainter.Page::size).toList(),
                    (pageIndex, graphics) -> painter.paint(graphics, pageIndex));
        }
        float pixelsPerPoint = 2;
        BufferedImage pdf = PdfReading.render(file, 0, pixelsPerPoint);
        BufferedImage preview = render(painter, 0, pixelsPerPoint);
        int previewDark = darkPixels(preview);
        assertThat(previewDark).as("something dark on the preview").isGreaterThan(1000);
        // The two rasterisers place an edge up to a pixel apart and spread a thin grey line differently, so a dark
        // pixel only needs a darkish neighbour; a label lost or painted white has none.
        assertThat(darkPixelsWithoutADarkNeighbour(preview, pdf)).as("dark on the preview but not in the PDF")
                .isLessThan(previewDark / 100);
        assertThat(darkPixelsWithoutADarkNeighbour(pdf, preview)).as("dark in the PDF but not on the preview")
                .isLessThan(previewDark / 100);
        double[] pdfColour = averageColour(pdf);
        double[] previewColour = averageColour(preview);
        for (int channel = 0; channel < 3; channel++) {
            assertThat(pdfColour[channel]).as("average colour, channel %d", channel)
                    .isCloseTo(previewColour[channel], org.assertj.core.api.Assertions.within(1.0));
        }
    }

    /** The average red, green and blue of the sheet. */
    private static double[] averageColour(BufferedImage image) {
        double[] sums = new double[3];
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                Color color = new Color(image.getRGB(x, y));
                sums[0] += color.getRed();
                sums[1] += color.getGreen();
                sums[2] += color.getBlue();
            }
        }
        double pixels = (double) image.getWidth() * image.getHeight();
        return new double[] {sums[0] / pixels, sums[1] / pixels, sums[2] / pixels};
    }

    private static boolean isDark(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y));
        return color.getRed() + color.getGreen() + color.getBlue() < 3 * 128;
    }

    private static int darkPixels(BufferedImage image) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (isDark(image, x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Dark pixels of {@code first} with no darkish pixel of {@code second} within two pixels. */
    private static int darkPixelsWithoutADarkNeighbour(BufferedImage first, BufferedImage second) {
        int reach = 2;
        int count = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (!isDark(first, x, y)) {
                    continue;
                }
                boolean found = false;
                for (int neighbourX = Math.max(0, x - reach);
                     !found && neighbourX <= Math.min(second.getWidth() - 1, x + reach); neighbourX++) {
                    for (int neighbourY = Math.max(0, y - reach);
                         !found && neighbourY <= Math.min(second.getHeight() - 1, y + reach); neighbourY++) {
                        Color neighbour = new Color(second.getRGB(neighbourX, neighbourY));
                        found = neighbour.getRed() + neighbour.getGreen() + neighbour.getBlue() < 3 * 176;
                    }
                }
                if (!found) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void theLabelsOfThePdfAreTheOnesOfThePreview(@TempDir Path folder) throws IOException {
        labelledCircuit();
        assertThePdfLooksLikeThePreview(painter(input(), fit()), folder);
    }

    @Test
    void theLabelsOfABlackAndWhitePdfAreTheOnesOfThePreview(@TempDir Path folder) throws IOException {
        labelledCircuit();
        assertThePdfLooksLikeThePreview(painter(input(), options(new PrintScope.WholeCircuit(),
                new ScaleMode.FitToOnePage(), true, true, true)), folder);
    }

    @Test
    void theLabelsOfTheBundledAluArePrintedInThePdf(@TempDir Path folder) throws Exception {
        ComponentTypeRegistry aluRegistry = StandardComponentTypes.registry();
        CircuitDocument alu = BundledExamples.open("alu-4-bits", aluRegistry);
        aluRegistry.addResolver(SubcircuitTypes.resolverFor(alu));
        Netlist netlist = new NetlistCompiler(aluRegistry).compile(alu);
        PrintInput input = PrintInput.capture(new PrintInput.LiveCircuit(alu, aluRegistry, netlist, alu.revision(),
                SimulationSnapshot.EMPTY, "alu-4-bits", circuitPath), Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT,
                CLOCK);
        assertThePdfLooksLikeThePreview(painter(input, fit()), folder);
        assertThePdfLooksLikeThePreview(painter(input, options(new PrintScope.WholeCircuit(),
                new ScaleMode.FitToOnePage(), false, true, false)), folder);
    }

    // --- each sheet carries its own share ----------------------------------------------------------------------

    /** Labelled gates every 8 units over about 2 × 2 A4 portrait sheets at 100 %, a free text and long wires. */
    private void spreadCircuit() {
        for (int x = 0; x <= 96; x += 8) {
            for (int y = 0; y <= 120; y += 8) {
                Component gate = registry.require(StandardComponentTypes.AND2).newComponent(document.newComponentId(),
                        new GridPoint(x, y), Rotation.R0, Map.of(), "G" + x + "_" + y);
                document.add(gate);
            }
        }
        value(wire(-2, 62, 104, 62), Value.ONE);
        wire(50, -2, 50, 126);
        document.add(new TextLabel(document.newLabelId(), new GridPoint(40, 60), "Una nota sobre el borde"));
    }

    private static Path writePdf(PagePainter painter, Path file) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            PdfPages.write(output, painter.pages().stream().map(PagePainter.Page::size).toList(),
                    (pageIndex, graphics) -> painter.paint(graphics, pageIndex));
        }
        return file;
    }

    @Test
    void aTiledPdfCarriesOnlyEachSheetsShareOfTheCircuit(@TempDir Path folder) throws IOException {
        spreadCircuit();
        PrintInput input = input();
        PagePainter tiled = painter(input, atFullSize());
        assertThat(tiled.pages()).hasSize(4);
        long single = Files.size(writePdf(painter(input, fit()), folder.resolve("una.pdf")));
        long fourSheets = Files.size(writePdf(tiled, folder.resolve("cuatro.pdf")));

        assertThat(fourSheets).as("2 × 2 sheets against %d bytes for one", single).isLessThan(2 * single);
    }

    @Test
    void leavingTheOtherSheetsElementsOutChangesNoPixelOfAnySheet() {
        spreadCircuit();
        PrintInput input = input();
        PrintArea area = PrintScopes.area(input, new PrintScope.WholeCircuit()).orElseThrow();
        for (PrintOptions options : List.of(atFullSize(), options(new PrintScope.WholeCircuit(),
                new ScaleMode.Percentage(100), true, true, true))) {
            PageGeometry geometry = PageGeometry.standard(options.paper(), options.orientation(),
                    options.headerFooter());
            PageLayout layout = PageLayout.of(area.gridArea(), geometry, options.scaleMode());
            PagePainter cut = new PagePainter(input, area, options, layout, true);
            PagePainter whole = new PagePainter(input, area, options, layout, false);
            assertThat(cut.pages()).hasSize(4);
            for (int pageIndex = 0; pageIndex < 4; pageIndex++) {
                assertThat(samePixels(render(cut, pageIndex, 2), render(whole, pageIndex, 2)))
                        .as("sheet %d", pageIndex + 1).isTrue();
            }
        }
    }
}
