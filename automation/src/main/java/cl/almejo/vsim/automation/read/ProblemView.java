package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;

import java.util.List;
import java.util.Objects;

/**
 * One entry of the Problems panel. {@code kind}: SHORT_CIRCUIT, WIDTH_MISMATCH, DISPLAY_BUS_OVERRIDE or one of
 * TUNNEL_DUPLICATE_ENTRANCE, TUNNEL_EXIT_WITHOUT_ENTRANCE, TUNNEL_ENTRANCE_WITHOUT_EXITS, TUNNEL_EXIT_DRIVEN,
 * TUNNEL_LONE_TUNNEL (a tunnel without direction alone with its name),
 * INTERFACE_UNCONNECTED_INSIDE (an outer terminal whose «Pin de entrada»/«Pin de salida» inside the component
 * connects to nothing), LINKED_FILE_MISSING, LINKED_FILE_UNREADABLE, OWN_CONTENTS_OVER_LINK (a piece keeps its own
 * contents of a memory linked to a file, and starts from them, not from the file) or OWN_CONTENTS_FOR_NOTHING (a piece
 * keeps contents for a memory no longer in its component: ignored), PIN_LOOSENED_BY_SHAPE_CHANGE (a pin left unconnected
 * on purpose by a shape change), PIN_PLACED_ELSEWHERE (a pin an inside edit added went to another spot of the piece),
 * WIRE_OF_REMOVED_PIN (a wire whose pin an inside edit removed) — these three only during the session that made them —,
 * WIRE_AT_PIECE_EDGE (a wire end at a user component's edge where no pin is, as a removed pin leaves it; found from the
 * circuit, so also after reopening),
 * OUTDATED_WOULD_BREAK, OUTDATED_CANNOT_UPDATE (a piece older than the library's that an update would break or cannot
 * take), or for a component library file not loaded LIBRARY_UNREADABLE, LIBRARY_INVALID, LIBRARY_DUPLICATE_ID or
 * LIBRARY_DIRECTORY_GONE (they name no part); every row the Problemas list shows for the circuit;
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
