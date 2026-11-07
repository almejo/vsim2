package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PlaceRef;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.tabs.TabMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The AI's mark on the places it reads, edits or runs (fix round D, item 5n): when it comes, bobs, greys and goes. */
class AiPlaceMarksTest {

    private static final DocumentId CIRCUIT_ID = new DocumentId("circuit-1");
    private static final DocumentId OTHER_ID = new DocumentId("circuit-2");
    private static final String MEMORY = "memoria-chica";
    private static final Optional<String> NO_DEFINITION = Optional.empty();
    private static final Optional<String> NO_CONTEXT = Optional.empty();

    /** A delay that runs when the test's clock passes its time. */
    private final class ClockDelay implements Delay {
        private record Pending(long at, Runnable task) {
        }

        final List<Pending> pending = new ArrayList<>();

        @Override
        public Runnable after(int milliseconds, Runnable task) {
            Pending added = new Pending(now + milliseconds, task);
            pending.add(added);
            return () -> pending.remove(added);
        }

        void runDue() {
            for (Pending due : List.copyOf(pending)) {
                if (due.at() <= now && pending.remove(due)) {
                    due.task().run();
                }
            }
        }
    }

    private long now = 10_000;
    private final ClockDelay delay = new ClockDelay();
    private final AiPlaceMarks marks = new AiPlaceMarks(() -> now, delay);
    private final List<String> changes = new ArrayList<>();
    private OpenDocument circuit;
    private OpenDocument other;

    @BeforeEach
    void open() {
        Messages.initialize(Messages.SPANISH);
        circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
        other = OpenDocument.createEmpty(StandardComponentTypes.registry());
        marks.addListener(() -> changes.add("changed"));
    }

    @AfterEach
    void close() {
        circuit.close();
        other.close();
    }

    private void advance(long milliseconds) {
        now += milliseconds;
        delay.runDue();
    }

    private Optional<TabMark> mainMark() {
        return marks.markOf(circuit, NO_DEFINITION, NO_CONTEXT);
    }

    private Optional<TabMark> memoryMark(Optional<String> context) {
        return marks.markOf(circuit, Optional.of(MEMORY), context);
    }

    private static TaskInfo task(String title) {
        return new TaskInfo("task-1", CIRCUIT_ID, title);
    }

    @Test
    void aCallWithoutATaskMarksThePlaceWorkingForAMomentThenGreyThenNothing() {
        marks.touched(CIRCUIT_ID, circuit, PlaceRef.main());

        assertThat(mainMark()).contains(new TabMark(TabMark.Phase.WORKING, Optional.empty()));
        assertThat(changes).isNotEmpty();

        changes.clear();
        advance(AiPlaceMarks.BRIEF_MILLISECONDS);
        assertThat(mainMark()).contains(new TabMark(TabMark.Phase.FINISHED, Optional.empty()));
        assertThat(changes).as("the windows hear that it turned grey").isNotEmpty();

        changes.clear();
        advance(AiPlaceMarks.FINISHED_MILLISECONDS);
        assertThat(mainMark()).isEmpty();
        assertThat(changes).as("and that it went").isNotEmpty();
        assertThat(delay.pending).as("nothing left to wait for").isEmpty();
    }

    @Test
    void duringATaskThePlaceWorksWithTheTaskTitleUntilTheTaskEnds() {
        marks.taskStarted(task("Probar los enlaces"));
        marks.touched(CIRCUIT_ID, circuit, new PlaceRef(MEMORY, Optional.of("c20")));

        advance(60_000);
        assertThat(memoryMark(Optional.of("c20")))
                .contains(new TabMark(TabMark.Phase.WORKING, Optional.of("Probar los enlaces")));
        assertThat(mainMark()).as("only the place it works in, not the whole group").isEmpty();

        marks.taskEnded(task("Probar los enlaces"));
        assertThat(memoryMark(Optional.of("c20")))
                .contains(new TabMark(TabMark.Phase.FINISHED, Optional.of("Probar los enlaces")));
        advance(AiPlaceMarks.FINISHED_MILLISECONDS);
        assertThat(memoryMark(Optional.of("c20"))).isEmpty();
    }

    @Test
    void theTaskWorksWhereItLastWentAndThePlaceItLeftTurnsGrey() {
        marks.taskStarted(task("Armar"));
        marks.touched(CIRCUIT_ID, circuit, PlaceRef.main());
        marks.touched(CIRCUIT_ID, circuit, new PlaceRef(MEMORY, Optional.empty()));

        assertThat(memoryMark(NO_CONTEXT).map(TabMark::phase)).contains(TabMark.Phase.WORKING);
        assertThat(mainMark().map(TabMark::phase)).contains(TabMark.Phase.FINISHED);
    }

    /** Round E (5q): the place a running task works in, where its «Presentar» panel shows. */
    @Test
    void aRunningTaskWorksInTheLastPlaceItTouchedUntilItEnds() {
        TaskInfo task = task("Armar");
        marks.taskStarted(task);
        assertThat(marks.placeOf("task-1")).as("no place touched yet").isEmpty();

        marks.touched(CIRCUIT_ID, circuit, PlaceRef.main());
        PlaceRef memory = new PlaceRef(MEMORY, Optional.of("c20"));
        marks.touched(CIRCUIT_ID, circuit, memory);
        assertThat(marks.placeOf("task-1")).contains(memory);
        assertThat(marks.placeOf("another-task")).isEmpty();

        marks.taskEnded(task);
        assertThat(marks.placeOf("task-1")).as("an ended task works nowhere").isEmpty();
    }

    @Test
    void aCallOnAnotherCircuitDuringATaskIsOnlyAMoment() {
        marks.taskStarted(task("Armar"));
        marks.touched(OTHER_ID, other, PlaceRef.main());

        assertThat(marks.markOf(other, NO_DEFINITION, NO_CONTEXT))
                .contains(new TabMark(TabMark.Phase.WORKING, Optional.empty()));
        advance(AiPlaceMarks.BRIEF_MILLISECONDS);
        assertThat(marks.markOf(other, NO_DEFINITION, NO_CONTEXT).map(TabMark::phase))
                .contains(TabMark.Phase.FINISHED);
    }

    @Test
    void aPieceContextMarksTheTabsShowingThatPieceAndAPlaceWithoutValuesMarksEveryTabOfTheComponent() {
        marks.touched(CIRCUIT_ID, circuit, new PlaceRef(MEMORY, Optional.of("c20")));

        assertThat(memoryMark(Optional.of("c20"))).isPresent();
        assertThat(memoryMark(Optional.of("c21"))).as("another piece's values").isEmpty();
        assertThat(memoryMark(NO_CONTEXT)).as("«Sin valores»").isEmpty();

        marks.touched(OTHER_ID, other, new PlaceRef(MEMORY, Optional.empty()));
        assertThat(marks.markOf(other, Optional.of(MEMORY), Optional.of("c21"))).isPresent();
        assertThat(marks.markOf(other, Optional.of(MEMORY), NO_CONTEXT)).isPresent();
        assertThat(marks.markOf(other, NO_DEFINITION, NO_CONTEXT)).isEmpty();
    }

    @Test
    void visitingAGreyPlaceTakesItsMarkAwayButAWorkingMarkStays() {
        marks.taskStarted(task("Armar"));
        marks.touched(CIRCUIT_ID, circuit, PlaceRef.main());
        marks.visited(circuit, NO_DEFINITION, NO_CONTEXT);
        assertThat(mainMark().map(TabMark::phase)).contains(TabMark.Phase.WORKING);

        marks.taskEnded(task("Armar"));
        changes.clear();
        marks.visited(circuit, NO_DEFINITION, NO_CONTEXT);
        assertThat(mainMark()).isEmpty();
        assertThat(changes).isNotEmpty();
    }

    @Test
    void theTooltipNamesTheTask() {
        assertThat(new TabMark(TabMark.Phase.WORKING, Optional.of("Probar los enlaces")).tooltip())
                .isEqualTo("La IA está trabajando aquí: «Probar los enlaces»");
        assertThat(new TabMark(TabMark.Phase.WORKING, Optional.empty()).tooltip())
                .isEqualTo("La IA está trabajando aquí");
        assertThat(new TabMark(TabMark.Phase.FINISHED, Optional.of("Armar")).tooltip())
                .isEqualTo("La IA trabajó aquí: «Armar»");
    }
}
