package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.WidthChange;
import cl.almejo.vsim.core.command.WidthsFollowConnectionCommand;
import cl.almejo.vsim.core.command.WrapsCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.List;
import java.util.Optional;

/**
 * The status-line notice of «Anchos que siguen lo conectado»: «Constante tomó el ancho de lo conectado: 8 bits», or
 * how many parts did, so a width that changed with a wire or a move never goes unnoticed (undo puts it back).
 */
public final class WidthsFollowNotice {

    private WidthsFollowNotice() {
    }

    /** The notice for {@code command} as last applied to {@code document}; empty when no part changed its width. */
    public static Optional<String> of(CircuitDocument document, Command command) {
        List<WidthChange> changes = changesOf(command);
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        if (changes.size() > 1) {
            return Optional.of(Messages.format("widthsFollow.many", changes.size()));
        }
        WidthChange change = changes.get(0);
        Optional<Component> component = document.component(change.componentId());
        return component.map(found -> Messages.format("widthsFollow.one",
                SelectionMoves.componentName(found, document), change.newWidth()));
    }

    private static List<WidthChange> changesOf(Command command) {
        Command current = command;
        while (true) {
            if (current instanceof WidthsFollowConnectionCommand following) {
                return following.widthChanges();
            }
            if (!(current instanceof WrapsCommand wrapper)) {
                return List.of();
            }
            current = wrapper.wrapped();
        }
    }
}
