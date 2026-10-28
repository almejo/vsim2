package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Every VSim 2 file says what it is: {@code "format": "vsim2"} and the version that saved it, first. */
class FileHeaderTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    private final ObjectMapper mapper = new ObjectMapper();
    private final CircuitReader reader = new CircuitReader(REGISTRY);

    @Test
    void theAppVersionComesFromTheBuild() {
        assertThat(VsimVersion.current()).matches("\\d+\\.\\d+(\\.\\d+)?.*");
    }

    @Test
    void circuitsLibraryComponentsAndFragmentsStartWithTheMarkerAndTheVersionThatSavedThem() throws Exception {
        CircuitDocument document = new CircuitDocument();
        document.add(new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "hola"));
        String circuit = new CircuitFormat(REGISTRY).write(document);
        String component = new DefinitionFormat(REGISTRY).write(
                new DefinitionBundle(ExampleDefinitions.inverter(), List.of()));
        String fragment = new FragmentFormat(REGISTRY).write(new CircuitFragment(List.of(), List.of(),
                document.labels(), List.of(), List.of()));

        assertThat(circuit).startsWith("{\n  \"format\" : \"vsim2\",\n  \"savedWith\" : \"" + VsimVersion.current()
                + "\",\n  \"formatVersion\" : 4,\n  \"kind\" : \"circuit\"");
        assertThat(firstKeys(component)).containsExactly("format", "savedWith", "formatVersion", "kind");
        assertThat(firstKeys(fragment)).containsExactly("format", "savedWith", "formatVersion", "kind");
        assertThat(mapper.readTree(fragment).path("format").asText()).isEqualTo("vsim2");
        assertThat(mapper.readTree(component).path("savedWith").asText()).isEqualTo(VsimVersion.current());
    }

    @Test
    void aFileWithoutTheMarkerButWithFormatVersionTwoStillOpens() throws Exception {
        assertThat(reader.read("{\"formatVersion\":2,\"kind\":\"circuit\",\"labels\":"
                + "[{\"id\":\"l1\",\"x\":0,\"y\":0,\"text\":\"viejo\"}]}").labels()).hasSize(1);
        assertThat(reader.read("{\"format\":\"vsim2\",\"formatVersion\":2,\"kind\":\"circuit\"}").labels()).isEmpty();
    }

    static Stream<Arguments> refusedFiles() {
        return Stream.of(
                arguments("{\"format\":\"vsim2\",\"formatVersion\":5,\"kind\":\"circuit\"}",
                        CircuitFormatException.Reason.NEWER_FORMAT, "5"),
                arguments("{\"formatVersion\":7,\"kind\":\"circuit\"}", CircuitFormatException.Reason.NEWER_FORMAT, "7"),
                arguments("{\"name\":\"package.json\",\"version\":\"1.0\"}",
                        CircuitFormatException.Reason.NOT_VSIM_FILE, null),
                arguments("[1, 2]", CircuitFormatException.Reason.NOT_VSIM_FILE, null),
                arguments("{\"format\":\"other\",\"formatVersion\":2,\"kind\":\"circuit\"}",
                        CircuitFormatException.Reason.NOT_VSIM_FILE, null),
                arguments("{\"format\":\"vsim2\",\"kind\":\"circuit\"}",
                        CircuitFormatException.Reason.UNSUPPORTED_VERSION, null),
                arguments("{\"format\":\"vsim2\",\"formatVersion\":1,\"kind\":\"circuit\"}",
                        CircuitFormatException.Reason.UNSUPPORTED_VERSION, null));
    }

    @ParameterizedTest
    @MethodSource("refusedFiles")
    void newerAndForeignFilesAreRefusedWithTheirOwnReason(String json, CircuitFormatException.Reason reason,
                                                          String detail) {
        List<ThrowingRead> reads = List.of(() -> reader.read(json), () -> new CircuitFormat(REGISTRY).read(json),
                () -> new DefinitionFormat(REGISTRY).read(json.replace("circuit", "component")),
                () -> new FragmentFormat(REGISTRY).read(json.replace("circuit", "fragment")));
        for (ThrowingRead read : reads) {
            assertThatThrownBy(read::run).isInstanceOfSatisfying(CircuitFormatException.class, exception -> {
                assertThat(exception.reason()).isEqualTo(reason);
                if (detail != null) {
                    assertThat(exception.detail()).isEqualTo(detail);
                }
            });
        }
    }

    @Test
    void vsimOneFilesStillGoThroughTheImporter(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("viejo.json"), "{\"gates\":[{\"type\":\"not\",\"position\":{\"x\":0,\"y\":0},"
                + "\"parameters\":{\"delay\":1}}],\"connections\":[]}");
        assertThat(reader.open(file).importedFromVsim1()).isTrue();
    }

    private List<String> firstKeys(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        List<String> keys = new ArrayList<>();
        root.fieldNames().forEachRemaining(keys::add);
        return keys.subList(0, 4);
    }

    private interface ThrowingRead {
        void run() throws Exception;
    }
}
