package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.ai.AiPreferencesPanel;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutsPanel;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The modal preferences dialog: tabs «General», «Atajos de teclado» and «IA». Changes are live, so its only button
 * closes it.
 */
public final class PreferencesDialog {

    /** The «General» tab, where the menu item opens the dialog. */
    public static final int GENERAL_TAB = 0;

    /** The «Atajos de teclado» tab, where the sheet of shortcuts sends the user to see and change them all. */
    public static final int SHORTCUTS_TAB = 1;

    /** The titles of the tabs, in order: what {@code tab} indexes. */
    static final List<String> TAB_KEYS = List.of("preferences.tab.general", "preferences.tab.shortcuts",
            "preferences.tab.ai");

    /** Until the groups have their own list: the four pages of the form one above the other in the «General» tab. */
    private static final class StackedForm extends JPanel {
        private final PreferencesForm form;

        StackedForm(PreferencesForm form) {
            super(new GridLayout(0, 1));
            this.form = form;
            add(form.general());
            add(form.appearance());
            add(form.accessibility());
            add(form.files());
        }
    }

    private PreferencesDialog() {
    }

    /** Opens the dialog on {@code tab}, an index of {@link #TAB_KEYS}. */
    public static void show(Component parent, AppPreferences preferences, int tab) {
        show(parent, tabs(preferences, PreferencesDialog::chooseDirectory, Optional.empty()), tab);
    }

    /**
     * With the application's AI server: «IA» shows and copies a port given on the command line for this session.
     * Opens on {@code tab}, an index of {@link #TAB_KEYS}.
     */
    public static void show(Component parent, AppPreferences preferences, AiServerService aiServer, int tab) {
        show(parent, tabs(preferences, PreferencesDialog::chooseDirectory, Optional.of(aiServer)), tab);
    }

    private static void show(Component parent, JTabbedPane tabs, int tab) {
        tabs.setSelectedIndex(tab);
        Object[] options = {Messages.text("preferences.close")};
        JOptionPane.showOptionDialog(parent, tabs, Messages.text("preferences.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        dispose(tabs); // modal: it is closed now
    }

    /** Lets the tabs stop following the preferences once the dialog is closed. */
    static void dispose(JTabbedPane tabs) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabs.getComponentAt(index) instanceof StackedForm stacked) {
                stacked.form.dispose();
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
        tabs.addTab(Messages.text(TAB_KEYS.get(0)), new StackedForm(new PreferencesForm(preferences, directoryChooser)));
        tabs.addTab(Messages.text(TAB_KEYS.get(1)), new ShortcutsPanel(new KeyBindings(preferences)));
        Consumer<String> clipboard = AiPreferencesPanel.systemClipboard();
        tabs.addTab(Messages.text(TAB_KEYS.get(2)),
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
