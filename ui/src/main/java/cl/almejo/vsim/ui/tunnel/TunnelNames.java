package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Optional;

/**
 * The rules for a tunnel name typed by the user (spec §7), shared by the inline editor and the inspector: what is
 * wrong with a name, and the rename that carries an entrada's salidas along (one undo step, Task 1's command).
 */
public final class TunnelNames {

    private TunnelNames() {
    }

    /** What is wrong with {@code text} as a tunnel name: blank, or spaces at either end. Empty when it is fine. */
    public static Optional<String> problemWith(String text) {
        if (text.isBlank()) {
            return Optional.of(Messages.text("tunnel.name.blank"));
        }
        if (!text.equals(text.strip())) {
            return Optional.of(Messages.text("tunnel.name.spaces"));
        }
        return Optional.empty();
    }

    /**
     * The command that renames tunnel {@code id} to {@code newName}: an entrada takes its salidas along, a salida
     * relinks only itself. Empty when the tunnel is gone or already has that name.
     */
    public static Optional<Command> renameCommand(OpenDocument openDocument, ElementId id, String newName) {
        Optional<Component> tunnel = openDocument.document().component(id)
                .filter(component -> Tunnels.isTunnel(component.typeId()));
        if (tunnel.isEmpty() || Tunnels.nameOf(tunnel.get()).equals(newName.strip())) {
            return Optional.empty();
        }
        return Optional.of(Tunnels.renameCommand(openDocument.document(), openDocument.registry(), id, newName));
    }

    /** Remembers {@code name} when tunnel {@code id} is an entrada being renamed: the next new salida proposes it. */
    public static void rememberEntrance(OpenDocument openDocument, ElementId id, String name) {
        openDocument.document().component(id)
                .filter(component -> Tunnels.kindOf(component).orElse(null) == TunnelKind.ENTRANCE)
                .ifPresent(entrance -> openDocument.rememberTunnelEntranceName(Tunnels.normalizeName(name)));
    }
}
