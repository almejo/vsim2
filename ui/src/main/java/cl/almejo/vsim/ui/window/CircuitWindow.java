package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.DocumentWindows;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.clipboard.FileDropHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.AppIcons;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.session.RecentFiles;
import cl.almejo.vsim.ui.session.WindowSession;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A top-level window of tabs (spec §4): groups of tabs per open document ({@link WindowTabs}), the active tab's menu
 * bar and toolbar. It is the editors' {@link WindowCommands}: file and window operations act on the active tab's
 * document. A window is never shown empty: a tab is added before {@code setVisible}.
 */
public final class CircuitWindow extends JFrame implements WindowCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitWindow.class);
    private static final int SAVE_OPTION = 0;
    private static final int DISCARD_OPTION = 1;
    private static final int CANCEL_OPTION = 2;

    private final Application application;
    private final WindowTabs tabs;
    private final Runnable windowsListener = this::windowsChanged;
    private final RecentFiles recentFiles;
    /** The bounds while not maximized: what Recientes and the session store for a maximized window. */
    private Rectangle normalBounds;
    private boolean closed;

    public CircuitWindow(Application application, EditorServices services) {
        this.application = application;
        this.recentFiles = new RecentFiles(services.preferences());
        this.tabs = new WindowTabs(new Host(), place -> newView(place, services),
                new SplitLayoutStore(services.preferences()), () -> services.preferences().animateViewMoves());
        setContentPane(tabs);
        setIconImages(AppIcons.images());
        // Component files are imported into the library instead of being opened as circuits (M4-A ruling); drops on
        // the window, the canvas and the palette are handled after the drop finishes (EditorView.dropFiles).
        setTransferHandler(new FileDropHandler(files -> tabs.activeView().dropFiles(files)));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }

            @Override
            public void windowOpened(WindowEvent event) {
                tabs.windowOpened(isMaximized());
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                rememberNormalBounds();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                rememberNormalBounds();
            }
        });
        application.addWindowsListener(windowsListener);
        setSize(1280, 820);
        setLocationByPlatform(true);
    }

    private EditorView newView(OpenDocument place, EditorServices services) {
        EditorView view = new EditorView(place, services, this);
        view.setCircuitOpener(circuits -> application.openPaths(circuits, this));
        return view;
    }

    public static String titleFor(OpenDocument openDocument) {
        return titleFor(openDocument, 0);
    }

    /** «contador (2) — VSim 2» for view 2 of a document with several views, «*» first when modified. */
    public static String titleFor(OpenDocument openDocument, int viewNumber) {
        return (openDocument.isModified() ? "*" : "") + Messages.format("window.title",
                DocumentWindows.viewTitle(openDocument.displayName(), viewNumber));
    }

    // --- tabs ---

    /** A new main tab of {@code document}, activated; it opens at {@code initialView}, else centered. */
    public EditorView addTab(OpenDocument document, Optional<ViewState> initialView) {
        return tabs.addTab(document, initialView);
    }

    /** The tabs of this window (another window moves a group into them). */
    public WindowTabs tabs() {
        return tabs;
    }

    /** The active tab's editor (the AI's pictures paint its panes). */
    public EditorView activeView() {
        return tabs.activeView();
    }

    /** The document of the active tab's group. */
    public OpenDocument activeDocument() {
        return tabs.activeDocument();
    }

    public List<OpenDocument> documents() {
        return tabs.documents();
    }

    public List<EditorView> viewsOf(OpenDocument document) {
        return tabs.viewsOf(document);
    }

    public int mainTabCount(OpenDocument document) {
        return tabs.mainTabCount(document);
    }

    /** Brings {@code document}'s first main tab to the front of this window. */
    public void activateMainTab(OpenDocument document) {
        tabs.viewOf(document).ifPresent(tabs::activate);
    }

    /** One entry per tab for the Ventana list; the active tab is checked when this is {@code current}. */
    public List<WindowMenu.Entry> tabEntries(boolean current, Runnable focus) {
        return tabs.menuEntries(current, focus);
    }

    /** {@code view} shows a file being opened: it comes back split as that file was last time. */
    public void restoreRememberedSplit(EditorView view) {
        tabs.restoreRememberedSplit(view);
    }

    /** Closes {@code document}'s group here, asking to save only as spec §4.5 says; false when cancelled. */
    public boolean closeGroup(OpenDocument document) {
        return tabs.closeGroup(document);
    }

    // --- state ---

    /** The zoom and center the active tab's active pane shows now (Nueva ventana con este circuito opens there). */
    public ViewState viewState() {
        return tabs.activeView().canvas().viewState();
    }

    /**
     * What the session remembers of this window (spec §4.7): where it is (a maximized window reports its earlier
     * bounds) and its tabs of saved circuits; empty when it shows only untitled ones.
     */
    public Optional<WindowSession> windowState() {
        return tabs.session(WindowState.of(normalBounds(), isMaximized(), viewState()));
    }

    /** Where this window is and what {@code document}'s first tab here shows (Recientes keeps it per file). */
    public WindowState windowState(OpenDocument document) {
        ViewState view = tabs.viewsOf(document).stream().findFirst()
                .map(found -> found.canvas().viewState()).orElseGet(this::viewState);
        return WindowState.of(normalBounds(), isMaximized(), view);
    }

    private Rectangle normalBounds() {
        return isMaximized() && normalBounds != null ? normalBounds : getBounds();
    }

    /**
     * Applies an (already clamped) state: the normal bounds are set before maximizing so that un-maximizing returns
     * to them. The zoom and center are the active tab's, applied when the window opens.
     */
    public void restoreWindowState(WindowState state) {
        normalBounds = state.bounds();
        setBounds(normalBounds);
        tabs.setInitialViewState(tabs.activeView(), state.view());
        if (state.maximized()) {
            setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
        }
    }

    private boolean isMaximized() {
        return (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
    }

    private void rememberNormalBounds() {
        if ((getExtendedState() & MAXIMIZED_BOTH) == 0) {
            normalBounds = getBounds();
        }
    }

    // --- WindowCommands, on the active tab ---

    @Override
    public void newDocument() {
        application.newDocument(this);
    }

    @Override
    public void open() {
        application.open(this);
    }

    @Override
    public void newView() {
        tabs.newViewOfActivePlace();
    }

    @Override
    public void newWindowWithCircuit() {
        application.newWindow(this);
    }

    /** With a single group there is nothing to move out: the window already shows only it. */
    @Override
    public void moveGroupToNewWindow() {
        if (tabs.documents().size() > 1) {
            application.moveGroupToNewWindow(this, activeDocument(),
                    NewViewPlacement.after(getLocation(), viewState()).location());
        }
    }

    @Override
    public void toggleFold() {
        OpenDocument document = activeDocument();
        tabs.foldGroup(document, !tabs.isFolded(document));
    }

    @Override
    public void nextTab() {
        tabs.nextTab();
    }

    @Override
    public void previousTab() {
        tabs.previousTab();
    }

    @Override
    public void closeTab() {
        tabs.closeTab(tabs.activeView());
    }

    @Override
    public void closeComponents() {
        tabs.closeComponentTabs(activeDocument());
    }

    @Override
    public void enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                      Optional<ViewState> from) {
        tabs.enter(document, definitionId, contextPath, from);
    }

    @Override
    public Optional<EditorView> showPlace(OpenDocument document, Optional<String> definitionId,
                                          Optional<String> contextPath) {
        return Optional.of(tabs.showPlace(document, definitionId, contextPath));
    }

    @Override
    public Optional<EditorView> showPlaceOfStep(OpenDocument document, Optional<String> definitionId,
                                                Optional<String> contextPath) {
        return Optional.of(tabs.showPlaceOfStep(document, definitionId, contextPath));
    }

    @Override
    public boolean goToPart(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                            ElementId part) {
        tabs.goToPart(document, definitionId, contextPath, part);
        return true;
    }

    @Override
    public List<WindowMenu.Entry> tabEntries() {
        return tabs.menuEntries(true, () -> {
        });
    }

    @Override
    public void openUntitled(CircuitDocument document) {
        application.openUntitled(document, this);
    }

    @Override
    public Optional<EditorView> showUntitled(CircuitDocument document, String windowName) {
        return Optional.of(application.openUntitled(document, windowName, this));
    }

    @Override
    public void openNewCircuit(String name, Command creation, String notice) {
        EditorView created = application.openNewCircuit(name, creation, this);
        if (!notice.isEmpty()) {
            created.statusBar().showNotice(notice);
        }
    }

    /** Opens the circuit files as new groups and imports the component files into the library (as a drop does). */
    public void openOrImport(List<Path> files) {
        tabs.activeView().openOrImport(files);
    }

    @Override
    public void save() {
        trySave(activeDocument());
    }

    @Override
    public void saveAs() {
        trySaveAs(activeDocument());
    }

    @Override
    public void closeWindow() {
        if (confirmClose()) {
            closeQuietly();
        }
    }

    @Override
    public void exit() {
        application.exit();
    }

    /**
     * Asks to save each modified document this window is the last holder of (spec §4.5); false means the user
     * cancelled. Open inline edits are finished first (confirmed when valid, cancelled otherwise), so the questions
     * see them.
     */
    public boolean confirmClose() {
        return CloseSequence.confirm(tabs.inlineEditors(), () -> tabs.documents().stream()
                .allMatch(document -> !application.isLastHolder(document, this) || confirmSave(document)));
    }

    /** Every tab's inline editor, finished before an exit asks to save (M6.8). */
    public List<InlineTextEditor> inlineEditors() {
        return tabs.inlineEditors();
    }

    /** Asks to save a modified document; false means the user cancelled. */
    public boolean confirmSave(OpenDocument document) {
        if (!document.isModified()) {
            return true;
        }
        tabs.viewOf(document).ifPresent(tabs::activate);
        Object[] options = new Object[3];
        options[SAVE_OPTION] = Messages.text("dialog.save");
        options[DISCARD_OPTION] = Messages.text("dialog.discard");
        options[CANCEL_OPTION] = Messages.text("dialog.cancel");
        int choice = JOptionPane.showOptionDialog(this,
                Messages.format("dialog.saveChanges.message", document.displayName()),
                Messages.text("dialog.saveChanges.title"), JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[SAVE_OPTION]);
        return switch (choice) {
            case SAVE_OPTION -> trySave(document);
            case DISCARD_OPTION -> true;
            default -> false;
        };
    }

    /**
     * A window that never got a tab (bringing back a session window failed before its first one) goes without being
     * shown and without telling the application it closed: it never held anything.
     */
    public void discardUnshown() {
        if (!tabs.isEmpty()) {
            throw new IllegalStateException("the window has tabs: close it instead");
        }
        closed = true;
        application.removeWindowsListener(windowsListener);
        tabs.disposeAll();
        dispose();
    }

    /** Closes this window without asking; the application closes the documents it was the last holder of. */
    public void closeQuietly() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            application.removeWindowsListener(windowsListener);
            tabs.disposeAll();
        } catch (RuntimeException exception) {
            // a broken extension disposer must not keep the window open or stop the application from exiting
            LOGGER.error("Could not release the editors of a window", exception);
        } finally {
            dispose();
            application.windowClosed(this);
        }
    }

    private boolean trySave(OpenDocument document) {
        if (document.needsSaveAs()) {
            return trySaveAs(document);
        }
        return writeTo(document, document.path().orElseThrow());
    }

    private boolean trySaveAs(OpenDocument document) {
        return FileDialogs.chooseSave(this,
                document.path().map(Path::getParent).orElseGet(application::initialDirectory),
                document.suggestedFileName())
                .map(target -> writeTo(document, target))
                .orElse(false);
    }

    private boolean writeTo(OpenDocument document, Path target) {
        try {
            Optional<Path> previous = document.path();
            document.save(target);
            application.rememberDirectory(target);
            rememberRecent(document, target);
            tabs.saved(document, previous, target);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not save {}", target, exception);
            JOptionPane.showMessageDialog(this, Messages.format("error.write", target, exception.getMessage()),
                    Messages.text("error.save.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** RecentFiles never throws; the window's state can (a window with no size), and must not block saving. */
    private void rememberRecent(OpenDocument document, Path target) {
        try {
            recentFiles.remember(target, windowState(document));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not add {} to the recent files", target, exception);
        }
    }

    private void windowsChanged() {
        tabs.refreshTitles();
        updateTitle();
    }

    /** The active tab's document names the window: «*lrv32im (2) — VSim 2». */
    private void updateTitle() {
        if (tabs.isEmpty()) {
            return;
        }
        OpenDocument document = tabs.activeDocument();
        setTitle(titleFor(document, application.viewNumber(document, this, tabs.activeMainTabIndex())));
        getRootPane().putClientProperty("Window.documentModified", document.isModified());
    }

    /** What the tabs ask of this window and of the application. */
    private final class Host implements WindowTabs.Host {

        @Override
        public void showMenuBar(JMenuBar menuBar) {
            setJMenuBar(menuBar);
            getRootPane().revalidate();
            getRootPane().repaint();
        }

        @Override
        public boolean isMaximized() {
            return CircuitWindow.this.isMaximized();
        }

        @Override
        public boolean isLastHolder(OpenDocument document) {
            return application.isLastHolder(document, CircuitWindow.this);
        }

        @Override
        public boolean confirmSave(OpenDocument document) {
            return CircuitWindow.this.confirmSave(document);
        }

        @Override
        public void tabsChanged(OpenDocument document) {
            application.tabsChanged(document, CircuitWindow.this);
        }

        @Override
        public void groupClosing(OpenDocument document) {
            application.groupClosing(document, CircuitWindow.this);
        }

        @Override
        public void groupClosed(OpenDocument document) {
            application.groupClosed(document, CircuitWindow.this);
        }

        @Override
        public void emptied() {
            closeQuietly();
        }

        @Override
        public void activeTabChanged() {
            updateTitle();
        }

        @Override
        public int viewNumber(OpenDocument document, int mainTabIndex) {
            return application.viewNumber(document, CircuitWindow.this, mainTabIndex);
        }

        @Override
        public void droppedOutside(OpenDocument document, Point screenPoint) {
            application.moveGroupToNewWindow(CircuitWindow.this, document, screenPoint);
        }

        @Override
        public List<WindowMenu.Entry> everyTab() {
            return application.windowEntries(CircuitWindow.this);
        }
    }
}
