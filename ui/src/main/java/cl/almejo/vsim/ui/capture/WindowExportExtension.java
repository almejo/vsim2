package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Archivo ▸ Exportar ▸ «Imagen de la ventana…» in every editor view ({@link WindowExportFeatures}); installed after
 * the other exports, so it lands right after «Imagen…» whatever they add.
 */
public final class WindowExportExtension implements EditorExtension {

    /** Asks the user where to write the PNG; empty when cancelled. An existing file is replaced only once confirmed. */
    @FunctionalInterface
    public interface Chooser {
        Optional<Path> choose(Component parent, Path directory, String suggestedName);
    }

    private final Chooser chooser;
    private final BiConsumer<Component, String> messages;

    /** The real thing: a save dialog for PNG files and an error dialog. */
    public WindowExportExtension() {
        this(WindowExportExtension::chooseFile, (parent, message) -> JOptionPane.showMessageDialog(parent, message,
                null, JOptionPane.ERROR_MESSAGE));
    }

    /** {@code chooser} asks for the file, {@code messages} shows a failure. */
    public WindowExportExtension(Chooser chooser, BiConsumer<Component, String> messages) {
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Runnable install(EditorView view) {
        WindowExportFeatures features = new WindowExportFeatures(view, chooser, messages);
        return features::dispose;
    }

    private static Optional<Path> chooseFile(Component parent, Path directory, String suggestedName) {
        return FileDialogs.chooseSave(parent, directory, suggestedName,
                new FileNameExtensionFilter(Messages.text("export.filter.png"), "png"),
                path -> FileNames.withExtension(path, ".png"));
    }
}
