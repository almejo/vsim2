package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
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
        return List.of(getPresentation(api), setPresentation(api), beginStage(api), awaitStep(api, stepWait));
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
                "Whether the user wants to see how you build in «Presentar» on this circuit, and at which pace. "
                        + "The user chooses it in VSim (Ver ▸ «Presentar lo que hace la IA»); read it before a task "
                        + "to know whether to send one step per vsim_edit.",
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
                        + "«Presentar» by yourself; the user can switch it off in VSim.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("presenting", Schema.bool("True for «Presentar», false for working mode"))
                        .required("document", "presenting").build(),
                settingsSchema("The mode VSim switched to"), Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "presenting", true)),
                arguments -> Tool.Output.of(settings(arguments.document(),
                        api.setPresenting(arguments.document(), arguments.bool("presenting", false)))));
    }

    // --- stages ---

    private static Tool beginStage(AutomationApi api) {
        return new Tool(BEGIN_STAGE, "Begin a stage",
                "In «Presentar», call it when a stage of the task begins: VSim answers the free origin, to the right "
                        + "of the previous stage, where the stage's top-left corner goes (build it there, left to "
                        + "right), and the view travels there. At the stage's end VSim wraps what the stage built in "
                        + "a frame titled with the stage, so the circuit reads like a storyboard.",
                Schema.object("")
                        .property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .property("stageNumber", Schema.integer("The stage's number in the plan, from 1", 1,
                                MAXIMUM_STAGES))
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
                    StageOrigin origin = api.beginStage(taskId, stageNumber - 1);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("taskId", taskId);
                    result.put("stageNumber", (long) stageNumber);
                    result.put("origin", Map.of("x", (long) origin.x(), "y", (long) origin.y()));
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
                                .property("applied", Schema.bool(""))
                                .property("version", Schema.integer("The circuit version after the step"))
                                .required("applied", "version").build())
                        .required("taskId", "status").build(),
                Tool.Hints.READS, List.of(Map.of("taskId", "task-1")),
                arguments -> {
                    String taskId = arguments.string("taskId");
                    StepOutcome outcome = api.awaitStep(taskId, stepWait);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("taskId", taskId);
                    result.put("status", outcome.status().name());
                    outcome.receipt().ifPresent(receipt -> result.put("receipt", Json.object(receipt)));
                    return Tool.Output.of(result);
                });
    }
}
