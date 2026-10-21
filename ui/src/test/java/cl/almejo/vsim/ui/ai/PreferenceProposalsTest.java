package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenceProposalsTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final PreferenceProposals proposals = new PreferenceProposals(preferences);

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
        preferences.setTheme(AppPreferences.Theme.LIGHT);
    }

    @Test
    void aProposalChangesNothingUntilTheUserAccepts() {
        AtomicInteger heard = new AtomicInteger();
        proposals.addListener(heard::incrementAndGet);

        PreferenceProposal proposal = proposals.propose(ProposedPreference.THEME, "DARK", "¿Paso al tema oscuro?");
        CompletableFuture<ProposalAnswer> answer = proposals.answerOf(proposal.id());

        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.LIGHT);
        assertThat(proposals.pending()).containsExactly(proposal);
        assertThat(proposal.description()).isEqualTo("Cambiar el tema a «Oscuro»");
        assertThat(answer).isNotDone();
        assertThat(heard).hasValue(1);

        proposals.answer(proposal.id(), true);

        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.DARK);
        assertThat(answer).isCompletedWithValue(ProposalAnswer.ACCEPTED);
        assertThat(proposals.answerOf(proposal.id())).isCompletedWithValue(ProposalAnswer.ACCEPTED);
        assertThat(proposals.pending()).isEmpty();
        assertThat(heard).hasValue(2);
    }

    @Test
    void noLeavesThePreferenceAsItWas() {
        PreferenceProposal proposal = proposals.propose(ProposedPreference.LANGUAGE, "en", "For the video");

        proposals.answer(proposal.id(), false);

        assertThat(preferences.language()).isEmpty();
        assertThat(proposals.answerOf(proposal.id())).isCompletedWithValue(ProposalAnswer.DECLINED);
    }

    @Test
    void anAcceptedLanguageIsStoredForTheNextStart() {
        proposals.answer(proposals.propose(ProposedPreference.LANGUAGE, "en", "").id(), true);
        assertThat(preferences.language()).contains("en");

        proposals.answer(proposals.propose(ProposedPreference.LANGUAGE, "system", "").id(), true);
        assertThat(preferences.language()).isEqualTo(Optional.empty());
    }

    @Test
    void aValueNotOfferedIsRejected() {
        assertThatThrownBy(() -> proposals.propose(ProposedPreference.THEME, "PINK", ""))
                .isInstanceOfSatisfying(AutomationException.class, exception ->
                        assertThat(exception.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThat(proposals.pending()).isEmpty();
    }

    @Test
    void anUnknownProposalIsAnInvalidReference() {
        assertThatThrownBy(() -> proposals.answerOf("proposal-99"))
                .isInstanceOfSatisfying(AutomationException.class, exception ->
                        assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void withdrawingDeclinesWhatIsStillPending() {
        PreferenceProposal proposal = proposals.propose(ProposedPreference.THEME, "DARK", "");

        proposals.withdrawAll();

        assertThat(proposals.answerOf(proposal.id())).isCompletedWithValue(ProposalAnswer.DECLINED);
        assertThat(proposals.pending()).isEmpty();
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.LIGHT);
    }
}
