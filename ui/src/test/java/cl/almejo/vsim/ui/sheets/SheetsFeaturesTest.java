package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.PreferenceLayer;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.guides.GuidesFeatures;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScope;
import cl.almejo.vsim.ui.print.ScaleMode;
import cl.almejo.vsim.ui.print.SheetGrid;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * «Mostrar hojas de impresión» in the views (sheets spec §0a, §5): the toggle in Ver and on the toolbar follows
 * {@code sheets.show}, each pane gets its overlay and summary label only while the sheets are shown, and the anchor is
 * taken by the first view that lays the sheets out tiled, kept while editing and while any view of the document shows
 * them, cleared when none does.
 */
class SheetsFeaturesTest {

    private static final String NAME = "Mostrar hojas de impresión";

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final SheetAnchors anchors = new SheetAnchors();
    private final SheetsExtension extension = new SheetsExtension(anchors, (menu, invoker, x, y) -> { });
    private final List<Runnable> cleanUps = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorServices services;
    private EditorView view;
    private SheetsFeatures features;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), List.of());
        view = newView(services);
        features = install(view);
        preferences.setPrintOptions(options(Paper.A4, new ScaleMode.Percentage(100)));
    }

    @AfterEach
    void close() {
        cleanUps.forEach(Runnable::run);
        openDocument.close();
    }

    private EditorView newView(EditorServices viewServices) {
        EditorView created = new EditorView(openDocument, viewServices, NO_WINDOW);
        created.canvas().setSize(800, 600);
        cleanUps.add(created::dispose);
        return created;
    }

    private SheetsFeatures install(EditorView target) {
        SheetsFeatures installed = extension.installFeatures(target);
        cleanUps.add(0, installed::dispose);
        return installed;
    }

    static PrintOptions options(Paper paper, ScaleMode scale) {
        return new PrintOptions(new PrintScope.WholeCircuit(), paper, PageOrientation.PORTRAIT, scale, true, false,
                false, "");
    }

    /** A switch at the left and a lamp about 100 units to its right: two A4 portrait columns at 100 %. */
    private Component twoColumns() {
        Component left = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        place(openDocument, StandardComponentTypes.LED, 100, 0, Map.of());
        return left;
    }

    private static List<JCheckBoxMenuItem> items(EditorView target) {
        List<JCheckBoxMenuItem> found = new ArrayList<>();
        for (java.awt.Component child : target.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JCheckBoxMenuItem item && NAME.equals(item.getText())) {
                found.add(item);
            }
        }
        return found;
    }

    private static List<AbstractButton> buttons(EditorView target) {
        List<AbstractButton> found = new ArrayList<>();
        for (java.awt.Component child : target.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && NAME.equals(button.getAction().getValue(Action.NAME))) {
                found.add(button);
            }
        }
        return found;
    }

    private static long sheetOverlays(CircuitCanvas pane) {
        return pane.overlays().stream().filter(SheetOverlay.class::isInstance).count();
    }

    private static long summaryHandles(CircuitCanvas pane) {
        return pane.edgeHandles().stream().filter(SheetSummaryHandle.class::isInstance).count();
    }

    private Optional<GridPoint> anchor() {
        return anchors.anchor(openDocument.document());
    }

    // --- the toggle --------------------------------------------------------------------------------------------

    @Test
    void theSheetsAreHiddenByDefaultWithOneItemAndOneToolbarToggle() {
        assertThat(preferences.showSheets()).isFalse();
        assertThat(items(view)).hasSize(1);
        assertThat(items(view).getFirst().isSelected()).isFalse();
        assertThat(buttons(view)).hasSize(1);
        assertThat(buttons(view).getFirst().getToolTipText()).isEqualTo(NAME);
        assertThat(buttons(view).getFirst().getIcon()).isNotNull();
    }

    @Test
    void theItemAndTheButtonFollowThePreferenceInEveryView() {
        EditorView other = newView(services);
        install(other);

        buttons(view).getFirst().doClick();

        assertThat(preferences.showSheets()).isTrue();
        for (EditorView each : List.of(view, other)) {
            assertThat(items(each).getFirst().isSelected()).as("item").isTrue();
            assertThat(buttons(each).getFirst().isSelected()).as("button").isTrue();
        }
        items(other).getFirst().doClick();
        assertThat(preferences.showSheets()).isFalse();
        assertThat(buttons(view).getFirst().isSelected()).isFalse();
    }

    @Test
    void theItemComesRightAfterClearGuidesAndTheShortcutHasNoDefaultKey() {
        GuidesFeatures guides = new GuidesFeatures(view);
        cleanUps.add(0, guides::dispose);
        features.dispose();
        install(view);

        List<String> texts = texts(view.menu(MenuId.VIEW));
        assertThat(texts.get(texts.indexOf("Borrar guías") + 1)).isEqualTo(NAME);
        assertThat(view.shortcutActions()).containsKey("view.showSheets");
        assertThat(ShortcutCatalog.find("view.showSheets").orElseThrow().defaultStroke()).isEmpty();
    }

    // --- the panes ---------------------------------------------------------------------------------------------

    @Test
    void everyPaneGetsAnOverlayAndASummaryLabelOnlyWhileTheSheetsAreShown() {
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        assertThat(view.panes()).allSatisfy(pane -> assertThat(sheetOverlays(pane)).isZero());

        preferences.setShowSheets(true);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        assertThat(view.panes()).hasSize(4).allSatisfy(pane -> {
            assertThat(sheetOverlays(pane)).isEqualTo(1);
            assertThat(summaryHandles(pane)).isEqualTo(1);
        });

        preferences.setShowSheets(false);
        assertThat(view.panes()).allSatisfy(pane -> {
            assertThat(sheetOverlays(pane)).isZero();
            assertThat(summaryHandles(pane)).isZero();
        });
    }

    @Test
    void disposingTakesBackTheItemTheButtonTheShortcutThePanesAndTheListeners() {
        int changeListeners = openDocument.changeListenerCount();
        preferences.setShowSheets(true);
        int overlays = view.canvas().overlayCount();

        features.dispose();

        assertThat(items(view)).isEmpty();
        assertThat(buttons(view)).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("view.showSheets");
        assertThat(view.canvas().overlayCount()).isEqualTo(overlays - 1);
        assertThat(summaryHandles(view.canvas())).isZero();
        assertThat(anchors.listenerCount()).isZero();
        assertThat(openDocument.changeListenerCount()).isEqualTo(changeListeners - 1);
    }

    @Test
    void nothingIsLaidOutWhileHiddenOrForAnEmptyCircuit() {
        twoColumns();
        assertThat(features.picture()).isEmpty();

        openDocument.history().undo();
        openDocument.history().undo();
        preferences.setShowSheets(true);
        assertThat(features.picture()).isEmpty();
        assertThat(anchor()).isEmpty();
    }

    @Test
    void theLayoutIsKeptUntilSomethingChanges() {
        twoColumns();
        preferences.setShowSheets(true);
        Optional<SheetPicture> first = features.picture();

        assertThat(features.picture()).isSameAs(first);
        place(openDocument, StandardComponentTypes.LED, 20, 40, Map.of());
        assertThat(features.picture()).isNotSameAs(first);
    }

    // --- the anchor --------------------------------------------------------------------------------------------

    @Test
    void showingATiledCircuitAnchorsItsSheetsWhereTheyAre() {
        twoColumns();
        preferences.setShowSheets(true);

        SheetGrid grid = features.picture().orElseThrow().grid();

        assertThat(grid.columns()).isEqualTo(2);
        Rectangle2D first = grid.sheetRectangle(0, 0);
        assertThat(anchor()).contains(new GridPoint((int) first.getX(), (int) first.getY()));
    }

    @Test
    void oneSheetIsNotAnchoredUntilTheCircuitGrowsToSeveral() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        preferences.setShowSheets(true);

        assertThat(features.picture().orElseThrow().grid().tiled()).isFalse();
        assertThat(anchor()).isEmpty();

        place(openDocument, StandardComponentTypes.LED, 100, 0, Map.of());
        assertThat(features.picture().orElseThrow().grid().tiled()).isTrue();
        assertThat(anchor()).isPresent();
    }

    @Test
    void movingAPartPastTheLeftEdgeAddsAColumnAndTheOtherSheetsStayPut() {
        Component left = twoColumns();
        preferences.setShowSheets(true);
        SheetGrid before = features.picture().orElseThrow().grid();

        openDocument.execute(new MoveElementsCommand(List.of(left.id()), -80, 0));
        SheetGrid after = features.picture().orElseThrow().grid();

        int added = after.columns() - before.columns();
        assertThat(added).isPositive();
        for (int column = 0; column < before.columns(); column++) {
            Rectangle2D kept = before.sheetRectangle(column, 0);
            Rectangle2D now = after.sheetRectangle(column + added, 0);
            assertThat(now.getX()).isCloseTo(kept.getX(), within(1e-6));
            assertThat(now.getY()).isCloseTo(kept.getY(), within(1e-6));
            assertThat(now.getWidth()).isCloseTo(kept.getWidth(), within(1e-6));
        }
    }

    @Test
    void hidingClearsTheAnchorAndShowingAgainLaysTheSheetsOutFromTheCircuitAsItIsNow() {
        Component left = twoColumns();
        preferences.setShowSheets(true);
        features.picture();
        GridPoint first = anchor().orElseThrow();

        preferences.setShowSheets(false);
        assertThat(anchor()).isEmpty();

        openDocument.execute(new MoveElementsCommand(List.of(left.id()), -30, 0));
        preferences.setShowSheets(true);
        features.picture();
        assertThat(anchor()).isPresent().get().isNotEqualTo(first);
        assertThat(anchor().orElseThrow().x()).isEqualTo(first.x() - 30);
    }

    @Test
    void changingThePaperOrTheScaleWhileShownKeepsTheAnchor() {
        twoColumns();
        preferences.setShowSheets(true);
        features.picture();
        GridPoint first = anchor().orElseThrow();

        preferences.setPrintOptions(options(Paper.A3, new ScaleMode.Percentage(150)));
        assertThat(features.picture().orElseThrow().grid().layout().geometry().paper()).isEqualTo(Paper.A3);
        assertThat(anchor()).contains(first);
        preferences.setPrintOptions(options(Paper.A4, new ScaleMode.FitToOnePage()));
        assertThat(features.picture().orElseThrow().grid().tiled()).isFalse();
        assertThat(anchor()).as("one sheet holds no anchor").isEmpty();
    }

    @Test
    void aOneSheetStateDropsTheAnchorSoTheNextTiledLayoutStartsFromTheCircuitsCorner() {
        twoColumns();
        preferences.setShowSheets(true);
        features.picture();
        GridPoint first = anchor().orElseThrow();

        preferences.setPrintOptions(options(Paper.A4, new ScaleMode.FitToOnePage()));
        assertThat(features.picture().orElseThrow().grid().tiled()).isFalse();
        assertThat(anchor()).as("no anchor while on one sheet").isEmpty();

        List<cl.almejo.vsim.core.document.ElementId> everything = openDocument.document().elements().stream()
                .map(cl.almejo.vsim.core.document.DocumentElement::id).toList();
        openDocument.execute(new MoveElementsCommand(everything, 150, 0));
        preferences.setPrintOptions(options(Paper.A4, new ScaleMode.Percentage(100)));
        SheetGrid grid = features.picture().orElseThrow().grid();

        assertThat(grid.tiled()).isTrue();
        assertThat(anchor()).isPresent().get().isNotEqualTo(first);
        assertThat(anchor().orElseThrow().x()).isEqualTo(first.x() + 150);
        assertThat(grid.columns()).as("the automatic grid, not one on the old lattice").isEqualTo(2);
    }

    @Test
    void redefiningAUserComponentThatResizesItsInstancesLaysTheSheetsOutAgain() {
        cl.almejo.vsim.core.library.ComponentDefinition small = new cl.almejo.vsim.core.library.ComponentDefinition(
                "box", "Caja", "", new cl.almejo.vsim.core.geometry.GridSize(4, 4), List.of(),
                new cl.almejo.vsim.core.io.CircuitFragment(List.of(), List.of(), List.of()));
        openDocument.document().putDefinition(small);
        place(openDocument, cl.almejo.vsim.core.library.SubcircuitTypes.typeIdOf("box"), 0, 0, Map.of());
        preferences.setShowSheets(true);
        double before = features.picture().orElseThrow().area().getWidth();

        cl.almejo.vsim.core.library.ComponentDefinition large = new cl.almejo.vsim.core.library.ComponentDefinition(
                "box", "Caja", "", new cl.almejo.vsim.core.geometry.GridSize(300, 4), List.of(),
                new cl.almejo.vsim.core.io.CircuitFragment(List.of(), List.of(), List.of()));
        openDocument.execute(cl.almejo.vsim.core.command.ReplaceDefinitionCommand.prepare(openDocument.document(),
                openDocument.registry(), new cl.almejo.vsim.core.library.DefinitionBundle(large, List.of())));

        assertThat(features.picture().orElseThrow().area().getWidth()).isGreaterThan(before + 200);
        assertThat(features.picture().orElseThrow().grid().tiled()).isTrue();
    }

    @Test
    void twoViewsShareTheAnchorAndItGoesWithTheLastViewThatShowsTheSheets() {
        twoColumns();
        EditorView other = newView(services);
        SheetsFeatures otherFeatures = install(other);
        preferences.setShowSheets(true);
        features.picture();
        GridPoint taken = anchor().orElseThrow();

        assertThat(otherFeatures.picture().orElseThrow().grid().sheetRectangle(0, 0).getX()).isEqualTo(taken.x());
        features.dispose();
        assertThat(anchor()).contains(taken);

        otherFeatures.dispose();
        assertThat(anchor()).isEmpty();
    }

    @Test
    void aWindowWhereTheAiShowsTheSheetsKeepsTheAnchorUntilItsLayerGoes() {
        twoColumns();
        AppPreferences layered = preferences.layered();
        EditorView aiWindow = newView(services.withPreferences(layered));
        SheetsFeatures aiFeatures = install(aiWindow);
        PreferenceLayer layer = layered.layer().orElseThrow();

        layer.put(PreferenceLayer.Setting.SHOW_SHEETS, "true");
        assertThat(features.picture()).isEmpty();
        aiFeatures.picture();
        assertThat(anchor()).isPresent();
        preferences.setPrintOptions(options(Paper.A4, new ScaleMode.Percentage(100))); // any change: still shown
        assertThat(anchor()).isPresent();

        layer.clear();
        assertThat(anchor()).isEmpty();
    }

    @Test
    void anotherDocumentsAnchorIsLeftAlone() {
        twoColumns();
        OpenDocument otherDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            anchors.take(otherDocument.document(), new GridPoint(5, 5));
            preferences.setShowSheets(true);
            features.picture();
            preferences.setShowSheets(false);

            assertThat(anchors.anchor(otherDocument.document())).contains(new GridPoint(5, 5));
        } finally {
            otherDocument.close();
        }
    }

    @Test
    void showingTheSheetsNeverMarksTheCircuitModifiedNorAddsAnUndoStep() {
        preferences.setShowSheets(true);
        assertThat(openDocument.isModified()).isFalse();
        assertThat(openDocument.history().canUndo()).isFalse();
    }
}
