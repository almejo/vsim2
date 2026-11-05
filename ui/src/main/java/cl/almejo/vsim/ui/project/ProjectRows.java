package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.library.InstancePaths.InstanceUse;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rows of the Proyecto panel (spec §5.2): the document first («{name}», «{n} piezas»), then every instance of a
 * user component nested by containment, parents before their children, each indented by its depth with the
 * definition's name and, on the right, the instance's own name. Pure: no Swing.
 */
public final class ProjectRows {

    /**
     * One row. {@code depth} is 0 for the document and the instance's nesting level (1 for a part of the circuit)
     * otherwise; {@code instance} is empty for the document row.
     */
    public record Row(int depth, String text, String right, Optional<InstanceUse> instance) {
    }

    private ProjectRows() {
    }

    public static List<Row> of(CircuitDocument document, String documentName) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(0, documentName, Messages.format("project.pieces", document.components().size()),
                Optional.empty()));
        for (InstanceUse use : InstancePaths.of(document)) {
            String definitionName = document.definition(use.definitionId()).map(ComponentDefinition::name)
                    .orElse(use.definitionId());
            rows.add(new Row(use.chain().size(), definitionName, nameOf(document, use), Optional.of(use)));
        }
        return List.copyOf(rows);
    }

    /** The instance's label, else its id: «Reloj», «c5». */
    private static String nameOf(CircuitDocument document, InstanceUse use) {
        List<Component> level = document.components();
        Component found = null;
        for (ElementId id : use.chain()) {
            found = level.stream().filter(component -> component.id().equals(id)).findFirst().orElse(null);
            if (found == null) {
                break;
            }
            level = SubcircuitTypes.definitionIdOf(found.typeId()).flatMap(document::definition)
                    .map(definition -> definition.body().components()).orElse(List.of());
        }
        String id = use.chain().getLast().value();
        return found == null || found.label().isBlank() ? id : found.label();
    }
}
