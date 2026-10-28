package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Objects;

/**
 * The «Descripción» box of Propiedades (and of the configure dialog): the rules and assumptions of the selected
 * part, read only, under a plain heading. The text wraps to the box's width. {@link #filling} asks for the height the
 * whole text needs and lets the layout give it all the space left (it scrolls only when the text is longer than that
 * space); {@link #capped} stops at {@link #MAXIMUM_LINES} lines and scrolls past them, so a long text (the RAM's)
 * never makes a dialog taller than the screen. Font and colours come from the look and feel each time it changes.
 */
public final class DescriptionBox extends JPanel {

    /** Lines a {@link #capped} box shows before it scrolls. */
    static final int MAXIMUM_LINES = 14;
    /** Lines a box keeps visible at least, whatever the space left. */
    private static final int MINIMUM_LINES = 3;
    /** The width assumed before the box has one. */
    private static final int FALLBACK_WIDTH = 260;
    private static final int GAP = 4;

    private final int maximumLines;
    private final JLabel title = new JLabel(Messages.text("properties.description.title"));
    private final JTextArea body = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    /** A box that takes all the height its container has left (Propiedades). */
    public static DescriptionBox filling(String text) {
        return new DescriptionBox(text, Integer.MAX_VALUE);
    }

    /** A box of at most {@link #MAXIMUM_LINES} lines (the configure dialog). */
    public static DescriptionBox capped(String text) {
        return new DescriptionBox(text, MAXIMUM_LINES);
    }

    private DescriptionBox(String text, int maximumLines) {
        super(new BorderLayout(0, GAP));
        this.maximumLines = maximumLines;
        setOpaque(false);
        body.setEditable(false);
        body.setFocusable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(title, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        applyLabelLook();
        showText(text);
        // The wrapped height depends on the width: ask the layout again when the width changes.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                revalidate();
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (body != null) {
            applyLabelLook();
        }
    }

    /** The body reads like the panel's labels in the current theme (a text area has its own font and colours). */
    private void applyLabelLook() {
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            body.setFont(font);
            title.setFont(font.deriveFont(Font.BOLD));
        }
        if (UIManager.getColor("Label.foreground") != null) {
            body.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    public String text() {
        return body.getText();
    }

    /** Shows {@code text} instead (the part's parameters changed); a blank text hides the whole box. */
    public void showText(String text) {
        body.setText(Objects.requireNonNull(text, "text"));
        body.setCaretPosition(0);
        setVisible(!text.isBlank());
        revalidate();
        repaint();
    }

    /**
     * As wide as the column lets it be (never wider than its title asks), as tall as its title plus the text wrapped
     * to its current width, up to {@link #maximumBodyHeight()}.
     */
    @Override
    public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : FALLBACK_WIDTH;
        int bodyHeight = Math.min(bodyHeightAt(width), maximumBodyHeight());
        return new Dimension(title.getPreferredSize().width, headerHeight() + GAP + bodyHeight);
    }

    /** The title and a few lines, so the box never collapses to nothing when the parameters take the space. */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, headerHeight() + GAP + lineHeight() * MINIMUM_LINES);
    }

    /** The height the whole text needs wrapped to {@code width}. */
    int bodyHeightAt(int width) {
        int textWidth = Math.max(1, width - scroll.getVerticalScrollBar().getPreferredSize().width);
        JTextArea measure = new JTextArea(body.getText());
        measure.setLineWrap(true);
        measure.setWrapStyleWord(true);
        measure.setFont(body.getFont());
        measure.setBorder(BorderFactory.createEmptyBorder());
        measure.setSize(textWidth, Short.MAX_VALUE);
        return measure.getPreferredSize().height;
    }

    /** The cap of a {@link #capped} box; no cap ({@link Integer#MAX_VALUE}) for a {@link #filling} one. */
    int maximumBodyHeight() {
        return maximumLines == Integer.MAX_VALUE ? Integer.MAX_VALUE : lineHeight() * maximumLines;
    }

    private int lineHeight() {
        return body.getFontMetrics(body.getFont()).getHeight();
    }

    int headerHeight() {
        return title.getPreferredSize().height;
    }
}
