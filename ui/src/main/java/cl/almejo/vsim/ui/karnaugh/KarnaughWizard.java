package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The «Circuito desde tabla de verdad» wizard (spec §3): a step bar («1 Tabla · 2 Mapa · 3 Grupos · 4 Expresión ·
 * 5 Crear»), the step panels, and «Atrás» · «Siguiente» · «Crear» · «Cerrar». Every step derives from one
 * {@link KarnaughModel}, so going back and changing anything recomputes the later steps. While the table has a
 * problem (a blank or repeated name) only step 1 is reachable and the reason shows next to «Siguiente». Closing a
 * table edited since it opened (or since «Crear» last made it) asks first.
 * <p>
 * The wizard is a panel; {@link #showDialog} puts it in a modal dialog, and the menu item reaches it through an
 * {@link Opener} so tests never show it.
 */
public final class KarnaughWizard extends JPanel {

    public enum Step {
        TABLE("karnaugh.step.table"),
        MAP("karnaugh.step.map"),
        GROUPS("karnaugh.step.groups"),
        EXPRESSION("karnaugh.step.expression"),
        CREATE("karnaugh.step.create");

        private final String titleKey;

        Step(String titleKey) {
            this.titleKey = titleKey;
        }

        public String title() {
            return Messages.text(titleKey);
        }
    }

    /** Shows a wizard to the user; the real one opens {@link #showDialog}. */
    @FunctionalInterface
    public interface Opener {
        void open(Component parent, KarnaughWizard wizard);

        static Opener dialog() {
            return KarnaughWizard::showDialog;
        }
    }

    /** Asks «¿Descartar la tabla?» before closing a wizard whose table was edited; true discards it. */
    @FunctionalInterface
    public interface DiscardConfirmation {
        boolean discard(Component parent);

        /** «Descartar» / «Seguir editando», with «Seguir editando» as the default answer. */
        static DiscardConfirmation dialog() {
            return parent -> {
                String discard = Messages.text("karnaugh.discard.discard");
                String keep = Messages.text("karnaugh.discard.keep");
                int answer = JOptionPane.showOptionDialog(parent, Messages.text("karnaugh.discard.message"),
                        Messages.text("karnaugh.discard.title"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, new Object[] {discard, keep}, keep);
                return answer == 0;
            };
        }
    }

    private static final Dimension DIALOG_SIZE = new Dimension(1000, 680);

    private final KarnaughModel model;
    private final HoverState hover = new HoverState();
    private final TablePanel tablePanel;
    private final MapPanel mapPanel;
    private final GroupsPanel groupsPanel;
    private final ExpressionPanel expressionPanel;
    private final CreatePanel createPanel;
    private final Map<Step, Component> panels = new EnumMap<>(Step.class);
    private final Map<Step, JToggleButton> stepButtons = new EnumMap<>(Step.class);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel reason = new ErrorLabel();
    private final Action backAction;
    private final Action nextAction;
    private final Action closeAction;
    private final JButton nextButton;
    private final JPanel buttonBar;
    private Runnable onClose = () -> { };
    private DiscardConfirmation discardConfirmation = DiscardConfirmation.dialog();
    /** The table as the wizard opened it, or as «Crear» last created it: closing asks only when it differs. */
    private KarnaughModel.Contents unchanged;
    private Step step = Step.TABLE;

    /**
     * @param target     where «Crear» puts the circuit (and the component)
     * @param background where the circuit is built (off the EDT in the application)
     */
    public KarnaughWizard(KarnaughModel model, KarnaughCost cost, CreationTarget target, Background background) {
        super(new BorderLayout());
        this.model = model;
        this.tablePanel = new TablePanel(model, hover);
        this.mapPanel = new MapPanel(model, hover);
        this.groupsPanel = new GroupsPanel(model, hover);
        this.expressionPanel = new ExpressionPanel(model, cost);
        this.createPanel = new CreatePanel(model, new CreationForm(target), new CircuitCreator(target, background),
                () -> onClose.run(), this::updateNavigation, () -> step == Step.CREATE);
        this.unchanged = model.contents();
        createPanel.controls().setOnCreated(() -> unchanged = model.contents());
        panels.put(Step.TABLE, tablePanel);
        panels.put(Step.MAP, mapPanel);
        panels.put(Step.GROUPS, groupsPanel);
        panels.put(Step.EXPRESSION, expressionPanel);
        panels.put(Step.CREATE, createPanel);

        backAction = new AbstractAction(Messages.text("karnaugh.back")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                back();
            }
        };
        nextAction = new AbstractAction(Messages.text("karnaugh.next")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                next();
            }
        };
        closeAction = new AbstractAction(Messages.text("karnaugh.close")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                requestClose();
            }
        };

        JPanel bar = new JPanel(new MigLayout("insets 8 12 4 12, gap 4"));
        ButtonGroup barGroup = new ButtonGroup();
        for (Step each : Step.values()) {
            JToggleButton button = new JToggleButton(each.title());
            button.addActionListener(event -> {
                if (!goTo(each)) {
                    stepButtons.get(step).setSelected(true);
                }
            });
            barGroup.add(button);
            bar.add(button);
            stepButtons.put(each, button);
            content.add(panels.get(each), each.name());
        }
        buttonBar = new JPanel(new MigLayout("insets 6 12 10 12, fillx", "[grow, fill][][][][]"));
        buttonBar.add(reason);
        buttonBar.add(new JButton(backAction));
        nextButton = new JButton(nextAction);
        buttonBar.add(nextButton);
        buttonBar.add(createPanel.controls().createButton());
        buttonBar.add(new JButton(closeAction), "gapleft 12");

        add(bar, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(buttonBar, BorderLayout.SOUTH);
        model.addListener(this::modelChanged);
        display(Step.TABLE);
    }

    public KarnaughModel model() {
        return model;
    }

    public Step step() {
        return step;
    }

    /**
     * Shows {@code target}; refused (false) while the table has a problem and {@code target} is past step 1, and while
     * «Crear» is building the circuit.
     */
    public boolean goTo(Step target) {
        if (createPanel.controls().isBusy() || target != Step.TABLE && model.problem().isPresent()) {
            return false;
        }
        display(target);
        return true;
    }

    public void next() {
        if (step.ordinal() < Step.values().length - 1) {
            goTo(Step.values()[step.ordinal() + 1]);
        }
    }

    public void back() {
        if (step.ordinal() > 0) {
            goTo(Step.values()[step.ordinal() - 1]);
        }
    }

    /**
     * Every way of closing the wizard («Cerrar», which reads «Cancelar» while «Crear» is building, Escape and the
     * window's close box) comes here: a creation that is running is cancelled; then, when the table was edited and
     * not created since, the user is asked «¿Descartar la tabla?», and «Seguir editando» keeps the wizard open.
     */
    public void requestClose() {
        createPanel.controls().cancel();
        if (edited() && !discardConfirmation.discard(this)) {
            return;
        }
        onClose.run();
    }

    /**
     * Whether any cell, name, count or the title differs from how the wizard opened (a table computed by «Tabla de
     * verdad…» counts as the start) or from the table «Crear» last created.
     */
    boolean edited() {
        return !model.contents().equals(unchanged);
    }

    /** What asks «¿Descartar la tabla?»; the real one shows a dialog, tests answer it. */
    public void setDiscardConfirmation(DiscardConfirmation confirmation) {
        discardConfirmation = confirmation;
    }

    /** What closing does once the creation is cancelled (the dialog disposes itself). */
    public void setOnClose(Runnable action) {
        onClose = action;
    }

    private void display(Step target) {
        hover.setRow(-1); // a row lit on another step's map would stay lit here
        step = target;
        cards.show(content, target.name());
        stepButtons.get(target).setSelected(true);
        refreshStep();
        updateNavigation();
    }

    private void modelChanged() {
        refreshStep();
        updateNavigation();
    }

    private void refreshStep() {
        if (panels.get(step) instanceof WizardStep wizardStep && model.problem().isEmpty()) {
            wizardStep.refresh();
        }
    }

    /**
     * While «Crear» builds the circuit every button is off (the table must not change under it) except «Cerrar»,
     * which then reads «Cancelar». «Crear» is on only on step 5 (its controls decide the rest); there it is the
     * default button (Enter), and «Siguiente» is on steps 1 to 4.
     */
    void updateNavigation() {
        Optional<String> problem = model.problem();
        boolean busy = createPanel != null && createPanel.controls().isBusy();
        reason.setText(problem.orElse(""));
        backAction.setEnabled(!busy && step.ordinal() > 0);
        nextAction.setEnabled(!busy && step.ordinal() < Step.values().length - 1 && problem.isEmpty());
        closeAction.putValue(Action.NAME, Messages.text(busy ? "karnaugh.cancel" : "karnaugh.close"));
        for (Map.Entry<Step, JToggleButton> entry : stepButtons.entrySet()) {
            entry.getValue().setEnabled(!busy && (entry.getKey() == Step.TABLE || problem.isEmpty()));
        }
        if (createPanel != null) {
            createPanel.controls().refresh();
            JRootPane root = getRootPane();
            if (root != null) {
                root.setDefaultButton(step == Step.CREATE ? createPanel.controls().createButton() : nextButton);
            }
        }
    }

    /** «Crear», in the button bar; on only on step 5 with valid names and nothing being built. */
    JButton createButton() {
        return createPanel.controls().createButton();
    }

    JButton nextButton() {
        return nextButton;
    }

    List<String> buttonBarTexts() {
        List<String> texts = new ArrayList<>();
        for (Component each : buttonBar.getComponents()) {
            if (each instanceof AbstractButton button) {
                texts.add(button.getText());
            }
        }
        return texts;
    }

    TablePanel tablePanel() {
        return tablePanel;
    }

    MapPanel mapPanel() {
        return mapPanel;
    }

    GroupsPanel groupsPanel() {
        return groupsPanel;
    }

    ExpressionPanel expressionPanel() {
        return expressionPanel;
    }

    /** Step 5 «Crear». */
    CreatePanel createPanel() {
        return createPanel;
    }

    HoverState hover() {
        return hover;
    }

    Action backAction() {
        return backAction;
    }

    Action nextAction() {
        return nextAction;
    }

    Action closeAction() {
        return closeAction;
    }

    String reasonText() {
        return reason.getText();
    }

    AbstractButton stepButton(Step target) {
        return stepButtons.get(target);
    }

    List<String> stepTitles() {
        List<String> titles = new ArrayList<>();
        for (Step each : Step.values()) {
            titles.add(stepButtons.get(each).getText());
        }
        return titles;
    }

    /**
     * Shows {@code wizard} in a large dialog over {@code parent}; «Cerrar», Escape and the close box all go through
     * {@link #requestClose()}. The dialog blocks only its own window, so a «Circuito nuevo» window opened from it is
     * usable (and comes to the front) while the wizard stays open to try other options.
     */
    public static void showDialog(Component parent, KarnaughWizard wizard) {
        JDialog dialog = WizardDialogs.create(parent, Messages.text("karnaugh.title"), wizard, wizard::requestClose);
        wizard.setOnClose(dialog::dispose);
        wizard.updateNavigation(); // the default button, now that there is a root pane
        dialog.setSize(DIALOG_SIZE);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /** The reason «Siguiente» is off, in the theme's error colour (read at paint time). */
    private static final class ErrorLabel extends JLabel {
        @Override
        public Color getForeground() {
            return WizardColors.error();
        }
    }
}
