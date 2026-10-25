package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;

import java.util.List;
import java.util.Objects;

/**
 * One entry of the Problems panel. {@code kind}: SHORT_CIRCUIT, WIDTH_MISMATCH, DISPLAY_BUS_OVERRIDE or one of
 * TUNNEL_DUPLICATE_ENTRANCE, TUNNEL_EXIT_WITHOUT_ENTRANCE, TUNNEL_ENTRANCE_WITHOUT_EXITS, TUNNEL_EXIT_DRIVEN,
 * DISCONNECTED_PIN, LOOSE_WIRE_END or INTERFACE_UNCONNECTED_INSIDE (an outer terminal whose «Pin de
 * entrada»/«Pin de salida» inside the component connects to nothing);
 * {@code warning} for the softer ones that may be on purpose while building. {@code elementIds} are the parts of the
 * circuit it is about (for a problem inside a user component, the instance that holds it); {@code pins} the pins of
 * those parts it names; {@code insidePaths} the ids inside user components ({@code c5/c2}) when it lives there.
 * {@code message} is a short English explanation for the AI.
 */
public record ProblemView(String kind, boolean warning, String netId, String message, List<String> elementIds,
                          List<PinReference> pins, List<String> insidePaths) {

    public ProblemView {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(netId, "netId");
        Objects.requireNonNull(message, "message");
        elementIds = List.copyOf(elementIds);
        pins = List.copyOf(pins);
        insidePaths = List.copyOf(insidePaths);
    }
}
