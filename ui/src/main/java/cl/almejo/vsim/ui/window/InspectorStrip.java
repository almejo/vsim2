package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.inline.ProblemBadge;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.theme.ThemeColors;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The fixed strip of section icons on the right edge of an editor view (option A of the 2026-11-06 mockup): one
 * button per {@link InspectorPanel} section, the open one with a soft accent background and an accent bar on its right
 * edge, a count badge at the top right of an icon, and the button that opens or closes the panel at the bottom. It
 * lives outside the panel's split, so it stays (with its badges) while the panel is closed. Colours are read from the
 * theme every time it paints; sizes are UI-scaled.
 */
final class InspectorStrip extends JPanel {

    /** The strip's width, before UI scaling. */
    static final int WIDTH = 40;
    private static final int BUTTON_SIZE = 32;
    private static final int BUTTON_GAP = 4;
    private static final int ICON_SIZE = 18;
    private static final int CORNER = 7;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_INSET = 6;
    private static final int BADGE_HEIGHT = 15;
    private static final int BADGE_PADDING = 3;
    private static final float BADGE_FONT_FACTOR = 0.75f;
    /** Larger counts show as «99+»: the pill stays the size of the icon's corner. */
    private static final int LARGEST_SHOWN_COUNT = 99;
    /** The soft accent behind the open section's icon and a hovered one: the accent at about a fifth. */
    private static final int SOFT_ACCENT_ALPHA = 56;
    private static final Color FALLBACK_WARNING = new Color(0xEDA200);

    private final Consumer<String> sectionClicked;
    private final Map<String, SectionButton> buttons = new LinkedHashMap<>();
    /** The sections' buttons, one under the other from the top; the open/close button is alone at the bottom. */
    private final JPanel sectionButtons = new JPanel(new GridLayout(0, 1));
    private final StripButton openClose;
    private boolean open;

    /**
     * @param sectionClicked what a click on a section's icon does ({@link InspectorPanel#toggleTab})
     * @param openOrClose    what the button at the bottom does
     */
    InspectorStrip(Consumer<String> sectionClicked, Runnable openOrClose) {
        super(new BorderLayout());
        this.sectionClicked = sectionClicked;
        sectionButtons.setOpaque(false);
        openClose = new StripButton();
        openClose.addActionListener(event -> openOrClose.run());
        add(sectionButtons, BorderLayout.NORTH);
        add(openClose, BorderLayout.SOUTH);
        showOpenClose();
    }

    void addSection(String id, String title, String iconName) {
        SectionButton button = new SectionButton(title, Icons.get(iconName, ICON_SIZE));
        button.addActionListener(event -> sectionClicked.accept(id));
        buttons.put(id, button);
        sectionButtons.add(button);
        revalidate();
    }

    void setBadge(String id, Optional<InspectorPanel.ShownBadge> badge) {
        SectionButton button = buttons.get(id);
        button.badge = badge;
        button.repaint();
    }

    void showShortcut(String id, Action action) {
        buttons.get(id).follow(action);
    }

    /** Highlights {@code active} (empty: none, the panel is closed) and shows the bottom button for {@code isOpen}. */
    void showState(Optional<String> active, boolean isOpen) {
        buttons.forEach((id, button) -> button.setActive(active.filter(id::equals).isPresent()));
        open = isOpen;
        showOpenClose();
    }

    private void showOpenClose() {
        String text = Messages.text(open ? "inspector.close" : "inspector.open");
        openClose.setIcon(Icons.get(open ? InspectorPanel.CLOSE_ICON : InspectorPanel.OPEN_ICON, ICON_SIZE));
        openClose.setToolTipText(text);
        openClose.getAccessibleContext().setAccessibleName(text);
    }

    List<String> sectionIds() {
        return List.copyOf(buttons.keySet());
    }

    String tooltip(String id) {
        return buttons.get(id).getToolTipText();
    }

    Optional<String> highlighted() {
        return buttons.entrySet().stream().filter(entry -> entry.getValue().active).map(Map.Entry::getKey)
                .findFirst();
    }

    void click(String id) {
        buttons.get(id).doClick();
    }

    void clickOpenClose() {
        openClose.doClick();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(WIDTH), super.getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(UIScale.scale(WIDTH), 0);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(UIScale.scale(WIDTH), Integer.MAX_VALUE);
    }

    @Override
    public Insets getInsets() {
        int gap = UIScale.scale(BUTTON_GAP);
        return new Insets(gap, 1, gap, 0);
    }

    /** A rule on the left edge, between the panel (or the canvas) and the strip. */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        ThemeColors.paintRule(graphics, this, 0, 0, 1, getHeight());
    }

    private static Color softAccent() {
        Color accent = ThemeColors.accent();
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), SOFT_ACCENT_ALPHA);
    }

    /** White or black, whichever reads better on {@code background}. */
    private static Color textOn(Color background) {
        return ReadableColors.contrast(background, Color.WHITE) >= ReadableColors.contrast(background, Color.BLACK)
                ? Color.WHITE : Color.BLACK;
    }

    static Color badgeBackground(InspectorPanel.Badge kind) {
        return switch (kind) {
            case ERROR -> ProblemBadge.background();
            case WARNING -> {
                yield ThemeColors.ui("Actions.Yellow", FALLBACK_WARNING);
            }
            case QUIET -> ReadableColors.mutedText();
        };
    }

    /** A strip button: the full strip wide, a 32 px rounded square for its icon, painted here, not by the theme. */
    private static class StripButton extends JButton {

        StripButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(WIDTH), UIScale.scale(BUTTON_SIZE + BUTTON_GAP));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        /** Where the rounded square of the icon goes: centred across, at the top of the button's height. */
        int squareLeft() {
            return (getWidth() - UIScale.scale(BUTTON_SIZE)) / 2;
        }

        int squareTop() {
            return (getHeight() - UIScale.scale(BUTTON_SIZE)) / 2;
        }

        boolean highlighted() {
            return getModel().isRollover();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D local = (Graphics2D) graphics.create();
            try {
                local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int size = UIScale.scale(BUTTON_SIZE);
                int corner = UIScale.scale(CORNER);
                if (highlighted()) {
                    local.setColor(softAccent());
                    local.fillRoundRect(squareLeft(), squareTop(), size, size, corner, corner);
                }
                Icon icon = getIcon();
                if (icon != null) {
                    icon.paintIcon(this, local, squareLeft() + (size - icon.getIconWidth()) / 2,
                            squareTop() + (size - icon.getIconHeight()) / 2);
                }
                paintExtras(local);
            } finally {
                local.dispose();
            }
        }

        /** What a section's button adds over its icon: the accent bar and the badge. */
        void paintExtras(Graphics2D graphics) {
        }
    }

    /** A section's button: its icon, its tooltip («Propiedades», «Propiedades · Alt+3»), the bar and the badge. */
    private static final class SectionButton extends StripButton {

        private final String title;
        private final PropertyChangeListener keyListener = change -> showTooltip();
        private Optional<Action> shortcut = Optional.empty();
        private Optional<InspectorPanel.ShownBadge> badge = Optional.empty();
        private boolean active;

        SectionButton(String title, Icon icon) {
            this.title = title;
            setIcon(icon);
            getAccessibleContext().setAccessibleName(title);
            showTooltip();
        }

        void follow(Action action) {
            shortcut.ifPresent(previous -> previous.removePropertyChangeListener(keyListener));
            shortcut = Optional.of(action);
            action.addPropertyChangeListener(keyListener);
            showTooltip();
        }

        private void showTooltip() {
            Optional<KeyStroke> stroke = shortcut.map(action -> action.getValue(Action.ACCELERATOR_KEY))
                    .filter(KeyStroke.class::isInstance).map(KeyStroke.class::cast);
            setToolTipText(stroke.map(key -> Messages.format("inspector.tooltip", title, KeyNames.describe(key)))
                    .orElse(title));
        }

        void setActive(boolean newActive) {
            if (active != newActive) {
                active = newActive;
                repaint();
            }
        }

        @Override
        boolean highlighted() {
            return active || super.highlighted();
        }

        @Override
        void paintExtras(Graphics2D graphics) {
            if (active) {
                int inset = UIScale.scale(BAR_INSET);
                int barWidth = UIScale.scale(BAR_WIDTH);
                graphics.setColor(ThemeColors.accent());
                graphics.fillRoundRect(getWidth() - barWidth, squareTop() + inset, barWidth,
                        UIScale.scale(BUTTON_SIZE) - 2 * inset, barWidth, barWidth);
            }
            badge.ifPresent(shown -> paintBadge(graphics, shown));
        }

        private void paintBadge(Graphics2D graphics, InspectorPanel.ShownBadge shown) {
            String text = shown.count() > LARGEST_SHOWN_COUNT ? LARGEST_SHOWN_COUNT + "+"
                    : Integer.toString(shown.count());
            Font base = UIManager.getFont("Label.font");
            Font font = (base != null ? base : getFont()).deriveFont(Font.BOLD,
                    (base != null ? base : getFont()).getSize2D() * BADGE_FONT_FACTOR);
            FontMetrics metrics = graphics.getFontMetrics(font);
            int height = UIScale.scale(BADGE_HEIGHT);
            int width = Math.max(height, metrics.stringWidth(text) + 2 * UIScale.scale(BADGE_PADDING));
            int right = squareLeft() + UIScale.scale(BUTTON_SIZE);
            int top = squareTop() + 1;
            Color background = badgeBackground(shown.kind());
            graphics.setColor(background);
            graphics.fillRoundRect(right - width, top, width, height, height, height);
            graphics.setColor(shown.kind() == InspectorPanel.Badge.ERROR ? ProblemBadge.foreground()
                    : textOn(background));
            graphics.setFont(font);
            graphics.drawString(text, right - width + (width - metrics.stringWidth(text)) / 2,
                    top + (height - metrics.getHeight()) / 2 + metrics.getAscent());
        }
    }
}
