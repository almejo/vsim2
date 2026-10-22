package cl.almejo.vsim.automation.check;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.PinReference;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The check that ends a stage (spec §3, A§9): the checks the AI asks VSim to run, and what VSim measured. A check is
 * {@link Status#PASSED} only when VSim finished it and every expectation held; one VSim could not finish (a budget, a
 * row limit, a simulation that has not caught up, a scope with no table) is {@link Status#NOT_FINISHED}, never passed.
 */
public final class StageCheck {

    /** The kind of the layout check: the only check that is not about what the circuit does. */
    private static final String LAYOUT = "layout";

    private StageCheck() {
    }

    /** The stage's checks; {@code taskId} and {@code stage} only label the result. */
    public record Request(DocumentId document, Optional<String> taskId, Optional<String> stage, List<Check> checks) {
        public Request {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(stage, "stage");
            checks = List.copyOf(checks);
        }
    }

    /** One check to run. */
    public sealed interface Check {

        /** Its name in the result: {@code truthTable}, {@code problems}, {@code layout} or {@code signals}. */
        String kind();

        /**
         * The truth table of the circuit (empty {@code elementIds}) or of some elements, on a copy, compared with the
         * expected rows: each names every input column (by its name or element id) and the outputs to compare.
         */
        record TruthTable(Set<String> elementIds, List<ExpectedRow> expected, int maximumRows, int eventBudget)
                implements Check {
            public TruthTable {
                elementIds = Set.copyOf(elementIds);
                expected = List.copyOf(expected);
            }

            @Override
            public String kind() {
                return "truthTable";
            }
        }

        /** Problems of the latest simulation (short circuits, width mismatches…): passed when there is none. */
        record Problems() implements Check {
            @Override
            public String kind() {
                return "problems";
            }
        }

        /** {@link LayoutCheck} over the elements given (empty: the whole circuit): passed when it finds no fault. */
        record Layout(Set<String> elementIds) implements Check {
            public Layout {
                elementIds = Set.copyOf(elementIds);
            }

            @Override
            public String kind() {
                return "layout";
            }
        }

        /** The values on some pins now, compared with the expected ones (set the inputs and step before). */
        record Signals(List<ExpectedSignal> expected) implements Check {
            public Signals {
                expected = List.copyOf(expected);
            }

            @Override
            public String kind() {
                return "signals";
            }
        }
    }

    /** One expected row: input and output values by column name or element id ({@code 0b1010}, {@code 0x3F}, {@code 5}). */
    public record ExpectedRow(Map<String, String> inputs, Map<String, String> outputs) {
        public ExpectedRow {
            inputs = Map.copyOf(inputs);
            outputs = Map.copyOf(outputs);
        }
    }

    /** One pin and the value expected on it. */
    public record ExpectedSignal(PinReference pin, String value) {
        public ExpectedSignal {
            Objects.requireNonNull(pin, "pin");
            Objects.requireNonNull(value, "value");
        }
    }

    /** How a check ended. */
    public enum Status {
        /** Finished, and every expectation held. */
        PASSED,
        /** Finished, and something did not hold: {@code details} says what. */
        FAILED,
        /** VSim could not finish it: never a pass. {@code details} says why. */
        NOT_FINISHED
    }

    /** What one check measured: a one-line summary for the AI and the measured data in {@code details}. */
    public record CheckResult(String kind, Status status, String summary, Map<String, Object> details) {
        public CheckResult {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(summary, "summary");
            details = Map.copyOf(details);
        }
    }

    /**
     * The stage's result: {@code passed} only when every check passed. {@code version} is the circuit version when
     * the checks began; {@code currentVersion} when they ended (they differ when the circuit changed meanwhile).
     */
    public record Result(DocumentId document, DocumentVersion version, DocumentVersion currentVersion,
                         Optional<String> taskId, Optional<String> stage, List<CheckResult> checks, boolean passed) {
        public Result {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(currentVersion, "currentVersion");
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(stage, "stage");
            checks = List.copyOf(checks);
        }

        /**
         * True when a check of what the circuit does (truth table, signals, problems) did not pass (FAILED or
         * NOT_FINISHED): «De corrido» stops at the stage's end. Layout faults and a circuit that changed while the
         * checks ran are reported, but never a functional failure (IA.3 Speed).
         */
        public boolean functionalFailure() {
            return checks.stream().anyMatch(check -> !LAYOUT.equals(check.kind()) && check.status() != Status.PASSED);
        }
    }

    /** The faults {@link LayoutCheck} found in one version of a circuit. */
    public record LayoutView(DocumentId document, DocumentVersion version, List<LayoutCheck.Fault> faults) {
        public LayoutView {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(version, "version");
            faults = List.copyOf(faults);
        }
    }
}
