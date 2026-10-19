package cl.almejo.vsim.ui.karnaugh;

/** A step panel of the wizard that derives what it shows from the model; the wizard calls it when it is shown. */
interface WizardStep {

    /** Shows the model as it is now (it may have changed while another step was on screen). */
    void refresh();
}
