package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
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

    @Test
    void anInstanceSimulatesItsDefinition() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
            place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
            place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, Map.of());
            Component led = place(openDocument, StandardComponentTypes.LED, 7, -2, Map.of());
            waitUntil(() -> openDocument.latestSnapshot().componentState(led.id().value())
                    .equals(new VisibleState.LedState(Value.ZERO)));
        }
    }
}
