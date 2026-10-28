package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.mcp.McpResults.structured;
import static cl.almejo.vsim.mcp.McpResults.map;
import static org.assertj.core.api.Assertions.assertThat;

/** «Presentar» as the AI reaches it (spec §4, §5.1, §6): the mode, the stage origin, proposed steps and their wait. */
class PresentationToolsTest {

    private static final JsonSchemaValidator VALIDATOR = McpJsonDefaults.getSchemaValidator();
    /** The poll limit of {@code vsim_await_step} in these tests: short, so «still waiting» comes at once. */
    private static final Duration SHORT_WAIT = Duration.ofMillis(30);

    private final ToolTestHost host = new ToolTestHost();
    private final AutomationApi api = new AutomationApi(host);
    private final ToolRunner runner = new ToolRunner(new JacksonMcpJsonMapper(new ObjectMapper()), VALIDATOR);
    private final List<Tool> tools = toolsWithShortWait();
    // The API tells its listeners on its own thread, in order.
    private final BlockingQueue<StepPreview> proposed = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> checked = new LinkedBlockingQueue<>();
    private final BlockingQueue<Narration> narrated = new LinkedBlockingQueue<>();
    private int nextOperation = 1;

    @BeforeEach
    void openACircuitAndListen() {
        host.open("circuit-1");
        api.addPresentationListener(new PresentationListener() {
            @Override
            public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
            }

            @Override
            public void stepProposed(StepPreview preview) {
                proposed.add(preview);
            }

            @Override
            public void stepApplied(StepPreview preview) {
            }

            @Override
            public void stepDiscarded(String taskId, StepStatus why) {
            }

            @Override
            public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
                checked.add(taskId + " " + stageIndex + " " + passed);
            }

            @Override
            public void narrated(Narration narration) {
                narrated.add(narration);
            }
        });
    }

    private List<Tool> toolsWithShortWait() {
        List<Tool> all = new ArrayList<>(VsimTools.tools(api));
        all.removeIf(tool -> tool.name().equals(PresentationTools.AWAIT_STEP));
        all.addAll(PresentationTools.tools(api, SHORT_WAIT).stream()
                .filter(tool -> tool.name().equals(PresentationTools.AWAIT_STEP)).toList());
        return all;
    }

    // --- the mode ---

    @Test
    void aCircuitStartsInWorkingModeWithThePaceAndPauseItWouldUse() {
        Map<String, Object> presentation = call("vsim_get_presentation", Map.of("document", "circuit-1"));

        assertThat(presentation).containsEntry("document", "circuit-1").containsEntry("presenting", false)
                .containsEntry("pace", "STEP_BY_STEP").containsEntry("pauseMillis", 1000L);
    }

    @Test
    void theAiAsksForPresentarAndVsimSwitchesAndSaysSo() {
        Map<String, Object> answer = call("vsim_set_presentation", Map.of("document", "circuit-1",
                "presenting", true));

        assertThat(answer).containsEntry("presenting", true);
        assertThat(call("vsim_get_presentation", Map.of("document", "circuit-1")))
                .containsEntry("presenting", true);
    }

    @Test
    void theAiNeverLeavesPresentarByItselfDuringATask() {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        beginTask();

        assertThat(refusal("vsim_set_presentation", Map.of("document", "circuit-1", "presenting", false)))
                .containsEntry("error", "NOT_ALLOWED");
        assertThat(call("vsim_get_presentation", Map.of("document", "circuit-1")))
                .containsEntry("presenting", true);
    }

    /** Agreed with the user: the AI may present «De corrido» for a recording, for its task only. */
    @Test
    void theAiMayAskForThePaceAndThePauseForItsTask() {
        beginTask();

        Map<String, Object> answer = call("vsim_set_presentation", Map.of("document", "circuit-1",
                "presenting", true, "pace", "CONTINUOUS", "pauseMillis", 2000));

        assertThat(answer).containsEntry("presenting", true).containsEntry("pace", "CONTINUOUS")
                .containsEntry("pauseMillis", 2000L);
        assertThat(host.layeredPresentations()).containsExactly("circuit-1 CONTINUOUS 2000");
    }

    @Test
    void thePaceFromTheAiNeedsARunningTaskAndAPauseWithinTheRange() {
        assertThat(refusal("vsim_set_presentation", Map.of("document", "circuit-1", "presenting", true,
                "pace", "CONTINUOUS"))).containsEntry("error", "NOT_ALLOWED");
        beginTask();
        assertThat(refusal("vsim_set_presentation", Map.of("document", "circuit-1", "presenting", true,
                "pauseMillis", 100))).containsKey("error");
        assertThat(host.layeredPresentations()).isEmpty();
    }

    @Test
    void anUnknownCircuitIsAnInvalidReference() {
        assertThat(refusal("vsim_get_presentation", Map.of("document", "circuit-9")))
                .containsEntry("error", "INVALID_REFERENCE");
    }

    // --- steps ---

    @Test
    void inWorkingModeAnEditIsAppliedAtOnce() {
        Map<String, Object> receipt = call("vsim_edit", edit(beginTask(), 0, List.of(place("one", 0, 0))));

        assertThat(receipt).containsEntry("status", "APPLIED").doesNotContainKey("applied");
    }

    @Test
    void aProposedStepIsAppliedWhenTheUserPressesSiguiente() {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        String task = beginTask();

        Map<String, Object> receipt = call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));

        assertThat(receipt).containsEntry("status", "PROPOSED").containsEntry("version", 0L);
        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STILL_WAITING")
                .doesNotContainKey("receipt");

        api.nextStep(task);

        Map<String, Object> outcome = call("vsim_await_step", Map.of("taskId", task));
        assertThat(outcome).containsEntry("taskId", task).containsEntry("status", "APPLIED");
        assertThat(map(outcome, "receipt")).containsEntry("version", 1L).containsKey("aliases");
    }

    @Test
    void aWaitingAwaitReturnsAsSoonAsTheStepIsApplied() throws Exception {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));
        Tool longWait = PresentationTools.tools(api, Duration.ofSeconds(10)).stream()
                .filter(tool -> tool.name().equals(PresentationTools.AWAIT_STEP)).findFirst().orElseThrow();

        CompletableFuture<McpSchema.CallToolResult> waiting = CompletableFuture.supplyAsync(
                () -> runner.call(longWait, Map.of("taskId", task)));
        api.nextStep(task);

        assertThat(structured(waiting.get(5, TimeUnit.SECONDS))).containsEntry("status", "APPLIED");
    }

    @Test
    void theStepsReasonReachesThePanel() throws InterruptedException {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        Map<String, Object> arguments = edit(beginTask(), 0, List.of(place("one", 0, 0)));
        arguments.put("text", "Coloco la NAND");
        arguments.put("reason", "Así el latch recuerda");

        call("vsim_edit", arguments);

        StepPreview preview = proposed.poll(5, TimeUnit.SECONDS);
        assertThat(preview).isNotNull();
        assertThat(preview.text()).isEqualTo("Coloco la NAND");
        assertThat(preview.reason()).contains("Así el latch recuerda");
    }

    @Test
    void aStoppedTaskAnswersStopped() {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));

        api.stopTask(task);

        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STOPPED");
    }

    @Test
    void awaitingAStepThatWasNeverProposedOrOfNoTaskIsRefused() {
        String task = beginTask();

        assertThat(refusal("vsim_await_step", Map.of("taskId", task))).containsEntry("error", "NOT_ALLOWED");
        assertThat(refusal("vsim_await_step", Map.of("taskId", "task-99")))
                .containsEntry("error", "INVALID_REFERENCE");
    }

    // --- stages ---

    @Test
    void aStageBeginsAtAFreeOriginRightOfWhatIsThere() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));

        Map<String, Object> stage = call("vsim_begin_stage", Map.of("taskId", task, "stageNumber", 2));

        assertThat(stage).containsEntry("taskId", task).containsEntry("stageNumber", 2L);
        assertThat(((Number) map(stage, "origin").get("x")).intValue()).isGreaterThan(0);
    }

    @Test
    void aStageThatIsNotInThePlanIsRefused() {
        assertThat(refusal("vsim_begin_stage", Map.of("taskId", beginTask(), "stageNumber", 7)))
                .containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void aCheckThatNamesTheTaskReachesThePanel() throws InterruptedException {
        String task = beginTask();

        call("vsim_check_stage", Map.of("document", "circuit-1", "taskId", task, "stage", "Primera",
                "checks", List.of(Map.of("kind", "problems"))));

        assertThat(checked.poll(5, TimeUnit.SECONDS)).isEqualTo(task + " 0 true");
    }

    // --- the AI's narration (IA.3 Task 7) ---

    @Test
    void theAiSaysWhatComesAndThePanelHearsIt() throws InterruptedException {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        String task = beginTask();

        Map<String, Object> answer = call("vsim_say", Map.of("taskId", task,
                "text", "Ahora copio el circuito para hacer un componente"));

        assertThat(answer).containsEntry("taskId", task).containsEntry("shown", true)
                .containsEntry("presenting", true).containsEntry("stageNumber", 1L);
        Narration narration = narrated.poll(5, TimeUnit.SECONDS);
        assertThat(narration).isNotNull();
        assertThat(narration.text()).isEqualTo("Ahora copio el circuito para hacer un componente");
    }

    @Test
    void aStageAndAStepMayCarryTheirNarration() throws InterruptedException {
        host.present("circuit-1", PresentationPace.STEP_BY_STEP);
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));
        api.nextStep(task);
        call("vsim_await_step", Map.of("taskId", task));

        call("vsim_begin_stage", Map.of("taskId", task, "stageNumber", 2, "narration", "Ahora la segunda etapa"));
        Narration stage = narrated.poll(5, TimeUnit.SECONDS);
        assertThat(stage).isNotNull();
        assertThat(stage.stageIndex()).isEqualTo(1);
        assertThat(stage.text()).isEqualTo("Ahora la segunda etapa");

        Map<String, Object> arguments = edit(task, 1, List.of(place("two", 0, 20)));
        arguments.put("stage", "Segunda");
        arguments.put("narration", "Pongo otra NAND");
        call("vsim_edit", arguments);
        Narration step = narrated.poll(5, TimeUnit.SECONDS);
        assertThat(step).isNotNull();
        assertThat(step.text()).isEqualTo("Pongo otra NAND");
    }

    @Test
    void aLineThatIsTooLongIsRefusedByTheSchemaAndAnHtmlOneByVsim() {
        String task = beginTask();

        assertThat(refusal("vsim_say", Map.of("taskId", task, "text", "x".repeat(201))))
                .containsEntry("error", "INVALID_PARAMETER");
        assertThat(refusal("vsim_say", Map.of("taskId", task, "text", "<html><img src=x>")))
                .containsEntry("error", "INVALID_PARAMETER");
        assertThat(refusal("vsim_say", Map.of("taskId", "task-99", "text", "Hola")))
                .containsEntry("error", "INVALID_REFERENCE");
        assertThat(narrated).isEmpty();
    }

    @Test
    void theSayToolsSchemaAsksForOneShortPlainLine() {
        Tool say = tool("vsim_say");

        assertThat(say.inputSchema().toString()).contains("maxLength=200");
        assertThat(say.description()).contains("subtitle", "«Presentar»", "do not repeat");
        assertThat(tool("vsim_edit").inputSchema().toString()).contains("narration");
        assertThat(tool("vsim_begin_stage").inputSchema().toString()).contains("narration");
    }

    // --- helpers ---

    private String beginTask() {
        return (String) call("vsim_begin_task", Map.of("document", "circuit-1", "title", "Latch",
                "stages", List.of(Map.of("title", "Primera", "steps", List.of("Coloco", "Conecto")),
                        Map.of("title", "Segunda")))).get("taskId");
    }

    private Map<String, Object> edit(String task, long version, List<Map<String, Object>> operations) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("document", "circuit-1");
        arguments.put("version", version);
        arguments.put("operationId", "operation-" + nextOperation++);
        arguments.put("taskId", task);
        arguments.put("stage", "Primera");
        arguments.put("step", "Coloco");
        arguments.put("operations", operations);
        return arguments;
    }

    private static Map<String, Object> place(String alias, int x, int y) {
        return Map.of("op", "PlacePart", "alias", alias, "typeId", "and", "x", x, "y", y);
    }

    private Tool tool(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
    }

    private Map<String, Object> call(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = runner.call(tool(name), arguments);
        assertThat(result.isError()).as("%s answered %s", name, result.content()).isFalse();
        JsonSchemaValidator.ValidationResponse validation = VALIDATOR.validate(tool(name).outputSchema(),
                result.structuredContent());
        assertThat(validation.valid()).as("%s output: %s", name, validation.errorMessage()).isTrue();
        return structured(result);
    }

    private Map<String, Object> refusal(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = runner.call(tool(name), arguments);
        assertThat(result.isError()).as("%s answered %s", name, result.content()).isTrue();
        return structured(result);
    }
}
