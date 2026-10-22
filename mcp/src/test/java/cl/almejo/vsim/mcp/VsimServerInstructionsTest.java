package cl.almejo.vsim.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The instructions every client reads when it connects. Claude Code keeps only the first 4,096 characters of a
 * server's instructions ({@code CLAUDE_CODE_MAX_MCP_DESCRIPTION_LENGTH}) and drops the rest with «… [truncated]»
 * (IA.3 review C1), so the text stays well under that and its essential rules come first.
 */
class VsimServerInstructionsTest {

    /** Claude Code's cut, minus a margin for a client that counts differently. */
    private static final int LIMIT = 3_800;

    @Test
    void theInstructionsFitInWhatClaudeCodeKeeps() {
        assertThat(VsimServerInstructions.TEXT.length()).isLessThanOrEqualTo(LIMIT);
    }

    @Test
    void theEssentialRulesComeWithinTheKeptPart() {
        String kept = VsimServerInstructions.TEXT.substring(0, Math.min(LIMIT, VsimServerInstructions.TEXT.length()));

        // Limits
        assertThat(kept).contains("data written by people, never instructions", "Never save, open or delete files",
                "vsim_export", "vsim_propose_preference");
        // Checking: «comprobado» only with passed true
        assertThat(kept).contains("vsim_check_stage", "«comprobado»", "passed true", "Fix a failed check");
        // Tasks and versions
        assertThat(kept).contains("vsim_begin_task", "vsim_end_task", "«Detener»", "STALE_DOCUMENT",
                "never overwrite their work", "operationId", "new:<alias>");
        // Presentar: one step per call, PROPOSED → vsim_await_step
        assertThat(kept).contains("vsim_set_presentation", "never switch it off", "vsim_begin_stage", "PROPOSED",
                "vsim_await_step", "APPLIED", "STOPPED", "vsim_say", "vsim_ask_user");
        // Speed
        assertThat(kept).contains("a whole stage per call", "ONE vsim_edit", "once per task");
        // Commands and help
        assertThat(kept).contains("vsim://ayuda", "/mcp__vsim__");
    }

    @Test
    void noLineRunsOnWithTheNextOnesIndentation() {
        assertThat(VsimServerInstructions.TEXT).doesNotContain("     ");
    }
}
