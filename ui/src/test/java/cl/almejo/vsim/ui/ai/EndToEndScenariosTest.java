package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.mcp.VsimServerInstructions;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The user's own examples (spec §1), built the way the AI builds them: an MCP client over real HTTP against the
 * server, the automation API and the editor's host on headless circuits, calling only the tools and following the
 * server's instructions (read, begin a task, edit by stages, check each stage with vsim_check_stage, end the task).
 */
class EndToEndScenariosTest {

    private static final String SECRET = "an-end-to-end-secret-for-tests";

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    @TempDir
    Path exportFolder;

    private final List<OpenDocument> open = new CopyOnWriteArrayList<>();
    /** The windows of the circuits that have one (export, window picture); the others have none. */
    private final List<ShownWindow> windows = new CopyOnWriteArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open),
            document -> windows.stream().filter(window -> window.view().openDocument() == document).toList(),
            EditorWindows.NONE, new PreferenceProposals(AppPreferences.inMemory()));
    private RunningServer server;
    private McpSyncClient client;
    private int nextOperation = 1;

    @BeforeEach
    void connect() throws Exception {
        Messages.initialize(Messages.SPANISH);
        server = VsimMcpServer.start(new AutomationApi(host), new McpServerSettings(0, SECRET));
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
                        .endpoint("/mcp")
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + SECRET))
                        .build())
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        McpSchema.InitializeResult initialized = client.initialize();
        assertThat(initialized.instructions()).isEqualTo(VsimServerInstructions.TEXT);
    }

    @AfterEach
    void disconnect() throws Exception {
        client.close();
        server.stop();
        SwingUtilities.invokeAndWait(() -> windows.forEach(window -> window.view().dispose()));
        open.forEach(OpenDocument::close);
    }

    // --- the D latch ---

    @Test
    void theAiBuildsADLatchInFourEditsChecksItAndMakesItAComponentWithoutReplacing() {
        String document = openCircuit("Flip-flop D");
        String task = beginTask(document, "Flip-flop D", "Latch D con NAND", "Componente");
        String stage = "Latch D con NAND";
        long version = versionOf(document);

        Map<String, Object> placed = edit(document, version, task, stage, "Coloco las piezas", List.of(
                part("d", "input-pin", 0, 2, "D"),
                part("e", "input-pin", 0, 12, "E"),
                part("front", "nand2", 10, 3, ""),
                part("back", "nand2", 10, 12, ""),
                part("top", "nand2", 22, 4, ""),
                part("bottom", "nand2", 22, 12, ""),
                part("q", "output-pin", 34, 5, "Q"),
                part("notQ", "output-pin", 34, 13, "Q'")));
        Map<String, String> ids = aliases(placed);
        version = versionIn(placed);

        Map<String, Object> front = edit(document, version, task, stage, "Conecto la entrada", List.of(
                connect(ids.get("d"), "pin", ids.get("front"), "in0"),
                connect(ids.get("e"), "pin", ids.get("front"), "in1"),
                connect(ids.get("e"), "pin", ids.get("back"), "in1"),
                connect(ids.get("front"), "out", ids.get("back"), "in0",
                        List.of(point(16, 5), point(16, 10), point(8, 10), point(8, 12)))));
        version = versionIn(front);

        Map<String, Object> latch = edit(document, version, task, stage, "Conecto la realimentación", List.of(
                connect(ids.get("front"), "out", ids.get("top"), "in0"),
                connect(ids.get("back"), "out", ids.get("bottom"), "in1"),
                connect(ids.get("top"), "out", ids.get("q"), "pin"),
                connect(ids.get("bottom"), "out", ids.get("notQ"), "pin"),
                connect(ids.get("top"), "out", ids.get("bottom"), "in0",
                        List.of(point(28, 6), point(28, 9), point(19, 9), point(19, 12))),
                connect(ids.get("bottom"), "out", ids.get("top"), "in1",
                        List.of(point(30, 14), point(30, 11), point(20, 11), point(20, 8)))));
        version = versionIn(latch);

        Map<String, Object> noted = edit(document, version, task, stage, "Explico cómo probarlo", List.of(
                Map.of("op", "AddNote", "text", "Prueba: con E en 1, Q sigue a D; con E en 0, Q se queda",
                        "x", 0, "y", 22)));
        version = versionIn(noted);
        for (Map<String, Object> receipt : List.of(placed, front, latch, noted)) {
            assertThat(list(receipt, "warnings")).as("%s", receipt).isEmpty();
        }

        // With E at 0 a latch holds whatever it had: on the fresh copy each such row never settles. It only uses up
        // its share of the default budget (cutShort), so the transparent rows the check expects are still measured.
        List<Map<String, Object>> transparentRows = List.of(
                row(Map.of("D", "0", "E", "1"), Map.of("Q", "0", "Q'", "1")),
                row(Map.of("D", "1", "E", "1"), Map.of("Q", "1", "Q'", "0")));
        Map<String, Object> table = call("vsim_truth_table", Map.of("document", document));
        assertThat(list(table, "rows")).filteredOn(tableRow -> Boolean.TRUE.equals(tableRow.get("cutShort")))
                .hasSize(2);

        Map<String, Object> checked = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", stage, "checks", List.of(
                        Map.of("kind", "truthTable", "expected", transparentRows),
                        Map.of("kind", "layout"))));
        assertThat(checked).as("%s", checked).containsEntry("passed", true);
        Map<String, Object> holding = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", stage, "checks", List.of(Map.of("kind", "truthTable", "expected", List.of(
                        row(Map.of("D", "0", "E", "0"), Map.of("Q", "0")))))));
        assertThat(list(holding, "checks").getFirst()).containsEntry("status", "NOT_FINISHED");
        assertThat((String) list(holding, "checks").getFirst().get("summary")).contains("eventBudget", "signals");

        // The memory, on the live circuit. One vsim_set_inputs call sets its values at one instant (the gates react
        // after it), so the AI pauses and gives the latch time between values, as the tool's description says.
        assertThat(toolDescription("vsim_set_inputs")).contains("same simulation time", "vsim_step");
        call("vsim_simulation", Map.of("document", document, "action", "pause"));
        setInputs(document, version, List.of(input(ids.get("d"), "1"), input(ids.get("e"), "1")));
        setInputs(document, version, List.of(input(ids.get("e"), "0")));
        setInputs(document, version, List.of(input(ids.get("d"), "0")));
        Map<String, Object> holds = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", stage, "checks", List.of(Map.of("kind", "signals", "expected", List.of(
                        Map.of("pin", pin(ids.get("q"), "pin"), "value", "1"),
                        Map.of("pin", pin(ids.get("notQ"), "pin"), "value", "0"))))));
        assertThat(holds).as("%s", holds).containsEntry("passed", true);

        List<String> stageElements = elementIds(call("vsim_read_circuit", Map.of("document", document)));
        Map<String, Object> component = edit(document, version, task, "Componente", "Creo el componente", List.of(
                Map.of("op", "CreateComponent", "alias", "latch", "name", "Latch D", "targets", stageElements,
                        "replaceSelection", false)));
        assertThat(list(component, "components")).singleElement().satisfies(created ->
                assertThat(created).containsEntry("name", "Latch D"));
        call("vsim_end_task", Map.of("taskId", task));

        Map<String, Object> catalogue = call("vsim_describe_components", Map.of("document", document,
                "filter", "Latch D"));
        assertThat(list(catalogue, "types")).singleElement().satisfies(type -> {
            assertThat(type).containsEntry("origin", "CIRCUIT");
            assertThat(list(type, "pins")).extracting(pin -> pin.get("name"))
                    .containsExactlyInAnyOrder("D", "E", "Q", "Q'");
        });
        Map<String, Object> after = call("vsim_read_circuit", Map.of("document", document));
        assertThat(list(after, "parts")).as("the stage stays as «cómo se hizo»").hasSize(8);
    }

    // --- the adders ---

    @Test
    void theAiBuildsA4BitAdderFromTwo2BitAddersMadeOfFullAdders() {
        String document = openCircuit("Sumador de 4 bits");
        String task = beginTask(document, "Sumador de 4 bits", "Sumador completo", "Sumador de 2 bits",
                "Sumador de 4 bits");

        // Stage 1: a full adder from gates, checked and made a component that keeps its parts.
        String fullAdderStage = "Sumador completo";
        long version = versionOf(document);
        Map<String, Object> gates = edit(document, version, task, fullAdderStage, "Coloco las compuertas", List.of(
                part("a", "input-pin", 0, 1, "A"),
                part("b", "input-pin", 0, 7, "B"),
                part("cin", "input-pin", 0, 15, "Cin"),
                part("x1", "xor2", 10, 2, ""),
                part("x2", "xor2", 22, 4, ""),
                part("a1", "and2", 22, 12, ""),
                part("a2", "and2", 22, 20, ""),
                part("o1", "or2", 32, 14, ""),
                part("s", "output-pin", 42, 5, "S"),
                part("cout", "output-pin", 42, 15, "Cout")));
        Map<String, String> ids = aliases(gates);
        List<Map<String, Object>> straight = List.of(
                connect(ids.get("a"), "pin", ids.get("x1"), "in0"),
                connect(ids.get("b"), "pin", ids.get("x1"), "in1"),
                connect(ids.get("x1"), "out", ids.get("x2"), "in0"),
                connect(ids.get("cin"), "pin", ids.get("x2"), "in1"),
                connect(ids.get("x1"), "out", ids.get("a1"), "in0"),
                connect(ids.get("cin"), "pin", ids.get("a1"), "in1"));
        List<Map<String, Object>> rest = List.of(
                connect(ids.get("a1"), "out", ids.get("o1"), "in0"),
                connect(ids.get("a2"), "out", ids.get("o1"), "in1"),
                connect(ids.get("x2"), "out", ids.get("s"), "pin"),
                connect(ids.get("o1"), "out", ids.get("cout"), "pin"));
        List<Map<String, Object>> firstTry = new ArrayList<>(straight);
        firstTry.add(connect(ids.get("a"), "pin", ids.get("a2"), "in0"));
        firstTry.add(connect(ids.get("b"), "pin", ids.get("a2"), "in1"));
        firstTry.addAll(rest);
        Map<String, Object> rejected = refusal("vsim_edit", editArguments(document, versionIn(gates), task,
                fullAdderStage, "Conecto las compuertas", firstTry));
        assertThat(rejected).containsEntry("error", "ROUTE_REJECTED");
        assertThat((String) rejected.get("message")).contains("Operation 6 (Connect, counting from 0)");
        assertThat(map(rejected, "recovery")).containsKeys("triedRoute", "wouldJoin");
        assertThat(versionOf(document)).as("nothing was changed").isEqualTo(versionIn(gates));

        // As the instructions say: read triedRoute and wouldJoin, retry with via points round the other nets.
        List<Map<String, Object>> secondTry = new ArrayList<>(straight);
        secondTry.add(connect(ids.get("a"), "pin", ids.get("a2"), "in0", List.of(point(4, 2), point(4, 20))));
        secondTry.add(connect(ids.get("b"), "pin", ids.get("a2"), "in1", List.of(point(3, 8), point(3, 24))));
        secondTry.addAll(rest);
        Map<String, Object> wired = edit(document, versionIn(gates), task, fullAdderStage, "Conecto las compuertas",
                secondTry);
        Map<String, Object> region = Map.of("x", -2, "y", -2, "width", 50, "height", 30);
        Map<String, Object> fullAdderCheck = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", fullAdderStage, "checks", List.of(
                        Map.of("kind", "truthTable", "elementIds", stageElements(document, region),
                                "expected", adderRows(1)),
                        Map.of("kind", "layout"))));
        assertThat(fullAdderCheck).as("%s", fullAdderCheck).containsEntry("passed", true);
        Map<String, Object> fullAdder = edit(document, versionIn(fullAdderCheck), task, fullAdderStage,
                "Lo convierto en componente", List.of(Map.of("op", "CreateComponent", "alias", "fullAdder",
                        "name", "Sumador completo", "targets", stageElements(document, region),
                        "replaceSelection", false)));
        assertThat(list(fullAdder, "warnings")).isEmpty();
        Map<String, Object> fullAdderType = describe(document, "Sumador completo");
        assertThat(list(fullAdderType, "pins")).extracting(pin -> pin.get("name"))
                .containsExactly("A", "B", "Cin", "S", "Cout");

        // Stage 2: two instances of it, chained by the carry.
        String twoBitStage = "Sumador de 2 bits";
        Map<String, Object> twoBitParts = edit(document, versionIn(fullAdder), task, twoBitStage,
                "Coloco dos sumadores completos", List.of(
                        part("a0", "input-pin", 0, 37, "A0"),
                        part("b0", "input-pin", 0, 43, "B0"),
                        part("cin", "input-pin", 0, 49, "Cin"),
                        part("a1", "input-pin", 0, 55, "A1"),
                        part("b1", "input-pin", 0, 61, "B1"),
                        instance("low", "Sumador completo", 14, 40),
                        instance("high", "Sumador completo", 14, 56),
                        part("s0", "output-pin", 30, 41, "S0"),
                        part("s1", "output-pin", 30, 57, "S1"),
                        part("cout", "output-pin", 30, 63, "Cout")));
        ids = aliases(twoBitParts);
        Map<String, Object> twoBitWires = edit(document, versionIn(twoBitParts), task, twoBitStage,
                "Encadeno el acarreo", List.of(
                        connect(ids.get("a0"), "pin", ids.get("low"), "A"),
                        connect(ids.get("b0"), "pin", ids.get("low"), "B"),
                        connect(ids.get("cin"), "pin", ids.get("low"), "Cin"),
                        connect(ids.get("a1"), "pin", ids.get("high"), "A"),
                        connect(ids.get("b1"), "pin", ids.get("high"), "B"),
                        connect(ids.get("low"), "Cout", ids.get("high"), "Cin",
                                List.of(point(22, 44), point(22, 52), point(11, 52), point(11, 62))),
                        connect(ids.get("low"), "S", ids.get("s0"), "pin"),
                        connect(ids.get("high"), "S", ids.get("s1"), "pin"),
                        connect(ids.get("high"), "Cout", ids.get("cout"), "pin")));
        assertThat(list(twoBitWires, "warnings")).as("%s", twoBitWires).isEmpty();
        Map<String, Object> twoBitRegion = Map.of("x", -2, "y", 34, "width", 38, "height", 34);
        Map<String, Object> twoBitCheck = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", twoBitStage, "checks", List.of(
                        Map.of("kind", "truthTable", "elementIds", stageElements(document, twoBitRegion),
                                "expected", adderRows(2)),
                        Map.of("kind", "layout"))));
        assertThat(twoBitCheck).as("%s", twoBitCheck).containsEntry("passed", true);
        Map<String, Object> twoBit = edit(document, versionIn(twoBitCheck), task, twoBitStage,
                "Lo convierto en componente", List.of(Map.of("op", "CreateComponent", "name", "Sumador de 2 bits",
                        "targets", stageElements(document, twoBitRegion), "replaceSelection", false)));
        assertThat(list(twoBit, "warnings")).isEmpty();
        assertThat(list(describe(document, "Sumador de 2 bits"), "pins")).extracting(pin -> pin.get("name"))
                .containsExactly("A0", "B0", "Cin", "A1", "B1", "S0", "S1", "Cout");

        // Stage 3: two instances of the 2-bit adder make the 4-bit one; the inputs fan in on their own columns so
        // no two routes share a corner, as the AI works out from the pins' positions.
        String fourBitStage = "Sumador de 4 bits";
        Map<String, Object> fourBitParts = edit(document, versionIn(twoBit), task, fourBitStage,
                "Coloco dos sumadores de 2 bits", List.of(
                        part("a0", "input-pin", 0, 77, "A0"),
                        part("b0", "input-pin", 0, 83, "B0"),
                        part("cin", "input-pin", 0, 89, "Cin"),
                        part("a1", "input-pin", 0, 95, "A1"),
                        part("b1", "input-pin", 0, 101, "B1"),
                        part("a2", "input-pin", 0, 109, "A2"),
                        part("b2", "input-pin", 0, 115, "B2"),
                        part("a3", "input-pin", 0, 121, "A3"),
                        part("b3", "input-pin", 0, 127, "B3"),
                        instance("low", "Sumador de 2 bits", 20, 84),
                        instance("high", "Sumador de 2 bits", 20, 112),
                        part("s0", "output-pin", 36, 81, "S0"),
                        part("s1", "output-pin", 36, 87, "S1"),
                        part("s2", "output-pin", 36, 111, "S2"),
                        part("s3", "output-pin", 36, 117, "S3"),
                        part("cout", "output-pin", 36, 123, "Cout")));
        ids = aliases(fourBitParts);
        Map<String, Object> fourBitWires = edit(document, versionIn(fourBitParts), task, fourBitStage,
                "Conecto las entradas, el acarreo y las salidas", List.of(
                        connect(ids.get("a0"), "pin", ids.get("low"), "A0", List.of(point(12, 78), point(12, 86))),
                        connect(ids.get("b0"), "pin", ids.get("low"), "B0", List.of(point(10, 84), point(10, 88))),
                        connect(ids.get("cin"), "pin", ids.get("low"), "Cin"),
                        connect(ids.get("a1"), "pin", ids.get("low"), "A1", List.of(point(10, 96), point(10, 92))),
                        connect(ids.get("b1"), "pin", ids.get("low"), "B1", List.of(point(12, 102), point(12, 94))),
                        connect(ids.get("a2"), "pin", ids.get("high"), "A0",
                                List.of(point(12, 110), point(12, 114))),
                        connect(ids.get("b2"), "pin", ids.get("high"), "B0"),
                        connect(ids.get("a3"), "pin", ids.get("high"), "A1",
                                List.of(point(10, 122), point(10, 120))),
                        connect(ids.get("b3"), "pin", ids.get("high"), "B1",
                                List.of(point(12, 128), point(12, 122))),
                        connect(ids.get("low"), "Cout", ids.get("high"), "Cin",
                                List.of(point(28, 90), point(28, 106), point(16, 106), point(16, 118))),
                        connect(ids.get("low"), "S0", ids.get("s0"), "pin", List.of(point(30, 86), point(30, 82))),
                        connect(ids.get("low"), "S1", ids.get("s1"), "pin"),
                        connect(ids.get("high"), "S0", ids.get("s2"), "pin",
                                List.of(point(30, 114), point(30, 112))),
                        connect(ids.get("high"), "S1", ids.get("s3"), "pin",
                                List.of(point(32, 116), point(32, 118))),
                        connect(ids.get("high"), "Cout", ids.get("cout"), "pin",
                                List.of(point(30, 118), point(30, 124)))));
        assertThat(list(fourBitWires, "warnings")).as("%s", fourBitWires).isEmpty();
        Map<String, Object> fourBitRegion = Map.of("x", -2, "y", 74, "width", 42, "height", 58);
        List<Map<String, Object>> fourBitRows = adderRows(4);
        assertThat(fourBitRows).hasSize(512);
        Map<String, Object> fourBitCheck = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", fourBitStage, "checks", List.of(
                        Map.of("kind", "truthTable", "elementIds", stageElements(document, fourBitRegion),
                                "expected", fourBitRows),
                        Map.of("kind", "layout"))));
        assertThat(fourBitCheck).as("%s", fourBitCheck).containsEntry("passed", true);
        assertThat(map(list(fourBitCheck, "checks").getFirst(), "details"))
                .containsEntry("evaluatedRows", 512).containsEntry("failedRows", 0);

        call("vsim_end_task", Map.of("taskId", task));
        Map<String, Object> summary = map(call("vsim_read_circuit", Map.of("document", document)), "summary");
        assertThat(summary.get("userComponents").toString()).contains("Sumador completo", "Sumador de 2 bits");
        assertThat(summary.get("partsByType").toString()).contains("subcircuit:sumador-completo=2",
                "subcircuit:sumador-de-2-bits=2");
    }

    /** The circuit's own component {@code name} as vsim_describe_component gives it. */
    private Map<String, Object> describe(String document, String name) {
        return call("vsim_describe_component", Map.of("document", document, "typeId", typeOf(document, name)));
    }

    private static Map<String, Object> instance(String alias, String definition, int x, int y) {
        return Map.of("op", "PlaceInstance", "alias", alias, "definition", definition, "x", x, "y", y);
    }

    /** The typeId of the circuit's own component {@code name}, as the catalogue lists it. */
    private String typeOf(String document, String name) {
        Map<String, Object> catalogue = call("vsim_describe_components", Map.of("document", document,
                "filter", name));
        return list(catalogue, "types").stream()
                .filter(type -> "CIRCUIT".equals(type.get("origin")) && name.equals(type.get("definitionName")))
                .map(type -> (String) type.get("typeId")).findFirst().orElseThrow();
    }

    /** The rows of an adder of {@code bits} bits with carry in: inputs A0.., B0.., Cin; outputs S0.., Cout. */
    private static List<Map<String, Object>> adderRows(int bits) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int a = 0; a < 1 << bits; a++) {
            for (int b = 0; b < 1 << bits; b++) {
                for (int carry = 0; carry < 2; carry++) {
                    int sum = a + b + carry;
                    Map<String, String> inputs = new LinkedHashMap<>();
                    Map<String, String> outputs = new LinkedHashMap<>();
                    for (int bit = 0; bit < bits; bit++) {
                        String suffix = bits == 1 ? "" : String.valueOf(bit);
                        inputs.put("A" + suffix, String.valueOf((a >> bit) & 1));
                        inputs.put("B" + suffix, String.valueOf((b >> bit) & 1));
                        outputs.put("S" + suffix, String.valueOf((sum >> bit) & 1));
                    }
                    inputs.put("Cin", String.valueOf(carry));
                    outputs.put("Cout", String.valueOf((sum >> bits) & 1));
                    rows.add(row(inputs, outputs));
                }
            }
        }
        return rows;
    }

    /** The ids of the parts and wires in {@code region}: one stage of the circuit. */
    private List<String> stageElements(String document, Map<String, Object> region) {
        return elementIds(call("vsim_read_circuit", Map.of("document", document, "region", region,
                "pageSize", 200)));
    }

    // --- the user works too ---

    @Test
    void anEditPreparedBeforeTheUsersMoveIsRefusedAndTheRetryAfterReadingAgainKeepsTheMove() throws Exception {
        String document = openCircuit("Latch SR");
        OpenDocument circuit = open.getLast();
        String task = beginTask(document, "Latch SR", "Latch SR con NAND");
        Map<String, Object> placed = edit(document, versionOf(document), task, "Latch SR con NAND",
                "Coloco las compuertas", List.of(part("top", "nand2", 10, 0, ""), part("bottom", "nand2", 10, 10, "")));
        Map<String, String> ids = aliases(placed);
        long readVersion = versionIn(placed);

        // The user drags the bottom gate down while the AI prepares its next edit.
        SwingUtilities.invokeAndWait(() -> circuit.execute(MoveWithWiresCommand.of(circuit.document(),
                circuit.registry(), Set.of(new ElementId(ids.get("bottom"))), 0, 6)));

        Map<String, Object> next = editArguments(document, readVersion, task, "Latch SR con NAND",
                "Agrego las entradas", List.of(part("s", "input-pin", 0, 0, "S'"),
                        connect("new:s", "pin", ids.get("top"), "in0")));
        Map<String, Object> stale = refusal("vsim_edit", next);
        assertThat(stale).containsEntry("error", "STALE_DOCUMENT");
        long currentVersion = ((Number) map(stale, "recovery").get("currentVersion")).longValue();
        assertThat(currentVersion).isGreaterThan(readVersion);
        assertThat(list(call("vsim_read_circuit", Map.of("document", document)), "parts")).hasSize(2);

        // Read again, adapt to where the user put the gate and send the edit with the current version.
        Map<String, Object> reread = call("vsim_read_circuit", Map.of("document", document,
                "elementIds", List.of(ids.get("bottom"))));
        assertThat(versionIn(reread)).isEqualTo(currentVersion);
        assertThat(map(list(reread, "parts").getFirst(), "position")).isEqualTo(Map.of("x", 10, "y", 16));
        Map<String, Object> retried = new LinkedHashMap<>(next);
        retried.put("version", currentVersion);
        retried.put("operationId", "operation-retry");
        retried.put("operations", List.of(part("s", "input-pin", 0, 0, "S'"),
                connect("new:s", "pin", ids.get("top"), "in0"),
                part("r", "input-pin", 0, 19, "R'"),
                connect("new:r", "pin", ids.get("bottom"), "in1")));
        Map<String, Object> applied = call("vsim_edit", retried);
        assertThat(applied).containsEntry("applied", true);
        assertThat(onEditor(() -> circuit.document().component(new ElementId(ids.get("bottom"))).orElseThrow()
                .position())).isEqualTo(new GridPoint(10, 16));

        // Undoing the task stops at the user's move: their work stays.
        Map<String, Object> undone = call("vsim_undo_task", Map.of("taskId", task));
        assertThat(undone).containsEntry("undone", 1).containsEntry("stoppedAtUserEdit", true);
        assertThat(list(call("vsim_read_circuit", Map.of("document", document)), "parts")).hasSize(2);
        call("vsim_end_task", Map.of("taskId", task));
    }

    @Test
    void undoingTheTaskReturnsTheCircuitToWhereTheAiStarted() throws Exception {
        String document = openCircuit("Mi circuito");
        OpenDocument circuit = open.getLast();
        onEditor(() -> UiTestSupport.place(circuit, StandardComponentTypes.NAND2, 40, 40, Map.of()));
        Map<String, Object> start = withoutVersions(call("vsim_read_circuit", Map.of("document", document,
                "detail", "FULL")));
        String task = beginTask(document, "Inversor", "Inversor", "Componente");

        Map<String, Object> placed = edit(document, versionOf(document), task, "Inversor", "Coloco las piezas",
                List.of(part("a", "input-pin", 0, 1, "A"), part("not", "not", 8, 0, ""),
                        part("y", "output-pin", 18, 1, "Y")));
        Map<String, String> ids = aliases(placed);
        Map<String, Object> wired = edit(document, versionIn(placed), task, "Inversor", "Conecto", List.of(
                connect(ids.get("a"), "pin", ids.get("not"), "in"),
                connect(ids.get("not"), "out", ids.get("y"), "pin")));
        Map<String, Object> component = edit(document, versionIn(wired), task, "Componente", "Creo el componente",
                List.of(Map.of("op", "CreateComponent", "alias", "inverter", "name", "Inversor",
                                "targets", List.of(ids.get("a"), ids.get("not"), ids.get("y")),
                                "replaceSelection", false),
                        instance("copy", "new:inverter", 30, 0),
                        Map.of("op", "AddGuide", "orientation", "VERTICAL", "position", 30),
                        Map.of("op", "MakeFrame", "title", "Inversor", "targets",
                                List.of(ids.get("a"), ids.get("not"), ids.get("y")))));
        assertThat(component).containsEntry("applied", true);

        Map<String, Object> undone = call("vsim_undo_task", Map.of("taskId", task));

        assertThat(undone).containsEntry("undone", 3).containsEntry("stoppedAtUserEdit", false);
        assertThat(withoutVersions(call("vsim_read_circuit", Map.of("document", document, "detail", "FULL"))))
                .isEqualTo(start);
        assertThat(onEditor(() -> circuit.document().definitions())).isEmpty();
        call("vsim_end_task", Map.of("taskId", task));
    }

    // --- a runaway simulation ---

    @Test
    void aRingOscillatorStopsOnItsBudgetWithBudgetExceededAndTheEditorStillAnswers() {
        String document = openCircuit("Oscilador");
        String task = beginTask(document, "Oscilador", "Oscilador en anillo");
        Map<String, Object> placed = edit(document, versionOf(document), task, "Oscilador en anillo",
                "Coloco la compuerta y la realimento", List.of(
                        part("enable", "input-pin", 0, 1, "EN"),
                        part("gate", "nand2", 8, 0, ""),
                        part("y", "output-pin", 20, 1, "Y"),
                        connect("new:enable", "pin", "new:gate", "in0"),
                        connect("new:gate", "out", "new:y", "pin"),
                        connect("new:gate", "out", "new:gate", "in1",
                                List.of(point(14, 2), point(14, 7), point(6, 7), point(6, 4)))));
        Map<String, String> ids = aliases(placed);
        call("vsim_end_task", Map.of("taskId", task));
        call("vsim_simulation", Map.of("document", document, "action", "pause"));
        call("vsim_set_inputs", Map.of("document", document, "version", versionIn(placed),
                "operationId", "enable", "values", List.of(input(ids.get("enable"), "1"))));

        Map<String, Object> runaway = refusal("vsim_step", Map.of("document", document, "kind", "DURATION",
                "amount", 1_000_000_000, "eventBudget", 1_000));
        assertThat(runaway).containsEntry("error", "BUDGET_EXCEEDED");
        Map<String, Object> receipt = map(map(runaway, "recovery"), "receipt");
        assertThat(receipt).containsEntry("outcome", "BUDGET_EXCEEDED");
        assertThat(Long.parseLong((String) receipt.get("time"))).isPositive().isLessThan(1_000_000_000L);

        Map<String, Object> table = call("vsim_truth_table", Map.of("document", document, "eventBudget", 5_000));
        assertThat(table).containsEntry("budgetExceeded", true);
        assertThat(list(table, "rows").getLast()).containsEntry("cutShort", true).containsEntry("settled", false);

        Map<String, Object> signals = call("vsim_read_signals", Map.of("document", document, "pins",
                List.of(pin(ids.get("y"), "pin"))));
        assertThat(signals).containsEntry("running", false);
    }

    // --- files and pictures ---

    @Test
    void anExportGoesIntoTheExportFolderUnderANewNameAndNeverOverwrites() throws Exception {
        String document = openCircuit("Inversor");
        OpenDocument circuit = open.getLast();
        EditorView view = windowFor(circuit);
        view.services().preferences().setPrintDirectory(exportFolder);
        onEditor(() -> UiTestSupport.place(circuit, StandardComponentTypes.NOT, 0, 0, Map.of()));
        Path existing = exportFolder.resolve("inversor.pdf");
        Files.writeString(existing, "the user's own file");

        Map<String, Object> first = call("vsim_export", Map.of("document", document, "format", "PDF",
                "fileName", "inversor"));
        Map<String, Object> second = call("vsim_export", Map.of("document", document, "format", "PDF",
                "fileName", "inversor"));

        assertThat(first).containsEntry("fileName", "inversor-2.pdf")
                .containsEntry("folder", exportFolder.toAbsolutePath().toString());
        assertThat(second).containsEntry("fileName", "inversor-3.pdf");
        assertThat(Files.readString(existing)).isEqualTo("the user's own file");
        for (Map<String, Object> receipt : List.of(first, second)) {
            Path written = Path.of((String) receipt.get("path"));
            assertThat(written).hasParent(exportFolder.toAbsolutePath());
            assertThat(new String(Files.readAllBytes(written), 0, 5, StandardCharsets.US_ASCII))
                    .isEqualTo("%PDF-");
        }
        // A name that tries to leave the folder is only a name: the file still lands in the export folder.
        Map<String, Object> escaping = call("vsim_export", Map.of("document", document, "format", "PDF",
                "fileName", "../fuera"));
        assertThat(Path.of((String) escaping.get("path"))).hasParent(exportFolder.toAbsolutePath());
    }

    @Test
    void theWindowPictureIsAPngOfTheWindow() throws Exception {
        String document = openCircuit("Inversor");
        OpenDocument circuit = open.getLast();
        windowFor(circuit);
        onEditor(() -> UiTestSupport.place(circuit, StandardComponentTypes.NOT, 0, 0, Map.of()));

        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder("vsim_window_picture")
                .arguments(Map.of("document", document)).build());

        assertThat(result.isError()).isNotEqualTo(true);
        McpSchema.ImageContent image = result.content().stream()
                .filter(McpSchema.ImageContent.class::isInstance).map(McpSchema.ImageContent.class::cast)
                .findFirst().orElseThrow();
        assertThat(image.mimeType()).isEqualTo("image/png");
        BufferedImage picture = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.data())));
        assertThat(picture.getWidth()).isEqualTo(((Number) structured(result).get("width")).intValue()).isPositive();
        assertThat(picture.getHeight()).isPositive();
    }

    /** A window for {@code circuit} that is never shown, laid out at 1200 × 800 as if it were. */
    private EditorView windowFor(OpenDocument circuit) throws Exception {
        AtomicReference<EditorView> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            EditorServices services = new EditorServices(PainterRegistry.standard(),
                    new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of());
            EditorView view = new EditorView(circuit, services, NO_WINDOW);
            JRootPane pane = new JRootPane();
            pane.setJMenuBar(view.menuBar());
            pane.setContentPane(view);
            pane.setSize(1200, 800);
            layOut(pane);
            windows.add(new ShownWindow(view, pane, circuit.displayName() + " - VSim", List.of()));
            created.set(view);
        });
        return created.get();
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }

    private static <T> T onEditor(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /** A read without its version fields, to compare the circuit's contents at two versions. */
    private static Map<String, Object> withoutVersions(Map<String, Object> read) {
        Map<String, Object> contents = new LinkedHashMap<>(read);
        contents.remove("version");
        return contents;
    }

    // --- helpers: the calls the AI makes ---

    private String openCircuit(String name) {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName(name);
        open.add(new OpenDocument(circuit, StandardComponentTypes.registry(), null, false));
        List<Map<String, Object>> documents = list(call("vsim_list_documents", Map.of()), "documents");
        return (String) documents.getLast().get("id");
    }

    private String beginTask(String document, String title, String... stages) {
        List<Map<String, Object>> plan = new ArrayList<>();
        for (String stage : stages) {
            plan.add(Map.of("title", stage));
        }
        return (String) call("vsim_begin_task", Map.of("document", document, "title", title, "stages", plan))
                .get("taskId");
    }

    private long versionOf(String document) {
        return versionIn(call("vsim_read_circuit", Map.of("document", document)));
    }

    private Map<String, Object> edit(String document, long version, String task, String stage, String step,
                                     List<Map<String, Object>> operations) {
        return call("vsim_edit", editArguments(document, version, task, stage, step, operations));
    }

    private Map<String, Object> editArguments(String document, long version, String task, String stage,
                                              String step, List<Map<String, Object>> operations) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("document", document);
        arguments.put("version", version);
        arguments.put("operationId", "operation-" + nextOperation++);
        arguments.put("taskId", task);
        arguments.put("stage", stage);
        arguments.put("step", step);
        arguments.put("text", step);
        arguments.put("operations", operations);
        return arguments;
    }

    private static Map<String, Object> part(String alias, String typeId, int x, int y, String label) {
        return Map.of("op", "PlacePart", "alias", alias, "typeId", typeId, "x", x, "y", y, "label", label);
    }

    private static Map<String, Object> connect(String from, String fromPin, String to, String toPin) {
        return connect(from, fromPin, to, toPin, List.of());
    }

    /** A Connect through {@code via}, as the instructions say after ROUTE_REJECTED or to go around a part. */
    private static Map<String, Object> connect(String from, String fromPin, String to, String toPin,
                                               List<Map<String, Object>> via) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", "Connect");
        operation.put("from", Map.of("target", from, "pinName", fromPin));
        operation.put("to", Map.of("target", to, "pinName", toPin));
        if (!via.isEmpty()) {
            operation.put("via", via);
        }
        return operation;
    }

    /** Sets {@code values} with the simulation paused, then lets it run 20 gate delays. */
    private void setInputs(String document, long version, List<Map<String, Object>> values) {
        call("vsim_set_inputs", Map.of("document", document, "version", version, "operationId",
                "inputs-" + nextOperation++, "values", values));
        call("vsim_step", Map.of("document", document, "kind", "DURATION", "amount", 20));
    }

    private String toolDescription(String name) {
        return client.listTools().tools().stream().filter(tool -> tool.name().equals(name)).findFirst()
                .orElseThrow().description();
    }

    private static Map<String, Object> point(int x, int y) {
        return Map.of("x", x, "y", y);
    }

    private static Map<String, Object> input(String elementId, String value) {
        return Map.of("elementId", elementId, "value", value);
    }

    private static Map<String, Object> pin(String elementId, String pinName) {
        return Map.of("elementId", elementId, "pinName", pinName, "index", 0);
    }

    /** Every part and wire id a read lists (what «select all» would take). */
    private static List<String> elementIds(Map<String, Object> circuit) {
        List<String> ids = new ArrayList<>();
        list(circuit, "parts").forEach(part -> ids.add((String) part.get("id")));
        list(circuit, "wires").forEach(wire -> ids.add((String) wire.get("id")));
        return ids;
    }

    private static Map<String, Object> row(Map<String, String> inputs, Map<String, String> outputs) {
        return Map.of("inputs", inputs, "outputs", outputs);
    }

    private Map<String, Object> call(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder(name)
                .arguments(arguments).build());
        assertThat(result.isError()).as("%s answered %s", name, result.structuredContent()).isNotEqualTo(true);
        return structured(result);
    }

    private Map<String, Object> refusal(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder(name)
                .arguments(arguments).build());
        assertThat(result.isError()).as("%s answered %s", name, result.structuredContent()).isTrue();
        return structured(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(McpSchema.CallToolResult result) {
        return (Map<String, Object>) result.structuredContent();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> aliases(Map<String, Object> receipt) {
        return (Map<String, String>) receipt.get("aliases");
    }

    private static long versionIn(Map<String, Object> result) {
        return ((Number) result.get("version")).longValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> object, String key) {
        return (Map<String, Object>) object.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> object, String key) {
        return (List<Map<String, Object>>) object.get(key);
    }
}
