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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class StartupSequenceTest {

    private static final ViewState LEFT_VIEW = new ViewState(1.5, 10, -4);
    private static final ViewState RIGHT_VIEW = new ViewState(2.0, 0, 0);
    private static final WindowState LEFT = new WindowState(0, 0, 960, 1080, false, LEFT_VIEW);
    private static final WindowState RIGHT = new WindowState(960, 0, 960, 1080, true, RIGHT_VIEW);

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

    /** One window with a main tab of each of {@code files}, the first active. */
    private static WindowSession window(WindowState bounds, Path... files) {
        return new WindowSession(bounds, Arrays.stream(files)
                .map(file -> TabState.main(file, bounds.view())).toList(), 0, List.of());
    }

    @Test
    void theLastSessionOpensEveryFileOnceKeepsItsWindowsAndListsMissingFilesOnce() {
        WindowSession both = window(LEFT, counter, gone, adder);
        WindowSession again = window(RIGHT, counter, gone);
        Plan plan = StartupSequence.plan(List.of(), true, List.of(both, again), List.of(), exists);

        assertThat(plan.openings()).containsExactly(new FromSession(counter), new FromSession(adder));
        assertThat(plan.windows()).containsExactly(window(LEFT, counter, adder), window(RIGHT, counter));
        assertThat(plan.missing()).containsExactly(gone);
        assertThat(plan.commandLineFiles()).isEmpty();
    }

    @Test
    void aWindowWhoseFilesAreAllGoneIsNotRestored() {
        Plan plan = StartupSequence.plan(List.of(), true, List.of(window(LEFT, gone), window(RIGHT, adder)),
                List.of(), exists);

        assertThat(plan.windows()).containsExactly(window(RIGHT, adder));
        assertThat(plan.missing()).containsExactly(gone);
    }

    @Test
    void aRecoveredCopyComesFirstAndReplacesItsSessionFileWhoseTabsStay() {
        RecoveryCopy copy = copyOf(counter);
        RecoveryCopy untitled = copyOf(null);
        Plan plan = StartupSequence.plan(List.of(copy, untitled), true,
                List.of(window(LEFT, counter, adder), window(RIGHT, counter)), List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy), new Recovered(untitled),
                new FromSession(adder));
        assertThat(plan.windows()).containsExactly(window(LEFT, counter, adder), window(RIGHT, counter));
        assertThat(plan.missing()).isEmpty();
    }

    @Test
    void aRecoveredCopyOfAFileThatIsGoneKeepsItsTabs() {
        RecoveryCopy copy = copyOf(gone);
        Plan plan = StartupSequence.plan(List.of(copy), true, List.of(window(LEFT, gone)), List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy));
        assertThat(plan.windows()).containsExactly(window(LEFT, gone));
        assertThat(plan.missing()).isEmpty();
    }

    @Test
    void filesOnTheCommandLineJoinTheRestoredSessionAndOneAlreadyThereIsOnlyFocused() {
        Plan plan = StartupSequence.plan(List.of(), true, List.of(window(LEFT, counter)), List.of(adder, counter),
                exists);

        assertThat(plan.openings()).containsExactly(new FromSession(counter));
        assertThat(plan.windows()).containsExactly(window(LEFT, counter));
        assertThat(plan.missing()).isEmpty();
        // Both still go through the already-open check: adder opens as a new group, counter's tab is focused.
        assertThat(plan.commandLineFiles()).containsExactly(adder, counter);
    }

    @Test
    void aRecoveredCopyReplacesTheSameFileOnTheCommandLine() {
        RecoveryCopy copy = copyOf(counter);
        Plan plan = StartupSequence.plan(List.of(copy), true, List.of(), List.of(counter, adder), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy));
        assertThat(plan.commandLineFiles()).containsExactly(adder);
    }

    @Test
    void withTheOptionOffOnlyRecoveredCopiesAndCommandLineFilesOpen() {
        RecoveryCopy copy = copyOf(counter);
        Plan plan = StartupSequence.plan(List.of(copy), false, List.of(window(LEFT, adder)), List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(copy));
        assertThat(plan.windows()).isEmpty();
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
                List.of(window(LEFT, counter)), List.of(), exists);

        assertThat(plan.openings()).containsExactly(new Recovered(untitled), new Recovered(newer),
                new Recovered(untitledToo));
        assertThat(plan.windows()).containsExactly(window(LEFT, counter));
    }
}
