package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The check every package's own runtime runs (Distribución T1). Here it runs in the test JVM, which has every module,
 * so it proves the steps themselves work; the jlink'd runtime runs it through {@code ./gradlew
 * :ui:packagedRuntimeCheck}.
 */
class PackagedRuntimeCheckTest {

    @TempDir
    Path workDirectory;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private int run(List<PackagedRuntimeCheck.Step> steps) {
        return PackagedRuntimeCheck.run(steps, new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
    }

    private String printed(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Test
    void everyStepPassesInThisJvmAndSaysOk() throws IOException {
        try (PackagedRuntimeCheck.Checks checks = new PackagedRuntimeCheck.Checks(workDirectory)) {
            int exitCode = run(checks.steps());

            assertThat(printed(errors)).isEmpty();
            assertThat(exitCode).isZero();
            assertThat(printed(output).lines()).contains(PackagedRuntimeCheck.SUCCESS_LINE);
        }
        assertThat(Files.readString(workDirectory.resolve(PackagedRuntimeCheck.SVG_FILE_NAME))).contains("<svg");
        byte[] pdf = Files.readAllBytes(workDirectory.resolve(PackagedRuntimeCheck.PDF_FILE_NAME));
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void theChecksCoverWhatAPackageCanLose() {
        try (PackagedRuntimeCheck.Checks checks = new PackagedRuntimeCheck.Checks(workDirectory)) {
            assertThat(checks.steps()).extracting(PackagedRuntimeCheck.Step::name).containsExactly(
                    "message bundles and language data", "bundled example", "simulation", "SVG export",
                    "PDF export", "AI server");
        }
    }

    @Test
    void theFirstFailingStepStopsTheCheckAndIsNamed() {
        List<String> ran = new ArrayList<>();
        List<PackagedRuntimeCheck.Step> steps = List.of(
                new PackagedRuntimeCheck.Step("first", () -> ran.add("first")),
                new PackagedRuntimeCheck.Step("missing module", () -> {
                    throw new NoClassDefFoundError("java/sql/Timestamp");
                }),
                new PackagedRuntimeCheck.Step("last", () -> ran.add("last")));

        int exitCode = run(steps);

        assertThat(exitCode).isNotZero();
        assertThat(ran).containsExactly("first");
        assertThat(printed(output)).doesNotContain(PackagedRuntimeCheck.SUCCESS_LINE);
        assertThat(printed(errors).lines().findFirst()).hasValue(
                "PackagedRuntimeCheck FAILED at missing module: java.lang.NoClassDefFoundError: java/sql/Timestamp");
    }
}
