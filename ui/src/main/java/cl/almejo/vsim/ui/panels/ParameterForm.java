package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterProblem;
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
import java.util.Set;

/** One labelled editor per parameter of a component type. */
public final class ParameterForm extends JPanel {

    /** Parameters measured in simulation time units (M6.1-E): «Retardo (t)», with a tooltip saying so. */
    static final Set<String> TIME_PARAMETERS = Set.of("delay", "time-up", "time-down", "sample-interval");

    /** FlatLaf's client property that paints a field's border as an error. */
    private static final String OUTLINE_PROPERTY = "JComponent.outline";

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
            JLabel label = new JLabel(labelText(parameter.name()));
            label.setLabelFor(editor);
            if (TIME_PARAMETERS.contains(parameter.name())) {
                String tip = Messages.text("form.timeUnitTip");
                label.setToolTipText(tip);
                editor.setToolTipText(tip);
                if (editor instanceof JSpinner spinner) {
                    spinnerField(spinner).setToolTipText(tip);
                }
            }
            add(label);
            add(editor, "width 160::");
        }
    }

    /** The label of a parameter: its name, plus « (t)» for a time parameter. Error messages use the plain name. */
    static String labelText(String parameterName) {
        String name = Messages.parameterName(parameterName);
        return TIME_PARAMETERS.contains(parameterName) ? Messages.format("form.timeUnit", name) : name;
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

    /**
     * What is wrong with the current contents, if anything: a parameter that cannot be read, or a problem the type's own
     * check finds across parameters (a constant that does not fit its width). The editor at fault is marked as an error.
     */
    public Optional<String> validationError() {
        editors.values().forEach(editor -> editor.putClientProperty(OUTLINE_PROPERTY, null));
        Map<String, Object> values = rawValues();
        for (ParameterSpec parameter : type.parameters()) {
            if (parameter.kind() != ParameterKind.TEXT && !readable(parameter, values.get(parameter.name()))) {
                return Optional.of(refuse(parameter.name(), invalidMessage(parameter)));
            }
        }
        Optional<ParameterProblem> problem = type.check(values);
        if (problem.isPresent()) {
            return Optional.of(refuse(problem.get().parameterName(),
                    Messages.format(problem.get().messageKey(), problem.get().arguments().toArray())));
        }
        for (ParameterSpec parameter : type.parameters()) {
            if (parameter.kind() == ParameterKind.TEXT && !readable(parameter, values.get(parameter.name()))) {
                return Optional.of(refuse(parameter.name(), invalidMessage(parameter)));
            }
        }
        return Optional.empty();
    }

    private String refuse(String parameterName, String message) {
        JComponent editor = editors.get(parameterName);
        if (editor != null) {
            editor.putClientProperty(OUTLINE_PROPERTY, "error");
        }
        return message;
    }

    private static boolean readable(ParameterSpec parameter, Object value) {
        if (value == null) {
            return false;
        }
        try {
            parameter.normalize(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Rewrites each text editor with its normalized text ({@code 0x00ff} becomes {@code 0xFF}); unreadable text stays. */
    public void showNormalizedText() {
        for (ParameterSpec parameter : type.parameters()) {
            if (parameter.kind() == ParameterKind.TEXT && editors.get(parameter.name()) instanceof JTextField field
                    && readable(parameter, field.getText())) {
                String normalized = parameter.normalize(field.getText()).toString();
                if (!normalized.equals(field.getText())) {
                    field.setText(normalized);
                }
            }
        }
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
