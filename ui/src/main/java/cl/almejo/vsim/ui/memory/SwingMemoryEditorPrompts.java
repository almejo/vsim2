package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.memory.MemoryFileFormat;
import cl.almejo.vsim.core.memory.MemoryFormats;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** {@link MemoryEditorPrompts} with the real windows and the system clipboard, over {@code parent}. */
public final class SwingMemoryEditorPrompts implements MemoryEditorPrompts {

    private final Supplier<Component> parent;
    private Path lastFolder = Path.of(System.getProperty("user.home"));

    public SwingMemoryEditorPrompts(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public Optional<Path> chooseImport() {
        JFileChooser chooser = new JFileChooser(lastFolder.toFile());
        chooser.setDialogTitle(Messages.text("memory.import.title"));
        if (chooser.showOpenDialog(parent.get()) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        Path chosen = chooser.getSelectedFile().toPath();
        remember(chosen);
        return Optional.of(chosen);
    }

    @Override
    public Optional<SaveChoice> chooseSave(String suggestedName) {
        JFileChooser chooser = new JFileChooser(lastFolder.toFile());
        chooser.setDialogTitle(Messages.text("memory.export.title"));
        chooser.setAcceptAllFileFilterUsed(false);
        List<MemoryFileFormat> formats = MemoryFormats.all().stream().filter(MemoryFileFormat::canWrite).toList();
        for (MemoryFileFormat format : formats) {
            chooser.addChoosableFileFilter(filterOf(format));
        }
        chooser.setFileFilter(chooser.getChoosableFileFilters()[0]);
        chooser.setSelectedFile(new File(lastFolder.toFile(), suggestedName));
        if (chooser.showSaveDialog(parent.get()) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        MemoryFileFormat format = formats.get(List.of(chooser.getChoosableFileFilters())
                .indexOf(chooser.getFileFilter()));
        Path target = MemoryFileIo.withExtension(chooser.getSelectedFile().toPath(), format);
        if (!FileDialogs.confirmOverwrite(parent.get(), target)) {
            return Optional.empty();
        }
        remember(target);
        return Optional.of(new SaveChoice(target, format));
    }

    private static FileFilter filterOf(MemoryFileFormat format) {
        String key = "memory.format." + format.id();
        String description = Messages.text(key);
        return new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    private void remember(Path chosen) {
        Path folder = chosen.toAbsolutePath().getParent();
        if (folder != null) {
            lastFolder = folder;
        }
    }

    @Override
    public Optional<FillRequest> askFill(String firstAddress, String lastAddress) {
        JTextField first = new JTextField(firstAddress, 10);
        JTextField last = new JTextField(lastAddress, 10);
        JTextField value = new JTextField("0", 10);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new javax.swing.JLabel(Messages.text("memory.fill.from")));
        form.add(first);
        form.add(new javax.swing.JLabel(Messages.text("memory.fill.to")));
        form.add(last);
        form.add(new javax.swing.JLabel(Messages.text("memory.fill.value")));
        form.add(value);
        int answer = JOptionPane.showConfirmDialog(parent.get(), form, Messages.text("memory.fill.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION
                ? Optional.of(new FillRequest(first.getText(), last.getText(), value.getText()))
                : Optional.empty();
    }

    @Override
    public Optional<String> askAddress() {
        Object answer = JOptionPane.showInputDialog(parent.get(), Messages.text("memory.goTo.prompt"),
                Messages.text("memory.goTo.title"), JOptionPane.PLAIN_MESSAGE, null, null, "");
        return answer == null ? Optional.empty() : Optional.of(String.valueOf(answer));
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(parent.get(), message, Messages.text("memory.editor.confirmTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public void inform(String message) {
        JOptionPane.showMessageDialog(parent.get(), message, Messages.text("memory.editor.confirmTitle"),
                JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Override
    public Optional<String> clipboardText() {
        try {
            return Optional.ofNullable((String) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor));
        } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException | IllegalStateException
                 exception) {
            return Optional.empty();
        }
    }
}
