package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.app.AppPreferences;
import java.awt.Component;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Arrows move and Supr deletes whatever is selected in the shape editor, whichever control was used last. */
class ShapeEditorSelectionKeysTest {
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    @Test
    void deleteAndBackspaceRemoveTheSelectedFigure() {
        for (int key : new int[]{KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE}) {
            ShapeEditorPanel panel = panelWithFigure();
            assertThat(panel.appearance().drawing().orElseThrow().paths()).hasSize(1);

            press(panel, key, 0);

            assertThat(panel.appearance().drawing().map(drawing -> drawing.paths().size()).orElse(0))
                    .as("key %s", key).isZero();
        }
    }

    @Test
    void arrowsMoveTheSelectedFigureAMagnetStepAndShiftArrowsFiveUnits() {
        ShapeEditorPanel panel = panelWithFigure();

        press(panel, KeyEvent.VK_RIGHT, 0);
        assertThat(firstPoint(panel)).isEqualTo(new SymbolPoint(4, 0));
        press(panel, KeyEvent.VK_DOWN, 0);
        assertThat(firstPoint(panel)).isEqualTo(new SymbolPoint(4, 4));
        press(panel, KeyEvent.VK_LEFT, 0);
        press(panel, KeyEvent.VK_UP, 0);
        assertThat(firstPoint(panel)).isEqualTo(new SymbolPoint(0, 0));

        press(panel, KeyEvent.VK_RIGHT, SHIFT);
        assertThat(firstPoint(panel)).isEqualTo(new SymbolPoint(40, 0));
        press(panel, KeyEvent.VK_DOWN, SHIFT);
        assertThat(firstPoint(panel)).as("clamped by the box, 4 units high").isEqualTo(new SymbolPoint(40, 24));
    }

    @Test
    void aSelectedPinMovesWithTheArrowsAndIsNotDeletedBySupr() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        ShapeEditorPanel panel = panel(definition);
        panel.canvas().selectPin(definition.pinout().getFirst().source());
        GridPoint original = pinOffset(panel);

        press(panel, KeyEvent.VK_DOWN, 0);
        assertThat(pinOffset(panel)).isEqualTo(new GridPoint(original.x(), original.y() + 1));
        press(panel, KeyEvent.VK_RIGHT, SHIFT);
        assertThat(pinOffset(panel).x()).as("five units, or as far as the box lets it").isGreaterThan(original.x());

        press(panel, KeyEvent.VK_DELETE, 0);
        press(panel, KeyEvent.VK_BACK_SPACE, 0);
        assertThat(panel.appearance().pins()).hasSize(1);
        assertThat(pinOffset(panel).y()).isEqualTo(original.y() + 1);
    }

    @Test
    void aSelectedTextMovesAndIsDeleted() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        ShapeEditorPanel panel = panel(definition);
        panel.apply(SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE).select(Set.of(1)));
        SymbolText before = panel.appearance().drawing().orElseThrow().texts().getFirst();

        press(panel, KeyEvent.VK_RIGHT, 0);
        SymbolText moved = panel.appearance().drawing().orElseThrow().texts().getFirst();
        assertThat(moved.anchor().x()).isEqualTo(before.anchor().x() + 4);

        press(panel, KeyEvent.VK_DELETE, 0);
        assertThat(panel.appearance().drawing().orElseThrow().texts()).isEmpty();
        assertThat(panel.appearance().drawing().orElseThrow().paths()).hasSize(1);
    }

    @Test
    void aSelectedNodeMovesWithTheArrowsAndIsDeletedBySupr() {
        ShapeEditorPanel panel = panelWithFigure();
        panel.canvas().setSize(400, 300);
        panel.canvas().setZoom(1);
        panel.canvas().setTool(ShapeTool.NODES);
        // A press and release on the figure's top left node (the canvas origin is at 70, 70).
        panel.canvas().dispatchEvent(new MouseEvent(panel.canvas(), MouseEvent.MOUSE_PRESSED, 0,
                MouseEvent.BUTTON1_DOWN_MASK, 70, 70, 1, false, MouseEvent.BUTTON1));
        panel.canvas().dispatchEvent(new MouseEvent(panel.canvas(), MouseEvent.MOUSE_RELEASED, 0, 0, 70, 70, 1,
                false, MouseEvent.BUTTON1));
        assertThat(panel.canvas().nodeSelected()).isTrue();

        press(panel, KeyEvent.VK_RIGHT, 0);
        assertThat(firstPoint(panel)).isEqualTo(new SymbolPoint(4, 0));

        press(panel, KeyEvent.VK_DELETE, 0);
        assertThat(((SymbolPath) panel.appearance().drawing().orElseThrow().elements().getFirst()).nodes())
                .hasSize(3);
    }

    @Test
    void aTextFieldASpinnerOrAComboBoxKeepsItsKeys() {
        JSpinner spinner = new JSpinner();
        Object[] owners = {new JTextField(), ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField(),
                new JComboBox<>(new String[]{"a"})};
        for (Object owner : owners) {
            ShapeEditorPanel panel = panelWithFigure();
            panel.focusOwner(() -> (Component) owner);

            press(panel, KeyEvent.VK_RIGHT, 0);
            press(panel, KeyEvent.VK_DELETE, 0);
            press(panel, KeyEvent.VK_BACK_SPACE, 0);

            assertThat(panel.appearance().drawing().orElseThrow().paths()).as(owner.getClass().getSimpleName())
                    .hasSize(1);
            assertThat(firstPoint(panel)).isEqualTo(new SymbolPoint(0, 0));
        }
    }

    @Test
    void choosingARowOfThePinTableOrAToolBringsTheKeysBackToTheCanvas() throws Exception {
        ShapeEditorPanel panel = panel(ShapeEditorPanelTest.definition());
        int initial = panel.canvasFocusRequests();

        panel.pinTable().setRowSelectionInterval(0, 0);
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(panel.canvasFocusRequests()).as("after choosing a pin row").isGreaterThan(initial);

        int afterRow = panel.canvasFocusRequests();
        ShapeEditorPanelTest.toolbarButtons(panel).stream().filter(JToggleButton.class::isInstance)
                .findFirst().orElseThrow().doClick(0);
        assertThat(panel.canvasFocusRequests()).as("after choosing a tool").isGreaterThan(afterRow);
    }

    @Test
    void theSelectionHintsMentionTheArrowsAndSupr() {
        ShapeEditorPanel panel = panelWithFigure();
        String delete = Messages.locale().getLanguage().equals("es") ? "Supr" : "Delete";

        assertThat(panel.hintText()).contains(delete);
        assertThat(Messages.text("shape.hint.pin")).containsIgnoringCase(Messages.locale().getLanguage().equals("es") ? "Mayús" : "Shift");
        assertThat(Messages.text("shape.hint.nodeSelected")).contains(delete);
    }

    private static GridPoint pinOffset(ShapeEditorPanel panel) {
        return panel.appearance().pins().stream().map(PinPlacement::offset).findFirst().orElseThrow();
    }

    private static SymbolPoint firstPoint(ShapeEditorPanel panel) {
        return ((SymbolPath) panel.appearance().drawing().orElseThrow().elements().getFirst()).nodes().getFirst()
                .point();
    }

    private static ShapeEditorPanel panelWithFigure() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        ShapeEditorPanel panel = panel(definition);
        SymbolDraft empty = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE).select(Set.of(0, 1)).deleteSelection();
        SymbolPath figure = SymbolTemplates.rectangle(new SymbolPoint(0, 0), new SymbolPoint(8, 8), false);
        panel.apply(empty.addFigure(figure).select(Set.of(0)));
        return panel;
    }

    private static ShapeEditorPanel panel(ComponentDefinition definition) {
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, false, "A2", 2, "U1"), AppPreferences.inMemory(),
                () -> ColorSchemes.LIGHT);
    }

    private static void press(ShapeEditorPanel panel, int keyCode, int modifiers) {
        Object name = panel.getInputMap(ShapeEditorPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(keyCode, modifiers));
        assertThat(name).as("a binding for key %s with %s", keyCode, modifiers).isNotNull();
        Action action = panel.getActionMap().get(name);
        action.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "key"));
    }
}
