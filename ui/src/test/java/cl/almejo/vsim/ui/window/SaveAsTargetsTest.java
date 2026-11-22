package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** APP-2 debt: «Guardar como» refuses a file open in another tab; the refusal is a decision apart from the dialogs. */
class SaveAsTargetsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void aFileOpenInAnotherTabIsRefusedAndTheRefusalNamesIt() {
        OpenDocument asking = OpenDocument.createEmpty(registry);
        OpenDocument other = OpenDocument.createEmpty(registry);
        try {
            Path target = Path.of("circuitos", "sumador.vsim");
            List<Path> refused = new ArrayList<>();

            boolean accepted = SaveAsTargets.accepts(target, asking, (file, document) -> Optional.of(other),
                    refused::add);

            assertThat(accepted).isFalse();
            assertThat(refused).containsExactly(target);
        } finally {
            asking.close();
            other.close();
        }
    }

    @Test
    void aFreeFileIsAcceptedWithoutARefusal() {
        OpenDocument asking = OpenDocument.createEmpty(registry);
        try {
            List<Path> refused = new ArrayList<>();

            assertThat(SaveAsTargets.accepts(Path.of("libre.vsim"), asking, (file, document) -> Optional.empty(),
                    refused::add)).isTrue();
            assertThat(refused).isEmpty();
        } finally {
            asking.close();
        }
    }

    @Test
    void theRefusalComesBeforeTheOverwriteQuestion() {
        OpenDocument asking = OpenDocument.createEmpty(registry);
        OpenDocument other = OpenDocument.createEmpty(registry);
        try {
            List<String> events = new ArrayList<>();

            Optional<Path> chosen = SaveAsTargets.confirmed(Path.of("sumador.vsim"), asking,
                    (file, document) -> Optional.of(other), file -> events.add("refused"),
                    file -> {
                        events.add("overwrite asked");
                        return true;
                    });

            assertThat(chosen).isEmpty();
            assertThat(events).containsExactly("refused");
        } finally {
            asking.close();
            other.close();
        }
    }

    @Test
    void aFreeFileIsThenAskedAboutOverwriting() {
        OpenDocument asking = OpenDocument.createEmpty(registry);
        try {
            Path target = Path.of("libre.vsim");
            assertThat(SaveAsTargets.confirmed(target, asking, (file, document) -> Optional.empty(), file -> { },
                    file -> true)).contains(target);
            assertThat(SaveAsTargets.confirmed(target, asking, (file, document) -> Optional.empty(), file -> { },
                    file -> false)).isEmpty();
        } finally {
            asking.close();
        }
    }
}
