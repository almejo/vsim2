package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The «Presentar» panel's buttons over the automation API (IA.2 Task 1): each call goes to a background thread, since
 * the API waits for the editor thread and the panel calls from it. «Deshacer etapa» and «Dejar solo el resultado» are
 * the API's storyboard calls (IA.2 Task 2); a refusal (a step still waits, the task still runs) is logged.
 */
public final class AutomationPresentationControls implements PresentationControls {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationPresentationControls.class);

    private final AutomationApi api;
    private final Executor background;

    /** The calls run, in order, on a daemon thread of their own. */
    public AutomationPresentationControls(AutomationApi api) {
        this(api, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vsim-ai-presenting");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** @param background where the calls run: never the editor thread (tests give one that runs at once) */
    public AutomationPresentationControls(AutomationApi api, Executor background) {
        this.api = Objects.requireNonNull(api, "api");
        this.background = Objects.requireNonNull(background, "background");
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

    /** {@link AutomationApi#undoStage}: the stage's steps and frame while they are on top of the history. */
    @Override
    public void undoStage(String taskId, int stageIndex) {
        run("undoStage", () -> api.undoStage(taskId, stageIndex));
    }

    /** {@link AutomationApi#keepOnlyResult}: only the last stage's frame and the components stay, as one undo step. */
    @Override
    public void keepOnlyResult(String taskId) {
        run("keepOnlyResult", () -> api.keepOnlyResult(taskId));
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
}
