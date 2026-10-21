package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the AI may change around the circuit (spec §7): editor settings in a temporary layer of the circuit's window,
 * and preferences only through a proposal the user accepts.
 */
final class SettingsTools {

    static final String SET_EDITOR_SETTING = "vsim_set_editor_setting";
    static final String PROPOSE_PREFERENCE = "vsim_propose_preference";
    static final String AWAIT_PROPOSAL = "vsim_await_proposal";

    private SettingsTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(setEditorSetting(api), proposePreference(api), awaitProposal(api));
    }

    private static Tool setEditorSetting(AutomationApi api) {
        StringBuilder values = new StringBuilder();
        for (EditorSetting setting : EditorSetting.values()) {
            values.append(values.isEmpty() ? "" : "; ").append(setting.name()).append(": ")
                    .append(String.join(" | ", setting.allowedValues()));
        }
        return new Tool(SET_EDITOR_SETTING, "Change an editor setting while working",
                "Changes an editor setting (the magnet «Ajustar» and «Ajustar a ▸», the grid style and spacing, "
                        + "«Mostrar guías», «Los cables siguen») in a temporary layer of the circuit's window only: "
                        + "the user's saved preferences and other windows never change, the panel lists it under "
                        + "«Ajustes que tocó la IA», and it goes back when the task ends unless the user keeps it. "
                        + "Only inside a task on that circuit (vsim_begin_task first). "
                        + "Values: " + values + ".",
                Schema.object("")
                        .property("document", Common.document())
                        .property("setting", Schema.enumeration("Which setting",
                                Arrays.stream(EditorSetting.values()).map(Enum::name).toList()))
                        .property("value", Schema.nonEmptyString("One of the setting's values"))
                        .required("document", "setting", "value").build(),
                Schema.result("The setting now in the window")
                        .property("document", Schema.string(""))
                        .property("setting", Schema.string(""))
                        .property("value", Schema.string(""))
                        .required("document", "setting", "value").build(),
                Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "setting", "GRID_STYLE", "value", "LINES"),
                        Map.of("document", "circuit-1", "setting", "SNAP_TO_GUIDES", "value", "true")),
                arguments -> {
                    EditorSetting setting = arguments.enumeration("setting", EditorSetting.class);
                    String value = arguments.string("value");
                    api.setEditorSetting(arguments.document(), setting, value);
                    return Tool.Output.of(Map.of("document", arguments.document().value(), "setting",
                            setting.name(), "value", value));
                });
    }

    private static Tool proposePreference(AutomationApi api) {
        StringBuilder values = new StringBuilder();
        for (ProposedPreference preference : ProposedPreference.values()) {
            values.append(values.isEmpty() ? "" : "; ").append(preference.name()).append(": ")
                    .append(String.join(" | ", preference.allowedValues()));
        }
        return new Tool(PROPOSE_PREFERENCE, "Propose a preference change",
                "The user's preferences (theme, language) are theirs: VSim only shows your proposal with «Aceptar» "
                        + "and «No» in its AI panel and changes nothing until the user accepts. Wait for the answer "
                        + "with vsim_await_proposal. Values: " + values + ".",
                Schema.object("")
                        .property("preference", Schema.enumeration("Which preference",
                                Arrays.stream(ProposedPreference.values()).map(Enum::name).toList()))
                        .property("value", Schema.nonEmptyString("One of the preference's values"))
                        .property("reason", Schema.nonEmptyString("One short line for the user, in their language "
                                + "(«¿Paso al tema oscuro para el video?»)"))
                        .required("preference", "value", "reason").build(),
                Schema.result("The proposal shown")
                        .property("proposalId", Schema.string("For vsim_await_proposal"))
                        .required("proposalId").build(),
                Tool.Hints.CHANGES,
                List.of(Map.of("preference", "THEME", "value", "DARK", "reason", "¿Paso al tema oscuro para el "
                        + "video?")),
                arguments -> Tool.Output.of(Map.of("proposalId", api.proposePreference(
                        arguments.enumeration("preference", ProposedPreference.class), arguments.string("value"),
                        arguments.string("reason")))));
    }

    private static Tool awaitProposal(AutomationApi api) {
        long maximumSeconds = AutomationApi.MAXIMUM_PROPOSAL_WAIT.toSeconds();
        return new Tool(AWAIT_PROPOSAL, "Wait for the user's answer",
                "Waits up to waitSeconds (at most " + maximumSeconds + ") for the user's answer to a proposal: "
                        + "ACCEPTED, DECLINED, or WAITING when the user has not answered yet (ask again).",
                Schema.object("")
                        .property("proposalId", Schema.nonEmptyString("From vsim_propose_preference"))
                        .property("waitSeconds", Schema.integer("How long to wait (default " + maximumSeconds + ")",
                                0, maximumSeconds))
                        .required("proposalId").build(),
                Schema.result("The answer")
                        .property("proposalId", Schema.string(""))
                        .property("answer", Schema.enumeration("", List.of("ACCEPTED", "DECLINED", "WAITING")))
                        .required("proposalId", "answer").build(),
                Tool.Hints.READS, List.of(Map.of("proposalId", "proposal-1", "waitSeconds", 20)),
                arguments -> {
                    String proposalId = arguments.string("proposalId");
                    Optional<ProposalAnswer> answer = api.awaitProposal(proposalId,
                            Duration.ofSeconds(arguments.integer("waitSeconds", (int) maximumSeconds)));
                    Map<String, Object> structured = new LinkedHashMap<>();
                    structured.put("proposalId", proposalId);
                    structured.put("answer", answer.map(Enum::name).orElse("WAITING"));
                    return Tool.Output.of(structured);
                });
    }
}
