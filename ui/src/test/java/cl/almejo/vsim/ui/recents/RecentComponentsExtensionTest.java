package cl.almejo.vsim.ui.recents;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.library.PlaceDefinitionTool;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PaletteDragTransferable;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.TransferHandler;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

class RecentComponentsExtensionTest {

    private final RecentComponentsExtension extension = new RecentComponentsExtension();
    private final List<EditorView> views = new ArrayList<>();
    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = newView();
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        document.close();
    }

    private EditorView newView() {
        EditorView created = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        created.canvas().setSize(800, 600);
        views.add(created);
        return created;
    }

    private CanvasMouseEvent at(EditorView target, int x, int y) {
        return new CanvasMouseEvent(new Point2D.Double(x, y), new GridPoint(x, y),
                target.canvas().viewport().gridToScreen(x, y), CanvasMouseEvent.PRIMARY, false, false, 1);
    }

    private void placeWithAClick(EditorView target, String typeId, int x, int y) {
        target.palette().choose(typeId);
        target.canvas().tool().mouseMoved(target.canvas(), at(target, x, y));
        target.canvas().tool().mousePressed(target.canvas(), at(target, x, y));
    }

    private static List<String> recentIds(EditorView target) {
        return target.palette().recentEntries().stream().map(PaletteEntry::id).toList();
    }

    @Test
    void placingWithAClickOrADropPutsTheComponentOnTop() {
        extension.install(view);

        placeWithAClick(view, "xor", 10, 10);
        view.canvas().dropComponent(PaletteTransfer.encode("led"), new Point(300, 300));

        assertThat(document.document().components()).hasSize(2);
        assertThat(extension.recents().typeIds()).containsExactly("led", "xor");
        assertThat(recentIds(view)).containsExactly("led", "xor");
    }

    @Test
    void placingAUserComponentCountsByItsSubcircuitKey() {
        extension.install(view);
        view.palette().setUserComponents(List.of(new DefinitionBundle(ExampleDefinitions.inverter(), List.of())),
                List.of());
        view.palette().setUserComponentChooser(component ->
                view.setToolOnEveryPane(() -> new PlaceDefinitionTool(component.bundle())));

        placeWithAClick(view, "subcircuit:inverter", 10, 10);

        assertThat(extension.recents().typeIds()).containsExactly("subcircuit:inverter");
    }

    @Test
    void placingNandWithAClickPlacesANegatedAndInOneStepAndCountsTheShortcut() {
        extension.install(view);

        placeWithAClick(view, "nand", 10, 10);

        assertThat(document.document().components()).singleElement().satisfies(component -> {
            assertThat(component.typeId()).isEqualTo(StandardComponentTypes.AND);
            assertThat(component.parameters()).containsEntry(StandardComponentTypes.NEGATE, true);
        });
        assertThat(extension.recents().typeIds()).containsExactly("nand");
        assertThat(recentIds(view)).containsExactly("nand");
        document.history().undo();
        assertThat(document.document().components()).isEmpty();
    }

    @Test
    void droppingNorPlacesANegatedOrAndCountsTheShortcut() {
        extension.install(view);

        assertThat(view.canvas().dropComponent(PaletteTransfer.encode("nor"), new Point(300, 300))).isTrue();

        assertThat(document.document().components()).singleElement().satisfies(component -> {
            assertThat(component.typeId()).isEqualTo(StandardComponentTypes.OR);
            assertThat(component.parameters()).containsEntry(StandardComponentTypes.NEGATE, true);
        });
        assertThat(extension.recents().typeIds()).containsExactly("nor");
    }

    @Test
    void shiftKeepsPlacingTheShortcutWithItsParameters() {
        extension.install(view);
        view.palette().choose("not");
        CanvasMouseEvent first = at(view, 10, 10);
        CanvasMouseEvent shifted = new CanvasMouseEvent(first.gridPosition(), first.snapped(),
                first.screenPosition(), CanvasMouseEvent.PRIMARY, true, false, 1);
        view.canvas().tool().mouseMoved(view.canvas(), shifted);
        view.canvas().tool().mousePressed(view.canvas(), shifted);
        view.canvas().tool().mouseMoved(view.canvas(), at(view, 30, 30));
        view.canvas().tool().mousePressed(view.canvas(), at(view, 30, 30));

        assertThat(document.document().components()).hasSize(2).allSatisfy(component -> {
            assertThat(component.typeId()).isEqualTo(StandardComponentTypes.BUFFER);
            assertThat(component.parameters()).containsEntry(StandardComponentTypes.NEGATE, true);
        });
        assertThat(extension.recents().typeIds()).containsExactly("not");
    }

    @Test
    void choosingAndCancellingWithEscapeOrDuplicatingDoesNotCount() {
        extension.install(view);
        view.palette().choose("led");
        view.canvas().tool().keyPressed(view.canvas(), KeyEvent.VK_ESCAPE, false);
        Component gate = document.registry().require("xor").newComponent(new ElementId("gate"),
                new GridPoint(20, 20), Rotation.R0, Map.of(), "");
        document.execute(new AddElementsCommand(List.of(gate)));
        view.selection().select(gate.id());

        view.selectionActions().duplicate().actionPerformed(new ActionEvent(view, 0, ""));
        view.canvas().tool().mousePressed(view.canvas(), at(view, 50, 50)); // a click fixes the floating copy
        view.canvas().tool().mouseReleased(view.canvas(), at(view, 50, 50));

        assertThat(document.document().components()).hasSize(2);
        assertThat(extension.recents().typeIds()).isEmpty();
        assertThat(view.palette().visibleSections()).doesNotContain("Recientes");
    }

    @Test
    void twoWindowsShareTheListAndADisposedOneLetsGo() {
        Runnable uninstallFirst = extension.install(view);
        EditorView second = newView();
        int listeners = extension.recents().listenerCount();
        Runnable uninstallSecond = extension.install(second);
        assertThat(extension.recents().listenerCount()).isEqualTo(listeners + 1);

        placeWithAClick(view, "xor", 10, 10);
        assertThat(recentIds(second)).containsExactly("xor");
        placeWithAClick(second, "led", 40, 40);
        assertThat(recentIds(view)).containsExactly("led", "xor");

        uninstallSecond.run();
        assertThat(extension.recents().listenerCount()).isEqualTo(listeners);
        placeWithAClick(second, "xor", 60, 60);
        assertThat(extension.recents().typeIds()).containsExactly("led", "xor");
        assertThat(recentIds(view)).containsExactly("led", "xor");
        uninstallFirst.run();
        assertThat(extension.recents().listenerCount()).isZero();
    }

    @Test
    void aPaneSplitLaterAlsoCounts() {
        extension.install(view);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);

        CircuitCanvas pane = view.panes().getLast();
        view.palette().choose("or");
        pane.tool().mouseMoved(pane, at(view, 5, 5));
        pane.tool().mousePressed(pane, at(view, 5, 5));

        assertThat(extension.recents().typeIds()).containsExactly("or");
        assertThat(view.palette().recentEntries()).extracting(PaletteEntry::type)
                .extracting(type -> type.id()).containsExactly("or");
    }

    private Transferable inverterFragment(boolean fromPalette) {
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        Component instance = SubcircuitTypes.typeOf(bundle.root())
                .newComponent(new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        String json = new FragmentFormat(document.registry()).write(
                new CircuitFragment(List.of(instance), List.of(), List.of(), bundle.all()));
        return fromPalette ? new PaletteDragTransferable(json, "subcircuit:inverter")
                : new FragmentTransferable(json);
    }

    private void dropAndFix(Transferable transferable) {
        TransferHandler handler = view.canvas().getTransferHandler();
        assertThat(handler.importData(new TransferHandler.TransferSupport(view.canvas(), transferable))).isTrue();
        view.canvas().tool().mouseMoved(view.canvas(), at(view, 30, 30));
        view.canvas().tool().mousePressed(view.canvas(), at(view, 30, 30));
        view.canvas().tool().mouseReleased(view.canvas(), at(view, 30, 30));
    }

    @Test
    void droppingAUserComponentDraggedFromThePaletteCounts() {
        extension.install(view);

        dropAndFix(inverterFragment(true));

        assertThat(document.document().components()).hasSize(1);
        assertThat(extension.recents().typeIds()).containsExactly("subcircuit:inverter");
    }

    @Test
    void pastingTheSameFragmentDoesNotCount() {
        extension.install(view);

        dropAndFix(inverterFragment(false));

        assertThat(document.document().components()).hasSize(1);
        assertThat(extension.recents().typeIds()).isEmpty();
    }

    @Test
    void aRecentOrFavouriteUserComponentPlacesThePlainDefinitionEvenWhenAPieceHasItsOwnShape() {
        extension.install(view);
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        view.palette().setUserComponents(List.of(bundle), List.of());
        view.palette().setUserComponentChooser(component ->
                view.setToolOnEveryPane(() -> new PlaceDefinitionTool(component.bundle())));
        view.palette().setFavoriteToggle(typeId -> { });
        placeWithAClick(view, "subcircuit:inverter", 10, 10);
        giveEveryPieceItsOwnShape(bundle);

        view.palette().chooseEntry(view.palette().recentEntries().getFirst());
        view.canvas().tool().mouseMoved(view.canvas(), at(view, 40, 10));
        view.canvas().tool().mousePressed(view.canvas(), at(view, 40, 10));
        view.palette().setFavorites(List.of("subcircuit:inverter"));
        view.palette().chooseEntry(view.palette().favoriteEntries().getFirst());
        view.canvas().tool().mouseMoved(view.canvas(), at(view, 70, 10));
        view.canvas().tool().mousePressed(view.canvas(), at(view, 70, 10));

        assertThat(document.document().components()).hasSize(3);
        assertThat(document.document().components()).filteredOn(component -> component.appearance().isEmpty())
                .hasSize(2);
    }

    private void giveEveryPieceItsOwnShape(DefinitionBundle bundle) {
        ComponentAppearance ownShape = SymbolDraft.of(ComponentAppearance.of(bundle.root()), bundle.root().pinout(),
                false).applyTemplate(SymbolTemplates.Template.ALU).appearance();
        document.document().components().forEach(component ->
                document.document().replace(component.withAppearance(Optional.of(ownShape))));
    }
}
