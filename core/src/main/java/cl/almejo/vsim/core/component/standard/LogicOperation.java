package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.value.Value;

import java.util.Locale;

/** What a gate does with its inputs; «Negar salida» (NAND, NOR, XNOR) is a parameter of the gate, not an operation. */
public enum LogicOperation {
    AND, OR, XOR;

    /** Bit by bit, at any width (a high-impedance bit is neutral, see {@link Value#and}). */
    public Value combine(Value left, Value right) {
        return switch (this) {
            case AND -> left.and(right);
            case OR -> left.or(right);
            case XOR -> left.xor(right);
        };
    }

    /** The id of the gate type: {@code and}, {@code or} or {@code xor}. */
    public String typeId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
