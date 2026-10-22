package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.QuestionAnswer;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A question the AI asks in VSim (IA.3, {@code vsim_ask_user}), in the «Presentar» panel or the status strip's
 * «Responder…»: «La IA pregunta», the question wrapped, a button per choice and, when the question takes it, a
 * one-line field with «Enviar» (Enter sends too). It answers once: then everything is disabled and it says what was
 * sent, until VSim takes the card away. The AI's words are plain text, never HTML; colours and fonts from the theme
 * at paint time. EDT only.
 */
public final class QuestionCard extends JPanel {

    private static final int FIELD_COLUMNS = 16;

    private final Question question;
    private final Consumer<QuestionAnswer> answered;
    private final JLabel title = new BoldLabel(Messages.text("ai.question.title"));
    private final JTextArea questionText = new ThemedText();
    private final List<JButton> choiceButtons = new ArrayList<>();
    private final JTextField field = new JTextField(FIELD_COLUMNS);
    private final JButton sendButton = new JButton(Messages.text("ai.question.send"));
    private final JLabel sentText = AiPanel.plainText(" ");
    /** «Respóndele en Claude Code…» once the AI stopped asking for the answer (IA.3 review I2). */
    private final JLabel attentionText = AiPanel.plainText(" ");
    private boolean sent;

    /** @param answered hears the user's answer, once */
    public QuestionCard(Question question, Consumer<QuestionAnswer> answered) {
        super(new MigLayout("insets 6 8 6 8, wrap 1, gap 0 4, hidemode 3", "[grow, fill]"));
        this.question = Objects.requireNonNull(question, "question");
        this.answered = Objects.requireNonNull(answered, "answered");
        setOpaque(false);
        setBorder(new Outline());
        add(title);
        questionText.setText(question.text());
        add(questionText, "wmin 0");
        if (!question.choices().isEmpty()) {
            JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            choices.setOpaque(false);
            for (String choice : question.choices()) {
                JButton button = new JButton(choice);
                button.putClientProperty("html.disable", Boolean.TRUE);
                button.addActionListener(event -> send(QuestionAnswer.choice(choice)));
                choiceButtons.add(button);
                choices.add(button);
            }
            add(choices, "wmin 0");
        }
        field.setToolTipText(Messages.text("ai.question.field"));
        field.getAccessibleContext().setAccessibleName(Messages.text("ai.question.field"));
        field.addActionListener(event -> sendWritten());
        sendButton.addActionListener(event -> sendWritten());
        JPanel written = new JPanel(new MigLayout("insets 0, gap 4", "[grow, fill][]"));
        written.setOpaque(false);
        written.add(field, "wmin 0");
        written.add(sendButton);
        field.setVisible(question.allowText());
        sendButton.setVisible(question.allowText());
        written.setVisible(question.allowText());
        add(written, "wmin 0");
        sentText.setVisible(false);
        add(sentText, "wmin 0");
        attentionText.setVisible(false);
        add(attentionText, "wmin 0");
    }

    /**
     * The AI has asked nothing about its task for {@code silentFor} while this question waits: the card says to answer
     * in Claude Code, with the minutes; {@link Duration#ZERO} (the AI asked again) takes the line away.
     */
    public void unattendedFor(Duration silentFor) {
        boolean unattended = !silentFor.isZero() && !silentFor.isNegative();
        attentionText.setText(unattended ? Messages.format("ai.question.unattended",
                Long.toString(Math.max(1, silentFor.toMinutes()))) : " ");
        attentionText.setToolTipText(unattended ? attentionText.getText() : null);
        if (attentionText.isVisible() != unattended) {
            attentionText.setVisible(unattended);
            revalidate();
            repaint();
        }
    }

    private void sendWritten() {
        String text = field.getText().strip();
        if (!text.isEmpty()) {
            send(QuestionAnswer.text(text));
        }
    }

    private void send(QuestionAnswer answer) {
        if (sent) {
            return;
        }
        sent = true;
        choiceButtons.forEach(button -> button.setEnabled(false));
        field.setEnabled(false);
        sendButton.setEnabled(false);
        sentText.setText(Messages.format("ai.question.sent", answer.choice().or(answer::text).orElse("")));
        sentText.setVisible(true);
        revalidate();
        repaint();
        answered.accept(answer);
    }

    /** As wide as its parent gives it; its question wraps. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, super.getMinimumSize().height);
    }

    public Question question() {
        return question;
    }

    JLabel title() {
        return title;
    }

    JTextArea questionText() {
        return questionText;
    }

    List<JButton> choiceButtons() {
        return List.copyOf(choiceButtons);
    }

    public JTextField field() {
        return field;
    }

    JButton sendButton() {
        return sendButton;
    }

    JLabel sentText() {
        return sentText;
    }

    public JLabel attentionText() {
        return attentionText;
    }

    /** The question: wrapped plain text in the theme's label font and colour, read when painted. */
    private static final class ThemedText extends JTextArea {

        ThemedText() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        public Font getFont() {
            Font font = UIManager.getFont("Label.font");
            return font != null ? font : super.getFont();
        }

        @Override
        public Color getForeground() {
            return AiColors.foreground();
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, super.getMinimumSize().height);
        }
    }

    /** «La IA pregunta» in the theme's bold label font, read when painted. */
    private static final class BoldLabel extends JLabel {

        BoldLabel(String text) {
            super(text);
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Font getFont() {
            Font font = UIManager.getFont("Label.font");
            return font != null ? font.deriveFont(Font.BOLD) : super.getFont();
        }
    }

    /** A thin rounded outline in the theme's border colour, read when painted. */
    private static final class Outline extends AbstractBorder {

        private static final int ARC = 8;

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Color color = UIManager.getColor("Component.borderColor");
            graphics.setColor(color != null ? color : Color.GRAY);
            graphics.drawRoundRect(x, y, width - 1, height - 1, ARC, ARC);
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }
    }
}
