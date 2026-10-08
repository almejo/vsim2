package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.value.Value;

import java.util.Locale;

public enum LogicOperation {
    AND, OR, XOR, NAND, NOR;

    public Value combine(Value left, Value right) {
        return switch (this) {
            case AND, NAND -> left.and(right);
            case OR, NOR -> left.or(right);
            case XOR -> left.xor(right);
        };
    }

    public boolean negated() {
        return this == NAND || this == NOR;
    }

    public String typeId(int inputCount) {
        return name().toLowerCase(Locale.ROOT) + inputCount;
    }
}
