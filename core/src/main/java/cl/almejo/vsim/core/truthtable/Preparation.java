package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.FlatCircuit;

import java.util.List;
import java.util.Map;

/** What {@link TruthTableScope#prepare} found: a circuit ready to evaluate, or why there is no table. */
public sealed interface Preparation permits Preparation.Ready, Preparation.Refused {

    /** Up to 1 024 rows the table is computed at once (spec §5.4). */
    int DIRECT_INPUT_BITS = 10;
    /** At most 65 536 rows; above {@link #DIRECT_INPUT_BITS} the user is asked first (spec §5.4). */
    int MAXIMUM_INPUT_BITS = 16;

    enum Reason { NO_INPUTS, NO_OUTPUTS, TOO_MANY_INPUT_BITS, SEQUENTIAL }

    /**
     * {@code evaluated} is the temporary document built from the scope; {@code circuit} its flat circuit, immutable and
     * shared by every row; {@code outputNets} maps each output column's component id to the flat net it reads.
     * {@code liveInputPins} holds, for each input column, the pin of the live document whose net carries that input:
     * the switch or input pin itself, or the instance's pin for a single user-component instance (spec §5.4).
     */
    record Ready(CircuitDocument evaluated, List<Column> inputs, List<Column> outputs, FlatCircuit circuit,
                 Map<String, String> outputNets, boolean hasFeedback, List<PinRef> liveInputPins)
            implements Preparation {

        public Ready {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            outputNets = Map.copyOf(outputNets);
            liveInputPins = List.copyOf(liveInputPins);
            if (liveInputPins.size() != inputs.size()) {
                throw new IllegalArgumentException("one live pin per input column: " + liveInputPins.size()
                        + " pins, " + inputs.size() + " inputs");
            }
        }

        /** A whole circuit or a selection: each input is read on pin 0 of the live component of the same id. */
        public Ready(CircuitDocument evaluated, List<Column> inputs, List<Column> outputs, FlatCircuit circuit,
                     Map<String, String> outputNets, boolean hasFeedback) {
            this(evaluated, inputs, outputs, circuit, outputNets, hasFeedback, inputs.stream()
                    .map(column -> new PinRef(new ElementId(column.componentId()), 0))
                    .toList());
        }

        /** The input columns' widths added up. */
        public int inputBits() {
            return inputs.stream().mapToInt(Column::width).sum();
        }

        /** 2 to the power of {@link #inputBits()}. */
        public int rowCount() {
            return 1 << inputBits();
        }

        /**
         * True when every input is a switch of the live document's top level (not an input pin, not a pin of a single
         * instance): a row can then be tried in the live circuit by setting those switches (spec §5.4).
         */
        public boolean inputsAreTopLevelSwitches() {
            for (int index = 0; index < inputs.size(); index++) {
                ElementId id = new ElementId(inputs.get(index).componentId());
                PinRef live = liveInputPins.get(index);
                boolean liveSwitch = live.pinIndex() == 0 && live.componentId().equals(id)
                        && evaluated.component(id)
                        .map(component -> StandardComponentTypes.isSwitchOrPushButton(component.typeId()))
                        .orElse(false);
                if (!liveSwitch) {
                    return false;
                }
            }
            return true;
        }

        /** More than 1 024 rows: the user confirms before they are computed (spec §5.4). */
        public boolean needsConfirmation() {
            return inputBits() > DIRECT_INPUT_BITS;
        }
    }

    /** {@code inputBits} is the number of input bits in scope (the message for too many bits shows it). */
    record Refused(Reason reason, int inputBits) implements Preparation {
    }
}
