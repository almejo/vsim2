package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.edit.InnerWires;
import cl.almejo.vsim.automation.simulate.InputValue;
import cl.almejo.vsim.automation.simulate.PreparedTruthTable;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.SimulationRequest;
import cl.almejo.vsim.automation.simulate.TruthTableRequest;
import cl.almejo.vsim.automation.simulate.ValueText;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.ClockEdgeMode;
import cl.almejo.vsim.core.simulation.InputAssignment;
import cl.almejo.vsim.core.simulation.RequestReport;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * The editor's side of the AI's simulation (IA.1 Task 4): checks a request against the open circuit on the EDT, hands
 * it to the circuit's simulation thread with its budget and writes the receipt back on the EDT once the simulation
 * answered. The EDT never waits: each hop is a callback.
 */
final class SimulationHostSupport {

    private static final Set<String> INPUT_TYPES = Set.of(StandardComponentTypes.SWITCH,
            StandardComponentTypes.PUSH_BUTTON, StandardComponentTypes.INPUT_PIN);

    /** A request handed to the simulation thread, with what the receipt needs to be written. */
    private record Started(CompletableFuture<RequestReport> report, int eventBudget, List<InputValue> asked,
                           List<SimulationReceipt.ObservedPin> observed) {
    }

    private final Function<DocumentId, Optional<OpenDocument>> documents;
    private final Executor editorThread;
    private final EditorQueue queue;

    /**
     * @param documents    the open circuit with that id, empty when there is none; called on the EDT
     * @param editorThread runs a task on the EDT later ({@code SwingUtilities::invokeLater})
     */
    SimulationHostSupport(Function<DocumentId, Optional<OpenDocument>> documents, Executor editorThread) {
        this(documents, editorThread, new EditorQueue());
    }

    /** @param queue refuses the jobs still waiting for the EDT once the AI is turned off */
    SimulationHostSupport(Function<DocumentId, Optional<OpenDocument>> documents, Executor editorThread,
                          EditorQueue queue) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /** A request the closed circuit's simulation will never run is DOCUMENT_CLOSED (review M5). */
    private static Throwable closedOr(Throwable failure, DocumentId document) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof cl.almejo.vsim.core.simulation.SimulationThread.ClosedException) {
            return new AutomationException(AutomationError.DOCUMENT_CLOSED, "The circuit " + document.value()
                    + " was closed before its simulation ran the request; list the documents again",
                    java.util.Map.of("document", document.value()));
        }
        return failure;
    }

    CompletableFuture<SimulationReceipt> simulate(SimulationRequest request) {
        return queue.submitAsync(editorThread, result -> {
            OpenDocument document = document(request.document());
            DocumentVersion version = versionOf(document);
            long netlistToken = document.netlistToken();
            Started started = start(document, version, request);
            started.report().whenComplete((report, failure) -> editorThread.execute(() -> {
                if (failure != null) {
                    result.completeExceptionally(closedOr(failure, request.document()));
                    return;
                }
                try {
                    result.complete(SimulationReceipt.of(request.document(), version, versionOf(document),
                            netlistToken, started.eventBudget(), report, started.asked(), started.observed()));
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            }));
        });
    }

    /** The words of memory {@code component} in the simulation now, asked of its thread; the EDT never waits. */
    CompletableFuture<Optional<MemoryContents>> currentMemory(DocumentId document, String component) {
        return queue.submitAsync(editorThread, result -> document(document).requestMemoryContents(new ElementId(component))
                .whenComplete((contents, failure) -> {
                    if (failure != null) {
                        result.completeExceptionally(closedOr(failure, document));
                    } else {
                        result.complete(contents);
                    }
                }));
    }

    CompletableFuture<PreparedTruthTable> prepareTruthTable(TruthTableRequest request) {
        return queue.submit(editorThread, () -> {
            OpenDocument document = document(request.document());
            Set<ElementId> scope = new HashSet<>();
            for (String elementId : request.elementIds()) {
                ElementId id = elementId(elementId);
                if (document.document().element(id).isEmpty()) {
                    throw unknownElement(elementId);
                }
                scope.add(id);
            }
            // Parts named by the AI take the wires between them along (InnerWires, Task 7).
            Set<ElementId> withWires = scope.isEmpty() ? scope
                    : InnerWires.of(document.document(), document.registry(), scope).elements();
            return new PreparedTruthTable(versionOf(document),
                    TruthTableScope.prepare(document.document(), withWires, document.registry()));
        });
    }

    private Started start(OpenDocument document, DocumentVersion version, SimulationRequest request) {
        return switch (request) {
            case SimulationRequest.Pause pause -> {
                document.pause();
                yield new Started(document.simulationReport(), 0, List.of(), List.of());
            }
            case SimulationRequest.Resume resume -> {
                document.resume();
                yield new Started(document.simulationReport(), 0, List.of(), List.of());
            }
            case SimulationRequest.Restart restart ->
                    new Started(document.restartWithin(restart.eventBudget()), restart.eventBudget(), List.of(),
                            List.of());
            case SimulationRequest.SetInputs setInputs -> {
                if (!setInputs.version().equals(version)) {
                    throw new AutomationException(AutomationError.STALE_DOCUMENT, "The circuit changed since version "
                            + setInputs.version().value() + "; read it again",
                            Map.of("currentVersion", version.value()));
                }
                // simulation only, like a truth-table row tried in the circuit: «Valor» and the file stay as they are
                yield new Started(document.setInputsWithin(assignments(document, setInputs.values()),
                        setInputs.eventBudget()), setInputs.eventBudget(), setInputs.values(), List.of());
            }
            case SimulationRequest.Step step -> new Started(switch (step.kind()) {
                case NEXT_EVENT -> document.advanceToNextEventWithin((int) step.amount(), step.eventBudget());
                case NEXT_CLOCK_EDGE -> document.advanceToNextClockEdgeWithin(ClockEdgeMode.ANY, (int) step.amount(),
                        step.eventBudget());
                case NEXT_CLOCK_CYCLE -> document.advanceToNextClockEdgeWithin(ClockEdgeMode.FALLING,
                        (int) step.amount(), step.eventBudget());
                case DURATION -> document.stepWithin(step.amount(), step.eventBudget());
            }, step.eventBudget(), List.of(), List.of());
            case SimulationRequest.ReadSignals readSignals -> {
                List<SimulationReceipt.ObservedPin> observed = new ArrayList<>();
                for (PinReference pin : readSignals.pins()) {
                    observed.add(new SimulationReceipt.ObservedPin(pin, netOf(document, pin)));
                }
                yield new Started(document.simulationReport(), 0, List.of(), observed);
            }
        };
    }

    /**
     * The values as the simulation thread takes them, checked here first: every element must be a switch or an input
     * pin of the circuit's top level and every value must fit its width, or nothing is set (INPUT_REJECTED).
     */
    private static List<InputAssignment> assignments(OpenDocument document, List<InputValue> values) {
        List<InputAssignment> assignments = new ArrayList<>();
        List<Map<String, Object>> problems = new ArrayList<>();
        for (InputValue value : values) {
            Optional<Component> component = component(document, value.elementId());
            if (component.isEmpty()) {
                problems.add(problem(value, "no component " + value.elementId()));
                continue;
            }
            if (!INPUT_TYPES.contains(component.get().typeId())) {
                problems.add(problem(value, value.elementId() + " (" + component.get().typeId()
                        + ") is not a switch, a push button or an input pin"));
                continue;
            }
            int width = typeOf(document, component.get()).pin(0).width();
            try {
                assignments.add(new InputAssignment(value.elementId(), ValueText.parse(value.value(), width)));
            } catch (IllegalArgumentException exception) {
                problems.add(problem(value, exception.getMessage()));
            }
        }
        if (!problems.isEmpty()) {
            throw new AutomationException(AutomationError.INPUT_REJECTED, "No input was set: " + problems.size()
                    + (problems.size() == 1 ? " value was" : " values were") + " refused", Map.of("inputs", problems));
        }
        return assignments;
    }

    private static Map<String, Object> problem(InputValue value, String reason) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("elementId", value.elementId());
        problem.put("value", value.value());
        problem.put("reason", reason);
        return problem;
    }

    /** The net {@code pin} is on: its top-level net, or the private net of a pin joined to nothing. */
    private static String netOf(OpenDocument document, PinReference pin) {
        Component component = component(document, pin.elementId()).orElseThrow(() -> unknownElement(pin.elementId()));
        ComponentType type = typeOf(document, component);
        List<String> names = new ArrayList<>();
        int seen = 0;
        int pinIndex = -1;
        for (int index = 0; index < type.pinCount(); index++) {
            PinSpec spec = type.pin(index);
            names.add(spec.name());
            if (spec.name().equals(pin.pinName()) && seen++ == pin.index()) {
                pinIndex = index;
            }
        }
        if (pinIndex < 0) {
            throw new AutomationException(AutomationError.INVALID_PIN, component.typeId() + " " + pin.elementId()
                    + " has no pin " + pin.pinName() + (pin.index() == 0 ? "" : " number " + pin.index()),
                    Map.of("elementId", pin.elementId(), "pins", names));
        }
        int found = pinIndex;
        return document.netlist().netForPin(new PinRef(component.id(), pinIndex))
                .map(net -> net.id().value())
                .orElseGet(() -> "p:" + component.id().value() + "#" + found);
    }

    private static ComponentType typeOf(OpenDocument document, Component component) {
        return document.registry().typeOf(component);
    }

    private static Optional<Component> component(OpenDocument document, String elementId) {
        return document.document().component(elementId(elementId));
    }

    private static ElementId elementId(String value) {
        try {
            return new ElementId(value);
        } catch (IllegalArgumentException exception) {
            throw unknownElement(value);
        }
    }

    private static AutomationException unknownElement(String elementId) {
        return new AutomationException(AutomationError.INVALID_REFERENCE, "There is no component " + elementId
                + " in this circuit; read it again", Map.of("elementId", elementId));
    }

    private OpenDocument document(DocumentId id) {
        return documents.apply(id).orElseThrow(() -> new AutomationException(AutomationError.DOCUMENT_CLOSED,
                "Circuit " + id.value() + " is not open; list the documents again", Map.of("document", id.value())));
    }

    private static DocumentVersion versionOf(OpenDocument document) {
        return new DocumentVersion(document.contentVersion());
    }
}
