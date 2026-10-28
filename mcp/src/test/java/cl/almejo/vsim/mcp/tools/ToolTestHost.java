package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.FakeAutomationHost;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.automation.read.ComponentNames;
import cl.almejo.vsim.automation.read.ReadSource;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.automation.simulate.PreparedTruthTable;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.SimulationRequest;
import cl.almejo.vsim.automation.simulate.TruthTableRequest;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.TruthTableScope;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A headless editor for the tool tests: real circuits (document, registry, history) read and edited by the real
 * automation layer on the calling thread; the simulation answers with receipts the test sets, truth tables are real
 * (on a copy), pictures are a few bytes, exports and settings are recorded.
 */
final class ToolTestHost extends FakeAutomationHost {

    private static final ComponentTypeRegistry BASE_REGISTRY = StandardComponentTypes.registry();
    private static final ComponentNames NAMES = new ComponentNames() {
        @Override
        public String spanish(ComponentType type) {
            return "es " + type.id();
        }

        @Override
        public String english(ComponentType type) {
            return "en " + type.id();
        }

        @Override
        public String description(ComponentType type, Map<String, Object> parameters) {
            return "reglas de " + type.id();
        }
    };
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final Map<DocumentId, Circuit> circuits = new LinkedHashMap<>();
    final List<SimulationRequest> simulationRequests = new ArrayList<>();
    final List<ExportRequest> exports = new ArrayList<>();
    final List<String> settings = new ArrayList<>();
    final CompletableFuture<ProposalAnswer> answer = new CompletableFuture<>();
    List<DefinitionBundle> library = List.of();
    /** «Presentar» per circuit as the user chose it; a circuit not listed is in working mode. */
    final Map<DocumentId, PresentationSettings> presentations = new LinkedHashMap<>();
    private final List<String> layeredPresentations = new ArrayList<>();
    Function<SimulationRequest, SimulationReceipt> simulation = request -> receipt(request.document(),
            SimulationReceipt.Outcome.COMPLETED);

    Circuit open(String id) {
        Circuit circuit = new Circuit(new DocumentId(id), new CircuitDocument());
        circuits.put(circuit.id, circuit);
        return circuit;
    }

    @Override
    protected List<DocumentSummary> openDocuments() {
        return circuits.values().stream()
                .map(circuit -> new DocumentSummary(circuit.id, circuit.id.value(), circuit.version(),
                        circuit.history.isModified()))
                .toList();
    }

    @Override
    public <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        return run(() -> work.apply(circuit(document)));
    }

    @Override
    public <T> CompletableFuture<T> reading(DocumentId document, Function<ReadSource, T> work) {
        return run(() -> {
            Circuit circuit = circuit(document);
            return work.apply(new ReadSource(circuit.id, circuit.version(), circuit.id.value(), circuit.document,
                    circuit.registry, new NetlistCompiler(circuit.registry).compile(circuit.document),
                    circuit.document.revision(), new SimulationSnapshot(0, circuit.document.revision(), 0, false, Map.of(), Map.of(),
                            List.of(), Optional.empty()),
                    NAMES, library, TextMeasure.APPROXIMATE));
        });
    }

    /** What the simulation holds in every memory, set by the test; empty: the simulation has none. */
    Optional<cl.almejo.vsim.core.memory.MemoryContents> currentMemory = Optional.empty();

    @Override
    public CompletableFuture<Optional<cl.almejo.vsim.core.memory.MemoryContents>> currentMemory(
            DocumentId document, String component) {
        return CompletableFuture.completedFuture(currentMemory);
    }

    @Override
    public CompletableFuture<RegionPicture> pictureOf(DocumentId document, Optional<Bounds> region,
                                                      boolean asOnScreen) {
        return run(() -> new RegionPicture(document, circuit(document).version(),
                region.orElse(new Bounds(0, 0, 10, 10)), asOnScreen, 80, 80, PNG));
    }

    @Override
    public CompletableFuture<WindowPicture> pictureOfWindow(DocumentId document) {
        return run(() -> new WindowPicture(document, circuit(document).version(), "VSim — " + document.value(),
                List.of(), 1200, 800, PNG));
    }

    @Override
    public CompletableFuture<SimulationReceipt> simulate(SimulationRequest request) {
        simulationRequests.add(request);
        return run(() -> {
            circuit(request.document());
            return simulation.apply(request);
        });
    }

    @Override
    public CompletableFuture<PreparedTruthTable> prepareTruthTable(TruthTableRequest request) {
        return run(() -> {
            Circuit circuit = circuit(request.document());
            Set<ElementId> scope = request.elementIds().stream().map(ElementId::new).collect(Collectors.toSet());
            return new PreparedTruthTable(circuit.version(),
                    TruthTableScope.prepare(circuit.document, scope, circuit.registry));
        });
    }

    @Override
    public PresentationSettings presentationSettings(DocumentId document) {
        circuit(document);
        return presentations.getOrDefault(document, PresentationSettings.WORKING);
    }

    @Override
    public void setPresenting(DocumentId document, boolean presenting) {
        PresentationSettings current = presentationSettings(document);
        presentations.put(document, new PresentationSettings(presenting, current.pace(), current.pause()));
    }

    @Override
    public void layerPresentation(DocumentId document, Optional<PresentationPace> pace, Optional<Duration> pause) {
        PresentationSettings current = presentationSettings(document);
        PresentationSettings layered = new PresentationSettings(current.presenting(), pace.orElse(current.pace()),
                pause.orElse(current.pause()));
        presentations.put(document, layered);
        layeredPresentations.add(document.value() + " " + layered.pace() + " " + layered.pause().toMillis());
    }

    /** What the AI layered for its task, as «circuit-1 CONTINUOUS 2000». */
    List<String> layeredPresentations() {
        return layeredPresentations;
    }

    /** Puts {@code document} in «Presentar» at {@code pace}, as the user's toggle would. */
    void present(String document, PresentationPace pace) {
        presentations.put(new DocumentId(document),
                new PresentationSettings(true, pace, PresentationSettings.DEFAULT_PAUSE));
    }

    @Override
    public DocumentId newCircuit(String name) {
        Circuit circuit = open("circuit-" + (circuits.size() + 1));
        circuit.document.setName(name);
        return circuit.id;
    }

    @Override
    public void setEditorSetting(DocumentId document, EditorSetting setting, String value) {
        circuit(document);
        if (!setting.allowedValues().contains(value)) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "not allowed",
                    Map.of("allowedValues", setting.allowedValues()));
        }
        settings.add(document.value() + " " + setting + "=" + value);
    }

    @Override
    public String proposePreference(ProposedPreference preference, String value, String reason) {
        settings.add("propose " + preference + "=" + value);
        return "proposal-1";
    }

    @Override
    public CompletableFuture<ProposalAnswer> proposalAnswer(String proposalId) {
        if (!proposalId.equals("proposal-1")) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "no such proposal");
        }
        return answer;
    }

    @Override
    public CompletableFuture<ExportReceipt> export(ExportRequest request) {
        exports.add(request);
        return run(() -> {
            Circuit circuit = circuit(request.document());
            String name = request.fileName().orElse("circuito") + "." + request.format().extension();
            return new ExportReceipt(request.document(), circuit.version(), request.format(), name, "/home/VSim",
                    "/home/VSim/" + name, 0, 0, 1);
        });
    }

    /** The receipt the simulation gives by default for {@code document}. */
    SimulationReceipt receipt(DocumentId document, SimulationReceipt.Outcome outcome) {
        DocumentVersion version = circuit(document).version();
        return new SimulationReceipt(document, version, version, 1, true, outcome, "0", "40", Optional.empty(),
                outcome == SimulationReceipt.Outcome.BUDGET_EXCEEDED ? 100 : 12, 100_000, false, Optional.empty(),
                List.of(), List.of());
    }

    private Circuit circuit(DocumentId document) {
        Circuit circuit = circuits.get(document);
        if (circuit == null) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "No circuit " + document.value());
        }
        return circuit;
    }

    /** One open circuit: its version grows with every change of its history, like {@code OpenDocument}. */
    final class Circuit implements EditableCircuit {

        final DocumentId id;
        final CircuitDocument document;
        final ComponentTypeRegistry registry;
        final History history;
        private long changeCount;

        Circuit(DocumentId id, CircuitDocument document) {
            this.id = id;
            this.document = document;
            this.registry = BASE_REGISTRY.copy();
            this.registry.addResolver(SubcircuitTypes.resolverFor(document));
            this.history = new History(document);
            history.addListener(changed -> changeCount++);
        }

        @Override
        public CircuitDocument document() {
            return document;
        }

        @Override
        public ComponentTypeRegistry registry() {
            return registry;
        }

        @Override
        public History history() {
            return history;
        }

        @Override
        public DocumentVersion version() {
            return new DocumentVersion(changeCount);
        }

        @Override
        public List<DefinitionBundle> library() {
            return library;
        }
    }
}
