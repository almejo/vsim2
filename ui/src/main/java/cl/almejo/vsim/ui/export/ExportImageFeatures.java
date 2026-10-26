package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The image export of one editor view: the menu item right after «Guardar como…», the action (off for an empty
 * document), the shortcut registration and the remembered options. {@link #dispose()} takes all of it back.
 */
public final class ExportImageFeatures extends FileExportFeatures {

    private final ExportChooser chooser;
    private final BiConsumer<Component, String> messages;

    public ExportImageFeatures(EditorView view, ExportChooser chooser, BiConsumer<Component, String> messages) {
        super(view, "action.exportImage", ExportIcons.EXPORT_IMAGE, "file.exportImage",
                indexAfter(view, view.actions().saveAs()));
        this.chooser = chooser;
        this.messages = messages;
    }

    /** Asks for the file and writes the selection, or the whole circuit when nothing is selected. */
    @Override
    protected void export() {
        if (!available()) {
            return;
        }
        EditorView view = view();
        OpenDocument openDocument = view.openDocument();
        AppPreferences preferences = view.services().preferences();
        Optional<ExportChoice> choice = chooser.choose(view, startDirectory(preferences.exportDirectory()),
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
        Set<ElementId> selection = view.selection().ids();
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

    private static String suggestedName(OpenDocument openDocument) {
        return FileNames.safeName(openDocument.displayName());
    }
}
