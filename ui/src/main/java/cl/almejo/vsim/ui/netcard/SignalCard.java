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

        /** The card was dragged: it wants to be at {@code location} (in its parent), which the user may clamp. */
        void dragged(Point location);
    }

    /** Unscaled sizes, in pixels at 100 %. */
    private static final int PADDING_VERTICAL = 6;
    private static final int PADDING_HORIZONTAL = 10;
    private static final int SECTION_GAP = 5;
    private static final int COLUMN_GAP = 10;
    private static final int CORNER = 10;
    private static final int ICON_SIZE = 14;
    private static final int LINK_PADDING = 1;
    /** How far the pointer must move after a press before it is a drag and not a click on the card's body. */
    private static final int DRAG_THRESHOLD = 4;
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
    private final Dragger dragger = new Dragger();
    private final List<EndButton> endButtons = new ArrayList<>();
    /** The value texts of the grid, top to bottom: a new value of the same net only changes their text. */
    private final List<JLabel> valueLabels = new ArrayList<>();
    private NetSummary summary;
    private boolean pinned;
    private float opacity = 1f;
    private AbstractButton pinButton;
    private JLabel title;
    private JPanel header;
    /** What the children were built for; null before the first build or after the look and feel may have changed. */
    private Structure builtFor;

    SignalCard(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        addMouseListener(pointerOverCard);
        addMouseListener(dragger);
        addMouseMotionListener(dragger);
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    /**
     * What decides the card's children: everything but the value texts. While it stays the same (a clock or a
     * counter on the net) the texts change in place, so 📌, ✕ and the part links under the pointer are never replaced
     * between a press and its release.
     */
    private record Structure(int width, List<NetEnd> drivers, List<NetEnd> readers,
                             List<String> tunnelNames, Optional<NetProblem> problem, List<String> valueRowLabels) {

        static Structure of(NetSummary summary) {
            return new Structure(summary.width(), NetCardText.drivers(summary), summary.readers(),
                    summary.tunnelNames(), summary.problem(),
                    NetCardText.valueRows(summary).stream().map(NetCardText.Row::label).toList());
        }
    }

    /**
     * Shows {@code summary}: built again only when its structure or the pinning changed, else only the header and
     * the value texts change (the pointer may be over an item).
     */
    void show(NetSummary newSummary, boolean newPinned) {
        Objects.requireNonNull(newSummary, "newSummary");
        if (newSummary.equals(summary) && newPinned == pinned && builtFor != null) {
            return;
        }
        boolean pinningChanged = newPinned != pinned;
        summary = newSummary;
        pinned = newPinned;
        Structure structure = Structure.of(newSummary);
        if (structure.equals(builtFor)) {
            if (pinningChanged) {
                swapThePinButton(); // in place: the pointer may be dragging a child of the card
            }
            changeTheValueTexts();
        } else {
            rebuild();
        }
    }

    /**
     * The card comes back over the pane: the look and feel may have changed while it was away (a theme, the
     * interface zoom), and only a card in the component tree hears it, so the next {@link #show} builds it again.
     */
    void lookMayHaveChanged() {
        builtFor = null;
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

    private void changeTheValueTexts() {
        title.setText(NetCardText.header(summary));
        List<NetCardText.Row> rows = NetCardText.valueRows(summary);
        for (int index = 0; index < rows.size(); index++) {
            valueLabels.get(index).setText(rows.get(index).text());
        }
        revalidate();
        repaint();
    }

    private void rebuild() {
        removeAll();
        endButtons.clear();
        valueLabels.clear();
        builtFor = Structure.of(summary);
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(PADDING_VERTICAL), UIScale.scale(PADDING_HORIZONTAL),
                UIScale.scale(PADDING_VERTICAL), UIScale.scale(PADDING_HORIZONTAL)));
        add(header());
        List<NetCardText.Row> rows = NetCardText.valueRows(summary);
        if (!rows.isEmpty()) {
            add(grid(rows, valueLabels));
        }
        summary.problem().ifPresent(problem -> add(left(new ToneLabel(NetCardText.problem(problem), Tone.PROBLEM))));
        addSection(NetCardText.driversTitle(), NetCardText.drivers(summary));
        addSection(NetCardText.readersTitle(), summary.readers());
        if (!summary.tunnelNames().isEmpty()) {
            add(grid(summary.tunnelNames().stream()
                    .map(name -> new NetCardText.Row(NetCardText.tunnelsTitle(), NetCardText.tunnel(name)))
                    .toList(), new ArrayList<>()));
        }
        listenToThePointer(this);
        revalidate();
        repaint();
    }

    private JComponent header() {
        header = row(new BorderLayout(UIScale.scale(COLUMN_GAP), 0));
        title = new JLabel(NetCardText.header(summary));
        title.putClientProperty(STYLE_CLASS, SEMIBOLD);
        header.add(title, BorderLayout.CENTER);
        pinButton = newPinButton();
        header.add(pinButton, BorderLayout.EAST);
        return header;
    }

    /** 📌 on an unpinned card, ✕ on a pinned one. */
    private AbstractButton newPinButton() {
        return pinned
                ? iconButton(Icons.get(CLOSE_ICON, ICON_SIZE), "netCard.close", listener::closePressed)
                : iconButton(Icons.get(PIN_ICON, ICON_SIZE), "netCard.pin", listener::pinPressed);
    }

    /** The card was pinned with the same content: only the header's button changes. */
    private void swapThePinButton() {
        header.remove(pinButton);
        pinButton = newPinButton();
        header.add(pinButton, BorderLayout.EAST);
        listenToThePointer(header);
        revalidate();
        repaint();
    }

    private static AbstractButton iconButton(Icon icon, String tooltipKey, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(Messages.text(tooltipKey));
        button.getAccessibleContext().setAccessibleName(Messages.text(tooltipKey));
        styleAsLink(button);
        button.addActionListener(event -> action.run());
        return button;
    }

    /** Two columns: «con signo» and «-5»; the texts of the second column are added to {@code texts}. */
    private JComponent grid(List<NetCardText.Row> rows, List<JLabel> texts) {
        JPanel grid = row(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        for (int index = 0; index < rows.size(); index++) {
            constraints.gridy = index;
            constraints.gridx = 0;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 0, 0, UIScale.scale(COLUMN_GAP));
            JLabel label = new ToneLabel(rows.get(index).label(), Tone.SECONDARY);
            label.putClientProperty(STYLE_CLASS, SMALL);
            grid.add(label, constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            constraints.insets = new Insets(0, 0, 0, 0);
            JLabel text = new JLabel(rows.get(index).text());
            texts.add(text);
            grid.add(text, constraints);
        }
        return grid;
    }

    private void addSection(String sectionTitle, List<NetEnd> ends) {
        JLabel heading = new ToneLabel(sectionTitle, Tone.SECONDARY);
        heading.putClientProperty(STYLE_CLASS, SMALL);
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

    /** The colour of a secondary or problem text, by its key in the look and feel. */
    private enum Tone {
        SECONDARY("Label.disabledForeground", Color.GRAY),
        PROBLEM("Actions.Red", new Color(0xDB5860));

        private final String key;
        private final Color fallback;

        Tone(String key, Color fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        Color color() {
            return uiColor(key, fallback);
        }
    }

    /** A label whose colour is read from the look and feel each time it is asked (when it paints), never kept. */
    private static final class ToneLabel extends JLabel {

        private final Tone tone;

        ToneLabel(String text, Tone tone) {
            super(text);
            this.tone = tone;
        }

        @Override
        public Color getForeground() {
            // null while JLabel's constructor installs the look and feel, before this field is set
            return tone == null ? super.getForeground() : tone.color();
        }
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private void listenToThePointer(Container container) {
        for (Component child : container.getComponents()) {
            child.removeMouseListener(pointerOverCard);
            child.addMouseListener(pointerOverCard);
            if (!(child instanceof AbstractButton)) { // 📌, ✕ and the part links keep their clicks
                child.removeMouseListener(dragger);
                child.removeMouseMotionListener(dragger);
                child.addMouseListener(dragger);
                child.addMouseMotionListener(dragger);
            }
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

    /**
     * Moves the card with the pointer once it travelled more than a few pixels from the press (so a click on the
     * body is not a drag). Positions are taken in the card's parent, which the card's own movement does not shift.
     */
    private final class Dragger extends MouseAdapter {

        private Point pressedAt;
        private Point cardAtPress;
        private boolean dragging;

        @Override
        public void mousePressed(MouseEvent event) {
            Container parent = getParent();
            if (parent == null || !SwingUtilities.isLeftMouseButton(event)) {
                pressedAt = null;
                return;
            }
            pressedAt = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), parent);
            cardAtPress = getLocation();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            Container parent = getParent();
            if (pressedAt == null || parent == null) {
                return;
            }
            Point now = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), parent);
            int deltaX = now.x - pressedAt.x;
            int deltaY = now.y - pressedAt.y;
            int threshold = UIScale.scale(DRAG_THRESHOLD);
            if (!dragging && Math.abs(deltaX) <= threshold && Math.abs(deltaY) <= threshold) {
                return;
            }
            dragging = true;
            listener.dragged(new Point(cardAtPress.x + deltaX, cardAtPress.y + deltaY));
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            pressedAt = null;
            dragging = false;
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
