package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KarnaughWizardTest {

    private KarnaughModel model;
    private KarnaughWizard wizard;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        model = new KarnaughModel();
        wizard = new KarnaughWizard(model, KarnaughCost.GENERATOR, new FakeCreationTarget(), Background.inline());
    }

    private static void press(JTable table, int row, int column) {
        Rectangle cell = layOut(table).getCellRect(row, column, false);
        mouse(table, MouseEvent.MOUSE_PRESSED, new Point((int) cell.getCenterX(), (int) cell.getCenterY()));
    }

    private static JTable layOut(JTable table) {
        table.setSize(table.getPreferredSize());
        table.doLayout();
        return table;
    }

    /**
     * Hands a mouse event to the wizard's own listeners (dispatching it would reach the look and feel's, which need
     * a screen).
     */
    private static void mouse(JComponent component, int id, Point point) {
        MouseEvent event = new MouseEvent(component, id, System.currentTimeMillis(), 0, point.x, point.y, 1, false,
                MouseEvent.BUTTON1);
        for (MouseListener listener : component.getMouseListeners()) {
            if (listener.getClass().getName().startsWith("cl.almejo")) {
                switch (id) {
                    case MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event);
                    case MouseEvent.MOUSE_EXITED -> listener.mouseExited(event);
                    case MouseEvent.MOUSE_ENTERED -> listener.mouseEntered(event);
                    default -> { }
                }
            }
        }
        for (MouseMotionListener listener : component.getMouseMotionListeners()) {
            if (listener.getClass().getName().startsWith("cl.almejo") && id == MouseEvent.MOUSE_MOVED) {
                listener.mouseMoved(event);
            }
        }
    }

    private static void key(JTable table, int keyCode) {
        Object name = table.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, 0));
        Action action = table.getActionMap().get(name);
        action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void startsOnTheTableWithTheFiveStepsInTheBar() {
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.TABLE);
        assertThat(wizard.stepTitles()).containsExactly("1 Tabla", "2 Mapa", "3 Grupos", "4 Expresión", "5 Crear");
        assertThat(wizard.backAction().isEnabled()).isFalse();
        assertThat(wizard.nextAction().isEnabled()).isTrue();
    }

    @Test
    void theSpinnersResizeTheTableAndKeepTheValues() {
        TablePanel table = wizard.tablePanel();
        press(table.table(), 5, table.table().getColumnCount() - 1);
        table.inputCountSpinner().setValue(4);
        assertThat(table.table().getRowCount()).isEqualTo(16);
        assertThat(table.table().getColumnCount()).isEqualTo(1 + 4 + 1);
        assertThat(table.table().getColumnName(4)).isEqualTo("D");
        assertThat(model.value(0, 0b1010)).isEqualTo(LogicValue.ONE);
        table.outputCountSpinner().setValue(2);
        assertThat(table.table().getColumnCount()).isEqualTo(1 + 4 + 2);
        assertThat(table.table().getColumnName(6)).isEqualTo("G");
        assertThat(table.outputNameFields()).hasSize(2);
    }

    @Test
    void clickingAnOutputCellCyclesItAndInputCellsDoNot() {
        JTable table = wizard.tablePanel().table();
        int output = table.getColumnCount() - 1;
        press(table, 2, output);
        assertThat(table.getValueAt(2, output)).isEqualTo("1");
        press(table, 2, output);
        assertThat(table.getValueAt(2, output)).isEqualTo("X");
        press(table, 2, output);
        assertThat(table.getValueAt(2, output)).isEqualTo("0");
        press(table, 2, 1);
        assertThat(model.value(0, 2)).isEqualTo(LogicValue.ZERO);
    }

    @Test
    void theKeyboardWritesZeroOneAndXAndSpaceCycles() {
        JTable table = wizard.tablePanel().table();
        int output = table.getColumnCount() - 1;
        table.changeSelection(3, output, false, false);
        key(table, KeyEvent.VK_1);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ONE);
        key(table, KeyEvent.VK_X);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.DONT_CARE);
        key(table, KeyEvent.VK_0);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ZERO);
        key(table, KeyEvent.VK_SPACE);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ONE);
        assertThat(table.getSelectedRow()).as("editing keeps the cell selected").isEqualTo(3);
        key(table, KeyEvent.VK_NUMPAD0);
        assertThat(model.value(0, 3)).isEqualTo(LogicValue.ZERO);
    }

    @Test
    void aBadNameBlocksSiguienteAndTheLaterStepsWithTheReason() {
        wizard.tablePanel().inputNameFields().get(1).setText("");
        assertThat(wizard.nextAction().isEnabled()).isFalse();
        assertThat(wizard.reasonText()).isEqualTo("Escribe un nombre para la entrada 2.");
        assertThat(wizard.goTo(KarnaughWizard.Step.GROUPS)).isFalse();
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.TABLE);
        assertThat(wizard.stepButton(KarnaughWizard.Step.MAP).isEnabled()).isFalse();

        wizard.tablePanel().inputNameFields().get(1).setText("Cin");
        assertThat(wizard.nextAction().isEnabled()).isTrue();
        assertThat(wizard.reasonText()).isEmpty();
        assertThat(wizard.tablePanel().table().getColumnName(2)).isEqualTo("Cin");
    }

    @Test
    void hoveringATableRowHighlightsItsMapCellAndViceVersa() {
        wizard.goTo(KarnaughWizard.Step.MAP);
        MapPanel map = wizard.mapPanel();
        JTable table = layOut(map.table());
        Rectangle cell = table.getCellRect(6, 1, false);
        mouse(table, MouseEvent.MOUSE_MOVED, new Point((int) cell.getCenterX(), (int) cell.getCenterY()));
        assertThat(wizard.hover().row()).isEqualTo(6);
        KarnaughMapView view = map.mapView(0);
        assertThat(view.highlightedRow()).hasValue(6);

        view.setSize(400, 300);
        MapGeometry geometry = view.geometry();
        Rectangle mapCell = geometry.cell(0, 1); // A = 0, BC = 01: row 1
        mouse(view, MouseEvent.MOUSE_MOVED, new Point((int) mapCell.getCenterX(), (int) mapCell.getCenterY()));
        assertThat(wizard.hover().row()).isEqualTo(1);
        assertThat(map.highlightedTableRow()).hasValue(1);
        mouse(view, MouseEvent.MOUSE_EXITED, new Point(0, 0));
        assertThat(wizard.hover().row()).isEqualTo(-1);
    }

    @Test
    void goingBackAndEditingRecomputesTheGroups() {
        model.setValue(0, 6, LogicValue.ONE);
        model.setValue(0, 7, LogicValue.ONE);
        assertThat(wizard.goTo(KarnaughWizard.Step.GROUPS)).isTrue();
        assertThat(wizard.groupsPanel().legendTexts()).containsExactly("① AB");

        wizard.back();
        wizard.back();
        assertThat(wizard.step()).isEqualTo(KarnaughWizard.Step.TABLE);
        press(wizard.tablePanel().table(), 5, wizard.tablePanel().table().getColumnCount() - 1);
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        assertThat(wizard.groupsPanel().legendTexts()).containsExactly("① AC", "② AB");
    }

    @Test
    void hoveringAGroupExplainsItInSpanish() {
        model.load(KarnaughModelTest.bcd());
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        GroupsPanel groups = wizard.groupsPanel();
        assertThat(groups.legendTexts()).containsExactly("① B'C", "② BD — compartido con M");
        assertThat(groups.explainedGroup()).isZero();
        assertThat(groups.variablesText()).isEqualTo("A cambia → se va · B = 0 → B' · C = 1 → C · D cambia → se va");
        assertThat(groups.sizeText()).isEqualTo("4 celdas: quedan 2 variables");
        assertThat(groups.whyText()).contains("Se va A:", "Queda B'C.");
        assertThat(groups.whyVisible()).isFalse();
        groups.whyToggle().doClick();
        assertThat(groups.whyVisible()).isTrue();

        KarnaughMapView view = groups.mapView(0);
        view.setSize(400, 300);
        List<KarnaughGroup> drawn = view.groups();
        Rectangle2D piece = view.geometry().pieces(drawn.get(1), 4).getFirst();
        mouse(view, MouseEvent.MOUSE_MOVED, new Point((int) piece.getCenterX(), (int) piece.getCenterY()));
        assertThat(groups.explainedGroup()).isEqualTo(1);
        assertThat(groups.variablesText()).isEqualTo("A cambia → se va · B = 1 → B · C cambia → se va · D = 1 → D");
        assertThat(groups.whyText()).contains(
                "Las celdas 13 y 15 son X («no importa»): aquí las tomamos como 1 para agrandar el grupo.");

        groups.selectOutput(1);
        assertThat(groups.legendTexts()).containsExactly("② BD — compartido con P", "③ BC", "④ A");
        assertThat(groups.rulesVisible()).isFalse();
        groups.rulesToggle().doClick();
        assertThat(groups.rulesVisible()).isTrue();
    }

    @Test
    void aConstantOutputIsExplainedInsteadOfGrouped() {
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        assertThat(wizard.groupsPanel().legendTexts()).isEmpty();
        assertThat(wizard.groupsPanel().specialCaseText()).isEqualTo("F = 0: no hay unos que agrupar.");
    }

    @Test
    void theMapPaintsEachGroupOverItsCells() {
        model.setValue(0, 6, LogicValue.ONE);
        model.setValue(0, 7, LogicValue.ONE);
        wizard.goTo(KarnaughWizard.Step.GROUPS);
        KarnaughMapView view = wizard.groupsPanel().mapView(0);
        view.setSize(400, 300);
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        view.paint(graphics);
        graphics.dispose();
        MapGeometry geometry = view.geometry();
        Rectangle grouped = geometry.cell(1, 2); // A = 1, BC = 11: row 7
        Rectangle alone = geometry.cell(0, 2);
        Color inside = new Color(image.getRGB((int) grouped.getCenterX(), grouped.y + grouped.height - 7), true);
        Color outside = new Color(image.getRGB((int) alone.getCenterX(), alone.y + alone.height - 7), true);
        assertThat(inside).isNotEqualTo(outside);
    }

    @Test
    void theExpressionColoursEachTermAndTheOptionsChangeTheCostLine() {
        model.load(KarnaughModelTest.bcd());
        wizard.goTo(KarnaughWizard.Step.EXPRESSION);
        ExpressionPanel expression = wizard.expressionPanel();
        assertThat(expression.expressionTexts()).containsExactly("P = B'C + BD", "M = BD + BC + A");
        assertThat(expression.line(1).runs()).extracting(ExpressionLine.Run::colorIndex)
                .containsExactly(-1, 1, -1, 2, -1, 3);
        assertThat(expression.costText()).isEqualTo("3 AND, 2 OR, 1 NOT (6 compuertas)");

        expression.shareCheckBox().doClick();
        assertThat(model.options().shareTerms()).isFalse();
        assertThat(expression.costText()).isEqualTo("4 AND, 2 OR, 1 NOT (7 compuertas)");

        expression.gateButton(WizardOptions.GateChoice.NOR_ONLY).doClick();
        assertThat(expression.costText()).contains("NOR").doesNotContain("AND");

        expression.formButton(Minimization.Form.PRODUCT_OF_SUMS).doClick();
        assertThat(expression.expressionTexts().getFirst()).isEqualTo("P = (B + C)(B' + D)");
        wizard.back();
        assertThat(wizard.groupsPanel().headerText()).startsWith("Grupos de 0");
    }

    @Test
    void theLastStepCreatesAndHasNoNext() {
        assertThat(wizard.goTo(KarnaughWizard.Step.CREATE)).isTrue();
        assertThat(wizard.nextAction().isEnabled()).isFalse();
        assertThat(wizard.backAction().isEnabled()).isTrue();
        assertThat(wizard.createPanel().controls().createButton().isEnabled()).isTrue();
    }
}
