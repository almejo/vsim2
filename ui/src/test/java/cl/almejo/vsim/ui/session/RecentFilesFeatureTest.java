package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

class RecentFilesFeatureTest {

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<Path> opened = new ArrayList<>();
    private final List<Path> missing = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of()), NO_WINDOW);
        view.setCircuitOpener(opened::addAll);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private RecentFilesFeature feature() {
        return new RecentFilesFeature((parent, path) -> missing.add(path));
    }

    private static List<String> entries(JMenu menu) {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < menu.getMenuComponentCount(); index++) {
            Component component = menu.getMenuComponent(index);
            entries.add(component instanceof JMenuItem item ? item.getText()
                    : component instanceof JSeparator ? "—" : component.getClass().getSimpleName());
        }
        return entries;
    }

    private static JMenu recentMenu(JMenu file) {
        for (Component component : file.getMenuComponents()) {
            if (component instanceof JMenu menu && "Recientes".equals(menu.getText())) {
                return menu;
            }
        }
        throw new AssertionError("no Recientes menu");
    }

    /** What opening the menu does: every menu listener hears menuSelected. */
    private static void open(JMenu menu) {
        for (MenuListener listener : menu.getMenuListeners()) {
            listener.menuSelected(new MenuEvent(menu));
        }
    }

    private static JMenuItem item(JMenu menu, String text) {
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no item " + text + " in " + entries(menu));
    }

    @Test
    void recentsComeRightAfterOpenAndGoAwayOnDispose() {
        JMenu file = view.menu(MenuId.FILE);
        List<String> before = entries(file);
        Runnable disposer = feature().install(view);

        List<String> after = entries(file);
        assertThat(after.get(after.indexOf("Abrir…") + 1)).isEqualTo("Recientes");
        assertThat(after).hasSize(before.size() + 1);
        JMenu recent = recentMenu(file);
        assertThat(recent.getMenuListeners()).hasSize(1);

        disposer.run();
        assertThat(entries(file)).isEqualTo(before);
        assertThat(recent.getMenuListeners()).isEmpty();
    }

    @Test
    void theListIsReadEachTimeTheMenuOpensNewestFirstWithItsFolder() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("circuitos"));
        Path counter = Files.createFile(folder.resolve("contador.vsim"));
        Path adder = Files.createFile(folder.resolve("sumador.vsim"));
        Runnable disposer = feature().install(view);
        JMenu recent = recentMenu(view.menu(MenuId.FILE));

        open(recent);
        assertThat(entries(recent)).containsExactly("No hay circuitos recientes", "—", "Limpiar lista");
        assertThat(item(recent, "Limpiar lista").isEnabled()).isFalse();

        RecentFiles recentFiles = new RecentFiles(preferences);
        recentFiles.touch(counter);
        recentFiles.touch(adder);
        open(recent);
        assertThat(entries(recent)).containsExactly("sumador — circuitos", "contador — circuitos", "—",
                "Limpiar lista");
        assertThat(item(recent, "sumador — circuitos").getToolTipText())
                .isEqualTo(FileNames.normalized(adder).toString());
        disposer.run();
    }

    @Test
    void choosingAnEntryOpensItAndAMissingFileIsRemovedWithAMessage() throws Exception {
        Path counter = FileNames.normalized(Files.createFile(directory.resolve("contador.vsim")));
        Path gone = FileNames.normalized(Files.createFile(directory.resolve("borrado.vsim")));
        RecentFiles recentFiles = new RecentFiles(preferences);
        recentFiles.touch(gone);
        recentFiles.touch(counter);
        Files.delete(gone);
        String folder = counter.getParent().getFileName().toString();
        Runnable disposer = feature().install(view);
        JMenu recent = recentMenu(view.menu(MenuId.FILE));
        open(recent);

        item(recent, "contador — " + folder).doClick(0);
        assertThat(opened).containsExactly(counter);

        item(recent, "borrado — " + folder).doClick(0);
        assertThat(missing).containsExactly(gone);
        assertThat(opened).hasSize(1);
        assertThat(recentFiles.entries()).containsExactly(counter);
        disposer.run();
    }

    @Test
    void clearEmptiesTheList() throws Exception {
        new RecentFiles(preferences).touch(Files.createFile(directory.resolve("contador.vsim")));
        Runnable disposer = feature().install(view);
        JMenu recent = recentMenu(view.menu(MenuId.FILE));
        open(recent);

        item(recent, "Limpiar lista").doClick(0);
        assertThat(new RecentFiles(preferences).entries()).isEmpty();
        open(recent);
        assertThat(entries(recent)).containsExactly("No hay circuitos recientes", "—", "Limpiar lista");
        disposer.run();
    }

    @Test
    void theTextOfARootPathIsThePathItself() {
        Path root = Path.of("").toAbsolutePath().getRoot();
        assertThat(RecentFilesFeature.entryText(root)).contains(root.toString());
    }
}
