package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.value.Value;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

/**
 * The combinational blocks written again straight from M7 spec §2.1, in {@link BigInteger} and with the flags taken
 * from the sign bits, sharing no code with the behaviours or {@link BlockSignals}: given the value on every input pin
 * (in pin order), the value every output pin must show (in pin order, after the inputs).
 */
record CombinationalReference(String description, String typeId, Map<String, Object> parameters, int inputCount,
                              Function<Value[], Value[]> outputs) {

    static final int ADDER_CARRY_IN = 2;
    static final int DECODER_ENABLE = 1;

    @Override
    public String toString() {
        return description;
    }

    static CombinationalReference adder(int width, int delay) {
        return new CombinationalReference("adder " + width, StandardComponentTypes.ADDER,
                Map.of(StandardComponentTypes.WIDTH, width, "delay", delay), 3, inputs -> {
            Boolean carryIn = control(inputs[2], false);
            if (!defined(inputs[0]) || !defined(inputs[1]) || carryIn == null) {
                return new Value[]{Value.highImpedance(width), Value.HIGH_IMPEDANCE};
            }
            BigInteger total = unsigned(inputs[0]).add(unsigned(inputs[1])).add(carryIn ? BigInteger.ONE
                    : BigInteger.ZERO);
            return new Value[]{number(width, total), Value.of(total.testBit(width))};
        });
    }

    static CombinationalReference alu(int width, int delay) {
        return new CombinationalReference("alu " + width, StandardComponentTypes.ALU,
                Map.of(StandardComponentTypes.WIDTH, width, "delay", delay), 3, inputs -> {
            Value[] undefined = {Value.highImpedance(width), Value.HIGH_IMPEDANCE, Value.HIGH_IMPEDANCE,
                    Value.HIGH_IMPEDANCE, Value.HIGH_IMPEDANCE};
            if (!defined(inputs[2])) {
                return undefined;
            }
            int operation = (int) inputs[2].bits();
            boolean readsSecond = operation <= 0b100;
            if (!defined(inputs[0]) || readsSecond && !defined(inputs[1])) {
                return undefined;
            }
            BigInteger first = unsigned(inputs[0]);
            BigInteger second = unsigned(inputs[1]);
            boolean firstSign = first.testBit(width - 1);
            boolean secondSign = second.testBit(width - 1);
            BigInteger result;
            boolean carry = false;
            boolean overflow = false;
            switch (operation) {
                case 0b000 -> {
                    BigInteger total = first.add(second);
                    result = total;
                    carry = total.testBit(width);
                    boolean resultSign = total.testBit(width - 1);
                    overflow = firstSign == secondSign && resultSign != firstSign;
                }
                case 0b001 -> {
                    result = first.subtract(second);
                    carry = first.compareTo(second) < 0;
                    boolean resultSign = modulo(width, result).testBit(width - 1);
                    overflow = firstSign != secondSign && resultSign != firstSign;
                }
                case 0b010 -> result = first.and(second);
                case 0b011 -> result = first.or(second);
                case 0b100 -> result = first.xor(second);
                case 0b101 -> result = first.not();
                case 0b110 -> {
                    result = first.shiftLeft(1);
                    carry = first.testBit(width - 1);
                }
                default -> {
                    result = first.shiftRight(1);
                    carry = first.testBit(0);
                }
            }
            BigInteger shown = modulo(width, result);
            return new Value[]{number(width, shown), Value.of(shown.signum() == 0), Value.of(shown.testBit(width - 1)),
                    Value.of(carry), Value.of(overflow)};
        });
    }

    static CombinationalReference comparator(int width, boolean signed, int delay) {
        return new CombinationalReference("comparator " + width + (signed ? " signed" : " unsigned"),
                StandardComponentTypes.COMPARATOR, Map.of(StandardComponentTypes.WIDTH, width, "delay", delay,
                StandardComponentTypes.SIGNEDNESS, signed ? StandardComponentTypes.SIGNEDNESS_SIGNED
                        : StandardComponentTypes.SIGNEDNESS_UNSIGNED), 2, inputs -> {
            if (!defined(inputs[0]) || !defined(inputs[1])) {
                return new Value[]{Value.HIGH_IMPEDANCE, Value.HIGH_IMPEDANCE, Value.HIGH_IMPEDANCE};
            }
            int comparison = signed ? signedNumber(inputs[0]).compareTo(signedNumber(inputs[1]))
                    : unsigned(inputs[0]).compareTo(unsigned(inputs[1]));
            return new Value[]{Value.of(comparison < 0), Value.of(comparison == 0), Value.of(comparison > 0)};
        });
    }

    static CombinationalReference mux(int dataInputs, int width, int delay) {
        return new CombinationalReference("mux " + dataInputs + "x" + width, StandardComponentTypes.MUX,
                Map.of(StandardComponentTypes.WIDTH, width, "delay", delay, "inputs", String.valueOf(dataInputs)),
                dataInputs + 1, inputs -> {
            Value select = inputs[dataInputs];
            if (!defined(select) || !defined(inputs[(int) select.bits()])) {
                return new Value[]{Value.highImpedance(width)};
            }
            return new Value[]{inputs[(int) select.bits()]};
        });
    }

    static CombinationalReference decoder(int inputBits, int delay) {
        int outputCount = 1 << inputBits;
        return new CombinationalReference("decoder " + inputBits, StandardComponentTypes.DECODER,
                Map.of("inputBits", inputBits, "delay", delay), 2, inputs -> {
            Value[] outputs = new Value[outputCount];
            Boolean enabled = control(inputs[1], true);
            for (int output = 0; output < outputCount; output++) {
                if (Boolean.FALSE.equals(enabled)) {
                    outputs[output] = Value.ZERO;
                } else if (enabled == null || !defined(inputs[0])) {
                    outputs[output] = Value.HIGH_IMPEDANCE;
                } else {
                    outputs[output] = Value.of(inputs[0].bits() == output);
                }
            }
            return outputs;
        });
    }

    /** No Z bit and no conflict: a data input the result can use. */
    static boolean defined(Value value) {
        return value.highImpedanceMask() == 0L && !value.conflict();
    }

    /** A 1-bit control input: all Z takes the rest value, a conflict is undefined (null). */
    static Boolean control(Value value, boolean restValue) {
        if (value.conflict()) {
            return null;
        }
        if (value.highImpedanceMask() != 0L) {
            return restValue;
        }
        return value.bits() == 1L;
    }

    private static BigInteger unsigned(Value value) {
        BigInteger number = BigInteger.ZERO;
        for (int bit = 0; bit < value.width(); bit++) {
            if ((value.bits() >>> bit & 1L) == 1L) {
                number = number.setBit(bit);
            }
        }
        return number;
    }

    private static BigInteger signedNumber(Value value) {
        BigInteger number = unsigned(value);
        return number.testBit(value.width() - 1) ? number.subtract(BigInteger.ONE.shiftLeft(value.width())) : number;
    }

    private static BigInteger modulo(int width, BigInteger number) {
        return number.mod(BigInteger.ONE.shiftLeft(width));
    }

    private static Value number(int width, BigInteger number) {
        return Value.of(width, modulo(width, number).longValue());
    }
}
