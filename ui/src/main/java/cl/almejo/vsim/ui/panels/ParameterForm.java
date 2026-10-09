package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** One labelled editor per parameter of a component type. */
public final class ParameterForm extends JPanel {

    private final ComponentType type;
    private final Map<String, Object> initial;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();

    public ParameterForm(ComponentType type, Map<String, Object> currentParameters) {
        super(new MigLayout("wrap 2, insets dialog", "[right][grow, fill]"));
        this.type = type;
        this.initial = type.resolveParameters(currentParameters);
        for (ParameterSpec parameter : type.parameters()) {
            JComponent editor = editorFor(parameter, initial.get(parameter.name()));
            editor.setName(parameter.name());
            editors.put(parameter.name(), editor);
            JLabel label = new JLabel(Messages.parameterName(parameter.name()));
            label.setLabelFor(editor);
            add(label);
            add(editor, "width 160::");
        }
    }

    JComponent editor(String parameterName) {
        return editors.get(parameterName);
    }

    /** Current editor contents; invalid spinner text is returned as the typed string so validation can report it. */
    public Map<String, Object> rawValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        editors.forEach((name, editor) -> values.put(name, valueOf(editor)));
        return values;
    }

    public Optional<String> validationError() {
        Map<String, Object> values = rawValues();
        for (ParameterSpec parameter : type.parameters()) {
            try {
                parameter.normalize(values.get(parameter.name()));
            } catch (IllegalArgumentException | NullPointerException exception) {
                String name = Messages.parameterName(parameter.name());
                return Optional.of(parameter.kind() == ParameterKind.INTEGER
                        ? Messages.format("dialog.invalidInteger", name, parameter.minimum(), parameter.maximum())
                        : Messages.format("dialog.invalidValue", name));
            }
        }
        return Optional.empty();
    }

    /** True when the (valid) values differ from the component's current ones. */
    public boolean hasChanges() {
        return !type.resolveParameters(rawValues()).equals(initial);
    }

    private static JComponent editorFor(ParameterSpec parameter, Object value) {
        return switch (parameter.kind()) {
            case INTEGER -> {
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(((Integer) value).intValue(),
                        parameter.minimum(), parameter.maximum(), 1));
                spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
                yield spinner;
            }
            case TEXT -> new JTextField(value.toString(), 16);
            case CHOICE -> {
                JComboBox<String> box = new JComboBox<>(parameter.choices().toArray(String[]::new));
                box.setSelectedItem(value);
                box.setRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList<?> list, Object item, int index,
                                                                  boolean selected, boolean focused) {
                        super.getListCellRendererComponent(list, item, index, selected, focused);
                        setText(Messages.choiceName(parameter.name(), String.valueOf(item)));
                        return this;
                    }
                });
                yield box;
            }
        };
    }

    private static Object valueOf(JComponent editor) {
        return switch (editor) {
            case JSpinner spinner -> {
                try {
                    spinner.commitEdit();
                    yield spinner.getValue();
                } catch (ParseException exception) {
                    yield ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
                }
            }
            case JTextField field -> field.getText();
            case JComboBox<?> box -> box.getSelectedItem();
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        };
    }
}
