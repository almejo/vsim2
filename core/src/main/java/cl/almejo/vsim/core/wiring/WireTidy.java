package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Proposes how to tidy the top-level wires of a document without changing what they mean. Pure: it never mutates
 * the document. Rules run in rounds until a round changes nothing (at most {@value #MAXIMUM_ROUNDS}); each rule pass
 * is checked against the {@link Connectivity} of the original wires (and replayed change by change, each one checked,
 * when that fails), and the final result once more with the netlist compiler.
 */
public final class WireTidy {

    static final int MAXIMUM_ROUNDS = 50;

    private WireTidy() {
    }

    /**
     * @param scope ids of the wires allowed to change (all wires when the caller has no selection); the connectivity
     *              check always covers the whole circuit
     */
    public static TidyPlan plan(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> scope,
                                TidyOptions options) {
        return plan(document, registry, scope, options, () -> false, round -> {
        }).orElseThrow();
    }

    /**
     * Like {@link #plan(CircuitDocument, ComponentTypeRegistry, Set, TidyOptions)} but stoppable: {@code cancelled}
     * is polled before every round, rule pass and change, and the result is empty when it said yes (no partial
     * plan). {@code progress} gets the number of each round (1, 2, …) as it starts. Like the evaluator of
     * truth tables, it never throws for a cancellation. May run on any thread as long as the document is not
     * changed meanwhile.
     */
    public static Optional<TidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                          Set<ElementId> scope, TidyOptions options, BooleanSupplier cancelled,
                                          IntConsumer progress) {
        if (cancelled.getAsBoolean()) {
            return Optional.empty();
        }
        try {
            TidyState state = new TidyState(document, registry, scope);
            state.cancellation(cancelled);
            List<TidyRule> rules = new ArrayList<>();
            if (options.removeStubs()) {
                rules.add(new StubRule());
            }
            if (options.mergeSegments()) {
                rules.add(new MergeRule());
            }
            if (options.flattenDetours()) {
                rules.add(detourRule());
            }
            for (int round = 0; round < MAXIMUM_ROUNDS; round++) {
                state.checkCancelled();
                progress.accept(round + 1);
                boolean changed = false;
                for (TidyRule rule : rules) {
                    changed |= state.run(rule);
                }
                if (!changed) {
                    break;
                }
            }
            state.checkCancelled();
            return Optional.of(state.toPlan());
        } catch (TidyState.Cancelled cancellation) {
            return Optional.empty();
        }
    }

    /** The U-detour rule (spec 2.2.4). */
    static TidyRule detourRule() {
        return new DetourRule();
    }
}
