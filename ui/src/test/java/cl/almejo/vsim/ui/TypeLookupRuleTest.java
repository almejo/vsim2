package cl.almejo.vsim.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Overview C2 of M5: a placed component's type comes from {@code typeOf}/{@code findTypeOf}, so its parameters (bus
 * widths) shape its pins everywhere.
 */
class TypeLookupRuleTest {

    private static final Pattern LOOKUP_BY_TYPE_ID = Pattern.compile("\\.(require|find)\\([^;]*?\\.typeId\\(\\)\\)");
    /**
     * The registry itself, and the file reader: it normalizes raw parameters of file entries inside its own error
     * handling, so it must look the type up without them.
     */
    private static final Set<String> EXEMPT = Set.of("ComponentTypeRegistry.java", "DefinitionEntries.java");

    @Test
    void placedComponentsAreLookedUpWithTheirParameters() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("../core/src/main/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    if (EXEMPT.contains(file.getFileName().toString())) {
                        continue;
                    }
                    String content = Files.readString(file);
                    Matcher matcher = LOOKUP_BY_TYPE_ID.matcher(content);
                    while (matcher.find()) {
                        int line = 1 + (int) content.substring(0, matcher.start()).chars().filter(ch -> ch == '\n').count();
                        offenders.add(file.getFileName() + ":" + line + ": " + matcher.group().strip());
                    }
                }
            }
        }
        assertThat(offenders).as("use registry.typeOf(component) / findTypeOf(component)").isEmpty();
    }

    @Test
    void theRuleAlsoCatchesALookupSplitAcrossLines() {
        assertThat(LOOKUP_BY_TYPE_ID.matcher("registry.require(document.component(id).orElseThrow()\n            .typeId())").find())
                .isTrue();
        assertThat(LOOKUP_BY_TYPE_ID.matcher("registry.require(component.typeId())").find()).isTrue();
        assertThat(LOOKUP_BY_TYPE_ID.matcher("registry.require(\"a\");\nother.typeId())").find()).isFalse();
    }
}
