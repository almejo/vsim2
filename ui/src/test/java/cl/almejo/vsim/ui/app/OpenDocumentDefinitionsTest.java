package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDocumentDefinitionsTest {

    @Test
    void eachDocumentResolvesOnlyItsOwnDefinitions() {
        ComponentTypeRegistry base = StandardComponentTypes.registry();
        String typeId = SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER);
        try (OpenDocument first = OpenDocument.createEmpty(base); OpenDocument second = OpenDocument.createEmpty(base)) {
            first.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
            assertThat(first.registry().find(typeId)).isPresent();
            assertThat(second.registry().find(typeId)).isEmpty();
            assertThat(base.find(typeId)).isEmpty();
            first.history().undo();
            assertThat(first.registry().find(typeId)).isEmpty();
        }
    }
}
