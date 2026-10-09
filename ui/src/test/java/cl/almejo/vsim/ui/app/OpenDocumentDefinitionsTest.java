package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void aSavedFileWithTemplatesReopensWithItsDefinitionsAndSimulates(@TempDir Path directory) throws Exception {
        ComponentTypeRegistry base = StandardComponentTypes.registry();
        Path file = directory.resolve("template.vsim");
        try (OpenDocument openDocument = OpenDocument.createEmpty(base)) {
            openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
            place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
            place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, Map.of());
            place(openDocument, StandardComponentTypes.LED, 7, -2, Map.of());
            openDocument.save(file);
        }
        try (OpenDocument reopened = OpenDocument.open(file, base)) {
            assertThat(reopened.document().definitions()).containsExactly(ExampleDefinitions.inverter());
            assertThat(reopened.registry().find(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER))).isPresent();
            Component led = reopened.document().components().stream()
                    .filter(component -> component.typeId().equals(StandardComponentTypes.LED)).findFirst().orElseThrow();
            waitUntil(() -> reopened.latestSnapshot().componentState(led.id().value())
                    .equals(new VisibleState.LedState(Value.ZERO)));
        }
        assertThat(base.find(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER))).isEmpty();
    }

    @Test
    void aHierarchyTooDeepToSimulateFailsTheSimulationButKeepsUndo() {
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            openDocument.execute(new PutDefinitionsCommand(nestedChain(40)));
            List<String> notifications = new ArrayList<>();
            openDocument.addChangeListener(() -> notifications.add("changed"));

            place(openDocument, SubcircuitTypes.typeIdOf("level-40"), 0, 0, Map.of());

            assertThat(openDocument.document().components()).hasSize(1);
            assertThat(openDocument.history().undoDescription()).isPresent();
            assertThat(notifications).isNotEmpty();
            waitUntil(() -> openDocument.latestSnapshot().failure().isPresent());
            assertThat(openDocument.latestSnapshot().running()).isFalse();

            openDocument.history().undo();
            assertThat(openDocument.document().components()).isEmpty();
            place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
            waitUntil(() -> openDocument.latestSnapshot().failure().isEmpty());
        }
    }

    /** level-0 is the inverter; level-k holds one level-(k-1) between its input and output pins. */
    private static List<ComponentDefinition> nestedChain(int levels) {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        List<Component> inverterBody = inverter.body().components();
        List<ComponentDefinition> chain = new ArrayList<>();
        chain.add(new ComponentDefinition("level-0", "Nivel 0", "", inverter.size(), inverter.pinout(),
                inverter.body()));
        for (int level = 1; level <= levels; level++) {
            Component inner = new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf("level-" + (level - 1)),
                    new GridPoint(2, 0), Rotation.R0, Map.of(), "");
            chain.add(new ComponentDefinition("level-" + level, "Nivel " + level, "", inverter.size(),
                    inverter.pinout(), new CircuitFragment(List.of(inverterBody.get(0), inner, inverterBody.get(2)),
                    List.of(), List.of())));
        }
        return chain;
    }

    @Test
    void savingAnInstanceWithoutItsDefinitionFailsAndKeepsTheOldFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("broken.vsim");
        Files.writeString(file, "previous content");
        try (OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            // No command leads here; this stands for a broken invariant.
            openDocument.document().add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf("missing"),
                    new GridPoint(0, 0), Rotation.R0, Map.of(), ""));
            assertThatThrownBy(() -> openDocument.save(file))
                    .isInstanceOf(IOException.class).hasMessageContaining("missing");
        }
        assertThat(Files.readString(file)).isEqualTo("previous content");
        try (var leftovers = Files.list(directory)) {
            assertThat(leftovers).containsExactly(file);
        }
    }
}
