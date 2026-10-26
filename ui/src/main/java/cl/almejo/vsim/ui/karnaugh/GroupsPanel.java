package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.GroupExplanation;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.Action;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Step 3 «Grupos» (spec §3): the map of each output with its minimal groups, a legend (① B'C, ② BD…) in the groups'
 * colours, and the explanation of the group under the mouse — what each variable does, the size, «¿Por qué?» with the
 * algebra — plus the collapsible «Reglas». A constant output is explained in words instead. The legend works from the
 * keyboard too: Tab reaches it and the arrows go through the groups. A click on a group (map or legend) pins its
 * explanation so the mouse can travel to «¿Por qué?» (spec §5); another click on it, a click on the map outside every
 * group, Esc or another output lets it go.
 * <p>
 * In the practice wizard («Practicar Karnaugh…», spec §2) step 3 is the practice only: the form can be chosen at the
 * top (the same option as step 4), the maps take the student's groups and the right side is the {@link PracticePanel}.
 * The mode is the wizard's and never changes here; the solution wizard's step 3 is as it was before the practice.
 */
final class GroupsPanel extends JPanel implements WizardStep {

    private static final String CIRCLED_DIGITS = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯";
    private static final String EXPANDED = " ▾";
    private static final String COLLAPSED = " ▸";

    private final KarnaughModel model;
    private final HoverState hover;
    private final OutputTabs tabs;
    private final Paragraph header = new Paragraph("");
    private final Paragraph specialCase = new Paragraph("", Font.BOLD);
    private final JPanel legend = new JPanel(new MigLayout("insets 0, gap 2, wrap 1"));
    private final List<LegendEntry> entries = new ArrayList<>();
    private final Paragraph explanationHeader = new Paragraph("");
    private final Paragraph variables = new Paragraph("", Font.BOLD);
    private final Paragraph size = new Paragraph("");
    private final JToggleButton whyToggle = new JToggleButton();
    private final Paragraph why = new Paragraph("");
    private final JToggleButton rulesToggle = new JToggleButton();
    private final Paragraph rules = new Paragraph("");
    private final JPanel practiceForm = new JPanel(new MigLayout("insets 0"));
    private final OptionControls optionControls;
    private final JPanel solutionSide = new JPanel(new MigLayout("insets 0, fillx, wrap 1, hidemode 3",
            "[grow, fill]"));
    private final PracticePanel practicePanel;
    private int shownOutput = -1;
    private List<Term> shownTerms = List.of();
    private boolean active;

    GroupsPanel(KarnaughModel model, HoverState hover) {
        // The solution wizard keeps step 3's layout from before the practice: no «Forma:» row.
        super(new MigLayout("insets 12, fill, hidemode 3", "[grow, fill][400!, fill]",
                model.practice().practising() ? "[][grow, fill]" : "[grow, fill]"));
        this.model = model;
        this.optionControls = new OptionControls(model::options, model::setOptions);
        this.hover = hover;
        this.tabs = new OutputTabs(model, hover, true);
        this.practicePanel = new PracticePanel(model,
                () -> tabs.view(model.selectedOutput()).requestFocusInWindow());

        for (JToggleButton toggle : List.of(whyToggle, rulesToggle)) {
            toggle.putClientProperty("JButton.buttonType", "borderless");
            toggle.addActionListener(event -> updateToggles());
        }
        why.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        rules.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        JPanel right = new WidthTrackingPanel(new MigLayout("insets 0 0 0 8, fillx, wrap 1, hidemode 3",
                "[grow, fill]"));
        solutionSide.add(header, "wmin 100");
        solutionSide.add(specialCase, "wmin 100");
        solutionSide.add(legend, "gaptop 6");
        solutionSide.add(explanationHeader, "gaptop 8, wmin 100");
        solutionSide.add(variables, "gaptop 4, wmin 100");
        solutionSide.add(size, "wmin 100");
        solutionSide.add(whyToggle, "growx 0");
        solutionSide.add(why, "wmin 100");
        solutionSide.add(rulesToggle, "growx 0, gaptop 8");
        solutionSide.add(rules, "wmin 100");
        // The wizard's mode is fixed: only its side is in the panel (the solution wizard has no practice controls).
        right.add(model.practice().practising() ? practicePanel : solutionSide, "wmin 100");
        JScrollPane scroll = new JScrollPane(right);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        buildPracticeForm();
        if (model.practice().practising()) {
            add(practiceForm, "span 2, growx, wrap");
        }
        add(tabs);
        add(scroll);
        hover.addListener(this::explain);
        bindEscape();
        updateToggles();
    }

    /** The practice's «Forma:» (the same option as step 4); not in the solution wizard's panel. */
    private void buildPracticeForm() {
        practiceForm.add(new JLabel(Messages.text("karnaugh.practice.form")));
        optionControls.formRadios().forEach(practiceForm::add);
        practiceForm.setVisible(model.practice().practising());
    }

    /** Esc lets a pinned group go; unpinned, the action is off so Esc reaches the window (which closes). */
    private void bindEscape() {
        Keys.bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "unpinGroup", () -> hover.pinnedGroup() >= 0, hover::unpin);
    }

    KarnaughMapView mapView(int output) {
        return tabs.view(output);
    }

    /** Shows output {@code output}'s tab (like a click on it). */
    void selectOutput(int output) {
        model.setSelectedOutput(output);
        refresh();
    }

    @Override
    public void refresh() {
        active = model.problem().isEmpty();
        if (!active) {
            return;
        }
        tabs.refresh();
        boolean practising = model.practice().practising();
        practiceForm.setVisible(practising);
        optionControls.syncForm(model.options());
        solutionSide.setVisible(!practising);
        practicePanel.setVisible(practising);
        if (practising) {
            practicePanel.refresh();
        }
        int output = model.selectedOutput();
        Minimization minimization = model.minimization();
        List<Term> terms = minimization.outputs().get(output).terms();
        if (output != shownOutput) {
            shownOutput = output;
            shownTerms = terms;
            hover.unpin();
            hover.setGroup(0);
        } else if (!terms.equals(shownTerms)) {
            shownTerms = terms;
            hover.unpin(); // other groups: the pinned index means another group now
        }
        boolean sumOfProducts = minimization.form() == Minimization.Form.SUM_OF_PRODUCTS;
        header.setText(Messages.text(sumOfProducts ? "karnaugh.groups.header.sop" : "karnaugh.groups.header.pos"));
        Optional<String> special = KarnaughTexts.specialCase(minimization, output);
        specialCase.setText(special.orElse(""));
        specialCase.setVisible(special.isPresent());
        rules.setText(bullets(KarnaughTexts.rules(minimization.form())));
        rebuildLegend(minimization, output);
        explain();
    }

    private void rebuildLegend(Minimization minimization, int output) {
        legend.removeAll();
        entries.clear();
        List<Term> terms = minimization.outputs().get(output).terms();
        TruthTableSpec spec = minimization.spec();
        for (int index = 0; index < terms.size(); index++) {
            String text = circled(index) + " "
                    + KarnaughTexts.termText(terms.get(index), spec.inputNames(), minimization.form());
            List<String> shared = model.sharedWith(output, index);
            if (!shared.isEmpty()) {
                text += " — " + Messages.format("karnaugh.groups.shared", String.join(", ", shared));
            }
            LegendEntry entry = new LegendEntry(text, index, model.colorIndex(output, index));
            entries.add(entry);
            legend.add(entry);
        }
        legend.revalidate();
        legend.repaint();
    }

    /** Shows the explanation of the group the hover points at (the first one until the mouse finds another). */
    private void explain() {
        if (!active || model.problem().isPresent()) {
            return;
        }
        Minimization minimization = model.minimization();
        List<Term> terms = minimization.outputs().get(model.selectedOutput()).terms();
        entries.forEach(JComponent::repaint);
        explanationHeader.setText(Messages.text(hover.pinnedGroup() >= 0 ? "karnaugh.practice.pinned"
                : "karnaugh.groups.hint"));
        boolean hasGroup = hover.group() >= 0 && hover.group() < terms.size();
        variables.setVisible(hasGroup);
        size.setVisible(hasGroup);
        whyToggle.setVisible(hasGroup);
        why.setVisible(hasGroup && whyToggle.isSelected());
        if (!hasGroup) {
            return;
        }
        TruthTableSpec spec = minimization.spec();
        GroupExplanation explanation = GroupExplanation.of(terms.get(hover.group()), spec, minimization.form(),
                model.selectedOutput());
        variables.setText(KarnaughTexts.variablesLine(explanation));
        size.setText(KarnaughTexts.sizeLine(explanation));
        why.setText(String.join("\n", KarnaughTexts.algebra(explanation, spec.inputNames())));
    }

    private void updateToggles() {
        whyToggle.setText(Messages.text("karnaugh.groups.why") + (whyToggle.isSelected() ? EXPANDED : COLLAPSED));
        rulesToggle.setText(Messages.text("karnaugh.groups.rules")
                + (rulesToggle.isSelected() ? EXPANDED : COLLAPSED));
        why.setVisible(whyToggle.isVisible() && whyToggle.isSelected());
        rules.setVisible(rulesToggle.isSelected());
        revalidate();
        repaint();
    }

    private static String bullets(List<String> lines) {
        List<String> bulleted = new ArrayList<>();
        for (String line : lines) {
            bulleted.add("• " + line);
        }
        return String.join("\n", bulleted);
    }

    /** ①, ②… numbered per map, so every map's legend starts at ① whatever the groups' colours. */
    private static String circled(int index) {
        int position = index % CIRCLED_DIGITS.length();
        return CIRCLED_DIGITS.substring(position, position + 1);
    }

    boolean solutionSideVisible() {
        return solutionSide.isVisible();
    }

    AbstractButton practiceFormButton(Minimization.Form form) {
        return optionControls.formButton(form);
    }

    boolean practiceFormVisible() {
        return practiceForm.isVisible();
    }

    boolean practicePanelVisible() {
        return practicePanel.isVisible();
    }

    PracticePanel practicePanel() {
        return practicePanel;
    }

    List<String> legendTexts() {
        return entries.stream().map(LegendEntry::getText).toList();
    }

    JComponent legendEntry(int index) {
        return entries.get(index);
    }

    int explainedGroup() {
        return hover.group();
    }

    String explanationHeaderText() {
        return explanationHeader.getText();
    }

    String headerText() {
        return header.getText();
    }

    String specialCaseText() {
        return specialCase.getText();
    }

    String variablesText() {
        return variables.getText();
    }

    String sizeText() {
        return size.getText();
    }

    String whyText() {
        return why.getText();
    }

    boolean whyVisible() {
        return why.isVisible();
    }

    AbstractButton whyToggle() {
        return whyToggle;
    }

    boolean rulesVisible() {
        return rules.isVisible();
    }

    AbstractButton rulesToggle() {
        return rulesToggle;
    }

    /** Explains the group {@code delta} entries away from {@code group} (clamped) and moves the focus there. */
    private void moveFrom(int group, int delta) {
        if (entries.isEmpty()) {
            return;
        }
        int target = Math.clamp(group + delta, 0, entries.size() - 1);
        explainFromLegend(target);
        entries.get(target).requestFocusInWindow();
    }

    /** The legend reached {@code group}: it is explained, and pinned instead when a group was pinned. */
    private void explainFromLegend(int group) {
        if (hover.pinnedGroup() >= 0) {
            hover.pin(group);
        } else {
            hover.setGroup(group);
        }
    }

    /**
     * One legend line: a swatch and the term in the group's colour (read at paint time), stronger while explained.
     * Hovering it or reaching it with Tab explains the group and clicking it pins it (or lets it go); the arrows move
     * to the previous or next group (carrying the pin), and space or Enter opens or closes «¿Por qué?».
     */
    private final class LegendEntry extends JLabel {

        private static final int SWATCH = 14;
        private final int group;
        private final int colorIndex;

        LegendEntry(String text, int group, int colorIndex) {
            super(text);
            this.group = group;
            this.colorIndex = colorIndex;
            setBorder(BorderFactory.createEmptyBorder(2, SWATCH + 10, 2, 6));
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 2f));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover.setGroup(LegendEntry.this.group);
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    hover.togglePin(LegendEntry.this.group);
                    requestFocusInWindow();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    explainFromLegend(LegendEntry.this.group);
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent event) {
                    repaint();
                }
            });
            bind("legendPrevious", () -> moveFrom(this.group, -1), KeyEvent.VK_UP, KeyEvent.VK_LEFT);
            bind("legendNext", () -> moveFrom(this.group, 1), KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT);
            bind("legendToggleWhy", whyToggle::doClick, KeyEvent.VK_SPACE, KeyEvent.VK_ENTER);
        }

        private void bind(String name, Runnable action, int... keyCodes) {
            Action keyAction = Actions.unnamed(action);
            for (int keyCode : keyCodes) {
                Keys.bind(this, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(keyCode, 0), name, keyAction);
            }
        }

        /** The group's colour, read at paint time so a theme change recolours the legend. */
        @Override
        public Color getForeground() {
            return GroupPalette.color(colorIndex, WizardColors.dark());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, SWATCH + 6));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            boolean dark = WizardColors.dark();
            boolean explained = hover.group() == group;
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int top = (getHeight() - SWATCH) / 2;
                if (explained) {
                    graphics2D.setColor(GroupPalette.fill(colorIndex, dark, false));
                    graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                graphics2D.setColor(GroupPalette.fill(colorIndex, dark, true));
                graphics2D.fillRoundRect(4, top, SWATCH, SWATCH, 6, 6);
                graphics2D.setColor(GroupPalette.color(colorIndex, dark));
                graphics2D.drawRoundRect(4, top, SWATCH, SWATCH, 6, 6);
                if (isFocusOwner()) {
                    graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
