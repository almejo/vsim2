package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.i18n.Messages;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a bubble says (mockup v4): its title, its text with the keys as keycaps, the «Atajo» / «En el menú» line when
 * it has one, and the row «n de m» · «Listo» ‹ ›. Transparent: the overlay paints the bubble's body, arrow and shadow
 * under it. A click on it stays on it (it never reaches the circuit under the bubble).
 */
final class NewsBubble extends JComponent {

    /** The mockup's bubble: 260 px wide, padded 12 × 14. */
    static final int WIDTH = 260;
    private static final int PADDING_Y = 12;
    private static final int PADDING_X = 14;
    private static final int TITLE_GAP = 4;
    private static final int SECTION_GAP = 10;
    private static final int RULE_GAP = 8;
    private static final int BUTTON_GAP = 6;
    private static final float TITLE_GROWTH = 1f;
    private static final float SMALL_SHRINK = 1f;
    private static final String PREVIOUS_ARROW = "‹";
    private static final String NEXT_ARROW = "›";

    /** What one bubble shows. */
    record Content(String title, FlowText text, Optional<FlowText> line, String count, boolean previousEnabled,
                   boolean last) {
    }

    private final BubbleButton done;
    private final BubbleButton previous;
    private final BubbleButton next;
    private Content content = new Content("", new FlowText(List.of()), Optional.empty(), "", false, false);

    NewsBubble(Runnable onDone, Runnable onPrevious, Runnable onNext) {
        setLayout(null);
        setOpaque(false);
        done = new BubbleButton(Messages.text("whatsNew.done"), BubbleButton.Look.OUTLINE, onDone);
        previous = new BubbleButton(PREVIOUS_ARROW, BubbleButton.Look.OUTLINE, onPrevious);
        previous.setToolTipText(Messages.text("whatsNew.previous"));
        next = new BubbleButton(NEXT_ARROW, BubbleButton.Look.SOLID, onNext);
        add(done);
        add(previous);
        add(next);
        addMouseListener(new MouseAdapter() {
        }); // swallows the clicks on the bubble's body
    }

    void show(Content shown) {
        this.content = shown;
        previous.setEnabled(shown.previousEnabled());
        next.setToolTipText(Messages.text(shown.last() ? "whatsNew.finish" : "whatsNew.next"));
        revalidate();
        repaint();
    }

    Content content() {
        return content;
    }

    List<AbstractButton> buttons() {
        return List.of(done, previous, next);
    }

    private static Font textFont() {
        Font font = UIManager.getFont("Label.font");
        return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : font;
    }

    private static Font titleFont() {
        Font font = textFont();
        return font.deriveFont(Font.BOLD, font.getSize2D() + TITLE_GROWTH);
    }

    private static Font smallFont() {
        Font font = textFont();
        return font.deriveFont(font.getSize2D() - SMALL_SHRINK);
    }

    /** How tall the bubble is at {@code width}, everything wrapped. */
    int heightFor(int width) {
        int inner = width - 2 * UIScale.scale(PADDING_X);
        int height = UIScale.scale(PADDING_Y) + getFontMetrics(titleFont()).getHeight() + UIScale.scale(TITLE_GAP)
                + content.text().height(this, textFont(), inner) + UIScale.scale(SECTION_GAP);
        if (content.line().isPresent()) {
            height += UIScale.scale(RULE_GAP) + content.line().get().height(this, smallFont(), inner)
                    + UIScale.scale(SECTION_GAP);
        }
        return height + rowHeight() + UIScale.scale(PADDING_Y);
    }

    private int rowHeight() {
        return next.getPreferredSize().height;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = UIScale.scale(WIDTH);
        return new Dimension(width, heightFor(width));
    }

    @Override
    public void doLayout() {
        int right = getWidth() - UIScale.scale(PADDING_X);
        int top = getHeight() - UIScale.scale(PADDING_Y) - rowHeight();
        List<BubbleButton> fromTheRight = new ArrayList<>(List.of(next, previous, done));
        for (BubbleButton button : fromTheRight) {
            Dimension size = button.getPreferredSize();
            right -= size.width;
            button.setBounds(right, top, size.width, rowHeight());
            right -= UIScale.scale(BUTTON_GAP);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int left = UIScale.scale(PADDING_X);
            int inner = getWidth() - 2 * left;
            FontMetrics titleMetrics = getFontMetrics(titleFont());
            int y = UIScale.scale(PADDING_Y);
            painter.setFont(titleFont());
            painter.setColor(BubbleColors.ink());
            painter.drawString(content.title(), left, y + titleMetrics.getAscent());
            y += titleMetrics.getHeight() + UIScale.scale(TITLE_GAP);
            y = content.text().paint(painter, this, textFont(), left, y, inner, BubbleColors.ink(),
                    BubbleColors.fill()) + UIScale.scale(SECTION_GAP);
            if (content.line().isPresent()) {
                painter.setColor(BubbleColors.rule());
                painter.fillRect(left, y, inner, Math.max(1, UIScale.scale(1)));
                y += UIScale.scale(RULE_GAP);
                content.line().get().paint(painter, this, smallFont(), left, y, inner, BubbleColors.ink(),
                        BubbleColors.fill());
            }
            paintCount(painter, left);
        } finally {
            painter.dispose();
        }
    }

    private void paintCount(Graphics2D painter, int left) {
        FontMetrics metrics = getFontMetrics(smallFont());
        int rowTop = getHeight() - UIScale.scale(PADDING_Y) - rowHeight();
        painter.setFont(smallFont());
        painter.setColor(BubbleColors.ink());
        painter.drawString(content.count(), left, rowTop + (rowHeight() - metrics.getHeight()) / 2
                + metrics.getAscent());
    }
}
