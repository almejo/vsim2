package cl.almejo.vsim.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rewrites VSim 2 files to the current format. Does nothing unless {@code VSIM_MIGRATE_PATHS} (a {@code :}-separated
 * list of files or folders) is set; then every circuit ({@code .vsim}) and component file
 * ({@code .vsimcomponent.json}) found there is loaded and saved back by VSim's own writers, one line per file:
 * unchanged / rewritten / failed (reason). From the repository root:
 * {@code VSIM_MIGRATE_PATHS=/a/b:/c ./gradlew :core:test --tests '*MigratePathsTest.migrateTheGivenPaths' -i}.
 */
class MigratePathsTest {

    private static final String VERSION_3_CIRCUIT = """
            {"formatVersion":3,"kind":"circuit","name":"old","components":[
              {"id":"c1","type":"nand2","x":0,"y":0,"rotation":0,"parameters":{}}],"wires":[]}""";

    @TempDir
    Path directory;

    @Test
    @EnabledIfEnvironmentVariable(named = "VSIM_MIGRATE_PATHS", matches = ".+")
    void migrateTheGivenPaths() throws IOException {
        List<Path> paths = Arrays.stream(System.getenv("VSIM_MIGRATE_PATHS").split(":"))
                .filter(text -> !text.isBlank()).map(Path::of).toList();
        MigratePaths.run(paths, System.out);
    }

    @Test
    void anOldCircuitIsRewrittenAsTheCurrentVersionAndASecondRunLeavesItUnchanged() throws IOException {
        Path file = directory.resolve("old.vsim");
        Files.writeString(file, VERSION_3_CIRCUIT);

        String first = output(List.of(directory));
        String second = output(List.of(file));

        assertThat(first).contains(file + ": rewritten");
        assertThat(Files.readString(file)).contains("\"formatVersion\" : " + CircuitFormat.FORMAT_VERSION)
                .contains("\"type\" : \"and\"").doesNotContain("nand2");
        assertThat(second).contains(file + ": unchanged");
    }

    @Test
    void aBrokenFileIsReportedAsFailedAndLeftAlone() throws IOException {
        Path file = directory.resolve("broken.vsim");
        Files.writeString(file, "{not json");

        String report = output(List.of(directory));

        assertThat(report).contains(file + ": failed (");
        assertThat(Files.readString(file)).isEqualTo("{not json");
    }

    @Test
    void otherFilesAreNotTouched() throws IOException {
        Path file = directory.resolve("notes.txt");
        Files.writeString(file, "hello");

        assertThat(output(List.of(directory))).doesNotContain("notes.txt");
    }

    private static String output(List<Path> paths) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MigratePaths.run(paths, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
