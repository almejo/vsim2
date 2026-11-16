package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineChecks;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineFinding.Kind;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The words of a machine's problems, as the mockup writes them («Los problemas, a la vista»). */
class MachineProblemTextTest {

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void twoArrowsTrueAtOnceSayTheInputsAsTheMockupDoes() {
        StateMachine machine = machine(List.of(state("a", "A"), state("b", "B"), state("c", "C")),
                List.of(arrow("t1", "a", "b", "x"), arrow("t2", "a", "c", "x + y"), arrow("t3", "a", "a", ""),
                        arrow("t4", "b", "a", ""), arrow("t5", "c", "a", "")),
                Optional.of("a"), List.of("x", "y"));

        assertThat(sentenceOf(machine, Kind.NONDETERMINISTIC))
                .isEqualTo("Desde A, «x» y «x + y» se cumplen a la vez con x = 1, y = 0: la máquina no sabe a cuál ir.");
    }

    @Test
    void anUnreachableStateAndAStateThatStaysReadAsInTheMockup() {
        StateMachine machine = machine(List.of(state("a", "A"), state("b", "B"), state("c", "C")),
                List.of(arrow("t1", "a", "b", ""), arrow("t2", "b", "a", "x")), Optional.of("a"), List.of("x"));

        assertThat(sentenceOf(machine, Kind.UNREACHABLE_STATE)).isEqualTo("El estado C no se alcanza desde el inicio.");
        assertThat(sentenceOf(machine, Kind.STAYS_FOR_SOME_INPUT))
                .isEqualTo("En B no se dice qué pasa con x = 0: se queda en B. ¿Es lo que querías?");
    }

    @Test
    void noInitialStateReadsAsInTheMockup() {
        StateMachine machine = machine(List.of(state("a", "A")), List.of(), Optional.empty(), List.of());

        assertThat(sentenceOf(machine, Kind.NO_INITIAL_STATE)).isEqualTo("Ningún estado está marcado como inicial.");
    }

    @Test
    void aBadConditionSaysItsColumnAndWhy() {
        StateMachine machine = machine(List.of(state("a", "A"), state("b", "B")),
                List.of(arrow("t1", "a", "b", "x +"), arrow("t2", "b", "a", "")), Optional.of("a"), List.of("x"));

        assertThat(sentenceOf(machine, Kind.BAD_CONDITION))
                .startsWith("La condición «x +» de la flecha de A a B no se puede leer (columna ")
                .contains("Falta un operando").endsWith("La flecha no se toma nunca.");
    }

    /** «Z=1» as a condition is an output written without its slash: the sentence says how to write it. */
    @Test
    void anOutputWrittenWithoutTheSlashIsToldWithTheSlash() {
        StateMachine alone = machine(List.of(state("a", "A"), state("b", "B")),
                List.of(arrow("t1", "a", "b", "Z=1")), Optional.of("a"), List.of());
        StateMachine afterACondition = machine(List.of(state("a", "A"), state("b", "B")),
                List.of(arrow("t1", "a", "b", "x Z=1")), Optional.of("a"), List.of("x"));

        assertThat(sentenceOf(alone, Kind.BAD_CONDITION)).contains("«/ Z=1»").contains("barra")
                .endsWith("La flecha no se toma nunca.");
        assertThat(sentenceOf(afterACondition, Kind.BAD_CONDITION)).contains("«x / Z=1»");
    }

    /**
     * A comparison a student writes («x==1», or «x = 1» with x an input) is not an output: no «/» is suggested for it
     * (review of the hand fixes, M6), only the reason it cannot be read.
     */
    @Test
    void aComparisonIsNotToldToTakeTheSlash() {
        StateMachine doubled = machine(List.of(state("a", "A"), state("b", "B")),
                List.of(arrow("t1", "a", "b", "x==1")), Optional.of("a"), List.of("x"));
        StateMachine onAnInput = machine(List.of(state("a", "A"), state("b", "B")),
                List.of(arrow("t1", "a", "b", "x = 1")), Optional.of("a"), List.of("x"));
        StateMachine notABit = machine(List.of(state("a", "A"), state("b", "B")),
                List.of(arrow("t1", "a", "b", "Z=y")), Optional.of("a"), List.of("y"));

        assertThat(sentenceOf(doubled, Kind.BAD_CONDITION)).doesNotContain("/").doesNotContain("barra");
        assertThat(sentenceOf(onAnInput, Kind.BAD_CONDITION)).doesNotContain("/").doesNotContain("barra");
        assertThat(sentenceOf(notABit, Kind.BAD_CONDITION)).doesNotContain("/").doesNotContain("barra");
    }

    @Test
    void theListSaysWhichMachineAndWhichCopy() {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(MachineDefinitions.create("maquina-1", "Detector"));
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf("maquina-1"), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "U1"));
        MachineFinding clock = new MachineFinding(Kind.CLOCK_UNDEFINED, List.of(), List.of(), List.of(), Map.of(),
                MachineFinding.NO_COLUMN);
        MachineFinding initial = new MachineFinding(Kind.NO_INITIAL_STATE, List.of(), List.of(), List.of(), Map.of(),
                MachineFinding.NO_COLUMN);

        assertThat(ProblemText.describe(MachineProblem.of("maquina-1", Optional.of("c1"), clock), document,
                StandardComponentTypes.registry()))
                .startsWith("Máquina «Detector» en U1")
                .endsWith(": CLK no tiene 0 ni 1 (Z o conflicto): la máquina no avanza.");
        assertThat(ProblemText.describe(MachineProblem.of("maquina-1", Optional.empty(), initial), document,
                StandardComponentTypes.registry()))
                .isEqualTo("Máquina «Detector»: Ningún estado está marcado como inicial.");
    }

    @Test
    void everyKindHasItsWordsInBothLanguages() {
        StateMachine machine = machine(List.of(state("a", "A")),
                List.of(arrow("t1", "a", "a", "x").withOutputs(List.of(new OutputAssignment("Z", false)))),
                Optional.of("a"), List.of("x"));
        for (Locale locale : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            Messages.initialize(locale);
            for (Kind kind : Kind.values()) {
                MachineFinding finding = new MachineFinding(kind, List.of("a"), List.of("t1"), List.of("Z"),
                        Map.of("x", true), 0);
                assertThat(MachineProblemText.sentence(finding, machine)).as("%s %s", locale, kind)
                        .isNotBlank().doesNotContain("problem.machine").doesNotContain("{");
            }
        }
    }

    @Test
    void errorsComeBeforeWarningsInTheList() {
        MachineProblem warning = MachineProblem.of("m", Optional.empty(), new MachineFinding(Kind.UNUSED_INPUT,
                List.of(), List.of(), List.of("w"), Map.of(), MachineFinding.NO_COLUMN));
        MachineProblem error = MachineProblem.of("m", Optional.empty(), new MachineFinding(Kind.NO_INITIAL_STATE,
                List.of(), List.of(), List.of(), Map.of(), MachineFinding.NO_COLUMN));

        List<Problem> ordered = ProblemText.ordered(List.of(warning, error));

        assertThat(ordered).containsExactly(error, warning);
    }

    private static String sentenceOf(StateMachine machine, Kind kind) {
        MachineFinding finding = MachineChecks.check(machine).stream().filter(found -> found.kind() == kind)
                .findFirst().orElseThrow();
        return MachineProblemText.sentence(finding, machine);
    }

    private static MachineState state(String id, String name) {
        return new MachineState(id, name, DiagramPoint.ORIGIN, List.of());
    }

    private static MachineTransition arrow(String id, String fromId, String toId, String condition) {
        return new MachineTransition(id, fromId, toId, condition, List.of());
    }

    private static StateMachine machine(List<MachineState> states, List<MachineTransition> transitions,
                                        Optional<String> initial, List<String> inputs) {
        return new StateMachine(states, transitions, initial, inputs, List.of(), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }
}
