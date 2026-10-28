package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.LedPainter;
import cl.almejo.vsim.ui.theme.ReadableColors;
import java.util.List;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import java.awt.Component;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterFormTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private ParameterForm form(String typeId, Map<String, Object> current) {
        return new ParameterForm(registry.require(typeId), current);
    }

    private static void typeInto(JSpinner spinner, String text) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    @Test
    void showsOneEditorPerParameterWithTheCurrentValues() {
        ParameterForm form = form(StandardComponentTypes.CLOCK, Map.of("time-up", 250));
        assertThat(((JSpinner) form.editor("time-up")).getValue()).isEqualTo(250);
        assertThat(((JSpinner) form.editor("time-down")).getValue()).isEqualTo(1000);
        JSpinner timeDown = (JSpinner) form.editor("time-down");
        assertThat(((JSpinner.DefaultEditor) timeDown.getEditor()).getTextField().getText()).isEqualTo("1000");
    }

    @Test
    void unchangedValuesReportNoChanges() {
        ParameterForm form = form(StandardComponentTypes.BUFFER, Map.of("delay", 3));
        assertThat(form.validationError()).isEmpty();
        assertThat(form.hasChanges()).isFalse();
    }

    @Test
    void changedValuesAreReported() {
        ParameterForm form = form(StandardComponentTypes.BUFFER, Map.of());
        ((JSpinner) form.editor("delay")).setValue(5);
        assertThat(form.validationError()).isEmpty();
        assertThat(form.hasChanges()).isTrue();
        assertThat(form.rawValues()).containsEntry("delay", 5);
    }

    @Test
    void choiceEditorOffersTheParameterChoices() {
        ParameterForm form = form(StandardComponentTypes.INPUT_PIN, Map.of());
        @SuppressWarnings("unchecked")
        JComboBox<String> values = (JComboBox<String>) form.editor("value");
        assertThat(values.getItemCount()).isEqualTo(3);
        assertThat(values.getSelectedItem()).isEqualTo("0");
        values.setSelectedItem("1");
        assertThat(form.rawValues()).containsEntry("value", "1");
        assertThat(form.hasChanges()).isTrue();
    }

    @Test
    void aColourParameterIsChosenFromNamedSwatches() {
        ParameterForm form = form(StandardComponentTypes.LED, Map.of());
        ColorSwatches<?> colors = (ColorSwatches<?>) form.editor("color");
        assertThat(colors.values()).hasSize(8);
        assertThat(colors.selectedValue()).isEqualTo("red");
        assertThat(colors.swatch(1).getToolTipText()).isEqualTo("Verde");

        colors.swatch(1).doClick();

        assertThat(form.rawValues()).containsEntry("color", "green");
        assertThat(form.hasChanges()).isTrue();
    }

    @Test
    void aTunnelEntradaPicksItsPaletteColourFromNamedSwatches() {
        ParameterForm form = form(StandardComponentTypes.TUNNEL_ENTRANCE, Map.of());
        ColorSwatches<?> colors = (ColorSwatches<?>) form.editor("color");
        assertThat(colors.values()).hasSize(8);
        assertThat(colors.swatch(0).getToolTipText()).isEqualTo(Messages.text("tunnel.color.red"));
        assertThat(colors.swatch(6).getToolTipText()).isEqualTo(Messages.text("tunnel.color.pink"));

        colors.swatch(6).doClick();

        assertThat(form.rawValues()).containsEntry("color", 6);
    }

    @Test
    void theTunnelSwatchesPaintWithTheColourSchemeTheCanvasUses() {
        // a dark scheme chosen in Preferencias on a light theme: the swatches show the dark variant, like the canvas
        ParameterForm form = new ParameterForm(registry.require(StandardComponentTypes.TUNNEL_ENTRANCE), Map.of(),
                () -> ColorSchemes.DARK);
        ColorSwatches<?> colors = (ColorSwatches<?>) form.editor("color");

        assertThat(SwatchPixels.centre(colors.swatch(5))).isEqualTo(ColorSchemes.DARK.tunnelColor(5).getRGB());
    }

    @Test
    void outOfRangeTextReportsALocalizedError() {
        ParameterForm form = form(StandardComponentTypes.BUFFER, Map.of());
        typeInto((JSpinner) form.editor("delay"), "0");
        assertThat(form.validationError()).hasValueSatisfying(message -> assertThat(message)
                .contains("Retardo").contains("1.000.000"));
    }

    @Test
    void nonNumericTextReportsALocalizedError() {
        ParameterForm form = form(StandardComponentTypes.CLOCK, Map.of());
        typeInto((JSpinner) form.editor("time-up"), "rápido");
        assertThat(form.validationError())
                .hasValueSatisfying(message -> assertThat(message).contains("Tiempo en alto"));
    }

    @Test
    void typeWithoutParametersHasNothingToChange() {
        ComponentType display = registry.require(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY);
        ParameterForm form = new ParameterForm(display, Map.of());
        assertThat(form.rawValues()).isEmpty();
        assertThat(form.hasChanges()).isFalse();
    }

    @Test
    void choiceWithNothingSelectedReportsAnInvalidValue() {
        ParameterForm form = form(StandardComponentTypes.INPUT_PIN, Map.of());
        ((JComboBox<?>) form.editor("value")).setSelectedItem(null);
        assertThat(form.validationError()).contains("Valor inválido para " + Messages.parameterName("value"));
    }

    @Test
    void spinnerTextFieldsKeepInvalidTextWhenFocusIsLost() {
        ParameterForm form = form(StandardComponentTypes.BUFFER, Map.of());
        JFormattedTextField field = ((JSpinner.DefaultEditor) ((JSpinner) form.editor("delay")).getEditor()).getTextField();
        assertThat(field.getFocusLostBehavior()).isEqualTo(JFormattedTextField.COMMIT);
    }

    @Test
    void commitListenersHearSpinnerAndChoiceChanges() {
        ParameterForm clock = form(StandardComponentTypes.CLOCK, Map.of());
        AtomicInteger commits = new AtomicInteger();
        clock.addCommitListener(commits::incrementAndGet);
        ((JSpinner) clock.editor("time-up")).setValue(250);
        assertThat(commits.get()).isPositive();
        assertThat(clock.rawValues()).containsEntry("time-up", 250);

        ParameterForm led = form(StandardComponentTypes.LED, Map.of());
        AtomicInteger choices = new AtomicInteger();
        led.addCommitListener(choices::incrementAndGet);
        ((ColorSwatches<?>) led.editor("color")).swatch(1).doClick();
        assertThat(choices.get()).isPositive();

        ParameterForm pin = form(StandardComponentTypes.INPUT_PIN, Map.of());
        AtomicInteger pinChoices = new AtomicInteger();
        pin.addCommitListener(pinChoices::incrementAndGet);
        ((JComboBox<?>) pin.editor("value")).setSelectedItem("1");
        assertThat(pinChoices.get()).isPositive();
    }

    private static JLabel labelOf(ParameterForm form, String parameterName) {
        for (Component child : form.getComponents()) {
            if (child instanceof JLabel label && label.getLabelFor() == form.editor(parameterName)) {
                return label;
            }
        }
        throw new AssertionError("no label for " + parameterName);
    }

    @Test
    void timeParametersSayTheirUnitAndExplainIt() {
        String tip = "En unidades de tiempo de simulación";
        ParameterForm clock = form(StandardComponentTypes.CLOCK, Map.of());
        assertThat(labelOf(clock, "time-up").getText()).isEqualTo("Tiempo en alto (t)");
        assertThat(labelOf(clock, "time-down").getText()).isEqualTo("Tiempo en bajo (t)");
        assertThat(labelOf(clock, "time-up").getToolTipText()).isEqualTo(tip);
        assertThat(clock.editor("time-up").getToolTipText()).isEqualTo(tip);
        ParameterForm gate = form(StandardComponentTypes.BUFFER, Map.of());
        assertThat(labelOf(gate, "delay").getText()).isEqualTo("Retardo (t)");
        assertThat(((JSpinner.DefaultEditor) ((JSpinner) gate.editor("delay")).getEditor()).getTextField()
                .getToolTipText()).isEqualTo(tip);
        ParameterForm diagram = form(StandardComponentTypes.TIME_DIAGRAM, Map.of());
        assertThat(labelOf(diagram, "sample-interval").getText()).isEqualTo("Intervalo de muestreo (t)");
        assertThat(labelOf(diagram, "history").getText()).isEqualTo("Historial (muestras)");
        assertThat(labelOf(diagram, "history").getToolTipText()).isNull();
        assertThat(labelOf(form(StandardComponentTypes.SWITCH, Map.of()), "value").getText()).isEqualTo("Valor");
    }

    @Test
    void anInvalidTimeKeepsThePlainNameInItsMessage() {
        ParameterForm gate = form(StandardComponentTypes.BUFFER, Map.of());
        typeInto((JSpinner) gate.editor("delay"), "0");
        assertThat(gate.validationError()).hasValueSatisfying(message -> assertThat(message)
                .contains("Retardo").doesNotContain("(t)"));
    }

    @Test
    void theRowsCanJoinAHostGridAndStillValidate() {
        ParameterForm form = form(StandardComponentTypes.CLOCK, Map.of());
        javax.swing.JPanel host = new javax.swing.JPanel();
        form.addRowsTo(host);
        assertThat(form.getComponentCount()).isZero();
        assertThat(host.getComponents()).hasSize(4);
        assertThat(((JLabel) host.getComponent(0)).getLabelFor()).isSameAs(form.editor("time-up"));
        assertThat(host.getComponent(1)).isSameAs(form.editor("time-up"));
        assertThat(host.getComponent(3)).isSameAs(form.editor("time-down"));
        typeInto((JSpinner) form.editor("time-up"), "0");
        assertThat(form.validationError()).isPresent();
        assertThat(((JSpinner) form.editor("time-up")).getClientProperty("JComponent.outline")).isEqualTo("error");
    }

    @Test
    void everyColourParameterUsesOnlyColourNamesTheSwatchesCanPaint() {
        for (ComponentType type : registry.all()) {
            for (ParameterSpec parameter : type.parameters()) {
                if (parameter.kind() == ParameterKind.COLOR) {
                    assertThat(parameter.choices()).as(type.id() + "." + parameter.name())
                            .allMatch(LedPainter::knowsColor);
                }
            }
        }
    }

    @Test
    void hiddenParametersGetNoFieldAndTravelUnchanged() {
        String contents = MemoryContents.empty().with(0, 5).with(1, 7).encode();
        ParameterForm form = form(StandardComponentTypes.RAM, Map.of("contents", contents, "link", "/tmp/x.hex"));
        assertThat(form.editor("contents")).isNull();
        assertThat(form.editor("link")).isNull();
        assertThat(form.editor("words")).isNotNull();
        assertThat(form.rawValues()).containsKeys("contents", "link");
        assertThat(form.rawValues().get("link")).isEqualTo("/tmp/x.hex");
        assertThat(form.validationError()).isEmpty();
        assertThat(form.hasChanges()).isFalse();
        ((JComboBox<?>) form.editor("words")).setSelectedIndex(0);
        assertThat(form.hasChanges()).isTrue();
        assertThat(form.rawValues().get("contents")).isEqualTo(contents);
    }

    private static ComponentType flaggedType() {
        return new ComponentType("test-flagged", ComponentCategory.GATES, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)), List.of(ParameterSpec.bool("negate", false)),
                parameters -> (context, pinIndex) -> {
                });
    }

    @Test
    void aBooleanParameterIsACheckBoxHoldingTheCurrentValue() {
        ParameterForm unchecked = new ParameterForm(flaggedType(), Map.of());
        ParameterForm checked = new ParameterForm(flaggedType(), Map.of("negate", true));

        assertThat(((JCheckBox) unchecked.editor("negate")).isSelected()).isFalse();
        assertThat(((JCheckBox) checked.editor("negate")).isSelected()).isTrue();
        assertThat(unchecked.validationError()).isEmpty();
    }

    @Test
    void togglingTheCheckBoxCommitsOnceAndReportsTheBoolean() {
        ParameterForm form = new ParameterForm(flaggedType(), Map.of());
        AtomicInteger commits = new AtomicInteger();
        form.addCommitListener(commits::incrementAndGet);

        ((JCheckBox) form.editor("negate")).doClick();

        assertThat(commits).hasValue(1);
        assertThat(form.rawValues()).containsEntry("negate", Boolean.TRUE);
        assertThat(form.hasChanges()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<String> switchValueBox(ParameterForm form) {
        return (JComboBox<String>) form.editor("value");
    }

    @Test
    void theSwitchValueIsAnEditableBoxWithOneZeroAndHighImpedance() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of());

        JComboBox<String> values = switchValueBox(form);

        assertThat(values.isEditable()).isTrue();
        assertThat(values.getItemCount()).isEqualTo(3);
        assertThat(values.getItemAt(0)).isEqualTo("1");
        assertThat(values.getItemAt(1)).isEqualTo("0");
        assertThat(values.getItemAt(2)).isEqualTo("Z");
        assertThat(values.getSelectedItem()).isEqualTo("1");
    }

    @Test
    void aOneBitSwitchTakesItsValueFromTheListAndAWideOneTheTypedNumber() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of());
        switchValueBox(form).setSelectedItem("Z");
        assertThat(form.rawValues()).containsEntry("value", "Z");
        assertThat(form.validationError()).isEmpty();

        ParameterForm wide = form(StandardComponentTypes.SWITCH, Map.of("width", 8, "value", "0xFF"));
        switchValueBox(wide).getEditor().setItem("0b1010");
        assertThat(wide.rawValues()).containsEntry("value", "0b1010");
        assertThat(wide.validationError()).isEmpty();
        assertThat(wide.hasChanges()).isTrue();
    }

    @Test
    void aTypedSwitchValueThatDoesNotFitIsRefusedAndOneInAnotherBaseIsShownNormalized() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 4));
        switchValueBox(form).getEditor().setItem("0x1F");
        assertThat(form.validationError()).isPresent();

        switchValueBox(form).getEditor().setItem("0x0a");
        form.showNormalizedText();
        assertThat(switchValueBox(form).getEditor().getItem()).isEqualTo("0xA");
    }

    private static void typeSwitchValue(ParameterForm form, String text) {
        ((JTextField) switchValueBox(form).getEditor().getEditorComponent()).setText(text);
    }

    private static void setWidth(ParameterForm form, int width) {
        ((JSpinner) form.editor("width")).setValue(width);
    }

    /** The texts of the reading block's labels, in order (caption, value, caption, value…). */
    private static List<String> readingTexts(ParameterForm form) {
        return List.of(form.valueReading().getComponents()).stream()
                .map(component -> ((JLabel) component).getText()).toList();
    }

    /** Lays the form out at a typical side-panel width. */
    private static void layOutAt(int width, ParameterForm form) {
        form.setSize(width, 400);
        // resize events are queued on the EDT: let them rebuild the block, then lay it out again
        for (int pass = 0; pass < 2; pass++) {
            cl.almejo.vsim.ui.UiTestSupport.layOut(form);
            flushEventQueue();
        }
        cl.almejo.vsim.ui.UiTestSupport.layOut(form);
    }

    private static void flushEventQueue() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (java.lang.reflect.InvocationTargetException | InterruptedException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void aWideSwitchValueIsReadBackInBinaryHexAndDecimalOneUnderTheOtherWhileTyping() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));

        typeSwitchValue(form, "100");

        assertThat(form.valueReading().isVisible()).isTrue();
        assertThat(readingTexts(form)).containsExactly("binario", "0110 0100", "hexa", "0x64", "decimal", "100");
    }

    @Test
    void unsignedNumbersReadTheDecimalWithoutSignAndHaveNoSignedRow() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));

        typeSwitchValue(form, "0xFF");

        assertThat(readingTexts(form)).containsExactly("binario", "1111 1111", "hexa", "0xFF", "decimal", "255");
    }

    @Test
    void signedNumbersReadTheDecimalWithSignInsteadOfTheUnsignedOne() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8, "signedness", "signed"));

        typeSwitchValue(form, "-1");
        assertThat(readingTexts(form)).containsExactly("binario", "1111 1111", "hexa", "0xFF", "con signo", "-1");

        typeSwitchValue(form, "0x7F");
        assertThat(readingTexts(form)).containsExactly("binario", "0111 1111", "hexa", "0x7F", "con signo", "127");
    }

    @Test
    void aNegativeValueIsRefusedUntilTheNumbersAreSigned() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));

        typeSwitchValue(form, "-1");
        assertThat(readingTexts(form)).containsExactly("Valor no válido (usa 0b…, 0x… o un número)");

        ((JComboBox<?>) form.editor("signedness")).setSelectedItem("signed");
        assertThat(readingTexts(form)).containsExactly("binario", "1111 1111", "hexa", "0xFF", "con signo", "-1");
        assertThat(form.rawValues()).containsEntry("signedness", "signed").containsEntry("value", "-1");

        ((JComboBox<?>) form.editor("signedness")).setSelectedItem("unsigned");
        assertThat(readingTexts(form)).hasSize(1);
    }

    @Test
    void theNumbersRowOnlyShowsFromTwoBitsOn() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of());
        assertThat(form.editor("signedness").isVisible()).isFalse();

        setWidth(form, 4);
        assertThat(form.editor("signedness").isVisible()).isTrue();

        setWidth(form, 1);
        assertThat(form.editor("signedness").isVisible()).isFalse();
    }

    @Test
    void aValueThatDoesNotFitOrDoesNotParseIsSaidInOneErrorLineInTheErrorColour() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));

        typeSwitchValue(form, "0b1_0000_0000");
        assertThat(readingTexts(form)).containsExactly("No cabe en 8 bits");
        assertThat(form.valueReading().getComponent(0).getForeground()).isEqualTo(ReadableColors.errorText());

        typeSwitchValue(form, "hola");
        assertThat(readingTexts(form)).containsExactly("Valor no válido (usa 0b…, 0x… o un número)");
        assertThat(form.valueReading().getComponent(0).getForeground()).isEqualTo(ReadableColors.errorText());
    }

    @Test
    void aOneBitSwitchHasNoReadingAndChangingTheWidthShowsOrHidesIt() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of());
        assertThat(form.valueReading().isVisible()).isFalse();

        setWidth(form, 4);
        typeSwitchValue(form, "5");
        assertThat(form.valueReading().isVisible()).isTrue();
        assertThat(readingTexts(form)).containsExactly("binario", "0101", "hexa", "0x5", "decimal", "5");

        setWidth(form, 3);
        assertThat(readingTexts(form)).containsExactly("binario", "101", "hexa", "0x5", "decimal", "5");

        setWidth(form, 2);
        assertThat(readingTexts(form)).containsExactly("No cabe en 2 bits");

        setWidth(form, 1);
        assertThat(form.valueReading().isVisible()).isFalse();
    }

    @Test
    void atATypicalPanelWidthNoReadingIsWiderThanTheField() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));
        layOutAt(240, form);
        int fieldWidth = form.editor("value").getWidth();
        assertThat(fieldWidth).isPositive();

        for (String text : List.of("0xFF", "1", "hola", "0b1_0000_0000")) {
            typeSwitchValue(form, text);
            layOutAt(240, form);
            for (java.awt.Component component : form.valueReading().getComponents()) {
                assertThat(component.getPreferredSize().width)
                        .as("«%s» in «%s»", ((JLabel) component).getText(), text).isLessThanOrEqualTo(fieldWidth);
            }
        }
    }

    @Test
    void aThirtyTwoBitBinaryWrapsToFitTheFieldInTwoLinesOf16BitsOrFourOf8() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 32));
        typeSwitchValue(form, "0x12345678");

        layOutAt(290, form);
        assertThat(readingTexts(form)).startsWith("binario", "0001 0010 0011 0100", "", "0101 0110 0111 1000",
                "hexa", "0x12345678");
        assertNoReadingWiderThanTheField(form);

        layOutAt(240, form);
        assertThat(readingTexts(form)).startsWith("binario", "0001 0010", "", "0011 0100", "", "0101 0110", "",
                "0111 1000", "hexa", "0x12345678");
        assertNoReadingWiderThanTheField(form);
    }

    private static void assertNoReadingWiderThanTheField(ParameterForm form) {
        int fieldWidth = form.editor("value").getWidth();
        for (java.awt.Component component : form.valueReading().getComponents()) {
            assertThat(component.getPreferredSize().width).isLessThanOrEqualTo(fieldWidth);
        }
    }

    @Test
    void aLongErrorWrapsAtTheFieldWidthInsteadOfBeingCut() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));
        layOutAt(240, form);

        typeSwitchValue(form, "hola");
        layOutAt(240, form);

        assertThat(readingTexts(form).size()).isGreaterThan(1);
        assertThat(String.join(" ", readingTexts(form))).isEqualTo("Valor no válido (usa 0b…, 0x… o un número)");
        assertNoReadingWiderThanTheField(form);
    }

    @Test
    void theReadingRowFollowsTheValueRowInAHostGrid() {
        ParameterForm form = form(StandardComponentTypes.SWITCH, Map.of("width", 8));
        JPanel host = new JPanel();

        form.addRowsTo(host);

        int valueIndex = List.of(host.getComponents()).indexOf(form.editor("value"));
        assertThat(host.getComponent(valueIndex + 2)).isSameAs(form.valueReading());
    }

    @Test
    void anotherComponentHasNoValueReading() {
        assertThat(form(StandardComponentTypes.CLOCK, Map.of()).valueReading()).isNull();
    }
}
