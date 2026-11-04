package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.export.ExportChoice;
import cl.almejo.vsim.ui.export.ExportImageFeatures;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What one pane draws of the sheets (sheets spec §3, §6, §7): dashed borders and overlap bands where the grid says,
 * only for the visible sheets; «Hoja n» inside each sheet unless it is small on screen, stuck to the visible corner
 * when the sheet's own corner is scrolled away; cut marks around parts and frames a border cuts; nothing when hidden
 * or empty, and never in an exported image.
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
                preferences, new ComponentLibrary(), List.of());
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
