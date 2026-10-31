package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;

import javax.swing.JComboBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The «Terminal enlazado» row of a Teclado in the «Propiedades» tab: a combo of the labels of the circuit's Terminals
 * (and «(ninguno)»). Choosing one writes the {@code linked-terminal} parameter as one command (one undo step); the
 * parameter is hidden from the generic form, this row is its editor. A link to a label no Terminal has now (renamed or
 * deleted) is still listed, so it is not lost by looking at the part.
 */
public final class KeyboardInspectorRows implements PropertiesPanel.ComponentRows {

    /** The name of the combo, for tests. */
    public static final String COMBO_NAME = "keyboard.linkedTerminal";

    private final OpenDocument openDocument;

    public KeyboardInspectorRows(OpenDocument openDocument) {
        this.openDocument = Objects.requireNonNull(openDocument, "openDocument");
    }

    @Override
    public void addRows(Component component, PropertiesPanel.Rows rows) {
        if (!component.typeId().equals(StandardComponentTypes.KEYBOARD)) {
            return;
        }
        ComponentType type = openDocument.registry().typeOf(component);
        String linked = String.valueOf(type.resolveParameters(component.parameters())
                .get(KeyboardTypes.LINKED_TERMINAL));
        String none = Messages.text("keyboard.inspector.noTerminal");
        List<String> labels = new ArrayList<>(terminalLabels());
        if (!linked.isBlank() && !labels.contains(linked)) {
            labels.add(linked);
        }
        List<String> choices = new ArrayList<>();
        choices.add(none);
        choices.addAll(labels);
        JComboBox<String> combo = new JComboBox<>(choices.toArray(String[]::new));
        combo.setName(COMBO_NAME);
        combo.setSelectedItem(linked.isBlank() ? none : linked);
        combo.addActionListener(action -> {
            String chosen = (String) combo.getSelectedItem();
            String newLink = chosen == null || chosen.equals(none) ? "" : chosen;
            if (!newLink.equals(linked)) {
                link(component, type, newLink);
            }
        });
        rows.add(Messages.text("keyboard.inspector.linkedTerminal"), combo);
    }

    /** The labels of the Terminals placed on the circuit, without repeats or blanks, in document order. */
    List<String> terminalLabels() {
        return openDocument.document().components().stream()
                .filter(candidate -> candidate.typeId().equals(StandardComponentTypes.TERMINAL))
                .map(Component::label)
                .filter(label -> !label.isBlank())
                .distinct()
                .toList();
    }

    private void link(Component keyboard, ComponentType type, String terminalLabel) {
        Map<String, Object> parameters = new HashMap<>(type.resolveParameters(keyboard.parameters()));
        parameters.put(KeyboardTypes.LINKED_TERMINAL, terminalLabel);
        openDocument.execute(new ChangeParametersCommand(keyboard.id(), type, parameters));
    }
}
