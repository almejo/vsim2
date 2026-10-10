package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.i18n.Messages;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The model behind the recovery dialog (spec §4.1): one row with a check box per copy left by a run that did not
 * close properly. Readable copies start checked; an unreadable one starts unchecked and only «Descartar» applies to
 * it. «Recuperar» opens the checked readable copies, «Descartar» deletes the checked copies, «Decidir después» keeps
 * them all; whatever a decision does not take stays for the next start.
 *
 * @param <C> the copy type ({@link RecoveryCopy} in the application, plain values in tests)
 */
public final class RecoveryChoices<C> {

    public enum Decision { RECOVER, DISCARD, LATER }

    /** What the dialog needs to know about one copy. */
    public record Candidate<C>(C copy, String displayName, Optional<Path> originalPath, Instant savedAt,
                               boolean readable) {
    }

    /** One row: the copy, its text, and the note under it («⚠ el archivo cambió después», «no se puede leer»). */
    public record Row<C>(C copy, String text, Optional<String> note, boolean readable) {
    }

    /** The copies to open and the copies to delete; every other copy is kept. */
    public record Outcome<C>(List<C> recover, List<C> discard) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final List<Row<C>> rows;
    private final boolean[] selected;

    /**
     * @param fileModified the modification time of an original file, empty when it cannot be read
     * @param clock        "today" and the time zone the times are shown in
     */
    public RecoveryChoices(List<Candidate<C>> candidates, Function<Path, Optional<Instant>> fileModified, Clock clock) {
        List<Row<C>> built = new ArrayList<>();
        selected = new boolean[candidates.size()];
        LocalDate today = LocalDate.now(clock);
        for (int index = 0; index < candidates.size(); index++) {
            Candidate<C> candidate = candidates.get(index);
            built.add(new Row<>(candidate.copy(), text(candidate, today, clock.getZone()),
                    note(candidate, fileModified), candidate.readable()));
            selected[index] = candidate.readable();
        }
        rows = List.copyOf(built);
    }

    public List<Row<C>> rows() {
        return rows;
    }

    public boolean isSelected(int index) {
        return selected[index];
    }

    public void setSelected(int index, boolean value) {
        selected[index] = value;
    }

    public Outcome<C> outcome(Decision decision) {
        List<C> recover = new ArrayList<>();
        List<C> discard = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            if (!selected[index]) {
                continue;
            }
            Row<C> row = rows.get(index);
            switch (decision) {
                case RECOVER -> {
                    if (row.readable()) {
                        recover.add(row.copy());
                    }
                }
                case DISCARD -> discard.add(row.copy());
                case LATER -> {
                }
            }
        }
        return new Outcome<>(List.copyOf(recover), List.copyOf(discard));
    }

    private static String text(Candidate<?> candidate, LocalDate today, ZoneId zone) {
        ZonedDateTime saved = candidate.savedAt().atZone(zone);
        String time = TIME.format(saved);
        if (saved.toLocalDate().equals(today)) {
            return Messages.format("recovery.row.today", candidate.displayName(), time);
        }
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Messages.locale()).format(saved);
        return Messages.format("recovery.row.earlier", candidate.displayName(), date, time);
    }

    private static Optional<String> note(Candidate<?> candidate, Function<Path, Optional<Instant>> fileModified) {
        if (!candidate.readable()) {
            return Optional.of(Messages.text("recovery.row.unreadable"));
        }
        boolean changedAfter = candidate.originalPath()
                .flatMap(fileModified)
                .map(modified -> modified.isAfter(candidate.savedAt()))
                .orElse(false);
        return changedAfter ? Optional.of(Messages.text("recovery.row.fileChanged")) : Optional.empty();
    }
}
