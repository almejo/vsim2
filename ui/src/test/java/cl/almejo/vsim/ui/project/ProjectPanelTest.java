package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The Proyecto panel: the circuit's components level by level; entering, highlighting, the menu. */
class ProjectPanelTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<String> calls = new ArrayList<>();
    private ProjectPanel panel;
    private String instanceId;

    private final ProjectPanel.Actions actions = new ProjectPanel.Actions() {
        @Override
        public void enter(ProjectRows.Row row) {
            calls.add("enter " + row.instance().orElseThrow().path());
        }

        @Override
        public void seePiece(ProjectRows.Row row) {
            calls.add("see " + row.instance().orElseThrow().path());
        }

        @Override
        public void changeShape(ProjectRows.Row row) {
            calls.add("shape " + row.instance().orElseThrow().path());
        }

        @Override
        public void properties(ProjectRows.Row row) {
            calls.add("properties " + row.instance().orElseThrow().path());
        }

        @Override
        public boolean editsHere(ProjectRows.Row row) {
            return true;
        }

        @Override
        public boolean canSee(ProjectRows.Row row) {
            return true;
        }
    };

    @BeforeEach
    void create() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        instanceId = place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, Map.of())
                .id().value();
        panel = new ProjectPanel(openDocument, actions);
        panel.setSize(240, 200);
        panel.doLayout();
        panel.rowList().setSize(240, 200);
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private MouseEvent click(int rowIndex, int clickCount, boolean popup) {
        Rectangle cell = panel.rowList().getCellBounds(rowIndex, rowIndex);
        return new MouseEvent(panel.rowList(), popup ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), popup ? MouseEvent.BUTTON3_DOWN_MASK : MouseEvent.BUTTON1_DOWN_MASK,
                cell.x + 5, cell.y + 5, clickCount, popup, popup ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1);
    }

    @Test
    void showsTheDocumentThenItsInstances() {
        assertThat(panel.rowList().getModel().getSize()).isEqualTo(2);
        assertThat(panel.rows().get(1).right()).isEqualTo(instanceId);
        assertThat(panel.rows().get(0).right()).isEqualTo(Messages.format("project.pieces", 1));
    }

    @Test
    void doubleClickEnters() {
        panel.rowList().dispatchEvent(click(1, 2, false));

        assertThat(calls).containsExactly("enter " + instanceId);
    }

    @Test
    void doubleClickOnTheDocumentRowDoesNothing() {
        panel.rowList().dispatchEvent(click(0, 2, false));

        assertThat(calls).isEmpty();
    }

    @Test
    void enterEntersTheChosenRow() {
        panel.rowList().setSelectedIndex(1);

        panel.rowList().getActionMap().get("project.enter").actionPerformed(
                new ActionEvent(panel.rowList(), ActionEvent.ACTION_PERFORMED, "enter"));

        assertThat(calls).containsExactly("enter " + instanceId);
    }

    @Test
    void theMenuOfAnInstanceOffersEnterSeeChangeShapeAndProperties() {
        JPopupMenu menu = panel.menuFor(panel.rows().get(1)).orElseThrow();

        assertThat(menu.getComponents()).filteredOn(JMenuItem.class::isInstance).extracting(
                component -> ((JMenuItem) component).getText()).containsExactly(Messages.text("action.enter"),
                Messages.text("project.seePiece"), Messages.text("shape.change"),
                Messages.text("project.properties"));
        ((JMenuItem) menu.getComponent(0)).doClick();
        assertThat(calls).containsExactly("enter " + instanceId);
    }

    @Test
    void theDocumentRowHasNoMenu() {
        assertThat(panel.menuFor(panel.rows().get(0))).isEmpty();
    }

    @Test
    void highlightsTheActiveContext() {
        OpenDocument body = openDocument.openBody(ExampleDefinitions.INVERTER, Optional.of(instanceId));
        ProjectPanel inside = new ProjectPanel(body, actions);
        try {
            assertThat(inside.highlightedRow()).hasValueSatisfying(row ->
                    assertThat(row.instance().orElseThrow().path()).isEqualTo(instanceId));

            body.setContextPath(Optional.empty());
            assertThat(inside.highlightedRow()).isEmpty();

            body.setContextPath(Optional.of(instanceId));
            assertThat(inside.highlightedRow()).isPresent();
        } finally {
            inside.dispose();
            body.close();
        }
    }

    @Test
    void followsTheDocumentWhenAnInstanceIsAdded() {
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 12, -1, Map.of());

        assertThat(panel.rows()).hasSize(3);
    }

    @Test
    void disposingStopsListening() {
        int before = openDocument.changeListenerCount();
        ProjectPanel another = new ProjectPanel(openDocument, actions);
        assertThat(openDocument.changeListenerCount()).isGreaterThan(before);

        another.dispose();

        assertThat(openDocument.changeListenerCount()).isEqualTo(before);
    }

    @Test
    void aRowFarFromAnyCellIsIgnored() {
        Point far = new Point(5, 190);
        panel.rowList().dispatchEvent(new MouseEvent(panel.rowList(), MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, far.x, far.y, 2, false, MouseEvent.BUTTON1));

        assertThat(calls).isEmpty();
    }
}
