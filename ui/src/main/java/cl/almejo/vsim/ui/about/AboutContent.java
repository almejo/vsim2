package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.AppIcons;
import net.miginfocom.swing.MigLayout;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.List;

/** The body of «Acerca de VSim 2…»: logo, name and version, what it is, who, licence, history, credits and links. */
final class AboutContent extends JPanel {

    static final String REPOSITORY = "https://github.com/almejo/vsim2";
    static final String RELEASES = REPOSITORY + "/releases";
    static final String COPY_BUTTON_NAME = "about.copy";
    static final String LOGO_NAME = "about.logo";
    private static final int LOGO_SIZE = 96;
    private static final float TITLE_SCALE = 1.6f;

    /** The libraries VSim really ships with (gradle/libs.versions.toml); product names are not translated. */
    static final List<String> CREDITS = List.of("FlatLaf", "MigLayout", "JFreeSVG", "JFreePDF", "Apache PDFBox",
            "MCP Java SDK", "Jetty", "Jackson", "SLF4J", "Logback");

    AboutContent(AboutInformation information, ClipboardTarget clipboard, LinkOpener links) {
        super(new MigLayout("insets 16, wrap 1, fillx", "[grow]"));
        add(logo(), "align center");
        add(title(Messages.format("about.name", information.version())), "align center");
        add(new JLabel(Messages.text("about.description")), "align center");
        add(new JLabel(Messages.text("about.author")), "align center, gaptop 8");
        add(new JLabel(Messages.text("about.license")), "align center");
        add(new JLabel(Messages.text("about.history")), "align center");
        add(new JLabel(Messages.format("about.credits", String.join(", ", CREDITS))), "align center, gaptop 8");
        add(linkRow(Messages.text("about.repository"), REPOSITORY, links), "growx, gaptop 8");
        add(linkRow(Messages.text("about.releases"), RELEASES, links), "growx");
        JButton copy = new JButton(Messages.text("about.copy"));
        copy.setName(COPY_BUTTON_NAME);
        copy.addActionListener(event -> clipboard.copy(information.asText()));
        add(copy, "align right, gaptop 8");
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * TITLE_SCALE));
        return label;
    }

    private static JLabel logo() {
        JLabel label = new JLabel(new LogoIcon());
        label.setName(LOGO_NAME);
        return label;
    }

    /** A caption, the address as selectable text and, where the browser can be started, an «Abrir» button. */
    private static JPanel linkRow(String caption, String link, LinkOpener links) {
        JPanel row = new JPanel(new MigLayout("insets 0", "[][grow, fill][]"));
        row.add(new JLabel(caption));
        JTextField address = new JTextField(link);
        address.setEditable(false);
        address.setBorder(null);
        address.setOpaque(false);
        row.add(address);
        if (links.available()) {
            JButton open = new JButton(Messages.text("about.open"));
            open.addActionListener(event -> links.open(link));
            row.add(open);
        }
        return row;
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
