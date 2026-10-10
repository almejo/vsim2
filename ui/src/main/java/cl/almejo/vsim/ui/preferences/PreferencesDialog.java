package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutsPanel;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The modal preferences dialog: tabs «General» and «Atajos de teclado». Changes are live, so its only button closes it. */
public final class PreferencesDialog {

    private PreferencesDialog() {
    }

public static void show(Component parent, AppPreferences preferences) {
        JTabbedPane tabs = tabs(preferences, PreferencesDialog::chooseDirectory);
        Object[] options = {Messages.text("preferences.close")};
        JOptionPane.showOptionDialog(parent, tabs, Messages.text("preferences.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }

    /** «General» (the unchanged form) and «Atajos de teclado» (M6-C); package-private so a headless test sees them. */
    static JTabbedPane tabs(AppPreferences preferences, PreferencesForm.DirectoryChooser directoryChooser) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.text("preferences.tab.general"), new PreferencesForm(preferences, directoryChooser));
        tabs.addTab(Messages.text("preferences.tab.shortcuts"), new ShortcutsPanel(new KeyBindings(preferences)));
        return tabs;
    }

    static Optional<Path> chooseDirectory(Component parent, Path current) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(Messages.text("preferences.library.chooseTitle"));
        Path existing = current.toAbsolutePath();
        while (existing != null && !Files.isDirectory(existing)) {
            existing = existing.getParent();
        }
        if (existing != null) {
            chooser.setCurrentDirectory(existing.toFile());
        }
        int answer = chooser.showDialog(parent, Messages.text("preferences.library.approve"));
        return answer == JFileChooser.APPROVE_OPTION ? Optional.of(chooser.getSelectedFile().toPath())
                : Optional.empty();
    }
}
