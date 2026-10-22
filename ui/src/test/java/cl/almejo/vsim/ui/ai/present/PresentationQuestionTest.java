package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.ai.QuestionCard;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JRootPane;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI's questions in the «Presentar» panel (IA.3 Task 8), and the hint «Claude está pensando…» after 20 s of
 * silence with nothing waiting for the user.
 */
class PresentationQuestionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final DocumentId THIS_CIRCUIT = new DocumentId("this");
    private static final TaskInfo TASK = new TaskInfo("t1", THIS_CIRCUIT, "Contador");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Flip-flops", List.of("Pongo los flip-flops", "Conecto el reloj")));
    private static final Question QUESTION = new Question("question-1", TASK.taskId(), THIS_CIRCUIT,
            "¿De cuántos bits?", List.of("2", "3", "4"), true);
    private static final long SECOND = 1_000_000_000L;

    private final AppPreferences saved = AppPreferences.inMemory();
    private final FakePresentationEvents events = new FakePresentationEvents();
    private final RecordingControls controls = new RecordingControls();
    private final Deque<Runnable> editorQueue = new ArrayDeque<>();
    private final AtomicLong clock = new AtomicLong(10 * SECOND);
    private OpenDocument document;
    private EditorView view;
    private PresentationExtension extension;
    private Runnable dispose;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        saved.setAnimateViewMoves(false);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), saved.layered(), new ComponentLibrary(), List.of()), NO_WINDOW);
        JRootPane root = new JRootPane();
        root.getContentPane().add(view);
        root.setSize(1200, 800);
        layOut(root);
        extension = new PresentationExtension(events, controls, any -> THIS_CIRCUIT, editorQueue::add, clock::get);
        dispose = extension.install(view);
    }

    @AfterEach
    void close() {
        if (dispose != null) {
            dispose.run();
        }
        view.dispose();
        document.close();
    }

    private PresentationFeatures features() {
        return extension.featuresOf(view).orElseThrow();
    }

    private PresentationPanel panel() {
        return features().panel();
    }

    private void runEditorThread() {
        while (!editorQueue.isEmpty()) {
            editorQueue.poll().run();
        }
    }

    private void presentATask() {
        saved.setAiPresenting(true);
        events.start(TASK);
        events.plan(TASK, STAGES, 0, 0);
        runEditorThread();
    }

    private void waitSeconds(long seconds) {
        clock.addAndGet(seconds * SECOND);
        features().thinking().tick();
    }

    @Test
    void theQuestionShowsInThePanelAndAButtonAnswersIt() {
        presentATask();

        events.ask(QUESTION);
        runEditorThread();

        Optional<QuestionCard> card = panel().questionCard();
        assertThat(card).isPresent();
        assertThat(card.get().question()).isEqualTo(QUESTION);
        JButton four = (JButton) find(card.get(), "4");
        four.doClick();
        assertThat(controls.calls).contains("answer question-1 choice 4");

        events.closeQuestion(TASK.taskId(), QUESTION.questionId(), true);
        runEditorThread();
        assertThat(panel().questionCard()).isEmpty();
    }

    @Test
    void aWrittenAnswerGoesToTheControlsAndTheCardStaysTheSameWhileThePanelRefreshes() {
        presentATask();
        events.ask(QUESTION);
        runEditorThread();
        QuestionCard card = panel().questionCard().orElseThrow();
        card.field().setText("5 bits");

        events.check(TASK.taskId(), 0, true, "ok"); // the panel shows itself again
        runEditorThread();
        assertThat(panel().questionCard()).containsSame(card);
        assertThat(card.field().getText()).isEqualTo("5 bits");

        card.field().postActionEvent();
        assertThat(controls.calls).contains("answer question-1 text 5 bits");
    }

    @Test
    void stoppingTheTaskTakesTheQuestionAway() {
        presentATask();
        events.ask(QUESTION);
        runEditorThread();

        events.stop(TASK);
        events.closeQuestion(TASK.taskId(), QUESTION.questionId(), false);
        runEditorThread();

        assertThat(panel().questionCard()).isEmpty();
    }

    // --- «Claude está pensando…» ---

    @Test
    void afterTwentySecondsOfSilenceThePanelSaysClaudeIsThinkingAndTheNextCallTakesItAway() {
        presentATask();

        waitSeconds(19);
        assertThat(panel().thinkingHint().isVisible()).isFalse();
        waitSeconds(1);
        assertThat(panel().thinkingHint().isVisible()).isTrue();
        assertThat(panel().thinkingHint().getText())
                .isEqualTo("Claude está pensando o te preguntó algo en Claude Code");
        assertThat(panel().thinkingHint().tone()).isEqualTo(StageCard.Tone.MUTED);

        events.activity(); // any call of the AI
        runEditorThread();
        assertThat(panel().thinkingHint().isVisible()).isFalse();
    }

    @Test
    void neverWhileAStepWaitsAPauseRunsOrAQuestionWaits() {
        presentATask();
        events.propose(step(TASK.taskId(), 0, 0, "Pongo los flip-flops"));
        runEditorThread();
        waitSeconds(60);
        assertThat(panel().thinkingHint().isVisible()).isFalse();

        events.apply(step(TASK.taskId(), 0, 0, "Pongo los flip-flops"));
        events.pace(TASK.taskId(), Duration.ofSeconds(1));
        runEditorThread();
        waitSeconds(60);
        assertThat(panel().thinkingHint().isVisible()).isFalse();

        events.apply(step(TASK.taskId(), 0, 1, "Conecto el reloj"));
        events.ask(QUESTION);
        runEditorThread();
        waitSeconds(60);
        assertThat(panel().thinkingHint().isVisible()).isFalse();

        events.closeQuestion(TASK.taskId(), QUESTION.questionId(), true);
        runEditorThread();
        waitSeconds(20);
        assertThat(panel().thinkingHint().isVisible()).isTrue();
    }

    @Test
    void theHintGoesWithTheTaskAndDisposeTakesTheActivityListenerBack() {
        presentATask();
        waitSeconds(30);
        assertThat(panel().thinkingHint().isVisible()).isTrue();

        events.end(TASK);
        runEditorThread();
        assertThat(panel().thinkingHint().isVisible()).isFalse();

        dispose.run();
        dispose = null;
        assertThat(events.activityListeners).isEmpty();
    }

    private static Component find(Container container, String buttonText) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && buttonText.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container inner) {
                Component found = find(inner, buttonText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOut(inner);
            }
        }
    }
}
