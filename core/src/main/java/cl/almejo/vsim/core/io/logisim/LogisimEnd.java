package cl.almejo.vsim.core.io.logisim;

import java.util.Objects;

/**
 * One connection point («end») of a placed Logisim component, where Logisim 2.7 puts it: {@code point} in the circuit's
 * pixels, {@code width} in bits, and what it does in its net ({@link Role}). {@code name} is the VSim pin it becomes
 * when the VSim part has one ({@code A}, {@code Cout}, {@code in1}, {@code CLK}; {@code inVsim}), otherwise Logisim's
 * own name for it ({@code OE}, {@code upper}, {@code EN}). A splitter's ends are {@code bus}, {@code end0}, … (its
 * pins follow {@link MappedPart.Extras#splitterEnds}); a subcircuit instance's are named by the location of the
 * circuit's {@code Pin} inside it ({@code 100,150}).
 */
public record LogisimEnd(String name, LogisimPoint point, Role role, int width, boolean inVsim) {

    /** What an end does to its net, for the direction of splitters and tunnels (gap analysis §2.2). */
    public enum Role {
        /** It reads the net (a gate input, a register's D). */
        INPUT,
        /** It drives the net (a gate output, a circuit's input pin). */
        OUTPUT,
        /** It may drive and read (a RAM's shared data bus). */
        BIDIRECTIONAL,
        /** It only joins nets: a splitter or tunnel end, which has no direction of its own. */
        ALIAS;

        /** Whether an end of this role puts a value on its net. */
        public boolean drives() {
            return this == OUTPUT || this == BIDIRECTIONAL;
        }
    }

    public LogisimEnd {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(role, "role");
        if (width < 0) {
            throw new IllegalArgumentException("negative width " + width);
        }
    }
}
