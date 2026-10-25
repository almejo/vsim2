package cl.almejo.vsim.ui.appearance;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * One line under the canvas of the shape editor: the tone's mark and a sentence that wraps to the width it has, so a
 * long warning is never cut. The colour follows the {@link ToneLabel.Tone} and is read from the theme at each paint.
 */
@SuppressWarnings("serial")
final class WrappedLine extends JPanel {
    private static final int GAP = 6;
    private final JLabel mark;
    private final Sentence sentence;

    WrappedLine(ToneLabel.Tone tone, String text) {
        super(null);
        setOpaque(false);
        mark = new JLabel(ProblemIcon.of(tone).orElse(null));
        // The mark sits on the first row of the text, whatever the number of rows.
        mark.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        sentence = new Sentence(tone, text);
        add(mark);
        add(sentence);
    }

    @Override
    public void doLayout() {
        Dimension markSize = mark.getPreferredSize();
        mark.setBounds(0, 0, markSize.width, markSize.height);
        sentence.setBounds(markSize.width + GAP, 0, Math.max(1, getWidth() - markSize.width - GAP), getHeight());
    }

    /** The width assumed before the panel has one. */
    static final int FALLBACK_WIDTH = 600;

    /** The height the line needs at {@code width}: the sentence wrapped to what the mark leaves it. */
    int heightFor(int width) {
        int markWidth = mark.getPreferredSize().width;
        return Math.max(mark.getPreferredSize().height + 1, sentence.heightFor(width - markWidth - GAP));
    }

    String text() {
        return sentence.getText();
    }

    /** The sentence as a transparent read-only text area: it wraps by words, and its height follows its width. */
    private static final class Sentence extends JTextArea {
        private final ToneLabel.Tone tone;

        Sentence(ToneLabel.Tone tone, String text) {
            super(text);
            this.tone = tone;
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(null);
            setFont(labelFont());
        }

        private static Font labelFont() {
            return UIManager.getFont("Label.font");
        }

        @Override
        public Color getForeground() {
            return ToneLabel.colorOf(tone, super.getForeground());
        }

        int heightFor(int width) {
            setSize(Math.max(1, width), Short.MAX_VALUE);
            return super.getPreferredSize().height;
        }
    }
}
