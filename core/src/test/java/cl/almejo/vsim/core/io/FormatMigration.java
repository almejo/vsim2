package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit test-scope migration tool; callers must choose tracked files, never a user's circuit directory. */
final class FormatMigration {
    private FormatMigration() {
    }

    /** Re-saves one VSim 2 circuit or library component; VSim 1 and unrelated JSON files stay untouched. */
    static void resave(Path path) throws IOException, CircuitFormatException {
        String content = Files.readString(path);
        JsonNode root = new ObjectMapper().readTree(content);
        if (!root.isObject() || !root.has("formatVersion")) {
            return;
        }
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        String written = switch (root.path("kind").asText()) {
            case "circuit" -> {
                CircuitFormat format = new CircuitFormat(registry);
                yield format.write(format.read(content));
            }
            case "component" -> {
                DefinitionFormat format = new DefinitionFormat(registry);
                yield format.write(format.read(content));
            }
            default -> throw new IllegalArgumentException("unsupported migration file kind: " + path);
        };
        Files.writeString(path, content.endsWith("\n") ? written + "\n" : written);
    }
}
