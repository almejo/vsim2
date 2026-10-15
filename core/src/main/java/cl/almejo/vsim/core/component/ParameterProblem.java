package cl.almejo.vsim.core.component;

import java.util.List;
import java.util.Objects;

/**
 * Why a set of parameters is refused by a type's own check: the parameter at fault, a message key of the UI bundle and
 * the arguments of that message.
 */
public record ParameterProblem(String parameterName, String messageKey, List<Object> arguments) {

    public ParameterProblem {
        Objects.requireNonNull(parameterName, "parameterName");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }
}
