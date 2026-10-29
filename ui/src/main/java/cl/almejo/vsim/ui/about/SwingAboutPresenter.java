package cl.almejo.vsim.ui.about;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;
import java.util.function.Supplier;

/** The dialog itself: a modal window over the editor with {@link AboutContent}. */
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
        JDialog dialog = new JDialog(window, Messages.text("action.about"), JDialog.DEFAULT_MODALITY_TYPE);
        dialog.setContentPane(new AboutContent(information.get(), clipboard, links));
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }
}
