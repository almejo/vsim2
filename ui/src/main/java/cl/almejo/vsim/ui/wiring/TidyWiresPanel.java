package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.util.function.BiFunction;

/**
 * The content of the tidy dialog: scope, the three options and the live summary, recomputed on every change. It
 * holds no dialog, so tests drive it directly.
 */
public final class TidyWiresPanel extends JPanel {

    private final BiFunction<TidyOptions, Boolean, TidyPlan> planner;
    private final JRadioButton selection = new JRadioButton(Messages.text("tidy.scope.selection"));
    private final JRadioButton everything = new JRadioButton(Messages.text("tidy.scope.all"));
    private final JCheckBox removeStubs = new JCheckBox(Messages.text("tidy.removeStubs"));
    private final JCheckBox mergeSegments = new JCheckBox(Messages.text("tidy.mergeSegments"));
    private final JCheckBox flattenDetours = new JCheckBox(Messages.text("tidy.flattenDetours"));
    private final JLabel summary = new JLabel();
    private Runnable changeListener = () -> { };
    private TidyPlan plan = TidyPlan.EMPTY;

    public TidyWiresPanel(boolean hasSelection, TidyOptions initial,
                          BiFunction<TidyOptions, Boolean, TidyPlan> planner) {
        super(new MigLayout("wrap 1, insets 8", "[grow]", ""));
        this.planner = planner;
        ButtonGroup scope = new ButtonGroup();
        scope.add(selection);
        scope.add(everything);
        selection.setEnabled(hasSelection);
        selection.setSelected(hasSelection);
        everything.setSelected(!hasSelection);
        removeStubs.setSelected(initial.removeStubs());
        mergeSegments.setSelected(initial.mergeSegments());
        flattenDetours.setSelected(initial.flattenDetours());
        add(new JLabel(Messages.text("tidy.where")));
        add(selection, "gapleft 12");
        add(everything, "gapleft 12");
        add(removeStubs, "gaptop 8");
        add(mergeSegments);
        add(flattenDetours);
        add(summary, "gaptop 12, growx, wmin 280");
        for (javax.swing.AbstractButton button : new javax.swing.AbstractButton[]{selection, everything,
                removeStubs, mergeSegments, flattenDetours}) {
            button.addActionListener(event -> refresh());
        }
        refresh();
    }

    /** Called after each recomputation of the summary (the dialog enables «Ordenar» from it). */
    public void onChange(Runnable listener) {
        changeListener = listener;
    }

    public TidyOptions options() {
        return new TidyOptions(removeStubs.isSelected(), mergeSegments.isSelected(), flattenDetours.isSelected());
    }

    public boolean selectionOnly() {
        return selection.isSelected();
    }

    public TidyChoice choice() {
        return new TidyChoice(options(), selectionOnly());
    }

    /** The plan behind the summary. */
    public TidyPlan plan() {
        return plan;
    }

    public String summaryText() {
        return summary.getText();
    }

    /** True when there is something to tidy with the current choices. */
    public boolean canApply() {
        return !plan.isEmpty();
    }

    JCheckBox removeStubsBox() {
        return removeStubs;
    }

    JCheckBox mergeSegmentsBox() {
        return mergeSegments;
    }

    JCheckBox flattenDetoursBox() {
        return flattenDetours;
    }

    JRadioButton selectionButton() {
        return selection;
    }

    JRadioButton everythingButton() {
        return everything;
    }

    private void refresh() {
        plan = planner.apply(options(), selectionOnly());
        summary.setText(TidySummary.preview(plan));
        changeListener.run();
    }
}
