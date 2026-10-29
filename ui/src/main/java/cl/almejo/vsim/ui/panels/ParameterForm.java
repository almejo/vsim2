package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.SwitchValues;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.LedPainter;
import cl.almejo.vsim.ui.tunnel.TunnelColorSwatches;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.Set;

/**
 * One labelled editor per parameter of a component type. It lays them out itself (the configure dialog) or hands
 * them to a host's two column grid with {@link #addRowsTo(JPanel)} (the Propiedades tab).
 */
public final class ParameterForm extends JPanel {

    /** Parameters measured in simulation time units (M6.1-E): «Retardo (t)», with a tooltip saying so. */
    static final Set<String> TIME_PARAMETERS = Set.of("delay", "time-up", "time-down", "sample-interval");

    static final String SWITCH_VALUE = "value";
    private static final List<String> SWITCH_VALUE_CHOICES = List.of("1", "0", "Z");

    /** FlatLaf's client property that paints a field's border as an error. */
    private static final String OUTLINE_PROPERTY = "JComponent.outline";

    private final ComponentType type;
    private final Map<String, Object> initial;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private final Map<String, JLabel> labels = new LinkedHashMap<>();
    /** The block under a multi-bit Switch's «Valor» (null for any other type); see {@link SwitchValueReading}. */
    private SwitchValueReading valueReading;

    public ParameterForm(ComponentType type, Map<String, Object> currentParameters) {
        this(type, currentParameters, TunnelColorSwatches.THEME_SCHEME);
    }

    /** {@code scheme} gives the colours the canvas paints with now (the swatches read it at every paint). */
    public ParameterForm(ComponentType type, Map<String, Object> currentParameters, Supplier<ColorScheme> scheme) {
        super(new MigLayout("wrap 2, insets dialog", "[right][grow, fill]"));
        this.type = type;
        this.initial = type.resolveParameters(currentParameters);
        for (ParameterSpec parameter : type.parameters()) {
            if (parameter.hidden()) {
                continue;
            }
            JComponent editor = editorFor(type, parameter, initial.get(parameter.name()), scheme);
            editor.setName(parameter.name());
            editors.put(parameter.name(), editor);
            JLabel label = new JLabel(labelText(parameter.name()));
            label.setLabelFor(editor);
            labels.put(parameter.name(), label);
            if (TIME_PARAMETERS.contains(parameter.name())) {
                String tip = Messages.text("form.timeUnitTip");
                label.setToolTipText(tip);
                editor.setToolTipText(tip);
                if (editor instanceof JSpinner spinner) {
                    spinnerField(spinner).setToolTipText(tip);
                }
            }
            add(label, rowConstraints(type, parameter, ""));
            add(editor, rowConstraints(type, parameter, "width 160::"));
            if (isSwitchValue(type, parameter)) {
                valueReading = new SwitchValueReading(type, initial, (JComboBox<?>) editor,
                        (JSpinner) editors.get(StandardComponentTypes.WIDTH),
                        (JComboBox<?>) editors.get(StandardComponentTypes.SIGNEDNESS));
                valueReading.addTo(this);
            }
        }
        if (editors.containsKey(StandardComponentTypes.SIGNEDNESS) && editors.containsKey(StandardComponentTypes.WIDTH)
                && type.id().equals(StandardComponentTypes.SWITCH)) {
            showNumbersOnlyWithManyBits();
            ((JComboBox<?>) editors.get(StandardComponentTypes.SIGNEDNESS))
                    .addActionListener(event -> showNegativeValueAsUnsigned());
        }
    }

    /** Back to «Sin signo», the «Valor» field shows a negative decimal as the bits it is ({@link #rawValues()}). */
    private void showNegativeValueAsUnsigned() {
        if (!(editors.get(SWITCH_VALUE) instanceof JComboBox<?> valueBox)) {
            return;
        }
        Object reconciled = rawValues().get(SWITCH_VALUE);
        if (!String.valueOf(valueBox.getEditor().getItem()).equals(reconciled)) {
            valueBox.getEditor().setItem(reconciled);
        }
    }

    /**
     * Review M-2: with «Números: Sin signo» a Switch's negative decimal «Valor» is read as its bits (on 8 bits, {@code -1}
     * is {@code 255}), so going back to «Sin signo» keeps the value instead of refusing it.
     */
    private void readNegativeValueAsUnsigned(Map<String, Object> values) {
        if (!type.id().equals(StandardComponentTypes.SWITCH)
                || !StandardComponentTypes.SIGNEDNESS_UNSIGNED.equals(values.get(StandardComponentTypes.SIGNEDNESS))
                || !(values.get(StandardComponentTypes.WIDTH) instanceof Integer width)
                || width < MultiBitLayout.MINIMUM_WIDTH || width > MultiBitLayout.MAXIMUM_WIDTH) {
            return;
        }
        SwitchValues.unsignedText(String.valueOf(values.get(SWITCH_VALUE)), width)
                .ifPresent(text -> values.put(SWITCH_VALUE, text));
    }

    /** The Switch's «Números» row disappears (taking no room) when it has one bit: there is nothing to sign. */
    private static String rowConstraints(ComponentType type, ParameterSpec parameter, String constraints) {
        if (type.id().equals(StandardComponentTypes.SWITCH)
                && parameter.name().equals(StandardComponentTypes.SIGNEDNESS)) {
            return constraints.isEmpty() ? "hidemode 3" : constraints + ", hidemode 3";
        }
        return constraints;
    }

    private void showNumbersOnlyWithManyBits() {
        JFormattedTextField widthField = spinnerField((JSpinner) editors.get(StandardComponentTypes.WIDTH));
        Runnable update = () -> {
            boolean many;
            try {
                many = Long.parseLong(widthField.getText().strip()) > 1;
            } catch (NumberFormatException exception) {
                many = true;
            }
            labels.get(StandardComponentTypes.SIGNEDNESS).setVisible(many);
            editors.get(StandardComponentTypes.SIGNEDNESS).setVisible(many);
        };
        widthField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update.run();
            }
        });
        widthField.addPropertyChangeListener("value", event -> update.run());
        update.run();
    }

    /** The block under a Switch's «Valor» saying how the number is read; null when the type has none. */
    JPanel valueReading() {
        return valueReading == null ? null : valueReading.block();
    }

    /**
     * Moves every caption and its editor into {@code host}, whose layout is a two column grid (caption, field) that
     * wraps after the field, so they share the host's columns with its own rows. The form keeps reading, validating
     * and listening to the editors; it is left empty and is not added anywhere.
     */
    public void addRowsTo(JPanel host) {
        labels.forEach((name, label) -> {
            String constraints = name.equals(StandardComponentTypes.SIGNEDNESS) ? "hidemode 3" : "";
            host.add(label, constraints);
            host.add(editors.get(name), constraints);
            if (valueReading != null && name.equals(SWITCH_VALUE)) {
                valueReading.addTo(host);
            }
        });
    }

    /** The label of a parameter: its name, plus « (t)» for a time parameter. Error messages use the plain name. */
    static String labelText(String parameterName) {
        String name = Messages.parameterName(parameterName);
        return TIME_PARAMETERS.contains(parameterName) ? Messages.format("form.timeUnit", name) : name;
    }

    JComponent editor(String parameterName) {
        return editors.get(parameterName);
    }

    /** Current editor contents (hidden parameters keep their value); invalid spinner text is returned as the typed string so validation can report it. */
    public Map<String, Object> rawValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ParameterSpec parameter : type.parameters()) {
            JComponent editor = editors.get(parameter.name());
            // a hidden parameter (a memory's contents) has no field: it goes through as it was
            values.put(parameter.name(), editor == null ? initial.get(parameter.name()) : valueOf(editor));
        }
        readNegativeValueAsUnsigned(values);
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
            JComponent editor = editors.get(parameter.name());
            if (parameter.kind() != ParameterKind.TEXT || editor == null) {
                continue;
            }
            String typed = String.valueOf(valueOf(editor));
            if (!readable(parameter, typed)) {
                continue;
            }
            String normalized = parameter.normalize(typed).toString();
            if (normalized.equals(typed)) {
                continue;
            }
            if (editor instanceof JTextField field) {
                field.setText(normalized);
            } else if (editor instanceof JComboBox<?> box) {
                box.getEditor().setItem(normalized);
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
                case JComboBox<?> box -> {
                    box.addActionListener(event -> listener.run());
                    if (box.isEditable()) {
                        box.getEditor().getEditorComponent().addFocusListener(onFocusLost);
                    }
                }
                case JCheckBox checkBox -> checkBox.addActionListener(event -> listener.run());
                case ColorSwatches<?> swatches -> swatches.addChoiceListener(listener);
                default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
            }
        }
    }

    private static JFormattedTextField spinnerField(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }

    /**
     * A tunnel entrada's palette colour (an integer, spec §5) and every {@code COLOR} parameter are picked from
     * swatches; every other parameter by its kind.
     */
    private static JComponent editorFor(ComponentType type, ParameterSpec parameter, Object value,
                                        Supplier<ColorScheme> scheme) {
        if (type.id().equals(StandardComponentTypes.TUNNEL_ENTRANCE) && parameter.name().equals(Tunnels.COLOR)) {
            return TunnelColorSwatches.create((Integer) value, scheme);
        }
        return switch (parameter.kind()) {
            case INTEGER -> {
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(((Integer) value).intValue(),
                        parameter.minimum(), parameter.maximum(), 1));
                spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
                spinnerField(spinner).setFocusLostBehavior(JFormattedTextField.COMMIT);
                FragmentRefusingTransferHandler.install(spinnerField(spinner));
                yield spinner;
            }
            case BOOLEAN -> new JCheckBox("", (Boolean) value);
            case TEXT -> isSwitchValue(type, parameter) ? switchValueBox(value.toString())
                    : FragmentRefusingTransferHandler.install(new JTextField(value.toString(), 16));
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
            // the LED's colour names are the colour vocabulary of COLOR parameters (it is the only one today)
            case COLOR -> new ColorSwatches<>(parameter.choices(), (String) value,
                    colorName -> Messages.choiceName(parameter.name(), colorName),
                    colorName -> Optional.of(LedPainter.colorFor(colorName)));
        };
    }

    /**
     * The Switch's «Valor»: a one-bit Switch is set to 1, 0 or Z from the list, as always; with more bits the same box
     * takes the number typed in it, so it keeps working when the width changes in the same form.
     */
    private static boolean isSwitchValue(ComponentType type, ParameterSpec parameter) {
        return type.id().equals(StandardComponentTypes.SWITCH) && parameter.name().equals(SWITCH_VALUE);
    }

    private static JComboBox<String> switchValueBox(String value) {
        JComboBox<String> box = new JComboBox<>(SWITCH_VALUE_CHOICES.toArray(String[]::new));
        box.setEditable(true);
        box.setSelectedItem(value);
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object item, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, item, index, selected, focused);
                setText(Messages.choiceName(SWITCH_VALUE, String.valueOf(item)));
                return this;
            }
        });
        FragmentRefusingTransferHandler.install((JTextField) box.getEditor().getEditorComponent());
        return box;
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
            case JComboBox<?> box -> box.isEditable() ? box.getEditor().getItem() : box.getSelectedItem();
            case JCheckBox checkBox -> checkBox.isSelected();
            case ColorSwatches<?> swatches -> swatches.selectedValue();
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        };
    }
}
