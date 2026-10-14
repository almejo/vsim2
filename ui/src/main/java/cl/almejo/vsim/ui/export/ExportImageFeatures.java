package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.Menus;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The image export of one editor view: the menu item right after «Guardar como…», the action (off for an empty
 * document), the shortcut registration and the remembered options. {@link #dispose()} takes all of it back.
 */
public final class ExportImageFeatures {

    private final EditorView view;
    private final ExportChooser chooser;
    private final BiConsumer<Component, String> messages;
    private final Action action;
    private final JMenu fileMenu;
    private final JMenuItem item;
    private final Runnable shortcutRemover;
    private final Runnable changeListener = this::updateEnabled;
    private boolean disposed;

    public ExportImageFeatures(EditorView view, ExportChooser chooser, BiConsumer<Component, String> messages) {
        this.view = view;
        this.chooser = chooser;
        this.messages = messages;
        this.action = new AbstractAction(Messages.text("action.exportImage")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                export();
            }
        };
        Icons.install(action, ExportIcons.EXPORT_IMAGE);
        this.fileMenu = view.menu(MenuId.FILE);
        this.item = new JMenuItem(action);
        int saveAs = Menus.indexOf(fileMenu, view.actions().saveAs());
        fileMenu.add(item, saveAs < 0 ? fileMenu.getMenuComponentCount() : saveAs + 1);
        this.shortcutRemover = view.registerShortcutAction("file.exportImage", action);
        view.openDocument().addChangeListener(changeListener);
        updateEnabled();
    }

    public Action action() {
        return action;
    }

    private void updateEnabled() {
        action.setEnabled(!view.openDocument().document().isEmpty());
    }

    /** Asks for the file and writes the selection, or the whole circuit when nothing is selected. */
    void export() {
        if (disposed || !action.isEnabled()) {
            return;
        }
        OpenDocument openDocument = view.openDocument();
        AppPreferences preferences = view.services().preferences();
        Optional<ExportChoice> choice = chooser.choose(view, startDirectory(openDocument),
                suggestedName(openDocument), preferences.exportScale(), preferences.exportPrintBackground());
        if (choice.isEmpty()) {
            return;
        }
        ExportChoice chosen = choice.get();
        preferences.setExportScale(chosen.scale());
        preferences.setExportPrintBackground(chosen.printBackground());
        Path folder = chosen.path().toAbsolutePath().getParent();
        if (folder != null) {
            preferences.setExportDirectory(folder);
        }
        Set<ElementId> selection = view.canvas().selection().ids();
        ColorScheme scheme = chosen.printBackground() ? CircuitImage.printScheme(ColorScheme.LIGHT)
                : view.services().appearance().scheme();
        Optional<CircuitImage> image = CircuitImage.of(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), openDocument.latestSnapshot(), openDocument.registry(),
                PainterRegistry.standard(), scheme, selection.isEmpty() ? Optional.empty()
                        : Optional.of(Set.copyOf(selection)));
        if (image.isEmpty()) {
            return;
        }
        try {
            if (chosen.format() == ExportChoice.Format.SVG) {
                ImageExport.writeSvg(chosen.path(), image.get());
            } else {
                ImageExport.writePng(chosen.path(), image.get(), chosen.scale());
            }
        } catch (IOException | RuntimeException exception) {
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            messages.accept(view, Messages.format("export.failed", chosen.path().getFileName(), reason));
        }
    }

    /** The last export folder, else the document's folder, else the user's home. */
    private Path startDirectory(OpenDocument openDocument) {
        return view.services().preferences().exportDirectory()
                .or(() -> openDocument.path().map(Path::getParent))
                .orElseGet(() -> Path.of(System.getProperty("user.home")));
    }

    private static String suggestedName(OpenDocument openDocument) {
        return openDocument.displayName().replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        fileMenu.remove(item);
        view.openDocument().removeChangeListener(changeListener);
    }
}
