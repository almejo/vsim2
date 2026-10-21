package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.simulate.InputValue;
import cl.almejo.vsim.automation.simulate.SimulationOperations;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.StepKind;
import cl.almejo.vsim.automation.simulate.TruthTableView;

import java.util.List;
import java.util.Map;

/**
 * Simulating with budgets and receipts (A§7): pause / resume / restart, explicit inputs, steps, signals and truth
 * tables. A run whose event budget ran out is answered as the error {@code BUDGET_EXCEEDED}, with the receipt (or
 * the rows evaluated) in its recovery: the simulation stopped at the time reached and paused, nothing rolled back.
 */
final class SimulationTools {

    static final String SIMULATION = "vsim_simulation";
    static final String SET_INPUTS = "vsim_set_inputs";
    static final String STEP = "vsim_step";
    static final String READ_SIGNALS = "vsim_read_signals";
    static final String TRUTH_TABLE = "vsim_truth_table";

    private SimulationTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(simulation(api), setInputs(api), step(api), readSignals(api), truthTable(api));
    }

    private static Map<String, Object> eventBudget() {
        return Schema.integer("The most events this call may run (default " + SimulationOperations.DEFAULT_EVENT_BUDGET
                + ", at most " + SimulationOperations.MAXIMUM_EVENT_BUDGET + "; 0 is the default)", 0,
                SimulationOperations.MAXIMUM_EVENT_BUDGET);
    }

    /** The receipt, or BUDGET_EXCEEDED with it when the budget ran out. */
    static Tool.Output receipt(SimulationReceipt receipt) {
        if (receipt.budgetExceeded()) {
            throw new AutomationException(AutomationError.BUDGET_EXCEEDED, "The event budget of "
                    + receipt.eventBudget() + " events ran out at time " + receipt.time() + "; the simulation "
                    + "stopped there and paused (nothing was rolled back). A circuit that never settles (an "
                    + "oscillator, a free-running clock) needs a step of a given duration instead",
                    Map.of("receipt", Json.object(receipt)));
        }
        return Tool.Output.of(receipt);
    }

    private static Tool simulation(AutomationApi api) {
        return new Tool(SIMULATION, "Pause, resume or restart the simulation",
                "pause and resume as «Pausar» / «Iniciar» (resume leaves «Paso a paso»); restart as «Reiniciar»: "
                        + "time 0, switches where they are. VSim never pauses or resumes by itself what the user "
                        + "started: steps need a paused simulation.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("action", Schema.enumeration("What to do", List.of("pause", "resume", "restart")))
                        .required("document", "action").build(),
                Common.simulationReceipt(), Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "action", "pause")),
                arguments -> receipt(switch (arguments.string("action")) {
                    case "pause" -> api.pause(arguments.document());
                    case "resume" -> api.resume(arguments.document());
                    case "restart" -> api.restart(arguments.document());
                    default -> throw arguments.invalid("action", "is pause, resume or restart");
                }));
    }

    private static Tool setInputs(AutomationApi api) {
        Map<String, Object> value = Schema.object("One input and its value")
                .property("elementId", Schema.nonEmptyString("A switch or input pin of the circuit's top level"))
                .property("value", Schema.nonEmptyString("0b1010 binary, 0x3F hexadecimal or a decimal number "
                        + "(1010 is a thousand and ten: write binary as 0b1010)"))
                .required("elementId", "value").build();
        return new Tool(SET_INPUTS, "Set the inputs",
                "Sets switches and input pins to explicit values, one after the other in the order given (each with "
                        + "its zero-delay effects before the next), if the circuit is still at version. All the "
                        + "values of one call change at the same simulation time: the gates react after the call. To "
                        + "give the circuit time between values (a latch, a counter driven by hand), pause, set, "
                        + "vsim_step by a DURATION, then set the next values in another call. All are "
                        + "checked first: one that cannot be set refuses the whole call (INPUT_REJECTED, nothing "
                        + "set). The receipt says what each one did. Idempotent by operationId.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("version", Common.version())
                        .property("operationId", Schema.nonEmptyString("Your own key: a retry with the same key and "
                                + "values gets the same receipt without setting anything again"))
                        .property("values", Schema.array("The values, in order", value, 1,
                                SimulationOperations.MAXIMUM_INPUTS))
                        .required("document", "version", "operationId", "values").build(),
                Common.simulationReceipt(), Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "version", 5, "operationId", "inputs-1", "values",
                        List.of(Map.of("elementId", "c1", "value", "1"), Map.of("elementId", "c2", "value", "0b0")))),
                arguments -> receipt(api.setInputs(arguments.document(), arguments.version("version"),
                        arguments.string("operationId"), arguments.objects("values").stream()
                                .map(input -> new InputValue(input.string("elementId"), input.string("value")))
                                .toList())));
    }

    private static Tool step(AutomationApi api) {
        return new Tool(STEP, "Step the simulation",
                "Advances a paused simulation within an event budget: NEXT_EVENT (amount = how many «Avanzar»), "
                        + "NEXT_CLOCK_EDGE (amount = how many clock edges) or DURATION (amount = simulation time; one "
                        + "gate delay is 1). A running simulation is SIMULATION_BUSY: pause it first.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("kind", Schema.enumeration("How far", List.of("NEXT_EVENT", "NEXT_CLOCK_EDGE",
                                "DURATION")))
                        .property("amount", Schema.integer("A count (1 to " + SimulationOperations.MAXIMUM_STEP_COUNT
                                + ") or a duration (1 to " + SimulationOperations.MAXIMUM_DURATION + "); 1 by "
                                + "default", 1, SimulationOperations.MAXIMUM_DURATION))
                        .property("eventBudget", eventBudget())
                        .required("document", "kind").build(),
                Common.simulationReceipt(), Tool.Hints.CHANGES,
                List.of(Map.of("document", "circuit-1", "kind", "NEXT_CLOCK_EDGE", "amount", 1),
                        Map.of("document", "circuit-1", "kind", "DURATION", "amount", 50, "eventBudget", 10000)),
                arguments -> receipt(api.step(arguments.document(), arguments.enumeration("kind", StepKind.class),
                        arguments.longValue("amount", 1), arguments.integer("eventBudget", 0))));
    }

    private static Tool readSignals(AutomationApi api) {
        return new Tool(READ_SIGNALS, "Read signal values",
                "The values on some pins' nets at one instant, once every earlier request ran: width, bits most "
                        + "significant first (0, 1, Z), hex, the Z mask and conflict (two drivers disagree). Times "
                        + "are decimal strings.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("pins", Schema.array("The pins to read", Common.pinReference("A pin"), 1,
                                SimulationOperations.MAXIMUM_PINS))
                        .required("document", "pins").build(),
                Common.versionedResult("The values")
                        .property("time", Schema.string("Simulation time read"))
                        .property("running", Schema.bool("The simulation goes on after this instant"))
                        .property("stale", Schema.bool("The circuit changed before the answer"))
                        .property("signals", Schema.array("One per pin asked: pin, width, bits, hex, "
                                + "highImpedanceMask, conflict", Schema.result("").build()))
                        .required("time", "signals").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1", "pins",
                        List.of(Map.of("elementId", "c7", "pinName", "out")))),
                arguments -> Tool.Output.of(api.readSignals(arguments.document(),
                        arguments.objects("pins").stream().map(Arguments::pin).toList())));
    }

    private static Tool truthTable(AutomationApi api) {
        return new Tool(TRUTH_TABLE, "Make a truth table",
                "The truth table of the circuit (no elementIds), of some of its elements, or of one user-component "
                        + "instance, as Herramientas ▸ Tabla de verdad makes it: on a copy, never touching the live "
                        + "simulation; combinational circuits only, at most 16 input bits. Rows in binary counting "
                        + "order; each cell is the bits (X! in front for a conflict). truncated: the row limit cut "
                        + "it. The event budget is shared among the rows: a row that never settles (an oscillation, "
                        + "a latch's hold row on the fresh copy) gets cutShort when its share runs out and the rows "
                        + "after it are still made; rows the budget never reached are BUDGET_EXCEEDED with the rows "
                        + "done. To check a stage against expected rows, use vsim_check_stage.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("elementIds", Schema.array("The parts to tabulate; the wires that join only these "
                                + "parts come along (empty: the whole circuit)",
                                Schema.string("")))
                        .property("maximumRows", Schema.integer("Rows to evaluate (default "
                                + SimulationOperations.DEFAULT_MAXIMUM_ROWS + ", at most "
                                + SimulationOperations.MAXIMUM_ROWS + "; 0 is the default)", 0,
                                SimulationOperations.MAXIMUM_ROWS))
                        .property("eventBudget", eventBudget())
                        .required("document").build(),
                Common.versionedResult("The table")
                        .property("inputs", Schema.array("Columns: name, elementId, width", Schema.result("").build()))
                        .property("outputs", Schema.array("Columns: name, elementId, width",
                                Schema.result("").build()))
                        .property("rows", Schema.array("inputs, outputs, settled, cutShort (the row ran out of "
                                + "its share of the events before settling: its outputs are not known)",
                                Schema.result("").build()))
                        .property("totalRows", Schema.integer("Rows the full table has"))
                        .property("truncated", Schema.bool("The row limit cut it"))
                        .property("budgetExceeded", Schema.bool("Some rows are cutShort (rows never reached are a "
                                + "BUDGET_EXCEEDED error instead)"))
                        .property("eventsProcessed", Schema.integer("Events run"))
                        .property("hasFeedback", Schema.bool("A loop without flip-flops"))
                        .required("inputs", "outputs", "rows", "totalRows", "truncated").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1"),
                        Map.of("document", "circuit-1", "elementIds", List.of("c9"), "maximumRows", 512)),
                arguments -> {
                    TruthTableView table = api.truthTable(arguments.document(), arguments.stringSet("elementIds"),
                            arguments.integer("maximumRows", 0), arguments.integer("eventBudget", 0));
                    if (table.rowsLeftOut()) {
                        throw new AutomationException(AutomationError.BUDGET_EXCEEDED, "The event budget of "
                                + table.eventBudget() + " events ran out after " + table.rows().size() + " of "
                                + table.totalRows() + " rows; the rows done are in recovery.table",
                                Map.of("table", Json.object(table)));
                    }
                    return Tool.Output.of(table);
                });
    }
}
