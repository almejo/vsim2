package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.export.ExportChoice;
import cl.almejo.vsim.ui.export.ExportImageFeatures;
import cl.almejo.vsim.ui.frames.FramesExtension;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.ScaleMode;
import cl.almejo.vsim.ui.print.SheetGrid;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What one pane draws of the sheets (sheets spec §3, §6, §7): dashed borders and overlap bands where the grid says,
 * only for the visible sheets; «Hoja n» inside each sheet unless it is small on screen, stuck to the visible corner
 * when the sheet's own corner is scrolled away; cut marks around parts and frames a border cuts; the lattice of the
 * sheets going on, fainter, over the whole pane while they are tiled; an amber outline around a dragged part or frame
 * a border would cut; nothing when hidden or empty, and never in an exported image.
 */
class SheetOverlayTest {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SheetAnchors anchors = new SheetAnchors();
    private final SheetsExtension extension = new SheetsExtension(anchors, (menu, invoker, x, y) -> { });
    private OpenDocument openDocument;
    private EditorView view;
    private SheetsFeatures features;
    private CircuitCanvas pane;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(new FramesExtension()));
        view = new EditorView(openDocument, services, NO_WINDOW);
        pane = view.canvas();
        pane.setSize(WIDTH, HEIGHT);
        features = extension.installFeatures(view);
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100)));
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private void twoColumns() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 100, 0, Map.of());
    }

    private SheetOverlay overlay() {
        return pane.overlays().stream().filter(SheetOverlay.class::isInstance).map(SheetOverlay.class::cast)
                .findFirst().orElseThrow();
    }

    private SheetGrid grid() {
        return features.picture().orElseThrow().grid();
    }

    /** Shows the whole first two sheets at {@code zoom}, their area in the pane. */
    private void showAt(double zoom, double gridX, double gridY) {
        pane.viewport().showAt(zoom, gridX, gridY, WIDTH, HEIGHT);
    }

    private Rectangle onScreen(Rectangle2D gridRectangle) {
        Viewport viewport = pane.viewport();
        Point2D topLeft = viewport.gridToScreen(gridRectangle.getX(), gridRectangle.getY());
        Point2D bottomRight = viewport.gridToScreen(gridRectangle.getMaxX(), gridRectangle.getMaxY());
        int left = (int) Math.round(topLeft.getX());
        int top = (int) Math.round(topLeft.getY());
        return new Rectangle(left, top, (int) Math.round(bottomRight.getX()) - left,
                (int) Math.round(bottomRight.getY()) - top);
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            pane.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int differingPixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void theBordersAndTheOverlapBandAreWhereTheGridSays() {
        twoColumns();
        preferences.setShowSheets(true);
        showAt(0.5, 50, 40);

        SheetDrawing drawing = overlay().drawing();

        assertThat(drawing.borders()).containsExactly(onScreen(grid().sheetRectangle(0, 0)),
                onScreen(grid().sheetRectangle(1, 0)));
        Rectangle band = onScreen(grid().overlapBands(0, 0).getFirst());
        assertThat(drawing.bands().getBounds()).isEqualTo(band);
        assertThat(drawing.cuts()).isEmpty();
    }

    @Test
    void eachSheetIsNumberedInsideItsCorner() {
        twoColumns();
        preferences.setShowSheets(true);
        showAt(0.5, 50, 40);

        List<SheetLabel> labels = overlay().drawing().labels();

        assertThat(labels).extracting(SheetLabel::text).containsExactly("Hoja 1", "Hoja 2");
        Rectangle second = onScreen(grid().sheetRectangle(1, 0));
        assertThat(second.contains(labels.get(1).box())).isTrue();
        assertThat(labels.get(1).box().x - second.x).isLessThan(10);
    }

    @Test
    void theNumbersHideWhenTheSheetsAreSmallOnScreen() {
        twoColumns();
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(400)));
        preferences.setShowSheets(true);
        showAt(Viewport.MINIMUM_ZOOM, 50, 40);

        SheetDrawing drawing = overlay().drawing();

        assertThat(drawing.borders()).isNotEmpty();
        assertThat(drawing.borders()).allSatisfy(border ->
                assertThat(border.width).isLessThan(SheetOverlay.LABEL_MINIMUM_WIDTH));
        assertThat(drawing.labels()).isEmpty();
    }

    @Test
    void zoomedInOnlyTheVisibleSheetIsDrawnAndItsNumberSticksToTheVisibleCorner() {
        twoColumns();
        preferences.setShowSheets(true);
        Rectangle2D first = grid().sheetRectangle(0, 0);
        showAt(Viewport.MAXIMUM_ZOOM, first.getCenterX() - 10, first.getCenterY());

        SheetDrawing drawing = overlay().drawing();

        assertThat(drawing.borders()).hasSize(1);
        assertThat(drawing.labels()).singleElement().satisfies(label -> {
            assertThat(label.text()).isEqualTo("Hoja 1");
            assertThat(label.box().x).isBetween(0, 40);
            assertThat(label.box().y).isBetween(0, 40);
        });
    }

    @Test
    void aFrameThatABorderCutsIsMarkedAndOneInsideASheetIsNot() {
        twoColumns();
        preferences.setShowSheets(true);
        double border = grid().sheetRectangle(0, 0).getMaxX();
        Frame cut = new Frame(openDocument.document().newFrameId(), new GridPoint((int) border - 6, 20), 12, 6,
                "", FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(cut));
        Frame inside = new Frame(openDocument.document().newFrameId(), new GridPoint(5, 30), 10, 6, "",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(inside));
        showAt(0.5, 50, 40);

        List<Rectangle> cuts = overlay().drawing().cuts();

        Rectangle2D cutRectangle = new Rectangle2D.Double(cut.topLeft().x(), cut.topLeft().y(), 12, 6);
        assertThat(features.picture().orElseThrow().cuts()).singleElement()
                .satisfies(bounds -> assertThat(bounds.contains(cutRectangle)).isTrue());
        assertThat(cuts).singleElement()
                .satisfies(mark -> assertThat(mark.contains(onScreen(cutRectangle))).isTrue());
        Rectangle mark = cuts.getFirst();
        Color amber = pane.scheme().sheetCut();
        int amberish = 0;
        BufferedImage image = paint();
        for (int x = mark.x + 2; x < mark.x + mark.width - 2; x++) {
            Color pixel = new Color(image.getRGB(x, mark.y), true);
            if (pixel.getRed() - pixel.getBlue() > 60 && Math.abs(pixel.getRed() - amber.getRed()) < 60) {
                amberish++;
            }
        }
        assertThat(amberish).as("amber pixels on the mark's top edge").isGreaterThan((mark.width - 4) / 2);
    }

    /** A switch and a lamp about 200 units apart: on A4 portrait at 100 % the sheets between them are empty. */
    private void farApart() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 200, 0, Map.of());
    }

    /** How dark the darkest pixel of row {@code y} between {@code fromX} and {@code toX} is (0 white, 255 black). */
    private static int darkest(BufferedImage image, int fromX, int toX, int y) {
        int darkest = 0;
        for (int x = fromX; x < toX; x++) {
            Color pixel = new Color(image.getRGB(x, y));
            darkest = Math.max(darkest, 255 - (pixel.getRed() + pixel.getGreen() + pixel.getBlue()) / 3);
        }
        return darkest;
    }

    @Test
    void skippingEmptySheetsTheyAreDimmedSayEmptyAndHaveNoBandTowardThem() {
        farApart();
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100), true));
        preferences.setShowSheets(true);
        showAt(0.3, 100, 40);
        SheetGrid grid = grid();
        int columns = grid.columns();
        assertThat(columns).as("the fixture: empty sheets between the parts").isGreaterThan(2);
        assertThat(grid.rows()).isEqualTo(1);

        SheetDrawing drawing = overlay().drawing();

        Rectangle first = onScreen(grid.sheetRectangle(0, 0));
        Rectangle last = onScreen(grid.sheetRectangle(columns - 1, 0));
        assertThat(drawing.borders()).containsExactly(first, last);
        assertThat(drawing.emptyBorders()).hasSize(columns - 2);
        List<String> expectedLabels = new ArrayList<>(List.of("Hoja 1"));
        for (int column = 1; column < columns - 1; column++) {
            expectedLabels.add("Hoja " + (column + 1) + " · vacía");
        }
        expectedLabels.add("Hoja " + columns);
        assertThat(drawing.labels()).extracting(SheetLabel::text).containsExactlyElementsOf(expectedLabels);
        assertThat(drawing.bands().isEmpty()).as("every band touches an empty sheet").isTrue();
        Rectangle empty = drawing.emptyBorders().getFirst();
        BufferedImage image = paint();
        int normalDarkness = darkest(image, first.x + 2, empty.x - 2, first.y);
        int nextSheet = onScreen(grid.sheetRectangle(2, 0)).x;
        int emptyDarkness = darkest(image, first.x + first.width + 2, nextSheet - 2, empty.y);
        assertThat(normalDarkness).as("the fixture: a dashed border").isPositive();
        assertThat(emptyDarkness).as("dimmer than a printed sheet's border").isPositive()
                .isLessThan(normalDarkness);
    }

    @Test
    void withTheOptionOffEmptySheetsLookLikeTheOthers() {
        farApart();
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(100), false));
        preferences.setShowSheets(true);
        showAt(0.3, 100, 40);

        SheetDrawing drawing = overlay().drawing();

        assertThat(drawing.borders()).hasSize(grid().columns());
        assertThat(drawing.emptyBorders()).isEmpty();
        assertThat(drawing.labels()).extracting(SheetLabel::text).noneMatch(text -> text.contains("vacía"));
        assertThat(drawing.bands().isEmpty()).isFalse();
    }

    @Test
    void oneSheetIsJustItsBorder() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        preferences.setShowSheets(true);
        showAt(0.5, 0, 0);

        SheetDrawing drawing = overlay().drawing();

        assertThat(drawing.borders()).hasSize(1);
        assertThat(drawing.labels()).isEmpty();
        assertThat(drawing.bands().isEmpty()).isTrue();
        assertThat(drawing.cuts()).isEmpty();
    }

    @Test
    void theSheetsPaintOverTheCircuitOnlyWhenShownAndNotForAnEmptyCircuit() {
        preferences.setShowSheets(true);
        BufferedImage emptyShown = paint();
        preferences.setShowSheets(false);
        assertThat(differingPixels(emptyShown, paint())).isZero();

        twoColumns();
        showAt(0.5, 50, 40);
        BufferedImage hidden = paint();
        preferences.setShowSheets(true);
        BufferedImage shown = paint();

        assertThat(differingPixels(hidden, shown)).isGreaterThan(500);
        Rectangle first = onScreen(grid().sheetRectangle(0, 0));
        int y = first.y + first.height / 2;
        assertThat(hidden.getRGB(first.x, y) != shown.getRGB(first.x, y)
                || hidden.getRGB(first.x, y + 4) != shown.getRGB(first.x, y + 4)).as("a dash on the left border")
                .isTrue();
    }

    /** Two columns and two rows of A4 portrait sheets at 100 %. */
    private void twoByTwo() {
        twoColumns();
        place(openDocument, StandardComponentTypes.LED, 0, 150, Map.of());
    }

    /** Where the sheet lattice continued over the whole plane has an edge between {@code from} and {@code to}. */
    private static List<Double> extendedLattice(double origin, double step, double overlap, double from, double to) {
        TreeSet<Double> edges = new TreeSet<>();
        for (long index = (long) Math.floor((from - origin) / step) - 1; origin + index * step <= to; index++) {
            for (double edge : new double[]{origin + index * step, origin + index * step + overlap}) {
                if (edge >= from && edge <= to) {
                    edges.add(edge);
                }
            }
        }
        return List.copyOf(edges);
    }

    @Test
    void theLatticeGoesOnOverTheWholePaneWhereTheGridSays() {
        twoByTwo();
        preferences.setShowSheets(true);
        SheetGrid grid = grid();
        assertThat(grid.columns()).as("the fixture").isEqualTo(2);
        assertThat(grid.rows()).as("the fixture").isEqualTo(2);
        Rectangle2D first = grid.sheetRectangle(0, 0);
        Rectangle2D last = grid.sheetRectangle(1, 1);
        int tallHeight = 2 * HEIGHT; // room for a row of the lattice above and below the sheets
        pane.setSize(WIDTH, tallHeight);
        pane.viewport().showAt(Viewport.MINIMUM_ZOOM, (first.getX() + last.getMaxX()) / 2,
                (first.getY() + last.getMaxY()) / 2, WIDTH, tallHeight);
        double stepX = grid.sheetRectangle(1, 0).getX() - first.getX();
        double stepY = grid.sheetRectangle(0, 1).getY() - first.getY();
        double overlapX = grid.rightOverlap(0, 0).orElseThrow().getWidth();
        double overlapY = grid.belowOverlap(0, 0).orElseThrow().getHeight();
        Viewport viewport = pane.viewport();
        Point2D visibleTopLeft = viewport.screenToGrid(0, 0);
        Point2D visibleBottomRight = viewport.screenToGrid(WIDTH, tallHeight);

        SheetDrawing drawing = overlay().drawing();

        List<Integer> expectedColumns = extendedLattice(first.getX(), stepX, overlapX, visibleTopLeft.getX(),
                visibleBottomRight.getX()).stream()
                .map(edge -> (int) Math.round(viewport.gridToScreen(edge, 0).getX())).distinct().toList();
        List<Integer> expectedRows = extendedLattice(first.getY(), stepY, overlapY, visibleTopLeft.getY(),
                visibleBottomRight.getY()).stream()
                .map(edge -> (int) Math.round(viewport.gridToScreen(0, edge).getY())).distinct().toList();
        assertThat(drawing.latticeColumns()).containsExactlyElementsOf(expectedColumns);
        assertThat(drawing.latticeRows()).containsExactlyElementsOf(expectedRows);
        Rectangle occupied = onScreen(new Rectangle2D.Double(first.getX(), first.getY(),
                last.getMaxX() - first.getX(), last.getMaxY() - first.getY()));
        assertThat(drawing.latticeColumns()).as("left of the sheets").anyMatch(x -> x < occupied.x);
        assertThat(drawing.latticeColumns()).as("right of the sheets").anyMatch(x -> x > occupied.getMaxX());
        assertThat(drawing.latticeRows()).as("above the sheets").anyMatch(y -> y < occupied.y);
        assertThat(drawing.latticeRows()).as("below the sheets").anyMatch(y -> y > occupied.getMaxY());
        assertThat(drawing.occupied()).isEqualTo(occupied);
        assertThat(drawing.borders()).hasSize(4);
    }

    @Test
    void oneSheetOrHiddenSheetsHaveNoLattice() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        preferences.setShowSheets(true);
        showAt(Viewport.MINIMUM_ZOOM, 0, 0);

        assertThat(overlay().drawing().latticeColumns()).isEmpty();
        assertThat(overlay().drawing().latticeRows()).isEmpty();

        place(openDocument, StandardComponentTypes.LED, 100, 0, Map.of());
        SheetOverlay overlay = overlay();
        assertThat(overlay.drawing().latticeColumns()).as("the fixture: tiled now").isNotEmpty();
        preferences.setShowSheets(false);
        assertThat(overlay.drawing().latticeColumns()).isEmpty();
        assertThat(overlay.drawing().latticeRows()).isEmpty();
    }

    @Test
    void theLatticeOutsideIsFainterThanTheSheetsBorders() {
        assertThat(SheetOverlay.latticeColor(ColorScheme.LIGHT).getAlpha())
                .isLessThan(ColorScheme.LIGHT.sheet().getAlpha() / 2);
        assertThat(SheetOverlay.latticeColor(ColorSchemes.forTheme(true)).getAlpha())
                .isLessThan(ColorSchemes.forTheme(true).sheet().getAlpha() / 2);

        twoColumns();
        preferences.setShowSheets(true);
        Rectangle2D first = grid().sheetRectangle(0, 0);
        showAt(0.5, first.getX(), first.getCenterY());
        SheetDrawing drawing = overlay().drawing();
        Rectangle sheet = onScreen(first);
        int outsideX = drawing.latticeColumns().stream().filter(x -> x < sheet.x).findFirst().orElseThrow();
        BufferedImage image = paint();

        int outside = darkestInColumn(image, outsideX, sheet.y + 4, sheet.y + sheet.height - 4);
        int inside = darkestInColumn(image, sheet.x, sheet.y + 4, sheet.y + sheet.height - 4);
        assertThat(outside).as("a dashed lattice line").isPositive();
        assertThat(outside).isLessThan(inside);
    }

    /** How dark the darkest pixel of column {@code x} between {@code fromY} and {@code toY} is (0 white). */
    private static int darkestInColumn(BufferedImage image, int x, int fromY, int toY) {
        int darkest = 0;
        for (int y = fromY; y < toY; y++) {
            Color pixel = new Color(image.getRGB(x, y));
            darkest = Math.max(darkest, 255 - (pixel.getRed() + pixel.getGreen() + pixel.getBlue()) / 3);
        }
        return darkest;
    }

    @Test
    void aLatticeTooDenseOnScreenIsLeftOut() {
        assertThat(SheetOverlay.latticeIsReadable(SheetOverlay.MINIMUM_LATTICE_STEP,
                SheetOverlay.MINIMUM_LATTICE_STEP)).isTrue();
        assertThat(SheetOverlay.latticeIsReadable(SheetOverlay.MINIMUM_LATTICE_STEP - 0.5, 100)).isFalse();
        assertThat(SheetOverlay.latticeIsReadable(100, SheetOverlay.MINIMUM_LATTICE_STEP - 0.5)).isFalse();

        twoColumns();
        preferences.setPrintMarginPoints(260);
        preferences.setPrintOptions(SheetsFeaturesTest.options(Paper.A4, new ScaleMode.Percentage(400)));
        preferences.setShowSheets(true);
        showAt(Viewport.MINIMUM_ZOOM, 50, 40);

        SheetDrawing drawing = overlay().drawing();

        assertThat(drawing.borders()).as("the occupied sheets are still drawn").isNotEmpty();
        assertThat(drawing.borders().getFirst().width).as("the fixture: tiny sheets")
                .isLessThan((int) SheetOverlay.MINIMUM_LATTICE_STEP);
        assertThat(drawing.latticeColumns()).isEmpty();
        assertThat(drawing.latticeRows()).isEmpty();
    }

    private static MouseEvent mouse(CircuitCanvas canvas, int id, Point2D screen, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, (int) Math.round(screen.getX()),
                (int) Math.round(screen.getY()), 1, false, button);
    }

    /** Presses at grid point {@code from} and drags, without releasing, {@code deltaX} grid units to the right. */
    private void pressAndDrag(Point2D from, int deltaX) {
        pressAndDrag(from, deltaX, InputEvent.BUTTON1_DOWN_MASK);
    }

    /** As {@link #pressAndDrag(Point2D, int)}, the drag with {@code dragModifiers} (Ctrl copies). */
    private void pressAndDrag(Point2D from, int deltaX, int dragModifiers) {
        Point2D pressed = pane.viewport().gridToScreen(from.getX(), from.getY());
        Point2D dragged = pane.viewport().gridToScreen(from.getX() + deltaX, from.getY());
        pane.getMouseListeners()[0].mousePressed(mouse(pane, MouseEvent.MOUSE_PRESSED, pressed,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        pane.getMouseMotionListeners()[0].mouseDragged(mouse(pane, MouseEvent.MOUSE_DRAGGED, dragged,
                dragModifiers, MouseEvent.NOBUTTON));
    }

    private void release(Point2D at) {
        pane.getMouseListeners()[0].mouseReleased(mouse(pane, MouseEvent.MOUSE_RELEASED, at, 0,
                MouseEvent.BUTTON1));
    }

    /** Drags the display of {@link #displayAndLamp()} to straddle the border left of the sheets; returns where. */
    private Component dragTheDisplayAcrossTheBorder(int dragModifiers) {
        Component display = displayAndLamp();
        preferences.setShowSheets(true);
        Rectangle2D bounds = drawn(display);
        showTheCellLeftOfTheSheets();
        pressAndDrag(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()),
                deltaTo(bounds, boundaryLeftOfTheSheets()), dragModifiers);
        assertThat(overlay().drawing().movingCuts()).as("the fixture: outlined while dragged").hasSize(1);
        return display;
    }

    private Rectangle2D drawn(DocumentElement element) {
        Bounds bounds = DrawnBounds.of(element, openDocument.registry());
        return new Rectangle2D.Double(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    /** A double seven-segment display at the origin and a lamp 100 units to its right: two columns of sheets. */
    private Component displayAndLamp() {
        Component display = place(openDocument, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 100, 0, Map.of());
        return display;
    }

    /** How many grid units to the right move {@code bounds}' centre onto {@code target}. */
    private static int deltaTo(Rectangle2D bounds, double target) {
        return (int) Math.round(target - bounds.getCenterX());
    }

    /** The middle of the pair of lattice edges one step left of the first sheet's left edge. */
    private double boundaryLeftOfTheSheets() {
        SheetGrid grid = grid();
        Rectangle2D first = grid.sheetRectangle(0, 0);
        double step = grid.sheetRectangle(1, 0).getX() - first.getX();
        return first.getX() - step + grid.rightOverlap(0, 0).orElseThrow().getWidth() / 2;
    }

    /** Shows the cell of the lattice left of the first sheet in the middle of the pane, at 50 %. */
    private void showTheCellLeftOfTheSheets() {
        SheetGrid grid = grid();
        Rectangle2D first = grid.sheetRectangle(0, 0);
        double step = grid.sheetRectangle(1, 0).getX() - first.getX();
        showAt(0.5, first.getX() - step / 2, first.getCenterY());
    }

    @Test
    void aPartDraggedAcrossALatticeBorderLeftOfTheSheetsIsOutlinedInAmberWithoutEditing() {
        Component display = displayAndLamp();
        preferences.setShowSheets(true);
        SheetGrid grid = grid();
        Rectangle2D first = grid.sheetRectangle(0, 0);
        double step = grid.sheetRectangle(1, 0).getX() - first.getX();
        double overlap = grid.rightOverlap(0, 0).orElseThrow().getWidth();
        Rectangle2D bounds = drawn(display);
        assertThat(bounds.getWidth()).as("the fixture: wider than the overlap").isGreaterThan(overlap + 2);
        showTheCellLeftOfTheSheets();
        long revision = openDocument.document().revision();

        pressAndDrag(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()),
                deltaTo(bounds, boundaryLeftOfTheSheets()));

        assertThat(pane.movingElements()).singleElement()
                .satisfies(moving -> assertThat(moving.id()).isEqualTo(display.id()));
        Rectangle2D moved = drawn(pane.movingElements().getFirst());
        assertThat(moved.getMinX()).as("the preview straddles the border").isLessThan(first.getX() - step)
                .isGreaterThan(first.getX() - 2 * step);
        assertThat(moved.getMaxX()).isGreaterThan(first.getX() - step + overlap);
        SheetDrawing drawing = overlay().drawing();
        assertThat(drawing.movingCuts()).singleElement()
                .satisfies(mark -> assertThat(mark.contains(onScreen(moved))).isTrue());
        Rectangle mark = drawing.movingCuts().getFirst();
        Color amber = pane.scheme().sheetCut();
        BufferedImage image = paint();
        int amberish = 0;
        for (int x = mark.x + 2; x < mark.x + mark.width - 2; x++) {
            Color pixel = new Color(image.getRGB(x, mark.y), true);
            if (pixel.getRed() - pixel.getBlue() > 60 && Math.abs(pixel.getRed() - amber.getRed()) < 60) {
                amberish++;
            }
        }
        assertThat(amberish).as("amber pixels on the outline's top edge").isGreaterThan((mark.width - 4) / 2);
        assertThat(openDocument.document().revision()).as("nothing is edited during the drag").isEqualTo(revision);
        assertThat(openDocument.document().element(display.id())).contains(display);
        assertThat(grid().columns()).as("the sheets do not move").isEqualTo(2);
    }

    @Test
    void theOutlineGoesWhenTheDragIsReleased() {
        Component display = dragTheDisplayAcrossTheBorder(InputEvent.BUTTON1_DOWN_MASK);
        Rectangle2D moved = drawn(pane.movingElements().getFirst());

        release(pane.viewport().gridToScreen(moved.getCenterX(), moved.getCenterY()));

        assertThat(pane.movingElements()).isEmpty();
        assertThat(overlay().drawing().movingCuts()).isEmpty();
        assertThat(openDocument.document().element(display.id())).as("the move is committed").isPresent();
    }

    @Test
    void theOutlineGoesWithEscape() {
        Component display = dragTheDisplayAcrossTheBorder(InputEvent.BUTTON1_DOWN_MASK);

        assertThat(pane.pressKeyOnTool(java.awt.event.KeyEvent.VK_ESCAPE, false)).isTrue();

        assertThat(pane.movingElements()).isEmpty();
        assertThat(overlay().drawing().movingCuts()).isEmpty();
        assertThat(openDocument.document().element(display.id()).orElseThrow()).as("nothing moved")
                .isEqualTo(display);
    }

    @Test
    void theOutlineGoesWhenTheToolIsSwitched() {
        dragTheDisplayAcrossTheBorder(InputEvent.BUTTON1_DOWN_MASK);

        pane.useDefaultTool();
        pane.setTool(new cl.almejo.vsim.ui.tools.WireTool());

        assertThat(pane.movingElements()).isEmpty();
        assertThat(overlay().drawing().movingCuts()).isEmpty();
    }

    @Test
    void aCtrlCopyIsOutlinedWhereTheCopyWouldLand() {
        Component display = dragTheDisplayAcrossTheBorder(InputEvent.BUTTON1_DOWN_MASK
                | InputEvent.CTRL_DOWN_MASK);

        assertThat(pane.movingElements()).singleElement()
                .satisfies(moving -> assertThat(moving.id()).isEqualTo(display.id()));
        Rectangle2D copy = drawn(pane.movingElements().getFirst());
        assertThat(overlay().drawing().movingCuts().getFirst().contains(onScreen(copy))).isTrue();
        assertThat(openDocument.document().element(display.id()).orElseThrow()).as("the original stays")
                .isEqualTo(display);
    }

    @Test
    void aStickyNoteDragOutlinesNothingAndDoesNotFail() {
        displayAndLamp();
        preferences.setShowSheets(true);
        StickyNote note = new StickyNote(openDocument.document().newStickyNoteId(), new GridPoint(0, 60), 8, 6,
                StickyColor.YELLOW, new StickyText("nota"), false);
        openDocument.execute(new AddElementsCommand(List.of(note)));
        showTheCellLeftOfTheSheets();

        pressAndDrag(new Point2D.Double(note.position().x() + 3, note.position().y() + 3),
                deltaTo(drawn(note), boundaryLeftOfTheSheets()));

        assertThat(overlay().drawing().movingCuts()).as("notes are not parts or frames").isEmpty();
    }

    @Test
    void aDragInAnotherPaneIsOutlinedHereToo() {
        Component display = displayAndLamp();
        preferences.setShowSheets(true);
        view.canvasPanes().split(cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation.VERTICAL, 0.5);
        CircuitCanvas other = view.panes().get(1);
        other.setSize(WIDTH, HEIGHT);
        SheetGrid grid = grid();
        Rectangle2D first = grid.sheetRectangle(0, 0);
        double step = grid.sheetRectangle(1, 0).getX() - first.getX();
        other.viewport().showAt(0.5, first.getX() - step / 2, first.getCenterY(), WIDTH, HEIGHT);
        showTheCellLeftOfTheSheets();
        Rectangle2D bounds = drawn(display);

        pressAndDrag(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()),
                deltaTo(bounds, boundaryLeftOfTheSheets()));

        SheetOverlay otherOverlay = other.overlays().stream().filter(SheetOverlay.class::isInstance)
                .map(SheetOverlay.class::cast).findFirst().orElseThrow();
        assertThat(other.movingElements()).extracting(DocumentElement::id).containsExactly(display.id());
        assertThat(otherOverlay.drawing().movingCuts()).as("the other pane outlines it too").hasSize(1);
    }

    @Test
    void theMovingPartsAreMeasuredOncePerChangeOfThePreviewNotAtEveryRepaint() {
        dragTheDisplayAcrossTheBorder(InputEvent.BUTTON1_DOWN_MASK);
        int measured = overlay().movingMeasurements();

        overlay().drawing();
        paint();
        overlay().drawing();

        assertThat(overlay().movingMeasurements()).as("same preview, nothing measured again").isEqualTo(measured);
        Rectangle2D moved = drawn(pane.movingElements().getFirst());
        pane.getMouseMotionListeners()[0].mouseDragged(mouse(pane, MouseEvent.MOUSE_DRAGGED,
                pane.viewport().gridToScreen(moved.getCenterX() + 1, moved.getCenterY()),
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
        overlay().drawing();
        assertThat(overlay().movingMeasurements()).as("another offset").isGreaterThan(measured);
    }

    @Test
    void theLatticeDashesKeepThePhaseOfTheSheetsCorner() {
        assertThat(SheetOverlay.dashStart(3)).as("the last dash period at or before the edge").isEqualTo(-5);
        assertThat(SheetOverlay.dashStart(-3)).isEqualTo(-3);
        assertThat(SheetOverlay.dashStart(8)).isZero();
        assertThat(SheetOverlay.dashStart(100)).isEqualTo(-4);
        for (int anchor = -50; anchor < 50; anchor++) {
            assertThat(SheetOverlay.dashStart(anchor)).isLessThanOrEqualTo(0).isGreaterThan(-8);
            assertThat(Math.floorMod(SheetOverlay.dashStart(anchor) - anchor, 8)).as("same phase as %d", anchor)
                    .isZero();
        }
    }

    @Test
    void aPartDraggedInsideALatticeCellIsNotOutlined() {
        Component display = displayAndLamp();
        preferences.setShowSheets(true);
        SheetGrid grid = grid();
        Rectangle2D first = grid.sheetRectangle(0, 0);
        double step = grid.sheetRectangle(1, 0).getX() - first.getX();
        double overlap = grid.rightOverlap(0, 0).orElseThrow().getWidth();
        Rectangle2D bounds = drawn(display);
        showTheCellLeftOfTheSheets();
        double cellMiddle = first.getX() - step / 2 + overlap / 2;

        pressAndDrag(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()), deltaTo(bounds, cellMiddle));

        assertThat(pane.movingElements()).hasSize(1);
        assertThat(grid.isCut(drawn(pane.movingElements().getFirst())))
                .as("the fixture: outside the occupied sheets").isTrue();
        assertThat(overlay().drawing().movingCuts()).isEmpty();
    }

    /** A frame {@code width} units wide below the display, created as the user would. */
    private Frame frameBelow(int width) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 40), width, 6, "",
                FrameTint.NONE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    @Test
    void aFrameDraggedByItsTitleAcrossABorderIsOutlined() {
        displayAndLamp();
        Frame frame = frameBelow(12);
        preferences.setShowSheets(true);
        showTheCellLeftOfTheSheets();
        long revision = openDocument.document().revision();

        pressAndDrag(new Point2D.Double(frame.topLeft().x() + 3, frame.topLeft().y() + 0.5),
                deltaTo(drawn(frame), boundaryLeftOfTheSheets()));

        assertThat(pane.movingElements()).extracting(DocumentElement::id).containsExactly(frame.id());
        assertThat(overlay().drawing().movingCuts()).hasSize(1);
        assertThat(openDocument.document().revision()).isEqualTo(revision);
    }

    @Test
    void aFrameBiggerThanASheetIsNotOutlinedWhileDragged() {
        displayAndLamp();
        preferences.setShowSheets(true);
        Frame frame = frameBelow((int) Math.ceil(grid().sheetRectangle(0, 0).getWidth()) + 4);
        showTheCellLeftOfTheSheets();

        pressAndDrag(new Point2D.Double(frame.topLeft().x() + 3, frame.topLeft().y() + 0.5),
                deltaTo(drawn(frame), boundaryLeftOfTheSheets()));

        assertThat(pane.movingElements()).extracting(DocumentElement::id).containsExactly(frame.id());
        assertThat(overlay().drawing().movingCuts()).as("cut anyway, so not marked").isEmpty();
    }

    @Test
    void anExportedImageIsTheSameWithTheSheetsShownOrHidden() throws Exception {
        twoColumns();
        List<Path> answers = new ArrayList<>(List.of(directory.resolve("hidden.png"),
                directory.resolve("shown.png"), directory.resolve("hidden.svg"), directory.resolve("shown.svg")));
        ExportImageFeatures export = new ExportImageFeatures(view,
                (parent, start, name, scale, background, stickyNotes) -> {
                    Path target = answers.removeFirst();
                    ExportChoice.Format format = target.toString().endsWith(".svg")
                            ? ExportChoice.Format.SVG : ExportChoice.Format.PNG;
                    return Optional.of(new ExportChoice(target, format, 1, true));
                }, (parent, message) -> { });
        try {
            perform(export.action());
            preferences.setShowSheets(true);
            features.picture();
            assertThat(overlay().drawing().latticeColumns()).as("the fixture: the lattice is on the canvas")
                    .isNotEmpty();
            perform(export.action());
            preferences.setShowSheets(false);
            perform(export.action());
            preferences.setShowSheets(true);
            perform(export.action());
        } finally {
            export.dispose();
        }

        assertThat(Files.readAllBytes(directory.resolve("shown.png")))
                .isEqualTo(Files.readAllBytes(directory.resolve("hidden.png")));
        assertThat(Files.readString(directory.resolve("shown.svg")))
                .isEqualTo(Files.readString(directory.resolve("hidden.svg")));
    }
}
