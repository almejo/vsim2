package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.simulate.InputResult;
import cl.almejo.vsim.automation.simulate.InputValue;
import cl.almejo.vsim.automation.simulate.SignalValue;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.SimulationRequest;
import cl.almejo.vsim.automation.simulate.StepKind;
import cl.almejo.vsim.automation.simulate.TruthTableView;
import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's simulation through the real editor host and simulation threads (IA.1 Task 4, Review Focus 3). */
class SimulationHostSupportTest {

    private static final String ADDER_CARRY_LED = "c79";
    private static final String COUNTER_LEDS_FROM_Q0 = "c12,c28,c43,c58";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<OpenDocument> open = new ArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open));
    private final AutomationApi api = new AutomationApi(host);

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    private OpenDocument example(String id) throws Exception {
        OpenDocument document = new OpenDocument(BundledExamples.open(id, registry), registry, null, false);
        open.add(document);
        return document;
    }

    private DocumentSummary summary() {
        return api.listDocuments().get(0);
    }

    private static void assertError(Runnable call, AutomationError error) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AutomationException.class,
                exception -> assertThat(exception.error()).isEqualTo(error));
    }

    private String read(DocumentId document, String elementId, String pinName) {
        return api.readSignals(document, List.of(new PinReference(elementId, pinName, 0))).signals().get(0).bits();
    }

    @Test
    void inputsSetInOrderThenStepsThenTheCarryIsRead() throws Exception {
        example("adder-4-bits");
        DocumentSummary adder = summary();
        assertThat(api.pause(adder.id()).running()).isFalse();

        SimulationReceipt set = api.setInputs(adder.id(), adder.version(), "op-1",
                List.of(new InputValue("c58", "1"), new InputValue("c59", "0b1")));
        SimulationReceipt settled = api.step(adder.id(), StepKind.NEXT_EVENT, 1_000, 0);

        assertThat(set.inputs()).extracting(InputResult::accepted).containsExactly(true, true);
        assertThat(set.version()).isEqualTo(adder.version());
        assertThat(set.stale()).isFalse();
        assertThat(set.ranOnThatCircuit()).isTrue();
        assertThat(settled.outcome()).isEqualTo(SimulationReceipt.Outcome.COMPLETED);
        assertThat(settled.nextEventTime()).as("the adder settled").isEmpty();
        assertThat(read(adder.id(), ADDER_CARRY_LED, "in")).isEqualTo("1");
        // The switches keep the values set (2026-10-28): that edit recompiles the circuit; the step runs on the new one.
        assertThat(settled.netlistToken()).isGreaterThan(set.netlistToken());
        assertThat(settled.ranOnThatCircuit()).isTrue();
    }

    /** An 8-bit Switch wired to an 8-bit LED; returns the ids (switch, LED) in an open document. */
    private List<String> eightBitSwitchToLed() throws Exception {
        OpenDocument document = new OpenDocument(new cl.almejo.vsim.core.document.CircuitDocument(), registry, null,
                false);
        open.add(document);
        Map<String, Object> eightBits = Map.of(StandardComponentTypes.WIDTH, 8);
        cl.almejo.vsim.core.document.Component input = place(document, StandardComponentTypes.SWITCH, 0, 0, eightBits);
        cl.almejo.vsim.core.document.Component output = place(document, StandardComponentTypes.LED, 20, 0, eightBits);
        cl.almejo.vsim.core.geometry.GridPoint from = registry.typeOf(input).pinLocation(input, 0);
        cl.almejo.vsim.core.geometry.GridPoint to = registry.typeOf(output).pinLocation(output, 0);
        addWire(document, from.x(), from.y(), to.x(), to.y());
        return List.of(input.id().value(), output.id().value());
    }

    @Test
    void anEightBitSwitchTakesHexBinaryAndDecimalAndRefusesWhatDoesNotFit() throws Exception {
        List<String> parts = eightBitSwitchToLed();
        DocumentSummary summary = summary();
        api.pause(summary.id());

        for (String written : List.of("0xA6", "0b1010_0110", "166")) {
            api.setInputs(summary.id(), summary.version(), null, List.of(new InputValue(parts.get(0), "0")));
            api.step(summary.id(), StepKind.NEXT_EVENT, 1_000, 0);
            api.setInputs(summary.id(), summary.version(), null, List.of(new InputValue(parts.get(0), written)));
            api.step(summary.id(), StepKind.NEXT_EVENT, 1_000, 0);
            SignalValue led = api.readSignals(summary.id(), List.of(new PinReference(parts.get(1), "in", 0)))
                    .signals().get(0);
            assertThat(led.bits()).as(written).isEqualTo("10100110");
            assertThat(led.hex()).as(written).containsIgnoringCase("A6");
            assertThat(led.width()).isEqualTo(8);
        }
        assertThatThrownBy(() -> api.setInputs(summary.id(), summary.version(), null,
                List.of(new InputValue(parts.get(0), "0x1FF"))))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.INPUT_REJECTED);
                    assertThat(exception.recovery().get("inputs").toString()).contains("does not fit in 8 bits");
                });
    }

    /** Decided 2026-10-28: a switch the AI sets keeps that «Valor», as a click does, in one undo step of its own. */
    @Test
    void aSwitchTheAiSetsKeepsTheValueAsOneUndoStepWithoutMakingItsVersionStale() throws Exception {
        List<String> parts = eightBitSwitchToLed();
        OpenDocument document = open.get(open.size() - 1);
        DocumentSummary summary = summary();
        cl.almejo.vsim.core.document.ElementId switchId = new cl.almejo.vsim.core.document.ElementId(parts.get(0));

        SimulationReceipt set = api.setInputs(summary.id(), summary.version(), null,
                List.of(new InputValue(parts.get(0), "0xA6")));

        assertThat(set.stale()).isFalse();
        assertThat(summary().version()).as("an input's value, like a click").isEqualTo(summary.version());
        assertThat(document.document().component(switchId).orElseThrow().parameters()
                .get(StandardComponentTypes.VALUE)).isEqualTo("166");
        assertThat(document.history().undoDescription()).contains(SimulationHostSupport.AI_SWITCH_VALUES);
        api.setInputs(summary.id(), summary.version(), null, List.of(new InputValue(parts.get(0), "0x0F")));
        document.history().undo();
        assertThat(document.document().component(switchId).orElseThrow().parameters()
                .get(StandardComponentTypes.VALUE)).isEqualTo("166");
    }

    @Test
    void anInputPreparedAgainstAnOlderVersionIsStaleAndSetsNothing() throws Exception {
        OpenDocument document = example("adder-4-bits");
        DocumentSummary adder = summary();
        api.pause(adder.id());
        SwingUtilities.invokeAndWait(() -> place(document, StandardComponentTypes.BUFFER, 60, 60, Map.of(StandardComponentTypes.NEGATE, true)));

        assertThatThrownBy(() -> api.setInputs(adder.id(), adder.version(), "op-1",
                List.of(new InputValue("c58", "1"), new InputValue("c59", "1"))))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.STALE_DOCUMENT);
                    assertThat(exception.recovery()).containsEntry("currentVersion", summary().version().value());
                });
        api.step(adder.id(), StepKind.NEXT_EVENT, 1_000, 0);
        assertThat(read(adder.id(), ADDER_CARRY_LED, "in")).isEqualTo("0");
    }

    @Test
    void aRejectedInputIsReportedAndNothingIsSet() throws Exception {
        example("adder-4-bits");
        DocumentSummary adder = summary();
        api.pause(adder.id());

        assertThatThrownBy(() -> api.setInputs(adder.id(), adder.version(), null, List.of(
                new InputValue("c6", "1"), new InputValue(ADDER_CARRY_LED, "1"), new InputValue("c4", "2"),
                new InputValue("ghost", "1"))))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.INPUT_REJECTED);
                    assertThat((List<?>) exception.recovery().get("inputs")).hasSize(3);
                    assertThat(exception.recovery().get("inputs").toString())
                            .contains("is not a switch or an input pin", "does not fit in 1 bit", "no component ghost");
                });
        assertThat(read(adder.id(), "c6", "out")).as("the valid value was not set either").isEqualTo("0");
    }

    @Test
    void steppingWhileTheUserRunsTheSimulationIsBusy() throws Exception {
        example("counter-4-bits");
        DocumentSummary counter = summary();

        assertError(() -> api.step(counter.id(), StepKind.NEXT_CLOCK_EDGE, 1, 0), AutomationError.SIMULATION_BUSY);
        assertThat(api.readSignals(counter.id(), List.of(new PinReference("c1", "out", 0))).running())
                .as("VSim does not pause what the user started").isTrue();
    }

    /** The counter's value on its LEDs Q3..Q0. */
    private int counterValue(DocumentId counter) {
        List<PinReference> leds = new ArrayList<>();
        for (String led : COUNTER_LEDS_FROM_Q0.split(",")) {
            leds.add(new PinReference(led, "in", 0));
        }
        List<SignalValue> values = api.readSignals(counter, leds).signals();
        int value = 0;
        for (int bit = 0; bit < values.size(); bit++) {
            value |= (values.get(bit).bits().equals("1") ? 1 : 0) << bit;
        }
        return value;
    }

    @Test
    void eachClockEdgeOfTheCounterCountsOne() throws Exception {
        example("counter-4-bits");
        DocumentId counter = summary().id();
        api.pause(counter);
        api.restart(counter);

        SimulationReceipt edge = api.step(counter, StepKind.NEXT_CLOCK_EDGE, 1, 0);
        api.step(counter, StepKind.DURATION, 100, 0);
        int first = counterValue(counter);
        api.step(counter, StepKind.NEXT_CLOCK_EDGE, 1, 0);
        api.step(counter, StepKind.DURATION, 100, 0);
        int second = counterValue(counter);
        SimulationReceipt twoEdges = api.step(counter, StepKind.NEXT_CLOCK_EDGE, 2, 0);
        api.step(counter, StepKind.DURATION, 100, 0);

        assertThat(edge.outcome()).isEqualTo(SimulationReceipt.Outcome.COMPLETED);
        assertThat(edge.fromTime()).isEqualTo("0");
        assertThat(Long.parseLong(edge.time())).isPositive();
        assertThat(second).isEqualTo((first + 1) % 16);
        assertThat(counterValue(counter)).isEqualTo((second + 2) % 16);
        assertThat(Long.parseLong(twoEdges.time()) - Long.parseLong(twoEdges.fromTime())).as("two periods, nearly")
                .isGreaterThan(500);
    }

    @Test
    void aFreeRunningClockStopsAtTheBudgetAndTheUserCanStillRunAndPause() throws Exception {
        example("counter-4-bits");
        DocumentId counter = summary().id();
        api.pause(counter);

        SimulationReceipt receipt = api.step(counter, StepKind.DURATION, 1_000_000_000L, 1_000);

        assertThat(receipt.budgetExceeded()).isTrue();
        assertThat(receipt.eventsProcessed()).isEqualTo(1_000);
        assertThat(Long.parseLong(receipt.time())).isLessThan(1_000_000_000L);
        assertThat(receipt.running()).isFalse();
        assertThat(api.resume(counter).running()).isTrue();
        assertThat(api.pause(counter).running()).isFalse();
    }

    @Test
    void aReceiptOfACircuitChangedMeanwhileSaysWhichVersionItDescribes() throws Exception {
        OpenDocument document = example("adder-4-bits");
        DocumentSummary adder = summary();
        AtomicReference<CompletableFuture<SimulationReceipt>> pending = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            pending.set(host.simulate(new SimulationRequest.Pause(adder.id())));
            // queued behind the request's own EDT step, so it lands before the receipt is written
            SwingUtilities.invokeLater(() -> place(document, StandardComponentTypes.BUFFER, 60, 60, Map.of(StandardComponentTypes.NEGATE, true)));
        });
        SimulationReceipt receipt = pending.get().get(10, TimeUnit.SECONDS);

        assertThat(receipt.version()).isEqualTo(adder.version());
        assertThat(receipt.currentVersion()).isEqualTo(summary().version());
        assertThat(receipt.stale()).isTrue();
    }

    @Test
    void theEditorThreadNeverWaitsForTheSimulationAndAClosedOneAnswersDocumentClosed() throws Exception {
        OpenDocument document = example("adder-4-bits");
        DocumentId adder = summary().id();
        document.close(); // its simulation thread will never run the request

        CompletableFuture<SimulationReceipt> receipt = host.simulate(new SimulationRequest.Pause(adder));
        long start = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(2_000);
        assertThat(receipt).failsWithin(5, TimeUnit.SECONDS)
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .havingCause().isInstanceOfSatisfying(cl.almejo.vsim.automation.AutomationException.class,
                        refusal -> assertThat(refusal.error())
                                .isEqualTo(cl.almejo.vsim.automation.AutomationError.DOCUMENT_CLOSED));
    }

    @Test
    void theCarryOfTheFourBitAdderInEveryRowOfItsTable() throws Exception {
        example("adder-4-bits");
        DocumentSummary adder = summary();

        TruthTableView table = api.truthTable(adder.id(), Set.of(), 0, 1_000_000);

        assertThat(table.budgetExceeded()).isFalse();
        assertThat(table.totalRows()).isEqualTo(512);
        assertThat(table.rows()).hasSize(512);
        assertThat(table.version()).isEqualTo(adder.version());
        List<String> names = table.inputs().stream().map(TruthTableView.ColumnView::name).toList();
        for (TruthTableView.RowView row : table.rows()) {
            int first = 0;
            int second = 0;
            int carryIn = 0;
            for (int column = 0; column < names.size(); column++) {
                String name = names.get(column);
                int bit = row.inputs().get(column).equals("1") ? 1 : 0;
                if (name.equals("Cin")) {
                    carryIn = bit;
                } else if (name.startsWith("A")) {
                    first |= bit << Integer.parseInt(name.substring(1));
                } else {
                    second |= bit << Integer.parseInt(name.substring(1));
                }
            }
            assertThat(row.outputs()).as("%d + %d + %d", first, second, carryIn)
                    .containsExactly(first + second + carryIn >= 16 ? "1" : "0");
        }
    }

    @Test
    void aCounterHasNoTruthTable() throws Exception {
        example("counter-4-bits");

        assertError(() -> api.truthTable(summary().id(), Set.of(), 0, 0), AutomationError.NOT_ALLOWED);
    }

    @Test
    void unknownCircuitsElementsAndPinsAreRefused() throws Exception {
        example("adder-4-bits");
        DocumentId adder = summary().id();

        assertError(() -> api.pause(new DocumentId("circuit-99")), AutomationError.INVALID_REFERENCE);
        assertError(() -> api.readSignals(adder, List.of(new PinReference("ghost", "in", 0))),
                AutomationError.INVALID_REFERENCE);
        assertThatThrownBy(() -> api.readSignals(adder, List.of(new PinReference(ADDER_CARRY_LED, "out", 0))))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.INVALID_PIN);
                    assertThat(exception.recovery()).containsEntry("pins", List.of("in"));
                });
        assertError(() -> api.truthTable(adder, Set.of("ghost"), 0, 0), AutomationError.INVALID_REFERENCE);
    }

    @Test
    void aRestartGoesBackToTimeZero() throws Exception {
        example("counter-4-bits");
        DocumentId counter = summary().id();
        api.pause(counter);
        api.step(counter, StepKind.DURATION, 700, 0);

        SimulationReceipt receipt = api.restart(counter);

        assertThat(receipt.time()).isEqualTo("0");
        assertThat(receipt.running()).isFalse();
        assertThat(receipt.outcome()).isEqualTo(SimulationReceipt.Outcome.COMPLETED);
    }

    @Test
    void aPinJoinedToNothingIsReadOnItsOwnNet() throws Exception {
        OpenDocument document = example("adder-4-bits");
        DocumentSummary before = summary();
        AtomicReference<String> gate = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> gate.set(place(document, StandardComponentTypes.BUFFER, 80, 80, Map.of(StandardComponentTypes.NEGATE, true))
                .id().value()));
        api.pause(before.id());
        api.step(before.id(), StepKind.NEXT_EVENT, 100, 0);

        assertThat(read(before.id(), gate.get(), "out")).as("NOT of a floating input").isNotEmpty();
        assertThat(api.readSignals(before.id(), List.of(new PinReference(gate.get(), "in", 0))).signals().get(0)
                .highImpedanceMask()).isEqualTo("1");
        assertThat(new DocumentVersion(before.version().value() + 1)).isEqualTo(summary().version());
    }
}
