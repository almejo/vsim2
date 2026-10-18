package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** The body of the create/update dialog: target (update), name with its inline message, pin order, replace box. */
public final class ComponentFormPanel extends JPanel {

    private static final Color ERROR_FALLBACK = new Color(0xDB5860);

    /** Width of the pins note before it wraps, in pixels. */
    private static final int NOTE_WIDTH = 360;

    private final ComponentForm form;
    private final JTextField name = new JTextField(24);
    private final JLabel message = new JLabel(" ");
    private final DefaultListModel<String> inputModel = new DefaultListModel<>();
    private final DefaultListModel<String> outputModel = new DefaultListModel<>();
    private final JList<String> inputs = new JList<>(inputModel);
    private final JList<String> outputs = new JList<>(outputModel);
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

    JLabel pinsHelpLabel() {
        return pinsHelp;
    }

    public ComponentFormPanel(ComponentForm form) {
        super(new MigLayout("insets dialog, wrap 2, fillx", "[grow, fill][grow, fill]"));
        this.form = form;
        add(pinsHelp, "span 2, gapbottom 6");
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
        add(new JLabel(Messages.text("template.dialog.name")), "span 2");
        name.setText(form.name());
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                form.setName(name.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                form.setName(name.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                form.setName(name.getText());
            }
        });
        add(name, "span 2");
        Color error = UIManager.getColor("Actions.Red");
        message.setForeground(error == null ? ERROR_FALLBACK : error);
        add(message, "span 2");
        if (!form.testParts().isEmpty()) {
            addTestParts();
        }
        add(new JLabel(Messages.text("template.dialog.inputs")));
        add(new JLabel(Messages.text("template.dialog.outputs")));
        inputs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outputs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(inputs), "h 80:120");
        add(new JScrollPane(outputs), "h 80:120");
        add(buttons(inputs, inputUp, inputDown, form::moveInput));
        add(buttons(outputs, outputUp, outputDown, form::moveOutput));
        if (form.mode() == ComponentForm.Mode.CREATE) {
            replace.setSelected(form.replaceSelection());
            replace.addActionListener(event -> form.setReplaceSelection(replace.isSelected()));
            add(replace, "span 2");
        }
        form.addListener(refresher);
        refresh();
    }

    /**
     * The test parts, one row each: a check box with the part's label (a constant's value after it, «Cin = 0») and
     * what the part becomes, «Entrada»/«Salida» when checked, «Queda como constante», «Reloj interno» or «Se quita»
     * when not.
     */
    private void addTestParts() {
        JLabel title = new JLabel(Messages.text("template.testParts.title"));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
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

    private static String outcome(TestParts.Kind kind, boolean converted) {
        if (converted) {
            return Messages.text(kind.becomesInput() ? "template.testParts.input" : "template.testParts.output");
        }
        return Messages.text(switch (kind) {
            case SWITCH, CONSTANT -> "template.testParts.constant";
            case CLOCK -> "template.testParts.internalClock";
            case LED, SEVEN_SEGMENTS_DISPLAY, TIME_DIAGRAM -> "template.testParts.removed";
        });
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

    JList<String> inputList() {
        return inputs;
    }

    JList<String> outputList() {
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

    private static JPanel buttons(JList<String> list, JButton up, JButton down, BiConsumer<Integer, Integer> move) {
        up.addActionListener(event -> moveSelected(list, move, -1));
        down.addActionListener(event -> moveSelected(list, move, 1));
        JPanel panel = new JPanel(new MigLayout("insets 0", "[][]"));
        panel.add(up);
        panel.add(down);
        return panel;
    }

    private static void moveSelected(JList<String> list, BiConsumer<Integer, Integer> move, int offset) {
        int index = list.getSelectedIndex();
        if (index < 0) {
            return;
        }
        move.accept(index, offset);
        int destination = index + offset;
        if (destination >= 0 && destination < list.getModel().getSize()) {
            list.setSelectedIndex(destination);
        }
    }

    private void refresh() {
        replaceItems(inputModel, form.inputNames());
        replaceItems(outputModel, form.outputNames());
        message.setText(form.problem().orElse(" "));
        List<TestParts.TestPart> parts = form.testParts();
        for (int index = 0; index < testPartChoices.size(); index++) {
            boolean converted = form.isConverted(parts.get(index).id());
            testPartChoices.get(index).setSelected(converted);
            testPartOutcomes.get(index).setText(outcome(parts.get(index).kind(), converted));
        }
    }

    private static void replaceItems(DefaultListModel<String> model, List<String> items) {
        model.clear();
        model.addAll(items);
    }
}
