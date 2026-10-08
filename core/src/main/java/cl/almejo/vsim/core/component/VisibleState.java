package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

import java.util.List;

public sealed interface VisibleState {

    VisibleState NONE = new None();

    record None() implements VisibleState {
    }

    record LedState(Value value) implements VisibleState {
    }

    record SwitchState(Value value) implements VisibleState {
    }

    record ClockState(Value value) implements VisibleState {
    }

    /** Value with one bit per display input; pin 0 is the least significant bit. */
    record SevenSegmentsState(Value value) implements VisibleState {
    }

    record TimeDiagramState(List<TimeDiagramSample> samples) implements VisibleState {
        public TimeDiagramState {
            samples = List.copyOf(samples);
        }
    }
}
