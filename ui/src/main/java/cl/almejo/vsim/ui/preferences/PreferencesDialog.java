package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.ai.AiPreferencesPanel;
import cl.almejo.vsim.ui.ai.AiServerService;
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
import java.util.function.Consumer;

/**
 * The modal preferences dialog: tabs «General», «Atajos de teclado» and «IA». Changes are live, so its only button
 * closes it.
 */
public final class PreferencesDialog {

    private PreferencesDialog() {
    }

    public static void show(Component parent, AppPreferences preferences) {
        show(parent, tabs(preferences, PreferencesDialog::chooseDirectory, Optional.empty()));
    }

    /** With the application's AI server: «IA» shows and copies a port given on the command line for this session. */
    public static void show(Component parent, AppPreferences preferences, AiServerService aiServer) {
        show(parent, tabs(preferences, PreferencesDialog::chooseDirectory, Optional.of(aiServer)));
    }

    private static void show(Component parent, JTabbedPane tabs) {
        Object[] options = {Messages.text("preferences.close")};
        JOptionPane.showOptionDialog(parent, tabs, Messages.text("preferences.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        dispose(tabs); // modal: it is closed now
    }

    /** Lets the tabs stop following the preferences once the dialog is closed. */
    static void dispose(JTabbedPane tabs) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabs.getComponentAt(index) instanceof PreferencesForm form) {
                form.dispose();
            } else if (tabs.getComponentAt(index) instanceof AiPreferencesPanel ai) {
                ai.dispose();
            }
        }
    }

    /**
     * «General» (the unchanged form), «Atajos de teclado» (M6-C) and «IA» (IA.1); package-private so a headless test
     * sees them.
     */
    static JTabbedPane tabs(AppPreferences preferences, PreferencesForm.DirectoryChooser directoryChooser) {
        return tabs(preferences, directoryChooser, Optional.empty());
    }

    static JTabbedPane tabs(AppPreferences preferences, PreferencesForm.DirectoryChooser directoryChooser,
                            Optional<AiServerService> aiServer) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.text("preferences.tab.general"), new PreferencesForm(preferences, directoryChooser));
        tabs.addTab(Messages.text("preferences.tab.shortcuts"), new ShortcutsPanel(new KeyBindings(preferences)));
        Consumer<String> clipboard = AiPreferencesPanel.systemClipboard();
        tabs.addTab(Messages.text("preferences.tab.ai"),
                aiServer.map(server -> new AiPreferencesPanel(preferences, server, clipboard))
                        .orElseGet(() -> new AiPreferencesPanel(preferences, clipboard)));
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
