package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.SwitchValues;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.LedPainter;
import cl.almejo.vsim.ui.tunnel.TunnelColorSwatches;
import cl.almejo.vsim.ui.paint.KeyboardPainter;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One labelled editor per parameter of a component type. It lays them out itself (the configure dialog) or hands
 * them to a host's two column grid with {@link #addRowsTo(JPanel)} (the Propiedades tab).
 *
 * <p>Parameters in {@linkplain ParameterSpec#group() groups} come under a header per group, the groups in the order of
 * their first parameter. A row with a {@linkplain ParameterSpec#visibility() condition} shows only while it holds. A
 * type with {@linkplain ComponentType#profiles() profiles} (spec §7) shows each value that differs from the chosen
 * profile's in bold with «●» and a «↺» that sets it back, says «(modificado)» in the profile box and counts the
 * differences under it with «↺ Volver al perfil». Choosing a profile, a «↺» and «Volver al perfil» each reach the
 * commit listeners once, with their own {@link #commitDescription()}.
 */
public final class ParameterForm extends JPanel {

    /** Parameters measured in simulation time units (M6.1-E): «Retardo (t)», with a tooltip saying so. */
    static final Set<String> TIME_PARAMETERS = Set.of("delay", "time-up", "time-down", "sample-interval");

    static final String SWITCH_VALUE = "value";
    private static final List<String> SWITCH_VALUE_CHOICES = List.of("1", "0", "Z");

    /** What a plain edit is called in «Deshacer …». */
    static final String CONFIGURE = "command.configure";
    /** What choosing a profile, or «Volver al perfil», is called in «Deshacer …». */
    static final String APPLY_PROFILE = "command.applyProfile";
    /** What one «↺» is called in «Deshacer …». */
    static final String RESTORE_PARAMETER = "command.restoreParameter";

    /** FlatLaf's client property that paints a field's border as an error. */
    private static final String OUTLINE_PROPERTY = "JComponent.outline";
    private static final String BORDERLESS_BUTTON = "borderless";
    private static final String BUTTON_TYPE_PROPERTY = "JButton.buttonType";
    /** The lines a multi-line text shows before it scrolls. */
    private static final int MULTILINE_ROWS = 3;
    /** The width of a text field, in columns. */
    private static final int TEXT_COLUMNS = 16;
    /** A row that may hide takes no room while hidden. */
    private static final String HIDE_MODE = "hidemode 3";
    /** A group's header spans both columns, a little apart from the rows above. */
    private static final String HEADER_CONSTRAINTS = "span 2, gaptop 6, hidemode 3";

    private final ComponentType type;
    private final Map<String, Object> initial;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private final Map<String, JLabel> labels = new LinkedHashMap<>();
    /** What each row puts in the field column: the editor, its scroll pane, or either with a «↺» after it. */
    private final Map<String, JComponent> cells = new LinkedHashMap<>();
    /** The «↺» of each parameter some profile sets. */
    private final Map<String, JButton> restoreButtons = new LinkedHashMap<>();
    private final Map<String, JLabel> groupHeaders = new LinkedHashMap<>();
    /** The rows in the order they are laid out. */
    private final List<Row> rows = new ArrayList<>();
    private final List<Runnable> commitListeners = new ArrayList<>();
    /** The block under a multi-bit Switch's «Valor» (null for any other type); see {@link SwitchValueReading}. */
    private SwitchValueReading valueReading;
    /** The line under the profile box (null for a type without profiles). */
    private ProfileSummary profileSummary;
    /** The parameters whose value is not the chosen profile's, as the editors hold them now. */
    private Set<String> differences = Set.of();
    /** True while the form sets editors itself (a profile, a «↺»): their own commits wait for one at the end. */
    private boolean updatingEditors;
    private String commitDescription = CONFIGURE;

    /** One row of the form: a group's header, a parameter, or the profile's summary. */
    private sealed interface Row {
    }

    private record HeaderRow(String group) implements Row {
    }

    private record ParameterRow(ParameterSpec parameter) implements Row {
    }

    private record SummaryRow() implements Row {
    }

    /** «2 propiedades distintas del perfil» and «↺ Volver al perfil», or «Igual al perfil», under the profile box. */
    private record ProfileSummary(JLabel caption, JPanel block, JLabel text, JButton restoreAll) {
    }

    public ParameterForm(ComponentType type, Map<String, Object> currentParameters) {
        this(type, currentParameters, TunnelColorSwatches.THEME_SCHEME);
    }

    /** {@code scheme} gives the colours the canvas paints with now (the swatches read it at every paint). */
    public ParameterForm(ComponentType type, Map<String, Object> currentParameters, Supplier<ColorScheme> scheme) {
        super(new MigLayout("wrap 2, insets dialog", "[right][grow, fill]"));
        this.type = type;
        this.initial = type.resolveParameters(currentParameters);
        planRows();
        for (Row row : rows) {
            if (row instanceof ParameterRow(ParameterSpec parameter)) {
                buildRow(parameter, scheme);
            }
        }
        if (editors.get(SWITCH_VALUE) instanceof JComboBox<?> valueBox && isSwitchValue(type, SWITCH_VALUE)) {
            valueReading = new SwitchValueReading(type, initial, valueBox,
                    (JSpinner) editors.get(StandardComponentTypes.WIDTH),
                    (JComboBox<?>) editors.get(StandardComponentTypes.SIGNEDNESS));
        }
        type.profileParameter().ifPresent(this::buildProfileChooser);
        layOut(this, "width 160::");
        if (editors.containsKey(StandardComponentTypes.SIGNEDNESS) && editors.containsKey(StandardComponentTypes.WIDTH)
                && type.id().equals(StandardComponentTypes.SWITCH)) {
            showNumbersOnlyWithManyBits();
            ((JComboBox<?>) editors.get(StandardComponentTypes.SIGNEDNESS))
                    .addActionListener(event -> showNegativeValueAsUnsigned());
        }
        editors.values().forEach(editor -> onEveryChange(editor, this::refreshPresentation));
        refreshPresentation();
    }

    /**
     * The rows in order: the parameters of each group together under its header, the groups (and the parameters of
     * no group) in the order of their first parameter; the profile's summary right after the profile row.
     */
    private void planRows() {
        Map<Optional<String>, List<ParameterSpec>> byGroup = new LinkedHashMap<>();
        for (ParameterSpec parameter : type.parameters()) {
            if (!parameter.hidden()) {
                byGroup.computeIfAbsent(parameter.group(), group -> new ArrayList<>()).add(parameter);
            }
        }
        byGroup.forEach((group, parameters) -> {
            group.ifPresent(key -> rows.add(new HeaderRow(key)));
            for (ParameterSpec parameter : parameters) {
                rows.add(new ParameterRow(parameter));
                if (type.profileParameter().filter(parameter.name()::equals).isPresent()) {
                    rows.add(new SummaryRow());
                }
            }
        });
    }

    private void buildRow(ParameterSpec parameter, Supplier<ColorScheme> scheme) {
        parameter.group().ifPresent(group -> groupHeaders.computeIfAbsent(group, ParameterForm::groupHeaderFor));
        JComponent editor = editorFor(type, parameter, initial.get(parameter.name()), scheme);
        editor.setName(parameter.name());
        editors.put(parameter.name(), editor);
        JLabel label = new JLabel(labelText(parameter.name()), ParameterIcons.labelIcon(parameter.name()),
                JLabel.LEADING);
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
        if (parameter.multiline()) {
            editor.setToolTipText(Messages.text("form.multilineTip"));
        }
        JComponent field = parameter.multiline() ? multilineBox(editor) : editor;
        cells.put(parameter.name(), isSetByAProfile(parameter) ? withRestoreButton(parameter, field) : field);
    }

    /**
     * A scroll pane for a multi-line text. Its height never goes below the rows it shows: when the inspector is
     * shorter than the form, the form scrolls as a whole, and this box (minimum: one scroll bar) must not collapse to a
     * sliver, hiding the text being typed.
     */
    private static JScrollPane multilineBox(JComponent editor) {
        JScrollPane box = new JScrollPane(editor);
        box.setMinimumSize(new Dimension(box.getMinimumSize().width, box.getPreferredSize().height));
        return box;
    }

    private static JLabel groupHeaderFor(String group) {
        JLabel header = new JLabel(Messages.groupName(group));
        header.putClientProperty("FlatLaf.style", "font: bold");
        return header;
    }

    private boolean isSetByAProfile(ParameterSpec parameter) {
        return type.profiles().stream().anyMatch(profile -> profile.values().containsKey(parameter.name()));
    }

    /** The field with a «↺» after it, shown while the value is not the profile's. */
    private JComponent withRestoreButton(ParameterSpec parameter, JComponent field) {
        JButton restore = new JButton(Messages.text("profile.restore"));
        restore.putClientProperty(BUTTON_TYPE_PROPERTY, BORDERLESS_BUTTON);
        restore.setName("restore-" + parameter.name());
        restore.addActionListener(event -> restore(parameter.name()));
        restoreButtons.put(parameter.name(), restore);
        JPanel cell = new JPanel(new MigLayout("insets 0, gap 4", "[grow, fill][]"));
        cell.setOpaque(false);
        cell.add(field, "wmin 0");
        cell.add(restore, HIDE_MODE);
        return cell;
    }

    /** The profile box says «(modificado)» while some value differs; choosing one sets all its values. */
    private void buildProfileChooser(String chooserName) {
        if (!(editors.get(chooserName) instanceof JComboBox<?> chooser)) {
            return;
        }
        chooser.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object item, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, item, index, selected, focused);
                String name = Messages.choiceName(chooserName, String.valueOf(item));
                // the box itself (index -1) says whether the part still looks like its profile; the list does not
                setText(index < 0 && !differences.isEmpty() ? Messages.format("profile.modified", name) : name);
                return this;
            }
        });
        chooser.addActionListener(event -> {
            if (!updatingEditors && chooser.getSelectedItem() != null) {
                applyProfile(String.valueOf(chooser.getSelectedItem()));
            }
        });
        JLabel text = new JLabel();
        JButton restoreAll = new JButton(Messages.text("profile.restoreAll"));
        restoreAll.putClientProperty(BUTTON_TYPE_PROPERTY, BORDERLESS_BUTTON);
        restoreAll.addActionListener(event -> applyProfile(String.valueOf(chooser.getSelectedItem())));
        JPanel block = new JPanel(new MigLayout("insets 0, gap 6"));
        block.setOpaque(false);
        block.add(text);
        block.add(restoreAll, HIDE_MODE);
        profileSummary = new ProfileSummary(new JLabel(), block, text, restoreAll);
    }

    /** Puts every row into {@code host}, a two column grid; {@code fieldConstraints} sizes the field column. */
    private void layOut(JPanel host, String fieldConstraints) {
        for (Row row : rows) {
            switch (row) {
                case HeaderRow(String group) -> host.add(groupHeaders.get(group), HEADER_CONSTRAINTS);
                case ParameterRow(ParameterSpec parameter) -> layOutParameter(host, parameter, fieldConstraints);
                case SummaryRow() -> {
                    if (profileSummary != null) {
                        host.add(profileSummary.caption(), HIDE_MODE);
                        host.add(profileSummary.block(), "growx, wmin 0, " + HIDE_MODE);
                    }
                }
            }
        }
    }

    private void layOutParameter(JPanel host, ParameterSpec parameter, String fieldConstraints) {
        String constraints = mayHide(parameter) ? HIDE_MODE : "";
        host.add(labels.get(parameter.name()), constraints);
        host.add(cells.get(parameter.name()), joined(constraints, fieldConstraints));
        if (valueReading != null && parameter.name().equals(SWITCH_VALUE)) {
            valueReading.addTo(host);
        }
    }

    private static String joined(String first, String second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first + second;
        }
        return first + ", " + second;
    }

    /** The Switch's «Números» row and every row with a condition can hide, taking no room then. */
    private boolean mayHide(ParameterSpec parameter) {
        return parameter.visibility().isPresent() || type.id().equals(StandardComponentTypes.SWITCH)
                && parameter.name().equals(StandardComponentTypes.SIGNEDNESS);
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
            cells.get(StandardComponentTypes.SIGNEDNESS).setVisible(many);
        };
        widthField.getDocument().addDocumentListener(documentListener(update));
        widthField.addPropertyChangeListener("value", event -> update.run());
        update.run();
    }

    private static DocumentListener documentListener(Runnable update) {
        return new DocumentListener() {
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
        };
    }

    /** Runs {@code action} whenever {@code editor} shows another value, typed or set. */
    private static void onEveryChange(JComponent editor, Runnable action) {
        switch (editor) {
            case JSpinner spinner -> spinner.addChangeListener(event -> action.run());
            case JTextComponent text -> text.getDocument().addDocumentListener(documentListener(action));
            case JComboBox<?> box -> box.addActionListener(event -> action.run());
            case JCheckBox checkBox -> checkBox.addActionListener(event -> action.run());
            case ColorSwatches<?> swatches -> swatches.addChoiceListener(action);
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        }
    }

    /** Shows the rows whose condition holds and marks the values that differ from the profile, as the form is now. */
    private void refreshPresentation() {
        Map<String, Object> values = presentationValues();
        showRowsWhoseConditionHolds(values);
        if (type.profileParameter().isEmpty()) {
            return;
        }
        differences = ParameterProfiles.differences(type, values);
        labels.forEach((name, label) -> markDifference(name, label, differences.contains(name)));
        restoreButtons.forEach((name, button) -> {
            button.setVisible(differences.contains(name));
            button.setToolTipText(restoreTip(name, values));
        });
        if (profileSummary != null) {
            profileSummary.text().setText(differences.isEmpty() ? Messages.text("profile.same")
                    : Messages.format("profile.differences", differences.size()));
            profileSummary.restoreAll().setVisible(!differences.isEmpty());
        }
        type.profileParameter().map(editors::get).ifPresent(JComponent::repaint);
    }

    private void showRowsWhoseConditionHolds(Map<String, Object> values) {
        Map<String, Boolean> groupShown = new LinkedHashMap<>();
        for (Row row : rows) {
            if (!(row instanceof ParameterRow(ParameterSpec parameter))) {
                continue;
            }
            boolean shown = type.isShown(parameter, values);
            if (parameter.visibility().isPresent()) {
                labels.get(parameter.name()).setVisible(shown);
                cells.get(parameter.name()).setVisible(shown);
            }
            parameter.group().ifPresent(group -> groupShown.merge(group, shown, Boolean::logicalOr));
        }
        groupShown.forEach((group, shown) -> groupHeaders.get(group).setVisible(shown));
    }

    private static void markDifference(String name, JLabel label, boolean different) {
        String text = labelText(name);
        label.setText(different ? Messages.format("profile.changedLabel", text) : text);
        Font plain = UIManager.getFont("Label.font");
        if (plain == null) {
            plain = label.getFont();
        }
        label.setFont(different ? plain.deriveFont(Font.BOLD) : plain.deriveFont(Font.PLAIN));
    }

    /** «Volver al valor del perfil: Subida»: the chosen profile's value of {@code name}, as the user reads it. */
    private String restoreTip(String name, Map<String, Object> values) {
        Optional<Object> profileValue = ParameterProfiles.chosen(type, values)
                .map(profile -> profile.values().get(name));
        Optional<ParameterSpec> parameter = type.parameterNamed(name);
        if (profileValue.isEmpty() || parameter.isEmpty()) {
            return null;
        }
        return Messages.format("profile.restoreTip", displayedValue(parameter.get(), profileValue.get()));
    }

    private static String displayedValue(ParameterSpec parameter, Object value) {
        return switch (parameter.kind()) {
            case CHOICE, COLOR -> Messages.choiceName(parameter.name(), String.valueOf(value));
            case BOOLEAN -> Messages.text(Boolean.TRUE.equals(value) ? "profile.yes" : "profile.no");
            case INTEGER, TEXT -> String.valueOf(value);
        };
    }

    /** Sets every value profile {@code profileName} lists and the profile itself, then commits once. */
    private void applyProfile(String profileName) {
        Map<String, Object> applied = ParameterProfiles.apply(type, presentationValues(), profileName);
        type.profile(profileName).ifPresent(profile -> setEditors(applied, profile.values().keySet()));
        type.profileParameter().ifPresent(chooser -> setEditors(applied, Set.of(chooser)));
        refreshPresentation();
        fireCommit(APPLY_PROFILE);
    }

    /** «↺»: sets {@code parameterName} back to the chosen profile's value, then commits once. */
    private void restore(String parameterName) {
        Map<String, Object> restored = ParameterProfiles.restore(type, presentationValues(), parameterName);
        setEditors(restored, Set.of(parameterName));
        refreshPresentation();
        fireCommit(RESTORE_PARAMETER);
    }

    private void setEditors(Map<String, Object> values, Set<String> names) {
        updatingEditors = true;
        try {
            for (String name : names) {
                JComponent editor = editors.get(name);
                if (editor != null) {
                    show(editor, values.get(name));
                }
            }
        } finally {
            updatingEditors = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void show(JComponent editor, Object value) {
        switch (editor) {
            case JSpinner spinner -> spinner.setValue(value);
            case JTextComponent text -> text.setText(String.valueOf(value));
            case JComboBox<?> box -> {
                if (box.isEditable()) {
                    box.getEditor().setItem(value);
                }
                box.setSelectedItem(value);
            }
            case JCheckBox checkBox -> checkBox.setSelected(Boolean.TRUE.equals(value));
            case ColorSwatches<?> swatches -> ((ColorSwatches<Object>) swatches).select(value);
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        }
    }

    private void fireCommit(String description) {
        commitDescription = description;
        try {
            List.copyOf(commitListeners).forEach(Runnable::run);
        } finally {
            commitDescription = CONFIGURE;
        }
    }

    /**
     * What the edit the commit listeners hear now is called in «Deshacer …»: {@code command.applyProfile} for a
     * profile chosen (or «Volver al perfil»), {@code command.restoreParameter} for one «↺», {@code command.configure}
     * otherwise. Read it inside a commit listener.
     */
    public String commitDescription() {
        return commitDescription;
    }

    /**
     * The editors' values without committing anything (a spinner's value, not text typed in it and not applied yet):
     * what the rows and the profile marks follow.
     */
    private Map<String, Object> presentationValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ParameterSpec parameter : type.parameters()) {
            JComponent editor = editors.get(parameter.name());
            values.put(parameter.name(), editor == null ? initial.get(parameter.name())
                    : editor instanceof JSpinner spinner ? spinner.getValue() : valueOf(editor));
        }
        return values;
    }

    /** The block under a Switch's «Valor» saying how the number is read; null when the type has none. */
    JPanel valueReading() {
        return valueReading == null ? null : valueReading.block();
    }

    /**
     * Moves every row (group headers included) into {@code host}, whose layout is a two column grid (caption, field)
     * that wraps after the field, so they share the host's columns with its own rows. The form keeps reading,
     * validating and listening to the editors; it is left empty and is not added anywhere. The fields may be narrower
     * than they would like (a choice box then ends its text in «…»), so with a wide font or a narrow side panel no
     * field runs past the panel's edge.
     */
    public void addRowsTo(JPanel host) {
        layOut(host, "wmin 0");
    }

    /** The label of a parameter: its name, plus « (t)» for a time parameter. Error messages use the plain name. */
    static String labelText(String parameterName) {
        String name = Messages.parameterName(parameterName);
        return TIME_PARAMETERS.contains(parameterName) ? Messages.format("form.timeUnit", name) : name;
    }

    JComponent editor(String parameterName) {
        return editors.get(parameterName);
    }

    JLabel label(String parameterName) {
        return labels.get(parameterName);
    }

    /** The «↺» of {@code parameterName}; null when no profile sets it. */
    JButton restoreButton(String parameterName) {
        return restoreButtons.get(parameterName);
    }

    /** The header of {@code group}; null when no shown parameter is in it. */
    JLabel groupHeader(String group) {
        return groupHeaders.get(group);
    }

    Map<String, JLabel> groupHeaders() {
        return Map.copyOf(groupHeaders);
    }

    /** The text under the profile box; null for a type without profiles. */
    JLabel profileSummary() {
        return profileSummary == null ? null : profileSummary.text();
    }

    /** «↺ Volver al perfil»; null for a type without profiles. */
    JButton restoreProfileButton() {
        return profileSummary == null ? null : profileSummary.restoreAll();
    }

    /** Whether the row of {@code parameterName} is shown now (its condition holds). */
    boolean rowShown(String parameterName) {
        return labels.get(parameterName).isVisible() && cells.get(parameterName).isVisible();
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
        regroupForNewWidth(values);
        return values;
    }

    /**
     * A bus splitter or joiner given a new «Ancho» keeps its groups sensible
     * ({@link StandardComponentTypes#regroupedForWidth}, the AI's rule too). The groups are hidden, so without this a
     * narrower bus would be refused for bits it no longer has.
     */
    private void regroupForNewWidth(Map<String, Object> values) {
        if (StandardComponentTypes.hasBitGroups(type.id())) {
            values.putAll(StandardComponentTypes.regroupedForWidth(initial, values));
        }
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
        return value != null && parameter.normalizeIfValid(value).isPresent();
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
            if (editor instanceof JTextComponent field) {
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
     * field, Ctrl+Enter or focus loss in a multi-line text, or a choice; a profile chosen, a «↺» or «Volver al perfil»
     * once each ({@link #commitDescription()} says which). Callers that apply edits immediately (the Properties tab)
     * use it; the dialog does not.
     */
    public void addCommitListener(Runnable listener) {
        commitListeners.add(listener);
        Runnable edited = () -> {
            if (!updatingEditors) {
                listener.run();
            }
        };
        FocusAdapter onFocusLost = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                edited.run();
            }
        };
        Optional<String> chooser = type.profileParameter();
        editors.forEach((name, editor) -> {
            if (chooser.filter(name::equals).isEmpty()) {
                listenForCommits(editor, edited, onFocusLost);
            }
        });
    }

    private static void listenForCommits(JComponent editor, Runnable edited, FocusAdapter onFocusLost) {
        switch (editor) {
            case JSpinner spinner -> {
                JFormattedTextField field = spinnerField(spinner);
                // "value" fires after the editor has synced its text; a spinner ChangeListener may run before.
                field.addPropertyChangeListener("value", event -> edited.run());
                field.addFocusListener(onFocusLost);
                field.addKeyListener(onEnter(edited, false));
            }
            case JTextField field -> {
                field.addActionListener(event -> edited.run());
                field.addFocusListener(onFocusLost);
            }
            case JTextArea area -> {
                area.addFocusListener(onFocusLost);
                area.addKeyListener(onEnter(edited, true));
            }
            case JComboBox<?> box -> {
                box.addActionListener(event -> edited.run());
                if (box.isEditable()) {
                    box.getEditor().getEditorComponent().addFocusListener(onFocusLost);
                }
            }
            case JCheckBox checkBox -> checkBox.addActionListener(event -> edited.run());
            case ColorSwatches<?> swatches -> swatches.addChoiceListener(edited);
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        }
    }

    /** Runs {@code edited} on Enter; with {@code withControl}, only on Ctrl+Enter (a plain one is a new line). */
    private static KeyAdapter onEnter(Runnable edited, boolean withControl) {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER && (!withControl || event.isControlDown())) {
                    if (withControl) {
                        event.consume();
                    }
                    edited.run();
                }
            }
        };
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
        if (type.id().equals(StandardComponentTypes.KEYBOARD) && parameter.name().equals(KeyboardTypes.CAPTURE_COLOR)) {
            return captureColorSwatches(parameter, (String) value, scheme);
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
            case TEXT -> textEditor(type, parameter, value.toString());
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
     * The Teclado's «Color al escribir» (a CHOICE, so the saved value stays its name) as swatches in the colours the
     * capture look has in {@code scheme}; «automatic», the scheme's selection colour, carries an «A».
     */
    private static ColorSwatches<String> captureColorSwatches(ParameterSpec parameter, String value,
                                                              Supplier<ColorScheme> scheme) {
        return new ColorSwatches<>(parameter.choices(), value,
                choice -> Messages.choiceName(parameter.name(), choice),
                choice -> Optional.of(KeyboardPainter.captureColor(scheme.get(), choice)),
                choice -> choice.equals(KeyboardTypes.CAPTURE_COLOR_CHOICES.get(0))
                        ? Optional.of(Messages.text("form.automaticMark")) : Optional.empty());
    }

    /** The Switch's «Valor», a box of a few lines for a multi-line text, a one-line field otherwise. */
    private static JComponent textEditor(ComponentType type, ParameterSpec parameter, String value) {
        if (isSwitchValue(type, parameter.name())) {
            return switchValueBox(value);
        }
        if (parameter.multiline()) {
            JTextArea area = new JTextArea(value, MULTILINE_ROWS, TEXT_COLUMNS);
            // wrapped lines: no horizontal scroll bar taking a row of the box, and the lines follow the panel's width
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            return FragmentRefusingTransferHandler.install(area);
        }
        return FragmentRefusingTransferHandler.install(new JTextField(value, TEXT_COLUMNS));
    }

    /**
     * The Switch's «Valor»: a one-bit Switch is set to 1, 0 or Z from the list, as always; with more bits the same box
     * takes the number typed in it, so it keeps working when the width changes in the same form.
     */
    private static boolean isSwitchValue(ComponentType type, String parameterName) {
        return type.id().equals(StandardComponentTypes.SWITCH) && parameterName.equals(SWITCH_VALUE);
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
            case JTextComponent text -> text.getText();
            case JComboBox<?> box -> box.isEditable() ? box.getEditor().getItem() : box.getSelectedItem();
            case JCheckBox checkBox -> checkBox.isSelected();
            case ColorSwatches<?> swatches -> swatches.selectedValue();
            default -> throw new IllegalStateException("unexpected editor " + editor.getClass());
        };
    }
}
