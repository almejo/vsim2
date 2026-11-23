package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineExamples;
import cl.almejo.vsim.core.machine.MachineTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryVersionsTest {

    @Test
    void onlyDefinitionsWhoseLibraryEntryDiffersAreOutdated() {
        ComponentLibrary library = new ComponentLibrary();
        List<ComponentDefinition> documentDefinitions = List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter());
        assertThat(LibraryVersions.outdated(documentDefinitions, library)).isEmpty();

        library.put(new DefinitionBundle(ExampleDefinitions.inverter(), List.of()));
        assertThat(LibraryVersions.outdated(documentDefinitions, library)).isEmpty();

        library.put(new DefinitionBundle(ExampleDefinitions.inverter().withName("Negador"), List.of()));
        assertThat(LibraryVersions.outdated(documentDefinitions, library)).containsExactly(ExampleDefinitions.INVERTER);
    }

    @Test
    void revisionDifferencesDistinguishNewerOlderAndDivergentContent() {
        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition renamed = original.withName("Negador");
        ComponentLibrary library = new ComponentLibrary();
        library.put(new DefinitionBundle(renamed, List.of()));
        assertThat(LibraryVersions.differences(List.of(original), library)).singleElement().satisfies(difference -> {
            assertThat(difference.circuitRevision()).isEqualTo(1);
            assertThat(difference.libraryRevision()).isEqualTo(2);
            assertThat(difference.relation()).isEqualTo(LibraryVersions.Relation.LIBRARY_NEWER);
        });
        assertThat(LibraryVersions.differences(List.of(original.withName("NOT")), library)).singleElement()
                .extracting(LibraryVersions.RevisionDifference::relation).isEqualTo(LibraryVersions.Relation.DIVERGENT);
        library.put(new DefinitionBundle(original, List.of()));
        assertThat(LibraryVersions.differences(List.of(renamed), library)).singleElement()
                .extracting(LibraryVersions.RevisionDifference::relation)
                .isEqualTo(LibraryVersions.Relation.CIRCUIT_NEWER);
    }

    @Test
    void revisionNumbersAloneDoNotMakeIdenticalContentOutdated() {
        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition sameContent = new ComponentDefinition(original.id(), original.name(),
                original.description(), original.size(), original.pinout(), original.body(), Optional.empty(), 9);
        ComponentLibrary library = new ComponentLibrary();
        library.put(new DefinitionBundle(sameContent, List.of()));
        assertThat(LibraryVersions.differences(List.of(original), library)).isEmpty();
        assertThat(LibraryVersions.outdated(List.of(original), library)).isEmpty();
    }

    @Test
    void localLogicEditsAdvanceOnceButPlainCopiesAndIncomingUpdatesKeepTheirRevision() {
        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition candidate = new ComponentDefinition(original.id(), "Negador", original.description(),
                original.size(), original.pinout(), original.body());
        assertThat(candidate.revisedFrom(original).revision()).isEqualTo(2);
        assertThat(original.revisedFrom(candidate.revisedFrom(original)).revision()).isEqualTo(3);
        assertThat(original.revisedFrom(original)).isSameAs(original);
    }

    /** Tests live only in the circuit (the user's rule, hito 4): a machine that has them is not outdated for that. */
    @Test
    void aMachineWhoseOnlyDifferenceIsItsTestsIsNotOutdated() {
        ComponentDefinition inLibrary = MachineDefinitions.create("maquina-1", "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector());
        ComponentDefinition inCircuit = MachineDefinitions.edited(inLibrary, inLibrary.machine().orElseThrow()
                .withTests(List.of(MachineTest.empty("p1", "Prueba 1", 8))));
        ComponentLibrary library = new ComponentLibrary();
        library.put(new DefinitionBundle(inLibrary, List.of()));

        assertThat(LibraryVersions.outdated(List.of(inCircuit), library)).isEmpty();
        assertThat(LibraryVersions.outdated(List.of(inCircuit.withName("Otra")), library))
                .containsExactly("maquina-1");
    }
}
