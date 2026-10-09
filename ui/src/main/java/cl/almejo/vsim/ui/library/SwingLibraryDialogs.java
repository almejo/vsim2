package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.io.DefinitionFormat;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The real, modal dialogs of the templates and library features (EDT only; never used by tests). */
public final class SwingLibraryDialogs implements LibraryDialogs {

    public static final SwingLibraryDialogs INSTANCE = new SwingLibraryDialogs();

    private static final Logger LOGGER = LoggerFactory.getLogger(SwingLibraryDialogs.class);

    private Path lastDirectory = Path.of(System.getProperty("user.home"));

    private SwingLibraryDialogs() {
    }

    @Override
    public Optional<ComponentForm.Result> editComponent(java.awt.Component parent, ComponentForm form) {
        String title = form.mode() == ComponentForm.Mode.CREATE ? Messages.text("template.dialog.createTitle")
                : Messages.text("template.dialog.updateTitle");
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title,
                Dialog.ModalityType.APPLICATION_MODAL);
        JButton accept = new JButton(Messages.text("template.dialog.accept"));
        JButton cancel = new JButton(Messages.text("template.dialog.cancel"));
        boolean[] accepted = {false};
        accept.setEnabled(form.canAccept());
        form.addListener(() -> accept.setEnabled(form.canAccept()));
        accept.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });
        cancel.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(accept);
        dialog.getContentPane().add(new ComponentFormPanel(form), BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(accept);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return accepted[0] && form.canAccept() ? Optional.of(form.result()) : Optional.empty();
    }

    @Override
    public Optional<String> askNewName(java.awt.Component parent, String currentName) {
        return TextPrompt.DIALOG.ask(parent, Messages.text("library.title"), Messages.text("library.rename.message"),
                currentName);
    }

    @Override
    public Optional<Path> chooseExportFile(java.awt.Component parent, String suggestedFileName) {
        JFileChooser chooser = chooser(Messages.text("library.export.title"));
        chooser.setSelectedFile(lastDirectory.resolve(suggestedFileName).toFile());
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        Path chosen = chooser.getSelectedFile().toPath();
        if (!LibraryDirectory.isComponentFile(chosen)) {
            chosen = chosen.resolveSibling(chosen.getFileName() + DefinitionFormat.FILE_SUFFIX);
        }
        remember(chosen);
        if (Files.exists(chosen) && JOptionPane.showConfirmDialog(parent,
                Messages.format("dialog.overwrite.message", chosen.getFileName()),
                Messages.text("dialog.overwrite.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) {
            return Optional.empty();
        }
        return Optional.of(chosen);
    }

    @Override
    public List<Path> chooseImportFiles(java.awt.Component parent) {
        JFileChooser chooser = chooser(Messages.text("library.import.title"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return List.of();
        }
        List<Path> files = Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList();
        if (!files.isEmpty()) {
            remember(files.get(0));
        }
        return files;
    }

    @Override
    public ImportChoice askImportCollision(java.awt.Component parent, String componentName) {
        Object[] options = {Messages.text("library.import.replace"), Messages.text("library.import.keepBoth"),
                Messages.text("template.dialog.cancel")};
        int answer = JOptionPane.showOptionDialog(parent, Messages.format("library.import.collision", componentName),
                Messages.text("library.import.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                options, options[1]);
        return switch (answer) {
            case 0 -> ImportChoice.REPLACE;
            case 1 -> ImportChoice.KEEP_BOTH;
            default -> ImportChoice.CANCEL;
        };
    }

    @Override
    public boolean confirmRemove(java.awt.Component parent, String componentName) {
        return JOptionPane.showConfirmDialog(parent, Messages.format("library.remove.confirm", componentName),
                Messages.text("action.removeFromLibrary"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    @Override
    public void showInFolder(java.awt.Component parent, Path file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                Desktop.getDesktop().browseFileDirectory(file.toFile());
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
                    && file.getParent() != null) {
                Desktop.getDesktop().open(file.getParent().toFile());
                return;
            }
        } catch (IOException | UnsupportedOperationException exception) {
            LOGGER.warn("Could not show {} in its folder", file, exception);
        }
        showMessage(parent, Messages.format("library.folder.message", file.toAbsolutePath()));
    }

    @Override
    public void showMessage(java.awt.Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, Messages.text("library.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showError(java.awt.Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, Messages.text("library.title"), JOptionPane.ERROR_MESSAGE);
    }

    private JFileChooser chooser(String title) {
        JFileChooser chooser = new JFileChooser(lastDirectory.toFile());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || LibraryDirectory.isComponentFile(file.toPath());
            }

            @Override
            public String getDescription() {
                return Messages.text("file.filter.components");
            }
        });
        return chooser;
    }

    private void remember(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
    }
}
