package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
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
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeEditorPanelTest {
    private static final ElementId INPUT = new ElementId("input");
    private static final ElementId SECOND_INPUT = new ElementId("second");
    private static final ElementId OUTPUT = new ElementId("output");

    @BeforeEach
    void spanish() { Messages.initialize(Messages.SPANISH); }

    @AfterEach
    void restoreSpanish() { Messages.initialize(Messages.SPANISH); }

    @Test
    void theAddNodeToolCanBeActivatedBeforeSelectingANodeOrFigure() {
        ShapeEditorPanel panel = panel(definition(), false);
        AbstractButton addNode = toolbarButtons(panel).stream()
                .filter(button -> Messages.text("shape.nodeAdd").equals(button.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(addNode.isEnabled()).isTrue();
    }

    @Test
    void acceptingCommitsTheInlineTextAndToolLettersDoNotOverrideTextEditing() {
        ShapeEditorPanel panel = panel(definition(), false);
        ShapeCanvas canvas = panel.canvas();
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
        assertThat(panel.messageLines()).contains(Messages.text("shape.scope.incompatible"));
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
    void everyToolButtonHasAnIconNoTextAndATooltipInBothLanguages() {
        for (Locale language : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            Messages.initialize(language);
            ShapeEditorPanel panel = panel(definition(), false);
            List<AbstractButton> tools = toolbarButtons(panel).stream()
                    .filter(button -> button != panel.zoomValueButton()).toList();
            assertThat(tools).hasSize(21);
            for (AbstractButton button : tools) {
                String name = button.getAccessibleContext().getAccessibleName();
                assertThat(button.getIcon()).as(name).isNotNull();
                assertThat(button.getText()).as(name).isNullOrEmpty();
                assertThat(name).isNotBlank().doesNotContain("!");
                assertThat(button.getToolTipText()).as(name).isNotBlank().doesNotContain("!").contains(name);
            }
        }
    }

    @Test
    void figureToolTooltipsNameTheirKeyAndWhatShiftDoes() {
        ShapeEditorPanel panel = panel(definition(), false);
        assertThat(toolTip(panel, "shape.tool.rectangle")).contains("Rectángulo (R)").contains("Mayús: cuadrado");
        assertThat(toolTip(panel, "shape.tool.ellipse")).contains("Elipse (E)").contains("Mayús: círculo");
        assertThat(toolTip(panel, "shape.tool.line")).contains("Línea (L)").contains("Mayús:");
        assertThat(toolTip(panel, "shape.tool.triangle")).contains("Triángulo (G)").contains("Mayús:");
        assertThat(toolTip(panel, "shape.tool.polygon")).contains("Polígono (P)").contains("Doble clic");
    }

    @Test
    void disabledContextualButtonsSayWhy() {
        ShapeEditorPanel panel = panel(definition(), false);
        for (String key : List.of("shape.nodeRemove", "shape.curve", "shape.fill", "shape.front", "shape.back",
                "shape.undo", "shape.redo")) {
            AbstractButton button = button(panel, key);
            assertThat(button.isEnabled()).as(key).isFalse();
            assertThat(button.getToolTipText()).as(key).contains(" — ");
        }
    }

    @Test
    void undoAndRedoTooltipsShowThePlatformShortcut() {
        ShapeEditorPanel panel = panel(definition(), false);
        panel.apply(SymbolDraft.of(panel.appearance(), definition().pinout(), false)
                .applyTemplate(SymbolTemplates.Template.MUX));
        String undo = KeyBindings.describe(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ShortcutCatalog.menuMask()));
        assertThat(button(panel, "shape.undo").getToolTipText()).contains(undo);
    }

    @Test
    void theZoomValueIsShownAsTextAndAClickGoesBackToOneHundredPercent() {
        ShapeEditorPanel panel = panel(definition(), false);
        panel.canvas().setZoom(1.5);
        assertThat(panel.zoomValueButton().getText()).isEqualTo("150 %");
        panel.zoomValueButton().doClick(0);
        assertThat(panel.zoomValueButton().getText()).isEqualTo("100 %");
    }

    @Test
    void fromACanvasPieceTheScopeIsASwitchWithASentencePerChoice() {
        ShapeEditorPanel panel = new ShapeEditorPanel(new ShapeEditRequest(definition(),
                ComponentAppearance.of(definition()), ShapeEditScope.INSTANCE, true, false, "", 3, "U3", true, false),
                AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
        assertThat(panel.scopeSwitchTexts()).containsExactly("Solo esta pieza (U3)", "Todas las piezas (3 en este circuito)");
        assertThat(panel.scopeDetail()).isEqualTo("El circuito de adentro sigue siendo el del componente.");
        panel.switchScope(ShapeEditScope.CIRCUIT_DEFINITION);
        assertThat(panel.scopeDetail()).isEqualTo("3 piezas cambian. Se guarda también en la librería.");
    }

    @Test
    void fromThePaletteTheScopeIsFixedText() {
        ShapeEditorPanel circuit = new ShapeEditorPanel(new ShapeEditRequest(definition(),
                ComponentAppearance.of(definition()), ShapeEditScope.CIRCUIT_DEFINITION, false, false, "", 1, "", false,
                false), AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
        assertThat(circuit.scopeSwitchTexts()).isEmpty();
        assertThat(circuit.scopeTitle()).isEqualTo("El componente en este circuito");
        assertThat(circuit.scopeDetail()).isEqualTo("1 pieza cambia.");

        ShapeEditorPanel library = new ShapeEditorPanel(new ShapeEditRequest(definition(),
                ComponentAppearance.of(definition()), ShapeEditScope.LIBRARY_DEFINITION, false, false, "", 0, "", true,
                true), AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
        assertThat(library.scopeTitle()).isEqualTo("El componente en la librería");
        assertThat(library.scopeDetail()).isEqualTo("Este circuito usa otra versión: no cambia.");
    }

    @Test
    void theStatusLineSaysNoChangesReadyWarningsOrWhatBlocks() {
        ComponentDefinition definition = twoPinDefinition();
        ShapeEditorPanel panel = panel(definition, false);
        assertThat(panel.statusText()).isEqualTo("Sin cambios.");
        assertThat(panel.acceptButton().isEnabled()).isFalse();

        SymbolDraft shaped = SymbolDraft.of(panel.appearance(), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE);
        panel.apply(shaped);
        assertThat(panel.statusText()).isEqualTo("Listo para aceptar.");
        assertThat(panel.acceptButton().isEnabled()).isTrue();

        panel.apply(shaped.movePin(INPUT, new GridPoint(2, 2)));
        assertThat(panel.statusText()).isEqualTo("1 aviso.");
        assertThat(panel.messageLines()).contains("El pin A no está en el borde. (No impide aceptar.)");
        assertThat(panel.acceptButton().isEnabled()).isTrue();

        panel.apply(shaped.movePin(INPUT, new GridPoint(0, 3)));
        assertThat(panel.statusText()).isEqualTo("Dos pines en el mismo punto: A y B.");
        assertThat(panel.statusIcon()).isNotNull();
        assertThat(panel.acceptButton().isEnabled()).isFalse();
        assertThat(panel.pinRowProblem(0)).isEqualTo("mismo punto que B");
    }

    @Test
    void enterAcceptsOnlyWhenThePanelCanAccept() {
        ComponentDefinition definition = twoPinDefinition();
        ShapeEditorPanel panel = panel(definition, false);
        AtomicBoolean accepted = new AtomicBoolean();
        panel.onAccept(() -> accepted.set(true));
        panel.apply(SymbolDraft.of(panel.appearance(), definition.pinout(), false).movePin(INPUT, new GridPoint(0, 3)));
        pressEnter(panel);
        assertThat(accepted).isFalse();
        panel.apply(SymbolDraft.of(panel.appearance(), definition.pinout(), false).movePin(INPUT, new GridPoint(0, 1)));
        pressEnter(panel);
        assertThat(accepted).isTrue();
    }

    @Test
    void theTextGroupShowsOnlyWithATextSelectedAndReflectsIt() {
        ComponentDefinition definition = definition();
        ShapeEditorPanel panel = panel(definition, false);
        assertThat(panel.textGroupVisible()).isFalse();
        SymbolDraft withText = SymbolDraft.of(panel.appearance(), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE)
                .addText(new SymbolText(new SymbolPoint(8, 8), java.util.Optional.of("X"), TextSize.LARGE,
                        TextAlignment.CENTER));
        int textIndex = withText.elements().size() - 1;
        panel.apply(withText.select(Set.of()));
        assertThat(panel.textGroupVisible()).isFalse();
        panel.apply(withText.select(Set.of(textIndex)));
        assertThat(panel.textGroupVisible()).isTrue();
        assertThat(panel.selectedTextSize()).isEqualTo(TextSize.LARGE);
        assertThat(panel.selectedTextAlignment()).isEqualTo(TextAlignment.CENTER);
    }

    @Test
    void choosingAPinRowSelectsThePinOnTheCanvasAndBack() {
        ComponentDefinition definition = twoPinDefinition();
        ShapeEditorPanel panel = panel(definition, false);
        panel.pinTable().setRowSelectionInterval(1, 1);
        assertThat(panel.canvas().selectedPin()).contains(SECOND_INPUT);

        Point pin = panel.canvas().pinLocation(INPUT);
        panel.canvas().dispatchEvent(new MouseEvent(panel.canvas(), MouseEvent.MOUSE_PRESSED, 0,
                MouseEvent.BUTTON1_DOWN_MASK, pin.x, pin.y, 1, false, MouseEvent.BUTTON1));
        assertThat(panel.pinTable().getSelectedRow()).isZero();
    }

    @Test
    void lockedPinsShowAPadlockAndSayWhy() {
        ShapeEditorPanel panel = new ShapeEditorPanel(new ShapeEditRequest(definition(),
                ComponentAppearance.of(definition()), ShapeEditScope.CIRCUIT_DEFINITION, false, true, "CPU simple", 1,
                ""), AppPreferences.inMemory(), () -> ColorSchemes.LIGHT);
        assertThat(panel.pinRowLocked(0)).isTrue();
        assertThat(panel.messageLines()).anySatisfy(line -> assertThat(line).contains("«CPU simple»"));
    }

    @Test
    void theHintUnderTheCanvasFollowsTheTool() {
        ShapeEditorPanel panel = panel(definition(), false);
        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.hint.select"));
        button(panel, "shape.tool.rectangle").doClick(0);
        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.hint.rectangle"));
        button(panel, "shape.tool.pan").doClick(0);
        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.pan.hint"));
    }

    @Test
    void thePreviewNamesTheCurrentSchemeAndTheOtherTheme() {
        assertThat(ShapeEditorPanel.previewCaptions(ColorSchemes.LIGHT)).containsExactly("Actual: Claro", "Oscuro");
        assertThat(ShapeEditorPanel.previewCaptions(ColorSchemes.DARK)).containsExactly("Actual: Oscuro", "Claro");
    }

    @Test
    void escapeAndEnterConsumeTheActiveGestureBeforeAcceptingOrClosing() {
        assertThat(ShapeKeys.escape(true, true)).isEqualTo(ShapeKeys.Intent.CANCEL_OPERATION);
        assertThat(ShapeKeys.escape(false, true)).isEqualTo(ShapeKeys.Intent.CLEAR_SELECTION);
        assertThat(ShapeKeys.escape(false, false)).isEqualTo(ShapeKeys.Intent.CLOSE);
        assertThat(ShapeKeys.enter(true)).isEqualTo(ShapeKeys.Intent.FINISH_OPERATION);
        assertThat(ShapeKeys.enter(false)).isEqualTo(ShapeKeys.Intent.ACCEPT);
    }

    private static void pressEnter(ShapeEditorPanel panel) {
        Object key = panel.getInputMap(ShapeEditorPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke("ENTER"));
        Action action = panel.getActionMap().get(key);
        action.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "enter"));
    }

    private static List<AbstractButton> toolbarButtons(ShapeEditorPanel panel) {
        return Arrays.stream(panel.toolbar().getComponents()).filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast).toList();
    }

    private static AbstractButton button(ShapeEditorPanel panel, String nameKey) {
        return toolbarButtons(panel).stream()
                .filter(button -> Messages.text(nameKey).equals(button.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static String toolTip(ShapeEditorPanel panel, String nameKey) {
        return button(panel, nameKey).getToolTipText();
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

    private static ComponentDefinition twoPinDefinition() {
        Component first = new Component(INPUT, InterfacePins.INPUT_PIN, new GridPoint(0, 0), Rotation.R0, Map.of(), "A");
        Component second = new Component(SECOND_INPUT, InterfacePins.INPUT_PIN, new GridPoint(0, 4), Rotation.R0,
                Map.of(), "B");
        Component output = new Component(OUTPUT, InterfacePins.OUTPUT_PIN, new GridPoint(8, 0), Rotation.R0, Map.of(),
                "F");
        return new ComponentDefinition("two", "Dos", "", new GridSize(6, 4),
                List.of(new DefinitionPin(PinSpec.input("A", 0, 2), INPUT),
                        new DefinitionPin(PinSpec.input("B", 0, 3), SECOND_INPUT),
                        new DefinitionPin(PinSpec.output("F", 6, 2), OUTPUT)),
                new CircuitFragment(List.of(first, second, output), List.of(), List.of()));
    }
}
