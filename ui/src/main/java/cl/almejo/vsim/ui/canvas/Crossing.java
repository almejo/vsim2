package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

/**
 * A point where a horizontal and a vertical wire cross without connecting: strictly inside both, and neither a wire
 * end nor a pin location (either would join the two wires, as in {@code NetlistCompiler}).
 */
public record Crossing(GridPoint point, ElementId horizontalWire, ElementId verticalWire) {
}
