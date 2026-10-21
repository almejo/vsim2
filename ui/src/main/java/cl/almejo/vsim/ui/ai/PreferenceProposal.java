package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Objects;

/**
 * A change of a user preference the AI asked for (spec §7: «¿Paso al tema oscuro para el video?»), waiting for the
 * user's «Aceptar» / «No» in the panel. {@code reason} is the AI's text: shown as data, never acted on.
 */
public record PreferenceProposal(String id, ProposedPreference preference, String value, String reason) {

    public PreferenceProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(preference, "preference");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
    }

    /** What accepting changes, in the user's words («Cambiar el tema a «Oscuro»»). */
    public String description() {
        return switch (preference) {
            case THEME -> Messages.format("ai.proposal.THEME", Messages.text(switch (value) {
                case "LIGHT" -> "preferences.theme.LIGHT";
                case "DARK" -> "preferences.theme.DARK";
                default -> "preferences.theme.SYSTEM";
            }));
            case LANGUAGE -> Messages.format("ai.proposal.LANGUAGE", Messages.text(switch (value) {
                case "es" -> "preferences.language.spanish";
                case "en" -> "preferences.language.english";
                default -> "preferences.language.system";
            }));
        };
    }
}
