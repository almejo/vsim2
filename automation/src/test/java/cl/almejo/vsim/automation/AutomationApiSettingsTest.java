package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationApiSettingsTest {

    private final SettingsFakeHost host = new SettingsFakeHost();
    private final AutomationApi api = new AutomationApi(host);

    @Test
    void newCircuitsSettingsAndProposalsRunOnTheEditorThread() {
        assertThat(api.newCircuit("Sumador")).isEqualTo(new DocumentId("circuit-9"));
        api.setEditorSetting(new DocumentId("circuit-9"), EditorSetting.GRID_STYLE, "LINES");
        assertThat(api.proposePreference(ProposedPreference.THEME, "DARK", "Para el video")).isEqualTo("proposal-1");

        assertThat(host.calls).containsExactly("editor:new Sumador", "editor:set circuit-9 GRID_STYLE=LINES",
                "editor:propose THEME=DARK");
    }

    @Test
    void aProposalOfAValueThePreferenceDoesNotTakeIsRefusedBeforeTheUserSeesIt() {
        assertThatThrownBy(() -> api.proposePreference(ProposedPreference.LANGUAGE, "fr", "Pour la vidéo"))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThat(host.calls).isEmpty();
    }

    @Test
    void waitingForAnAnswerReturnsItOrSaysTheUserHasNotAnsweredYet() {
        assertThat(api.awaitProposal("proposal-1", Duration.ofMillis(30))).isEmpty();

        host.answer.complete(ProposalAnswer.ACCEPTED);

        assertThat(api.awaitProposal("proposal-1", Duration.ofMillis(30))).contains(ProposalAnswer.ACCEPTED);
    }

    @Test
    void anExportRefusedByTheEditorReachesTheCallerAsItIs() {
        ExportRequest request = new ExportRequest(new DocumentId("circuit-1"), ExportRequest.Format.PNG,
                new ExportRequest.Scope.WholeCircuit(), Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                ExportRequest.PdfOptions.DEFAULT);

        assertThatThrownBy(() -> new AutomationApi(new AutomationApiTest.FakeHost(List.of())).export(request))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
        assertThat(api.export(request).fileName()).isEqualTo("circuito.png");
    }

    /** Records which settings calls ran inside {@link #onEditorThread}. */
    private static final class SettingsFakeHost implements AutomationHost {
        final List<String> calls = new ArrayList<>();
        final CompletableFuture<ProposalAnswer> answer = new CompletableFuture<>();
        private boolean onEditorThread;

        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            onEditorThread = true;
            try {
                return CompletableFuture.completedFuture(work.apply(List::of));
            } finally {
                onEditorThread = false;
            }
        }

        private String where() {
            return onEditorThread ? "editor:" : "elsewhere:";
        }

        @Override
        public DocumentId newCircuit(String name) {
            calls.add(where() + "new " + name);
            return new DocumentId("circuit-9");
        }

        @Override
        public void setEditorSetting(DocumentId document, EditorSetting setting, String value) {
            calls.add(where() + "set " + document.value() + " " + setting + "=" + value);
        }

        @Override
        public String proposePreference(ProposedPreference preference, String value, String reason) {
            calls.add(where() + "propose " + preference + "=" + value);
            return "proposal-1";
        }

        @Override
        public CompletableFuture<ProposalAnswer> proposalAnswer(String proposalId) {
            return answer;
        }

        @Override
        public CompletableFuture<ExportReceipt> export(ExportRequest request) {
            return CompletableFuture.completedFuture(new ExportReceipt(request.document(), new DocumentVersion(1),
                    request.format(), "circuito.png", "/home/VSim", "/home/VSim/circuito.png", 100, 80, 1));
        }
    }
}
