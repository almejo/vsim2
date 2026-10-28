package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
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
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class WaveformMenuContributorTest {

    private final List<ProbeActions.AddResult> results = new ArrayList<>();
    private final Selection selection = new Selection();
    private OpenDocument openDocument;
    private WaveformMenuContributor contributor;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        contributor = new WaveformMenuContributor(new ProbeActions(openDocument), results::add);
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private JPopupMenu menuFor(Optional<DocumentElement> target) {
        JPopupMenu menu = new JPopupMenu();
        contributor.contribute(menu, target, selection, new Point2D.Double(1, 1));
        return menu;
    }

    private static void perform(JPopupMenu menu) {
        JMenuItem item = (JMenuItem) menu.getComponent(0);
        item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void aComponentOffersItsPinsAndTheResultGoesToTheCallback() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        JPopupMenu menu = menuFor(Optional.of(toggle));
        assertThat(texts(menu)).containsExactly("Ver pines en formas de onda");

        perform(menu);

        assertThat(openDocument.document().probes()).hasSize(1);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.addedIds()).hasSize(1));
    }

    @Test
    void aWireOffersItsNet() {
        place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(4, 1), new GridPoint(8, 1));
        openDocument.execute(new AddElementsCommand(List.of(wire)));
        JPopupMenu menu = menuFor(Optional.of(wire));
        assertThat(texts(menu)).containsExactly("Ver en formas de onda");
        perform(menu);
        assertThat(openDocument.document().probes()).hasSize(1);
    }

    @Test
    void aMultiSelectionOffersEverySelectedElement() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));
        selection.set(List.of(toggle.id(), gate.id()));

        JPopupMenu menu = menuFor(Optional.of(gate));
        assertThat(texts(menu)).containsExactly("Ver en formas de onda");
        perform(menu);

        assertThat(openDocument.document().probes()).extracting(probe -> ProbeNames.displayName(probe,
                openDocument.document(), openDocument.netlist(), openDocument.registry()))
                .containsExactly("c1.out", "c2.out");
    }

    @Test
    void labelsAndTheEmptyCanvasOfferNothing() {
        assertThat(texts(menuFor(Optional.of(new TextLabel(new ElementId("l1"), GridPoint.ORIGIN, "nota")))))
                .isEmpty();
        assertThat(texts(menuFor(Optional.empty()))).isEmpty();
    }
}
