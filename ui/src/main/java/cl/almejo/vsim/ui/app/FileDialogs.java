package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class FileDialogs {

    private FileDialogs() {
    }

    public static Optional<Path> chooseOpen(java.awt.Component parent, Path directory) {
        JFileChooser chooser = new JFileChooser(directory.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter(Messages.text("file.filter.circuits"), "vsim", "json"));
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                ? Optional.of(chooser.getSelectedFile().toPath())
                : Optional.empty();
    }

    public static Optional<Path> chooseSave(java.awt.Component parent, Path directory, String suggestedName) {
        return chooseSave(parent, directory, suggestedName,
                new FileNameExtensionFilter(Messages.text("file.filter.circuits"), "vsim"), FileNames::withExtension);
    }

    /**
     * A save dialog for any kind of file: {@code filter} chooses what is listed and {@code withExtension} completes
     * the chosen name (M6.1-C uses it for CSV). An existing file is replaced only after the user confirms.
     */
    public static Optional<Path> chooseSave(java.awt.Component parent, Path directory, String suggestedName,
                                            FileFilter filter, UnaryOperator<Path> withExtension) {
        JFileChooser chooser = new JFileChooser(directory.toFile());
        chooser.setFileFilter(filter);
        chooser.setSelectedFile(directory.resolve(suggestedName).toFile());
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        Path target = withExtension.apply(chooser.getSelectedFile().toPath());
        if (Files.exists(target)) {
            int answer = JOptionPane.showConfirmDialog(parent,
                    Messages.format("dialog.overwrite.message", target.getFileName()),
                    Messages.text("dialog.overwrite.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return Optional.empty();
            }
        }
        return Optional.of(target);
    }
}
