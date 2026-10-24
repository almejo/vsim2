package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;

/**
 * A command that may leave pins of placed pieces unconnected on purpose («Aplicar y conectar a mano», spec §6.4).
 * The editor marks those pins until they are connected again or the command is undone.
 */
public interface LoosensPins {

    /** The pins this command leaves unconnected, by their index after the command; empty when none. */
    List<PinRef> loosenedPins();
}
