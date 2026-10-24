package cl.almejo.vsim.core.component.appearance;

import java.util.List;

/** A live editor validation message; its arguments identify the affected pins. */
public record DraftProblem(Kind kind, List<String> pins) {
    public enum Kind { COINCIDING_PINS, PIN_OFF_EDGE, EMPTY_DRAWING }

    public DraftProblem {
        pins = List.copyOf(pins);
    }

    public boolean blocksAccept() {
        return kind == Kind.COINCIDING_PINS;
    }
}
