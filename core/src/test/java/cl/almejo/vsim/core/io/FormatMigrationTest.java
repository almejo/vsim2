package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FormatMigrationTest {
    @Test
    void migrationKeepsCircuitContentAndIsIdempotent(@TempDir Path directory) throws Exception {
        String old = "{\"formatVersion\":2,\"kind\":\"circuit\",\"name\":\"old\",\"labels\":"
                + "[{\"id\":\"l1\",\"x\":3,\"y\":4,\"text\":\"A\"}]}";
        Path file = Files.writeString(directory.resolve("old.vsim"), old);
        CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());
        FormatMigration.resave(file);
        String migrated = Files.readString(file);
        assertThat(migrated).contains("\"formatVersion\" : 4");
        assertThat(format.read(migrated).labels()).isEqualTo(format.read(old).labels());
        FormatMigration.resave(file);
        assertThat(Files.readString(file)).isEqualTo(migrated);
    }

    /** Explicitly selected tracked files only: VSIM_MIGRATE_FILES=1 ./gradlew :core:test --tests '*FormatMigrationTest.resaveTrackedFiles'. */
    @Test
    @EnabledIfEnvironmentVariable(named = "VSIM_MIGRATE_FILES", matches = "1")
    void resaveTrackedFiles() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Process git = new ProcessBuilder("git", "-C", root.toString(), "ls-files", "-z").start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(git.waitFor()).isZero();
        for (String relative : Arrays.asList(output.split("\u0000"))) {
            if (relative.endsWith(".vsim") || relative.endsWith(".json")) {
                FormatMigration.resave(root.resolve(relative));
            }
        }
    }
}
