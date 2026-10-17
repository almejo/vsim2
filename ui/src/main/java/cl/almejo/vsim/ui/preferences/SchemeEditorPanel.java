package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorRole;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.NamedColorScheme;
import net.miginfocom.swing.MigLayout;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The content of «Personalizar…» (M6.8): the scheme's name, one colour button per {@link ColorRole} (swatch and hex;
 * a click opens the colour chooser) and a live {@link SchemePreview} of the edited scheme. The chooser is injected so
 * tests never show it.
 */
public final class SchemeEditorPanel extends JPanel {

    /** Asks the user for a colour starting at {@code initial}; empty when cancelled. */
    @FunctionalInterface
    public interface ColorChooser {
        Optional<Color> choose(Component parent, String title, Color initial);
    }

    static final String ROLE_KEY_PREFIX = "scheme.role.";
    private static final int SWATCH_SIZE = 14;
    private static final int CHECKER = 4;

    private final NamedColorScheme start;
    private final ColorChooser chooser;
    private final JTextField nameField;
    private final Map<ColorRole, JButton> buttons = new EnumMap<>(ColorRole.class);
    private final Map<ColorRole, JLabel> labels = new EnumMap<>(ColorRole.class);
    private final SchemePreview preview;
    private ColorScheme scheme;

    public SchemeEditorPanel(NamedColorScheme start, ColorChooser chooser) {
        super(new MigLayout("insets dialog, wrap 4", "[right][fill, sizegroup colour]24[right][fill, sizegroup colour]"));
        this.start = start;
        this.chooser = chooser;
        this.scheme = start.scheme();
        this.nameField = new JTextField(start.name(), 24);
        this.preview = new SchemePreview(scheme);

        add(new JLabel(Messages.text("schemeEditor.name")));
        add(nameField, "span 3, growx, wrap");
        List<ColorRole> roles = List.of(ColorRole.values());
        int half = (roles.size() + 1) / 2;
        for (int row = 0; row < half; row++) {
            addRole(roles.get(row));
            if (row + half < roles.size()) {
                addRole(roles.get(row + half));
            } else {
                add(new JLabel(), "span 2, wrap");
            }
        }
        add(new JLabel(Messages.text("schemeEditor.preview")), "span 4, gaptop 12, left, wrap");
        add(preview, "span 4, grow, push");
    }

    private void addRole(ColorRole role) {
        String roleName = roleName(role);
        JLabel label = new JLabel(roleName);
        JButton button = new JButton();
        button.setHorizontalAlignment(JButton.LEFT);
        button.addActionListener(event -> chooser.choose(this, roleName, role.get(scheme))
                .ifPresent(color -> setColor(role, color)));
        labels.put(role, label);
        buttons.put(role, button);
        showColor(role);
        add(label);
        add(button);
    }

    /** The translated name of a role ({@code scheme.role.<key>}). */
    public static String roleName(ColorRole role) {
        return Messages.text(ROLE_KEY_PREFIX + role.key());
    }

    /** What the role's button does with the chooser's answer: the scheme, its button and the preview follow. */
    public void setColor(ColorRole role, Color color) {
        scheme = scheme.with(role, color);
        showColor(role);
        preview.setScheme(scheme);
    }

    public ColorScheme scheme() {
        return scheme;
    }

    /** The typed name, trimmed; the starting name when it is blank. */
    public String name() {
        String typed = nameField.getText().strip();
        return typed.isEmpty() ? start.name() : typed;
    }

    SchemePreview preview() {
        return preview;
    }

    JTextField nameField() {
        return nameField;
    }

    Map<ColorRole, JButton> roleButtons() {
        return buttons;
    }

    JButton roleButton(ColorRole role) {
        return buttons.get(role);
    }

    JLabel roleLabel(ColorRole role) {
        return labels.get(role);
    }

    private void showColor(ColorRole role) {
        Color color = role.get(scheme);
        JButton button = buttons.get(role);
        button.setText(hex(color));
        button.setIcon(new Swatch(color));
    }

    /** {@code #RRGGBB}, with the alpha appended ({@code #RRGGBBAA}) when the colour is not opaque. */
    static String hex(Color color) {
        String rgb = String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        return color.getAlpha() == 255 ? rgb : rgb + String.format(Locale.ROOT, "%02X", color.getAlpha());
    }

    /** The colour over a light checkerboard, so a translucent colour shows as such; the border follows the theme. */
    private record Swatch(Color color) implements Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            for (int row = 0; row < SWATCH_SIZE; row += CHECKER) {
                for (int column = 0; column < SWATCH_SIZE; column += CHECKER) {
                    graphics.setColor((row + column) / CHECKER % 2 == 0 ? Color.WHITE : Color.LIGHT_GRAY);
                    graphics.fillRect(x + column, y + row, CHECKER, CHECKER);
                }
            }
            graphics.setColor(color);
            graphics.fillRect(x, y, SWATCH_SIZE, SWATCH_SIZE);
            Color border = UIManager.getColor("Component.borderColor");
            graphics.setColor(border == null ? Color.GRAY : border);
            graphics.drawRect(x, y, SWATCH_SIZE - 1, SWATCH_SIZE - 1);
        }

        @Override
        public int getIconWidth() {
            return SWATCH_SIZE;
        }

        @Override
        public int getIconHeight() {
            return SWATCH_SIZE;
        }
    }
}
