package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.InspectorPanel;
import net.miginfocom.swing.MigLayout;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Supplier;

/**
 * The card at the top of Propiedades: an icon tile, the name of what is selected and, under it, what kind of thing it
 * is. With nothing selected it is the circuit's card and also counts its parts and wires. Colours (tile, component
 * icon) are read when it paints, so a theme or colour scheme switch shows at once.
 */
final class PropertyCard extends JPanel {

    private static final int TILE = 38;
    private static final int ICON = 24;
    private static final int ARC = 10;
    private static final String CIRCUIT_ICON = "grid";

    private static final PainterRegistry PAINTERS = PainterRegistry.standard();

    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel counts = new JLabel();
    private final JLabel problemsStat = new JLabel();
    private int problemErrors;
    private int problemWarnings;
    private int parts;
    private int wires;
    private final boolean circuit;

    private PropertyCard(Tile tile, String titleText, String subtitleText, boolean circuit) {
        super(new MigLayout("insets 0, gap 10", "[][grow, fill]"));
        this.circuit = circuit;
        setOpaque(false);
        title.putClientProperty("FlatLaf.style", "font: bold +1");
        subtitle.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        counts.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        for (JLabel label : new JLabel[]{title, subtitle, counts}) {
            label.setMinimumSize(new Dimension(0, label.getMinimumSize().height));
        }
        add(tile, "spany 3, aligny center");
        add(title, "wmin 0, wrap");
        add(subtitle, "wmin 0, wrap");
        add(counts, "split 2, wmin 0, hidemode 3");
        add(problemsStat, "gapleft 6, wmin 0, hidemode 3");
        counts.setVisible(circuit);
        problemsStat.setVisible(false);
        setTexts(titleText, subtitleText);
    }

    /** A component: its label (or its type name when it has none) as title, the type name under it. */
    static PropertyCard forComponent(ComponentType type, String typeName, String label, Supplier<ColorScheme> scheme) {
        Tile tile = new Tile(() -> new ComponentIcon(type, PAINTERS.painterFor(type.id()), scheme.get(), ICON));
        return new PropertyCard(tile, titleOf(typeName, label), typeName, false);
    }

    /** The circuit itself, with nothing selected. */
    static PropertyCard forCircuit(String name, int parts, int wires) {
        PropertyCard card = new PropertyCard(new Tile(() -> Icons.get(CIRCUIT_ICON, ICON)),
                name.isBlank() ? Messages.text("properties.circuitUnnamed") : name, Messages.text("properties.none"),
                true);
        card.setCounts(parts, wires);
        return card;
    }

    /** Any other element (a wire, a text, a frame, a note) or several: a title and the icon of its kind. */
    static PropertyCard forElement(String titleText, String iconName) {
        return new PropertyCard(new Tile(() -> Icons.get(iconName, ICON)), titleText, "", false);
    }

    /** The title a component's card has: its label, or its type name when it has none. */
    static String titleOf(String typeName, String label) {
        return label == null || label.isBlank() ? typeName : label;
    }

    void setTexts(String titleText, String subtitleText) {
        title.setText(titleText);
        // a label equal to the type name would say the same twice
        String shownSubtitle = subtitleText.equals(titleText) ? "" : subtitleText;
        subtitle.setText(shownSubtitle);
        subtitle.setVisible(!shownSubtitle.isEmpty());
    }

    void setCounts(int parts, int wires) {
        this.parts = parts;
        this.wires = wires;
        counts.setText(Messages.format("properties.card.parts", parts) + " · "
                + Messages.format("properties.card.wires", wires));
    }

    /**
     * The circuit's problems as the badge on the strip counts them: the stat shows only when there are some, coloured
     * like the badge (red with errors, otherwise amber). The colour is read at every paint.
     */
    void setProblems(int errors, int warnings) {
        problemErrors = errors;
        problemWarnings = warnings;
        int shown = errors > 0 ? errors : warnings;
        problemsStat.setText(shown == 0 ? "" : "· " + Messages.format("properties.card.problems", shown));
        problemsStat.setVisible(shown > 0);
    }

    int problemErrors() {
        return problemErrors;
    }

    int problemWarnings() {
        return problemWarnings;
    }

    /** The stat's text without its separator; empty when it is hidden. */
    String problemsText() {
        String text = problemsStat.getText();
        return text.startsWith("· ") ? text.substring(2) : text;
    }

    /** The colour the stat paints with now: the badge's. */
    Color problemsColor() {
        return InspectorPanel.badgeColor(problemErrors > 0 ? InspectorPanel.Badge.ERROR : InspectorPanel.Badge.WARNING);
    }

    @Override
    public void paint(Graphics graphics) {
        problemsStat.setForeground(problemsColor()); // a theme or scheme switch shows at once
        super.paint(graphics);
    }

    String title() {
        return title.getText();
    }

    String subtitle() {
        return subtitle.getText();
    }

    int parts() {
        return parts;
    }

    int wires() {
        return wires;
    }

    boolean isCircuit() {
        return circuit;
    }

    /** The square tile with the icon; the icon is asked for at every paint (the colour scheme may have changed). */
    private static final class Tile extends JComponent {

        private final Supplier<Icon> icon;

        Tile(Supplier<Icon> icon) {
            this.icon = icon;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(TILE, TILE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D local = (Graphics2D) graphics.create();
            try {
                local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color raised = UIManager.getColor("Button.background");
                local.setColor(raised != null ? raised : Color.LIGHT_GRAY);
                local.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                Color border = UIManager.getColor("Component.borderColor");
                if (border != null) {
                    local.setColor(border);
                    local.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
                }
                Icon current = icon.get();
                current.paintIcon(this, local, (getWidth() - current.getIconWidth()) / 2,
                        (getHeight() - current.getIconHeight()) / 2);
            } finally {
                local.dispose();
            }
        }
    }
}
