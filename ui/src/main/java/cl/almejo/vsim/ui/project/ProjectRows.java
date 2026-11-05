package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.library.InstancePaths.InstanceUse;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.InstanceNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rows of the Proyecto panel (spec §5.2): the document first («{name}», «{n} piezas»), then every instance of a
 * user component nested by containment, parents before their children, each indented by its depth with the
 * definition's name and, on the right, the instance's reference ({@link InstanceNames#reference}: its label, else its
 * id), as the place bar's route names it. Pure: no Swing.
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
        int pieces = document.components().size();
        rows.add(new Row(0, documentName, pieces == 1 ? Messages.text("project.piecesOne")
                : Messages.format("project.pieces", pieces), Optional.empty()));
        for (InstanceUse use : InstancePaths.of(document)) {
            // InstancePaths only yields chains of the document, so the instance is found
            Component instance = InstanceNames.chain(use.path(), document).orElseThrow().getLast();
            rows.add(new Row(use.chain().size(), InstanceNames.typeName(instance, document),
                    InstanceNames.reference(instance), Optional.of(use)));
        }
        return List.copyOf(rows);
    }
}
