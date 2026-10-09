package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.CircuitWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Open windows and documents of the running application (EDT only). */
public final class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final PainterRegistry painters = PainterRegistry.standard();
    private final ColorScheme scheme = ColorScheme.LIGHT;
    private final List<CircuitWindow> windows = new ArrayList<>();
    private Path lastDirectory;

    public void start(List<Path> files) {
        files.forEach(file -> openPath(file, null));
        if (windows.isEmpty()) {
            newDocument();
        }
    }

    public CircuitWindow newDocument() {
        return show(OpenDocument.createEmpty(registry));
    }

    public void open(CircuitWindow requester) {
        FileDialogs.chooseOpen(requester, initialDirectory()).ifPresent(path -> openPath(path, requester));
    }

    public void openPath(Path path, CircuitWindow requester) {
        OpenDocument document;
        try {
            document = OpenDocument.open(path, registry);
        } catch (CircuitFormatException exception) {
            LOGGER.warn("Could not open {}", path, exception);
            JOptionPane.showMessageDialog(requester, Messages.readError(exception), Messages.text("error.open.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        rememberDirectory(path);
        show(document);
        if (requester != null && requester.openDocument().isPristine()) {
            requester.closeQuietly();
        }
    }

    /** Asks to save every modified document; true when all windows were closed. */
    public boolean exit() {
        for (CircuitWindow window : List.copyOf(windows)) {
            if (!window.confirmClose()) {
                return false;
            }
        }
        List.copyOf(windows).forEach(CircuitWindow::closeQuietly);
        return true;
    }

    public Path initialDirectory() {
        if (lastDirectory != null) {
            return lastDirectory;
        }
        for (String candidate : List.of("examples", "../examples")) {
            Path examples = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(examples)) {
                return examples;
            }
        }
        return Path.of(System.getProperty("user.home"));
    }

    public void rememberDirectory(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
    }

    public void windowClosed(CircuitWindow window) {
        windows.remove(window);
        if (windows.isEmpty()) {
            System.exit(0);
        }
    }

    private CircuitWindow show(OpenDocument document) {
        CircuitWindow window = new CircuitWindow(this, document, painters, scheme);
        windows.add(window);
        window.setVisible(true);
        return window;
    }
}
