package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.favorites.PaletteFavorites;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.PlaceDefinitionTool;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import cl.almejo.vsim.ui.recents.RecentComponentsExtension;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

class QuickInsertTest {

    private OpenDocument document;
    private EditorView view;
    private QuickInsertFeatures features;
    private QuickInsertPanel shown;
    private int closed;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        view.canvas().setSize(800, 600);
        features = new QuickInsertFeatures(view, (canvas, point, panel) -> {
            shown = panel;
            return () -> closed++;
        });
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        document.close();
    }

    private CanvasMouseEvent at(int x, int y, int clicks) {
        return new CanvasMouseEvent(new Point2D.Double(x, y), new GridPoint(x, y),
                view.canvas().viewport().gridToScreen(x, y), CanvasMouseEvent.PRIMARY, false, false, clicks);
    }

    @Test
    void quickInsertWorksWithoutThePaletteFavoritesWhichAreTheirOwnExtension() {
        assertThat(view.palette().visibleSections()).doesNotContain("Favoritos");
        view.services().preferences().setPaletteFavorites(List.of("led"));
        view.canvas().tool().mousePressed(view.canvas(), at(10, 20, 2));
        assertThat(shown.results()).isEmpty();
        shown.setQuery("and3");
        shown.acceptSelection();
        assertThat(document.document().components()).singleElement()
                .extracting(Component::typeId).isEqualTo("and3");
    }

    @Test
    void aBlankSearchListsTheFavoritesThenTheRecentsAndTheArrowsSkipTheHeadings() {
        showFavorites();
        view.services().preferences().setPaletteFavorites(List.of("led"));
        view.palette().setRecents(List.of("and3", "led", "or2"));
        view.canvas().tool().mousePressed(view.canvas(), at(10, 20, 2));

        assertThat(shown.groupTitles()).containsExactly("Favoritos", "Recientes");
        assertThat(shown.results()).extracting(entry -> entry.type().id()).containsExactly("led", "and3", "or2");
        JTextField field = shown.searchField();
        Object down = field.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        field.getActionMap().get(down).actionPerformed(new ActionEvent(field, 0, ""));
        shown.acceptSelection();

        assertThat(document.document().components()).singleElement()
                .extracting(Component::typeId).isEqualTo("and3");
    }

    @Test
    void withOnlyRecentsTheirGroupIsTheOnlyOneAndAQuickInsertCountsAsUsed() {
        new RecentComponentsExtension().install(view);
        view.canvas().tool().mousePressed(view.canvas(), at(10, 20, 2));
        assertThat(shown.groupTitles()).isEmpty();
        shown.setQuery("and3");
        shown.acceptSelection();

        view.canvas().tool().mousePressed(view.canvas(), at(40, 40, 2));

        assertThat(shown.groupTitles()).containsExactly("Recientes");
        assertThat(shown.results()).extracting(entry -> entry.type().id()).containsExactly("and3");
    }

    private void showFavorites() {
        new PaletteFavorites(view.palette(), view.services().preferences()).install();
    }

    @Test
    void doubleClickPlacesAtTheChosenPointAsOneUndoAndKeepsItSelected() {
        view.canvas().tool().mousePressed(view.canvas(), at(10, 20, 2));
        assertThat(shown).isNotNull();
        assertThat(document.document().components()).isEmpty();
        shown.setQuery("and3");
        shown.acceptSelection();
        assertThat(document.document().components()).singleElement().satisfies(component -> {
            assertThat(component.typeId()).isEqualTo("and3");
            assertThat(component.position()).isEqualTo(new GridPoint(10, 20));
            assertThat(view.selection().contains(component.id())).isTrue();
        });
        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
        document.history().undo();
        assertThat(document.document().components()).isEmpty();
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void theShortcutStartsFloatingPlacementAndEscapeCancelsWithoutAnEdit() {
        view.shortcutActions().get("edit.quickInsert").actionPerformed(new ActionEvent(view, 0, ""));
        shown.setQuery("and3");
        shown.acceptSelection();
        assertThat(document.document().components()).isEmpty();
        assertThat(view.canvas().tool()).isInstanceOf(PlaceComponentTool.class);
        view.canvas().tool().keyPressed(view.canvas(), KeyEvent.VK_R, false);
        assertThat(((PlaceComponentTool) view.canvas().tool()).rotation()).isEqualTo(Rotation.R90);
        view.canvas().tool().keyPressed(view.canvas(), KeyEvent.VK_ESCAPE, false);
        assertThat(document.document().components()).isEmpty();
        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
    }

    @Test
    void blankSearchUsesFavoritesAndTypingSearchesTheWholeCatalogueIncludingUserComponents() {
        showFavorites();
        view.services().preferences().setPaletteFavorites(List.of("led", "missing", "and3"));
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        view.palette().setUserComponents(List.of(bundle), List.of());
        view.palette().setUserComponentChooser(component ->
                view.setToolOnEveryPane(() -> new PlaceDefinitionTool(component.bundle())));
        view.canvas().tool().mousePressed(view.canvas(), at(12, 14, 2));
        assertThat(shown.results()).extracting(entry -> entry.type().id()).containsExactly("led", "and3");
        shown.setQuery("INVERSOR");
        assertThat(shown.results()).extracting(entry -> entry.type().id()).containsExactly("subcircuit:inverter");
        shown.acceptSelection();
        assertThat(document.document().components()).singleElement()
                .extracting(component -> component.typeId()).isEqualTo("subcircuit:inverter");
        assertThat(document.document().definition("inverter")).isPresent();
        document.history().undo();
        assertThat(document.document().components()).isEmpty();
        assertThat(document.document().definition("inverter")).isEmpty();
    }

    @Test
    void aDoubleClickOnAPinTipOutsideTheBodyDoesNotOpenTheSearch() {
        Component gate = document.registry().require("and3").newComponent(new ElementId("gate"),
                new GridPoint(20, 20), Rotation.R0, Map.of(), "");
        document.execute(new AddElementsCommand(List.of(gate)));
        SelectToolDelegate delegate = ((SelectTool) view.canvas().defaultTool()).delegates().getLast();
        Point2D outputTip = new Point2D.Double(24.4, 22);
        assertThat(view.canvas().hitTester().elementAt(document.document(), outputTip)).as("outside the body")
                .isEmpty();

        CanvasMouseEvent event = new CanvasMouseEvent(outputTip, new GridPoint(24, 22), new Point2D.Double(),
                CanvasMouseEvent.PRIMARY, false, false, 2);

        assertThat(delegate.pressed(view.canvas(), event)).isEqualTo(SelectToolDelegate.Press.IGNORED);
        assertThat(shown).isNull();
    }

    @Test
    void choosingAUserComponentNobodyPlacesSendsNoPressToTheSelectTool() {
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        view.palette().setUserComponents(List.of(bundle), List.of());
        view.canvas().tool().mousePressed(view.canvas(), at(10, 20, 2));
        List<CanvasMouseEvent> presses = new ArrayList<>();
        ((SelectTool) view.canvas().defaultTool()).addDelegate((context, event) -> {
            presses.add(event);
            return SelectToolDelegate.Press.IGNORED;
        });

        shown.setQuery("INVERSOR");
        shown.acceptSelection();

        assertThat(view.canvas().tool()).isInstanceOf(SelectTool.class);
        assertThat(presses).isEmpty();
        assertThat(document.document().components()).isEmpty();
    }

    @Test
    void existingComponentsLabelsBusValuesWiresAndFrameControlsKeepTheirDoubleClick() {
        Component gate = document.registry().require("and3").newComponent(new ElementId("gate"),
                new GridPoint(20, 20), Rotation.R0, Map.of(), "Etiqueta larga");
        Component bus = document.registry().require(StandardComponentTypes.INPUT_PIN)
                .newComponent(new ElementId("bus"), new GridPoint(40, 40), Rotation.R0, Map.of("width", 8), "");
        document.execute(new AddElementsCommand(List.of(gate, bus,
                new Wire(new ElementId("wire"), new GridPoint(0, 60), new GridPoint(20, 60)),
                new Frame(new ElementId("frame"), new GridPoint(50, 50), 30, 20, "Marco", FrameTint.NONE, false))));
        SelectToolDelegate delegate = ((SelectTool) view.canvas().defaultTool()).delegates().getLast();
        Rectangle2D label = LabelLayout.componentLabel(document.registry().typeOf(gate), gate).orElseThrow().bounds();
        Rectangle2D value = InterfacePinPainter.valueBox(document.registry().typeOf(bus), bus).orElseThrow();
        for (Point2D point : List.of(new Point2D.Double(21, 21), new Point2D.Double(10, 60),
                new Point2D.Double(60, 51),
                new Point2D.Double(label.getCenterX() / GridPoint.PIXELS_PER_UNIT,
                        label.getCenterY() / GridPoint.PIXELS_PER_UNIT),
                new Point2D.Double(value.getCenterX() / GridPoint.PIXELS_PER_UNIT,
                        value.getCenterY() / GridPoint.PIXELS_PER_UNIT))) {
            CanvasMouseEvent event = new CanvasMouseEvent(point, GridPoint.ORIGIN, new Point2D.Double(),
                    CanvasMouseEvent.PRIMARY, false, false, 2);
            assertThat(delegate.pressed(view.canvas(), event)).isEqualTo(SelectToolDelegate.Press.IGNORED);
        }
        assertThat(shown).isNull();
    }

    @Test
    void panesCreatedLaterGetInsertionAndRemovingOneClosesItsPopup() {
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas second = view.panes().getLast();
        assertThat(((SelectTool) second.defaultTool()).delegates()).hasSize(1);
        second.tool().mousePressed(second, at(10, 10, 2));
        assertThat(shown).isNotNull();
        view.canvasPanes().removeDivider(Orientation.VERTICAL);
        assertThat(closed).isEqualTo(1);
        assertThat(((SelectTool) second.defaultTool()).delegates()).isEmpty();
        features.dispose();
        assertThat(closed).isEqualTo(1);
        for (CircuitCanvas pane : view.panes()) {
            assertThat(((SelectTool) pane.defaultTool()).delegates()).isEmpty();
        }
    }

    @Test
    void theSearchFieldKeepsTheFocusWhileArrowsAndEnterChooseAndEscapeCloses() {
        showFavorites();
        view.services().preferences().setPaletteFavorites(List.of("and3", "led"));
        view.canvas().tool().mousePressed(view.canvas(), at(10, 10, 2));
        JTextField field = shown.searchField();
        Object down = field.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        field.getActionMap().get(down).actionPerformed(new ActionEvent(field, 0, ""));
        field.postActionEvent();
        assertThat(document.document().components()).singleElement()
                .extracting(component -> component.typeId()).isEqualTo("led");
        view.canvas().tool().mousePressed(view.canvas(), at(100, 100, 2));
        JTextField nextField = shown.searchField();
        Object escape = nextField.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        nextField.getActionMap().get(escape).actionPerformed(new ActionEvent(nextField, 0, ""));
        assertThat(closed).isEqualTo(2);
        assertThat(document.document().components()).hasSize(1);
    }

    @Test
    void noMatchAndCancelDoNothingAndDisposeRemovesTheDelegateAndLeavesThePreferencesAlone() {
        int listeners = view.services().preferences().listenerCount();
        view.canvas().tool().mousePressed(view.canvas(), at(10, 10, 2));
        shown.setQuery("no-component-has-this-name");
        shown.acceptSelection();
        assertThat(document.document().components()).isEmpty();
        shown.cancel();
        assertThat(closed).isEqualTo(1);
        features.dispose();
        assertThat(view.shortcutActions()).doesNotContainKey("edit.quickInsert");
        assertThat(((SelectTool) view.canvas().defaultTool()).delegates()).isEmpty();
        assertThat(view.services().preferences().listenerCount()).isEqualTo(listeners);
    }
}
