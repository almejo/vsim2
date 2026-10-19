package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;

import java.util.Objects;

/**
 * The step-4 options of the Karnaugh wizard (spec §3): which gates, which form, and whether outputs share terms.
 * {@link GateChoice} has the same constants as the generator's gate style, so the two map one to one.
 *
 * @param gates      «AND / OR / NOT», «Solo NAND» or «Solo NOR»
 * @param form       sum of products (groups of 1s) or product of sums (groups of 0s)
 * @param shareTerms one gate per distinct term across the outputs
 */
public record WizardOptions(GateChoice gates, Minimization.Form form, boolean shareTerms) {

    public enum GateChoice {
        AND_OR_NOT,
        NAND_ONLY,
        NOR_ONLY
    }

    public WizardOptions {
        Objects.requireNonNull(gates, "gates");
        Objects.requireNonNull(form, "form");
    }

    /** AND / OR / NOT, sum of products, sharing on (spec §10.4). */
    public static WizardOptions defaults() {
        return new WizardOptions(GateChoice.AND_OR_NOT, Minimization.Form.SUM_OF_PRODUCTS, true);
    }

    public WizardOptions withGates(GateChoice newGates) {
        return new WizardOptions(newGates, form, shareTerms);
    }

    public WizardOptions withForm(Minimization.Form newForm) {
        return new WizardOptions(gates, newForm, shareTerms);
    }

    public WizardOptions withShareTerms(boolean newShareTerms) {
        return new WizardOptions(gates, form, newShareTerms);
    }
}
