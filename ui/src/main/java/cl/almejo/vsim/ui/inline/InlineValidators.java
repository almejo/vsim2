package cl.almejo.vsim.ui.inline;

import java.util.Optional;
import java.util.function.Function;

/** The validators given to an {@link InlineEditRequest}: each one returns the problem message, empty when valid. */
public final class InlineValidators {

    private InlineValidators() {
    }

    /** Every text is fine, blank included (what a blank text means is the caller's decision). */
    public static Function<String, Optional<String>> anyText() {
        return text -> Optional.empty();
    }
}
