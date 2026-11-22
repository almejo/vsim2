package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ProbeTarget;

/**
 * The name a waveform channel with an empty stored name shows (computed live from its target, in the user's language;
 * the editor's is {@code ProbeNames.defaultName}). Deleting what such a channel watches stores this name in the moved
 * channel, so its row keeps the name the user saw ({@link ProbeResolver#afterRemoval}).
 */
@FunctionalInterface
public interface ProbeNaming {

    /** No name to keep: a moved channel keeps its empty name and shows its new target's (headless callers, tests). */
    ProbeNaming NONE = (target, document, netlist, registry) -> "";

    /** What a channel on {@code target} shows in {@code document}, whose nets are {@code netlist}. */
    String defaultName(ProbeTarget target, CircuitDocument document, Netlist netlist, ComponentTypeRegistry registry);
}
