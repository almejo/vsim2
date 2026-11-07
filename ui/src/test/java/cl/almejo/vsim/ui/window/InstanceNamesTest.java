package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How an instance is named (spec §5.3): its label, else its type name, numbered in document order when several unlabeled
 * pieces of one type share a place; a route names each step the same way.
 */
class InstanceNamesTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    @Test
    void labelElseUnlabeledName() {
        Component unlabeled = place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of());
        Component labeled = place(openDocument, StandardComponentTypes.BUFFER, 10, 0, Map.of());
        openDocument.execute(new SetLabelCommand(labeled.id(), "mi buffer"));

        assertThat(InstanceNames.name(unlabeled.id().value(), openDocument.document()))
                .contains(Messages.distinguishedName(
                        Messages.componentName(StandardComponentTypes.BUFFER, unlabeled.parameters()),
                        unlabeled.parameters()));
        assertThat(InstanceNames.name(labeled.id().value(), openDocument.document())).contains("mi buffer");
        assertThat(InstanceNames.name("c9", openDocument.document())).isEmpty();
    }

    @Test
    void unlabeledPiecesOfOneTypeInOnePlaceAreNumberedInDocumentOrder() {
        Component first = place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of());
        Component labeled = place(openDocument, StandardComponentTypes.BUFFER, 10, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.BUFFER, 20, 0, Map.of());
        Component alone = place(openDocument, StandardComponentTypes.LED, 30, 0, Map.of());
        openDocument.execute(new SetLabelCommand(labeled.id(), "U2"));

        assertThat(InstanceNames.name(first.id().value(), openDocument.document())).contains("Buffer 1");
        assertThat(InstanceNames.name(second.id().value(), openDocument.document())).contains("Buffer 2");
        assertThat(InstanceNames.name(labeled.id().value(), openDocument.document())).contains("U2");
        assertThat(InstanceNames.name(alone.id().value(), openDocument.document())).contains("LED");
    }

    @Test
    void routeNamesEachStepByItsDefinitionAndReference() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 0, 0, Map.of());

        assertThat(InstanceNames.route("c1/c3", openDocument.document()))
                .containsExactly("Doble inversor", "Inversor 2");
        assertThat(InstanceNames.path("c1/c3/c2", openDocument.document())).contains("Doble inversor › Inversor 2 › NOT");
        assertThat(InstanceNames.route("c9", openDocument.document())).isEmpty();
    }

    @Test
    void aLabelNamesTheStep() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0,
                Map.of());
        openDocument.execute(new SetLabelCommand(instance.id(), "U4"));

        assertThat(InstanceNames.route(instance.id().value(), openDocument.document()))
                .containsExactly("U4");
    }
}
