package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.Map;
import java.util.Optional;

/**
 * What a tunnel placed from the palette starts with (spec §2, §5): a new entrada proposes the first free name (T1,
 * T2, …) and the first colour no entrada uses; a new salida proposes the name of the last entrada placed or renamed in
 * this document, else T1. Every other type starts with its defaults.
 */
public final class NewTunnels {

    private NewTunnels() {
    }

    /** The parameters a new component of {@code type} starts with in {@code openDocument}. */
    public static Map<String, Object> parameters(OpenDocument openDocument, ComponentType type) {
        Optional<TunnelKind> kind = TunnelKind.of(type.id());
        if (kind.isEmpty()) {
            return Map.of();
        }
        CircuitDocument document = openDocument.document();
        return switch (kind.get()) {
            case ENTRANCE -> Map.of(Tunnels.NAME, Tunnels.proposedEntranceName(document),
                    Tunnels.COLOR, Tunnels.freeColor(document));
            case EXIT -> Map.of(Tunnels.NAME,
                    Tunnels.proposedExitName(document, openDocument.lastTunnelEntranceName()));
        };
    }

    /** Remembers {@code placed}'s name when it is an entrada, so the next salida proposes it. */
    public static void placed(OpenDocument openDocument, Component placed) {
        if (Tunnels.kindOf(placed).orElse(null) == TunnelKind.ENTRANCE) {
            openDocument.rememberTunnelEntranceName(Tunnels.nameOf(placed));
        }
    }
}
