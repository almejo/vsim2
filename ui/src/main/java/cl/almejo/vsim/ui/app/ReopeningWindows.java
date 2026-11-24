package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.panels.CanvasNotice;
import cl.almejo.vsim.ui.session.AutosaveService;
import cl.almejo.vsim.ui.session.RecentFiles;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.window.CircuitWindow;
import cl.almejo.vsim.ui.window.ClosedTabReopener;
import cl.almejo.vsim.ui.window.WindowTabs;
import cl.almejo.vsim.ui.window.closed.ClosedTabs;

import java.awt.Rectangle;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The application's side of «Reabrir pestaña cerrada» (card «ctrl-t»): its windows, reading a file as Abrir does
 * (Recientes, the last folder, autosave), a new window placed where the closed one was, and the notices over a
 * window's circuit. Kept out of {@link Application} so that class only wires it. EDT only.
 */
final class ReopeningWindows implements ClosedTabReopener.Windows {

    private static final int FRAME_MILLISECONDS = 16;

    private final DocumentWindows<CircuitWindow> windows;
    private final OpeningFiles openingFiles;
    private final RecentFiles recentFiles;
    private final AutosaveService autosave;
    private final Supplier<CircuitWindow> createWindow;
    private final Consumer<CircuitWindow> focus;
    private final Supplier<List<Rectangle>> screens;
    private final Consumer<Path> rememberDirectory;

    ReopeningWindows(DocumentWindows<CircuitWindow> windows, OpeningFiles openingFiles, RecentFiles recentFiles,
                     AutosaveService autosave, Supplier<CircuitWindow> createWindow, Consumer<CircuitWindow> focus,
                     Supplier<List<Rectangle>> screens, Consumer<Path> rememberDirectory) {
        this.windows = windows;
        this.openingFiles = openingFiles;
        this.recentFiles = recentFiles;
        this.autosave = autosave;
        this.createWindow = createWindow;
        this.focus = focus;
        this.screens = screens;
        this.rememberDirectory = rememberDirectory;
    }

    /** Brings back the newest entry of {@code closedTabs}, asked for in {@code requester}. */
    void reopen(ClosedTabs closedTabs, CircuitWindow requester) {
        KeyBindings keys = new KeyBindings(requester.activeView().services().preferences());
        new ClosedTabReopener(closedTabs, this, Clock.systemDefaultZone(),
                () -> keys.strokeFor("window.reopenClosedTab")).reopen(requester.tabs());
    }

    @Override
    public boolean isOpen(WindowTabs window) {
        return windowOf(window).isPresent();
    }

    @Override
    public Optional<OpenDocument> openDocumentOf(Path path) {
        Path normalized = FileNames.normalized(path);
        return windows.documents().stream()
                .filter(document -> document.path().map(FileNames::normalized).filter(normalized::equals).isPresent())
                .findFirst();
    }

    @Override
    public Optional<OpenDocument> read(Path path, WindowTabs target) {
        Optional<OpenDocument> document = openingFiles.read(path, windowOf(target).orElse(null));
        document.ifPresent(opened -> {
            rememberDirectory.accept(path);
            recentFiles.touch(path); // never throws: a Recientes failure never blocks opening
            autosave.track(opened); // M6-B: every open document is copied while modified
        });
        return document;
    }

    @Override
    public WindowTabs newWindow() {
        return createWindow.get().tabs();
    }

    @Override
    public void show(WindowTabs window, WindowState bounds) {
        windowOf(window).ifPresent(created -> {
            created.restoreWindowState(bounds.clampedTo(screens.get()));
            created.setVisible(true);
        });
    }

    @Override
    public void focus(WindowTabs window) {
        windowOf(window).ifPresent(focus);
    }

    @Override
    public void discard(WindowTabs window) {
        windowOf(window).ifPresent(ReopeningWindows::discardWindow);
    }

    private static void discardWindow(CircuitWindow created) {
        if (created.tabs().isEmpty()) {
            created.discardUnshown();
        } else {
            created.closeQuietly(); // not recorded: the documents it last held close, the entry goes back
        }
    }

    @Override
    public void notice(WindowTabs window, String text, Duration duration) {
        windowOf(window).filter(found -> !found.tabs().isEmpty()).ifPresent(found ->
                CanvasNotice.show(found.activeView().canvasPanes(), text, CanvasNotice.Position.TOP, duration,
                        FrameTicker.swing(FRAME_MILLISECONDS), found.activeView().services().preferences()));
    }

    /** The open window of {@code tabs}; a window just created is not in the list yet, so it is asked by its tabs. */
    private Optional<CircuitWindow> windowOf(WindowTabs tabs) {
        if (tabs.isDisposed()) {
            return Optional.empty();
        }
        return windows.all().stream().filter(window -> window.tabs() == tabs).findFirst()
                .or(() -> Optional.ofNullable((CircuitWindow) javax.swing.SwingUtilities.getWindowAncestor(tabs)));
    }
}
