package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.document.ElementId;

public record PinRef(ElementId componentId, int pinIndex) {
}
