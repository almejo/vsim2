package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.check.StageCheck;
import cl.almejo.vsim.automation.check.StageChecks;
import cl.almejo.vsim.automation.simulate.SimulationOperations;

import java.util.List;
import java.util.Map;

/** {@code vsim_check_stage}: the check that ends a stage, with VSim's measured results only (spec §3, §6, A§9). */
final class CheckTools {

    static final String CHECK_STAGE = "vsim_check_stage";

    private CheckTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(checkStage(api));
    }

    private static Map<String, Object> checks() {
        Map<String, Object> values = Schema.mapOf("Values by column name or element id: 0b1010, 0x3F or a decimal",
                Schema.nonEmptyString(""));
        Map<String, Object> row = Schema.object("One row: every input column and the outputs to compare")
                .property("inputs", values)
                .property("outputs", values)
                .required("inputs", "outputs").build();
        Map<String, Object> signal = Schema.object("One pin and the value it must have")
                .property("pin", Common.pinReference("The pin"))
                .property("value", Schema.nonEmptyString("0b1010, 0x3F or a decimal"))
                .required("pin", "value").build();
        List<Map<String, Object>> kinds = List.of(
                Schema.object("The truth table on a copy, compared row by row")
                        .property("kind", Schema.constant("truthTable"))
                        .property("elementIds", Schema.array("The part of the circuit (empty: all of it)",
                                Schema.string("")))
                        .property("expected", Schema.array("The rows it must have", row, 1,
                                SimulationOperations.MAXIMUM_ROWS))
                        .property("maximumRows", Schema.integer("Rows to evaluate (0: the default)", 0,
                                SimulationOperations.MAXIMUM_ROWS))
                        .property("eventBudget", Schema.integer("Events in all (0: the default)", 0,
                                SimulationOperations.MAXIMUM_EVENT_BUDGET))
                        .required("kind", "expected").build(),
                Schema.object("No problems in the latest simulation (short circuits, width mismatches…)")
                        .property("kind", Schema.constant("problems"))
                        .required("kind").build(),
                Schema.object("The layout: no part overlapping another part, no label or note covering a part or "
                                + "another text, no wire through a part's body")
                        .property("kind", Schema.constant("layout"))
                        .property("elementIds", Schema.array("Only faults that involve these (empty: all)",
                                Schema.string("")))
                        .required("kind").build(),
                Schema.object("The values on some pins now (set the inputs and step first)")
                        .property("kind", Schema.constant("signals"))
                        .property("expected", Schema.array("Pins and values", signal, 1,
                                SimulationOperations.MAXIMUM_PINS))
                        .required("kind", "expected").build());
        return Schema.array("The checks, run in order", Schema.oneOf("One check, chosen by kind", kinds), 1,
                StageChecks.MAXIMUM_CHECKS);
    }

    private static StageCheck.Check checkOf(Arguments check) {
        return switch (check.string("kind")) {
            case "truthTable" -> new StageCheck.Check.TruthTable(check.stringSet("elementIds"),
                    check.objects("expected").stream()
                            .map(row -> new StageCheck.ExpectedRow(row.stringMap("inputs"), row.stringMap("outputs")))
                            .toList(),
                    check.integer("maximumRows", 0), check.integer("eventBudget", 0));
            case "problems" -> new StageCheck.Check.Problems();
            case "layout" -> new StageCheck.Check.Layout(check.stringSet("elementIds"));
            case "signals" -> new StageCheck.Check.Signals(check.objects("expected").stream()
                    .map(expected -> new StageCheck.ExpectedSignal(expected.object("pin").pin(),
                            expected.string("value")))
                    .toList());
            default -> throw check.invalid("kind", "is truthTable, problems, layout or signals");
        };
    }

    private static Tool checkStage(AutomationApi api) {
        return new Tool(CHECK_STAGE, "Check a stage",
                "End every stage with this: VSim runs the checks asked (a truth table against expected rows, "
                        + "Problems, the layout, pin values) and reports what it measured. Each check is PASSED "
                        + "only when VSim finished it and every expectation held, FAILED with what differed, or "
                        + "NOT_FINISHED when VSim could not finish it (a budget, a row limit, a simulation that has "
                        + "not caught up, a circuit with no table): never say a stage is checked unless passed is "
                        + "true. Report VSim's results, not your own expectations.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("taskId", Schema.string("The task, to label the result"))
                        .property("stage", Schema.string("The stage's title, to label the result"))
                        .property("checks", checks())
                        .required("document", "checks").build(),
                Common.versionedResult("What VSim measured")
                        .property("currentVersion", Schema.integer("The version when the checks ended"))
                        .property("taskId", Schema.string(""))
                        .property("stage", Schema.string(""))
                        .property("checks", Schema.array("One per check: kind, status, summary, details",
                                Schema.result("")
                                        .property("kind", Schema.string(""))
                                        .property("status", Schema.enumeration("", List.of("PASSED", "FAILED",
                                                "NOT_FINISHED")))
                                        .property("summary", Schema.string(""))
                                        .property("details", Schema.result("").build())
                                        .required("kind", "status", "summary").build()))
                        .property("passed", Schema.bool("True only when every check PASSED"))
                        .required("checks", "passed").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1", "taskId", "task-1", "stage", "Semisumador", "checks",
                        List.of(Map.of("kind", "truthTable", "expected", List.of(
                                        Map.of("inputs", Map.of("A", "0", "B", "0"),
                                                "outputs", Map.of("S", "0", "C", "0")),
                                        Map.of("inputs", Map.of("A", "0", "B", "1"),
                                                "outputs", Map.of("S", "1", "C", "0")),
                                        Map.of("inputs", Map.of("A", "1", "B", "0"),
                                                "outputs", Map.of("S", "1", "C", "0")),
                                        Map.of("inputs", Map.of("A", "1", "B", "1"),
                                                "outputs", Map.of("S", "0", "C", "1")))),
                                Map.of("kind", "problems"),
                                Map.of("kind", "layout")))),
                arguments -> Tool.Output.of(api.checkStage(new StageCheck.Request(arguments.document(),
                        arguments.optionalString("taskId"), arguments.optionalString("stage"),
                        arguments.objects("checks").stream().map(CheckTools::checkOf).toList()))));
    }
}
