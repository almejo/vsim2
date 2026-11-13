package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.theme.ThemeColors;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The signal card of a lit net: «Red de 4 bits = 0xB», the value in every format, its problem, «La maneja», «Llega
 * a» and the tunnels it passes through, with a 📌 to pin it (✕ once pinned). Each part is a button that asks to go
 * there. It is made of Swing children, so fonts come from the look and feel (FlatLaf style classes, which follow the
 * interface zoom) and every size is scaled with {@link UIScale} when it is laid out; its background, border and the
 * accent of a pinned card are read when it paints. It fades as a whole by {@link #setOpacity}.
 * <p>
 * The pointer moving from the card onto one of its items is not leaving it: {@link Listener#pointerLeft()} comes only
 * when the pointer is outside the card.
 */
final class SignalCard extends JComponent {

    /** What the card's user is told. */
    interface Listener {

        /** 📌 was pressed. */
        void pinPressed();

        /** ✕ was pressed on a pinned card. */
        void closePressed();

        /** A part was clicked: go there. */
        void endPressed(NetEnd end);

        void pointerEntered();

        void pointerLeft();
    }

    /** Unscaled sizes, in pixels at 100 %. */
    private static final int PADDING_VERTICAL = 6;
    private static final int PADDING_HORIZONTAL = 10;
    private static final int SECTION_GAP = 5;
    private static final int COLUMN_GAP = 10;
    private static final int CORNER = 10;
    private static final int ICON_SIZE = 14;
    private static final int LINK_PADDING = 1;
    /** The waveform panel's pushpin and the find bar's cross, recoloured by FlatLaf in dark themes. */
    private static final String PIN_ICON = "waveform-pin";
    private static final String CLOSE_ICON = "find-close";
    private static final float BORDER_WIDTH = 1f;
    private static final float PINNED_BORDER_WIDTH = 2f;
    private static final String SMALL = "small";
    private static final String SEMIBOLD = "semibold";
    private static final String STYLE_CLASS = "FlatLaf.styleClass";
    private static final Color FALLBACK_BACKGROUND = new Color(0xF7F7F7);
    private static final Color FALLBACK_BORDER = Color.GRAY;

    private final Listener listener;
    private final PointerOverCard pointerOverCard = new PointerOverCard();
    private final List<EndButton> endButtons = new ArrayList<>();
    private NetSummary summary;
    private boolean pinned;
    private float opacity = 1f;
    private AbstractButton pinButton;

    SignalCard(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        addMouseListener(pointerOverCard);
    }

    /** Shows {@code summary}; built again only when it or the pinning changed (the pointer may be over an item). */
    void show(NetSummary newSummary, boolean newPinned) {
        Objects.requireNonNull(newSummary, "newSummary");
        if (newSummary.equals(summary) && newPinned == pinned && getComponentCount() > 0) {
            return;
        }
        summary = newSummary;
        pinned = newPinned;
        rebuild();
    }

    boolean isPinned() {
        return pinned;
    }

    /** 1 = fully shown; lower while it fades in the grace. */
    void setOpacity(float newOpacity) {
        float clamped = Math.max(0f, Math.min(1f, newOpacity));
        if (clamped != opacity) {
            opacity = clamped;
            repaint();
        }
    }

    float opacity() {
        return opacity;
    }

    /**
     * The card left the pane: the pointer is no longer over it. Swing sends no exit to a removed component, so without
     * this the next card would never say the pointer entered it (and would fade under the pointer).
     */
    void forgetThePointer() {
        pointerOverCard.inside = false;
    }

    @Override
    public void removeNotify() {
        forgetThePointer();
        super.removeNotify();
    }

    /** The interface zoom or the theme changed: the scaled padding is worked out again. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (summary != null) {
            rebuild();
        }
    }

    @Override
    public void paint(Graphics graphics) {
        Graphics2D faded = (Graphics2D) graphics.create();
        try {
            faded.setComposite(AlphaComposite.SrcOver.derive(opacity));
            super.paint(faded);
        } finally {
            faded.dispose();
        }
    }

    /** A rounded panel in the tooltip colours; a pinned card has a thicker border in the accent. */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D card = (Graphics2D) graphics.create();
        try {
            card.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float borderWidth = UIScale.scale(pinned ? PINNED_BORDER_WIDTH : BORDER_WIDTH);
            float corner = UIScale.scale((float) CORNER);
            RoundRectangle2D shape = new RoundRectangle2D.Float(borderWidth / 2, borderWidth / 2,
                    getWidth() - borderWidth, getHeight() - borderWidth, corner, corner);
            card.setColor(uiColor("ToolTip.background", uiColor("Panel.background", FALLBACK_BACKGROUND)));
            card.fill(shape);
            card.setColor(pinned ? ThemeColors.accent() : uiColor("Component.borderColor", FALLBACK_BORDER));
            card.setStroke(new BasicStroke(borderWidth));
            card.draw(shape);
        } finally {
            card.dispose();
        }
    }

    /** Every text on the card, top to bottom: for tests and for reading it aloud. */
    List<String> texts() {
        List<String> texts = new ArrayList<>();
        collectTexts(this, texts);
        return texts;
    }

    /** The button of the part whose text is {@code text} («NOT U2 · in»). */
    Optional<AbstractButton> endButton(String text) {
        return endButtons.stream().filter(button -> button.getText().equals(text))
                .map(AbstractButton.class::cast).findFirst();
    }

    /** 📌 on an unpinned card, ✕ on a pinned one. */
    AbstractButton pinButton() {
        return pinButton;
    }

    private void rebuild() {
        removeAll();
        endButtons.clear();
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(PADDING_VERTICAL), UIScale.scale(PADDING_HORIZONTAL),
                UIScale.scale(PADDING_VERTICAL), UIScale.scale(PADDING_HORIZONTAL)));
        add(header());
        List<NetCardText.Row> rows = NetCardText.valueRows(summary);
        if (!rows.isEmpty()) {
            add(grid(rows));
        }
        summary.problem().ifPresent(problem -> add(line(NetCardText.problem(problem), problemColor())));
        addSection(NetCardText.driversTitle(), summary.drivers());
        addSection(NetCardText.readersTitle(), summary.readers());
        if (!summary.tunnelNames().isEmpty()) {
            add(grid(summary.tunnelNames().stream()
                    .map(name -> new NetCardText.Row(NetCardText.tunnelsTitle(), NetCardText.tunnel(name)))
                    .toList()));
        }
        add(hint());
        listenToThePointer(this);
        revalidate();
        repaint();
    }

    private JComponent header() {
        JPanel header = row(new BorderLayout(UIScale.scale(COLUMN_GAP), 0));
        JLabel title = new JLabel(NetCardText.header(summary));
        title.putClientProperty(STYLE_CLASS, SEMIBOLD);
        header.add(title, BorderLayout.CENTER);
        pinButton = pinned
                ? iconButton(Icons.get(CLOSE_ICON, ICON_SIZE), "netCard.close", listener::closePressed)
                : iconButton(Icons.get(PIN_ICON, ICON_SIZE), "netCard.pin", listener::pinPressed);
        header.add(pinButton, BorderLayout.EAST);
        return header;
    }

    private static AbstractButton iconButton(Icon icon, String tooltipKey, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(Messages.text(tooltipKey));
        button.getAccessibleContext().setAccessibleName(Messages.text(tooltipKey));
        styleAsLink(button);
        button.addActionListener(event -> action.run());
        return button;
    }

    /** Two columns: «con signo» and «-5». */
    private JComponent grid(List<NetCardText.Row> rows) {
        JPanel grid = row(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        for (int index = 0; index < rows.size(); index++) {
            constraints.gridy = index;
            constraints.gridx = 0;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 0, 0, UIScale.scale(COLUMN_GAP));
            JLabel label = new JLabel(rows.get(index).label());
            label.putClientProperty(STYLE_CLASS, SMALL);
            label.setForeground(secondaryColor());
            grid.add(label, constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            constraints.insets = new Insets(0, 0, 0, 0);
            grid.add(new JLabel(rows.get(index).text()), constraints);
        }
        return grid;
    }

    private void addSection(String title, List<NetEnd> ends) {
        JLabel heading = new JLabel(title);
        heading.putClientProperty(STYLE_CLASS, SMALL);
        heading.setForeground(secondaryColor());
        heading.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(SECTION_GAP), 0, 0, 0));
        add(left(heading));
        if (ends.isEmpty()) {
            add(left(new JLabel(NetCardText.nobody())));
            return;
        }
        for (NetEnd end : ends) {
            EndButton button = new EndButton(end);
            endButtons.add(button);
            add(left(button));
        }
    }

    private JComponent hint() {
        JLabel hint = new JLabel(Messages.text(pinned ? "netCard.hintPinned" : "netCard.hint"));
        hint.putClientProperty(STYLE_CLASS, SMALL);
        hint.setForeground(secondaryColor());
        hint.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(SECTION_GAP), 0, 0, 0));
        return left(hint);
    }

    private static JComponent line(String text, Color color) {
        JLabel line = new JLabel(text);
        line.setForeground(color);
        return left(line);
    }

    private static JPanel row(java.awt.LayoutManager layout) {
        JPanel row = new JPanel(layout);
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        return component;
    }

    private static void styleAsLink(AbstractButton button) {
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setFocusable(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(LINK_PADDING), 0, UIScale.scale(LINK_PADDING), 0));
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static Color secondaryColor() {
        return uiColor("Label.disabledForeground", Color.GRAY);
    }

    private static Color problemColor() {
        return uiColor("Actions.Red", new Color(0xDB5860));
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private void listenToThePointer(Container container) {
        for (Component child : container.getComponents()) {
            child.removeMouseListener(pointerOverCard);
            child.addMouseListener(pointerOverCard);
            if (child instanceof Container nested) {
                listenToThePointer(nested);
            }
        }
    }

    private static void collectTexts(Container container, List<String> texts) {
        for (Component child : container.getComponents()) {
            String text = child instanceof JLabel label ? label.getText()
                    : child instanceof AbstractButton button ? button.getText() : null;
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
            if (child instanceof Container nested) {
                collectTexts(nested, texts);
            }
        }
    }

    /** A part of the net as a link: a click asks to go there. */
    private final class EndButton extends JButton {

        EndButton(NetEnd end) {
            super(NetCardText.end(end));
            styleAsLink(this);
            addActionListener(event -> listener.endPressed(end));
        }
    }

    /** Whether the pointer is over the card or any of its items, told once on the way in and once on the way out. */
    private final class PointerOverCard extends MouseAdapter {

        private boolean inside;

        @Override
        public void mouseEntered(MouseEvent event) {
            if (!inside) {
                inside = true;
                listener.pointerEntered();
            }
        }

        @Override
        public void mouseExited(MouseEvent event) {
            Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), SignalCard.this);
            if (inside && !contains(point)) {
                inside = false;
                listener.pointerLeft();
            }
        }
    }
}
