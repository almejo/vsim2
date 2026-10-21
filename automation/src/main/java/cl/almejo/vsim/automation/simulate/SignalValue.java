package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.value.Value;

import java.util.Objects;

/**
 * The value on one pin's net: {@code width} bits, {@code bits} most significant first ({@code 0}, {@code 1}, {@code Z}),
 * {@code hex} as the editor shows it, {@code highImpedanceMask} with {@code 1} on each Z bit and {@code conflict} when
 * two drivers disagree (a short circuit).
 */
public record SignalValue(PinReference pin, int width, String bits, String hex, String highImpedanceMask,
                          boolean conflict) {

    public SignalValue {
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(bits, "bits");
        Objects.requireNonNull(hex, "hex");
        Objects.requireNonNull(highImpedanceMask, "highImpedanceMask");
    }

    public static SignalValue of(PinReference pin, Value value) {
        return new SignalValue(pin, value.width(), ValueText.bits(value), value.hex(),
                ValueText.highImpedanceMask(value), value.conflict());
    }
}
