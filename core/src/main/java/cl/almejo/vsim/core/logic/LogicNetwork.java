package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.component.standard.LogicOperation;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.logic.GeneratorOptions.GateStyle;
import cl.almejo.vsim.core.logic.Minimization.Form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
     * A gate: its component type id ({@code and}, {@code or}, {@code xor} or {@code buffer}), whether its output is
     * negated, and the signal of each input pin in order (the same signal twice for an inverter made of a NAND or NOR
     * with tied inputs).
     */
    record Gate(String typeId, boolean negated, List<Signal> inputs) {

        Gate {
            inputs = List.copyOf(inputs);
        }

        /** The parameters of the placed part: 1-bit, «Negar salida» and, for an AND/OR/XOR, «Entradas». */
        Map<String, Object> parameters() {
            Map<String, Object> parameters = new LinkedHashMap<>();
            if (!typeId.equals(StandardComponentTypes.BUFFER)) {
                parameters.put(StandardComponentTypes.GATE_INPUTS, inputs.size());
            }
            parameters.put(StandardComponentTypes.WIDTH, 1);
            parameters.put(StandardComponentTypes.NEGATE, negated);
            return Collections.unmodifiableMap(parameters);
        }

        /** The name of the part: {@code and}, {@code nand}, {@code not}, … */
        String nameId() {
            return StandardComponentTypes.nameId(typeId, parameters());
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

    /** A sub-expression of a written line; {@code owner} keeps the outputs apart when terms are not shared. */
    private record Written(Expression expression, int owner) {
    }

    private final GateStyle style;
    private final List<Gate> gates = new ArrayList<>();
    private final List<Signal> outputs = new ArrayList<>();
    private final Map<Realization, Signal> realized = new HashMap<>();
    private final Map<Signal, Signal> inverted = new HashMap<>();
    private final Map<Written, Signal> written = new HashMap<>();

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

    /**
     * The gates of {@code lines} as written («Circuito desde expresiones» without «Simplificar», spec §3): one AND,
     * OR, XOR or NOT per operator of the (flattened) trees, trees of 2..4-input gates for wider operations. NOT of a
     * variable is the inverter next to that input (one per input, for every output); NOT of anything else is a NOT
     * gate after it; a variable output is the input itself; a constant is a {@link ConstantSignal}. Equal
     * sub-expressions are one gate inside one output always, and across outputs when {@code shareTerms}.
     *
     * @throws IllegalArgumentException when {@code spec} is not a table of {@code lines} (other outputs, or a variable
     *                                  that is not one of its inputs)
     */
    static LogicNetwork ofExpressions(TruthTableSpec spec, List<ExpressionLine> lines, boolean shareTerms) {
        if (!spec.outputNames().equals(lines.stream().map(ExpressionLine::outputName).toList())) {
            throw new IllegalArgumentException("the table's outputs are not the lines' outputs");
        }
        LogicNetwork network = new LogicNetwork(GateStyle.AND_OR_NOT);
        for (int output = 0; output < lines.size(); output++) {
            int owner = shareTerms ? 0 : output + 1;
            network.outputs.add(network.realizeWritten(lines.get(output).expression(), owner, spec));
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

    /** Gate counts per {@linkplain GateCost#key gate key}, in the {@link GateCost} order. */
    GateCost cost() {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> familyOrders = new HashMap<>();
        for (Gate gate : gates) {
            String key = GateCost.key(gate.typeId(), gate.parameters());
            counts.merge(key, 1, Integer::sum);
            familyOrders.computeIfAbsent(key, ignored -> familyOrder(gate));
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.keySet().stream()
                .sorted(Comparator.comparingInt((String type) -> familyOrders.get(type))
                        .thenComparingInt(type -> type.length())
                        .thenComparing(Comparator.naturalOrder()))
                .forEach(type -> ordered.put(type, counts.get(type)));
        return new GateCost(gates.size(), ordered);
    }

    private static int familyOrder(Gate gate) {
        int index = GateCost.family(gate.typeId(), gate.parameters()).map(GateCost.FAMILIES::indexOf).orElse(-1);
        return index < 0 ? GateCost.FAMILIES.size() : index;
    }

    /** True for an inverter whose input is an input of the table: the inverters drawn next to the inputs. */
    boolean isInputInverter(Gate gate) {
        return isInverter(gate) && gate.inputs().get(0) instanceof InputSignal;
    }

    static boolean isInverter(Gate gate) {
        if (!gate.negated()) {
            return false;
        }
        return gate.typeId().equals(StandardComponentTypes.BUFFER)
                || gate.inputs().size() == 2 && gate.inputs().get(0).equals(gate.inputs().get(1))
                && !gate.typeId().equals(StandardComponentTypes.XOR);
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

    // --- expressions as written ---

    private Signal realizeWritten(Expression expression, int owner, TruthTableSpec spec) {
        Written key = new Written(expression, owner);
        Signal known = written.get(key);
        if (known != null) {
            return known;
        }
        Signal signal = switch (expression) {
            case Expression.Variable variable -> inputSignal(variable, spec);
            case Expression.Constant constant -> new ConstantSignal(constant.value(), owner);
            case Expression.Not not -> not.operand() instanceof Expression.Variable variable
                    ? invert(inputSignal(variable, spec))
                    : invert(realizeWritten(not.operand(), owner, spec));
            case Expression.And and -> tree(LogicOperation.AND, false, writtenSignals(and.operands(), owner, spec));
            case Expression.Or or -> tree(LogicOperation.OR, false, writtenSignals(or.operands(), owner, spec));
            case Expression.Xor xor -> tree(LogicOperation.XOR, false, writtenSignals(xor.operands(), owner, spec));
        };
        written.put(key, signal);
        return signal;
    }

    private List<Signal> writtenSignals(List<Expression> operands, int owner, TruthTableSpec spec) {
        List<Signal> signals = new ArrayList<>();
        for (Expression operand : operands) {
            signals.add(realizeWritten(operand, owner, spec));
        }
        return signals;
    }

    private static InputSignal inputSignal(Expression.Variable variable, TruthTableSpec spec) {
        int input = spec.inputNames().indexOf(variable.name());
        if (input < 0) {
            throw new IllegalArgumentException("the table has no input " + variable.name());
        }
        return new InputSignal(input);
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
                    : tree(operation.and() ? LogicOperation.AND : LogicOperation.OR, false,
                    childSignals(operation, false));
            // AND = NOT NAND; OR = NAND of the complements.
            case NAND_ONLY -> operation.and() == complement
                    ? tree(LogicOperation.AND, true, childSignals(operation, !operation.and()))
                    : invert(realize(operation, !complement));
            // OR = NOT NOR; AND = NOR of the complements.
            case NOR_ONLY -> operation.and() != complement
                    ? tree(LogicOperation.OR, true, childSignals(operation, operation.and()))
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
     * One gate of {@code operation} ({@code negated}: NAND, NOR) over {@code inputs}, or a tree when there are more
     * than four: the inputs are split into balanced groups of 2..4 whose results feed the same operation again. For
     * NAND (NOR) the groups are ANDed (ORed) first, which is a NAND (NOR) followed by an inverter.
     */
    private Signal tree(LogicOperation operation, boolean negated, List<Signal> inputs) {
        if (inputs.size() <= MAXIMUM_FAN_IN) {
            return gate(operation.typeId(), negated, inputs);
        }
        List<Signal> groups = new ArrayList<>();
        for (List<Signal> group : balancedGroups(inputs)) {
            Signal combined = tree(operation, negated, group);
            groups.add(negated ? invert(combined) : combined);
        }
        return tree(operation, negated, groups);
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
            case AND_OR_NOT -> gate(StandardComponentTypes.BUFFER, true, List.of(signal));
            case NAND_ONLY -> gate(StandardComponentTypes.AND, true, List.of(signal, signal));
            case NOR_ONLY -> gate(StandardComponentTypes.OR, true, List.of(signal, signal));
        };
        inverted.put(signal, result);
        return result;
    }

    private Signal gate(String typeId, boolean negated, List<Signal> inputs) {
        gates.add(new Gate(typeId, negated, inputs));
        return new GateSignal(gates.size() - 1);
    }
}
