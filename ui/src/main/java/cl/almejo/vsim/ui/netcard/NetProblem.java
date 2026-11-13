package cl.almejo.vsim.ui.netcard;

import java.util.List;
import java.util.Objects;

/** What is wrong with a net, shown on the signal card and never hidden. */
public sealed interface NetProblem {

    /** Several drivers impose different values: each with the value it imposes. */
    record ShortCircuit(List<NetEnd> drivers) implements NetProblem {
        public ShortCircuit {
            drivers = List.copyOf(drivers);
        }
    }

    /** No pin that could drive the net touches it: it is Z. */
    record Undriven() implements NetProblem {
    }

    /** Pins that could drive it touch it, but none does now: the simulation gives it Z (a released tri-state). */
    record Floating() implements NetProblem {
    }

    /** Pins of different widths on one net: {@code text} as the Problems list words it. */
    record WidthMismatch(String text) implements NetProblem {
        public WidthMismatch {
            Objects.requireNonNull(text, "text");
        }
    }
}
