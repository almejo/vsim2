package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
            Object value = values.get(parameter.name());
            if (value == null) {
                return Optional.of(invalidMessage(parameter));
            }
            try {
                parameter.normalize(value);
            } catch (IllegalArgumentException exception) {
                return Optional.of(invalidMessage(parameter));
            }
        }
        return Optional.empty();
    }

    private static String invalidMessage(ParameterSpec parameter) {
        String name = Messages.parameterName(parameter.name());
        return parameter.kind() == ParameterKind.INTEGER
                ? Messages.format("dialog.invalidInteger", name, parameter.minimum(), parameter.maximum())
                : Messages.format("dialog.invalidValue", name);
    }

    /** True when the (valid) values differ from the component's current ones. */
    public boolean hasChanges() {
        return !type.resolveParameters(rawValues()).equals(initial);
    }

    /**
     * Calls {@code listener} whenever the user finishes an edit: a spinner value change, Enter or focus loss in a
     * field, or a choice. Callers that apply edits immediately (the Properties tab) use it; the dialog does not.
     */
    public void addCommitListener(Runnable listener) {
        FocusAdapter onFocusLost = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                listener.run();
            }
        };
        for (JComponent editor : editors.values()) {
            switch (editor) {
                case JSpinner spinner -> {
                    JFormattedTextField field = spinnerField(spinner);
                    // "value" fires after the editor has synced its text; a spinner ChangeListener may run before.
                    field.addPropertyChangeListener("value", event -> listener.run());
                    field.addFocusListener(onFocusLost);
                    field.addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyPressed(KeyEvent event) {
                            if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                                listener.run();
                            }
                        }
                    });
                }
                case JTextField field -> {
                    field.addActionListener(event -> listener.run());
                    field.addFocusListener(onFocusLost);
                }
                case JComboBox<?> box -> box.addActionListener(event -> listener.run());
                default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
            }
        }
    }

    private static JFormattedTextField spinnerField(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }

    private static JComponent editorFor(ParameterSpec parameter, Object value) {
        return switch (parameter.kind()) {
            case INTEGER -> {
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(((Integer) value).intValue(),
                        parameter.minimum(), parameter.maximum(), 1));
                spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
                spinnerField(spinner).setFocusLostBehavior(JFormattedTextField.COMMIT);
                FragmentRefusingTransferHandler.install(spinnerField(spinner));
                yield spinner;
            }
            case TEXT -> FragmentRefusingTransferHandler.install(new JTextField(value.toString(), 16));
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
                    yield spinnerField(spinner).getText();
                }
            }
            case JTextField field -> field.getText();
            case JComboBox<?> box -> box.getSelectedItem();
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        };
    }
}
