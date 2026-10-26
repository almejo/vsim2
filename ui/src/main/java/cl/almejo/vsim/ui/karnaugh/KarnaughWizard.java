package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ErrorTextLabel;
import net.miginfocom.swing.MigLayout;

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
import java.awt.Component;
import java.awt.Dimension;
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
 * <p>
 * Its {@link Mode} is fixed when it is created: «Circuito desde tabla de verdad…» (and every handoff) opens the
 * solution, «Practicar Karnaugh…» the practice (spec §2), where step 3 is only the student's groups and nothing shows
 * VSim's answer before «Revisar» or «Me rindo», and going on from step 3 asks while some output is still pending.
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

    /** What the wizard is for; it never changes once the wizard is created. */
    public enum Mode {
        /** «Circuito desde tabla de verdad…»: step 3 shows VSim's groups and explains them. */
        SOLUTION,
        /** «Practicar Karnaugh…»: step 3 is the student's groups; step 4 offers «Usar mis grupos». */
        PRACTICE
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

    /**
     * Asks before leaving step 3 of the practice with outputs still pending («Te falta: G (sin grupos)… Si sigues, te
     * rindes en esas salidas…»); true goes on («Seguir igual»), false stays («Seguir practicando»).
     */
    @FunctionalInterface
    public interface LeaveConfirmation {
        boolean leave(Component parent, String message);

        /** «Seguir practicando» / «Seguir igual», with «Seguir practicando» as the default answer. */
        static LeaveConfirmation dialog() {
            return (parent, message) -> {
                String stay = Messages.text("karnaugh.practice.leave.stay");
                String go = Messages.text("karnaugh.practice.leave.go");
                int answer = JOptionPane.showOptionDialog(parent, message,
                        Messages.text("karnaugh.practice.leave.title"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, new Object[] {stay, go}, stay);
                return answer == 1;
            };
        }
    }

    private static final Dimension DIALOG_SIZE = new Dimension(1000, 680);

    private final KarnaughModel model;
    private final Mode mode;
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
    private final JLabel reason = new ErrorTextLabel(); // why «Siguiente» is off
    private final Action backAction;
    private final Action nextAction;
    private final Action closeAction;
    private final JButton nextButton;
    private final JPanel buttonBar;
    private Runnable onClose = () -> { };
    private DiscardConfirmation discardConfirmation = DiscardConfirmation.dialog();
    private LeaveConfirmation leaveConfirmation = LeaveConfirmation.dialog();
    /** The table as the wizard opened it, or as «Crear» last created it: closing asks only when it differs. */
    private KarnaughModel.Contents unchanged;
    private Step step = Step.TABLE;
    /**
     * Set when the wizard shows the table of «Circuito desde expresiones» (over that dialog): what closes the
     * expressions dialog after a successful «Crear». Null in the normal wizard.
     */
    private Runnable afterCreating;

    /** A solution wizard ({@link Mode#SOLUTION}). */
    public KarnaughWizard(KarnaughModel model, KarnaughCost cost, CreationTarget target, Background background) {
        this(model, cost, target, background, Mode.SOLUTION);
    }

    /**
     * @param target     where «Crear» puts the circuit (and the component)
     * @param background where the circuit is built (off the EDT in the application)
     * @param mode       the solution or the practice, for good
     */
    public KarnaughWizard(KarnaughModel model, KarnaughCost cost, CreationTarget target, Background background,
                          Mode mode) {
        super(new BorderLayout());
        this.model = model;
        this.mode = mode;
        model.practice().setPractising(mode == Mode.PRACTICE);
        this.tablePanel = new TablePanel(model, hover);
        this.mapPanel = new MapPanel(model, hover);
        this.groupsPanel = new GroupsPanel(model, hover);
        this.expressionPanel = new ExpressionPanel(model, cost);
        this.createPanel = new CreatePanel(model, new CreationForm(target), new CircuitCreator(target, background),
                this::closeAfterCreating, this::updateNavigation, () -> step == Step.CREATE);
        this.unchanged = model.contents();
        createPanel.controls().setOnCreated(() -> unchanged = model.contents());
        panels.put(Step.TABLE, tablePanel);
        panels.put(Step.MAP, mapPanel);
        panels.put(Step.GROUPS, groupsPanel);
        panels.put(Step.EXPRESSION, expressionPanel);
        panels.put(Step.CREATE, createPanel);

        backAction = Actions.of(Messages.text("karnaugh.back"), () -> back());
        nextAction = Actions.of(Messages.text("karnaugh.next"), () -> next());
        closeAction = Actions.of(Messages.text("karnaugh.close"), () -> requestClose());

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

    /**
     * «Ver en Karnaugh…» of «Circuito desde expresiones»: the table comes from the expressions, so step 1 shows it
     * read-only with a note, and closing reads «Volver a las expresiones» and never asks (nothing typed here can be
     * lost). A successful «Crear» closes the wizard and then runs {@code closeExpressions}.
     */
    void showTableFromExpressions(Runnable closeExpressions) {
        afterCreating = closeExpressions;
        tablePanel.setReadOnly();
        updateNavigation();
    }

    /** The creation is done (closing after it never asks); over the expressions, they close too. */
    private void closeAfterCreating() {
        onClose.run();
        if (afterCreating != null) {
            afterCreating.run();
        }
    }

    public KarnaughModel model() {
        return model;
    }

    public Mode mode() {
        return mode;
    }

    /** «Circuito desde tabla de verdad» or «Práctica de Karnaugh»: the dialog's title. */
    public String dialogTitle() {
        return Messages.text(mode == Mode.PRACTICE ? "karnaugh.practice.title" : "karnaugh.title");
    }

    public Step step() {
        return step;
    }

    /**
     * Shows {@code target}; refused (false) while the table has a problem and {@code target} is past step 1, and while
     * «Crear» is building the circuit. In the practice, going on from step 3 with outputs still pending asks first:
     * «Seguir igual» gives up on them (for good) and goes on, «Seguir practicando» stays on step 3 showing the first
     * pending output (false).
     */
    public boolean goTo(Step target) {
        if (createPanel.controls().isBusy() || target != Step.TABLE && model.problem().isPresent()) {
            return false;
        }
        if (step == Step.GROUPS && target.ordinal() > Step.GROUPS.ordinal() && !leavePractice()) {
            return false;
        }
        display(target);
        return true;
    }

    /** Whether step 3 may be left: nothing pending, or the student chose «Seguir igual» (giving those outputs up). */
    private boolean leavePractice() {
        PracticeModel practice = model.practice();
        if (!practice.practising()) {
            return true;
        }
        List<Integer> pending = practice.pendingOutputs();
        if (pending.isEmpty()) {
            return true;
        }
        if (leaveConfirmation.leave(this, leaveMessage(pending))) {
            for (int output : pending) {
                practice.giveUp(output, model.options().form());
            }
            return true;
        }
        model.setSelectedOutput(pending.getFirst());
        display(Step.GROUPS);
        return false;
    }

    /** «Te falta: G (sin grupos), H (faltan 2 unos). Si sigues, te rindes en esas salidas…». */
    private String leaveMessage(List<Integer> pending) {
        PracticeModel practice = model.practice();
        Minimization.Form form = model.options().form();
        boolean sumOfProducts = form == Minimization.Form.SUM_OF_PRODUCTS;
        List<String> items = new ArrayList<>();
        for (int output : pending) {
            String name = model.outputNames().get(output).strip();
            if (!practice.practised(output, form)) {
                items.add(Messages.format("karnaugh.practice.leave.noGroups", name));
            } else {
                int uncovered = practice.result(output, form).uncoveredRows().size();
                items.add(Messages.format(sumOfProducts ? "karnaugh.practice.leave.remaining.sop"
                        : "karnaugh.practice.leave.remaining.pos", name, uncovered));
            }
        }
        return Messages.format("karnaugh.practice.leave.message", String.join(", ", items));
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
     * not created since, the user is asked «¿Descartar la tabla?», and «Seguir editando» keeps the wizard open. Over
     * the expressions («Volver a las expresiones») it never asks: the table there cannot be edited.
     */
    public void requestClose() {
        createPanel.controls().cancel();
        if (afterCreating == null && edited() && !discardConfirmation.discard(this)) {
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

    /** What asks before leaving step 3 of the practice with outputs pending; the real one shows a dialog. */
    public void setLeaveConfirmation(LeaveConfirmation confirmation) {
        leaveConfirmation = confirmation;
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
        closeAction.putValue(Action.NAME, Messages.text(busy ? "karnaugh.cancel"
                : afterCreating != null ? "expressions.wizard.back" : "karnaugh.close"));
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
     * usable (and comes to the front); a successful «Crear» closes the wizard, one that failed leaves it open.
     */
    public static void showDialog(Component parent, KarnaughWizard wizard) {
        JDialog dialog = WizardDialogs.create(parent, wizard.dialogTitle(), wizard, wizard::requestClose);
        wizard.setOnClose(dialog::dispose);
        wizard.updateNavigation(); // the default button, now that there is a root pane
        dialog.setSize(DIALOG_SIZE);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
