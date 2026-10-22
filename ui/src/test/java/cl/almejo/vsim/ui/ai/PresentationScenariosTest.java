package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.StepPacer;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.ai.present.AutomationPresentationControls;
import cl.almejo.vsim.ui.ai.present.PresentationEvents;
import cl.almejo.vsim.ui.ai.present.PresentationExtension;
import cl.almejo.vsim.ui.ai.present.PresentationProbe;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
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

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Presentar» end to end (IA.2, spec §4–§6): an MCP client over real HTTP plays the AI and follows the server's
 * instructions (ask for «Presentar», one step per vsim_edit, vsim_begin_stage, vsim_await_step until APPLIED), against
 * the automation API and the editor's host on a headless window that has the «Presentar» extension installed: its
 * floating panel's buttons are what the «user» presses. «De corrido» runs on a clock and timer the test moves by hand.
 */
class PresentationScenariosTest {

    private static final String SECRET = "a-presenting-secret-for-tests";
    private static final Duration PAUSE = Duration.ofMillis(500);

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final List<OpenDocument> open = new CopyOnWriteArrayList<>();
    /**
     * The window's preferences: «Presentar», its pace and pause, as the toggle and the panel write them; layered over
     * the saved ones as a real window's, so the AI may give its task a pace of its own (IA.3 «exponer»).
     */
    private final AppPreferences preferences = AppPreferences.inMemory().layered();
    private final ManualPacer pacer = new ManualPacer();
    /** The panel's buttons ask the API on this thread; the test waits for it to finish what a press started. */
    private final ExecutorService buttonsThread = Executors.newSingleThreadExecutor();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open),
            document -> List.of(), new PresentingWindows(), new PreferenceProposals(AppPreferences.inMemory()), pacer);
    private final AutomationApi api = new AutomationApi(host);
    private final PresentationExtension presentation = new PresentationExtension(PresentationEvents.of(api),
            new AutomationPresentationControls(api, buttonsThread), host::documentId);
    /** What the AI's presenting stream said, as the «user» heard it after the panel did. */
    private final List<StepPreview> proposedSteps = new CopyOnWriteArrayList<>();
    private final List<StepPreview> appliedSteps = new CopyOnWriteArrayList<>();
    private final BlockingQueue<String> heard = new LinkedBlockingQueue<>();
    private final List<String> pressFailures = new CopyOnWriteArrayList<>();
    /** The fake «user»: presses «Siguiente ▸» in the panel as soon as a step is proposed. */
    private volatile boolean pressesSiguiente;
    private Runnable stopListening;
    private OpenDocument circuit;
    private EditorView view;
    private JRootPane root;
    private RunningServer server;
    private McpSyncClient client;
    private String document;
    private long known;
    private int nextOperation = 1;

    @BeforeEach
    void connect() throws Exception {
        Messages.initialize(Messages.SPANISH);
        preferences.setAnimateViewMoves(false); // travels jump, so the test sees where the view went
        CircuitDocument empty = new CircuitDocument();
        empty.setName("Sumador");
        circuit = new OpenDocument(empty, StandardComponentTypes.registry(), null, false);
        open.add(circuit);
        SwingUtilities.invokeAndWait(() -> {
            EditorServices services = new EditorServices(PainterRegistry.standard(),
                    new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of(presentation));
            view = new EditorView(circuit, services, NO_WINDOW);
            root = new JRootPane();
            root.setJMenuBar(view.menuBar());
            root.setContentPane(view);
            root.setSize(1200, 800);
            layOut(root);
        });
        stopListening = api.addPresentationListener(new TheUser());
        server = VsimMcpServer.start(api, new McpServerSettings(0, SECRET));
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
                        .endpoint("/mcp")
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + SECRET))
                        .build())
                .requestTimeout(Duration.ofSeconds(90))
                .build();
        client.initialize();
        document = (String) list(call("vsim_list_documents", Map.of()), "documents").getFirst().get("id");
    }

    @AfterEach
    void disconnect() throws Exception {
        client.close();
        server.stop();
        stopListening.run();
        buttonsThread.shutdownNow();
        SwingUtilities.invokeAndWait(() -> view.dispose());
        open.forEach(OpenDocument::close);
    }

    // --- the user's story, «Paso a paso» ---

    @Test
    void theUsersAdderStoryInPasoAPasoLeavesFourFramesEveryStepOneUndoAndEveryGhostWhatWasApplied()
            throws Exception {
        assertThat(onEditor(() -> circuit.history().canUndo())).isFalse();
        // The user wants to see how it is built: the AI reads the mode and asks for «Presentar».
        assertThat(call("vsim_get_presentation", Map.of("document", document))).containsEntry("presenting", false);
        Map<String, Object> asked = call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        assertThat(asked).containsEntry("presenting", true).containsEntry("pace", "STEP_BY_STEP");
        assertThat(preferences.aiPresenting()).as("the window's toggle turned on").isTrue();
        pressesSiguiente = true;

        String task = beginTask("Sumador de 4 bits y su tabla", "Sumador de 2 bits", "Sumador de 4 bits",
                "Componente de 4 bits", "Tabla de verdad");
        awaitHeard(event -> event.startsWith("planChanged"));
        onEditor(() -> null); // the panel has handled what it heard before the test's listener
        assertThat(onEditor(() -> panelNotice())).as("the panel says the AI switched")
                .isEqualTo(Messages.text("ai.present.aiSwitched"));
        known = versionOf();
        int steps = 0;

        // Stage 1: a full adder from gates, made a component (kept as «cómo se hizo»), two of them make 2 bits.
        Shift first = new Shift(beginStage(task, 1), 1);
        String twoBit = "Sumador de 2 bits";
        Map<String, Object> gates = step(task, twoBit, "Coloco las compuertas del sumador completo",
                "Dos XOR para la suma, dos AND y una OR para el acarreo", List.of(
                        part("a", "input-pin", first.x(0), first.y(1), "A"),
                        part("b", "input-pin", first.x(0), first.y(7), "B"),
                        part("cin", "input-pin", first.x(0), first.y(15), "Cin"),
                        part("x1", "xor2", first.x(10), first.y(2), ""),
                        part("x2", "xor2", first.x(22), first.y(4), ""),
                        part("a1", "and2", first.x(22), first.y(12), ""),
                        part("a2", "and2", first.x(22), first.y(20), ""),
                        part("o1", "or2", first.x(32), first.y(14), ""),
                        part("s", "output-pin", first.x(42), first.y(5), "S"),
                        part("cout", "output-pin", first.x(42), first.y(15), "Cout")));
        steps++;
        Map<String, String> ids = aliases(gates);
        step(task, twoBit, "Conecto las compuertas", "Cada señal a su compuerta, rodeando las otras", List.of(
                connect(ids.get("a"), "pin", ids.get("x1"), "in0"),
                connect(ids.get("b"), "pin", ids.get("x1"), "in1"),
                connect(ids.get("x1"), "out", ids.get("x2"), "in0"),
                connect(ids.get("cin"), "pin", ids.get("x2"), "in1"),
                connect(ids.get("x1"), "out", ids.get("a1"), "in0"),
                connect(ids.get("cin"), "pin", ids.get("a1"), "in1"),
                connect(ids.get("a"), "pin", ids.get("a2"), "in0", List.of(first.point(4, 2), first.point(4, 20))),
                connect(ids.get("b"), "pin", ids.get("a2"), "in1", List.of(first.point(3, 8), first.point(3, 24))),
                connect(ids.get("a1"), "out", ids.get("o1"), "in0"),
                connect(ids.get("a2"), "out", ids.get("o1"), "in1"),
                connect(ids.get("x2"), "out", ids.get("s"), "pin"),
                connect(ids.get("o1"), "out", ids.get("cout"), "pin")));
        steps++;
        step(task, twoBit, "Creo el sumador completo", "Sin reemplazar: las compuertas quedan como «cómo se hizo»",
                List.of(Map.of("op", "CreateComponent", "name", "Sumador completo",
                        "targets", elementsIn(first.region(-2, -2, 50, 30)), "replaceSelection", false)));
        steps++;
        Map<String, Object> pair = step(task, twoBit, "Coloco dos sumadores completos", "Uno por bit", List.of(
                part("a0", "input-pin", first.x(0), first.y(37), "A0"),
                part("b0", "input-pin", first.x(0), first.y(43), "B0"),
                part("cin", "input-pin", first.x(0), first.y(49), "Cin"),
                part("a1", "input-pin", first.x(0), first.y(55), "A1"),
                part("b1", "input-pin", first.x(0), first.y(61), "B1"),
                instance("low", "Sumador completo", first.x(14), first.y(40)),
                instance("high", "Sumador completo", first.x(14), first.y(56)),
                part("s0", "output-pin", first.x(30), first.y(41), "S0"),
                part("s1", "output-pin", first.x(30), first.y(57), "S1"),
                part("cout", "output-pin", first.x(30), first.y(63), "Cout")));
        steps++;
        ids = aliases(pair);
        step(task, twoBit, "Encadeno el acarreo", "El acarreo del bit 0 entra al bit 1", List.of(
                connect(ids.get("a0"), "pin", ids.get("low"), "A"),
                connect(ids.get("b0"), "pin", ids.get("low"), "B"),
                connect(ids.get("cin"), "pin", ids.get("low"), "Cin"),
                connect(ids.get("a1"), "pin", ids.get("high"), "A"),
                connect(ids.get("b1"), "pin", ids.get("high"), "B"),
                connect(ids.get("low"), "Cout", ids.get("high"), "Cin", List.of(first.point(22, 44),
                        first.point(22, 52), first.point(11, 52), first.point(11, 62))),
                connect(ids.get("low"), "S", ids.get("s0"), "pin"),
                connect(ids.get("high"), "S", ids.get("s1"), "pin"),
                connect(ids.get("high"), "Cout", ids.get("cout"), "pin")));
        steps++;
        Map<String, Object> twoBitRegion = first.region(-2, 34, 38, 34);
        checkTruthTable(task, twoBit, elementsIn(twoBitRegion), adderRows(2));
        step(task, twoBit, "Creo el sumador de 2 bits", "Lo usaré dos veces", List.of(Map.of("op",
                "CreateComponent", "name", "Sumador de 2 bits", "targets", elementsIn(twoBitRegion),
                "replaceSelection", false)));
        steps++;

        // Stage 2: two instances of the 2-bit adder make the 4-bit one.
        String fourBit = "Sumador de 4 bits";
        Shift second = new Shift(beginStage(task, 2), 77);
        Map<String, Object> halves = step(task, fourBit, "Coloco dos sumadores de 2 bits", "Bits 0–1 y 2–3",
                List.of(part("a0", "input-pin", second.x(0), second.y(77), "A0"),
                        part("b0", "input-pin", second.x(0), second.y(83), "B0"),
                        part("cin", "input-pin", second.x(0), second.y(89), "Cin"),
                        part("a1", "input-pin", second.x(0), second.y(95), "A1"),
                        part("b1", "input-pin", second.x(0), second.y(101), "B1"),
                        part("a2", "input-pin", second.x(0), second.y(109), "A2"),
                        part("b2", "input-pin", second.x(0), second.y(115), "B2"),
                        part("a3", "input-pin", second.x(0), second.y(121), "A3"),
                        part("b3", "input-pin", second.x(0), second.y(127), "B3"),
                        instance("low", twoBit, second.x(20), second.y(84)),
                        instance("high", twoBit, second.x(20), second.y(112)),
                        part("s0", "output-pin", second.x(36), second.y(81), "S0"),
                        part("s1", "output-pin", second.x(36), second.y(87), "S1"),
                        part("s2", "output-pin", second.x(36), second.y(111), "S2"),
                        part("s3", "output-pin", second.x(36), second.y(117), "S3"),
                        part("cout", "output-pin", second.x(36), second.y(123), "Cout")));
        steps++;
        ids = aliases(halves);
        step(task, fourBit, "Conecto las entradas, el acarreo y las salidas", "Cada entrada por su columna",
                List.of(connect(ids.get("a0"), "pin", ids.get("low"), "A0",
                                List.of(second.point(12, 78), second.point(12, 86))),
                        connect(ids.get("b0"), "pin", ids.get("low"), "B0",
                                List.of(second.point(10, 84), second.point(10, 88))),
                        connect(ids.get("cin"), "pin", ids.get("low"), "Cin"),
                        connect(ids.get("a1"), "pin", ids.get("low"), "A1",
                                List.of(second.point(10, 96), second.point(10, 92))),
                        connect(ids.get("b1"), "pin", ids.get("low"), "B1",
                                List.of(second.point(12, 102), second.point(12, 94))),
                        connect(ids.get("a2"), "pin", ids.get("high"), "A0",
                                List.of(second.point(12, 110), second.point(12, 114))),
                        connect(ids.get("b2"), "pin", ids.get("high"), "B0"),
                        connect(ids.get("a3"), "pin", ids.get("high"), "A1",
                                List.of(second.point(10, 122), second.point(10, 120))),
                        connect(ids.get("b3"), "pin", ids.get("high"), "B1",
                                List.of(second.point(12, 128), second.point(12, 122))),
                        connect(ids.get("low"), "Cout", ids.get("high"), "Cin", List.of(second.point(28, 90),
                                second.point(28, 106), second.point(16, 106), second.point(16, 118))),
                        connect(ids.get("low"), "S0", ids.get("s0"), "pin",
                                List.of(second.point(30, 86), second.point(30, 82))),
                        connect(ids.get("low"), "S1", ids.get("s1"), "pin"),
                        connect(ids.get("high"), "S0", ids.get("s2"), "pin",
                                List.of(second.point(30, 114), second.point(30, 112))),
                        connect(ids.get("high"), "S1", ids.get("s3"), "pin",
                                List.of(second.point(32, 116), second.point(32, 118))),
                        connect(ids.get("high"), "Cout", ids.get("cout"), "pin",
                                List.of(second.point(30, 118), second.point(30, 124)))));
        steps++;
        List<String> fourBitElements = elementsIn(second.region(-2, 74, 42, 58));
        checkTruthTable(task, fourBit, fourBitElements, adderRows(4));

        // Stage 3: the 4-bit adder becomes a component (its stage stays as «cómo se hizo») and one instance shows it.
        String component = "Componente de 4 bits";
        Shift third = new Shift(beginStage(task, 3), 0);
        step(task, component, "Creo el componente", "Sin reemplazar las piezas de la etapa anterior",
                List.of(Map.of("op", "CreateComponent", "name", fourBit, "targets", fourBitElements,
                        "replaceSelection", false)));
        steps++;
        step(task, component, "Coloco una instancia", "Así se ve el sumador como una caja",
                List.of(instance("adder", fourBit, third.x(0), third.y(0))));
        steps++;

        // Stage 4: its truth table, on an instance with its inputs and outputs.
        String table = "Tabla de verdad";
        Shift fourth = new Shift(beginStage(task, 4), 2);
        Map<String, Object> tested = step(task, table, "Coloco el sumador que voy a probar", "Una caja nueva",
                List.of(instance("adder", fourBit, fourth.x(14), fourth.y(2))));
        steps++;
        String adder = aliases(tested).get("adder");
        List<Map<String, Object>> wiring = wireEveryPin(adder);
        Map<String, Object> wired = step(task, table, "Le pongo entradas y salidas", "Una por patita", wiring);
        steps++;
        assertThat(list(wired, "warnings")).as("%s", wired).isEmpty();
        List<String> tableElements = new ArrayList<>();
        tableElements.add(adder);
        tableElements.addAll(addedBy(wired));
        checkTruthTable(task, table, tableElements, adderRows(4));
        call("vsim_end_task", Map.of("taskId", task));

        assertThat(pressFailures).isEmpty();
        assertThat(proposedSteps).hasSize(steps);
        assertThat(appliedSteps).extracting(StepPreview::text).isEqualTo(proposedSteps.stream()
                .map(StepPreview::text).toList());

        // Four frames, left to right, on the same top, each titled with its stage.
        List<Frame> frames = onEditor(this::framesLeftToRight);
        assertThat(frames).extracting(Frame::title).containsExactly(twoBit, fourBit, component, table);
        for (int index = 1; index < frames.size(); index++) {
            Frame previous = frames.get(index - 1);
            assertThat(frames.get(index).topLeft().x()).isGreaterThan(previous.topLeft().x() + previous.width());
            assertThat(frames.get(index).topLeft().y()).isEqualTo(frames.getFirst().topLeft().y());
        }
        assertThat(frames).extracting(Frame::locked).as("the stages kept as «cómo se hizo» are locked")
                .containsExactly(true, true, false, false);

        // «Dejar solo el resultado»: the panel offers it at the end; the truth table's frame and every definition stay.
        Set<String> everything = onEditor(this::elementIds);
        pressInThePanel("ai.present.keepOnlyResult");
        List<Frame> kept = onEditor(this::framesLeftToRight);
        assertThat(kept).extracting(Frame::title).containsExactly(table);
        Set<String> inTheResult = onEditor(() -> values(FrameContents.of(circuit.document(), circuit.registry(),
                kept.getFirst())));
        Set<String> expected = new HashSet<>(inTheResult);
        expected.add(kept.getFirst().id().value());
        assertThat(onEditor(this::elementIds)).isEqualTo(expected).contains(adder);
        assertThat(onEditor(() -> circuit.document().definitions())).extracting(definition -> definition.name())
                .containsExactlyInAnyOrder("Sumador completo", twoBit, fourBit);
        onEditor(() -> {
            circuit.history().undo();
            return null;
        });
        assertThat(onEditor(this::elementIds)).as("one undo brings every stage back").isEqualTo(everything);

        // Every step was one undo, and so was every frame VSim made, and locking the 4-bit stage's frame once a
        // component was made of it in the next stage: undoing them all leaves the circuit as it began.
        List<String> undone = onEditor(() -> {
            List<String> descriptions = new ArrayList<>();
            while (circuit.history().canUndo()) {
                descriptions.add(circuit.history().undoDescription().orElse("?"));
                circuit.history().undo();
            }
            return descriptions;
        });
        assertThat(undone).hasSize(steps + frames.size() + 1);
        // IA.3: VSim's frames and their lock undo as «Deshacer marco de etapa», the AI's steps as its changes.
        assertThat(undone).filteredOn("command.aiEdit"::equals).hasSize(steps);
        assertThat(undone).filteredOn("command.aiStageFrame"::equals).hasSize(frames.size() + 1);
        assertThat(onEditor(() -> circuit.document().elements())).isEmpty();
    }

    /** The inputs and outputs of {@code adder}, each one straight onto its pin, inputs left and outputs right. */
    private List<Map<String, Object>> wireEveryPin(String adder) {
        Map<String, Object> read = call("vsim_read_circuit", Map.of("document", document, "detail", "FULL",
                "elementIds", List.of(adder)));
        List<Map<String, Object>> pins = list(list(read, "parts").getFirst(), "pins");
        GridPoint inputPin = pinOffset("input-pin");
        GridPoint outputPin = pinOffset("output-pin");
        List<Map<String, Object>> operations = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> pin : pins) {
            String name = (String) map(pin, "pin").get("pinName");
            Map<String, Object> position = map(pin, "position");
            int x = ((Number) position.get("x")).intValue();
            int y = ((Number) position.get("y")).intValue();
            String alias = "io" + index;
            boolean input = "INPUT".equalsIgnoreCase((String) pin.get("direction"));
            // Inputs two columns deep, so the pins of neighbouring inputs never touch.
            int distance = input ? (index % 2 == 0 ? 6 : 12) : 6;
            GridPoint offset = input ? inputPin : outputPin;
            operations.add(part(alias, input ? "input-pin" : "output-pin",
                    (input ? x - distance : x + distance) - offset.x(), y - offset.y(), name));
            operations.add(input ? connect("new:" + alias, "pin", adder, name)
                    : connect(adder, name, "new:" + alias, "pin"));
            index++;
        }
        return operations;
    }

    /** Where the pin of a {@code typeId} part lies from the part's position, as vsim_describe_component says. */
    private GridPoint pinOffset(String typeId) {
        Map<String, Object> described = call("vsim_describe_component", Map.of("document", document,
                "typeId", typeId));
        Map<String, Object> position = map(list(described, "pins").getFirst(), "position");
        return new GridPoint(((Number) position.get("x")).intValue(), ((Number) position.get("y")).intValue());
    }

    // --- «De corrido» ---

    @Test
    void deCorridoAppliesTheFirstStepAtOnceAndEachNextOneAfterThePauseOnTheTestsClock() throws Exception {
        preferences.setAiPace(PresentationPace.CONTINUOUS);
        preferences.setAiPauseMillis((int) PAUSE.toMillis());
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = beginTask("Tres luces", "Luces");
        known = versionOf();

        Map<String, Object> firstLamp = call("vsim_edit", stepArguments(task, "Luces", "Pongo la primera",
                List.of(part("one", "led", 0, 0, ""))));
        assertThat(firstLamp).containsEntry("status", "APPLIED").containsEntry("applied", true);
        assertThat(pacer.requested).as("the first step waits for nothing").isEmpty();
        known = versionIn(firstLamp);

        CompletableFuture<Map<String, Object>> secondLamp = CompletableFuture.supplyAsync(() -> call("vsim_edit",
                stepArguments(task, "Luces", "Pongo la segunda", List.of(part("two", "led", 6, 0, "")))));
        assertThat(pacer.nextRequestedDelay()).isEqualTo(PAUSE);
        assertThat(secondLamp).isNotDone();
        assertThat(onEditor(() -> circuit.document().components())).as("nothing applied during the pause")
                .hasSize(1);
        pacer.advance(PAUSE);
        Map<String, Object> second = secondLamp.get(30, TimeUnit.SECONDS);
        assertThat(second).containsEntry("status", "APPLIED").containsEntry("applied", true);
        known = versionIn(second);

        // An AI slower than the pause waits for nothing more.
        pacer.advance(Duration.ofSeconds(2));
        Map<String, Object> third = call("vsim_edit", stepArguments(task, "Luces", "Pongo la tercera",
                List.of(part("three", "led", 12, 0, ""))));
        assertThat(third).containsEntry("status", "APPLIED");
        assertThat(pacer.requested).isEmpty();
        call("vsim_end_task", Map.of("taskId", task));

        assertThat(onEditor(() -> circuit.document().components())).hasSize(3);
        assertThat(proposedSteps).as("«De corrido» shows no ghosts").isEmpty();
        assertThat(appliedSteps).extracting(StepPreview::text)
                .containsExactly("Pongo la primera", "Pongo la segunda", "Pongo la tercera");
    }

    // --- the user works while a ghost waits ---

    @Test
    void aGhostTheUserOutdatesIsDiscardedAsStaleAndTheAiProposesItAgain() throws Exception {
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = beginTask("Una luz", "Luz");
        known = versionOf();
        Map<String, Object> proposed = call("vsim_edit", stepArguments(task, "Luz", "Pongo la luz",
                List.of(part("lamp", "led", 0, 0, ""))));
        assertThat(proposed).containsEntry("status", "PROPOSED").containsEntry("applied", false);
        assertThat(versionOf()).as("a proposed step changes nothing").isEqualTo(known);
        assertThat(onEditor(() -> circuit.history().canUndo())).isFalse();

        // The user places a part of their own while the ghost waits.
        onEditor(() -> UiTestSupport.place(circuit, StandardComponentTypes.NAND2, 40, 40, Map.of()));

        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STALE");
        assertThat(onEditor(() -> circuit.document().components())).as("the old ghost was never applied")
                .hasSize(1);
        // As the instructions say: read again and propose the step again.
        known = versionOf();
        pressesSiguiente = true;
        Map<String, Object> again = call("vsim_edit", stepArguments(task, "Luz", "Pongo la luz",
                List.of(part("lamp", "led", 0, 0, ""))));
        assertThat(again).containsEntry("status", "PROPOSED");
        Map<String, Object> receipt = awaitApplied(task);
        assertThat(receipt).containsEntry("applied", true);
        call("vsim_end_task", Map.of("taskId", task));

        assertThat(onEditor(() -> circuit.document().components())).hasSize(2);
        assertThat(pressFailures).isEmpty();
    }

    // --- «Detener» ---

    @Test
    void detenerMidStageEndsTheWaitWithStoppedAndTheAisNextEditIsRefused() throws Exception {
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = beginTask("Dos luces", "Luces");
        known = versionOf();
        pressesSiguiente = true;
        step(task, "Luces", "Pongo la primera", "La de la izquierda", List.of(part("one", "led", 0, 0, "")));
        pressesSiguiente = false;
        Map<String, Object> waiting = call("vsim_edit", stepArguments(task, "Luces", "Pongo la segunda",
                List.of(part("two", "led", 6, 0, ""))));
        assertThat(waiting).containsEntry("status", "PROPOSED");
        awaitHeard(event -> event.equals("stepProposed Pongo la segunda"));

        pressInThePanel("ai.present.stop");

        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STOPPED");
        Map<String, Object> refused = refusal("vsim_edit", stepArguments(task, "Luces", "Pongo la tercera",
                List.of(part("three", "led", 12, 0, ""))));
        assertThat(refused).containsEntry("error", "NOT_ALLOWED");
        assertThat(onEditor(() -> circuit.document().components())).as("only the step applied before «Detener»")
                .hasSize(1);
        assertThat(heard).contains("stepDiscarded STOPPED");
    }

    // --- IA.3: the commands, followed ---

    @Test
    void theExponerCommandIsListedAndFollowedStepByStepUpToAPassedCheckInsideOneFrame() throws Exception {
        // The user types /mcp__vsim__ in Claude Code: the client lists VSim's commands and its help.
        List<String> names = client.listPrompts().prompts().stream().map(McpSchema.Prompt::name).toList();
        assertThat(names).containsExactlyInAnyOrder("guia", "armar", "exponer", "explicar", "revisar", "probar",
                "componente", "exportar");
        for (String name : names) {
            McpSchema.GetPromptResult empty = client.getPrompt(McpSchema.GetPromptRequest.builder(name).arguments(Map.of()).build());
            assertThat(empty.messages()).as("%s with nothing typed after it", name).singleElement()
                    .satisfies(message -> assertThat(message.role()).isEqualTo(McpSchema.Role.USER));
        }
        McpSchema.ReadResourceResult help = client.readResource(McpSchema.ReadResourceRequest.builder("vsim://ayuda").build());
        String helpText = ((McpSchema.TextResourceContents) help.contents().getFirst()).text();
        names.forEach(name -> assertThat(helpText).contains("/mcp__vsim__" + name));

        // «/mcp__vsim__exponer un inversor paso a paso», as a client that hands the whole line to the first argument.
        McpSchema.GetPromptResult exponer = client.getPrompt(McpSchema.GetPromptRequest.builder("exponer")
                .arguments(Map.of("pedido", "un inversor paso a paso")).build());
        String asked = ((McpSchema.TextContent) exponer.messages().getFirst().content()).text();
        assertThat(asked).contains("un inversor", "presenting true", "pace STEP_BY_STEP", "vsim_begin_stage",
                "vsim_await_step", "vsim_check_stage", "passed true");

        // The scripted AI does what the command says: «Presentar», the pace for this task, a stage, its check.
        assertThat(call("vsim_get_presentation", Map.of("document", document))).containsEntry("presenting", false);
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = beginTask("Un inversor", "Inversor");
        assertThat(call("vsim_set_presentation", Map.of("document", document, "presenting", true,
                "pace", "STEP_BY_STEP"))).containsEntry("pace", "STEP_BY_STEP");
        known = versionOf();
        pressesSiguiente = true;
        GridPoint origin = beginStage(task, 1);
        Map<String, Object> parts = step(task, "Inversor", "Coloco la entrada, el inversor y la salida",
                "La señal va de izquierda a derecha", List.of(
                        part("a", "input-pin", origin.x(), origin.y() + 1, "A"),
                        part("not", "not", origin.x() + 8, origin.y(), ""),
                        part("y", "output-pin", origin.x() + 18, origin.y() + 1, "Y")));
        Map<String, String> ids = aliases(parts);
        step(task, "Inversor", "Conecto la entrada y la salida", "Así el inversor recibe A y entrega Y", List.of(
                connect(ids.get("a"), "pin", ids.get("not"), "in"),
                connect(ids.get("not"), "out", ids.get("y"), "pin")));
        // The «Prueba» note in one note of three lines (IA.3: \n starts a new line), under the inverter.
        String test = "Prueba:\nA en 0: Y en 1\nA en 1: Y en 0";
        step(task, "Inversor", "Explico cómo probarlo", "Una nota de tres líneas", List.of(Map.of("op", "AddNote",
                "text", test, "x", origin.x(), "y", origin.y() + 6)));
        checkTruthTable(task, "Inversor", elementsIn(Map.of("x", origin.x() - 2, "y", origin.y() - 2,
                "width", 30, "height", 6)), List.of(
                Map.of("inputs", Map.of("A", "0"), "outputs", Map.of("Y", "1")),
                Map.of("inputs", Map.of("A", "1"), "outputs", Map.of("Y", "0"))));
        call("vsim_end_task", Map.of("taskId", task));
        assertThat(pressFailures).isEmpty();

        // One frame around the stage: it takes in every line of the note, and the stage began two columns right and
        // five rows down from its corner (IA.3: one more row than before, so the labels fit under the title).
        Frame frame = onEditor(this::framesLeftToRight).getFirst();
        assertThat(onEditor(() -> circuit.document().frames())).hasSize(1);
        assertThat(frame.title()).isEqualTo("Inversor");
        assertThat(origin).isEqualTo(new GridPoint(frame.topLeft().x() + 2, frame.topLeft().y() + 5));
        TextLabel note = onEditor(() -> circuit.document().elements().stream()
                .filter(TextLabel.class::isInstance)
                .map(TextLabel.class::cast).findFirst().orElseThrow());
        assertThat(note.text()).isEqualTo(test);
        Bounds drawn = DrawnBounds.of(note, circuit.registry());
        assertThat(drawn.height()).as("three lines are taller than one").isGreaterThan(2);
        assertThat(drawn.x()).isGreaterThanOrEqualTo(frame.topLeft().x());
        assertThat(drawn.y()).isGreaterThanOrEqualTo(frame.topLeft().y());
        assertThat(drawn.x() + drawn.width()).isLessThanOrEqualTo(frame.topLeft().x() + frame.width());
        assertThat(drawn.y() + drawn.height()).as("the third line is inside the frame")
                .isLessThanOrEqualTo(frame.topLeft().y() + frame.height());
    }

    // --- IA.3: what the AI says and asks, in VSim ---

    @Test
    void whatTheAiSaysIsASubtitleAndInTheStagesCardAndItsQuestionIsAnsweredInThePanel() throws Exception {
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = beginTask("Un contador", "Contador");
        String before = "Primero te pregunto de cuántos bits lo quieres";
        Map<String, Object> begun = call("vsim_begin_stage", Map.of("taskId", task, "stageNumber", 1,
                "narration", before));
        assertThat(begun).containsEntry("stageNumber", 1);
        assertThat(onEditor(() -> PresentationProbe.subtitle(presentation, view))).contains(before);
        assertThat(onEditor(() -> PresentationProbe.cardLines(presentation, view))).contains(before);

        String said = "Con tu respuesta elijo cuántos flip-flops pongo";
        Map<String, Object> saying = call("vsim_say", Map.of("taskId", task, "text", said));
        assertThat(saying).containsEntry("shown", true).containsEntry("presenting", true)
                .containsEntry("stageNumber", 1);
        assertThat(onEditor(() -> PresentationProbe.subtitle(presentation, view))).as("the new line replaces it")
                .contains(said);
        assertThat(onEditor(() -> PresentationProbe.cardLines(presentation, view))).contains(said)
                .doesNotContain(before);

        // The question shows in the floating panel, with a button per choice; the user presses one there.
        Map<String, Object> asked = call("vsim_ask_user", Map.of("taskId", task,
                "question", "¿De cuántos bits hago el contador?", "choices", List.of("2 bits", "3 bits", "4 bits")));
        assertThat(asked).containsEntry("status", "ASKED");
        String questionId = (String) asked.get("questionId");
        onEditor(() -> {
            QuestionCard card = PresentationProbe.questionCard(presentation, view).orElseThrow();
            assertThat(card.question().text()).isEqualTo("¿De cuántos bits hago el contador?");
            card.choiceButtons().stream().filter(button -> "3 bits".equals(button.getText())).findFirst()
                    .orElseThrow().doClick();
            return null;
        });
        buttonsThread.submit(() -> { }).get(30, TimeUnit.SECONDS);
        Map<String, Object> answer = call("vsim_await_answer", Map.of("questionId", questionId));
        assertThat(answer).containsEntry("status", "ANSWERED").containsEntry("choice", "3 bits");

        call("vsim_end_task", Map.of("taskId", task));
        assertThat(onEditor(() -> PresentationProbe.subtitle(presentation, view))).as("gone with the task")
                .isEmpty();
        assertThat(onEditor(() -> PresentationProbe.questionCard(presentation, view))).isEmpty();
    }

    // --- IA.3: a client that went away ---

    @Test
    void aGhostLeftByAClientThatStoppedCallingIsStoppedAfterTwoMinutesAndThePanelSaysWhy() throws Exception {
        call("vsim_set_presentation", Map.of("document", document, "presenting", true));
        String task = beginTask("Una luz", "Luz");
        known = versionOf();
        assertThat(call("vsim_edit", stepArguments(task, "Luz", "Pongo la luz",
                List.of(part("lamp", "led", 0, 0, ""))))).containsEntry("status", "PROPOSED");
        awaitHeard(event -> event.equals("stepProposed Pongo la luz"));

        // Nobody presses «Siguiente» and the AI never calls again (Claude Code was closed).
        pacer.advance(Duration.ofSeconds(119));
        assertThat(onEditor(() -> PresentationProbe.notice(presentation, view))).as("a minute and 59: still alive")
                .isNotEqualTo(Messages.text("ai.present.silent"));
        pacer.advance(Duration.ofSeconds(2));

        awaitHeard(event -> event.equals("stepDiscarded STOPPED"));
        assertThat(onEditor(() -> PresentationProbe.notice(presentation, view)))
                .isEqualTo(Messages.text("ai.present.silent"));
        assertThat(onEditor(() -> circuit.document().components())).as("the ghost was never applied").isEmpty();
        // A client that comes back hears that its task stopped.
        assertThat(call("vsim_await_step", Map.of("taskId", task))).containsEntry("status", "STOPPED");
        assertThat(refusal("vsim_edit", stepArguments(task, "Luz", "Pongo la luz",
                List.of(part("lamp", "led", 0, 0, ""))))).containsEntry("error", "NOT_ALLOWED");
    }

    // --- the AI's calls ---

    private String beginTask(String title, String... stages) {
        List<Map<String, Object>> plan = new ArrayList<>();
        for (String stage : stages) {
            plan.add(Map.of("title", stage));
        }
        return (String) call("vsim_begin_task", Map.of("document", document, "title", title, "stages", plan))
                .get("taskId");
    }

    /** vsim_begin_stage: the origin the stage is built at; the window has travelled there. */
    private GridPoint beginStage(String task, int stageNumber) throws Exception {
        Map<String, Object> begun = call("vsim_begin_stage", Map.of("taskId", task, "stageNumber", stageNumber));
        Map<String, Object> origin = map(begun, "origin");
        GridPoint point = new GridPoint(((Number) origin.get("x")).intValue(), ((Number) origin.get("y")).intValue());
        assertThat(onEditor(() -> view.canvas().shows(new Bounds(point.x(), point.y(), 1, 1))))
                .as("the window shows where stage %d begins", stageNumber).isTrue();
        return point;
    }

    /**
     * One step in «Paso a paso»: proposed (nothing applied yet), «Siguiente» in the panel, then vsim_await_step until
     * APPLIED. The ghost shown is exactly what the circuit has after it. The AI keeps the receipt's version.
     */
    private Map<String, Object> step(String task, String stage, String text, String reason,
                                     List<Map<String, Object>> operations) throws Exception {
        Map<String, Object> arguments = stepArguments(task, stage, text, operations);
        arguments.put("reason", reason);
        Map<String, Object> answer = call("vsim_edit", arguments);
        assertThat(answer).as("%s", text).containsEntry("status", "PROPOSED").containsEntry("applied", false);
        Map<String, Object> receipt = awaitApplied(task);
        known = versionIn(receipt);
        StepPreview ghost = proposedSteps.getLast();
        assertThat(ghost.text()).isEqualTo(text);
        assertThat(ghost.reason()).contains(reason);
        onEditor(() -> {
            for (DocumentElement element : ghost.ghostElements()) {
                assertThat(circuit.document().element(element.id())).as("%s: %s", text, element.id())
                        .contains(element);
            }
            for (ElementId removed : ghost.removedIds()) {
                assertThat(circuit.document().element(removed)).isEmpty();
            }
            return null;
        });
        return receipt;
    }

    private Map<String, Object> awaitApplied(String task) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Map<String, Object> outcome = call("vsim_await_step", Map.of("taskId", task));
            if (!"STILL_WAITING".equals(outcome.get("status"))) {
                assertThat(outcome).containsEntry("status", "APPLIED");
                return map(outcome, "receipt");
            }
        }
        throw new AssertionError("«Siguiente» never applied the step: " + pressFailures);
    }

    private Map<String, Object> stepArguments(String task, String stage, String text,
                                              List<Map<String, Object>> operations) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("document", document);
        arguments.put("version", known);
        arguments.put("operationId", "operation-" + nextOperation++);
        arguments.put("taskId", task);
        arguments.put("stage", stage);
        arguments.put("step", text);
        arguments.put("text", text);
        arguments.put("operations", operations);
        return arguments;
    }

    private void checkTruthTable(String task, String stage, List<String> elementIds,
                                 List<Map<String, Object>> rows) {
        Map<String, Object> checked = call("vsim_check_stage", Map.of("document", document, "taskId", task,
                "stage", stage, "checks", List.of(Map.of("kind", "truthTable", "elementIds", elementIds,
                        "expected", rows))));
        assertThat(checked).as("%s: %s", stage, checked).containsEntry("passed", true);
    }

    private List<String> elementsIn(Map<String, Object> region) {
        Map<String, Object> read = call("vsim_read_circuit", Map.of("document", document, "region", region,
                "pageSize", 200));
        List<String> ids = new ArrayList<>();
        list(read, "parts").forEach(part -> ids.add((String) part.get("id")));
        list(read, "wires").forEach(wire -> ids.add((String) wire.get("id")));
        return ids;
    }

    private long versionOf() {
        return versionIn(call("vsim_read_circuit", Map.of("document", document)));
    }

    // --- the user ---

    /** Presses the panel's button {@code key} on the editor thread and waits for what it asked the API to finish. */
    private void pressInThePanel(String key) throws Exception {
        onEditor(() -> {
            AbstractButton button = panelButton(key);
            assertThat(button.isEnabled()).as("%s enabled", key).isTrue();
            button.doClick();
            return null;
        });
        buttonsThread.submit(() -> { }).get(30, TimeUnit.SECONDS);
    }

    private AbstractButton panelButton(String key) {
        String text = Messages.text(key);
        return find(root, AbstractButton.class, button -> text.equals(button.getText()))
                .orElseThrow(() -> new AssertionError("no button " + text));
    }

    private String panelNotice() {
        return find(root, JLabel.class, label -> label.isVisible()
                && Messages.text("ai.present.aiSwitched").equals(label.getText()))
                .map(JLabel::getText).orElse("");
    }

    private static <T extends Component> Optional<T> find(Container container, Class<T> type, Predicate<T> wanted) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child) && wanted.test(type.cast(child))) {
                return Optional.of(type.cast(child));
            }
            if (child instanceof Container nested) {
                Optional<T> found = find(nested, type, wanted);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** Hears the presenting stream after the window's panel and overlays (it registered after them). */
    private final class TheUser implements PresentationListener {

        @Override
        public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
            heard.add("planChanged " + currentStage + " " + currentStep);
        }

        @Override
        public void stepProposed(StepPreview preview) {
            proposedSteps.add(preview);
            heard.add("stepProposed " + preview.text());
            if (pressesSiguiente) {
                // Queued after the panel's own update, so the button is enabled when it is pressed.
                SwingUtilities.invokeLater(() -> {
                    AbstractButton next = panelButton("ai.present.next");
                    if (next.isEnabled()) {
                        next.doClick();
                    } else {
                        pressFailures.add("«Siguiente» disabled for " + preview.text());
                    }
                });
            }
        }

        @Override
        public void stepApplied(StepPreview preview) {
            appliedSteps.add(preview);
            heard.add("stepApplied " + preview.text());
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
            heard.add("stepDiscarded " + why);
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
            heard.add("stageChecked " + stageIndex + " " + passed);
        }
    }

    private void awaitHeard(Predicate<String> wanted) throws InterruptedException {
        while (true) {
            String event = heard.poll(10, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("never heard it");
            }
            if (wanted.test(event)) {
                return;
            }
        }
    }

    /** The windows as {@code Application}'s: «Presentar» in the window's preferences, the AI's request, travel. */
    private final class PresentingWindows implements EditorWindows {

        @Override
        public OpenDocument openNewCircuit(String name) {
            throw new UnsupportedOperationException("no new windows in this test");
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument openDocument) {
            return SettingsOverride.of(preferences);
        }

        @Override
        public cl.almejo.vsim.automation.present.PresentationSettings presentationSettings(
                OpenDocument openDocument) {
            return PresentationPreferences.read(preferences);
        }

        @Override
        public void setPresenting(OpenDocument openDocument, boolean presenting) {
            if (presenting) {
                presentation.aiAskedForPresenting(host.documentId(openDocument));
            }
            preferences.setAiPresenting(presenting);
        }

        @Override
        public void travelToStageOrigin(OpenDocument openDocument, GridPoint origin) {
            presentation.overlaysOf(view).ifPresent(overlays -> overlays.travelToStageOrigin(origin));
        }
    }

    /** «De corrido»'s clock and timer, moved by the test. */
    private static final class ManualPacer implements StepPacer {

        private Instant now = Instant.parse("2026-10-21T12:00:00Z");
        private final List<Wait> waits = new ArrayList<>();
        final BlockingQueue<Duration> requested = new LinkedBlockingQueue<>();

        private record Wait(Instant due, CompletableFuture<Void> done) {
        }

        @Override
        public synchronized Instant now() {
            return now;
        }

        @Override
        public synchronized CompletableFuture<Void> after(Duration delay) {
            CompletableFuture<Void> done = new CompletableFuture<>();
            waits.add(new Wait(now.plus(delay), done));
            requested.add(delay);
            return done;
        }

        /** The watch over a gone client (IA.3): due like a pause, never one of the pauses the scenarios ask about. */
        @Override
        public synchronized CompletableFuture<Void> silenceCheckAfter(Duration delay) {
            CompletableFuture<Void> done = new CompletableFuture<>();
            waits.add(new Wait(now.plus(delay), done));
            return done;
        }

        void advance(Duration amount) {
            List<CompletableFuture<Void>> due = new ArrayList<>();
            synchronized (this) {
                now = now.plus(amount);
                waits.removeIf(wait -> {
                    if (!wait.due().isAfter(now)) {
                        due.add(wait.done());
                        return true;
                    }
                    return false;
                });
            }
            due.forEach(done -> done.complete(null));
        }

        Duration nextRequestedDelay() throws InterruptedException {
            Duration delay = requested.poll(30, TimeUnit.SECONDS);
            if (delay == null) {
                throw new AssertionError("no pause was asked for");
            }
            return delay;
        }
    }

    /** A stage's layout, written relative to where the stage begins (its origin), {@code top} rows above it. */
    private record Shift(GridPoint origin, int top) {

        int x(int relative) {
            return origin.x() + relative;
        }

        int y(int relative) {
            return origin.y() + relative - top;
        }

        Map<String, Object> point(int relativeX, int relativeY) {
            return Map.of("x", x(relativeX), "y", y(relativeY));
        }

        Map<String, Object> region(int relativeX, int relativeY, int width, int height) {
            return Map.of("x", x(relativeX), "y", y(relativeY), "width", width, "height", height);
        }
    }

    // --- helpers ---

    private List<Frame> framesLeftToRight() {
        List<Frame> frames = new ArrayList<>(circuit.document().frames());
        frames.sort(Comparator.comparingInt(frame -> frame.topLeft().x()));
        return frames;
    }

    private Set<String> elementIds() {
        Set<String> ids = new HashSet<>();
        circuit.document().elements().forEach(element -> ids.add(element.id().value()));
        return ids;
    }

    private static Set<String> values(Set<ElementId> ids) {
        Set<String> values = new HashSet<>();
        ids.forEach(id -> values.add(id.value()));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<String> addedBy(Map<String, Object> receipt) {
        return (List<String>) map(receipt, "changes").get("added");
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
                        inputs.put("A" + bit, String.valueOf((a >> bit) & 1));
                        inputs.put("B" + bit, String.valueOf((b >> bit) & 1));
                        outputs.put("S" + bit, String.valueOf((sum >> bit) & 1));
                    }
                    inputs.put("Cin", String.valueOf(carry));
                    outputs.put("Cout", String.valueOf((sum >> bits) & 1));
                    rows.add(Map.of("inputs", inputs, "outputs", outputs));
                }
            }
        }
        return rows;
    }

    private static Map<String, Object> part(String alias, String typeId, int x, int y, String label) {
        return Map.of("op", "PlacePart", "alias", alias, "typeId", typeId, "x", x, "y", y, "label", label);
    }

    private static Map<String, Object> instance(String alias, String definition, int x, int y) {
        return Map.of("op", "PlaceInstance", "alias", alias, "definition", definition, "x", x, "y", y);
    }

    private static Map<String, Object> connect(String from, String fromPin, String to, String toPin) {
        return connect(from, fromPin, to, toPin, List.of());
    }

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

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }

    private static <T> T onEditor(Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception | AssertionError problem) {
                failure.set(problem);
            }
        });
        if (failure.get() instanceof AssertionError assertion) {
            throw assertion;
        }
        if (failure.get() instanceof Exception exception) {
            throw exception;
        }
        return result.get();
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
