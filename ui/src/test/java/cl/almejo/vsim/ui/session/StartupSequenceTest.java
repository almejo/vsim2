package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.session.StartupSequence.FromSession;
import cl.almejo.vsim.ui.session.StartupSequence.Plan;
import cl.almejo.vsim.ui.session.StartupSequence.Recovered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class StartupSequenceTest {

    private static final WindowState LEFT = new WindowState(0, 0, 960, 1080, false, new ViewState(1.5, 10, -4));
    private static final WindowState RIGHT = new WindowState(960, 0, 960, 1080, true, new ViewState(2.0, 0, 0));

    @TempDir
    Path directory;

    private Path counter;
    private Path adder;
    private Path gone;
    private Predicate<Path> exists;

    @BeforeEach
    void files() {
        counter = directory.resolve("contador.vsim");
        adder = directory.resolve("sumador.vsim");
        gone = directory.resolve("borrado.vsim");
        exists = Set.of(counter, adder)::contains;
    }

    private RecoveryCopy copyOf(Path original) {
        return copyOf(original, Instant.EPOCH);
    }

    private RecoveryCopy copyOf(Path original, Instant savedAt) {
        return new RecoveryCopy(directory.resolve("recovery").resolve(UUID.randomUUID() + ".vsim"),
                Optional.ofNullable(original), "copia", savedAt);
    }

    @Test
    void theLastSessionOpensEveryViewAndListsMissingFilesOnce() {
        Plan plan = StartupSequence.plan(List.of(), true, List.of(
                        new SessionDocument(counter, List.of(LEFT, RIGHT)),
                        new SessionDocument(gone, List.of(LEFT)),
                        new SessionDocument(adder, List.of(RIGHT)),
                        new SessionDocument(counter, List.of(RIGHT))),
                List.of(), exists);

        assertThat(plan.openings()).containsExactly(new FromSession(counter, List.of(LEFT, RIGHT)),
                new FromSession(adder, List.of(RIGHT)));
        assertThat(plan.missing()).containsExactly(gone);
        assertThat(plan.commandLineFiles()).isEmpty();
    }

    @Test
    void aRecoveredCopyComesFirstReplacesItsSessionFileAndTakesItsViews() {
        RecoveryCopy copy = copyOf(counter);
        RecoveryCopy untitled = copyOf(null);
        Plan plan = StartupSequence.plan(List.of(copy, untitled), true, List.of(
                        new SessionDocument(counter, List.of(LEFT, RIGHT)),
                        new SessionDocument(adder, List.of(RIGHT))),
                List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy, List.of(LEFT, RIGHT)),
                new Recovered(untitled, List.of()), new FromSession(adder, List.of(RIGHT)));
        assertThat(plan.missing()).isEmpty();
    }

    @Test
    void filesOnTheCommandLineSkipTheSession() {
        Plan plan = StartupSequence.plan(List.of(), true, List.of(new SessionDocument(counter, List.of(LEFT))),
                List.of(adder), exists);

        assertThat(plan.openings()).isEmpty();
        assertThat(plan.missing()).isEmpty();
        assertThat(plan.commandLineFiles()).containsExactly(adder);
    }

    @Test
    void aRecoveredCopyReplacesTheSameFileOnTheCommandLine() {
        RecoveryCopy copy = copyOf(counter);
        Plan plan = StartupSequence.plan(List.of(copy), true, List.of(), List.of(counter, adder), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy, List.of()));
        assertThat(plan.commandLineFiles()).containsExactly(adder);
    }

    @Test
    void withTheOptionOffOnlyRecoveredCopiesAndCommandLineFilesOpen() {
        RecoveryCopy copy = copyOf(counter);
        Plan plan = StartupSequence.plan(List.of(copy), false, List.of(new SessionDocument(adder, List.of(LEFT))),
                List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy, List.of()));
        assertThat(plan.missing()).isEmpty();
    }

    @Test
    void ofSeveralRecoveredCopiesOfOneFileOnlyTheNewestOpens() {
        RecoveryCopy older = copyOf(counter, Instant.parse("2026-10-09T14:00:00Z"));
        RecoveryCopy newer = copyOf(directory.resolve(".").resolve("contador.vsim"),
                Instant.parse("2026-10-09T15:00:00Z"));
        RecoveryCopy untitled = copyOf(null, Instant.parse("2026-10-09T13:00:00Z"));
        RecoveryCopy untitledToo = copyOf(null, Instant.parse("2026-10-09T13:30:00Z"));
        Plan plan = StartupSequence.plan(List.of(older, untitled, newer, untitledToo), true,
                List.of(new SessionDocument(counter, List.of(LEFT))), List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(untitled, List.of()),
                new Recovered(newer, List.of(LEFT)), new Recovered(untitledToo, List.of()));
    }
}
