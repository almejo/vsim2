package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The connections sheet (mockup 10): a bold title, the pins that will be left to connect by hand and the pieces that
 * keep their old shape, each with its reason, the pieces that change without trouble, and «Volver» · «Aplicar y
 * conectar a mano» (the default, at the right). A piece that is blocked by itself only gets «Volver». The content is
 * built without a window so tests can read it; {@link #ask} shows it modally.
 */
final class ConnectionsSheet {
    private static final int TEXT_WIDTH = 440;
    private static final float HEADING_GROWTH = 3f;
    private static final String LAYOUT = "insets 16 20 16 20, wrap 1, fillx";
    private static final String BUTTON_LAYOUT = "alignx right, growx 0, gaptop 12";

    private final JPanel content = new JPanel(new MigLayout(LAYOUT, "[grow, fill]"));
    private final JButton back = new JButton(Messages.text("shape.connections.back"));
    private final JButton apply = new JButton(Messages.text("shape.connections.apply"));
    private final boolean blocked;
    private boolean confirmed;

    ConnectionsSheet(ConnectionsQuestion question) {
        blocked = question.blockedInstance();
        content.add(heading());
        addGroup("disconnected", "shape.connections.disconnected", "shape.connections.disconnectedItem",
                question.disconnected());
        addGroup("kept", "shape.connections.kept", "shape.connections.keptItem", question.kept());
        if (blocked) {
            content.add(plainLabel("blocked", Messages.text("shape.connections.blocked")));
        }
        if (!question.unaffectedPieces().isEmpty()) {
            content.add(plainLabel("others", Messages.format(question.unaffectedPieces().size() == 1
                    ? "shape.connections.others.one" : "shape.connections.others.many",
                    String.join(", ", question.unaffectedPieces()))));
        }
        content.add(buttons(), BUTTON_LAYOUT);
    }

    JComponent content() { return content; }

    /** «Aplicar y conectar a mano», or «Volver» when that is the only answer. */
    JButton defaultButton() { return blocked ? back : apply; }

    boolean confirmed() { return confirmed; }

    /** Shows the sheet over {@code owner}'s window and returns true when the user chose to apply. */
    static boolean ask(Component owner, ConnectionsQuestion question) {
        ConnectionsSheet sheet = new ConnectionsSheet(question);
        Window window = SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, Messages.text("shape.connections.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(sheet.content);
        sheet.back.addActionListener(ignored -> dialog.dispose());
        sheet.apply.addActionListener(ignored -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(sheet.defaultButton());
        dialog.getRootPane().registerKeyboardAction(ignored -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return sheet.confirmed;
    }

    private JLabel heading() {
        JLabel heading = new JLabel(Messages.text("shape.connections.heading"),
                UIManager.getIcon("OptionPane.warningIcon"), JLabel.LEADING);
        heading.setName("heading");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + HEADING_GROWTH));
        heading.setIconTextGap(10);
        return heading;
    }

    private void addGroup(String name, String titleKey, String itemKey, List<ConnectionsQuestion.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        String items = entries.stream().map(entry -> "<li>" + Messages.format(itemKey, escape(entry.piece()),
                escape(entry.pin()), escape(entry.touchedNet())) + "</li>").collect(Collectors.joining());
        content.add(label(name, "<b>" + escape(Messages.text(titleKey)) + "</b><ul style='margin: 2px 0 0 16px'>"
                + items + "</ul>"));
    }

    private JPanel buttons() {
        JPanel row = new JPanel(new MigLayout("insets 0", "[]8[]"));
        row.setOpaque(false);
        back.setName("back");
        apply.setName("apply");
        apply.addActionListener(ignored -> confirmed = true);
        row.add(back);
        if (!blocked) {
            row.add(apply);
        }
        return row;
    }

    private static JLabel plainLabel(String name, String text) {
        return label(name, escape(text));
    }

    private static JLabel label(String name, String htmlBody) {
        JLabel label = new JLabel("<html><body style='width: " + TEXT_WIDTH + "px'>" + htmlBody + "</body></html>");
        label.setName(name);
        return label;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
