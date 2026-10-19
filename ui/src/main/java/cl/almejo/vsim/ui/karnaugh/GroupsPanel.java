package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.GroupExplanation;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Step 3 «Grupos» (spec §3): the map of each output with its minimal groups, a legend (① B'C, ② BD…) in the groups'
 * colours, and the explanation of the group under the mouse — what each variable does, the size, «¿Por qué?» with the
 * algebra — plus the collapsible «Reglas». A constant output is explained in words instead.
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
    private final Paragraph variables = new Paragraph("", Font.BOLD);
    private final Paragraph size = new Paragraph("");
    private final JToggleButton whyToggle = new JToggleButton();
    private final Paragraph why = new Paragraph("");
    private final JToggleButton rulesToggle = new JToggleButton();
    private final Paragraph rules = new Paragraph("");
    private int shownOutput = -1;
    private boolean active;

    GroupsPanel(KarnaughModel model, HoverState hover) {
        super(new MigLayout("insets 12, fill", "[grow, fill][400!, fill]", "[grow, fill]"));
        this.model = model;
        this.hover = hover;
        this.tabs = new OutputTabs(model, hover, true);

        for (JToggleButton toggle : List.of(whyToggle, rulesToggle)) {
            toggle.putClientProperty("JButton.buttonType", "borderless");
            toggle.addActionListener(event -> updateToggles());
        }
        why.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        rules.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        JPanel right = new WidthTrackingPanel(new MigLayout("insets 0 0 0 8, fillx, wrap 1, hidemode 3",
                "[grow, fill]"));
        right.add(header, "wmin 100");
        right.add(specialCase, "wmin 100");
        right.add(legend, "gaptop 6");
        right.add(new Paragraph(Messages.text("karnaugh.groups.hint")), "gaptop 8, wmin 100");
        right.add(variables, "gaptop 4, wmin 100");
        right.add(size, "wmin 100");
        right.add(whyToggle, "growx 0");
        right.add(why, "wmin 100");
        right.add(rulesToggle, "growx 0, gaptop 8");
        right.add(rules, "wmin 100");
        JScrollPane scroll = new JScrollPane(right);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(tabs);
        add(scroll);
        hover.addListener(this::explain);
        updateToggles();
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
        int output = model.selectedOutput();
        if (output != shownOutput) {
            shownOutput = output;
            hover.setGroup(0);
        }
        Minimization minimization = model.minimization();
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
            String text = circled(model.colorIndex(output, index)) + " "
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

    private static String circled(int colorIndex) {
        int position = colorIndex % CIRCLED_DIGITS.length();
        return CIRCLED_DIGITS.substring(position, position + 1);
    }

    List<String> legendTexts() {
        return entries.stream().map(LegendEntry::getText).toList();
    }

    int explainedGroup() {
        return hover.group();
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

    /** A panel that takes the width of its scroll pane, so the paragraphs wrap instead of running off the side. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        WidthTrackingPanel(MigLayout layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * One legend line: a swatch and the term in the group's colour (read at paint time), stronger while explained.
     * Hovering it explains the group.
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
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover.setGroup(LegendEntry.this.group);
                }
            });
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
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
