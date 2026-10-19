package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What surrounds a circuit generated from a table (spec §3 step 5, §6): the check that it does what the table says,
 * the library component made of it, the table of a circuit handed to the wizard. Headless; safe off the EDT (each call
 * works on its own scratch document).
 */
public final class TableCircuits {

    /** Where a generated circuit lands in a new document: a small margin from the top-left corner. */
    public static final GridPoint NEW_CIRCUIT_ORIGIN = new GridPoint(2, 2);

    private TableCircuits() {
    }

    /** A row and output where the circuit does not give what the table says. */
    public record Mismatch(int row, String output, LogicValue expected) {
    }

    /**
     * The result of {@link #check}: {@code evaluated} is false when the circuit could not be evaluated as a table with
     * the spec's columns (a bug in the generator, never the user's doing).
     */
    public record Check(boolean evaluated, List<Mismatch> mismatches) {

        public Check {
            mismatches = List.copyOf(mismatches);
        }

        /** The circuit gives the table on every row that is not X. */
        public boolean passed() {
            return evaluated && mismatches.isEmpty();
        }
    }

    /**
     * Evaluates {@code fragment}'s truth table and compares it with {@code spec} on every row that is not X
     * («✓ El circuito cumple la tabla»). An unsettled row counts as a mismatch.
     */
    public static Check check(TruthTableSpec spec, CircuitFragment fragment, ComponentTypeRegistry registry) {
        CircuitDocument document = scratch(fragment);
        if (!(TruthTableScope.prepare(document, Set.of(), registry) instanceof Preparation.Ready ready)
                || !names(ready.inputs()).equals(spec.inputNames())
                || !names(ready.outputs()).equals(spec.outputNames())) {
            return new Check(false, List.of());
        }
        TruthTable table = TruthTableEvaluator.evaluate(ready, percent -> {
        }, () -> false).orElseThrow();
        List<Mismatch> mismatches = new ArrayList<>();
        for (int row = 0; row < spec.rowCount(); row++) {
            Row computed = table.rows().get(row);
            for (int output = 0; output < spec.outputCount(); output++) {
                LogicValue expected = spec.value(output, row);
                if (expected != LogicValue.DONT_CARE && (!computed.settled()
                        || !computed.outputs().get(output).equals(Value.of(expected == LogicValue.ONE)))) {
                    mismatches.add(new Mismatch(row, spec.outputNames().get(output), expected));
                }
            }
        }
        return new Check(true, mismatches);
    }

    /**
     * The library component of a generated circuit: its gates, pins and wires (not its frame) as the body, the input
     * pins on the left and the output pins on the right in table order, named after the table's columns.
     */
    public static DefinitionBundle component(TruthTableSpec spec, CircuitFragment fragment, String id, String name,
                                             ComponentTypeRegistry registry) {
        CircuitDocument document = scratch(fragment);
        List<ElementId> ids = new ArrayList<>();
        document.components().forEach(component -> ids.add(component.id()));
        document.wires().forEach(wire -> ids.add(wire.id()));
        document.labels().forEach(label -> ids.add(label.id()));
        PinOrder order = new PinOrder(pins(document, StandardComponentTypes.INPUT_PIN, spec.inputNames()),
                pins(document, StandardComponentTypes.OUTPUT_PIN, spec.outputNames()));
        return DefinitionBuilder.fromSelection(document, ids, id, name, order, registry);
    }

    /** One instance of {@code bundle}'s component at (0,0), carrying its definitions: what «Insertar» floats. */
    public static CircuitFragment instance(DefinitionBundle bundle) {
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(bundle.root().id()),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        return new CircuitFragment(List.of(instance), List.of(), List.of(), bundle.all());
    }

    /**
     * The table of a circuit as a spec for the wizard: every column must be one bit wide and there must be
     * 1..{@value TruthTableSpec#MAXIMUM_INPUTS} inputs and 1..{@value TruthTableSpec#MAXIMUM_OUTPUTS} outputs. An
     * output that is unsettled, Z or in conflict becomes X. Blank names become A, B, … (inputs) and Y1, Y2, …
     * (outputs); a repeated name (ignoring case) gets 2, 3, ….
     *
     * @throws IllegalArgumentException when a column is wider than one bit or the counts are out of range
     */
    public static TruthTableSpec specOf(TruthTable table, String title) {
        if (table.inputs().stream().anyMatch(column -> column.width() != 1)
                || table.outputs().stream().anyMatch(column -> column.width() != 1)) {
            throw new IllegalArgumentException("every column must be one bit wide");
        }
        Set<String> taken = new HashSet<>();
        List<String> inputNames = new ArrayList<>();
        for (int index = 0; index < table.inputs().size(); index++) {
            inputNames.add(unique(table.inputs().get(index).name(), DefinitionBuilder.letters(index + 1), taken));
        }
        List<String> outputNames = new ArrayList<>();
        for (int index = 0; index < table.outputs().size(); index++) {
            outputNames.add(unique(table.outputs().get(index).name(), "Y" + (index + 1), taken));
        }
        List<List<LogicValue>> outputs = new ArrayList<>();
        for (int output = 0; output < table.outputs().size(); output++) {
            List<LogicValue> column = new ArrayList<>();
            for (Row row : table.rows()) {
                Value value = row.outputs().get(output);
                column.add(!row.settled() || !value.isFullyDefined() ? LogicValue.DONT_CARE
                        : value.equals(Value.of(true)) ? LogicValue.ONE : LogicValue.ZERO);
            }
            outputs.add(column);
        }
        return new TruthTableSpec(title, inputNames, outputNames, outputs);
    }

    private static String unique(String name, String fallback, Set<String> taken) {
        String base = name == null || name.isBlank() ? fallback : name.strip();
        String candidate = base;
        for (int number = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); number++) {
            candidate = base + number;
        }
        return candidate;
    }

    private static List<ElementId> pins(CircuitDocument document, String typeId, List<String> names) {
        return document.components().stream()
                .filter(component -> component.typeId().equals(typeId))
                .sorted(Comparator.comparingInt((Component component) -> names.indexOf(component.label()))
                        .thenComparingInt(component -> component.position().y()))
                .map(Component::id)
                .toList();
    }

    private static List<String> names(List<Column> columns) {
        return columns.stream().map(Column::name).toList();
    }

    private static CircuitDocument scratch(CircuitFragment fragment) {
        CircuitDocument document = new CircuitDocument();
        new PasteCommand(fragment, GridPoint.ORIGIN).apply(document);
        return document;
    }
}
