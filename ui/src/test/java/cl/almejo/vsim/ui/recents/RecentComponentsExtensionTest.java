package cl.almejo.vsim.ui.recents;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.PlaceDefinitionTool;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecentComponentsExtensionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final RecentComponentsExtension extension = new RecentComponentsExtension();
    private final List<EditorView> views = new ArrayList<>();
    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
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
        return target.palette().recentEntries().stream().map(entry -> entry.type().id()).toList();
    }

    @Test
    void placingWithAClickOrADropPutsTheComponentOnTop() {
        extension.install(view);

        placeWithAClick(view, "and3", 10, 10);
        view.canvas().dropComponent(PaletteTransfer.encode("led"), new Point(300, 300));

        assertThat(document.document().components()).hasSize(2);
        assertThat(extension.recents().typeIds()).containsExactly("led", "and3");
        assertThat(recentIds(view)).containsExactly("led", "and3");
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
    void choosingAndCancellingWithEscapeOrDuplicatingDoesNotCount() {
        extension.install(view);
        view.palette().choose("led");
        view.canvas().tool().keyPressed(view.canvas(), KeyEvent.VK_ESCAPE, false);
        Component gate = document.registry().require("and3").newComponent(new ElementId("gate"),
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

        placeWithAClick(view, "and3", 10, 10);
        assertThat(recentIds(second)).containsExactly("and3");
        placeWithAClick(second, "led", 40, 40);
        assertThat(recentIds(view)).containsExactly("led", "and3");

        uninstallSecond.run();
        assertThat(extension.recents().listenerCount()).isEqualTo(listeners);
        placeWithAClick(second, "xor2", 60, 60);
        assertThat(extension.recents().typeIds()).containsExactly("led", "and3");
        assertThat(recentIds(view)).containsExactly("led", "and3");
        uninstallFirst.run();
        assertThat(extension.recents().listenerCount()).isZero();
    }

    @Test
    void aPaneSplitLaterAlsoCounts() {
        extension.install(view);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);

        CircuitCanvas pane = view.panes().getLast();
        view.palette().choose("or2");
        pane.tool().mouseMoved(pane, at(view, 5, 5));
        pane.tool().mousePressed(pane, at(view, 5, 5));

        assertThat(extension.recents().typeIds()).containsExactly("or2");
        assertThat(view.palette().recentEntries()).extracting(PaletteEntry::type)
                .extracting(type -> type.id()).containsExactly("or2");
    }
}
