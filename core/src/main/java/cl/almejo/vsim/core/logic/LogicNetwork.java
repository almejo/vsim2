package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.component.standard.LogicOperation;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.logic.GeneratorOptions.GateStyle;
import cl.almejo.vsim.core.logic.Minimization.Form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gates of a minimized table, without geometry: what {@link CircuitGenerator} places and what
 * {@link CircuitGenerator#cost} counts, so both always agree.
 * <p>
 * The minimization becomes a two-level expression (sum of products or product of sums) whose literals are the inputs
 * or their complements. One-literal terms are the literal itself; one-term outputs are the term itself; constant
 * outputs are a {@link ConstantSignal}. Wide gates become trees of 2..4-input gates. For NAND-only and NOR-only the
 * expression is rebuilt with the usual identities (an AND is a NAND whose output is inverted, an OR is a NAND of the
 * complemented inputs, …), asking each node for itself or its complement so double inversions never appear.
 * Inverters are shared per signal (one NOT per negated input); terms are shared between outputs when the options say
 * so.
 */
final class LogicNetwork {

    /** At most this many inputs per gate (the standard gates have 2, 3 or 4). */
    static final int MAXIMUM_FAN_IN = 4;

    /** Something that drives a net. */
    sealed interface Signal permits InputSignal, GateSignal, ConstantSignal {
    }

    /** Input {@code input} of the table, straight from its pin. */
    record InputSignal(int input) implements Signal {
    }

    /** The output of gate {@code gate} (an index into {@link #gates()}). */
    record GateSignal(int gate) implements Signal {
    }

    /** A constant 0 or 1; {@code owner} keeps unshared constants apart. */
    record ConstantSignal(boolean value, int owner) implements Signal {
    }

    /**
     * A gate: its component type id and the signal of each input pin in order (the same signal twice for an
     * inverter made of a NAND2 or NOR2 with tied inputs).
     */
    record Gate(String typeId, List<Signal> inputs) {

        Gate {
            inputs = List.copyOf(inputs);
        }
    }

    /** Nodes of the two-level expression. */
    private sealed interface Node permits Literal, Operation, Constant {
    }

    private record Literal(int input, boolean positive) implements Node {
    }

    /** An AND ({@code and} true) or an OR of two or more nodes; {@code owner} keeps unshared terms apart. */
    private record Operation(boolean and, List<Node> children, int owner) implements Node {
    }

    private record Constant(boolean value, int owner) implements Node {
    }

    private record Realization(Node node, boolean inverted) {
    }

    private final GateStyle style;
    private final List<Gate> gates = new ArrayList<>();
    private final List<Signal> outputs = new ArrayList<>();
    private final Map<Realization, Signal> realized = new HashMap<>();
    private final Map<Signal, Signal> inverted = new HashMap<>();

    private LogicNetwork(GateStyle style) {
        this.style = style;
    }

    /** The gates of {@code minimization} built with {@code options}; its form must be the options' form. */
    static LogicNetwork of(Minimization minimization, GeneratorOptions options) {
        if (minimization.form() != options.form()) {
            throw new IllegalArgumentException("the minimization is a " + minimization.form()
                    + " but the options ask for a " + options.form());
        }
        LogicNetwork network = new LogicNetwork(options.gates());
        TruthTableSpec spec = minimization.spec();
        for (Minimization.OutputExpression expression : minimization.outputs()) {
            int owner = options.shareTerms() ? 0 : expression.output() + 1;
            Node node = expressionOf(expression.terms(), minimization.form(), spec.inputCount(), owner);
            network.outputs.add(network.realize(node, false));
        }
        return network;
    }

    /** Every gate, sources before the gates they feed. */
    List<Gate> gates() {
        return gates;
    }

    /** The signal of each output, in the table's order. */
    List<Signal> outputs() {
        return outputs;
    }

    /** Gate counts per type id, in the {@link GateCost} order. */
    GateCost cost() {
        Map<String, Integer> counts = new HashMap<>();
        for (Gate gate : gates) {
            counts.merge(gate.typeId(), 1, Integer::sum);
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.keySet().stream()
                .sorted(Comparator.comparingInt((String type) -> familyOrder(type))
                        .thenComparingInt(type -> type.length())
                        .thenComparing(Comparator.naturalOrder()))
                .forEach(type -> ordered.put(type, counts.get(type)));
        return new GateCost(gates.size(), ordered);
    }

    private static int familyOrder(String typeId) {
        int index = GateCost.FAMILIES.indexOf(GateCost.family(typeId).toUpperCase(Locale.ROOT));
        return index < 0 ? GateCost.FAMILIES.size() : index;
    }

    /** True for an inverter whose input is an input of the table: the inverters drawn next to the inputs. */
    boolean isInputInverter(Gate gate) {
        return isInverter(gate) && gate.inputs().get(0) instanceof InputSignal;
    }

    static boolean isInverter(Gate gate) {
        return gate.typeId().equals(StandardComponentTypes.NOT)
                || gate.inputs().size() == 2 && gate.inputs().get(0).equals(gate.inputs().get(1))
                && (gate.typeId().equals(LogicOperation.NAND.typeId(2))
                || gate.typeId().equals(LogicOperation.NOR.typeId(2)));
    }

    // --- the expression ---

    private static Node expressionOf(List<Term> terms, Form form, int inputCount, int owner) {
        boolean products = form == Form.SUM_OF_PRODUCTS;
        // A term without variables covers every row: the output is the constant (1 in a sum, 0 in a product).
        if (terms.stream().anyMatch(term -> term.mask() == 0)) {
            return new Constant(products, owner);
        }
        if (terms.isEmpty()) {
            return new Constant(!products, owner);
        }
        List<Node> termNodes = new ArrayList<>();
        for (Term term : terms) {
            List<Node> literals = new ArrayList<>();
            for (int input = 0; input < inputCount; input++) {
                if (term.uses(input, inputCount)) {
                    boolean groupValue = term.valueOf(input, inputCount);
                    // A product uses the variable as it is in the group of 1s; a sum complements the group of 0s.
                    literals.add(new Literal(input, products == groupValue));
                }
            }
            termNodes.add(literals.size() == 1 ? literals.get(0) : new Operation(products, literals, owner));
        }
        return termNodes.size() == 1 ? termNodes.get(0) : new Operation(!products, termNodes, owner);
    }

    // --- realization in gates ---

    private Signal realize(Node node, boolean complement) {
        Realization key = new Realization(node, complement);
        Signal known = realized.get(key);
        if (known != null) {
            return known;
        }
        Signal signal = switch (node) {
            case Literal literal -> literal.positive() != complement
                    ? new InputSignal(literal.input())
                    : invert(new InputSignal(literal.input()));
            case Constant constant -> new ConstantSignal(constant.value() != complement, constant.owner());
            case Operation operation -> realizeOperation(operation, complement);
        };
        realized.put(key, signal);
        return signal;
    }

    private Signal realizeOperation(Operation operation, boolean complement) {
        return switch (style) {
            case AND_OR_NOT -> complement
                    ? invert(realize(operation, false))
                    : tree(operation.and() ? LogicOperation.AND : LogicOperation.OR, childSignals(operation, false));
            // AND = NOT NAND; OR = NAND of the complements.
            case NAND_ONLY -> operation.and() == complement
                    ? tree(LogicOperation.NAND, childSignals(operation, !operation.and()))
                    : invert(realize(operation, !complement));
            // OR = NOT NOR; AND = NOR of the complements.
            case NOR_ONLY -> operation.and() != complement
                    ? tree(LogicOperation.NOR, childSignals(operation, operation.and()))
                    : invert(realize(operation, !complement));
        };
    }

    private List<Signal> childSignals(Operation operation, boolean complement) {
        List<Signal> signals = new ArrayList<>();
        for (Node child : operation.children()) {
            signals.add(realize(child, complement));
        }
        return signals;
    }

    /**
     * One gate of {@code operation} over {@code inputs}, or a tree when there are more than four: the inputs are
     * split into balanced groups of 2..4 whose results feed the same operation again. For NAND (NOR) the groups are
     * ANDed (ORed) first, which is a NAND (NOR) followed by an inverter.
     */
    private Signal tree(LogicOperation operation, List<Signal> inputs) {
        if (inputs.size() <= MAXIMUM_FAN_IN) {
            return gate(operation.typeId(inputs.size()), inputs);
        }
        List<Signal> groups = new ArrayList<>();
        for (List<Signal> group : balancedGroups(inputs)) {
            Signal combined = tree(operation, group);
            groups.add(operation.negated() ? invert(combined) : combined);
        }
        return tree(operation, groups);
    }

    /** {@code inputs} (more than four) in ⌈n/4⌉ consecutive groups whose sizes differ by at most one. */
    static <T> List<List<T>> balancedGroups(List<T> inputs) {
        int groupCount = (inputs.size() + MAXIMUM_FAN_IN - 1) / MAXIMUM_FAN_IN;
        int smallest = inputs.size() / groupCount;
        int larger = inputs.size() % groupCount;
        List<List<T>> groups = new ArrayList<>();
        int start = 0;
        for (int group = 0; group < groupCount; group++) {
            int size = smallest + (group < larger ? 1 : 0);
            groups.add(inputs.subList(start, start + size));
            start += size;
        }
        return groups;
    }

    private Signal invert(Signal signal) {
        Signal known = inverted.get(signal);
        if (known != null) {
            return known;
        }
        Signal result = switch (style) {
            case AND_OR_NOT -> gate(StandardComponentTypes.NOT, List.of(signal));
            case NAND_ONLY -> gate(LogicOperation.NAND.typeId(2), List.of(signal, signal));
            case NOR_ONLY -> gate(LogicOperation.NOR.typeId(2), List.of(signal, signal));
        };
        inverted.put(signal, result);
        return result;
    }

    private Signal gate(String typeId, List<Signal> inputs) {
        gates.add(new Gate(typeId, inputs));
        return new GateSignal(gates.size() - 1);
    }
}
