package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Step 1 «Tabla» (spec §3): the title, how many inputs (2..4) and outputs (1..4), their names, and the table whose
 * output cells the user fills by clicking or typing.
 */
final class TablePanel extends JPanel {

    private static final int NAME_COLUMNS = 5;

    private final KarnaughModel model;
    private final JTextField titleField = new JTextField(18);
    private final JSpinner inputCount;
    private final JSpinner outputCount;
    private final JPanel inputNamesPanel = new JPanel(new MigLayout("insets 0, gap 6"));
    private final JPanel outputNamesPanel = new JPanel(new MigLayout("insets 0, gap 6"));
    private final List<JTextField> inputNameFields = new ArrayList<>();
    private final List<JTextField> outputNameFields = new ArrayList<>();
    private final WizardTable table;
    private final Paragraph help = new Paragraph(Messages.text("karnaugh.table.help"));
    /** True over «Circuito desde expresiones»: the table comes from the expressions and cannot be edited here. */
    private boolean readOnly;

    TablePanel(KarnaughModel model, HoverState hover) {
        super(new MigLayout("insets 12, fillx, wrap 4", "[][grow, fill][][grow, fill]", "[][][][][][][grow, fill]"));
        this.model = model;
        this.inputCount = new JSpinner(new SpinnerNumberModel(model.inputCount(), KarnaughModel.MINIMUM_INPUTS,
                KarnaughModel.MAXIMUM_INPUTS, 1));
        this.outputCount = new JSpinner(new SpinnerNumberModel(model.outputCount(), KarnaughModel.MINIMUM_OUTPUTS,
                KarnaughModel.MAXIMUM_OUTPUTS, 1));
        this.table = new WizardTable(model, hover, true);

        titleField.setText(model.title());
        onEdit(titleField, () -> model.setTitle(titleField.getText()));
        inputCount.addChangeListener(event -> model.setInputCount((Integer) inputCount.getValue()));
        outputCount.addChangeListener(event -> model.setOutputCount((Integer) outputCount.getValue()));

        add(new JLabel(Messages.text("karnaugh.table.titleLabel")));
        add(titleField, "span 3, growx 0, wrap");
        add(new JLabel(Messages.text("karnaugh.table.inputs")));
        add(inputCount, "growx 0");
        add(new JLabel(Messages.text("karnaugh.table.outputs")), "gapleft 18");
        add(outputCount, "growx 0");
        add(new JLabel(Messages.text("karnaugh.table.inputNames")));
        add(inputNamesPanel, "span 3");
        add(new JLabel(Messages.text("karnaugh.table.outputNames")));
        add(outputNamesPanel, "span 3");
        add(help, "span 4, growx, wmin 100");
        add(new Paragraph(Messages.text("karnaugh.table.dontCare")), "span 4, growx, wmin 100");
        add(new JScrollPane(table), "span 4, grow, push");

        rebuildNameFields();
        model.addListener(this::modelChanged);
    }

    /**
     * The table comes from «Circuito desde expresiones»: nothing here can be edited (title, counts, names, cells), and
     * the help about editing becomes the note that says where to change it.
     */
    void setReadOnly() {
        readOnly = true;
        titleField.setEditable(false);
        inputCount.setEnabled(false);
        outputCount.setEnabled(false);
        inputNameFields.forEach(field -> field.setEditable(false));
        outputNameFields.forEach(field -> field.setEditable(false));
        table.stopEditing();
        help.setText(Messages.text("expressions.wizard.tableNote"));
    }

    /** The read-only note, empty when the table can be edited. */
    String noteText() {
        return readOnly ? help.getText() : "";
    }

    WizardTable table() {
        return table;
    }

    JSpinner inputCountSpinner() {
        return inputCount;
    }

    JSpinner outputCountSpinner() {
        return outputCount;
    }

    JTextField titleField() {
        return titleField;
    }

    List<JTextField> inputNameFields() {
        return List.copyOf(inputNameFields);
    }

    List<JTextField> outputNameFields() {
        return List.copyOf(outputNameFields);
    }

    /** Keeps the controls in step with the model (counts and names may change from elsewhere, e.g. a load). */
    private void modelChanged() {
        if ((Integer) inputCount.getValue() != model.inputCount()) {
            inputCount.setValue(model.inputCount());
        }
        if ((Integer) outputCount.getValue() != model.outputCount()) {
            outputCount.setValue(model.outputCount());
        }
        if (inputNameFields.size() != model.inputCount() || outputNameFields.size() != model.outputCount()) {
            rebuildNameFields();
        } else {
            sync(inputNameFields, model.inputNames());
            sync(outputNameFields, model.outputNames());
        }
        if (!titleField.getText().equals(model.title())) {
            titleField.setText(model.title());
        }
    }

    private static void sync(List<JTextField> fields, List<String> names) {
        for (int index = 0; index < fields.size(); index++) {
            if (!fields.get(index).getText().equals(names.get(index))) {
                fields.get(index).setText(names.get(index));
            }
        }
    }

    private void rebuildNameFields() {
        rebuild(inputNamesPanel, inputNameFields, model::inputNames, model::setInputName, !readOnly);
        rebuild(outputNamesPanel, outputNameFields, model::outputNames, model::setOutputName, !readOnly);
        revalidate();
        repaint();
    }

    private static void rebuild(JPanel panel, List<JTextField> fields, Supplier<List<String>> names,
                                BiConsumer<Integer, String> rename, boolean editable) {
        panel.removeAll();
        fields.clear();
        List<String> current = names.get();
        for (int index = 0; index < current.size(); index++) {
            JTextField field = new JTextField(current.get(index), NAME_COLUMNS);
            field.setEditable(editable);
            int position = index;
            onEdit(field, () -> rename.accept(position, field.getText()));
            fields.add(field);
            panel.add(field);
        }
    }

    private static void onEdit(JTextField field, Runnable action) {
        Consumer<DocumentEvent> run = event -> action.run();
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                run.accept(event);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                run.accept(event);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                run.accept(event);
            }
        });
    }
}
