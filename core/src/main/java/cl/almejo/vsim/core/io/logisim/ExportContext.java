package cl.almejo.vsim.core.io.logisim;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * What an export rule may need to know beyond the part itself: the VSim circuit it is in (the document's or a
 * component's name), whether that is the main circuit (where a Switch can be a Logisim input pin), the width of the
 * net on each of the part's pins, by pin index (a tunnel's own pin has none; 0 when unknown), and whether each pin is
 * connected to something (a wire or another pin).
 */
record ExportContext(String circuit, boolean main, IntUnaryOperator netWidth, IntPredicate connected) {

    ExportContext {
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(netWidth, "netWidth");
        Objects.requireNonNull(connected, "connected");
    }
}
