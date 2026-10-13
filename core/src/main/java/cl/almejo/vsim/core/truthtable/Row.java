package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.value.Value;

import java.util.List;

/**
 * One input combination and the outputs it gave. {@code settled} is false when the circuit was still changing at the
 * limit (an oscillating loop); the outputs are then the values seen at that moment, shown as
 * {@link TruthTableText#UNSETTLED}.
 */
public record Row(List<Value> inputs, List<Value> outputs, boolean settled) {

    public Row {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
