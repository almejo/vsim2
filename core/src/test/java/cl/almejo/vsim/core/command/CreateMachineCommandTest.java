package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** «Máquina de estados» from the palette: the definition and its piece in one undo step. */
class CreateMachineCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);
    private final ComponentDefinition definition = MachineDefinitions.create("maquina-1", "Máquina 1");

    @Test
    void theDefinitionAndItsPieceComeAndGoTogether() {
        CreateMachineCommand create = new CreateMachineCommand(definition, new GridPoint(12, 8));

        history.execute(create);

        assertThat(document.definition("maquina-1")).containsSame(definition);
        Component piece = document.component(create.instanceId().orElseThrow()).orElseThrow();
        assertThat(piece.typeId()).isEqualTo(SubcircuitTypes.typeIdOf("maquina-1"));
        assertThat(piece.position()).isEqualTo(new GridPoint(12, 8));
        assertThat(create.description()).isEqualTo("command.createMachine");

        history.undo();
        assertThat(document.definition("maquina-1")).isEmpty();
        assertThat(document.components()).isEmpty();

        history.redo();
        assertThat(document.components()).extracting(Component::id).containsExactly(piece.id());
        assertThat(document.definition("maquina-1")).isPresent();
    }
}
