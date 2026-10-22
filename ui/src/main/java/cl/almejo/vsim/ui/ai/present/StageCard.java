package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.ui.ai.AiColors;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One stage of the presented task in the panel (spec §5, mockup «ai-step-panel-v2»): a done stage folded to its title
 * and its check («✓ cumple la tabla»), the current one open with its steps (done struck through, current in bold and
 * marked, next in grey), a future one as its title with «se detalla al llegar». Colours and fonts are read at paint
 * time; the AI's words are never HTML. EDT only.
 */
final class StageCard extends JPanel {

    /** A line's colour, from the current theme each time it is painted. */
    enum Tone {
        NORMAL(AiColors::foreground),
        MUTED(AiColors::muted),
        SUCCESS(AiColors::success),
        ERROR(ReadableColors::errorText);

        private final Supplier<Color> color;

        Tone(Supplier<Color> color) {
            this.color = color;
        }

        Color color() {
            return color.get();
        }
    }

    enum Style { PLAIN, BOLD, STRUCK }

    private static final String DONE_MARK = "✓ ";
    private static final String CURRENT_MARK = "▸ ";
    private static final String NEXT_MARK = "· ";

    private final List<Line> lines = new ArrayList<>();

    StageCard(PresentedTask task, int stageIndex) {
        super(new MigLayout("insets 4 8 4 8, wrap 1, gap 0 1", "[grow, fill]"));
        setOpaque(false);
        setBorder(new Outline(1));
        TaskStage stage = task.stages().get(stageIndex);
        String title = (stageIndex + 1) + ". " + stage.title();
        switch (task.stageState(stageIndex)) {
            case DONE -> {
                add(title, Tone.NORMAL, Style.PLAIN);
                task.check(stageIndex).ifPresent(check -> add(Messages.format(
                        check.passed() ? "ai.present.checkPassed" : "ai.present.checkFailed", check.summary()),
                        check.passed() ? Tone.SUCCESS : Tone.ERROR, Style.PLAIN));
            }
            case CURRENT -> {
                add(title, Tone.NORMAL, Style.BOLD);
                addSteps(task, stageIndex, stage.steps());
            }
            case FUTURE -> {
                add(title, Tone.MUTED, Style.PLAIN);
                add(stage.steps().isEmpty() ? Messages.text("ai.present.detailedLater")
                        : Messages.format("ai.present.stepCount", stage.steps().size()), Tone.MUTED, Style.PLAIN);
            }
        }
    }

    /** The current stage's steps; a proposed step beyond the plan (the AI added one) shows after them. */
    private void addSteps(PresentedTask task, int stageIndex, List<String> steps) {
        Optional<StepPreview> pending = task.pending();
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            String text = steps.get(stepIndex);
            switch (task.stepState(stageIndex, stepIndex)) {
                case DONE -> add(DONE_MARK + text, Tone.NORMAL, Style.STRUCK);
                case CURRENT -> add(CURRENT_MARK + proposedOrNot(text, pending.isPresent()), Tone.NORMAL,
                        Style.BOLD);
                case NEXT -> add(NEXT_MARK + text, Tone.MUTED, Style.PLAIN);
            }
        }
        pending.filter(preview -> preview.stepIndex() >= steps.size())
                .ifPresent(preview -> add(CURRENT_MARK + proposedOrNot(preview.text(), true), Tone.NORMAL,
                        Style.BOLD));
    }

    private static String proposedOrNot(String text, boolean proposed) {
        return proposed ? Messages.format("ai.present.proposed", text) : text;
    }

    private void add(String text, Tone tone, Style style) {
        Line line = new Line(text, tone, style);
        lines.add(line);
        add(line);
    }

    List<Line> lines() {
        return List.copyOf(lines);
    }

    List<String> lineTexts() {
        return lines.stream().map(JLabel::getText).toList();
    }

    /** A line of plain text whose colour and font come from the theme at paint time. */
    static final class Line extends JLabel {

        private final Tone tone;
        private final Style style;

        Line(String text, Tone tone, Style style) {
            this.tone = tone;
            this.style = style;
            putClientProperty("html.disable", Boolean.TRUE);
            setText(text);
        }

        Tone tone() {
            return tone;
        }

        /** Narrower than its text when the panel is: the text ends in «…» and the tooltip shows all of it. */
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, super.getMinimumSize().height);
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            setToolTipText(text == null || text.isBlank() ? null : text);
        }

        /** The whole text, only while it does not fit. */
        @Override
        public String getToolTipText(MouseEvent event) {
            return getWidth() < getPreferredSize().width ? getText() : null;
        }

        @Override
        public Color getForeground() {
            return tone == null ? super.getForeground() : tone.color();
        }

        @Override
        public Font getFont() {
            Font base = UIManager.getFont("Label.font");
            if (style == null || base == null) {
                return super.getFont();
            }
            return switch (style) {
                case PLAIN -> base;
                case BOLD -> base.deriveFont(Font.BOLD);
                case STRUCK -> base.deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
            };
        }
    }

    /** A thin rounded outline in the theme's border colour, read when painted, with {@code padding} inside it. */
    static final class Outline extends AbstractBorder {

        private static final int ARC = 8;
        private final int padding;

        Outline(int padding) {
            this.padding = padding;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Color color = UIManager.getColor("Component.borderColor");
            graphics.setColor(color != null ? color : Color.GRAY);
            graphics.drawRoundRect(x, y, width - 1, height - 1, ARC, ARC);
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(padding, padding, padding, padding);
        }
    }
}
