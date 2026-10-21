package cl.almejo.vsim.automation;

import java.util.List;

/**
 * A user preference the AI may only propose (spec §7): VSim shows the proposal with «Aceptar» / «No» and changes it
 * only after «Aceptar». Values travel as text, one of {@link #allowedValues()}.
 */
public enum ProposedPreference {
    /** The window theme: light, dark or as the system. */
    THEME(List.of("LIGHT", "DARK", "SYSTEM")),
    /** The language, applied at VSim's next start; {@code system} follows the computer's. */
    LANGUAGE(List.of("es", "en", "system"));

    private final List<String> allowedValues;

    ProposedPreference(List<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public List<String> allowedValues() {
        return allowedValues;
    }
}
