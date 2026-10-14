package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        TidyState state = new TidyState(document, registry, scope);
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
            boolean changed = false;
            for (TidyRule rule : rules) {
                changed |= state.run(rule);
            }
            if (!changed) {
                break;
            }
        }
        return state.toPlan();
    }

    /** The U-detour rule (spec 2.2.4). */
    static TidyRule detourRule() {
        return new DetourRule();
    }
}
