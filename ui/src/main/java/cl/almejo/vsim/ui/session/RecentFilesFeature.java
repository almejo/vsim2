package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.event.MenuListener;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Archivo ▸ Recientes (spec §4.1), right after «Abrir…»: rebuilt from the preferences each time it opens, so every
 * window shows the same list without listening to anything shared. Choosing an entry opens it as Abrir would (the
 * window state is restored by the application); a file that no longer exists is removed with a message instead.
 */
public final class RecentFilesFeature implements EditorExtension {

    private final BiConsumer<Component, Path> missingNotice;

    public RecentFilesFeature() {
        this(RecentFilesFeature::showMissing);
    }

    RecentFilesFeature(BiConsumer<Component, Path> missingNotice) {
        this.missingNotice = missingNotice;
    }

    @Override
    public Runnable install(EditorView view) {
        JMenu file = view.menu(MenuId.FILE);
        JMenu recent = new JMenu(Messages.text("menu.file.recent"));
        MenuListener rebuilder = Menus.onOpen(recent, () -> rebuild(recent, view));
        file.add(recent, positionAfter(file, view.actions().open()));
        return () -> {
            recent.removeMenuListener(rebuilder);
            file.remove(recent);
        };
    }

    public void rebuild(JMenu recent, EditorView view) {
        recent.removeAll();
        RecentFiles recentFiles = new RecentFiles(view.services().preferences());
        List<Path> entries = recentFiles.entries();
        if (entries.isEmpty()) {
            JMenuItem empty = new JMenuItem(Messages.text("recent.empty"));
            empty.setEnabled(false);
            recent.add(empty);
        }
        for (Path path : entries) {
            JMenuItem item = new JMenuItem(entryText(path));
            item.setToolTipText(path.toString());
            item.addActionListener(event -> openRecent(path, view, recentFiles));
            recent.add(item);
        }
        recent.addSeparator();
        JMenuItem clear = new JMenuItem(Messages.text("recent.clear"));
        clear.setEnabled(!entries.isEmpty());
        clear.addActionListener(event -> recentFiles.clear());
        recent.add(clear);
    }

    /** «nombre — carpeta»: the file name without extension and the name of its folder. */
    static String entryText(Path path) {
        // A root has no file name; RecentFiles skips one, and its text is then the path itself.
        String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path parent = path.getParent();
        String folder = parent == null ? "" : parent.getFileName() == null ? parent.toString()
                : parent.getFileName().toString();
        return Messages.format("recent.entry", name, folder);
    }

    /** Right after the item of {@code action}, or at the end when the menu has no such item. */
    static int positionAfter(JMenu menu, Action action) {
        int index = Menus.indexOf(menu, action);
        return index < 0 ? menu.getMenuComponentCount() : index + 1;
    }

    private void openRecent(Path path, EditorView view, RecentFiles recentFiles) {
        if (!Files.isRegularFile(path)) {
            recentFiles.remove(path);
            missingNotice.accept(view, path);
            return;
        }
        view.openOrImport(List.of(path));
    }

    private static void showMissing(Component parent, Path path) {
        JOptionPane.showMessageDialog(parent, Messages.format("recent.missing", path),
                Messages.text("recent.missing.title"), JOptionPane.WARNING_MESSAGE);
    }
}
