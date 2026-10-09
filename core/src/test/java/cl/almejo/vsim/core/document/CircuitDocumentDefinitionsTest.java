package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitDocumentDefinitionsTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private final ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();

    private Component instance(String id, String definitionId) {
        Component component = new Component(new ElementId(id), SubcircuitTypes.typeIdOf(definitionId),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    private List<DocumentChange> record() {
        List<DocumentChange> changes = new ArrayList<>();
        document.addListener((changed, change) -> changes.add(change));
        return changes;
    }

    @Test
    void replacingADefinitionReportsItsInstancesAndTheInstancesOfDefinitionsThatNestIt() {
        document.putDefinition(inverter);
        document.putDefinition(doubleInverter);
        Component direct = instance("c1", ExampleDefinitions.INVERTER);
        Component nesting = instance("c2", ExampleDefinitions.DOUBLE_INVERTER);
        instance("c3", "unrelated");
        List<DocumentChange> changes = record();

        assertThat(document.putDefinition(inverter.withName("Negador"))).contains(inverter);

        assertThat(changes).containsExactly(new DocumentChange(Set.of(), Set.of(), Set.of(direct.id(), nesting.id())));
        assertThat(document.definition(ExampleDefinitions.INVERTER)).get()
                .extracting(ComponentDefinition::name).isEqualTo("Negador");
    }

    @Test
    void aDefinitionWithoutInstancesNotifiesNobody() {
        List<DocumentChange> changes = record();
        document.putDefinition(inverter);
        assertThat(document.removeDefinition(ExampleDefinitions.INVERTER)).contains(inverter);
        assertThat(changes).isEmpty();
        assertThat(document.revision()).isZero();
        assertThat(document.definitions()).isEmpty();
    }

    @Test
    void removingANestedDefinitionReportsTheInstancesOfDefinitionsThatNestIt() {
        document.putDefinition(inverter);
        document.putDefinition(doubleInverter);
        Component nesting = instance("c1", ExampleDefinitions.DOUBLE_INVERTER);
        List<DocumentChange> changes = record();

        assertThat(document.removeDefinition(ExampleDefinitions.INVERTER)).contains(inverter);

        assertThat(changes).containsExactly(new DocumentChange(Set.of(), Set.of(), Set.of(nesting.id())));
        assertThat(document.definitions()).containsExactly(doubleInverter);
    }

    @Test
    void puttingAMissingInnerDefinitionReportsTheInstancesOfDefinitionsThatNestIt() {
        document.putDefinition(doubleInverter);
        Component nesting = instance("c1", ExampleDefinitions.DOUBLE_INVERTER);
        List<DocumentChange> changes = record();

        document.putDefinition(inverter);

        assertThat(changes).singleElement()
                .satisfies(change -> assertThat(change.changed()).contains(nesting.id()));
    }

    @Test
    void theResolverKeepsOneTypePerDefinitionObject() {
        ComponentTypeRegistry.TypeResolver resolver = SubcircuitTypes.resolverFor(document);
        String typeId = SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER);
        assertThat(resolver.resolve(typeId)).isEmpty();

        document.putDefinition(inverter);
        ComponentType first = resolver.resolve(typeId).orElseThrow();
        assertThat(resolver.resolve(typeId)).containsSame(first);

        document.putDefinition(inverter.withName("Negador"));
        ComponentType replaced = resolver.resolve(typeId).orElseThrow();
        assertThat(replaced).isNotSameAs(first);
        assertThat(SubcircuitTypes.definitionOf(replaced)).get()
                .extracting(ComponentDefinition::name).isEqualTo("Negador");
        assertThat(resolver.resolve("unrelated")).isEmpty();
    }

    @Test
    void recursionIsRejectedBeforeAnythingChanges() {
        List<Component> components = new ArrayList<>(inverter.body().components());
        components.add(new Component(new ElementId("c9"), SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER),
                new GridPoint(20, 20), Rotation.R0, Map.of(), ""));
        ComponentDefinition inverterUsingDouble = new ComponentDefinition(ExampleDefinitions.INVERTER, "Inversor", "",
                new GridSize(4, 4), inverter.pinout(), new CircuitFragment(components, List.of(), List.of()));
        document.putDefinition(inverterUsingDouble);

        assertThatThrownBy(() -> document.putDefinition(doubleInverter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recursive definition");
        assertThat(document.definition(ExampleDefinitions.DOUBLE_INVERTER)).isEmpty();
    }

    @Test
    void usedDefinitionsAreTheClosureOfTheInstancesDependenciesFirst() {
        document.putDefinition(doubleInverter);
        document.putDefinition(inverter);
        document.putDefinition(new ComponentDefinition("spare", "Sobrante", "", inverter.size(), inverter.pinout(),
                inverter.body()));
        instance("c1", ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(document.usedDefinitions()).containsExactly(inverter, doubleInverter);
        assertThat(document.definitionsUsedBy(List.of())).isEmpty();
    }
}
