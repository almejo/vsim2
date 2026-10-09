package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

/** A top-level window showing one {@link OpenDocument}. */
public final class CircuitWindow extends JFrame implements WindowCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitWindow.class);

    private final Application application;
    private final OpenDocument openDocument;
    private final EditorView view;
    private final Runnable titleUpdater = this::updateTitle;

    public CircuitWindow(Application application, OpenDocument openDocument, PainterRegistry painters, ColorScheme scheme) {
        this.application = application;
        this.openDocument = openDocument;
        this.view = new EditorView(openDocument, painters, scheme, this);
        setJMenuBar(view.menuBar());
        setContentPane(view);
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

    /** Asks to save a modified document; false means the user cancelled. */
    public boolean confirmClose() {
        if (!openDocument.isModified()) {
            return true;
        }
        Object[] options = {Messages.text("dialog.save"), Messages.text("dialog.discard"), Messages.text("dialog.cancel")};
        int choice = JOptionPane.showOptionDialog(this,
                Messages.format("dialog.saveChanges.message", openDocument.displayName()),
                Messages.text("dialog.saveChanges.title"), JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return switch (choice) {
            case 0 -> trySave();
            case 1 -> true;
            default -> false;
        };
    }

    /** Closes without asking: stops the simulation and disposes the window. */
    public void closeQuietly() {
        openDocument.removeChangeListener(titleUpdater);
        view.dispose();
        openDocument.close();
        dispose();
        application.windowClosed(this);
    }

    private boolean trySave() {
        if (openDocument.needsSaveAs()) {
            return trySaveAs();
        }
        return writeTo(openDocument.path().orElseThrow());
    }

    private boolean trySaveAs() {
        return FileDialogs.chooseSave(this, application.initialDirectory(), openDocument.suggestedFileName())
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
