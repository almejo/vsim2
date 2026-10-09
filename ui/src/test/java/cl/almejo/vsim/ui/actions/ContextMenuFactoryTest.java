package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ContextMenuFactoryTest {

    private static final Point2D CLICK = new Point2D.Double(2.5, 3.25);

    private OpenDocument openDocument;
    private EditorView view;
    /** Only the contributors a test adds: the view's own (clipboard) contributor is not under test here. */
    private final List<ContextMenuContributor> contributors = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
        });
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private JPopupMenu contextMenu(Optional<cl.almejo.vsim.core.document.DocumentElement> target, Point2D gridPosition) {
        return ContextMenuFactory.create(target, view.actions(), view.canvas().selection(), gridPosition, contributors);
    }

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    @Test
    void componentMenuOffersConfigureRotateAndDelete() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());
        assertThat(texts(contextMenu(Optional.of(gate), CLICK)))
                .containsExactly("Configurar…", "Rotar ↻", "Rotar ↺", "---", "Borrar");
    }

    @Test
    void wireMenuOffersDeleteWire() {
        Wire wire = new Wire(new ElementId("w9"), GridPoint.ORIGIN, new GridPoint(3, 0));
        assertThat(texts(contextMenu(Optional.of(wire), CLICK))).containsExactly("Borrar cable");
    }

    @Test
    void emptyCanvasMenuOffersCenterView() {
        assertThat(texts(contextMenu(Optional.empty(), CLICK))).containsExactly("Centrar circuito");
    }

    @Test
    void contributorsAddTheirGroupsInOrderSeparatedFromTheRest() {
        List<Selection> seen = new ArrayList<>();
        List<Point2D> positions = new ArrayList<>();
        contributors.add((menu, target, selection, gridPosition) -> {
            seen.add(selection);
            positions.add(gridPosition);
            menu.add(new JMenuItem("Uno"));
        });
        contributors.add((menu, target, selection, gridPosition) -> { });
        contributors.add((menu, target, selection, gridPosition) -> {
            if (target.isEmpty()) {
                menu.add(new JMenuItem("Dos"));
                menu.add(new JMenuItem("Tres"));
            }
        });
        assertThat(texts(contextMenu(Optional.empty(), CLICK)))
                .containsExactly("Centrar circuito", "---", "Uno", "---", "Dos", "Tres");
        Wire wire = new Wire(new ElementId("w9"), GridPoint.ORIGIN, new GridPoint(3, 0));
        assertThat(texts(contextMenu(Optional.of(wire), CLICK))).containsExactly("Borrar cable", "---", "Uno");
        assertThat(seen).hasSize(2).allMatch(selection -> selection == view.canvas().selection());
        assertThat(positions).containsExactly(CLICK, CLICK);
    }

    @Test
    void targetInAMultiSelectionSkipsTheSingleElementEntries() {
        Component first = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 10, 0, Map.of());
        view.canvas().selection().set(List.of(first.id(), second.id()));
        assertThat(texts(contextMenu(Optional.of(first), CLICK))).isEmpty();
        contributors.add((menu, target, selection, gridPosition) -> menu.add(new JMenuItem("Uno")));
        assertThat(texts(contextMenu(Optional.of(first), CLICK))).containsExactly("Uno");
        Component outsider = place(openDocument, StandardComponentTypes.NOT, 20, 0, Map.of());
        assertThat(texts(contextMenu(Optional.of(outsider), CLICK)))
                .containsExactly("Configurar…", "Rotar ↻", "Rotar ↺", "---", "Borrar", "---", "Uno");
    }

    @Test
    void contributorsCannotAddAnActionThatIsAlreadyInTheMenu() {
        contributors.add((menu, target, selection, gridPosition) -> {
            menu.add(new JMenuItem(view.actions().delete()));
            menu.add(new JMenuItem(view.actions().rotateClockwise()));
            menu.add(new JMenuItem(view.actions().delete()));
        });
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());
        assertThat(texts(contextMenu(Optional.of(gate), CLICK)))
                .containsExactly("Configurar…", "Rotar ↻", "Rotar ↺", "---", "Borrar");
        Component other = place(openDocument, StandardComponentTypes.NOT, 10, 0, Map.of());
        view.canvas().selection().set(List.of(gate.id(), other.id()));
        assertThat(texts(contextMenu(Optional.of(gate), CLICK))).containsExactly("Borrar", "Rotar ↻");
    }

    @Test
    void theRealViewMenuIncludesTheClipboardAndLabelContributors() {
        assertThat(texts(view.contextMenu(Optional.empty(), CLICK)))
                .containsExactly("Centrar circuito", "---", "Pegar", "Seleccionar todo", "---", "Agregar texto aquí");
    }

    @Test
    void theRealComponentMenuOffersClipboardAndLabelEntries() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        view.canvas().selection().select(gate.id());
        assertThat(texts(view.contextMenu(Optional.of(gate), CLICK)))
                .containsExactly("Configurar…", "Rotar ↻", "Rotar ↺", "---", "Borrar",
                        "---", "Cortar", "Copiar", "Duplicar", "---", "Etiqueta…");
    }

    @Test
    void theRealWireMenuOffersDeleteSegment() {
        Wire wire = new Wire(new ElementId("w9"), GridPoint.ORIGIN, new GridPoint(3, 0));
        assertThat(texts(view.contextMenu(Optional.of(wire), CLICK)))
                .containsExactly("Borrar cable", "---", "Borrar tramo");
    }
}
