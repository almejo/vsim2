package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ExpressionLine;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The «Crear» part shared by the wizard's step 5 and the import dialog (spec §3 step 5, §6): «Destino» (circuito
 * nuevo / insertar en el circuito actual), «Crear también como componente» with its name, a busy indicator while the
 * circuit is built off the EDT, and the outcome («✓ El circuito cumple la tabla»). The «Crear» button belongs to these
 * controls (they decide when it is on) but its owner puts it in the dialog's button bar, next to «Cerrar»; owners hear
 * every {@link #refresh()} to follow its state (the default button). Everything is disabled while busy. After a
 * successful creation (the check passed and the component, if any, was saved) the dialog closes without asking, and
 * the outcome goes to the status bar of the window that got the circuit (with the drop hint after an «Insertar»);
 * when something failed the dialog stays open showing it. Closing the dialog while busy {@linkplain #cancel() cancels} the creation.
 */
final class CreationControls extends JPanel {

    private final CreationForm form;
    private final CircuitCreator creator;
    private final Supplier<Optional<TruthTableSpec>> spec;
    private final Supplier<WizardOptions> options;
    private final Runnable closeDialog;
    private final Runnable busyChanged;
    private final BooleanSupplier available;
    private final List<Runnable> refreshListeners = new ArrayList<>();
    private Runnable created = () -> { };
    private Supplier<Optional<List<ExpressionLine>>> asWritten = Optional::empty;
    private Supplier<Optional<Minimization>> minimization = Optional::empty;
    private final Map<CreationForm.Destination, JRadioButton> destinationButtons =
            new EnumMap<>(CreationForm.Destination.class);
    private final JCheckBox component = new JCheckBox(Messages.text("karnaugh.create.alsoComponent"));
    private final JLabel nameLabel = new JLabel(Messages.text("karnaugh.create.componentName"));
    private final JTextField name = new JTextField(20);
    private final JLabel reason = new JLabel(" ") {
        @Override
        public Color getForeground() {
            return WizardColors.error();
        }
    };
    private final JButton create = new JButton(Messages.text("karnaugh.create.create"));
    private final JProgressBar progress = new JProgressBar();
    private final JLabel busyLabel = new JLabel(Messages.text("karnaugh.create.busy"));
    private final Paragraph result = new Paragraph("");
    private boolean syncing;

    /**
     * @param spec        the table to create, empty while it has problems (then «Crear» is off)
     * @param closeDialog closes the dialog (after a successful creation)
     * @param busyChanged told when a creation starts or ends ({@link #isBusy()}), so the owner disables its buttons
     * @param available   whether the owner lets «Crear» run now (the wizard's last step; no file being read)
     */
    CreationControls(CreationForm form, CircuitCreator creator, Supplier<Optional<TruthTableSpec>> spec,
                     Supplier<WizardOptions> options, Runnable closeDialog, Runnable busyChanged,
                     BooleanSupplier available) {
        super(new MigLayout("insets 0, fillx, wrap 2", "[][grow, fill]", "[][][][]12[]"));
        this.form = form;
        this.creator = creator;
        this.spec = spec;
        this.options = options;
        this.closeDialog = closeDialog;
        this.busyChanged = busyChanged;
        this.available = available;

        ButtonGroup destinations = new ButtonGroup();
        JPanel destinationPanel = new JPanel(new MigLayout("insets 0"));
        addDestination(destinations, destinationPanel, CreationForm.Destination.NEW_CIRCUIT,
                "karnaugh.create.newCircuit");
        addDestination(destinations, destinationPanel, CreationForm.Destination.INSERT, "karnaugh.create.insert");
        component.addActionListener(event -> form.setAlsoComponent(component.isSelected()));
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                typed();
            }
        });
        create.addActionListener(event -> create());
        progress.setIndeterminate(true);

        add(new JLabel(Messages.text("karnaugh.create.destination")));
        add(destinationPanel);
        add(component, "span 2");
        add(nameLabel, "gapleft 24");
        add(name, "growx 0, wmin 160");
        add(reason, "span 2");
        JPanel action = new JPanel(new MigLayout("insets 0", "[]8[]"));
        action.add(progress, "hidemode 3, wmax 120");
        action.add(busyLabel, "hidemode 3");
        add(action, "span 2");
        add(result, "span 2, growx, wmin 100");
        form.addListener(this::refresh);
        refresh();
    }

    private void addDestination(ButtonGroup group, JPanel panel, CreationForm.Destination destination, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> form.setDestination(destination));
        group.add(button);
        panel.add(button);
        destinationButtons.put(destination, button);
    }

    private void typed() {
        if (!syncing) {
            form.setComponentName(name.getText());
        }
    }

    /** Shows the form and the table as they are now (call when the table may have changed), then tells the owner. */
    void refresh() {
        boolean busy = creator.isBusy();
        syncing = true;
        try {
            if (!name.getText().equals(form.componentName())) {
                name.setText(form.componentName());
            }
        } finally {
            syncing = false;
        }
        for (Map.Entry<CreationForm.Destination, JRadioButton> entry : destinationButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == form.destination());
            entry.getValue().setEnabled(!busy && (entry.getKey() != CreationForm.Destination.INSERT
                    || form.target().canInsert()));
        }
        component.setSelected(form.alsoComponent());
        component.setEnabled(!busy);
        nameLabel.setEnabled(!busy && form.alsoComponent());
        name.setEnabled(!busy && form.alsoComponent());
        Optional<String> problem = form.problem();
        reason.setText(problem.orElse(" "));
        create.setEnabled(canCreate());
        progress.setVisible(busy);
        busyLabel.setVisible(busy);
        for (Runnable listener : List.copyOf(refreshListeners)) {
            listener.run();
        }
    }

    /** Whether «Crear» would run now: the owner allows it, nothing is being built and every name is valid. */
    boolean canCreate() {
        return available.getAsBoolean() && !creator.isBusy() && form.problem().isEmpty() && spec.get().isPresent();
    }

    /** Runs after every {@link #refresh()}; it must not call {@code refresh} itself. */
    void addRefreshListener(Runnable listener) {
        refreshListeners.add(listener);
    }

    /** Told when a circuit was created (opened or floated), before a successful creation closes the dialog. */
    void setOnCreated(Runnable action) {
        created = action;
    }

    /** The lines to build as written (when present) instead of minimizing the table; by default it is never. */
    void setAsWritten(Supplier<Optional<List<ExpressionLine>>> lines) {
        asWritten = lines;
    }

    /** The terms to build (when present) instead of minimizing the table: the student's groups; by default never. */
    void setMinimization(Supplier<Optional<Minimization>> terms) {
        minimization = terms;
    }

    boolean isBusy() {
        return creator.isBusy();
    }

    /** Drops the creation that is running, if any (the dialog is closing): its result will be ignored. */
    void cancel() {
        if (creator.isBusy()) {
            creator.cancel();
            refresh();
            busyChanged.run();
        }
    }

    /** «Crear»: builds the circuit off the EDT; the controls are off until it is done. */
    void create() {
        Optional<TruthTableSpec> table = spec.get();
        if (!canCreate() || table.isEmpty()) {
            return;
        }
        CircuitCreator.Request request = new CircuitCreator.Request(table.get(), options.get(), form.destination(),
                form.wantedComponent(), asWritten.get(), minimization.get());
        result.setText("");
        creator.create(request, this::finished);
        if (creator.isBusy()) {
            refresh();
            busyChanged.run();
        }
    }

    private void finished(CircuitCreator.Outcome outcome) {
        result.setText(outcome.text());
        refresh();
        if (outcome.created()) {
            created.run();
        }
        busyChanged.run();
        if (outcome.succeeded()) {
            // The work is done: the outcome goes to the window that got the circuit (a new circuit's own status bar
            // got it when it opened) and the dialog closes. A failed check or component stays here to be read.
            if (form.destination() == CreationForm.Destination.INSERT) {
                form.target().notice(outcome.text() + " " + Messages.text("karnaugh.create.dropHint"));
            }
            closeDialog.run();
        }
    }

    AbstractButton destinationButton(CreationForm.Destination destination) {
        return destinationButtons.get(destination);
    }

    JCheckBox componentCheckBox() {
        return component;
    }

    JTextField nameField() {
        return name;
    }

    /** «Crear», which the owner puts in its button bar. */
    JButton createButton() {
        return create;
    }

    String reasonText() {
        return reason.getText().strip();
    }

    String resultText() {
        return result.getText();
    }

    boolean busyIndicatorShown() {
        return progress.isVisible();
    }
}
