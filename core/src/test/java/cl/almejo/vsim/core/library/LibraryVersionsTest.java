package cl.almejo.vsim.core.library;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
