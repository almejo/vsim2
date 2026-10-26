package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The step-4 option controls the Karnaugh panels share: the gate radios, the form radios and the «share terms» box,
 * each writing its change into the model's {@link WizardOptions} through the getter and setter it was given.
 */
final class OptionControls {

    private final Supplier<WizardOptions> options;
    private final Consumer<WizardOptions> setOptions;
    private final JPanel gatesPanel = new JPanel(new MigLayout("insets 0"));
    private final JPanel formsPanel = new JPanel(new MigLayout("insets 0"));
    private final Map<WizardOptions.GateChoice, JRadioButton> gateButtons =
            new EnumMap<>(WizardOptions.GateChoice.class);
    private final Map<Minimization.Form, JRadioButton> formButtons = new EnumMap<>(Minimization.Form.class);
    private final JCheckBox share = new JCheckBox(Messages.text("karnaugh.expression.share"));

    OptionControls(Supplier<WizardOptions> options, Consumer<WizardOptions> setOptions) {
        this.options = options;
        this.setOptions = setOptions;
        ButtonGroup gates = new ButtonGroup();
        addGate(gates, WizardOptions.GateChoice.AND_OR_NOT, "karnaugh.expression.gates.andOrNot");
        addGate(gates, WizardOptions.GateChoice.NAND_ONLY, "karnaugh.expression.gates.nandOnly");
        addGate(gates, WizardOptions.GateChoice.NOR_ONLY, "karnaugh.expression.gates.norOnly");
        ButtonGroup forms = new ButtonGroup();
        addForm(forms, Minimization.Form.SUM_OF_PRODUCTS, "karnaugh.expression.form.sop");
        addForm(forms, Minimization.Form.PRODUCT_OF_SUMS, "karnaugh.expression.form.pos");
        share.addActionListener(event -> setOptions.accept(options.get().withShareTerms(share.isSelected())));
    }

    private void addGate(ButtonGroup group, WizardOptions.GateChoice choice, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> setOptions.accept(options.get().withGates(choice)));
        group.add(button);
        gatesPanel.add(button);
        gateButtons.put(choice, button);
    }

    private void addForm(ButtonGroup group, Minimization.Form form, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> setOptions.accept(options.get().withForm(form)));
        group.add(button);
        formsPanel.add(button);
        formButtons.put(form, button);
    }

    JPanel gatesPanel() {
        return gatesPanel;
    }

    JPanel formsPanel() {
        return formsPanel;
    }

    JCheckBox shareBox() {
        return share;
    }

    AbstractButton gateButton(WizardOptions.GateChoice choice) {
        return gateButtons.get(choice);
    }

    AbstractButton formButton(Minimization.Form form) {
        return formButtons.get(form);
    }

    /** Shows the given options: the radio of each chosen option selected and the box set. */
    void sync(WizardOptions shown) {
        gateButtons.get(shown.gates()).setSelected(true);
        formButtons.get(shown.form()).setSelected(true);
        share.setSelected(shown.shareTerms());
    }

    /** Only the form radios, to show the selected form (the practice row has no gates or box). */
    void syncForm(WizardOptions shown) {
        formButtons.get(shown.form()).setSelected(true);
    }

    /** The form radios, in order (so a panel can lay them out in its own row). */
    Collection<JRadioButton> formRadios() {
        return formButtons.values();
    }

    /** The gate and form radios (not the box), to enable or disable together. */
    Collection<AbstractButton> radioButtons() {
        List<AbstractButton> radios = new ArrayList<>(gateButtons.values());
        radios.addAll(formButtons.values());
        return radios;
    }
}
