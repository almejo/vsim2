package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The line under a multi-bit Switch's «Valor» that says how VSim reads the number («= 0110 0100 en binario · 0x64 ·
 * 100 en decimal»), or why it cannot («No cabe en 8 bits»), updated on every keystroke and whenever the width
 * changes. With one bit (the 1/0/Z list) there is no line. The colours are asked at every paint.
 */
final class SwitchValueReading {

    private final ComponentType type;
    private final Map<String, Object> initial;
    private final JComboBox<?> valueBox;
    private final JSpinner widthSpinner;
    private final JLabel caption = new JLabel();
    private final JLabel label = new JLabel() {
        @Override
        public Color getForeground() {
            return problem ? ReadableColors.errorText() : ReadableColors.mutedText();
        }
    };
    private boolean problem;

    SwitchValueReading(ComponentType type, Map<String, Object> initial, JComboBox<?> valueBox, JSpinner widthSpinner) {
        this.type = type;
        this.initial = initial;
        this.valueBox = valueBox;
        this.widthSpinner = widthSpinner;
        label.setName("value-reading");
        DocumentListener refreshOnEdit = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refresh();
            }
        };
        ((JTextComponent) valueBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(refreshOnEdit);
        valueBox.addActionListener(event -> refresh());
        JFormattedTextField widthField = ((JSpinner.DefaultEditor) widthSpinner.getEditor()).getTextField();
        widthField.getDocument().addDocumentListener(refreshOnEdit);
        widthField.addPropertyChangeListener("value", event -> refresh());
        refresh();
    }

    JLabel label() {
        return label;
    }

    /** Adds the empty caption cell and the line as one more row of a two column grid; hidden rows take no room. */
    void addTo(JPanel host) {
        host.add(caption, "hidemode 3");
        host.add(label, "hidemode 3");
    }

    private void refresh() {
        OptionalLong width = width();
        boolean wide = width.isPresent() && width.getAsLong() > 1;
        caption.setVisible(wide);
        label.setVisible(wide);
        if (!wide) {
            return;
        }
        String text = String.valueOf(valueBox.getEditor().getItem()).strip();
        Optional<ParameterProblem> found = problemWith((int) width.getAsLong(), text);
        problem = found.isPresent();
        label.setText(found.map(this::problemText).orElseGet(() -> reading(text, (int) width.getAsLong())));
    }

    /** The width as typed now (the spinner is not committed on every keystroke); empty when it is not a number. */
    private OptionalLong width() {
        JFormattedTextField field = ((JSpinner.DefaultEditor) widthSpinner.getEditor()).getTextField();
        try {
            return OptionalLong.of(Long.parseLong(field.getText().strip()));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private Optional<ParameterProblem> problemWith(int width, String text) {
        Map<String, Object> values = new LinkedHashMap<>(initial);
        values.put(StandardComponentTypes.WIDTH, width);
        values.put(ParameterForm.SWITCH_VALUE, text);
        return type.check(values);
    }

    private String problemText(ParameterProblem found) {
        return Messages.format(found.messageKey(), found.arguments().toArray());
    }

    /** Binary as the multi-bit Switch draws it, hex, unsigned decimal and, with the top bit on, the signed one. */
    private static String reading(String text, int width) {
        long bits = ConstantValue.bitsOf(text, width).orElseThrow();
        String binary = BusValueText.binary(Value.of(width, bits));
        String hex = ConstantValue.display(bits, width, ConstantValue.HEX);
        String decimal = ConstantValue.display(bits, width, ConstantValue.DECIMAL);
        boolean topBitOn = (bits >>> (width - 1) & 1L) == 1L;
        return topBitOn
                ? Messages.format("switch.valueReadingSigned", binary, hex, decimal,
                        ConstantValue.display(bits, width, ConstantValue.SIGNED))
                : Messages.format("switch.valueReading", binary, hex, decimal);
    }
}
