package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

import java.util.List;

public record TimeDiagramSample(long time, List<Value> values) {
    public TimeDiagramSample {
        values = List.copyOf(values);
    }
}
