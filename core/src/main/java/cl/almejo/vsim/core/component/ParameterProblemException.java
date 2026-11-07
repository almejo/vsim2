package cl.almejo.vsim.core.component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by {@link ComponentType#resolveParameters} when the type's own check refuses the parameters. It is an
 * {@link IllegalArgumentException} (its message names the bundle key, as before) that also carries the problem and the
 * raw parameters, so a caller with words of its own (the AI's API) can explain it.
 */
public final class ParameterProblemException extends IllegalArgumentException {

    private final transient ParameterProblem problem;
    private final transient Map<String, Object> rawParameters;

    public ParameterProblemException(ParameterProblem problem, Map<String, Object> rawParameters) {
        super("parameter " + problem.parameterName() + ": " + problem.messageKey() + " " + problem.arguments());
        this.problem = problem;
        this.rawParameters = rawParameters == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawParameters));
    }

    public ParameterProblem problem() {
        return problem;
    }

    /** The parameters that were refused, as given. */
    public Map<String, Object> rawParameters() {
        return rawParameters;
    }
}
