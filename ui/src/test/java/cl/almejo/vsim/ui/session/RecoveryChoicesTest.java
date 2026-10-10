package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryChoicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T18:00:00Z"), ZoneOffset.UTC);
    private static final Path ORIGINAL = Path.of("/home/ana/contador.vsim");

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static RecoveryChoices.Candidate<String> candidate(String copy, String name, Optional<Path> original,
                                                               String savedAt, boolean readable) {
        return new RecoveryChoices.Candidate<>(copy, name, original, Instant.parse(savedAt), readable);
    }

    private static RecoveryChoices<String> choices(List<RecoveryChoices.Candidate<String>> candidates) {
        return new RecoveryChoices<>(candidates, path -> Optional.empty(), CLOCK);
    }

    @Test
    void eachRowNamesTheCircuitAndWhenItWasSaved() {
        RecoveryChoices<String> choices = choices(List.of(
                candidate("a", "contador", Optional.of(ORIGINAL), "2026-10-09T14:32:00Z", true),
                candidate("b", "Sin título", Optional.empty(), "2026-10-07T09:05:00Z", true)));

        assertThat(choices.rows().get(0).text()).isEqualTo("contador — guardado a las 14:32");
        assertThat(choices.rows().get(1).text()).startsWith("Sin título — guardado el ").endsWith(" a las 09:05");
        assertThat(choices.rows()).allSatisfy(row -> assertThat(row.note()).isEmpty());
        assertThat(choices.rows()).extracting(RecoveryChoices.Row::copy).containsExactly("a", "b");
    }

    @Test
    void aFileChangedAfterTheCopyIsPointedOut() {
        List<RecoveryChoices.Candidate<String>> candidates =
                List.of(candidate("a", "contador", Optional.of(ORIGINAL), "2026-10-09T14:32:00Z", true));
        RecoveryChoices<String> newer = new RecoveryChoices<>(candidates,
                path -> path.equals(ORIGINAL) ? Optional.of(Instant.parse("2026-10-09T15:00:00Z")) : Optional.empty(),
                CLOCK);
        assertThat(newer.rows().get(0).note()).contains("⚠ el archivo cambió después");

        RecoveryChoices<String> older = new RecoveryChoices<>(candidates,
                path -> Optional.of(Instant.parse("2026-10-09T14:00:00Z")), CLOCK);
        assertThat(older.rows().get(0).note()).isEmpty();
    }

    @Test
    void anUnreadableCopyStartsUncheckedAndCanOnlyBeDiscarded() {
        RecoveryChoices<String> choices = choices(List.of(
                candidate("good", "contador", Optional.of(ORIGINAL), "2026-10-09T14:32:00Z", true),
                candidate("broken", "sumador", Optional.empty(), "2026-10-09T14:33:00Z", false)));
        assertThat(choices.isSelected(0)).isTrue();
        assertThat(choices.isSelected(1)).isFalse();
        assertThat(choices.rows().get(1).note()).contains("no se puede leer");
        assertThat(choices.rows().get(1).readable()).isFalse();

        choices.setSelected(1, true);
        assertThat(choices.outcome(RecoveryChoices.Decision.RECOVER))
                .isEqualTo(new RecoveryChoices.Outcome<>(List.of("good"), List.of()));
        assertThat(choices.outcome(RecoveryChoices.Decision.DISCARD))
                .isEqualTo(new RecoveryChoices.Outcome<>(List.of(), List.of("good", "broken")));
    }

    @Test
    void decidingLaterKeepsEverythingAndUncheckedCopiesAreKeptByEveryDecision() {
        RecoveryChoices<String> choices = choices(List.of(
                candidate("a", "contador", Optional.empty(), "2026-10-09T14:32:00Z", true),
                candidate("b", "sumador", Optional.empty(), "2026-10-09T14:33:00Z", true)));
        choices.setSelected(1, false);

        assertThat(choices.outcome(RecoveryChoices.Decision.LATER))
                .isEqualTo(new RecoveryChoices.Outcome<>(List.of(), List.of()));
        assertThat(choices.outcome(RecoveryChoices.Decision.RECOVER))
                .isEqualTo(new RecoveryChoices.Outcome<>(List.of("a"), List.of()));
        assertThat(choices.outcome(RecoveryChoices.Decision.DISCARD))
                .isEqualTo(new RecoveryChoices.Outcome<>(List.of(), List.of("a")));
    }

    @Test
    void ofTwoCopiesOfTheSameOriginalOnlyTheNewestStartsChecked() {
        RecoveryChoices<String> choices = choices(List.of(
                candidate("older", "contador", Optional.of(ORIGINAL), "2026-10-09T14:32:00Z", true),
                candidate("other", "sumador", Optional.of(Path.of("/home/ana/sumador.vsim")), "2026-10-09T14:00:00Z",
                        true),
                candidate("newer", "contador", Optional.of(Path.of("/home/ana/./contador.vsim")),
                        "2026-10-09T15:10:00Z", true),
                candidate("untitled1", "Sin título", Optional.empty(), "2026-10-09T14:00:00Z", true),
                candidate("untitled2", "Sin título", Optional.empty(), "2026-10-09T14:05:00Z", true)));
        assertThat(choices.isSelected(0)).isFalse();
        assertThat(choices.isSelected(1)).isTrue();
        assertThat(choices.isSelected(2)).isTrue();
        assertThat(choices.isSelected(3)).isTrue();
        assertThat(choices.isSelected(4)).isTrue();
    }

    @Test
    void anUnreadableNewerCopyDoesNotUncheckTheReadableOne() {
        RecoveryChoices<String> choices = choices(List.of(
                candidate("readable", "contador", Optional.of(ORIGINAL), "2026-10-09T14:32:00Z", true),
                candidate("broken", "contador", Optional.of(ORIGINAL), "2026-10-09T15:10:00Z", false)));
        assertThat(choices.isSelected(0)).isTrue();
        assertThat(choices.isSelected(1)).isFalse();
    }
}
