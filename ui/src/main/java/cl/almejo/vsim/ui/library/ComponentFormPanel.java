package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.library.ComponentDefinition;
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
import java.util.List;
import java.util.function.BiConsumer;

/** The body of the create/update dialog: target (update), name with its inline message, pin order, replace box. */
public final class ComponentFormPanel extends JPanel {

    private static final Color ERROR_FALLBACK = new Color(0xDB5860);

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

    public ComponentFormPanel(ComponentForm form) {
        super(new MigLayout("insets dialog, wrap 2, fillx", "[grow, fill][grow, fill]"));
        this.form = form;
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
        form.addListener(this::refresh);
        refresh();
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
    }

    private static void replaceItems(DefaultListModel<String> model, List<String> items) {
        model.clear();
        model.addAll(items);
    }
}
