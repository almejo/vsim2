package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** The validators given to an {@link InlineEditRequest}: each one returns the problem message, empty when valid. */
public final class InlineValidators {

    private InlineValidators() {
    }

    /** Every text is fine, blank included (what a blank text means is the caller's decision). */
    public static Function<String, Optional<String>> anyText() {
        return text -> Optional.empty();
    }

    /**
     * The value of a Constante: the type's own check of {@code parameters} with the typed text as «value», so the
     * messages are the ones of the parameter form («No cabe en 8 bits», «Valor no válido…»).
     */
    public static Function<String, Optional<String>> constantValue(ComponentType type,
                                                                   Map<String, Object> parameters) {
        return constantValue(type, () -> parameters);
    }

    /**
     * M7: a value typed into a bus input pin, at the width {@code currentWidth} gives on every check: {@code 0b…},
     * {@code 0x…}, a decimal or a negative decimal (two's complement), with the messages of the Constante.
     */
    public static Function<String, Optional<String>> busValue(IntSupplier currentWidth) {
        return text -> {
            if (!ConstantValue.isValidText(text)) {
                return Optional.of(Messages.text("constant.invalidValue"));
            }
            int width = currentWidth.getAsInt();
            return ConstantValue.fitsText(text, width) ? Optional.empty()
                    : Optional.of(Messages.format("constant.doesNotFit", width));
        };
    }

    /** Same, asking {@code currentParameters} on every check, so a width changed meanwhile is taken into account. */
    public static Function<String, Optional<String>> constantValue(ComponentType type,
                                                                   Supplier<Map<String, Object>> currentParameters) {
        return text -> {
            Map<String, Object> typed = new HashMap<>(currentParameters.get());
            typed.put("value", text.strip());
            Optional<ParameterProblem> problem = type.check(typed);
            return problem.map(found -> Messages.format(found.messageKey(), found.arguments().toArray()));
        };
    }
}
