package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The rows of the Proyecto panel: the document, then every instance of a user component by containment. */
class ProjectRowsTest {

    private final CircuitDocument document = new CircuitDocument();

    @Test
    void nestsInstancesByContainment() {
        document.putDefinition(definition("leaf", "Hoja", List.of(gate("g1"))));
        document.putDefinition(definition("mid", "Medio", List.of(instance("m1", "leaf", 0, ""))));
        document.putDefinition(definition("top", "Alto", List.of(instance("t1", "mid", 0, "Reloj"))));
        document.add(instance("c1", "top", 0, ""));
        document.add(gate("g9"));

        List<ProjectRows.Row> rows = ProjectRows.of(document, "lrv32im");

        assertThat(rows).extracting(ProjectRows.Row::depth).containsExactly(0, 1, 2, 3);
        assertThat(rows).extracting(ProjectRows.Row::text).containsExactly("lrv32im", "Alto", "Medio", "Hoja");
        assertThat(rows.getFirst().right()).isEqualTo(Messages.format("project.pieces", 2));
        assertThat(rows.getFirst().instance()).isEmpty();
        assertThat(rows.get(1).right()).isEqualTo("c1");
        assertThat(rows.get(2).right()).isEqualTo("Reloj");
        assertThat(rows.get(3).right()).isEqualTo("m1");
        assertThat(rows.get(3).instance().orElseThrow().path()).isEqualTo("c1/t1/m1");
    }

    @Test
    void aDocumentWithoutComponentsHasOneRow() {
        List<ProjectRows.Row> rows = ProjectRows.of(document, "vacío");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().text()).isEqualTo("vacío");
        assertThat(rows.getFirst().right()).isEqualTo(Messages.format("project.pieces", 0));
    }

    @Test
    void aDocumentWithOnlyBuiltInPartsHasOneRow() {
        document.add(gate("g1"));

        assertThat(ProjectRows.of(document, "solo")).hasSize(1);
    }

    private static ComponentDefinition definition(String id, String name, List<Component> components) {
        return new ComponentDefinition(id, name, "", new GridSize(4, 4), List.of(),
                new CircuitFragment(components, List.of(), List.of()));
    }

    private static Component instance(String id, String definitionId, int x, String label) {
        return new Component(new ElementId(id), SubcircuitTypes.typeIdOf(definitionId), new GridPoint(x, 0),
                Rotation.R0, Map.of(), label);
    }

    private static Component gate(String id) {
        return StandardComponentTypes.registry().require(StandardComponentTypes.BUFFER).newComponent(
                new ElementId(id), new GridPoint(0, 0), Rotation.R0, Map.of(), "");
    }
}
