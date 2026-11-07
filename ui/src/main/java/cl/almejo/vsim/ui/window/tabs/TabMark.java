package cl.almejo.vsim.ui.window.tabs;

import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The AI's mark on a tab (decided mockup 2026-11-06, option A): a small robot while the AI reads, edits or runs in
 * the tab's place ({@link Phase#WORKING}, it bobs), grey a while after it finished there ({@link Phase#FINISHED}).
 * {@code taskTitle} is the title of the AI's task, when the work belongs to one.
 */
public record TabMark(Phase phase, Optional<String> taskTitle) {

    /** Working beats finished: the stronger mark wins where two meet (a folded group's circuit tab). */
    public enum Phase { FINISHED, WORKING }

    public TabMark {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(taskTitle, "taskTitle");
    }

    public boolean isWorking() {
        return phase == Phase.WORKING;
    }

    /** «La IA está trabajando aquí: «Probar los enlaces»», or the line without a task, or its «trabajó» forms. */
    public String tooltip() {
        return taskTitle.map(title -> Messages.format(isWorking() ? "tab.ai.working" : "tab.ai.finished", title))
                .orElseGet(() -> Messages.text(isWorking() ? "tab.ai.workingNoTask" : "tab.ai.finishedNoTask"));
    }

    /** The strongest of {@code marks}: a working mark over a finished one; empty when there is none. */
    public static Optional<TabMark> strongest(Stream<Optional<TabMark>> marks) {
        return marks.flatMap(Optional::stream).max(Comparator.comparing(TabMark::phase));
    }
}
