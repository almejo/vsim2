package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The model edits the diagram's gestures and the right panel make (plan 2026-11-14 task 5). */
class MachineEditsTest {

    private final StateMachine detector = MachineFixtures.detector();

    @Test
    void aNewStateTakesTheFirstFreeNameAndIdAndGoesWhereAsked() {
        StateMachine renamed = detector.withState("s1", state -> state.withName("S7"));

        StateMachine added = MachineEdits.withNewState(renamed, new DiagramPoint(40, 200));

        MachineState state = added.states().get(added.states().size() - 1);
        assertThat(state.name()).isEqualTo("S1");
        assertThat(state.id()).isEqualTo("s3");
        assertThat(state.position()).isEqualTo(new DiagramPoint(40, 200));
        assertThat(added.initialStateId()).contains("s0");
    }

    @Test
    void theFirstStateOfAnEmptyMachineIsItsInitialState() {
        StateMachine empty = detector.withInitialStateId(Optional.empty()).withTransitions(List.of())
                .withStates(List.of());

        StateMachine added = MachineEdits.withNewState(empty, DiagramPoint.ORIGIN);

        assertThat(added.initialStateId()).contains("s0");
    }

    @Test
    void aNewArrowJoinsTheTwoStatesWithoutACondition() {
        StateMachine joined = MachineEdits.withNewTransition(detector, "s2", "s1");

        MachineTransition arrow = joined.transitions().get(joined.transitions().size() - 1);
        assertThat(arrow.id()).isEqualTo("t7");
        assertThat(arrow.fromId()).isEqualTo("s2");
        assertThat(arrow.toId()).isEqualTo("s1");
        assertThat(arrow.isOtherwise()).isTrue();
    }

    @Test
    void deletingAStateTakesItsArrowsAndAnInitialStateLeavesNoInitial() {
        StateMachine without = MachineEdits.without(detector, Set.of("s0"), Set.of("t3"));

        assertThat(without.states()).extracting(MachineState::id).containsExactly("s1", "s2");
        assertThat(without.transitions()).extracting(MachineTransition::id).containsExactly("t4");
        assertThat(without.initialStateId()).isEmpty();
    }

    @Test
    void movingStatesMovesOnlyThoseStates() {
        StateMachine moved = MachineEdits.withStatesMoved(detector, Set.of("s1", "s2"), 10, -20);

        assertThat(moved.state("s0").orElseThrow().position()).isEqualTo(new DiagramPoint(0, 0));
        assertThat(moved.state("s1").orElseThrow().position()).isEqualTo(new DiagramPoint(170, -20));
        assertThat(moved.state("s2").orElseThrow().position()).isEqualTo(new DiagramPoint(330, -20));
    }

    @Test
    void aStateNameMustBeGivenAndFree() {
        assertThat(MachineEdits.stateNameProblem(detector, "s1", " ")).contains("machine.stateName.blank");
        assertThat(MachineEdits.stateNameProblem(detector, "s1", "S2")).contains("machine.stateName.taken");
        assertThat(MachineEdits.stateNameProblem(detector, "s1", "S1")).isEmpty();
        assertThat(MachineEdits.stateNameProblem(detector, "s1", "Espera")).isEmpty();
    }

    @Test
    void theInitialStateIsChosenOrTakenAway() {
        assertThat(MachineEdits.withInitial(detector, "s2", true).initialStateId()).contains("s2");
        assertThat(MachineEdits.withInitial(detector, "s0", false).initialStateId()).isEmpty();
        assertThat(MachineEdits.withInitial(detector, "s1", false).initialStateId()).contains("s0");
    }

    @Test
    void mooreOutputsAndConditionsAreSet() {
        StateMachine edited = MachineEdits.withMooreOutputs(detector, "s1", List.of(new OutputAssignment("Y", false)));
        edited = MachineEdits.withCondition(edited, "t5", "a +");

        assertThat(edited.state("s1").orElseThrow().outputs()).containsExactly(new OutputAssignment("Y", false));
        assertThat(edited.transition("t5").orElseThrow().condition()).isEqualTo("a +");
    }

    @Test
    void mealyOutputsAreSetOnAnArrowAndKeepItsCondition() {
        StateMachine edited = MachineEdits.withMealyOutputs(detector, "t2", List.of(new OutputAssignment("Z", true)));

        assertThat(edited.transition("t2").orElseThrow().outputs()).containsExactly(new OutputAssignment("Z", true));
        assertThat(edited.transition("t2").orElseThrow().condition())
                .isEqualTo(detector.transition("t2").orElseThrow().condition());
    }

    @Test
    void aDuplicateLandsClearOfEveryStateNotOnlyOfTheOneItCopies() {
        // S1 (at 160, 0) duplicated: a shift of one radius put it half over S1 (hand test of hito 2, round 2)
        StateMachine piece = MachineEdits.pieceOf(detector, Set.of("s1")).orElseThrow();

        MachineEdits.Pasted pasted = MachineEdits.withPasted(detector, piece);

        assertClearOfTheOthers(pasted);
    }

    @Test
    void aPasteBesideAStateButNotOnItsCentreStillMovesClear() {
        // 25 to the right of S0's centre: the old rule (only nearly coinciding centres) left it over S0
        MachineEdits.Pasted pasted = MachineEdits.withPasted(detector, lone(new DiagramPoint(25, 10)));

        assertClearOfTheOthers(pasted);
        MachineState added = pasted.machine().state(pasted.stateIds().iterator().next()).orElseThrow();
        assertThat(Math.hypot(added.position().x() - 25, added.position().y() - 10))
                .as("the nearest free place along the search, not far away").isLessThan(200);
    }

    @Test
    void aPasteWhereNothingIsInTheWayStaysWhereItWas() {
        MachineEdits.Pasted pasted = MachineEdits.withPasted(detector, lone(new DiagramPoint(0, 400)));

        assertThat(pasted.machine().state(pasted.stateIds().iterator().next()).orElseThrow().position())
                .isEqualTo(new DiagramPoint(0, 400));
    }

    private static StateMachine lone(DiagramPoint position) {
        return new StateMachine(List.of(new MachineState("a", "LEJOS", position, List.of())), List.of(),
                Optional.empty(), List.of(), List.of(), false, false, StateMachine.EDGE_FALLING, List.of());
    }

    /** Every pasted state's circle is at least {@link MachineEdits#PASTE_GAP} from every state that was there. */
    private static void assertClearOfTheOthers(MachineEdits.Pasted pasted) {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(pasted.machine());
        for (String added : pasted.stateIds()) {
            for (MachineState state : pasted.machine().states()) {
                if (pasted.stateIds().contains(state.id())) {
                    continue;
                }
                double between = geometry.centre(added).distance(geometry.centre(state.id()))
                        - geometry.radius(added) - geometry.radius(state.id());
                assertThat(between).as("%s clear of %s", added, state.name())
                        .isGreaterThanOrEqualTo(MachineEdits.PASTE_GAP);
            }
        }
    }

    private static MachineNote note(String id, double x, double y) {
        return new MachineNote(id, new DiagramPoint(x, y), MachineNote.DEFAULT_WIDTH, MachineNote.DEFAULT_HEIGHT,
                StickyColor.YELLOW, id);
    }

    @Test
    void aNewNoteIsYellowEmptyOfTheDefaultSizeWithTheFirstFreeId() {
        StateMachine withOne = detector.withNotes(List.of(note("n1", 0, 0)));

        StateMachine added = MachineEdits.withNewNote(withOne, new DiagramPoint(30, 40));

        MachineNote created = MachineEdits.lastNote(added);
        assertThat(created.id()).isEqualTo("n2");
        assertThat(created.position()).isEqualTo(new DiagramPoint(30, 40));
        assertThat(created.color()).isEqualTo(StickyColor.YELLOW);
        assertThat(created.text()).isEmpty();
        assertThat(created.width()).isEqualTo(MachineNote.DEFAULT_WIDTH);
        assertThat(created.height()).isEqualTo(MachineNote.DEFAULT_HEIGHT);
    }

    @Test
    void movingAndDeletingTakeTheSelectedNotes() {
        StateMachine withNotes = detector.withNotes(List.of(note("n1", 0, 200), note("n2", 300, 200)));

        StateMachine moved = MachineEdits.withMoved(withNotes, Set.of("s0"), Set.of("n2"), 10, 20);
        assertThat(moved.notes().get(0).position()).isEqualTo(new DiagramPoint(0, 200));
        assertThat(moved.notes().get(1).position()).isEqualTo(new DiagramPoint(310, 220));
        assertThat(moved.state("s0").orElseThrow().position()).isEqualTo(new DiagramPoint(10, 20));

        StateMachine without = MachineEdits.without(withNotes, Set.of(), Set.of(), Set.of("n1"));
        assertThat(without.notes()).extracting(MachineNote::id).containsExactly("n2");
        assertThat(without.states()).hasSize(3);
    }

    @Test
    void aResizedNoteKeepsWholeStepsOfEightAndTheSmallestSize() {
        StateMachine withNote = detector.withNotes(List.of(note("n1", 0, 200)));

        assertThat(MachineEdits.withNoteResized(withNote, "n1", 61, 68).notes().getFirst())
                .extracting(MachineNote::width, MachineNote::height).containsExactly(224.0, 192.0);
        assertThat(MachineEdits.withNoteResized(withNote, "n1", -500, -500).notes().getFirst())
                .extracting(MachineNote::width, MachineNote::height)
                .containsExactly(MachineNote.MINIMUM_WIDTH, MachineNote.MINIMUM_HEIGHT);
    }

    @Test
    void aPieceTakesTheSelectedNotesAndAPasteGivesThemNewIdsShiftedWithTheStates() {
        StateMachine withNotes = detector.withNotes(List.of(note("n1", 160, 150), note("n2", 600, 600)));

        StateMachine piece = MachineEdits.pieceOf(withNotes, Set.of("s1"), Set.of("n1")).orElseThrow();
        assertThat(piece.notes()).extracting(MachineNote::id).containsExactly("n1");
        MachineEdits.Pasted pasted = MachineEdits.withPasted(withNotes, piece);

        assertThat(pasted.noteIds()).containsExactly("n3");
        MachineNote copy = pasted.machine().notes().get(2);
        MachineState copyOfS1 = pasted.machine().state(pasted.stateIds().iterator().next()).orElseThrow();
        double shift = copyOfS1.position().x() - 160;
        assertThat(shift).isPositive();
        assertThat(copy.position()).isEqualTo(new DiagramPoint(160 + shift, 150 + shift));
        assertThat(MachineEdits.pieceOf(withNotes, Set.of(), Set.of())).isEmpty();
        assertThat(MachineEdits.pieceOf(withNotes, Set.of(), Set.of("n2")).orElseThrow().states()).isEmpty();
    }
}
