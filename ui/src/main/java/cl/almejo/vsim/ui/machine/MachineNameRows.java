package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PropertiesPanel;
import cl.almejo.vsim.ui.theme.WrappedErrorText;

import javax.swing.JTextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Objects;
import java.util.Optional;

/**
 * «Nombre de la máquina» in Propiedades for a machine's piece in a circuit (hand test of hito 2, round 2): renames
 * the machine, the same as «Nombre» in its tab ({@link MachineNaming}), applied on Enter or when the field loses the
 * focus; a name that cannot be is told under it and nothing changes.
 */
final class MachineNameRows implements PropertiesPanel.ComponentRows {

    /** The field's component name (tests find it by it). */
    static final String FIELD_NAME = "machine.definitionName";
    /** The name of the line that says what is wrong with a typed name. */
    static final String PROBLEM_NAME = "machine.definitionName.problem";

    private final OpenDocument place;

    MachineNameRows(OpenDocument place) {
        this.place = Objects.requireNonNull(place, "place");
    }

    @Override
    public void addRows(Component component, PropertiesPanel.Rows rows) {
        Optional<String> definitionId = place.registry().findTypeOf(component)
                .flatMap(SubcircuitTypes::definitionOf)
                .filter(ComponentDefinition::isMachine)
                .map(ComponentDefinition::id);
        if (definitionId.isEmpty()) {
            return;
        }
        String id = definitionId.get();
        JTextField field = new JTextField(MachineNaming.nameOf(place, id).orElse(""));
        field.setName(FIELD_NAME);
        WrappedErrorText problem = new WrappedErrorText(" ");
        problem.setName(PROBLEM_NAME);
        problem.setVisible(false);
        Runnable apply = () -> {
            Optional<String> wrong = MachineNaming.problem(place, id, field.getText());
            problem.setText(wrong.orElse(" "));
            problem.setVisible(wrong.isPresent());
            if (wrong.isEmpty()) {
                MachineNaming.rename(place, id, field.getText());
                MachineNaming.nameOf(place, id).filter(name -> !name.equals(field.getText()))
                        .ifPresent(field::setText);
            }
        };
        field.addActionListener(event -> apply.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (field.isShowing()) {
                    apply.run();
                }
            }
        });
        rows.add(Messages.text("machine.piece.name"), field);
        rows.addWide(problem);
    }
}
