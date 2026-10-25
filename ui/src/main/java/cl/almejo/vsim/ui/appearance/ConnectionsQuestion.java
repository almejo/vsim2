package cl.almejo.vsim.ui.appearance;

import java.util.List;

/**
 * What the connections sheet tells the user before a change of shape is applied: the pins whose wires cannot follow,
 * the pieces that keep their old shape so as not to touch another wire, and the other pieces that change without
 * trouble. {@code blockedInstance} is the case of one piece that cannot change at all: the only answer is «Volver».
 */
public record ConnectionsQuestion(List<Entry> disconnected, List<Entry> kept, List<String> unaffectedPieces,
                                  boolean blockedInstance) {

    /** A pin of a piece; {@code touchedNet} names the net it would touch (empty for a pin left to connect by hand). */
    public record Entry(String piece, String pin, String touchedNet) { }

    public ConnectionsQuestion {
        disconnected = List.copyOf(disconnected);
        kept = List.copyOf(kept);
        unaffectedPieces = List.copyOf(unaffectedPieces);
    }
}
