package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.simulation.FlatCircuit;

import java.util.List;
import java.util.Map;

/** What {@link TruthTableScope#prepare} found: a circuit ready to evaluate, or why there is no table. */
public sealed interface Preparation permits Preparation.Ready, Preparation.Refused {

    /** At most 1 024 rows (spec §8.18). */
    int MAXIMUM_INPUT_BITS = 10;

    enum Reason { NO_INPUTS, NO_OUTPUTS, TOO_MANY_INPUT_BITS, SEQUENTIAL }

    /**
     * {@code evaluated} is the temporary document built from the scope; {@code circuit} its flat circuit, immutable and
     * shared by every row; {@code outputNets} maps each output column's component id to the flat net it reads.
     */
    record Ready(CircuitDocument evaluated, List<Column> inputs, List<Column> outputs, FlatCircuit circuit,
                 Map<String, String> outputNets, boolean hasFeedback) implements Preparation {

        public Ready {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            outputNets = Map.copyOf(outputNets);
        }

        /** The input columns' widths added up. */
        public int inputBits() {
            return inputs.stream().mapToInt(Column::width).sum();
        }

        /** 2 to the power of {@link #inputBits()}. */
        public int rowCount() {
            return 1 << inputBits();
        }
    }

    /** {@code inputBits} is the number of input bits in scope (the message for too many bits shows it). */
    record Refused(Reason reason, int inputBits) implements Preparation {
    }
}
