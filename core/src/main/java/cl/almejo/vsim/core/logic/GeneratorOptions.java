package cl.almejo.vsim.core.logic;

import java.util.Objects;

/**
 * How {@link CircuitGenerator} builds a circuit (spec §3 step 4).
 *
 * @param gates      which gates it may use
 * @param form       sum of products or product of sums; must be the form of the minimization it is given
 * @param shareTerms one gate per distinct term even when several outputs use it
 */
public record GeneratorOptions(GateStyle gates, Minimization.Form form, boolean shareTerms) {

    /** AND / OR / NOT, sum of products, terms shared: the wizard's defaults. */
    public static final GeneratorOptions DEFAULTS =
            new GeneratorOptions(GateStyle.AND_OR_NOT, Minimization.Form.SUM_OF_PRODUCTS, true);

    public GeneratorOptions {
        Objects.requireNonNull(gates, "gates");
        Objects.requireNonNull(form, "form");
    }

    /**
     * The gates of the circuit. An inverter is a {@code not} gate with AND / OR / NOT, and a NAND2 (NOR2) with both
     * inputs tied together with «Solo NAND» («Solo NOR»).
     */
    public enum GateStyle {
        AND_OR_NOT,
        NAND_ONLY,
        NOR_ONLY
    }
}
