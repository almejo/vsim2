package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** What tests of other packages (the window's) need of a tab's test mode, without showing a dialog. */
public final class MachineTestModes {

    private MachineTestModes() {
    }

    /** Answers a test mode's questions with scripted option texts (none left: closed, -1), and records them. */
    public static final class ScriptedQuestions implements MachineTestQuestions {
        private final Deque<String> answers = new ArrayDeque<>();
        private final List<String> asked = new ArrayList<>();

        @Override
        public int ask(Component parent, String message, List<String> offered) {
            asked.add(message);
            String answer = answers.poll();
            return answer == null ? -1 : offered.indexOf(answer);
        }

        /** The next question is answered with the option {@code text}. */
        public ScriptedQuestions then(String text) {
            answers.add(text);
            return this;
        }

        /** The questions asked so far. */
        public List<String> asked() {
            return asked;
        }
    }

    /** {@code view}'s machine mode asks through {@code questions} from now on: call it before the test starts. */
    public static ScriptedQuestions answerWith(EditorView view, ScriptedQuestions questions) {
        MachineMode.of(view).orElseThrow().askWith(questions);
        return questions;
    }

    /** Sets the first input to 1 in the first column of the chosen test of {@code view}'s test mode: one draft step. */
    public static void setFirstInput(EditorView view) {
        MachineTestMode mode = MachineMode.of(view).orElseThrow().testMode().orElseThrow();
        String inputId = mode.machine().pins(MachinePinEdits.Side.INPUT).getFirst().id();
        mode.editTests(tests -> {
            List<MachineTest> changed = new ArrayList<>(tests);
            changed.set(0, MachineTests.withInput(tests.getFirst(), 0, inputId, true));
            return changed;
        }, "cambiar x");
    }

    /** Opens the name of state {@code stateId} on {@code view}'s diagram and types {@code text}, without confirming. */
    public static void typeStateName(EditorView view, String stateId, String text) {
        MachineInlineEditor inline = MachineMode.of(view).orElseThrow().inlineEditor();
        inline.editStateName(stateId);
        inline.editor().textComponent().setText(text);
    }

    /** The draft {@code view}'s test mode shows (its tests as the table sees them), while it tests. */
    public static Optional<StateMachine> draftOf(EditorView view) {
        return MachineMode.of(view).flatMap(MachineMode::testMode).map(MachineTestMode::machine);
    }
}
