package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every place a definition is used, as an instance path through the bodies (spec §10.5). */
class InstancePathsTest {

    private final CircuitDocument document = new CircuitDocument();

    @Test
    void ofListsNestedInstancesParentsFirst() {
        document.putDefinition(definition("b", List.of(gate("c1"))));
        document.putDefinition(definition("a", List.of(instance("b1", "b", 0), instance("b2", "b", 10))));
        document.add(instance("a", "a", 0));
        document.add(instance("b", "b", 30));

        List<InstancePaths.InstanceUse> uses = InstancePaths.of(document);

        assertThat(uses).extracting(InstancePaths.InstanceUse::path).containsExactly("a", "a/b1", "a/b2", "b");
        assertThat(uses).extracting(InstancePaths.InstanceUse::definitionId).containsExactly("a", "b", "b", "b");
        assertThat(uses.get(2).chain()).containsExactly(new ElementId("a"), new ElementId("b2"));
        assertThat(InstancePaths.usesOf(document, "b")).extracting(InstancePaths.InstanceUse::path)
                .containsExactly("a/b1", "a/b2", "b");
        assertThat(InstancePaths.isUsedInsideAnother(document, "b")).isTrue();
        assertThat(InstancePaths.isUsedInsideAnother(document, "a")).isFalse();
    }

    @Test
    void aDefinitionWithoutInstancesHasNoUses() {
        document.putDefinition(definition("b", List.of(gate("c1"))));

        assertThat(InstancePaths.usesOf(document, "b")).isEmpty();
        assertThat(InstancePaths.of(document)).isEmpty();
    }

    @Test
    void ofStopsAtDepth32() {
        int chainLength = 40;
        document.putDefinition(definition("d" + chainLength, List.of(gate("c1"))));
        for (int level = chainLength - 1; level >= 0; level--) {
            document.putDefinition(definition("d" + level, List.of(instance("c1", "d" + (level + 1), 0))));
        }
        document.add(instance("top", "d0", 0));

        List<InstancePaths.InstanceUse> uses = InstancePaths.of(document);

        assertThat(uses).hasSize(InstancePaths.MAXIMUM_DEPTH);
        assertThat(uses.getLast().chain()).hasSize(InstancePaths.MAXIMUM_DEPTH);
    }

    private static ComponentDefinition definition(String id, List<Component> components) {
        return new ComponentDefinition(id, "Def " + id, "", new GridSize(4, 4), List.of(),
                new CircuitFragment(components, List.of(), List.of()));
    }

    private static Component instance(String id, String definitionId, int x) {
        return new Component(new ElementId(id), SubcircuitTypes.typeIdOf(definitionId), new GridPoint(x, 0),
                Rotation.R0, Map.of(), "");
    }

    private static Component gate(String id) {
        return StandardComponentTypes.registry().require(StandardComponentTypes.BUFFER).newComponent(
                new ElementId(id), new GridPoint(0, 0), Rotation.R0, Map.of(), "");
    }
}
