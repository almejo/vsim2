package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
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
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.window.panes.RememberedSplit;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A top-level window showing one {@link OpenDocument}. */
public final class CircuitWindow extends JFrame implements WindowCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitWindow.class);
    private static final int SAVE_OPTION = 0;
    private static final int DISCARD_OPTION = 1;
    private static final int CANCEL_OPTION = 2;

    private final Application application;
    private final OpenDocument openDocument;
    private final EditorView view;
    private final Runnable titleUpdater = this::updateTitle;
    private ViewState initialViewState;
    /** Applies the initial view when the window opens, and again after a restored maximized window's layout. */
    private final InitialView initialView = new InitialView(this::showInitialView);
    private final RecentFiles recentFiles;
    /** The bounds while not maximized: what Recientes and the session store for a maximized window. */
    private Rectangle normalBounds;
    /** Dividir la vista: this window's split, remembered per file in the preferences (spec §7). */
    private final RememberedSplit rememberedSplit;

    public CircuitWindow(Application application, OpenDocument openDocument, EditorServices services) {
        this.application = application;
        this.openDocument = openDocument;
        this.recentFiles = new RecentFiles(services.preferences());
        this.view = new EditorView(openDocument, services, this);
        this.rememberedSplit = new RememberedSplit(new SplitLayoutStore(services.preferences()), view.canvasPanes(),
                openDocument::path, RememberedSplit.Delay.swing());
        setJMenuBar(view.menuBar());
        setContentPane(view);
        setIconImages(AppIcons.images());
        new WindowMenu(view.menu(MenuId.WINDOW), () -> application.windowEntries(this));
        // Component files are imported into the library instead of being opened as circuits (M4-A ruling); drops on
        // the window, the canvas and the palette are handled after the drop finishes (EditorView.dropFiles).
        view.setCircuitOpener(circuits -> application.openPaths(circuits, this));
        setTransferHandler(new FileDropHandler(view::dropFiles));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }

            @Override
            public void windowOpened(WindowEvent event) {
                initialView.opened(isMaximized());
                view.canvas().requestFocusInWindow();
            }
        });
        view.canvas().addViewListener(initialView::viewChanged);
        // Pans by drag, wheel or autoscroll do not reach the view listeners; any press or wheel turn counts as one.
        view.canvas().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                initialView.userInteracted();
            }
        });
        view.canvas().addMouseWheelListener(event -> initialView.userInteracted());
        view.canvas().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                initialView.canvasResized(isMaximized());
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
        openDocument.addChangeListener(titleUpdater);
        application.addWindowsListener(titleUpdater);
        updateTitle();
        setSize(1280, 820);
        setLocationByPlatform(true);
    }

    public static String titleFor(OpenDocument openDocument) {
        return titleFor(openDocument, 0);
    }

    /** «contador (2) — VSim 2» for view 2 of a document with several views, «*» first when modified. */
    public static String titleFor(OpenDocument openDocument, int viewNumber) {
        return (openDocument.isModified() ? "*" : "") + Messages.format("window.title",
                DocumentWindows.viewTitle(openDocument.displayName(), viewNumber));
    }

    public OpenDocument openDocument() {
        return openDocument;
    }

    /** Shows {@code state} instead of centering the circuit when the window opens; call it before {@code setVisible}. */
    public void setInitialViewState(ViewState state) {
        this.initialViewState = state;
    }

    /** The zoom and center this window's canvas shows now. */
    public ViewState viewState() {
        return view.canvas().viewState();
    }

    /** Where this window is and what it shows (M6-B); a maximized window reports the bounds it had before. */
    public WindowState windowState() {
        boolean maximized = isMaximized();
        Rectangle bounds = maximized && normalBounds != null ? normalBounds : getBounds();
        return WindowState.of(bounds, maximized, viewState());
    }

    /**
     * Applies an (already clamped) state: the normal bounds are set before maximizing so that un-maximizing returns
     * to them. The zoom and center are applied when the window opens ({@link InitialView}).
     */
    public void restoreWindowState(WindowState state) {
        normalBounds = state.bounds();
        setBounds(normalBounds);
        setInitialViewState(state.view());
        if (state.maximized()) {
            setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
        }
    }

    /**
     * This window shows a file being opened, not another view of an open one: it opens split as that file's window
     * was last time (when it fits). Call it before {@code setVisible}.
     */
    public void restoreRememberedSplit() {
        rememberedSplit.restoreWhenShown();
    }

    private void showInitialView() {
        if (initialViewState != null) {
            view.canvas().applyViewState(initialViewState);
        } else {
            view.canvas().centerCircuit();
        }
        rememberedSplit.showRemembered();
    }

    private boolean isMaximized() {
        return (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
    }

    private void rememberNormalBounds() {
        if ((getExtendedState() & MAXIMIZED_BOTH) == 0) {
            normalBounds = getBounds();
        }
    }

    @Override
    public void newDocument() {
        application.newDocument();
    }

    @Override
    public void open() {
        application.open(this);
    }

    @Override
    public void newView() {
        application.newWindow(this);
    }

    @Override
    public void openUntitled(CircuitDocument document) {
        application.openUntitled(document, this);
    }

    @Override
    public void openNewCircuit(String name, Command creation, String notice) {
        CircuitWindow created = application.openNewCircuit(name, creation);
        if (!notice.isEmpty()) {
            created.view.statusBar().showNotice(notice);
        }
    }

    /** Opens the circuit files in new windows and imports the component files into the library (as a drop does). */
    public void openOrImport(List<Path> files) {
        view.openOrImport(files);
    }

    @Override
    public void save() {
        trySave();
    }

    @Override
    public void saveAs() {
        trySaveAs();
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
     * Asks to save only when this is the last window of a modified document; false means the user cancelled. An open
     * inline edit is finished first (confirmed when valid, cancelled otherwise), so the question sees it.
     */
    public boolean confirmClose() {
        return CloseSequence.confirm(List.of(view.inlineEditor()),
                () -> !application.isLastWindow(this) || confirmSave());
    }

    /** This window's inline editor, finished before an exit asks to save (M6.8). */
    public InlineTextEditor inlineEditor() {
        return view.inlineEditor();
    }

    /** Asks to save a modified document; false means the user cancelled. */
    public boolean confirmSave() {
        if (!openDocument.isModified()) {
            return true;
        }
        Object[] options = new Object[3];
        options[SAVE_OPTION] = Messages.text("dialog.save");
        options[DISCARD_OPTION] = Messages.text("dialog.discard");
        options[CANCEL_OPTION] = Messages.text("dialog.cancel");
        int choice = JOptionPane.showOptionDialog(this,
                Messages.format("dialog.saveChanges.message", openDocument.displayName()),
                Messages.text("dialog.saveChanges.title"), JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[SAVE_OPTION]);
        return switch (choice) {
            case SAVE_OPTION -> trySave();
            case DISCARD_OPTION -> true;
            default -> false;
        };
    }

    /** Closes this window without asking; the application closes the document after its last window. */
    public void closeQuietly() {
        try {
            openDocument.removeChangeListener(titleUpdater);
            application.removeWindowsListener(titleUpdater);
            rememberedSplit.close();
            view.dispose();
        } catch (RuntimeException exception) {
            // a broken extension disposer must not keep the window open or stop the application from exiting
            LOGGER.error("Could not release the editor of {}", openDocument.displayName(), exception);
        } finally {
            dispose();
            application.windowClosed(this);
        }
    }

    private boolean trySave() {
        if (openDocument.needsSaveAs()) {
            return trySaveAs();
        }
        return writeTo(openDocument.path().orElseThrow());
    }

    private boolean trySaveAs() {
        return FileDialogs.chooseSave(this,
                openDocument.path().map(Path::getParent).orElseGet(application::initialDirectory), openDocument.suggestedFileName())
                .map(this::writeTo)
                .orElse(false);
    }

    private boolean writeTo(Path target) {
        try {
            Optional<Path> previous = openDocument.path();
            openDocument.save(target);
            application.rememberDirectory(target);
            rememberRecent(target);
            rememberedSplit.saved(previous, target);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not save {}", target, exception);
            JOptionPane.showMessageDialog(this, Messages.format("error.write", target, exception.getMessage()),
                    Messages.text("error.save.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** RecentFiles never throws; the window's state can (a window with no size), and must not block saving. */
    private void rememberRecent(Path target) {
        try {
            recentFiles.remember(target, windowState());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not add {} to the recent files", target, exception);
        }
    }

    private void updateTitle() {
        setTitle(titleFor(openDocument, application.viewNumber(this)));
        getRootPane().putClientProperty("Window.documentModified", openDocument.isModified());
    }
}
