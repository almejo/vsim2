package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemOpenFilesTest {

    private final List<List<Path>> delivered = new ArrayList<>();
    private final SystemOpenFiles incoming = new SystemOpenFiles();

    @Test
    void filesThatArriveBeforeTheApplicationIsReadyWaitAndAreDeliveredOnAttach() {
        incoming.receive(List.of(Path.of("a.vsim")));
        incoming.receive(List.of(Path.of("b.vsim"), Path.of("c.vsim")));
        assertThat(delivered).isEmpty();

        incoming.attach(delivered::add);

        assertThat(delivered).containsExactly(List.of(Path.of("a.vsim"), Path.of("b.vsim"), Path.of("c.vsim")));
    }

    @Test
    void filesThatArriveAfterAttachingGoStraightToTheApplication() {
        incoming.attach(delivered::add);

        incoming.receive(List.of(Path.of("a.vsim")));

        assertThat(delivered).containsExactly(List.of(Path.of("a.vsim")));
    }

    @Test
    void attachingWithNothingWaitingDeliversNothingAndAnEmptyEventIsIgnored() {
        incoming.attach(delivered::add);
        incoming.receive(List.of());
        assertThat(delivered).isEmpty();
    }
}
