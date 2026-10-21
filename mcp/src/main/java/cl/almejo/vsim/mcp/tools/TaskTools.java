package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.edit.TaskStage;

import java.util.List;
import java.util.Map;

/** The AI's task (spec §3): its plan of stages and steps, its end, and undoing it. */
final class TaskTools {

    static final String BEGIN_TASK = "vsim_begin_task";
    static final String UPDATE_PLAN = "vsim_update_plan";
    static final String END_TASK = "vsim_end_task";
    static final String UNDO_TASK = "vsim_undo_task";

    private TaskTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(beginTask(api), updatePlan(api), endTask(api), undoTask(api));
    }

    private static Map<String, Object> stages() {
        Map<String, Object> stage = Schema.object("One stage: what it builds and its steps")
                .property("title", Schema.nonEmptyString("«Latch SR con NAND», «Sumador de 2 bits»…"))
                .property("steps", Schema.array("Its steps in order; a future stage may list none yet",
                        Schema.nonEmptyString("«Coloco las dos NAND», «Conecto la realimentación»…")))
                .required("title").build();
        return Schema.array("The plan: one stage for a small request, several for a bigger one", stage, 1, 50);
    }

    private static List<TaskStage> stagesOf(Arguments arguments) {
        return arguments.objects("stages").stream()
                .map(stage -> new TaskStage(stage.string("title"), stage.strings("steps")))
                .toList();
    }

    private static Map<String, Object> taskSchema() {
        return Schema.result("The task")
                .property("taskId", Schema.string("Name it in every edit of this task"))
                .property("document", Schema.string("The circuit it works on"))
                .property("title", Schema.string("What the user sees: «IA trabajando: <title>…»"))
                .required("taskId", "document", "title").build();
    }

    private static Tool beginTask(AutomationApi api) {
        return new Tool(BEGIN_TASK, "Begin a task",
                "Every request that changes a circuit is a task: begin it before the first change with a title "
                        + "(the user sees «IA trabajando: <title>…» and can press «Detener») and a plan of stages "
                        + "with steps. Each edit names its task. A task still running on that circuit is ended "
                        + "first. End it with vsim_end_task.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("title", Schema.nonEmptyString("Short, in the user's language"))
                        .property("stages", stages())
                        .required("document", "title", "stages").build(),
                taskSchema(), Tool.Hints.CHANGES,
                List.of(Map.of("document", "circuit-1", "title", "Flip-flop D",
                        "stages", List.of(Map.of("title", "Latch D", "steps",
                                List.of("Coloco las compuertas", "Conecto la realimentación")),
                                Map.of("title", "Componente")))),
                arguments -> Tool.Output.of(task(api.beginTask(arguments.document(), arguments.string("title"),
                        stagesOf(arguments)))));
    }

    private static Tool updatePlan(AutomationApi api) {
        return new Tool(UPDATE_PLAN, "Update the task's plan",
                "Replaces the plan of stages and steps of a running task, for example to detail a stage when it is "
                        + "reached.",
                Schema.object("")
                        .property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .property("stages", stages())
                        .required("taskId", "stages").build(),
                Schema.result("The plan was replaced")
                        .property("taskId", Schema.string(""))
                        .property("updated", Schema.bool(""))
                        .required("taskId", "updated").build(),
                Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("taskId", "task-1", "stages", List.of(Map.of("title", "Latch D"),
                        Map.of("title", "Componente", "steps", List.of("Creo el componente sin reemplazar"))))),
                arguments -> {
                    String taskId = arguments.string("taskId");
                    api.updatePlan(taskId, stagesOf(arguments));
                    return Tool.Output.of(Map.of("taskId", taskId, "updated", true));
                });
    }

    private static Tool endTask(AutomationApi api) {
        return new Tool(END_TASK, "End the task",
                "Ends the task: the user sees «La IA terminó: <title>», what it changed glows briefly and the "
                        + "editor settings it changed go back to the user's. Say what VSim measured, not more.",
                Schema.object("").property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .required("taskId").build(),
                taskSchema(), Tool.Hints.CHANGES_IDEMPOTENT, List.of(Map.of("taskId", "task-1")),
                arguments -> Tool.Output.of(task(api.endTask(arguments.string("taskId")))));
    }

    private static Tool undoTask(AutomationApi api) {
        return new Tool(UNDO_TASK, "Undo the task",
                "Undoes the task's edits from the top of the circuit's history, one after the other, as «Deshacer lo "
                        + "que hizo la IA» does; it stops at the user's own edit and says so (stoppedAtUserEdit), "
                        + "so it never undoes the user's work.",
                Schema.object("").property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .required("taskId").build(),
                Schema.result("What was undone")
                        .property("undone", Schema.integer("Edits undone"))
                        .property("stoppedAtUserEdit", Schema.bool("Some of the task's edits stay because "
                                + "something else is on top of them"))
                        .required("undone", "stoppedAtUserEdit").build(),
                Tool.Hints.MAY_REMOVE, List.of(Map.of("taskId", "task-1")),
                arguments -> {
                    UndoOutcome outcome = api.undoTask(arguments.string("taskId"));
                    return Tool.Output.of(outcome);
                });
    }

    private static Map<String, Object> task(TaskInfo task) {
        return Json.object(task);
    }
}
