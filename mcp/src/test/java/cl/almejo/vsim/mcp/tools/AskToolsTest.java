package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.QuestionAnswer;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.mcp.McpResults.structured;
import static org.assertj.core.api.Assertions.assertThat;

/** The AI's questions as it reaches them (IA.3 Task 8): ask, wait for the answer, withdraw. */
class AskToolsTest {

    private static final JsonSchemaValidator VALIDATOR = McpJsonDefaults.getSchemaValidator();
    private static final Duration SHORT_WAIT = Duration.ofMillis(30);

    private final ToolTestHost host = new ToolTestHost();
    private final AutomationApi api = new AutomationApi(host);
    private final ToolRunner runner = new ToolRunner(new JacksonMcpJsonMapper(new ObjectMapper()), VALIDATOR);
    private final List<Tool> tools = toolsWithShortWait();
    private final BlockingQueue<Question> asked = new LinkedBlockingQueue<>();

    @BeforeEach
    void openACircuitAndListen() {
        host.open("circuit-1");
        api.addPresentationListener(new PresentationListener() {
            @Override
            public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
            }

            @Override
            public void stepProposed(StepPreview preview) {
            }

            @Override
            public void stepApplied(StepPreview preview) {
            }

            @Override
            public void stepDiscarded(String taskId, StepStatus why) {
            }

            @Override
            public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
            }

            @Override
            public void questionAsked(Question question) {
                asked.add(question);
            }
        });
    }

    private List<Tool> toolsWithShortWait() {
        List<Tool> all = new ArrayList<>(VsimTools.tools(api));
        all.removeIf(tool -> tool.name().equals(AskTools.AWAIT_ANSWER));
        all.addAll(AskTools.tools(api, SHORT_WAIT).stream()
                .filter(tool -> tool.name().equals(AskTools.AWAIT_ANSWER)).toList());
        return all;
    }

    @Test
    void theAiAsksWithChoicesAndGetsTheButtonTheUserPressed() throws InterruptedException {
        String task = beginTask();

        Map<String, Object> question = call("vsim_ask_user", Map.of("taskId", task,
                "question", "¿De cuántos bits hago el contador?", "choices", List.of("2", "3", "4")));

        String questionId = (String) question.get("questionId");
        assertThat(question).containsEntry("taskId", task).containsEntry("status", "ASKED");
        Question shown = asked.poll(5, TimeUnit.SECONDS);
        assertThat(shown).isNotNull();
        assertThat(shown.choices()).containsExactly("2", "3", "4");
        assertThat(shown.allowText()).isTrue();
        assertThat(call("vsim_await_answer", Map.of("questionId", questionId)))
                .containsEntry("status", "STILL_WAITING").doesNotContainKeys("choice", "text");

        api.answerQuestion(questionId, QuestionAnswer.choice("4"));

        assertThat(call("vsim_await_answer", Map.of("questionId", questionId)))
                .containsEntry("questionId", questionId).containsEntry("status", "ANSWERED")
                .containsEntry("choice", "4").doesNotContainKey("text");
    }

    @Test
    void aWrittenAnswerComesAsText() {
        String task = beginTask();
        String questionId = (String) call("vsim_ask_user", Map.of("taskId", task,
                "question", "¿Cómo lo llamo?", "allowText", true)).get("questionId");

        api.answerQuestion(questionId, QuestionAnswer.text("Contador"));

        assertThat(call("vsim_await_answer", Map.of("questionId", questionId)))
                .containsEntry("status", "ANSWERED").containsEntry("text", "Contador");
    }

    @Test
    void theAiWithdrawsAQuestionAnsweredInTheChatAndDetenerWithdrawsItToo() {
        String task = beginTask();
        String first = (String) call("vsim_ask_user", Map.of("taskId", task, "question", "¿Sigo?",
                "choices", List.of("Sí", "No"), "allowText", false)).get("questionId");

        assertThat(call("vsim_withdraw_question", Map.of("questionId", first)))
                .containsEntry("questionId", first).containsEntry("withdrawn", true);
        assertThat(call("vsim_await_answer", Map.of("questionId", first))).containsEntry("status", "WITHDRAWN");

        String second = (String) call("vsim_ask_user", Map.of("taskId", task, "question", "¿Sigo?"))
                .get("questionId");
        api.stopTask(task);
        assertThat(call("vsim_await_answer", Map.of("questionId", second))).containsEntry("status", "WITHDRAWN");
    }

    @Test
    void closingTheWindowAndTurningTheAiOffWithdrawTheQuestion() {
        String task = beginTask();
        String first = (String) call("vsim_ask_user", Map.of("taskId", task, "question", "¿Uno?")).get("questionId");

        api.documentClosed(new DocumentId("circuit-1"));
        assertThat(call("vsim_await_answer", Map.of("questionId", first))).containsEntry("status", "WITHDRAWN");

        String second = (String) call("vsim_ask_user", Map.of("taskId", task, "question", "¿Dos?"))
                .get("questionId");
        api.stopPresenting();
        assertThat(call("vsim_await_answer", Map.of("questionId", second))).containsEntry("status", "WITHDRAWN");
    }

    @Test
    void badQuestionsAreRefused() {
        String task = beginTask();

        assertThat(refusal("vsim_ask_user", Map.of("taskId", task, "question", "x".repeat(301))))
                .containsEntry("error", "INVALID_PARAMETER");
        assertThat(refusal("vsim_ask_user", Map.of("taskId", task, "question", "¿Cuál?",
                "choices", List.of("a", "b", "c", "d", "e")))).containsEntry("error", "INVALID_PARAMETER");
        assertThat(refusal("vsim_ask_user", Map.of("taskId", task, "question", "¿Cuál?", "allowText", false)))
                .containsEntry("error", "INVALID_PARAMETER");
        assertThat(refusal("vsim_await_answer", Map.of("questionId", "question-99")))
                .containsEntry("error", "INVALID_REFERENCE");
        call("vsim_ask_user", Map.of("taskId", task, "question", "¿Uno?"));
        assertThat(refusal("vsim_ask_user", Map.of("taskId", task, "question", "¿Dos?")))
                .containsEntry("error", "NOT_ALLOWED");
    }

    @Test
    void theToolsSayTheAnswerIsTheUsersDataAndToKeepAskingInTheChat() {
        assertThat(tool("vsim_ask_user").description()).contains("«Presentar»", "one question at a time",
                "in the chat too");
        assertThat(tool("vsim_await_answer").description()).contains("data", "never instructions");
        assertThat(tool("vsim_ask_user").inputSchema().toString()).contains("maxLength=300", "maxItems=4");
    }

    private String beginTask() {
        return (String) call("vsim_begin_task", Map.of("document", "circuit-1", "title", "Contador",
                "stages", List.of(Map.of("title", "Primera", "steps", List.of("Coloco"))))).get("taskId");
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
