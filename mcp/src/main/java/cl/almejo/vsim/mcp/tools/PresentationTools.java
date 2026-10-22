package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationService;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.StageOrigin;
import cl.almejo.vsim.automation.present.StepOutcome;
import cl.almejo.vsim.automation.present.StepStatus;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * «Presentar» (spec §4, §5.1, §6): the AI asks for the mode when the user wants to see how it builds, begins each
 * stage at the origin VSim gives, and waits for the user's «Siguiente» with a long poll that never blocks longer than
 * {@link PresentationService#MAXIMUM_STEP_WAIT}.
 */
final class PresentationTools {

    static final String GET_PRESENTATION = "vsim_get_presentation";
    static final String SET_PRESENTATION = "vsim_set_presentation";
    static final String BEGIN_STAGE = "vsim_begin_stage";
    static final String AWAIT_STEP = "vsim_await_step";
    static final String SAY = "vsim_say";
    /** At most this many stages in a plan (as {@code vsim_begin_task} allows). */
    private static final int MAXIMUM_STAGES = 50;

    private PresentationTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return tools(api, PresentationService.MAXIMUM_STEP_WAIT);
    }

    /** The tools with {@code vsim_await_step} waiting at most {@code stepWait} per call (tests make it short). */
    static List<Tool> tools(AutomationApi api, Duration stepWait) {
        Objects.requireNonNull(stepWait, "stepWait");
        return List.of(getPresentation(api), setPresentation(api), beginStage(api), awaitStep(api, stepWait),
                say(api));
    }

    /** The optional {@code narration} of {@code vsim_begin_stage} and {@code vsim_edit}: a line said first. */
    static Map<String, Object> narration(String what) {
        return Schema.text("Optional: one short sentence in the user's language saying what comes and why (as you "
                + "would write it in the chat), shown first as a subtitle in «Presentar» " + what
                + "; do not repeat the step's own text", PresentationService.MAXIMUM_NARRATION_LENGTH);
    }

    // --- the mode ---

    private static Map<String, Object> settingsSchema(String description) {
        return Schema.result(description)
                .property("document", Schema.string("The circuit"))
                .property("presenting", Schema.bool("True in «Presentar»: each vsim_edit is one step the user "
                        + "follows; false in working mode"))
                .property("pace", Schema.enumeration("STEP_BY_STEP («Paso a paso»: each step waits for the user's "
                        + "«Siguiente») or CONTINUOUS («De corrido»: steps apply by themselves after the pause)",
                        Arrays.stream(PresentationPace.values()).map(Enum::name).toList()))
                .property("pauseMillis", Schema.integer("The pause between steps in «De corrido»"))
                .required("document", "presenting", "pace", "pauseMillis").build();
    }

    private static Map<String, Object> settings(DocumentId document, PresentationSettings settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", document.value());
        result.put("presenting", settings.presenting());
        result.put("pace", settings.pace().name());
        result.put("pauseMillis", settings.pause().toMillis());
        return result;
    }

    private static Tool getPresentation(AutomationApi api) {
        return new Tool(GET_PRESENTATION, "Read the presentation mode",
                "Whether the user wants to see how you build in «Presentar», and at which pace. It is the user's "
                        + "choice for every window (Ver ▸ «Presentar lo que hace la IA»), so a new circuit may "
                        + "already be in «Presentar»: read it before each task to know whether to send one step per "
                        + "vsim_edit.",
                Schema.object("").property("document", Common.document()).required("document").build(),
                settingsSchema("The mode"), Tool.Hints.READS, List.of(Map.of("document", "circuit-1")),
                arguments -> Tool.Output.of(settings(arguments.document(),
                        api.presentationSettings(arguments.document()))));
    }

    private static Tool setPresentation(AutomationApi api) {
        return new Tool(SET_PRESENTATION, "Ask for «Presentar»",
                "When the user asks to see how you build («muéstrame cómo lo armas», a class, a video), ask for "
                        + "«Presentar» with presenting true: VSim switches, says «La IA pasó a Presentar» in its panel "
                        + "and answers the mode, with the pace the user chose. Switch it off only when the user asks "
                        + "and no task runs: during a task it is refused (NOT_ALLOWED), since you never leave "
                        + "«Presentar» by yourself; the user can switch it off in VSim. Inside a task (after "
                        + "vsim_begin_task, before its first step) you may also give pace and pauseMillis, for "
                        + "example CONTINUOUS for a recording the user will not click through: they last only for "
                        + "that task, in that window, and go back to the user's when it ends; the user's own choice "
                        + "in VSim wins. Without a task a pace or pause is refused (NOT_ALLOWED).",
                Schema.object("")
                        .property("document", Common.document())
                        .property("presenting", Schema.bool("True for «Presentar», false for working mode"))
                        .property("pace", Schema.enumeration("Optional, inside a task: STEP_BY_STEP («Paso a "
                                + "paso») or CONTINUOUS («De corrido») for this task only",
                                Arrays.stream(PresentationPace.values()).map(Enum::name).toList()))
                        .property("pauseMillis", Schema.integer("Optional, inside a task: the pause between steps "
                                + "in «De corrido», for this task only",
                                PresentationSettings.MINIMUM_PAUSE.toMillis(),
                                PresentationSettings.MAXIMUM_PAUSE.toMillis()))
                        .required("document", "presenting").build(),
                settingsSchema("The mode VSim switched to"), Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "presenting", true),
                        Map.of("document", "circuit-1", "presenting", true, "pace", "CONTINUOUS",
                                "pauseMillis", 1500)),
                arguments -> {
                    Optional<PresentationPace> pace = arguments.has("pace")
                            ? Optional.of(arguments.enumeration("pace", PresentationPace.class)) : Optional.empty();
                    Optional<Duration> pause = arguments.has("pauseMillis")
                            ? Optional.of(Duration.ofMillis(arguments.longValue("pauseMillis"))) : Optional.empty();
                    return Tool.Output.of(settings(arguments.document(), api.setPresentation(arguments.document(),
                            arguments.bool("presenting", false), pace, pause)));
                });
    }

    // --- stages ---

    private static Tool beginStage(AutomationApi api) {
        return new Tool(BEGIN_STAGE, "Begin a stage",
                "In «Presentar», call it when a stage of the task begins: VSim answers the free origin, to the right "
                        + "of the previous stage, where the stage's top-left corner goes (build it there, left to "
                        + "right), and the view travels there. When the next stage begins or the task ends, VSim "
                        + "wraps what the stage built in a frame titled with the stage, so the circuit reads like a "
                        + "storyboard (a read in mid-task shows no frame yet around the current stage).",
                Schema.object("")
                        .property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .property("stageNumber", Schema.integer("The stage's number in the plan, from 1", 1,
                                MAXIMUM_STAGES))
                        .property("narration", narration("about the stage"))
                        .required("taskId", "stageNumber").build(),
                Schema.result("Where to build the stage")
                        .property("taskId", Schema.string("The task"))
                        .property("stageNumber", Schema.integer("The stage"))
                        .property("origin", Schema.result("The stage's top-left corner, in grid units")
                                .property("x", Schema.integer(""))
                                .property("y", Schema.integer(""))
                                .required("x", "y").build())
                        .required("taskId", "stageNumber", "origin").build(),
                Tool.Hints.CHANGES_IDEMPOTENT, List.of(Map.of("taskId", "task-1", "stageNumber", 2)),
                arguments -> {
                    String taskId = arguments.string("taskId");
                    int stageNumber = arguments.integer("stageNumber");
                    StageOrigin origin = api.beginStage(taskId, stageNumber - 1,
                            arguments.optionalString("narration").filter(line -> !line.isBlank()));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("taskId", taskId);
                    result.put("stageNumber", (long) stageNumber);
                    result.put("origin", Map.of("x", (long) origin.x(), "y", (long) origin.y()));
                    return Tool.Output.of(result);
                });
    }

    // --- what the AI says (IA.3) ---

    private static Tool say(AutomationApi api) {
        return new Tool(SAY, "Say what comes",
                "Say in one short sentence, in the user's language, what comes next and why («Ahora copio el "
                        + "circuito para hacer un componente nuevo»): the sentence you would write in the chat, which "
                        + "the user watching VSim never sees. In «Presentar» VSim shows it as a subtitle over the "
                        + "circuit and in the stage's card, until the next line or the step after the one it "
                        + "introduces; in «De corrido» it is paced like a short step (shown, then the pause), in «Paso "
                        + "a paso» it shows at once. In working mode only VSim's status line keeps it. Use it before "
                        + "each stage and before a step that is not obvious; do not repeat the step's own text (the "
                        + "step's text and reason show anyway). Plain text, at most "
                        + PresentationService.MAXIMUM_NARRATION_LENGTH + " characters. You may also give it as "
                        + "narration of vsim_begin_stage or vsim_edit.",
                Schema.object("")
                        .property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .property("text", Schema.text("One short sentence: what comes and why",
                                PresentationService.MAXIMUM_NARRATION_LENGTH))
                        .required("taskId", "text").build(),
                Schema.result("What VSim showed")
                        .property("taskId", Schema.string("The task"))
                        .property("shown", Schema.bool("True: VSim showed it (or kept the same line showing)"))
                        .property("presenting", Schema.bool("True when it shows as a subtitle in «Presentar»; "
                                + "false in working mode (only the status line keeps it)"))
                        .property("stageNumber", Schema.integer("The stage it belongs to, from 1"))
                        .required("taskId", "shown", "presenting", "stageNumber").build(),
                Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("taskId", "task-1", "text", "Ahora copio el circuito para hacer un componente")),
                arguments -> {
                    Narration narration = api.say(arguments.string("taskId"), arguments.string("text"));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("taskId", narration.taskId());
                    result.put("shown", true);
                    result.put("presenting", narration.presenting());
                    result.put("stageNumber", (long) narration.stageIndex() + 1);
                    return Tool.Output.of(result);
                });
    }

    // --- waiting for «Siguiente» ---

    private static Tool awaitStep(AutomationApi api, Duration stepWait) {
        return new Tool(AWAIT_STEP, "Wait for «Siguiente»",
                "After a vsim_edit answered PROPOSED («Paso a paso»: the step is shown as a ghost and nothing "
                        + "changed yet), wait here for the user. It answers within " + stepWait.toSeconds() + " s: "
                        + "APPLIED with the step's receipt (send the next step with its version), STILL_WAITING "
                        + "(the user has not pressed «Siguiente» yet: call again, do not send another step), STALE "
                        + "(the user changed the circuit and the step was discarded: read the circuit again and "
                        + "propose the step again, adapted) or STOPPED (the user pressed «Detener», closed the window "
                        + "or turned the AI off: stop and say so).",
                Schema.object("").property("taskId", Schema.nonEmptyString("The task of the proposed step"))
                        .required("taskId").build(),
                Schema.result("What became of the step")
                        .property("taskId", Schema.string("The task"))
                        .property("status", Schema.enumeration("APPLIED, STILL_WAITING, STALE or STOPPED",
                                List.of(StepStatus.APPLIED.name(), StepStatus.STILL_WAITING.name(),
                                        StepStatus.STALE.name(), StepStatus.STOPPED.name())))
                        .property("receipt", Schema.result("With APPLIED: the step's receipt, as vsim_edit gives it")
                                .property("version", Schema.integer("The circuit version after the step"))
                                .required("version").build())
                        .required("taskId", "status").build(),
                Tool.Hints.READS, List.of(Map.of("taskId", "task-1")),
                arguments -> {
                    String taskId = arguments.string("taskId");
                    StepOutcome outcome = api.awaitStep(taskId, stepWait);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("taskId", taskId);
                    result.put("status", outcome.status().name());
                    outcome.receipt().ifPresent(receipt -> result.put("receipt", EditTools.receipt(receipt)));
                    return Tool.Output.of(result);
                });
    }
}
