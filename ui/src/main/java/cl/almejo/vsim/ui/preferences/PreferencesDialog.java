package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.ai.AiPreferencesPanel;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutsPanel;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * The modal preferences dialog: a list of groups («General», «Aspecto», «Accesibilidad», «Archivos», then «Atajos de
 * teclado» and «IA») with a search field above it. Changes are live, so its only button closes it.
 */
public final class PreferencesDialog {

    /** The groups, in order: their ids are stored as the last group seen and name the palette's entries. */
    public static final List<String> GROUP_IDS = List.of("general", "appearance", "accessibility", "files",
            "shortcuts", "ai");

    /** The group where the sheet of shortcuts sends the user to see and change them all. */
    public static final String SHORTCUTS_GROUP = "shortcuts";

    private static final String TITLE_KEY_PREFIX = "preferences.group.";
    private static final String AI_KEYWORDS_KEY = "preferences.keywords.ai";

    private PreferencesDialog() {
    }

    /** The message key of the title of the group {@code groupId} (one of {@link #GROUP_IDS}). */
    public static String titleKey(String groupId) {
        return TITLE_KEY_PREFIX + groupId;
    }

    /** Opens the dialog on {@code groupId}, or on the last group seen when empty. */
    public static void show(Component parent, AppPreferences preferences, Optional<String> groupId) {
        show(parent, panel(preferences, PreferencesDialog::chooseDirectory, Optional.empty(), groupId));
    }

    /**
     * With the application's AI server: «IA» shows and copies a port given on the command line for this session.
     * Opens on {@code groupId}, or on the last group seen when empty.
     */
    public static void show(Component parent, AppPreferences preferences, AiServerService aiServer,
                            Optional<String> groupId) {
        show(parent, panel(preferences, PreferencesDialog::chooseDirectory, Optional.of(aiServer), groupId));
    }

    private static void show(Component parent, PreferencesPanel panel) {
        Object[] options = {Messages.text("preferences.close")};
        try {
            JOptionPane.showOptionDialog(parent, panel, Messages.text("preferences.title"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        } finally {
            panel.dispose(); // modal: it is closed now
        }
    }

    /** The whole content of the dialog; package-private so a headless test builds it without showing anything. */
    static PreferencesPanel panel(AppPreferences preferences, PreferencesForm.DirectoryChooser directoryChooser,
                                  Optional<AiServerService> aiServer, Optional<String> groupId) {
        PreferencesForm form = new PreferencesForm(preferences, directoryChooser);
        ShortcutsPanel shortcuts = new ShortcutsPanel(new KeyBindings(preferences));
        AiPreferencesPanel ai = aiServer.map(server -> new AiPreferencesPanel(preferences, server,
                        AiPreferencesPanel.systemClipboard()))
                .orElseGet(() -> new AiPreferencesPanel(preferences, AiPreferencesPanel.systemClipboard()));
        List<PreferencesGroup> groups = new ArrayList<>();
        groups.add(group("general", false, form.general(), form.general()::filter));
        groups.add(group("appearance", false, form.appearance(), form.appearance()::filter));
        groups.add(group("accessibility", false, form.accessibility(), form.accessibility()::filter));
        groups.add(group("files", false, form.files(), form.files()::filter));
        groups.add(group(SHORTCUTS_GROUP, true, shortcuts, query -> {
            shortcuts.setQuery(query.strip());
            return shortcuts.matchCount(query.strip());
        }));
        groups.add(group("ai", false, ai, query -> matchingTexts(ai, query)));
        return new PreferencesPanel(preferences, groups, groupId, shortcuts::isCapturing, () -> {
            form.dispose();
            ai.dispose();
        });
    }

    private static PreferencesGroup group(String id, boolean separatorBefore, JComponent page,
                                          ToIntFunction<String> filter) {
        return new PreferencesGroup(id, titleKey(id), page, separatorBefore, filter);
    }

    /** «IA» is shown whole: its count is how many of its labels, buttons and keywords the query finds. */
    private static int matchingTexts(AiPreferencesPanel ai, String query) {
        List<String> texts = new ArrayList<>(ai.searchTexts());
        Messages.find(AI_KEYWORDS_KEY).ifPresent(texts::add);
        return (int) texts.stream().filter(text -> PreferencesSearch.matches(text, query)).count();
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
