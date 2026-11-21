package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The one-line forms of a machine's problems, for the rows of its Propiedades (fix round 3, K). */
class MachineProblemShortTextTest {

    @AfterEach
    void restore() {
        Messages.initialize(Messages.SPANISH);
    }

    private static StateMachine machine() {
        List<MachineState> states = List.of(
                new MachineState("a", "A", new DiagramPoint(0, 0), List.of()),
                new MachineState("b", "B", new DiagramPoint(200, 0), List.of()));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "a", "b", "x", List.of()),
                new MachineTransition("t2", "a", "b", "x + y", List.of()));
        return new StateMachine(states, transitions, Optional.of("a"), List.of("x", "y"), List.of(), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }

    private static MachineFinding finding(MachineFinding.Kind kind) {
        return new MachineFinding(kind, List.of("a"), List.of("t1", "t2"), List.of("x"),
                Map.of("x", false, "y", false), kind == MachineFinding.Kind.BAD_CONDITION ? 0 : -1);
    }

    @Test
    void everyKindHasAShortFormInBothLanguagesShorterThanTheSentence() {
        for (Locale language : List.of(Messages.SPANISH, Locale.ENGLISH)) {
            Messages.initialize(language);
            for (MachineFinding.Kind kind : MachineFinding.Kind.values()) {
                String shortText = MachineProblemText.shortSentence(finding(kind), machine());
                String sentence = MachineProblemText.sentence(finding(kind), machine());

                assertThat(shortText).as(language.getLanguage() + " " + kind).doesNotStartWith("!").doesNotContain("{")
                        .isNotBlank();
                assertThat(shortText.length()).as(language.getLanguage() + " " + kind).isLessThan(sentence.length());
            }
        }
    }

    @Test
    void theStayingStateReadsAsAShortLine() {
        MachineFinding stays = new MachineFinding(MachineFinding.Kind.STAYS_FOR_SOME_INPUT, List.of("a"), List.of(),
                List.of(), Map.of("x", false), -1);

        assertThat(MachineProblemText.shortSentence(stays, machine())).isEqualTo("A: falta qué pasa con x = 0");
    }
}
