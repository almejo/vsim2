package cl.almejo.vsim.core.logic;

import java.util.List;

/**
 * The minimized expressions of every output of a table.
 *
 * @param spec    the table
 * @param form    sum of products (groups of 1s) or product of sums (groups of 0s)
 * @param outputs one expression per output, in the table's order
 */
public record Minimization(TruthTableSpec spec, Form form, List<OutputExpression> outputs) {

    public Minimization {
        outputs = List.copyOf(outputs);
    }

    public enum Form {
        SUM_OF_PRODUCTS,
        PRODUCT_OF_SUMS
    }

    /**
     * The terms of one output, in a deterministic order. Constant outputs: in a sum of products no terms means
     * always 0 and a single term with mask 0 means always 1; in a product of sums no terms means always 1 and a single
     * term with mask 0 means always 0.
     */
    public record OutputExpression(int output, List<Term> terms) {

        public OutputExpression {
            terms = List.copyOf(terms);
        }
    }
}
