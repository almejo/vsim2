package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;

/** The real dialog: the message, and a «Detalles» button that opens the technical text under it. */
public final class SwingFileProblemDialog implements FileProblemDialog {

    private static final int TEXT_WIDTH_PIXELS = 420;

    @Override
    public void show(Component parent, String title, String message, String details) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel label = new JLabel("<html><body style='width: " + TEXT_WIDTH_PIXELS + "px'>"
                + escaped(message).replace("\n", "<br>") + "</body></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
        if (!details.isBlank()) {
            JTextArea text = new JTextArea(details, 6, 40);
            text.setEditable(false);
            text.setLineWrap(true);
            JScrollPane scroll = new JScrollPane(text);
            scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            scroll.setPreferredSize(new Dimension(TEXT_WIDTH_PIXELS, 110));
            scroll.setVisible(false);
            JButton toggle = new JButton(Messages.text("fileproblem.details"));
            toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            toggle.addActionListener(action -> {
                scroll.setVisible(!scroll.isVisible());
                Window window = SwingUtilities.getWindowAncestor(content);
                if (window != null) {
                    window.pack();
                }
            });
            content.add(Box.createVerticalStrut(8));
            content.add(toggle);
            content.add(scroll);
        }
        JOptionPane.showMessageDialog(parent, content, title, JOptionPane.ERROR_MESSAGE);
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
