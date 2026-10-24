package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeEditorPanelTest {
    private static final ElementId INPUT = new ElementId("input");

    @Test
    void theAddNodeToolCanBeActivatedBeforeSelectingANodeOrFigure() {
        ShapeEditorPanel panel = panel(definition(), false);
        AbstractButton addNode = Arrays.stream(panel.toolbar().getComponents()).filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .filter(button -> Messages.text("shape.nodeAdd").equals(button.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(addNode.isEnabled()).isTrue();
    }

    @Test
    void acceptingCommitsTheInlineTextAndToolLettersDoNotOverrideTextEditing() {
        ShapeEditorPanel panel = panel(definition(), false);
        ShapeCanvas canvas = Arrays.stream(panel.getComponents()).filter(ShapeCanvas.class::isInstance)
                .map(ShapeCanvas.class::cast).findFirst().orElseThrow();
        canvas.setTool(ShapeTool.TEXT);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, 90, 90, 1, false, MouseEvent.BUTTON1));
        JTextField field = Arrays.stream(canvas.getComponents()).filter(JTextField.class::isInstance)
                .map(JTextField.class::cast).findFirst().orElseThrow();
        field.setText("Registro");
        assertThat(panel.hasChanges()).isTrue();
        assertThat(panel.getInputMap(ShapeEditorPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke("V"))).isNull();
        AtomicBoolean accepted = new AtomicBoolean();
        panel.onAccept(() -> accepted.set(true));
        panel.acceptButton().doClick(0);
        assertThat(accepted).isTrue();
        assertThat(panel.appearance().drawing().orElseThrow().texts()).singleElement()
                .satisfies(text -> assertThat(text.text()).contains("Registro"));
    }

    @Test
    void switchingToALockedDefinitionKeepsAnIncompatibleInstanceDraftAndItsScope() {
        ComponentDefinition definition = definition();
        ShapeEditorPanel panel = panel(definition, true);
        SymbolDraft moved = SymbolDraft.of(panel.appearance(), definition.pinout(), false)
                .movePin(INPUT, new GridPoint(0, 3)).applyTemplate(SymbolTemplates.Template.ALU);
        panel.apply(moved);
        panel.switchScope(ShapeEditScope.CIRCUIT_DEFINITION);
        assertThat(panel.result().scope()).isEqualTo(ShapeEditScope.INSTANCE);
        assertThat(panel.appearance()).isEqualTo(moved.appearance());
    }

    @Test
    void scopeSwitchingRetainsTheDraftAndClosingStartsANewLocalHistory() {
        ComponentDefinition definition = definition();
        ShapeEditorPanel panel = panel(definition, false);
        SymbolDraft shaped = SymbolDraft.of(panel.appearance(), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.MUX);
        panel.apply(shaped);
        panel.switchScope(ShapeEditScope.CIRCUIT_DEFINITION);
        assertThat(panel.appearance()).isEqualTo(shaped.appearance());
        panel.undo();
        assertThat(panel.appearance()).isEqualTo(ComponentAppearance.of(definition));
        panel.redo();
        assertThat(panel.appearance()).isEqualTo(shaped.appearance());
        ShapeEditorPanel reopened = new ShapeEditorPanel(new ShapeEditRequest(definition, shaped.appearance(),
                ShapeEditScope.INSTANCE, true, false, "", 2, "U1"), AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
        reopened.undo();
        assertThat(reopened.appearance()).isEqualTo(shaped.appearance());
    }

    @Test
    void allToolbarActionsHaveAnIconATooltipAndNoButtonText() {
        ShapeEditorPanel panel = panel(definition(), false);
        for (java.awt.Component component : panel.toolbar().getComponents()) {
            if (component instanceof AbstractButton button) {
                assertThat(button.getIcon()).isNotNull();
                assertThat(button.getText()).isNullOrEmpty();
                assertThat(button.getToolTipText()).isNotBlank().doesNotStartWith("!");
                assertThat(button.getAccessibleContext().getAccessibleName()).isNotBlank();
            }
        }
    }

    @Test
    void escapeAndEnterConsumeTheActiveGestureBeforeAcceptingOrClosing() {
        assertThat(ShapeKeys.escape(true, true)).isEqualTo(ShapeKeys.Intent.CANCEL_OPERATION);
        assertThat(ShapeKeys.escape(false, true)).isEqualTo(ShapeKeys.Intent.CLEAR_SELECTION);
        assertThat(ShapeKeys.escape(false, false)).isEqualTo(ShapeKeys.Intent.CLOSE);
        assertThat(ShapeKeys.enter(true)).isEqualTo(ShapeKeys.Intent.FINISH_OPERATION);
        assertThat(ShapeKeys.enter(false)).isEqualTo(ShapeKeys.Intent.ACCEPT);
    }

    private static ShapeEditorPanel panel(ComponentDefinition definition, boolean locked) {
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, locked, "A2", 2, "U1"), AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
    }

    static ComponentDefinition definition() {
        Component input = new Component(INPUT, InterfacePins.INPUT_PIN, new GridPoint(0, 0), Rotation.R0, Map.of(), "A");
        return new ComponentDefinition("test", "Prueba", "", new GridSize(6, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 2), INPUT)),
                new CircuitFragment(List.of(input), List.of(), List.of()));
    }
}
