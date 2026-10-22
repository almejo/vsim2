package cl.almejo.vsim.mcp;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.mcp.prompts.HelpResource;
import cl.almejo.vsim.mcp.prompts.VsimPrompts;
import io.modelcontextprotocol.client.McpSyncClient;
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

/** The commands that teach the AI (IA.3 Task 1): prompts and the help resource, seen through the SDK's client. */
class McpPromptsTest {

    private static final String SECRET = "a-test-secret-of-enough-length";
    private static final List<String> PROMPT_NAMES = List.of("guia", "armar", "exponer", "explicar", "revisar",
            "probar", "componente", "exportar");
    /** Prompts whose request changes a circuit: each must end its stages with VSim's check. */
    private static final List<String> BUILDING_PROMPTS = List.of("armar", "exponer", "componente");
    /** Prompts that take the user's request as free text. */
    private static final List<String> REQUEST_PROMPTS = List.of("armar", "exponer", "probar", "componente",
            "exportar");
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
        // at most one free text (pedido) plus one-word options, all optional (a client may split the text)
        assertThat(arguments(prompts.get("guia"))).isEmpty();
        assertThat(arguments(prompts.get("armar"))).containsExactly(Map.entry("pedido", false));
        assertThat(arguments(prompts.get("exponer"))).containsExactly(Map.entry("pedido", false),
                Map.entry("ritmo", false));
        assertThat(arguments(prompts.get("explicar"))).isEmpty();
        assertThat(arguments(prompts.get("revisar"))).isEmpty();
        assertThat(arguments(prompts.get("probar"))).containsExactly(Map.entry("pedido", false));
        assertThat(arguments(prompts.get("componente"))).containsExactly(Map.entry("pedido", false));
        assertThat(arguments(prompts.get("exportar"))).containsExactly(Map.entry("formato", false),
                Map.entry("pedido", false));
    }

    @Test
    void aPromptIsOneUserMessageInSpanishThatNamesWhatWasAsked() {
        McpSchema.GetPromptResult result = prompt("armar", Map.of("pedido", "un sumador de 4 bits"));

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().role()).isEqualTo(McpSchema.Role.USER);
        assertThat(text(result)).contains("un sumador de 4 bits", "vsim_begin_task", "vsim_end_task");
    }

    @Test
    void aMissingOrCutRequestNeverFailsAndTellsTheAiToAsk() {
        for (String name : REQUEST_PROMPTS) {
            assertThat(text(prompt(name, Map.of()))).as(name).contains("llegó cortado", "pregúntame");
            assertThat(text(prompt(name, Map.of("pedido", "un")))).as(name).contains("llegó cortado");
        }
        for (String name : BUILDING_PROMPTS) {
            assertThat(text(prompt(name, Map.of("pedido", "  ")))).as(name).contains("llegó cortado o vacío");
        }
        assertThat(text(prompt("armar", Map.of()))).contains("todavía no te dije qué");
    }

    /** Claude Code splits the text after the command on spaces, one word per argument, in order. */
    @Test
    void wordsSplitOverTheArgumentsAreJoinedAgainAndTheOptionsFoundByValue() {
        String exponer = text(prompt("exponer", ordered("pedido", "sumador", "ritmo", "de 4 bits de corrido")));
        assertThat(exponer).contains("sumador de 4 bits de corrido", "pace CONTINUOUS");

        String paceFirst = text(prompt("exponer", ordered("pedido", "corrido", "ritmo", "un latch SR")));
        assertThat(paceFirst).contains("un latch SR", "pace CONTINUOUS").doesNotContain("corrido un latch");

        String exportar = text(prompt("exportar", ordered("formato", "para", "pedido", "la guía en png")));
        assertThat(exportar).contains("format PNG", "para la guía en");

        String unknownKey = text(prompt("armar", ordered("pedido", "un", "extra", "sumador de 4 bits")));
        assertThat(unknownKey).contains("un sumador de 4 bits");
    }

    @Test
    void theReceivedArgumentsAreLoggedForTheUsersCheck() {
        assertThat(VsimPrompts.received("armar", ordered("pedido", "un", "otro", "sumador")))
                .isEqualTo("prompt armar received pedido=«un», otro=«sumador»");
        assertThat(VsimPrompts.received("guia", Map.of())).isEqualTo("prompt guia received nothing");
    }

    @Test
    void exponerDeCorridoGivesThePaceParametersOfSetPresentation() {
        String continuous = text(prompt("exponer", Map.of("pedido", "un latch SR", "ritmo", "de corrido")));

        assertThat(continuous).contains("vsim_set_presentation", "presenting true", "pace CONTINUOUS",
                "pauseMillis", "vsim_begin_stage", "vsim_await_step");
    }

    @Test
    void exponerPasoAPasoAsksForStepByStepAndWithoutAPaceLeavesItToTheUser() {
        String stepByStep = text(prompt("exponer", Map.of("pedido", "un latch SR", "ritmo", "Paso a paso")));
        String unchosen = text(prompt("exponer", Map.of("pedido", "un latch SR")));

        assertThat(stepByStep).contains("pace STEP_BY_STEP").doesNotContain("pace CONTINUOUS");
        assertThat(unchosen).doesNotContain("pace CONTINUOUS", "pace STEP_BY_STEP").contains("presenting true");
    }

    @Test
    void anUnknownPaceStaysPartOfTheRequestAndAPaceInsideTheCircuitTextCounts() {
        String spilled = text(prompt("exponer", ordered("pedido", "sumador", "ritmo", "de 4 bits")));

        assertThat(spilled).contains("sumador de 4 bits").doesNotContain("pace CONTINUOUS", "pace STEP_BY_STEP");
    }

    @Test
    void exportarNamesTheFormatAndDefaultsToPdf() {
        assertThat(text(prompt("exportar", Map.of("formato", "png")))).contains("format PNG");
        assertThat(text(prompt("exportar", Map.of("formato", "SVG")))).contains("format SVG");
        assertThat(text(prompt("exportar", Map.of("pedido", "en svg para la guía")))).contains("format SVG");
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

    /** Review M2: setting inputs is simulation state, so «probar» must not make the AI refuse to test. */
    @Test
    void probarSaysThatSettingInputsDoesNotChangeTheCircuit() {
        assertThat(text(prompt("probar", Map.of()))).contains("poner entradas y avanzar la simulación no lo cambia");
    }

    /** Review M3: «exponer» names the AI's narration and its questions in VSim. */
    @Test
    void exponerSaysWhatComesAndAsksInVsim() {
        assertThat(text(prompt("exponer", Map.of("pedido", "un latch SR")))).contains("vsim_say", "vsim_ask_user");
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
                "vsim_check_stage", "llega cortado o vacío");
        assertThat(contents.text()).doesNotContain("     "); // no line runs on with the next one's indentation
    }

    /** The details the instructions leave to the tools (IA.3 review C1) are in the tools' descriptions. */
    @Test
    void theToolsCarryTheDetailsTheInstructionsLeaveOut() {
        Map<String, String> descriptions = client.listTools().tools().stream()
                .collect(Collectors.toMap(McpSchema.Tool::name, McpSchema.Tool::description));

        assertThat(descriptions.get("vsim_say")).contains("never sees", "subtitle",
                "do not repeat the step's own text");
        assertThat(descriptions.get("vsim_ask_user")).contains("one question at a time", "ask it in the chat too",
                "vsim_await_answer", "vsim_withdraw_question");
        assertThat(descriptions.get("vsim_begin_stage")).contains("origin", "storyboard");
        assertThat(descriptions.get("vsim_check_stage")).contains("continuousStops");
        assertThat(descriptions.get("vsim_edit")).contains("layoutOnly", "PROPOSED", "vsim_await_step");
    }

    private McpSchema.GetPromptResult prompt(String name, Map<String, Object> arguments) {
        return client.getPrompt(McpSchema.GetPromptRequest.builder(name).arguments(arguments).build());
    }

    private static String text(McpSchema.GetPromptResult result) {
        return result.messages().stream()
                .map(message -> ((McpSchema.TextContent) message.content()).text())
                .collect(Collectors.joining("\n"));
    }

    /** Arguments in the order given, as a client that splits the text would send them. */
    private static Map<String, Object> ordered(String... namesAndValues) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            arguments.put(namesAndValues[index], namesAndValues[index + 1]);
        }
        return arguments;
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
        variants.put("armar", Map.of("pedido", "un contador de 3 bits"));
        variants.put("armar vacío", Map.of());
        variants.put("exponer", Map.of("pedido", "un latch SR"));
        variants.put("exponer vacío", Map.of());
        variants.put("exponer paso", Map.of("pedido", "un latch SR", "ritmo", "paso a paso"));
        variants.put("exponer corrido", Map.of("pedido", "un latch SR", "ritmo", "de corrido"));
        variants.put("explicar", Map.of());
        variants.put("revisar", Map.of());
        variants.put("probar", Map.of());
        variants.put("probar que", Map.of("pedido", "la salida S con todas las entradas"));
        variants.put("componente", Map.of());
        variants.put("componente nombrado", Map.of("pedido", "Medio sumador con las dos compuertas"));
        variants.put("exportar", Map.of());
        variants.put("exportar png", Map.of("formato", "png"));
        variants.put("exportar svg", Map.of("formato", "svg"));
        return variants;
    }
}
