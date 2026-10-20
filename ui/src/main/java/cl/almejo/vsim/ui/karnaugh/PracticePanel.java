package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.GroupCheck;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization.Form;
import cl.almejo.vsim.core.logic.PracticeResult;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The right side of step 3 while practising (spec §2): how to draw, «La tabla cambió…», one legend line per student
 * group with its verdict (red: not a group; amber: can grow or not needed; green: its term), what is left to group,
 * «Pista», «Me rindo · Ver la solución», «Revisar» with the grade and VSim's solution in a small map, and «Borrar
 * todos». Nothing shows VSim's answer before «Revisar» or «Me rindo» (which asks first); either one reveals the output
 * ({@link PracticeModel#reveal}). «Revisar» shows the small map until the groups, the output or the form change; «Me
 * rindo» gives up on the map for good ({@link PracticeModel#giveUp}): the small map stays, the student's map is
 * read-only and every button here is off with the reason.
 * Everything is read from the model's {@link PracticeModel} on each {@link #refresh()}; a click on a legend line
 * selects that group on the map.
 */
final class PracticePanel extends JPanel {

    private static final String CIRCLED_DIGITS = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯";
    /** «Borrar todos» asks first from this many groups on. */
    private static final int CONFIRMED_CLEAR = 3;

    /** Asks «¿Borrar los 3 grupos de F?»; true clears them. The real one shows a dialog, tests answer it. */
    @FunctionalInterface
    interface ClearConfirmation {
        boolean clear(Component parent, int count, String outputName);

        static ClearConfirmation dialog() {
            return (parent, count, outputName) -> {
                String clear = Messages.text("karnaugh.practice.clearAll.confirm");
                String keep = Messages.text("karnaugh.practice.clearAll.keep");
                int answer = JOptionPane.showOptionDialog(parent,
                        Messages.format("karnaugh.practice.clearAll.message", count, outputName),
                        Messages.text("karnaugh.practice.clearAll.title"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, new Object[] {clear, keep}, keep);
                return answer == 0;
            };
        }
    }

    /** Asks «¿Rendirte en F y ver la solución de VSim?» before «Me rindo» gives up; true gives up. Tests answer it. */
    @FunctionalInterface
    interface GiveUpConfirmation {
        boolean giveUp(Component parent, String outputName);

        /** «Me rindo» / «Seguir intentando», with «Seguir intentando» as the default answer. */
        static GiveUpConfirmation dialog() {
            return (parent, outputName) -> {
                String show = Messages.text("karnaugh.practice.giveUp.confirm");
                String keep = Messages.text("karnaugh.practice.giveUp.keep");
                int answer = JOptionPane.showOptionDialog(parent,
                        Messages.format("karnaugh.practice.giveUp.message", outputName),
                        Messages.text("karnaugh.practice.giveUp.title"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, new Object[] {show, keep}, keep);
                return answer == 0;
            };
        }
    }

    /** A cover graded by «Revisar»: the grade and VSim's map show while the output, the form and the groups stay. */
    private record Review(int output, Form form, List<DrawnGroup> groups) {

        boolean sameCover(int otherOutput, Form otherForm, List<DrawnGroup> otherGroups) {
            return output == otherOutput && form == otherForm && groups.equals(otherGroups);
        }
    }

    private final KarnaughModel model;
    private final Runnable focusMap;
    private final Paragraph header = new Paragraph("");
    private final Paragraph specialCase = new Paragraph("", Font.BOLD);
    private final Paragraph notice = new ColouredParagraph(WizardColors::warning);
    private final Paragraph givenUpNote = new ColouredParagraph(WizardColors::muted);
    private final Paragraph full = new ColouredParagraph(WizardColors::warning);
    private final Paragraph empty = new ColouredParagraph(WizardColors::muted);
    private final JPanel legend = new JPanel(new MigLayout("insets 0, gap 2, wrap 1"));
    private final List<LegendLine> lines = new ArrayList<>();
    private final Paragraph counter = new Paragraph("", Font.BOLD);
    private final JButton hintButton = new JButton(Messages.text("karnaugh.practice.hint"));
    private final Paragraph hint = new Paragraph("");
    private final JLabel hintsUsed = new MutedLabel();
    private final JButton giveUpButton = new JButton(Messages.text("karnaugh.practice.giveUp"));
    private final JButton reviewButton = new JButton(Messages.text("karnaugh.practice.review"));
    private final JButton clearAllButton = new JButton(Messages.text("karnaugh.practice.clearAll"));
    private final Paragraph reviewText = new ColouredParagraph(this::reviewColor);
    private final JLabel solutionTitle = new JLabel(Messages.text("karnaugh.practice.solution"));
    private final JPanel solutionHolder = new JPanel(new MigLayout("insets 0"));
    private KarnaughMapView solutionMap;
    private ClearConfirmation clearConfirmation = ClearConfirmation.dialog();
    private GiveUpConfirmation giveUpConfirmation = GiveUpConfirmation.dialog();
    private Review review;
    private Supplier<Color> reviewColour = WizardColors::foreground;

    /** @param focusMap gives the keyboard to the shown map (after a legend click, so Supr deletes the group) */
    PracticePanel(KarnaughModel model, Runnable focusMap) {
        super(new MigLayout("insets 0, fillx, wrap 1, hidemode 3", "[grow, fill]"));
        this.model = model;
        this.focusMap = focusMap;
        hintButton.addActionListener(event -> model.practice().pressHint());
        reviewButton.addActionListener(event -> review());
        giveUpButton.addActionListener(event -> giveUp());
        clearAllButton.addActionListener(event -> clearAll());
        solutionTitle.setFont(solutionTitle.getFont().deriveFont(Font.BOLD));
        reviewText.setFont(reviewText.getFont().deriveFont(Font.BOLD));

        JPanel hintRow = new JPanel(new MigLayout("insets 0", "[][grow][]"));
        hintRow.add(hintButton);
        hintRow.add(hintsUsed);
        hintRow.add(giveUpButton);
        JPanel reviewRow = new JPanel(new MigLayout("insets 0", "[][grow][]"));
        reviewRow.add(reviewButton);
        reviewRow.add(new JLabel(), "growx");
        reviewRow.add(clearAllButton);

        add(header, "wmin 100");
        add(notice, "gaptop 6, wmin 100");
        add(givenUpNote, "gaptop 6, wmin 100");
        add(specialCase, "gaptop 6, wmin 100");
        add(legend, "gaptop 6");
        add(empty, "wmin 100");
        add(full, "wmin 100");
        add(counter, "gaptop 8, wmin 100");
        add(hintRow, "gaptop 8");
        add(hint, "wmin 100");
        add(reviewRow, "gaptop 8");
        add(reviewText, "gaptop 4, wmin 100");
        add(solutionTitle, "gaptop 8");
        add(solutionHolder, "growx 0");
    }

    /** What asks before «Me rindo» shows VSim's solution. */
    void setGiveUpConfirmation(GiveUpConfirmation confirmation) {
        giveUpConfirmation = confirmation;
    }

    /** What asks before «Borrar todos» clears 3 or more groups. */
    void setClearConfirmation(ClearConfirmation confirmation) {
        clearConfirmation = confirmation;
    }

    void refresh() {
        PracticeModel practice = model.practice();
        int output = model.selectedOutput();
        Form form = model.options().form();
        boolean sumOfProducts = form == Form.SUM_OF_PRODUCTS;
        TruthTableSpec spec = model.spec();
        List<DrawnGroup> groups = practice.groups(output, form);
        List<GroupCheck.Result> verdicts = practice.verdicts(output, form);
        PracticeResult result = practice.result(output, form);

        header.setText(Messages.text(sumOfProducts ? "karnaugh.practice.header.sop" : "karnaugh.practice.header.pos"));
        notice.setText(practice.restarted() ? Messages.text("karnaugh.practice.tableChanged") : "");
        notice.setVisible(practice.restarted());
        boolean givenUp = practice.givenUp(output, form);
        String givenUpReason = givenUp
                ? Messages.format("karnaugh.practice.givenUp", model.outputNames().get(output).strip()) : null;
        givenUpNote.setText(givenUp ? givenUpReason : "");
        givenUpNote.setVisible(givenUp);
        // A table with X may hide its answer in the special case («F = 1» uses the X): only once revealed.
        Optional<String> special = practice.revealed(output, form) || !hasDontCare(spec, output)
                ? KarnaughTexts.specialCase(model.minimization(), output) : Optional.empty();
        specialCase.setText(special.orElse(""));
        specialCase.setVisible(special.isPresent());

        rebuildLegend(groups, verdicts, spec, form);
        empty.setText(Messages.text("karnaugh.practice.empty"));
        empty.setVisible(groups.isEmpty());
        boolean isFull = groups.size() >= PracticeModel.MAXIMUM_GROUPS;
        full.setText(isFull ? Messages.format("karnaugh.practice.full", PracticeModel.MAXIMUM_GROUPS) : "");
        full.setVisible(isFull);

        int required = requiredCount(spec, output, form);
        int uncovered = result.uncoveredRows().size();
        if (required == 0) {
            counter.setText(Messages.text(sumOfProducts ? "karnaugh.practice.nothing.sop"
                    : "karnaugh.practice.nothing.pos"));
        } else if (uncovered == 0) {
            counter.setText(Messages.text(sumOfProducts ? "karnaugh.practice.done.sop" : "karnaugh.practice.done.pos"));
        } else {
            counter.setText(Messages.format(sumOfProducts ? "karnaugh.practice.remaining.sop"
                    : "karnaugh.practice.remaining.pos", uncovered));
        }

        // After the second press (the group shows) another press does nothing until the groups change.
        hintButton.setEnabled(!givenUp && uncovered > 0 && practice.hintGroup().isEmpty());
        String hintLine = practice.hintGroup().isPresent() ? Messages.text("karnaugh.practice.hint.group")
                : practice.hintRow().isPresent() ? Messages.text("karnaugh.practice.hint.cell") : "";
        hint.setText(hintLine);
        hint.setVisible(!hintLine.isEmpty());
        int used = practice.hintsUsed(output, form);
        hintsUsed.setText(used == 0 ? "" : Messages.format("karnaugh.practice.hintsUsed", used));

        boolean anyRed = verdicts.stream().anyMatch(verdict -> verdict.verdict().isInvalid());
        reviewButton.setEnabled(!givenUp && !groups.isEmpty() && !anyRed);
        clearAllButton.setEnabled(!givenUp && !groups.isEmpty());
        if (review != null && !review.sameCover(output, form, groups)) {
            review = null;
        }
        giveUpButton.setEnabled(!givenUp && review == null);
        for (JComponent button : List.of(hintButton, reviewButton, clearAllButton, giveUpButton)) {
            button.setToolTipText(givenUpReason);
        }
        showReview(spec, output, form, result, givenUp);
        revalidate();
        repaint();
    }

    private static boolean hasDontCare(TruthTableSpec spec, int output) {
        for (int row = 0; row < spec.rowCount(); row++) {
            if (spec.value(output, row) == LogicValue.DONT_CARE) {
                return true;
            }
        }
        return false;
    }

    private void rebuildLegend(List<DrawnGroup> groups, List<GroupCheck.Result> verdicts, TruthTableSpec spec,
                               Form form) {
        legend.removeAll();
        lines.clear();
        for (int index = 0; index < groups.size(); index++) {
            GroupCheck.Result result = verdicts.get(index);
            String text = circled(index) + " " + verdictText(result, groups.get(index).cells().size(),
                    spec.inputNames(), form);
            LegendLine line = new LegendLine(text, index, result.verdict());
            lines.add(line);
            legend.add(line);
        }
        legend.setVisible(!groups.isEmpty());
        legend.revalidate();
        legend.repaint();
    }

    /** The verdict of a group as its legend line says it: the reason it is red or amber, or its term when green. */
    static String verdictText(GroupCheck.Result result, int cellCount, List<String> inputNames, Form form) {
        boolean sumOfProducts = form == Form.SUM_OF_PRODUCTS;
        return switch (result.verdict()) {
            case INVALID_SIZE -> Messages.format("karnaugh.practice.verdict.size", cellCount);
            case NOT_RECTANGLE -> Messages.text("karnaugh.practice.verdict.notRectangle");
            case INCLUDES_WRONG_VALUE -> Messages.text(sumOfProducts ? "karnaugh.practice.verdict.wrong.sop"
                    : "karnaugh.practice.verdict.wrong.pos");
            case REDUNDANT -> Messages.text(sumOfProducts ? "karnaugh.practice.verdict.redundant.sop"
                    : "karnaugh.practice.verdict.redundant.pos");
            case CAN_GROW -> Messages.format("karnaugh.practice.verdict.canGrow",
                    KarnaughTexts.termText(result.largerTerm().orElseThrow(), inputNames, form));
            case VALID -> KarnaughTexts.termText(result.term().orElseThrow(), inputNames, form);
        };
    }

    /** The colour of a verdict's text, read at paint time. */
    private static Color verdictColor(GroupCheck.Verdict verdict) {
        if (verdict.isInvalid()) {
            return WizardColors.failure();
        }
        return verdict == GroupCheck.Verdict.VALID ? WizardColors.success() : WizardColors.warning();
    }

    /** The rows the form has to cover: the 1s of a sum of products, the 0s of a product of sums. */
    private static int requiredCount(TruthTableSpec spec, int output, Form form) {
        LogicValue wanted = form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
        int count = 0;
        for (int row = 0; row < spec.rowCount(); row++) {
            if (spec.value(output, row) == wanted) {
                count++;
            }
        }
        return count;
    }

    /**
     * «Me rindo · Ver la solución»: after asking, gives up on the shown map for good ({@link PracticeModel#giveUp}):
     * VSim's map shows without a grade and stays, and the student's map becomes read-only.
     */
    private void giveUp() {
        int output = model.selectedOutput();
        if (giveUpConfirmation.giveUp(this, model.outputNames().get(output).strip())) {
            model.practice().giveUp(output, model.options().form());
        }
    }

    private void review() {
        int output = model.selectedOutput();
        Form form = model.options().form();
        review = new Review(output, form, model.practice().groups(output, form));
        model.practice().reveal(output, form); // step 4 may show VSim's expression of this output from now on
        refresh();
    }

    /**
     * After «Revisar» «¡Mínima!», «Correcta pero no mínima…» or «No es equivalente…» and VSim's map beside it; on a
     * map given up VSim's map alone, for good.
     */
    private void showReview(TruthTableSpec spec, int output, Form form, PracticeResult result, boolean givenUp) {
        boolean graded = review != null && !givenUp;
        boolean shown = graded || givenUp;
        reviewText.setVisible(graded);
        solutionTitle.setVisible(shown);
        solutionHolder.setVisible(shown);
        if (!shown) {
            reviewText.setText("");
            return;
        }
        if (!graded) {
            reviewText.setText("");
        } else if (!result.equivalent()) {
            int row = result.firstDifferingRow().orElseThrow();
            boolean tableOne = spec.value(output, row) == LogicValue.ONE;
            reviewText.setText(Messages.format("karnaugh.practice.review.notEquivalent", String.valueOf(row),
                    assignment(spec, row), tableOne ? "0" : "1", tableOne ? "1" : "0"));
            reviewColour = WizardColors::failure;
        } else if (result.minimal()) {
            reviewText.setText(Messages.text("karnaugh.practice.review.minimal"));
            reviewColour = WizardColors::success;
        } else {
            reviewText.setText(Messages.format("karnaugh.practice.review.notMinimal", result.solutionGroupCount(),
                    result.solutionLiteralCount(), result.groupCount(), result.literalCount()));
            reviewColour = WizardColors::warning;
        }
        if (solutionMap == null || solutionMap.output() != output) {
            solutionHolder.removeAll();
            solutionMap = new KarnaughMapView(model, new HoverState(), output, true);
            solutionMap.showSolutionOnly();
            solutionMap.setCompact();
            solutionHolder.add(solutionMap);
        }
        solutionMap.repaint();
    }

    /** «A=1, B=0, C=1»: the inputs of table row {@code row}. */
    private static String assignment(TruthTableSpec spec, int row) {
        List<String> parts = new ArrayList<>();
        int inputCount = spec.inputCount();
        for (int input = 0; input < inputCount; input++) {
            parts.add(spec.inputNames().get(input) + "=" + ((row >> (inputCount - 1 - input)) & 1));
        }
        return String.join(", ", parts);
    }

    /** «Borrar todos»: asks first only from 3 groups on. */
    private void clearAll() {
        int output = model.selectedOutput();
        Form form = model.options().form();
        int count = model.practice().groups(output, form).size();
        if (count >= CONFIRMED_CLEAR
                && !clearConfirmation.clear(this, count, model.outputNames().get(output).strip())) {
            return;
        }
        model.practice().clear(output, form);
    }

    private static String circled(int index) {
        int position = index % CIRCLED_DIGITS.length();
        return CIRCLED_DIGITS.substring(position, position + 1);
    }

    /** The colour of the review's text (green, amber or red), as last graded. */
    Color reviewColor() {
        return reviewColour.get();
    }

    String headerText() {
        return header.getText();
    }

    String noticeText() {
        return notice.isVisible() ? notice.getText() : "";
    }

    String givenUpText() {
        return givenUpNote.isVisible() ? givenUpNote.getText() : "";
    }

    String specialCaseText() {
        return specialCase.getText();
    }

    String fullText() {
        return full.isVisible() ? full.getText() : "";
    }

    List<String> legendTexts() {
        return lines.stream().map(LegendLine::getText).toList();
    }

    JComponent legendEntry(int index) {
        return lines.get(index);
    }

    Color legendColor(int index) {
        return lines.get(index).getForeground();
    }

    boolean legendSelected(int index) {
        return lines.get(index).selected();
    }

    String counterText() {
        return counter.getText();
    }

    AbstractButton hintButton() {
        return hintButton;
    }

    String hintText() {
        return hint.isVisible() ? hint.getText() : "";
    }

    String hintsUsedText() {
        return hintsUsed.getText();
    }

    AbstractButton reviewButton() {
        return reviewButton;
    }

    AbstractButton giveUpButton() {
        return giveUpButton;
    }

    String reviewText() {
        return reviewText.isVisible() ? reviewText.getText() : "";
    }

    boolean solutionMapVisible() {
        return solutionHolder.isVisible() && solutionMap != null;
    }

    String solutionTitleText() {
        return solutionTitle.getText();
    }

    KarnaughMapView solutionMap() {
        return solutionMap;
    }

    AbstractButton clearAllButton() {
        return clearAllButton;
    }

    /** A paragraph whose text colour comes from the theme at paint time. */
    private static final class ColouredParagraph extends Paragraph {
        private final transient Supplier<Color> colour;

        ColouredParagraph(Supplier<Color> colour) {
            super("");
            this.colour = colour;
        }

        @Override
        public Color getForeground() {
            return colour == null ? super.getForeground() : colour.get();
        }
    }

    private static final class MutedLabel extends JLabel {
        @Override
        public Color getForeground() {
            return WizardColors.muted();
        }
    }

    /**
     * One student group in the legend: a swatch in the group's colour (its drawing order, like the map) and the
     * verdict in red, amber or green; stronger while the group is selected. A click selects it on the map.
     */
    private final class LegendLine extends JLabel {

        private static final int SWATCH = 14;
        private final int group;
        private final GroupCheck.Verdict verdict;

        LegendLine(String text, int group, GroupCheck.Verdict verdict) {
            super(text);
            this.group = group;
            this.verdict = verdict;
            setBorder(BorderFactory.createEmptyBorder(2, SWATCH + 10, 2, 6));
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 1f));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    model.practice().select(LegendLine.this.group);
                    focusMap.run();
                }
            });
        }

        boolean selected() {
            OptionalInt selected = model.practice().selected();
            return selected.isPresent() && selected.getAsInt() == group;
        }

        @Override
        public Color getForeground() {
            return verdict == null ? super.getForeground() : verdictColor(verdict);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, SWATCH + 6));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            boolean dark = WizardColors.dark();
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int top = (getHeight() - SWATCH) / 2;
                if (selected()) {
                    graphics2D.setColor(GroupPalette.fill(group, dark, false));
                    graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                graphics2D.setColor(GroupPalette.fill(group, dark, true));
                graphics2D.fillRoundRect(4, top, SWATCH, SWATCH, 6, 6);
                graphics2D.setColor(GroupPalette.color(group, dark));
                graphics2D.drawRoundRect(4, top, SWATCH, SWATCH, 6, 6);
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
