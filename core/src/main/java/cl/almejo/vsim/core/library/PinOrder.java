package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.document.ElementId;

import java.util.List;

/**
 * The interface pins of a template in box order: {@code inputs} top to bottom on the left, {@code outputs} top to
 * bottom on the right. The ids are those of the selected {@code input-pin}/{@code output-pin} components.
 */
public record PinOrder(List<ElementId> inputs, List<ElementId> outputs) {

    public PinOrder {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public boolean isEmpty() {
        return inputs.isEmpty() && outputs.isEmpty();
    }
}
