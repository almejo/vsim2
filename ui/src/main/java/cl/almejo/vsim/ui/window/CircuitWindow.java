package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FileDropHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.AppIcons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

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

    public CircuitWindow(Application application, OpenDocument openDocument, EditorServices services) {
        this.application = application;
        this.openDocument = openDocument;
        this.view = new EditorView(openDocument, services, this);
        setJMenuBar(view.menuBar());
        setContentPane(view);
        setIconImages(AppIcons.images());
        new WindowMenu(view.menu(MenuId.WINDOW), () -> application.newWindow(this),
                () -> application.windowEntries(this));
        // Open after the drop finishes: an error dialog inside a drop transaction can hang the drag source.
        Consumer<List<Path>> openDropped = files -> SwingUtilities.invokeLater(() -> application.openPaths(files, this));
        setTransferHandler(new FileDropHandler(openDropped));
        view.canvas().setFileDropHandler(openDropped);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeWindow();
            }

            @Override
            public void windowOpened(WindowEvent event) {
                view.canvas().centerCircuit();
                view.canvas().requestFocusInWindow();
            }
        });
        openDocument.addChangeListener(titleUpdater);
        updateTitle();
        setSize(1280, 820);
        setLocationByPlatform(true);
    }

    public static String titleFor(OpenDocument openDocument) {
        return (openDocument.isModified() ? "*" : "") + Messages.format("window.title", openDocument.displayName());
    }

    public OpenDocument openDocument() {
        return openDocument;
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

    /** Asks to save only when this is the last window of a modified document; false means the user cancelled. */
    public boolean confirmClose() {
        return !application.isLastWindow(this) || confirmSave();
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
            openDocument.save(target);
            application.rememberDirectory(target);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Could not save {}", target, exception);
            JOptionPane.showMessageDialog(this, Messages.format("error.write", target, exception.getMessage()),
                    Messages.text("error.save.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void updateTitle() {
        setTitle(titleFor(openDocument));
        getRootPane().putClientProperty("Window.documentModified", openDocument.isModified());
    }
}
