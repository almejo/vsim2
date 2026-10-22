package cl.almejo.vsim.mcp;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.mcp.prompts.HelpResource;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The commands that teach the AI (IA.3 Task 1): prompts and the help resource, seen through the SDK's client. */
class McpPromptsTest {

    private static final String SECRET = "a-test-secret-of-enough-length";
    private static final List<String> PROMPT_NAMES = List.of("guia", "armar", "exponer", "explicar", "revisar",
            "probar", "componente", "exportar");
    /** Prompts whose request changes a circuit: each must end its stages with VSim's check. */
    private static final List<String> BUILDING_PROMPTS = List.of("armar", "exponer", "componente");
    /** Words that would tell the AI to break a rule (Review Focus 1): save, skip checks, leave «Presentar». */
    private static final List<String> FORBIDDEN = List.of("guard", "save", "ctrl+s", "sin comprobar",
            "no compruebes", "salta", "skip", "omite", "comprobado", "presenting false", "apaga", "desactiva",
            "sale de «presentar»", "quita «presentar»");

    private RunningServer server;
    private McpSyncClient client;

    @BeforeEach
    void startServer() throws PortInUseException {
        server = VsimMcpServer.start(new AutomationApi(new VsimMcpServerTest.FixedHost(List.of())),
                new McpServerSettings(0, SECRET));
        client = VsimMcpServerTest.client(server.port(), SECRET);
        client.initialize();
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.stop();
    }

    @Test
    void theServerOffersPromptsAndResources() {
        McpSchema.ServerCapabilities capabilities = client.getServerCapabilities();

        assertThat(capabilities.prompts()).isNotNull();
        assertThat(capabilities.resources()).isNotNull();
        assertThat(capabilities.tools()).isNotNull();
    }

    @Test
    void everyPromptIsListedWithItsArguments() {
        Map<String, McpSchema.Prompt> prompts = client.listPrompts().prompts().stream()
                .collect(Collectors.toMap(McpSchema.Prompt::name, prompt -> prompt));

        assertThat(prompts).containsOnlyKeys(PROMPT_NAMES);
        assertThat(prompts.values()).allSatisfy(prompt -> {
            assertThat(prompt.title()).isNotBlank();
            assertThat(prompt.description()).isNotBlank();
        });
        assertThat(arguments(prompts.get("guia"))).isEmpty();
        assertThat(arguments(prompts.get("armar"))).containsExactly(Map.entry("circuito", true));
        assertThat(arguments(prompts.get("exponer"))).containsExactly(Map.entry("circuito", true),
                Map.entry("ritmo", false));
        assertThat(arguments(prompts.get("explicar"))).isEmpty();
        assertThat(arguments(prompts.get("revisar"))).isEmpty();
        assertThat(arguments(prompts.get("probar"))).containsExactly(Map.entry("que", false));
        assertThat(arguments(prompts.get("componente"))).containsExactly(Map.entry("nombre", false),
                Map.entry("partes", false));
        assertThat(arguments(prompts.get("exportar"))).containsExactly(Map.entry("formato", false));
    }

    @Test
    void aPromptIsOneUserMessageInSpanishThatNamesWhatWasAsked() {
        McpSchema.GetPromptResult result = prompt("armar", Map.of("circuito", "un sumador de 4 bits"));

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
        assertThat(text(result)).contains("un sumador de 4 bits", "vsim_begin_task", "vsim_end_task");
    }

    @Test
    void aMissingRequiredArgumentIsAnInvalidParameter() {
        assertThatThrownBy(() -> prompt("armar", Map.of()))
                .isInstanceOfSatisfying(McpError.class, error -> assertThat(error.getJsonRpcError().code())
                        .isEqualTo(McpSchema.ErrorCodes.INVALID_PARAMS));
        assertThatThrownBy(() -> prompt("exponer", Map.of("circuito", "  ")))
                .isInstanceOf(McpError.class);
    }

    @Test
    void exponerDeCorridoGivesThePaceParametersOfSetPresentation() {
        String continuous = text(prompt("exponer", Map.of("circuito", "un latch SR", "ritmo", "de corrido")));

        assertThat(continuous).contains("vsim_set_presentation", "presenting true", "pace CONTINUOUS",
                "pauseMillis", "vsim_begin_stage", "vsim_await_step");
    }

    @Test
    void exponerPasoAPasoAsksForStepByStepAndWithoutAPaceLeavesItToTheUser() {
        String stepByStep = text(prompt("exponer", Map.of("circuito", "un latch SR", "ritmo", "Paso a paso")));
        String unchosen = text(prompt("exponer", Map.of("circuito", "un latch SR")));

        assertThat(stepByStep).contains("pace STEP_BY_STEP").doesNotContain("pace CONTINUOUS");
        assertThat(unchosen).doesNotContain("pace CONTINUOUS", "pace STEP_BY_STEP").contains("presenting true");
    }

    @Test
    void anUnknownPaceStaysPartOfTheRequestAndAPaceInsideTheCircuitTextCounts() {
        String spilled = text(prompt("exponer", Map.of("circuito", "sumador", "ritmo", "de 4 bits de corrido")));

        assertThat(spilled).contains("sumador de 4 bits de corrido", "pace CONTINUOUS");
    }

    @Test
    void exportarNamesTheFormatAndDefaultsToPdf() {
        assertThat(text(prompt("exportar", Map.of("formato", "png")))).contains("format PNG");
        assertThat(text(prompt("exportar", Map.of("formato", "SVG")))).contains("format SVG");
        assertThat(text(prompt("exportar", Map.of()))).contains("format PDF");
    }

    @Test
    void noPromptTellsTheAiToBreakARule() {
        for (Map.Entry<String, Map<String, Object>> request : everyVariant().entrySet()) {
            String name = request.getKey().split(" ")[0];
            String lowered = text(prompt(name, request.getValue())).toLowerCase(Locale.ROOT);

            assertThat(FORBIDDEN).as(request.getKey()).noneMatch(lowered::contains);
        }
    }

    @Test
    void everyBuildingPromptEndsItsStagesWithVsimsCheck() {
        for (Map.Entry<String, Map<String, Object>> request : everyVariant().entrySet()) {
            String name = request.getKey().split(" ")[0];
            if (BUILDING_PROMPTS.contains(name)) {
                assertThat(text(prompt(name, request.getValue()))).as(request.getKey())
                        .contains("vsim_check_stage", "passed true");
            }
        }
    }

    @Test
    void promptsThatOnlyLookSayNotToChangeTheCircuit() {
        for (String name : List.of("guia", "explicar", "revisar", "probar")) {
            assertThat(text(prompt(name, Map.of()))).as(name).contains("No cambies el circuito");
        }
    }

    @Test
    void theHelpResourceIsMarkdownAndListsEveryPrompt() {
        List<McpSchema.Resource> resources = client.listResources().resources();
        assertThat(resources).extracting(McpSchema.Resource::uri).containsExactly(HelpResource.URI);
        assertThat(resources.getFirst().mimeType()).isEqualTo("text/markdown");

        McpSchema.ReadResourceResult read = client.readResource(
                McpSchema.ReadResourceRequest.builder(HelpResource.URI).build());

        assertThat(read.contents()).hasSize(1);
        McpSchema.TextResourceContents contents = (McpSchema.TextResourceContents) read.contents().getFirst();
        assertThat(contents.mimeType()).isEqualTo("text/markdown");
        assertThat(contents.uri()).isEqualTo("vsim://ayuda");
        for (String name : PROMPT_NAMES) {
            assertThat(contents.text()).contains("/mcp__vsim__" + name);
        }
        assertThat(contents.text()).contains("«Presentar»", "Paso a paso", "De corrido", "Qué no hace",
                "vsim_check_stage");
    }

    @Test
    void theInstructionsMentionTheCommandsAndHowToGoFast() {
        assertThat(VsimServerInstructions.TEXT).contains("vsim://ayuda", "/mcp__vsim__", "once per task",
                "ONE vsim_edit", "a whole stage per call", "before the next stage", "continuousStops",
                "failed check");
    }

    private McpSchema.GetPromptResult prompt(String name, Map<String, Object> arguments) {
        return client.getPrompt(McpSchema.GetPromptRequest.builder(name).arguments(arguments).build());
    }

    private static String text(McpSchema.GetPromptResult result) {
        return result.messages().stream()
                .map(message -> ((McpSchema.TextContent) message.content()).text())
                .collect(Collectors.joining("\n"));
    }

    private static List<Map.Entry<String, Boolean>> arguments(McpSchema.Prompt prompt) {
        List<Map.Entry<String, Boolean>> arguments = new ArrayList<>();
        if (prompt.arguments() != null) {
            prompt.arguments().forEach(argument -> arguments.add(Map.entry(argument.name(),
                    Boolean.TRUE.equals(argument.required()))));
        }
        return arguments;
    }

    /** Every prompt with each kind of argument it takes, so the scans see every text a prompt can give. */
    private static Map<String, Map<String, Object>> everyVariant() {
        Map<String, Map<String, Object>> variants = new LinkedHashMap<>();
        variants.put("guia", Map.of());
        variants.put("armar", Map.of("circuito", "un contador de 3 bits"));
        variants.put("exponer", Map.of("circuito", "un latch SR"));
        variants.put("exponer paso", Map.of("circuito", "un latch SR", "ritmo", "paso a paso"));
        variants.put("exponer corrido", Map.of("circuito", "un latch SR", "ritmo", "de corrido"));
        variants.put("explicar", Map.of());
        variants.put("revisar", Map.of());
        variants.put("probar", Map.of());
        variants.put("probar que", Map.of("que", "la salida S con todas las entradas"));
        variants.put("componente", Map.of());
        variants.put("componente nombrado", Map.of("nombre", "Medio sumador", "partes", "las dos compuertas"));
        variants.put("exportar", Map.of());
        variants.put("exportar png", Map.of("formato", "png"));
        variants.put("exportar svg", Map.of("formato", "svg"));
        return variants;
    }
}
