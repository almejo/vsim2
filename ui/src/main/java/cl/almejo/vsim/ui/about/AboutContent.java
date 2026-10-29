package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.AppIcons;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The body of «Acerca de VSim 2»: a card (logo, name, version and what it is, a label/value grid, the credits) over a
 * footer band with «Copiar información» and «Cerrar». Colours come from the look and feel at paint time, so a theme
 * change follows: the card uses the list background, the footer the window's, a separator line between them.
 */
final class AboutContent extends JPanel {

    static final String REPOSITORY = "https://github.com/almejo/vsim2";
    static final String RELEASES = REPOSITORY + "/releases";
    static final String COPY_BUTTON_NAME = "about.copy";
    static final String CLOSE_BUTTON_NAME = "about.close";
    static final String LOGO_NAME = "about.logo";
    static final String VERSION_NAME = "about.version";
    private static final int LOGO_SIZE = 72;
    private static final int WIDTH = 460;
    private static final float TITLE_SCALE = 1.6f;
    private static final float SMALL_SCALE = 0.85f;
    private static final String CLOSE_KEY = "about.closeDialog";

    /** The libraries VSim really ships with (gradle/libs.versions.toml); product names are not translated. */
    static final List<String> CREDITS = List.of("FlatLaf", "MigLayout", "JFreeSVG", "JFreePDF", "Apache PDFBox",
            "MCP Java SDK", "Jetty", "Jackson", "SLF4J", "Logback");

    private final JPanel card = new Card();
    private final JPanel footer = new Footer();
    private final JButton closeButton = new JButton(Messages.text("about.close"));

    AboutContent(AboutInformation information, ClipboardTarget clipboard, LinkOpener links) {
        super(new BorderLayout());
        buildCard(information, links);
        buildFooter(information, clipboard);
        add(card, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private void buildCard(AboutInformation information, LinkOpener links) {
        card.setLayout(new MigLayout("insets 20 24 16 24, wrap 2, fillx", "[]16[grow, fill]"));
        card.add(heading(information), "span 2, gapbottom 14");
        addRow(Messages.text("about.label.author"), new JLabel(Messages.text("about.value.author")));
        addRow(Messages.text("about.label.license"), new JLabel(Messages.text("about.value.license")));
        addRow(Messages.text("about.label.code"), address(REPOSITORY, links));
        addRow(Messages.text("about.label.releases"), address(RELEASES, links));
        addRow(Messages.text("about.label.history"), new JLabel(Messages.text("about.value.history")));
        JLabel credits = new MutedLabel(Messages.format("about.credits", String.join(", ", CREDITS)));
        credits.setFont(credits.getFont().deriveFont(credits.getFont().getSize2D() * SMALL_SCALE));
        card.add(creditsText(credits), "span 2, gaptop 12");
    }

    /** The credits wrap inside the card's width, as HTML does for a label. */
    private static JLabel creditsText(JLabel credits) {
        credits.setText("<html><body style='width:" + (WIDTH - 2 * 24 - 8) + "px'>" + credits.getText()
                + "</body></html>");
        return credits;
    }

    private JPanel heading(AboutInformation information) {
        JPanel heading = new JPanel(new MigLayout("insets 0, wrap 2", "[]16[]"));
        heading.setOpaque(false);
        JLabel logo = new JLabel(new LogoIcon());
        logo.setName(LOGO_NAME);
        heading.add(logo, "spany 3");
        JLabel name = new JLabel(Messages.text("about.name"));
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() * TITLE_SCALE));
        heading.add(name, "aligny bottom");
        MutedLabel version = new MutedLabel(Messages.format("about.version", information.version()));
        version.setName(VERSION_NAME);
        heading.add(version);
        heading.add(new JLabel(Messages.text("about.description")), "aligny top");
        return heading;
    }

    private void addRow(String label, Component value) {
        card.add(new MutedLabel(label));
        card.add(value);
    }

    /** The address without its scheme: a link where the browser can be started, selectable text otherwise. */
    private static Component address(String link, LinkOpener links) {
        String shown = link.substring(link.indexOf("//") + 2);
        if (links.available()) {
            return new LinkLabel(shown, link, links);
        }
        JTextField field = new JTextField(shown);
        field.setEditable(false);
        field.setBorder(null);
        field.setOpaque(false);
        field.setCaret(new DefaultCaret() {
            @Override
            public boolean isVisible() {
                return false;
            }
        });
        return field;
    }

    private void buildFooter(AboutInformation information, ClipboardTarget clipboard) {
        footer.setLayout(new MigLayout("insets 10 24 12 24", "[grow][][]", "[]"));
        JButton copy = new JButton(Messages.text("about.copy"));
        copy.setName(COPY_BUTTON_NAME);
        copy.addActionListener(event -> clipboard.copy(information.asText()));
        closeButton.setName(CLOSE_BUTTON_NAME);
        closeButton.addActionListener(event -> close());
        footer.add(new JLabel(), "growx");
        footer.add(copy);
        footer.add(closeButton);
    }

    private void close() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.dispose();
        }
    }

    JButton closeButton() {
        return closeButton;
    }

    JPanel card() {
        return card;
    }

    JPanel footer() {
        return footer;
    }

    /** Makes «Cerrar» the root pane's default button and Esc close the window. */
    void bindTo(JRootPane rootPane) {
        rootPane.setDefaultButton(closeButton);
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), CLOSE_KEY);
        rootPane.getActionMap().put(CLOSE_KEY, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                close();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(size.width, UIScale.scale(WIDTH)), size.height);
    }

    private static Color color(String key, String fallbackKey, String lastResortKey) {
        Color color = UIManager.getColor(key);
        if (color == null) {
            color = UIManager.getColor(fallbackKey);
        }
        return color != null ? color : UIManager.getColor(lastResortKey);
    }

    /** The card: opaque, with the list background read when it is painted. */
    private static final class Card extends JPanel {
        @Override
        public Color getBackground() {
            return color("List.background", "TextField.background", "Panel.background");
        }
    }

    /** The footer band: the window's background with a one-pixel separator line above it. */
    private static final class Footer extends JPanel {
        @Override
        public Color getBackground() {
            return UIManager.getColor("Panel.background");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            graphics.setColor(color("Separator.foreground", "Component.borderColor", "Label.foreground"));
            graphics.fillRect(0, 0, getWidth(), 1);
        }
    }

    /** A label in the muted colour of the look and feel, read each time it is painted. */
    private static final class MutedLabel extends JLabel {
        MutedLabel(String text) {
            super(text);
        }

        @Override
        public Color getForeground() {
            return color("Label.disabledForeground", "Component.infoForeground", "Label.foreground");
        }
    }

    /** An address in the link colour, with the hand cursor and an underline while the pointer is over it. */
    static final class LinkLabel extends JLabel {

        private final String link;
        private final LinkOpener links;
        private boolean hovered;

        LinkLabel(String shown, String link, LinkOpener links) {
            super(shown);
            this.link = link;
            this.links = links;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    activate();
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        void activate() {
            links.open(link);
        }

        @Override
        public Color getForeground() {
            return color("Component.linkColor", "Hyperlink.linkColor", "Label.foreground");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (hovered) {
                graphics.setColor(getForeground());
                int baseline = getFontMetrics(getFont()).getAscent() + 1;
                graphics.drawLine(0, baseline, getFontMetrics(getFont()).stringWidth(getText()), baseline);
            }
        }
    }

    /** The largest application image drawn at {@link #LOGO_SIZE} logical pixels, so a HiDPI screen gets all of it. */
    private static final class LogoIcon implements Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            List<Image> images = AppIcons.images();
            if (images.isEmpty()) {
                return;
            }
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                canvas.drawImage(images.get(images.size() - 1), x, y, LOGO_SIZE, LOGO_SIZE, null);
            } finally {
                canvas.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return LOGO_SIZE;
        }

        @Override
        public int getIconHeight() {
            return LOGO_SIZE;
        }
    }
}
