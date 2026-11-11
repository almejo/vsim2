package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.theme.ThemeColors;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The card of the tip of the day (mockup v11): rounded, with a soft shadow, in the theme's colours read at paint time.
 * The lit bulb, «Consejo del día» and ×; the picture; the title; the text with its keys as keycaps; «Mostrar al
 * iniciar», «Probarlo» and ‹ ›. No counter: it never says how many tips there are. It never takes the focus by
 * itself; a click on it gives it the focus. Constructible without a window ({@link TipShowing} places it).
 */
final class TipCard extends JComponent {

    /** What the card shows. */
    record Content(String title, FlowText text, TipPicture.Content picture, boolean canTry, boolean showAtStart) {

        Content {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(picture, "picture");
        }
    }

    /** What the card's controls do. */
    record Handlers(Runnable close, Runnable previous, Runnable next, Runnable tryIt, Consumer<Boolean> showAtStart) {

        Handlers {
            Objects.requireNonNull(close, "close");
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(tryIt, "tryIt");
            Objects.requireNonNull(showAtStart, "showAtStart");
        }
    }

    /** The mockup's card: 300 px wide, padded 12 × 14, rounded 10. */
    static final int WIDTH = 300;
    private static final int PADDING_Y = 12;
    private static final int PADDING_X = 14;
    private static final int CORNER = 10;
    /** Room around the card for its shadow, inside this component's bounds. */
    static final int SHADOW = 12;
    private static final int SHADOW_LAYERS = 6;
    private static final int SHADOW_ALPHA = 9;
    private static final int SHADOW_DROP = 4;
    private static final int GAP = 6;
    private static final float TITLE_GROWTH = 2f;
    private static final float SMALL_SHRINK = 1f;
    private static final String CLOSE = "×";
    private static final String PREVIOUS_ARROW = "‹";
    private static final String NEXT_ARROW = "›";
    private static final Insets SMALL_BUTTON_MARGIN = new Insets(1, 8, 1, 8);

    private final JLabel header = new JLabel(Messages.text("tip.header"), new TipBulbIcon(), JLabel.LEADING) {
        @Override
        public Color getForeground() {
            return TipPicture.mutedColour(); // read at paint time: the theme changes live
        }
    };
    private final JButton closeButton = new JButton(CLOSE);
    private final TipPicture picture;
    private final JLabel titleLabel = new JLabel();
    private final TextView textView = new TextView();
    private final JCheckBox showAtStartBox = new JCheckBox(Messages.text("tip.showAtStart"));
    private final JButton tryButton = new JButton(Messages.text("tip.try"));
    private final JButton previousButton = new JButton(PREVIOUS_ARROW);
    private final JButton nextButton = new JButton(NEXT_ARROW);
    private Content content;
    private float opacity = 1f;

    TipCard(PainterRegistry painters, Supplier<ColorScheme> scheme, Handlers handlers) {
        Objects.requireNonNull(handlers, "handlers");
        this.picture = new TipPicture(painters, scheme);
        setOpaque(false);
        setFocusable(true);
        int inner = innerWidth();
        int shadow = UIScale.scale(SHADOW);
        setLayout(new MigLayout("wrap 1, fillx, gap 0 " + UIScale.scale(GAP) + ", insets "
                + (shadow + UIScale.scale(PADDING_Y)) + " " + (shadow + UIScale.scale(PADDING_X)) + " "
                + (shadow + UIScale.scale(PADDING_Y)) + " " + (shadow + UIScale.scale(PADDING_X)),
                "[" + inner + "!]"));
        header.setIconTextGap(UIScale.scale(GAP));
        header.setFont(smallFont());
        closeButton.setToolTipText(Messages.text("tip.close"));
        closeButton.putClientProperty("JButton.buttonType", "toolBarButton");
        titleLabel.setFont(titleFont());
        showAtStartBox.setFont(smallFont());
        showAtStartBox.setOpaque(false);
        previousButton.setToolTipText(Messages.text("tip.previous"));
        nextButton.setToolTipText(Messages.text("tip.next"));
        for (JButton button : List.of(tryButton, previousButton, nextButton)) {
            button.putClientProperty("JComponent.minimumWidth", 0);
            button.setMargin(SMALL_BUTTON_MARGIN);
        }
        closeButton.addActionListener(event -> handlers.close().run());
        previousButton.addActionListener(event -> handlers.previous().run());
        nextButton.addActionListener(event -> handlers.next().run());
        tryButton.addActionListener(event -> handlers.tryIt().run());
        showAtStartBox.addActionListener(event -> handlers.showAtStart().accept(showAtStartBox.isSelected()));

        add(header, "split 2, growx, pushx");
        add(closeButton, "aligny center");
        add(picture, "w " + inner + "!, h " + TipPicture.heightFor(inner) + "!, gaptop " + UIScale.scale(2));
        add(titleLabel, "growx, gaptop " + UIScale.scale(2));
        add(textView, "w " + inner + "!");
        add(showAtStartBox, "split 4, growx, pushx, gaptop " + UIScale.scale(4));
        add(tryButton);
        add(previousButton);
        add(nextButton);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow(); // a click on the card gives it the focus; it never takes it by itself
            }
        });
    }

    /** The width inside the padding, scaled. */
    static int innerWidth() {
        return UIScale.scale(WIDTH) - 2 * UIScale.scale(PADDING_X);
    }

    void show(Content shown) {
        this.content = Objects.requireNonNull(shown, "shown");
        titleLabel.setText(shown.title());
        textView.flow = shown.text();
        picture.show(shown.picture());
        tryButton.setVisible(shown.canTry());
        showAtStartBox.setSelected(shown.showAtStart());
        textView.invalidate(); // its height follows the text: the card measures itself again
        invalidate();
        setSize(getPreferredSize());
        revalidate();
        repaint();
    }

    /** How opaque the card is drawn, 0 to 1 (it fades in when animations are on). */
    void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
        repaint();
    }

    float opacity() {
        return opacity;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension laidOut = super.getPreferredSize();
        return new Dimension(UIScale.scale(WIDTH) + 2 * UIScale.scale(SHADOW), laidOut.height);
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setComposite(AlphaComposite.SrcOver.derive(opacity));
            super.paint(painter);
        } finally {
            painter.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int shadow = UIScale.scale(SHADOW);
            int width = getWidth() - 2 * shadow;
            int height = getHeight() - 2 * shadow;
            int corner = UIScale.scale(CORNER);
            painter.setColor(new Color(0, 0, 0, SHADOW_ALPHA));
            for (int layer = SHADOW_LAYERS; layer >= 1; layer--) {
                int spread = UIScale.scale(layer * 2);
                painter.fillRoundRect(shadow - spread / 2, shadow - spread / 2 + UIScale.scale(SHADOW_DROP),
                        width + spread, height + spread, corner + spread, corner + spread);
            }
            painter.setColor(background());
            painter.fillRoundRect(shadow, shadow, width, height, corner, corner);
            painter.setColor(TipPicture.borderColour());
            painter.drawRoundRect(shadow, shadow, width - 1, height - 1, corner, corner);
        } finally {
            painter.dispose();
        }
    }

    private static Color background() {
        Color panel = UIManager.getColor("Panel.background");
        return panel == null ? Color.WHITE : panel;
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

    // --- what tests read ---

    String title() {
        return titleLabel.getText();
    }

    /** Every text the card shows: the labels, the buttons and the words of its text. */
    List<String> visibleTexts() {
        List<String> texts = new ArrayList<>();
        for (Component child : getComponents()) {
            if (child instanceof JLabel label) {
                texts.add(label.getText());
            } else if (child instanceof AbstractButton button && button.isVisible()) {
                texts.add(button.getText());
            }
        }
        texts.addAll(textWords());
        return texts;
    }

    /** The keys the text draws as keycaps, in order. */
    List<List<String>> textKeys() {
        return content == null ? List.of() : content.text().items().stream()
                .filter(FlowText.Keys.class::isInstance).map(item -> ((FlowText.Keys) item).keys()).toList();
    }

    /** The words of the text, in order. */
    List<String> textWords() {
        return content == null ? List.of() : content.text().items().stream()
                .filter(FlowText.Words.class::isInstance).map(item -> ((FlowText.Words) item).text()).toList();
    }

    /** The big key of the picture. */
    Optional<List<String>> pictureKey() {
        return content == null ? Optional.empty() : content.picture().key();
    }

    AbstractButton closeButton() {
        return closeButton;
    }

    AbstractButton previousButton() {
        return previousButton;
    }

    AbstractButton nextButton() {
        return nextButton;
    }

    AbstractButton tryButton() {
        return tryButton;
    }

    JCheckBox showAtStartBox() {
        return showAtStartBox;
    }

    /** The tip's text, wrapped in the card's width, its keys as light keycaps in the theme's accent. */
    private static final class TextView extends JComponent {

        private FlowText flow = new FlowText(List.of());

        @Override
        public Dimension getPreferredSize() {
            int width = innerWidth();
            return new Dimension(width, flow.height(this, textFont(), width));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D painter = (Graphics2D) graphics.create();
            try {
                painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color ink = UIManager.getColor("Label.foreground");
                flow.paintOutlined(painter, this, textFont(), 0, 0, getWidth(), ink == null ? Color.BLACK : ink,
                        ThemeColors.accent());
            } finally {
                painter.dispose();
            }
        }
    }
}
