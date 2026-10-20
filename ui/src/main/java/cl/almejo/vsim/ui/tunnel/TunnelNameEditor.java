package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.inline.InlineEditRequest;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.paint.TunnelPainter;

import java.util.List;
import java.util.Optional;

/**
 * Edits a tunnel's name in place, over the name in its tag (spec §7): double click or F2. The name is checked on every
 * keystroke ({@link TunnelNames#problemWith}); a salida offers the names that have an entrada. Confirming renames
 * through {@link TunnelNames#renameCommand} (an entrada takes its salidas along, one undo step).
 */
public final class TunnelNameEditor {

    private final OpenDocument openDocument;
    private final InlineTextEditor editor;

    public TunnelNameEditor(OpenDocument openDocument, InlineTextEditor editor) {
        this.openDocument = openDocument;
        this.editor = editor;
    }

    /** Opens the editor over {@code tunnel}'s name; nothing when it is gone or is not a tunnel. */
    public void edit(Component tunnel) {
        Optional<Component> current = openDocument.document().component(tunnel.id())
                .filter(component -> Tunnels.isTunnel(component.typeId()));
        if (current.isEmpty()) {
            return;
        }
        ElementId id = tunnel.id();
        editor.open(new InlineEditRequest(TunnelShape.of(current.get()).documentNameArea(current.get()),
                TunnelPainter.nameFont(), Tunnels.nameOf(current.get()), TunnelNames::problemWith,
                text -> rename(id, text), InlineEditRequest.Anchor.CENTER, suggestionsFor(current.get())),
                () -> openDocument.document().component(id).isPresent());
    }

    /** A salida offers the names that have an entrada, sorted; an entrada offers none. */
    private List<String> suggestionsFor(Component tunnel) {
        if (Tunnels.kindOf(tunnel).orElseThrow() == TunnelKind.ENTRANCE) {
            return List.of();
        }
        return Tunnels.groups(openDocument.document()).values().stream()
                .filter(group -> !group.entrances().isEmpty())
                .map(Tunnels.TunnelGroup::name)
                .toList();
    }

    private void rename(ElementId id, String newName) {
        TunnelNames.renameCommand(openDocument, id, newName).ifPresent(command ->
                TunnelNames.executeRemembering(openDocument, id, () -> openDocument.execute(command)));
    }
}
