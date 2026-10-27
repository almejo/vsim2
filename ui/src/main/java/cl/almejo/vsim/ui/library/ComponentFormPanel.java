package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.TextChanges;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The body of the create/update dialog: target (update), name with its inline message, pin order and names (edited in
 * place, {@link PinNameTable}), replace box.
 */
public final class ComponentFormPanel extends JPanel {

    private static final Color ERROR_FALLBACK = new Color(0xDB5860);

    /** Width of the pins note before it wraps, in pixels. */
    private static final int NOTE_WIDTH = 360;

    private final ComponentForm form;
    private final JTextField name = new JTextField(24);
    private final JLabel message = new JLabel(" ");
    private final PinNameTable inputs;
    private final PinNameTable outputs;
    private final JLabel renameHelp = new JLabel("<html><body style='width:" + NOTE_WIDTH + "px'>"
            + Messages.text("template.pins.renameHelp") + "</body></html>");
    private final JButton inputUp = new JButton(Messages.text("template.dialog.up"));
    private final JButton inputDown = new JButton(Messages.text("template.dialog.down"));
    private final JButton outputUp = new JButton(Messages.text("template.dialog.up"));
    private final JButton outputDown = new JButton(Messages.text("template.dialog.down"));
    private final JCheckBox replace = new JCheckBox(Messages.text("template.dialog.replace"));
    private final JComboBox<ComponentDefinition> target = new JComboBox<>();
    private final JLabel pinsHelp = new JLabel("<html><body style='width:" + NOTE_WIDTH + "px'>"
            + Messages.text("template.dialog.pinsHelp") + "</body></html>");
    private final JPanel testPartsPanel = new JPanel(new MigLayout("insets 0, wrap 2", "[grow, fill][]"));
    private final List<JCheckBox> testPartChoices = new ArrayList<>();
    private final List<JLabel> testPartOutcomes = new ArrayList<>();
    private final Runnable refresher = this::refresh;
    private JLabel updateNote;

    JLabel pinsHelpLabel() {
        return pinsHelp;
    }

    /** The note of the update dialog: the pins that already exist keep their place in the box. */
    Optional<JLabel> updateNoteLabel() {
        return Optional.ofNullable(updateNote);
    }

    public ComponentFormPanel(ComponentForm form) {
        super(new MigLayout("insets dialog, wrap 2, fillx", "[grow, fill][grow, fill]"));
        this.form = form;
        this.inputs = new PinNameTable(form::inputNames, form::renameInput, form::inputProblem);
        this.outputs = new PinNameTable(form::outputNames, form::renameOutput, form::outputProblem);
        add(pinsHelp, "span 2, gapbottom 6");
        addTargetChooser();
        addNameAndMessage();
        if (!form.testParts().isEmpty()) {
            addTestParts();
        }
        add(new JLabel(Messages.text("template.dialog.inputs")));
        add(new JLabel(Messages.text("template.dialog.outputs")));
        add(new JScrollPane(inputs), "h 80:120");
        add(new JScrollPane(outputs), "h 80:120");
        add(buttons(inputs, inputUp, inputDown, form::moveInput));
        add(buttons(outputs, outputUp, outputDown, form::moveOutput));
        add(renameHelp, "span 2");
        addNotesAndReplace();
        form.addListener(refresher);
        refresh();
    }

    private void addTargetChooser() {
        if (form.mode() == ComponentForm.Mode.UPDATE) {
            form.targets().forEach(target::addItem);
            target.setSelectedItem(form.target().orElse(null));
            target.setRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                       boolean selected, boolean focused) {
                    super.getListCellRendererComponent(list, value, index, selected, focused);
                    if (value instanceof ComponentDefinition definition) {
                        setText(definition.name());
                    }
                    return this;
                }
            });
            target.addActionListener(event -> {
                if (target.getSelectedItem() instanceof ComponentDefinition definition
                        && !form.target().map(definition::equals).orElse(false)) {
                    form.setTarget(definition);
                    name.setText(form.name());
                }
            });
            add(new JLabel(Messages.text("template.dialog.target")), "span 2");
            add(target, "span 2");
        }
    }

    private void addNameAndMessage() {
        add(new JLabel(Messages.text("template.dialog.name")), "span 2");
        name.setText(form.name());
        TextChanges.onChange(name, () -> form.setName(name.getText()));
        add(name, "span 2");
        Color error = UIManager.getColor("Actions.Red");
        message.setForeground(error == null ? ERROR_FALLBACK : error);
        add(message, "span 2");
    }

    private void addNotesAndReplace() {
        if (form.mode() == ComponentForm.Mode.UPDATE) {
            updateNote = new JLabel("<html><body style='width:" + NOTE_WIDTH + "px'>"
                    + Messages.text("template.dialog.updateKeepsPlaces") + "</body></html>");
            add(updateNote, "span 2");
        }
        if (form.mode() == ComponentForm.Mode.CREATE) {
            replace.setSelected(form.replaceSelection());
            replace.addActionListener(event -> form.setReplaceSelection(replace.isSelected()));
            add(replace, "span 2");
        }
    }

    /**
     * The test parts, one row each: a check box with the part's label (a constant's value after it, «Cin = 0») and
     * what the part becomes ({@link ComponentForm#outcome}): the pins it gives, named, so unlabeled parts can be told
     * apart and matched with the pin lists.
     */
    private void addTestParts() {
        JLabel title = new JLabel(Messages.text("template.testParts.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, "span 2, gaptop 6");
        add(new JLabel("<html><body style='width:" + NOTE_WIDTH + "px'>" + Messages.text("template.testParts.help")
                + "</body></html>"), "span 2");
        for (TestParts.TestPart part : form.testParts()) {
            JCheckBox choice = new JCheckBox(partText(part), form.isConverted(part.id()));
            choice.addActionListener(event -> form.setConverted(part.id(), choice.isSelected()));
            JLabel outcome = new JLabel();
            testPartChoices.add(choice);
            testPartOutcomes.add(outcome);
            testPartsPanel.add(choice);
            testPartsPanel.add(outcome);
        }
        JScrollPane scroll = new JScrollPane(testPartsPanel);
        scroll.setBorder(null);
        add(scroll, "span 2, h ::160, gapbottom 6");
    }

    private static String partText(TestParts.TestPart part) {
        String name = part.label().isEmpty() ? Messages.componentName(part.typeId()) : part.label();
        return part.kind() == TestParts.Kind.CONSTANT ? name + " = " + part.value() : name;
    }

    /** What the panel listens to on its form; the dialog removes it when the panel goes away. */
    Runnable refresher() {
        return refresher;
    }

    JPanel testPartsPanel() {
        return testPartsPanel;
    }

    List<JCheckBox> testPartChoices() {
        return testPartChoices;
    }

    List<JLabel> testPartOutcomes() {
        return testPartOutcomes;
    }

    JTextField nameField() {
        return name;
    }

    JLabel messageLabel() {
        return message;
    }

    JTable inputTable() {
        return inputs;
    }

    JTable outputTable() {
        return outputs;
    }

    JButton inputDownButton() {
        return inputDown;
    }

    JCheckBox replaceCheckBox() {
        return replace;
    }

    JComboBox<ComponentDefinition> targetChoice() {
        return target;
    }

    private static JPanel buttons(PinNameTable table, JButton up, JButton down,
                                  BiConsumer<Integer, Integer> move) {
        up.addActionListener(event -> moveSelected(table, move, -1));
        down.addActionListener(event -> moveSelected(table, move, 1));
        JPanel panel = new JPanel(new MigLayout("insets 0", "[][]"));
        panel.add(up);
        panel.add(down);
        return panel;
    }

    /** Moves the selected pin; a name being edited is kept first, so it moves with its pin. */
    private static void moveSelected(PinNameTable table, BiConsumer<Integer, Integer> move, int offset) {
        table.stopEditing();
        int index = table.getSelectedRow();
        if (index < 0) {
            return;
        }
        move.accept(index, offset);
        int destination = index + offset;
        if (destination >= 0 && destination < table.getRowCount()) {
            table.setRowSelectionInterval(destination, destination);
        }
    }

    private void refresh() {
        inputs.refresh();
        outputs.refresh();
        message.setText(form.problem().orElse(" "));
        List<TestParts.TestPart> parts = form.testParts();
        for (int index = 0; index < testPartChoices.size(); index++) {
            boolean converted = form.isConverted(parts.get(index).id());
            testPartChoices.get(index).setSelected(converted);
            testPartOutcomes.get(index).setText(form.outcome(parts.get(index).id()));
        }
    }
}
