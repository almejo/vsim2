package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.ui.app.AppPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The AI's preference proposals of this VSim (spec §7): the user preferences change only through «Aceptar». One per
 * application, like the server; every window's panel shows the pending ones. EDT only; the answers complete futures
 * the automation layer waits for off the EDT.
 */
public final class PreferenceProposals {

    private static final String ID_PREFIX = "proposal-";
    /** At most this many proposals wait for the user at once (review M11: a looping AI cannot fill the panel). */
    static final int MAXIMUM_PENDING = 5;
    /** How many answered proposals are remembered for a late {@link #answerOf}. */
    private static final int REMEMBERED_ANSWERS = 50;

    private final AppPreferences preferences;
    private final Map<String, PreferenceProposal> pending = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<ProposalAnswer>> answers = new HashMap<>();
    private final List<String> answeredOrder = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long lastId;

    public PreferenceProposals(AppPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** Shows a new proposal; {@link AutomationError#INVALID_PARAMETER} for a value the preference does not offer. */
    public PreferenceProposal propose(ProposedPreference preference, String value, String reason) {
        Objects.requireNonNull(preference, "preference");
        if (value == null || !preference.allowedValues().contains(value)) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER,
                    "«" + value + "» is not a value of " + preference + "; use one of " + preference.allowedValues(),
                    Map.of("allowedValues", preference.allowedValues()));
        }
        if (pending.size() >= MAXIMUM_PENDING) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "The user has " + pending.size()
                    + " proposals waiting already; wait for their answers (vsim_await_proposal) before proposing "
                    + "more", Map.of("maximum", MAXIMUM_PENDING));
        }
        PreferenceProposal proposal = new PreferenceProposal(ID_PREFIX + ++lastId, preference, value,
                reason == null ? "" : reason);
        pending.put(proposal.id(), proposal);
        answers.put(proposal.id(), new CompletableFuture<>());
        notifyListeners();
        return proposal;
    }

    /** The proposals waiting for an answer, oldest first. */
    public List<PreferenceProposal> pending() {
        return List.copyOf(pending.values());
    }

    /** The answer of {@code proposalId}, done once answered; {@link AutomationError#INVALID_REFERENCE} if unknown. */
    public CompletableFuture<ProposalAnswer> answerOf(String proposalId) {
        CompletableFuture<ProposalAnswer> answer = answers.get(proposalId);
        if (answer == null) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE,
                    "There is no proposal «" + proposalId + "»; propose it again");
        }
        return answer;
    }

    /** «Aceptar» ({@code accepted}) changes the preference; «No» leaves it. A proposal already answered is ignored. */
    public void answer(String proposalId, boolean accepted) {
        PreferenceProposal proposal = pending.remove(proposalId);
        if (proposal == null) {
            return;
        }
        if (accepted) {
            apply(proposal);
        }
        finish(proposal, accepted ? ProposalAnswer.ACCEPTED : ProposalAnswer.DECLINED);
        notifyListeners();
    }

    /** The AI was turned off: every pending proposal is declined. */
    public void withdrawAll() {
        if (pending.isEmpty()) {
            return;
        }
        List<PreferenceProposal> withdrawn = pending();
        pending.clear();
        withdrawn.forEach(proposal -> finish(proposal, ProposalAnswer.DECLINED));
        notifyListeners();
    }

    /** {@code listener} runs whenever a proposal arrives or is answered, until the returned runnable takes it back. */
    public Runnable addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    int listenerCount() {
        return listeners.size();
    }

    private void apply(PreferenceProposal proposal) {
        switch (proposal.preference()) {
            case THEME -> preferences.setTheme(AppPreferences.Theme.valueOf(proposal.value()));
            case LANGUAGE -> preferences.setLanguage("system".equals(proposal.value()) ? Optional.empty()
                    : Optional.of(proposal.value()));
        }
    }

    private void finish(PreferenceProposal proposal, ProposalAnswer answer) {
        answers.get(proposal.id()).complete(answer);
        answeredOrder.add(proposal.id());
        while (answeredOrder.size() > REMEMBERED_ANSWERS) {
            answers.remove(answeredOrder.remove(0));
        }
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
