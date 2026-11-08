package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.ImageExport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ExportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Action;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Archivo ▸ Exportar ▸ «Imagen de la ventana…» of one editor view: its item right after «Imagen…», its shortcut
 * registration ({@code file.exportWindowImage}, no default stroke) and the action, which asks for a file in the
 * remembered export folder and writes a PNG of the window as the user sees it, dialogs included, at the screen's
 * scale ({@link WindowCapture}). On also for an empty circuit: the window is there to picture. {@link #dispose()}
 * takes it all back.
 */
final class WindowExportFeatures {

    static final String SHORTCUT_ID = "file.exportWindowImage";
    private static final String EXPORT_IMAGE_ID = "file.exportImage";
    private static final String PNG_EXTENSION = ".png";
    private static final String CIRCUIT_EXTENSION = ".vsim";
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowExportFeatures.class);

    private final EditorView view;
    private final WindowExportExtension.Chooser chooser;
    private final BiConsumer<Component, String> messages;
    private final Runnable shortcutRemover;
    private final Runnable menuRemover;
    private boolean disposed;

    WindowExportFeatures(EditorView view, WindowExportExtension.Chooser chooser,
                         BiConsumer<Component, String> messages) {
        this.view = view;
        this.chooser = chooser;
        this.messages = messages;
        Action action = Actions.of(Messages.text("action.exportWindowImage"), this::export);
        Icons.install(action, ExportIcons.EXPORT_WINDOW);
        JMenuItem item = new JMenuItem(action);
        item.setText(Messages.text("menu.file.export.windowImage"));
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT_ID, action);
        Runnable added;
        try {
            added = ExportMenu.add(view.menu(MenuId.FILE), item, view.shortcutActions().get(EXPORT_IMAGE_ID));
        } catch (RuntimeException failure) {
            shortcutRemover.run();
            throw failure;
        }
        this.menuRemover = added;
    }

    /** Asks for the file and writes the window's picture there; a failure is told in a sentence, details in the log. */
    private void export() {
        if (disposed) {
            return;
        }
        AppPreferences preferences = view.services().preferences();
        Optional<Path> target = chooser.choose(view,
                view.openDocument().startDirectory(preferences.exportDirectory()), suggestedName());
        if (target.isEmpty()) {
            return;
        }
        Path folder = target.get().toAbsolutePath().getParent();
        if (folder != null) {
            preferences.setExportDirectory(folder);
        }
        try {
            BufferedImage image = WindowCapture.paint(view, CaptureKind.WINDOW, WindowCapture.screenScale(view));
            ImageExport.writePng(target.get(), image);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("The window's picture could not be written to {}", target.get(), failure);
            messages.accept(view, Messages.format("capture.exportWindow.failed", target.get().getFileName()));
        }
    }

    /** The circuit's name without «.vsim», as a PNG. */
    private String suggestedName() {
        String name = FileNames.safeName(view.openDocument().displayName());
        if (name.toLowerCase(Locale.ROOT).endsWith(CIRCUIT_EXTENSION)) {
            name = name.substring(0, name.length() - CIRCUIT_EXTENSION.length());
        }
        return name + PNG_EXTENSION;
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        menuRemover.run();
    }
}
