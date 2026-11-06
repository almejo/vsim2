package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.Font;
import java.util.Set;

/**
 * The content of the tidy dialog: scope, «Partes» (separate overlaps), «Cables» (the three wire options) and the live summary, recomputed on every change. It
 * holds no dialog, so tests drive it directly. The summary is computed in the background («Calculando…» meanwhile);
 * a change cancels the computation in flight, so the latest choice wins.
 */
public final class TidyWiresPanel extends JPanel {

    private final TidyPlanner planner;
    private final JRadioButton selection = new JRadioButton(Messages.text("tidy.scope.selection"));
    private final JRadioButton everything = new JRadioButton(Messages.text("tidy.scope.all"));
    private final JCheckBox separateOverlaps = new JCheckBox(Messages.text("tidy.separateOverlaps"));
    private final JCheckBox removeStubs = new JCheckBox(Messages.text("tidy.removeStubs"));
    private final JCheckBox mergeSegments = new JCheckBox(Messages.text("tidy.mergeSegments"));
    private final JCheckBox flattenDetours = new JCheckBox(Messages.text("tidy.flattenDetours"));
    private final JLabel summary = new JLabel();
    /** Width the summary wraps at, as CSS pixels (Swing draws them about a third wider: ~310 logical pixels). */
    static final int SUMMARY_WIDTH_PIXELS = 240;
    /** The summary as written, without the HTML that makes the label wrap. */
    private String summaryPlainText = "";
    private Runnable changeListener = () -> { };
    private CircuitTidyPlan plan = emptyPlan();
    private boolean ready;
    private Runnable cancelRunning = () -> { };
    private int generation;

    public TidyWiresPanel(boolean hasSelection, CircuitTidyOptions initial, TidyPlanner planner) {
        super(new MigLayout("wrap 1, insets 8", "[grow]", ""));
        this.planner = planner;
        ButtonGroup scope = new ButtonGroup();
        scope.add(selection);
        scope.add(everything);
        selection.setEnabled(hasSelection);
        selection.setSelected(hasSelection);
        everything.setSelected(!hasSelection);
        separateOverlaps.setSelected(initial.separateOverlaps());
        removeStubs.setSelected(initial.wires().removeStubs());
        mergeSegments.setSelected(initial.wires().mergeSegments());
        flattenDetours.setSelected(initial.wires().flattenDetours());
        add(heading("tidy.where"));
        add(selection, "gapleft 12");
        add(everything, "gapleft 12");
        add(heading("tidy.section.parts"), "gaptop 8");
        add(separateOverlaps, "gapleft 12");
        add(heading("tidy.section.wires"), "gaptop 8");
        add(removeStubs, "gapleft 12");
        add(mergeSegments, "gapleft 12");
        add(flattenDetours, "gapleft 12");
        add(summary, "gaptop 12, growx, wmin 280");
        for (javax.swing.AbstractButton button : new javax.swing.AbstractButton[]{selection, everything,
                separateOverlaps, removeStubs, mergeSegments, flattenDetours}) {
            button.addActionListener(event -> refresh());
        }
        refresh();
    }

    /** Called after each recomputation of the summary (the dialog enables «Ordenar» from it). */
    public void onChange(Runnable listener) {
        changeListener = listener;
    }

    public CircuitTidyOptions options() {
        return new CircuitTidyOptions(separateOverlaps.isSelected(),
                new TidyOptions(removeStubs.isSelected(), mergeSegments.isSelected(), flattenDetours.isSelected()));
    }

    private static CircuitTidyPlan emptyPlan() {
        return new CircuitTidyPlan(new CircuitDocument(), 0, 0, Set.of(), TidyPlan.EMPTY);
    }

    private static JLabel heading(String key) {
        JLabel label = new JLabel(Messages.text(key));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    public boolean selectionOnly() {
        return selection.isSelected();
    }

    public TidyChoice choice() {
        return new TidyChoice(options(), selectionOnly());
    }

    /** The plan behind the summary. */
    public CircuitTidyPlan plan() {
        return plan;
    }

    public String summaryText() {
        return summaryPlainText;
    }

    /** True when there is something to tidy with the current choices. */
    public boolean canApply() {
        return ready && !plan.isEmpty();
    }

    /** False while the summary is being computed. */
    public boolean isReady() {
        return ready;
    }

    /** Stops the computation in flight (the dialog is closing); nothing is delivered afterwards. */
    public void dispose() {
        generation++;
        cancelRunning.run();
        cancelRunning = () -> { };
    }

    JCheckBox separateOverlapsBox() {
        return separateOverlaps;
    }

    JCheckBox removeStubsBox() {
        return removeStubs;
    }

    JCheckBox mergeSegmentsBox() {
        return mergeSegments;
    }

    JRadioButton selectionButton() {
        return selection;
    }

    JRadioButton everythingButton() {
        return everything;
    }

    /**
     * Shows {@code text} wrapped to the summary's width (a long summary is never cut with «…»): HTML with a fixed
     * body width, so the dialog's pack gives it the lines it needs.
     */
    void showSummary(String text) {
        summaryPlainText = text;
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        summary.setText("<html><body style='width: " + SUMMARY_WIDTH_PIXELS + "px'>" + escaped + "</body></html>");
    }

    private void refresh() {
        cancelRunning.run();
        int mine = ++generation;
        ready = false;
        plan = emptyPlan();
        showSummary(Messages.text("tidy.calculating"));
        changeListener.run();
        Runnable cancel = planner.plan(options(), selectionOnly(), result -> {
            if (mine != generation) {
                return;
            }
            plan = result;
            ready = true;
            cancelRunning = () -> { };
            showSummary(TidySummary.preview(plan));
            changeListener.run();
        });
        if (mine == generation && !ready) {
            cancelRunning = cancel;
        }
    }
}
