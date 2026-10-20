package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rules for a tunnel name typed by the user (spec §7), shared by the inline editor, the inspector and
 * «Configurar…»: what is wrong with a name, the rename that carries an entrada's salidas along (one undo step, Task 1's
 * command), and remembering an entrada's new name for the next salida once the change is done.
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

    /**
     * The command that sets the parameters of component {@code id} (of {@code type}) to {@code values}, as a form
     * gives them: a plain parameter change, except when a tunnel's name changes. Then it is the rename that carries an
     * entrada's salidas along, followed by the other changes (an entrada's colour) in the same undo step. «Configurar…»
     * and the inspector both build their change here, so the two paths follow the same rules.
     */
    public static Command parametersCommand(OpenDocument openDocument, ElementId id, ComponentType type,
                                            Map<String, Object> values) {
        Command change = new ChangeParametersCommand(id, type, values);
        if (!Tunnels.isTunnel(type.id())) {
            return change;
        }
        Map<String, Object> resolved = type.resolveParameters(values);
        Optional<Command> rename = renameCommand(openDocument, id, (String) resolved.get(Tunnels.NAME));
        if (rename.isEmpty()) {
            return change;
        }
        if (onlyTheNameChanges(openDocument, id, type, resolved)) {
            return rename.get();
        }
        return new CompositeCommand("command.configure", List.of(rename.get(), change));
    }

    private static boolean onlyTheNameChanges(OpenDocument openDocument, ElementId id, ComponentType type,
                                              Map<String, Object> resolved) {
        Component component = openDocument.document().component(id).orElseThrow();
        Map<String, Object> renamed = new HashMap<>(type.resolveParameters(component.parameters()));
        renamed.put(Tunnels.NAME, resolved.get(Tunnels.NAME));
        return renamed.equals(resolved);
    }

    /** Executes {@link #parametersCommand} as one undo step, remembering an entrada's new name afterwards. */
    public static void applyParameters(OpenDocument openDocument, ElementId id, ComponentType type,
                                       Map<String, Object> values) {
        executeRemembering(openDocument, id, () -> openDocument.execute(
                parametersCommand(openDocument, id, type, values)));
    }

    /**
     * Runs {@code edit} (which executes a command); when it changed the name of entrada {@code id}, the next new
     * salida proposes the new name. The name is read after the edit, so an edit that did nothing remembers nothing.
     */
    public static void executeRemembering(OpenDocument openDocument, ElementId id, Runnable edit) {
        Optional<String> before = entranceName(openDocument, id);
        edit.run();
        Optional<String> after = entranceName(openDocument, id);
        if (after.isPresent() && !after.equals(before)) {
            openDocument.rememberTunnelEntranceName(after.get());
        }
    }

    private static Optional<String> entranceName(OpenDocument openDocument, ElementId id) {
        return openDocument.document().component(id)
                .filter(component -> Tunnels.kindOf(component).orElse(null) == TunnelKind.ENTRANCE)
                .map(Tunnels::nameOf);
    }
}
