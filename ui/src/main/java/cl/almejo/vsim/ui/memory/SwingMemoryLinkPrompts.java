package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.nio.file.Path;
import java.util.Optional;

/** {@link MemoryLinkPrompts} with real file choosers and dialogs; the folder last chosen is offered next time. */
final class SwingMemoryLinkPrompts implements MemoryLinkPrompts {

    private Path lastFolder = Path.of(System.getProperty("user.home"));

    @Override
    public Optional<Path> chooseLinkFile(java.awt.Component parent) {
        return choose(parent, "memory.link.title");
    }

    @Override
    public Optional<Path> chooseMemoryFile(java.awt.Component parent) {
        return choose(parent, "memory.importComponent.title");
    }

    private Optional<Path> choose(java.awt.Component parent, String titleKey) {
        JFileChooser chooser = new JFileChooser(lastFolder.toFile());
        chooser.setDialogTitle(Messages.text(titleKey));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        Path chosen = chooser.getSelectedFile().toPath();
        Path folder = chosen.toAbsolutePath().getParent();
        if (folder != null) {
            lastFolder = folder;
        }
        return Optional.of(chosen);
    }

    @Override
    public Optional<MemoryFileImport.Proposal> chooseKind(java.awt.Component parent, String fileName,
                                                          MemoryFileImport.Proposal proposal) {
        return MemoryKindDialog.ask(parent, fileName, proposal);
    }

    @Override
    public void inform(java.awt.Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, Messages.text("memory.editor.confirmTitle"),
                JOptionPane.INFORMATION_MESSAGE);
    }
}
