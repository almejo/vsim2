package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The «Circuito desde tabla de verdad» wizard (spec §3): a step bar («1 Tabla · 2 Mapa · 3 Grupos · 4 Expresión ·
 * 5 Crear»), the step panels, and «Atrás» / «Siguiente». Every step derives from one {@link KarnaughModel}, so going
 * back and changing anything recomputes the later steps. While the table has a problem (a blank or repeated name)
 * only step 1 is reachable and the reason shows next to «Siguiente».
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

    private static final Dimension DIALOG_SIZE = new Dimension(1000, 680);

    private final KarnaughModel model;
    private final HoverState hover = new HoverState();
    private final TablePanel tablePanel;
    private final MapPanel mapPanel;
    private final GroupsPanel groupsPanel;
    private final ExpressionPanel expressionPanel;
    private final JPanel createPanel;
    private final Map<Step, Component> panels = new EnumMap<>(Step.class);
    private final Map<Step, JToggleButton> stepButtons = new EnumMap<>(Step.class);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel reason = new ErrorLabel();
    private final Action backAction;
    private final Action nextAction;
    private final Action closeAction;
    private Runnable onClose = () -> { };
    private Step step = Step.TABLE;

    public KarnaughWizard(KarnaughModel model, KarnaughCost cost) {
        super(new BorderLayout());
        this.model = model;
        this.tablePanel = new TablePanel(model, hover);
        this.mapPanel = new MapPanel(model, hover);
        this.groupsPanel = new GroupsPanel(model, hover);
        this.expressionPanel = new ExpressionPanel(model, cost);
        // Step 5 «Crear» comes with Task 6; until then it only says so.
        this.createPanel = new JPanel(new MigLayout("insets 24, fillx", "[grow, fill]"));
        createPanel.add(new Paragraph(Messages.text("karnaugh.create.placeholder")), "wmin 100");
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
                onClose.run();
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
        JPanel buttons = new JPanel(new MigLayout("insets 6 12 10 12, fillx", "[grow, fill][][][]"));
        buttons.add(reason);
        buttons.add(new JButton(backAction));
        JButton nextButton = new JButton(nextAction);
        buttons.add(nextButton);
        buttons.add(new JButton(closeAction), "gapleft 12");

        add(bar, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
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
     * Shows {@code target}; refused (false) while the table has a problem and {@code target} is past step 1.
     */
    public boolean goTo(Step target) {
        if (target != Step.TABLE && model.problem().isPresent()) {
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

    /** What «Cerrar» does (the dialog closes itself). */
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

    private void updateNavigation() {
        Optional<String> problem = model.problem();
        reason.setText(problem.orElse(""));
        backAction.setEnabled(step.ordinal() > 0);
        nextAction.setEnabled(step.ordinal() < Step.values().length - 1 && problem.isEmpty());
        for (Map.Entry<Step, JToggleButton> entry : stepButtons.entrySet()) {
            entry.getValue().setEnabled(entry.getKey() == Step.TABLE || problem.isEmpty());
        }
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

    /** Step 5's panel, a placeholder until Task 6 fills it. */
    JPanel createPanel() {
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

    /** Shows {@code wizard} in a large modal dialog over {@code parent}; «Cerrar» and Escape close it. */
    public static void showDialog(Component parent, KarnaughWizard wizard) {
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent),
                Messages.text("karnaugh.title"), JDialog.DEFAULT_MODALITY_TYPE);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        wizard.setOnClose(dialog::dispose);
        dialog.setContentPane(wizard);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
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
