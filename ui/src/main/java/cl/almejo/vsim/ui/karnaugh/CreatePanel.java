package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JPanel;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Step 5 «Crear» (spec §3): a short explanation and the {@link CreationControls} for the wizard's table and options;
 * the «Crear» button itself is in the wizard's button bar. The component's name follows the table's title until the
 * user types one.
 */
final class CreatePanel extends JPanel implements WizardStep {

    private final KarnaughModel model;
    private final CreationForm form;
    private final CreationControls controls;

    CreatePanel(KarnaughModel model, CreationForm form, CircuitCreator creator, Runnable closeDialog,
                Runnable busyChanged, BooleanSupplier available) {
        super(new MigLayout("insets 24, fillx, wrap 1", "[grow, fill]", "[]16[]"));
        this.model = model;
        this.form = form;
        this.controls = new CreationControls(form, creator,
                () -> model.problem().isEmpty() ? Optional.of(model.spec()) : Optional.empty(),
                model::options, closeDialog, busyChanged, available);
        add(new Paragraph(Messages.text("karnaugh.create.intro")), "wmin 100");
        add(controls);
    }

    @Override
    public void refresh() {
        form.proposeName(model.title());
        controls.refresh();
    }

    CreationForm form() {
        return form;
    }

    CreationControls controls() {
        return controls;
    }
}
