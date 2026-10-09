package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class LabelMenuContributorTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private final FakePrompt prompt = new FakePrompt();
    private Point2D clickedAt = new Point2D.Double(7.8, 0.2);
    private LabelMenuContributor contributor;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        contributor = new LabelMenuContributor(new LabelActions(openDocument, selection, null, prompt));
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private JPopupMenu menuFor(Optional<DocumentElement> target) {
        JPopupMenu menu = new JPopupMenu();
        contributor.contribute(menu, target, selection, clickedAt);
        return menu;
    }

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private static void choose(JPopupMenu menu, int index) {
        JMenuItem item = (JMenuItem) menu.getComponent(index);
        item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void componentGetsLabelItem() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        selection.select(gate.id());
        JPopupMenu menu = menuFor(Optional.of(gate));
        assertThat(texts(menu)).containsExactly("Etiqueta…");
        prompt.answer("U9");
        choose(menu, 0);
        assertThat(openDocument.document().component(gate.id()).orElseThrow().label()).isEqualTo("U9");
    }

    @Test
    void freeTextGetsEditTextItem() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "nota");
        openDocument.execute(new AddElementsCommand(List.of(label)));
        selection.select(label.id());
        assertThat(texts(menuFor(Optional.of(label)))).containsExactly("Editar texto…");
    }

    @Test
    void multiSelectionGetsNoLabelItems() {
        Component first = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 10, 0, Map.of());
        selection.set(List.of(first.id(), second.id()));
        assertThat(texts(menuFor(Optional.of(first)))).isEmpty();
    }

    @Test
    void wireGetsRemoveSegmentAtTheClickedPoint() {
        Wire main = new Wire(openDocument.document().newWireId(), new GridPoint(0, 0), new GridPoint(10, 0));
        openDocument.execute(new AddWireCommand(main));
        openDocument.execute(new AddWireCommand(new Wire(openDocument.document().newWireId(),
                new GridPoint(5, 0), new GridPoint(5, 4))));
        selection.select(main.id());
        JPopupMenu menu = menuFor(Optional.of(main));
        assertThat(texts(menu)).containsExactly("Borrar tramo");
        choose(menu, 0);
        assertThat(openDocument.document().wire(main.id()).orElseThrow().end()).isEqualTo(new GridPoint(5, 0));
    }

    @Test
    void emptyCanvasGetsAddTextHere() {
        clickedAt = new Point2D.Double(4.4, 6.6);
        JPopupMenu menu = menuFor(Optional.empty());
        assertThat(texts(menu)).containsExactly("Agregar texto aquí");
        prompt.answer("nota");
        choose(menu, 0);
        assertThat(openDocument.document().labels()).singleElement()
                .satisfies(label -> assertThat(label.position()).isEqualTo(new GridPoint(4, 7)));
    }
}
