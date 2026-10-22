package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.present.StoryboardState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The «Presentar» panel's buttons over the automation API (IA.2 Task 1): each call goes to a background thread, since
 * the API waits for the editor thread and the panel calls from it; answers go back to the editor thread. «Deshacer
 * etapa», «Dejar solo el resultado» and the storyboard's state are the API's storyboard calls (IA.2 Task 2); a
 * refusal (a step still waits, the task still runs, the stages are not separate) is logged and answered.
 */
public final class AutomationPresentationControls implements PresentationControls {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationPresentationControls.class);

    private final AutomationApi api;
    private final Executor background;
    private final Executor editorThread;

    /** The calls run, in order, on a daemon thread of their own; the answers on the Swing thread. */
    public AutomationPresentationControls(AutomationApi api) {
        this(api, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vsim-ai-presenting");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** @param background where the calls run: never the editor thread; the answers go to the Swing thread */
    public AutomationPresentationControls(AutomationApi api, Executor background) {
        this(api, background, SwingUtilities::invokeLater);
    }

    /**
     * @param background   where the calls run: never the editor thread (tests give one that runs at once)
     * @param editorThread where the answers go
     */
    public AutomationPresentationControls(AutomationApi api, Executor background, Executor editorThread) {
        this.api = Objects.requireNonNull(api, "api");
        this.background = Objects.requireNonNull(background, "background");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
    }

    @Override
    public void next(String taskId) {
        run("next", () -> api.nextStep(taskId));
    }

    @Override
    public void toStageEnd(String taskId) {
        run("toStageEnd", () -> api.toStageEnd(taskId));
    }

    @Override
    public void stop(String taskId) {
        run("stop", () -> api.stopTask(taskId));
    }

    /** {@link AutomationApi#undoLatestStage}: the latest stage's steps and frame while they are on top. */
    @Override
    public void undoStage(String taskId, Consumer<StageUndoOutcome> done) {
        ask("undoStage", () -> api.undoLatestStage(taskId), StageUndoOutcome.NOTHING_TO_UNDO, done);
    }

    /** {@link AutomationApi#keepOnlyResult}: only the last stage's frame and the components stay, as one undo step. */
    @Override
    public void keepOnlyResult(String taskId, Consumer<Boolean> done) {
        ask("keepOnlyResult", () -> {
            api.keepOnlyResult(taskId);
            return Boolean.TRUE;
        }, Boolean.FALSE, done);
    }

    @Override
    public void storyboardState(String taskId, Consumer<StoryboardState> done) {
        ask("storyboardState", () -> api.storyboardState(taskId), StoryboardState.NONE, done);
    }

    private void run(String control, Runnable call) {
        Objects.requireNonNull(control, "control");
        background.execute(() -> {
            try {
                call.run();
            } catch (RuntimeException failure) {
                // The panel hears the outcome through the presentation listener; a refusal here changes nothing.
                LOGGER.warn("«Presentar» could not {}: {}", control, failure.getMessage());
            }
        });
    }

    /** Runs {@code call} in the background and gives its answer ({@code refused} when it failed) to {@code done}. */
    private <T> void ask(String control, Supplier<T> call, T refused, Consumer<T> done) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(done, "done");
        background.execute(() -> {
            T answer;
            try {
                answer = call.get();
            } catch (RuntimeException failure) {
                // A refusal changes nothing: the panel hears it as the refused answer.
                LOGGER.warn("«Presentar» could not {}: {}", control, failure.getMessage());
                answer = refused;
            }
            T finished = answer;
            editorThread.execute(() -> done.accept(finished));
        });
    }
}
