package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.util.Arrays;
import java.util.Optional;

/**
 * The two tunnel parts: an «entrada» takes the signal of its wire into the name and every «salida» of that name gives
 * it out on its own wire. Electrically both only join nets; the direction is a teaching and checking aid.
 */
public enum TunnelKind {
    ENTRANCE(StandardComponentTypes.TUNNEL_ENTRANCE),
    EXIT(StandardComponentTypes.TUNNEL_EXIT);

    private final String typeId;

    TunnelKind(String typeId) {
        this.typeId = typeId;
    }

    public String typeId() {
        return typeId;
    }

    /** The kind of tunnel {@code typeId} is, empty for every other component type. */
    public static Optional<TunnelKind> of(String typeId) {
        return Arrays.stream(values()).filter(kind -> kind.typeId.equals(typeId)).findFirst();
    }
}
