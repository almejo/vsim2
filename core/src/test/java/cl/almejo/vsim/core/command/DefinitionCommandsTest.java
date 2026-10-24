package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionCommandsTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private final ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();

    private ComponentTypeRegistry registry() {
        return StandardComponentTypes.registry().extendedWith(SubcircuitTypes.resolverFor(document));
    }

    @Test
    void putAddsOnlyAbsentDefinitionsAndRevertRemovesThem() {
        ComponentDefinition existing = inverter.withName("Mío");
        document.putDefinition(existing);
        PutDefinitionsCommand command = new PutDefinitionsCommand(List.of(inverter, doubleInverter));

        command.apply(document);
        assertThat(command.addedIds()).containsExactly(ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(document.definition(ExampleDefinitions.INVERTER)).containsSame(existing);
        assertThat(document.definition(ExampleDefinitions.DOUBLE_INVERTER)).contains(doubleInverter);

        command.revert(document);
        assertThat(document.definitions()).containsExactly(existing);
        command.apply(document);
        assertThat(document.definitions()).containsExactly(existing, doubleInverter);
    }

    @Test
    void aFailedPutLeavesTheDocumentAsItWas() {
        List<Component> components = new ArrayList<>(inverter.body().components());
        components.add(new Component(new ElementId("c9"), SubcircuitTypes.typeIdOf("loop"), new GridPoint(20, 20),
                Rotation.R0, Map.of(), ""));
        ComponentDefinition loop = new ComponentDefinition("loop", "Bucle", "", new GridSize(4, 4), inverter.pinout(),
                new CircuitFragment(components, List.of(), List.of()));
        PutDefinitionsCommand command = new PutDefinitionsCommand(List.of(inverter, loop));
        assertThatThrownBy(() -> command.apply(document)).isInstanceOf(IllegalArgumentException.class);
        assertThat(document.definitions()).isEmpty();
    }

    @Test
    void replaceChangesEveryInstanceAndRevertRestoresTheOldDefinition() {
        document.putDefinition(inverter);
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        document.add(instance);
        List<DocumentChange> changes = new ArrayList<>();
        document.addListener((changed, change) -> changes.add(change));
        ComponentDefinition renamed = inverter.withName("Negador");
        ReplaceDefinitionCommand command = ReplaceDefinitionCommand.prepare(document, registry(),
                new DefinitionBundle(renamed, List.of()));

        command.apply(document);
        assertThat(document.definition(ExampleDefinitions.INVERTER)).containsSame(renamed);
        command.revert(document);
        assertThat(document.definition(ExampleDefinitions.INVERTER)).containsSame(inverter);
        assertThat(changes).containsExactly(
                new DocumentChange(Set.of(), Set.of(), Set.of(instance.id())),
                new DocumentChange(Set.of(), Set.of(), Set.of(instance.id())));
        assertThat(command.description()).isEqualTo("command.updateDefinition");
    }

    @Test
    void replaceAddsMissingDependenciesAndRevertTakesThemAway() {
        ReplaceDefinitionCommand command = ReplaceDefinitionCommand.prepare(document, registry(),
                DefinitionBundle.of(doubleInverter, id -> Optional.of(inverter)));
        command.apply(document);
        assertThat(document.definitions()).containsExactly(inverter, doubleInverter);
        command.revert(document);
        assertThat(document.definitions()).isEmpty();
    }

    @Test
    void aFailedReplaceTakesAwayTheDependenciesItAdded() {
        ComponentDefinition helperUsingInverter = wrapping("helper", ExampleDefinitions.INVERTER);
        document.putDefinition(inverter);
        document.putDefinition(helperUsingInverter);
        List<ComponentDefinition> before = document.definitions();
        // The new inverter uses helper, which in this document uses inverter: a cycle only the document reveals.
        Component input = inverter.body().components().get(0);
        Component output = inverter.body().components().get(2);
        ComponentDefinition recursiveInverter = new ComponentDefinition(ExampleDefinitions.INVERTER, "Inversor", "",
                inverter.size(), inverter.pinout(), new CircuitFragment(List.of(input,
                instance("c2", "helper", 2, 0), instance("c4", "extra", 20, 20), output), List.of(), List.of()));
        ReplaceDefinitionCommand command = ReplaceDefinitionCommand.prepare(document, registry(),
                new DefinitionBundle(recursiveInverter, List.of(plain("helper"), plain("extra"))));

        assertThatThrownBy(() -> command.apply(document))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recursive");
        assertThat(document.definitions()).isEqualTo(before);
        assertThat(document.definition("extra")).isEmpty();
        assertThat(document.definition(ExampleDefinitions.INVERTER)).containsSame(inverter);
    }

    private ComponentDefinition plain(String id) {
        return new ComponentDefinition(id, id, "", inverter.size(), inverter.pinout(), inverter.body());
    }

    /** input-pin c1, one instance of {@code innerId}, output-pin c3: the inverter's layout around another box. */
    private ComponentDefinition wrapping(String id, String innerId) {
        List<Component> body = inverter.body().components();
        return new ComponentDefinition(id, id, "", inverter.size(), inverter.pinout(), new CircuitFragment(
                List.of(body.get(0), instance("c2", innerId, 2, 0), body.get(2)), List.of(), List.of()));
    }

    private static Component instance(String id, String definitionId, int x, int y) {
        return new Component(new ElementId(id), SubcircuitTypes.typeIdOf(definitionId), new GridPoint(x, y),
                Rotation.R0, Map.of(), "");
    }
}
