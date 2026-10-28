package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Objects;

/**
 * The «Descripción» box at the bottom of Propiedades (and of the configure dialog): the rules and assumptions of the
 * selected part, read only. A click on its title folds or unfolds it, and every box remembers the choice in
 * {@link AppPreferences#descriptionFolded()}. The text wraps to the box's width and scrolls past
 * {@link #MAXIMUM_LINES} lines, so a long text (the RAM's) never pushes the rest out of sight. Font and colours come
 * from the look and feel each time it changes.
 */
public final class DescriptionBox extends JPanel {

    /** Lines the box shows before it scrolls. */
    static final int MAXIMUM_LINES = 14;
    /** The width assumed before the box has one. */
    private static final int FALLBACK_WIDTH = 260;
    private static final int GAP = 4;

    private final AppPreferences preferences;
    private final JButton title = new JButton();
    private final JTextArea body = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    public DescriptionBox(String text, AppPreferences preferences) {
        super(new BorderLayout(0, GAP));
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        setOpaque(false);
        title.putClientProperty("JButton.buttonType", "borderless");
        title.setHorizontalAlignment(SwingConstants.LEADING);
        title.setFocusable(true);
        title.addActionListener(event -> toggle());
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
        showFolding();
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
        }
        if (UIManager.getColor("Label.foreground") != null) {
            body.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    /** Folds an unfolded box and the other way round, and remembers it for every box from now on. */
    void toggle() {
        preferences.setDescriptionFolded(!isFolded());
        showFolding();
        revalidate();
        repaint();
    }

    private void showFolding() {
        boolean folded = isFolded();
        title.setText(Messages.text(folded ? "properties.description.folded" : "properties.description.unfolded"));
        title.getAccessibleContext().setAccessibleDescription(Messages.text(folded
                ? "properties.description.unfold" : "properties.description.fold"));
        scroll.setVisible(!folded);
    }

    boolean isFolded() {
        return preferences.descriptionFolded();
    }

    boolean isBodyVisible() {
        return scroll.isVisible();
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
        Dimension titleSize = title.getPreferredSize();
        if (!scroll.isVisible()) {
            return new Dimension(titleSize.width, titleSize.height);
        }
        int width = getWidth() > 0 ? getWidth() : FALLBACK_WIDTH;
        int bodyHeight = Math.min(bodyHeightAt(width), maximumBodyHeight());
        return new Dimension(titleSize.width, titleSize.height + GAP + bodyHeight);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, title.getPreferredSize().height);
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

    /** {@link #MAXIMUM_LINES} lines of the body's font. */
    int maximumBodyHeight() {
        return body.getFontMetrics(body.getFont()).getHeight() * MAXIMUM_LINES;
    }

    int headerHeight() {
        return title.getPreferredSize().height;
    }
}
