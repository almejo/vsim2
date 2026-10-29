package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;
import java.util.function.Supplier;

/** The dialog itself: a modal, fixed-size window owned by the editor, with {@link AboutContent} and no option pane. */
final class SwingAboutPresenter implements AboutPresenter {

    private final Supplier<AboutInformation> information;
    private final ClipboardTarget clipboard;
    private final LinkOpener links;

    SwingAboutPresenter(Supplier<AboutInformation> information, ClipboardTarget clipboard, LinkOpener links) {
        this.information = information;
        this.clipboard = clipboard;
        this.links = links;
    }

    @Override
    public void show(Component owner) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, Messages.text("about.title"), JDialog.DEFAULT_MODALITY_TYPE);
        AboutContent content = new AboutContent(information.get(), clipboard, links);
        dialog.setContentPane(content);
        content.bindTo(dialog.getRootPane());
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }
}
