package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The settings side of the host (spec §7): new circuits, the window override and the proposals. */
class EditorAutomationHostSettingsTest {

    private final AppPreferences saved = AppPreferences.inMemory();
    private final List<OpenDocument> open = new ArrayList<>();
    private final Map<OpenDocument, AppPreferences> windowPreferences = new HashMap<>();
    private final PreferenceProposals proposals = new PreferenceProposals(saved);
    private final AtomicInteger requests = new AtomicInteger();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open), new EditorWindows() {
        @Override
        public OpenDocument openNewCircuit(String name) {
            OpenDocument created = OpenDocument.createEmpty(StandardComponentTypes.registry());
            created.document().setName(name);
            open.add(created);
            windowPreferences.put(created, saved.layered());
            return created;
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
            return Optional.ofNullable(windowPreferences.get(document)).flatMap(SettingsOverride::of);
        }
    }, proposals);

    @BeforeEach
    void spanish() {
        host.addRequestListener(requests::incrementAndGet);
    }

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    @Test
    void aNewCircuitIsAnUnsavedWindowWithItsNameAndAnId() throws Exception {
        DocumentId created = onEditor(() -> host.newCircuit("Sumador de 2 bits"));
        List<DocumentSummary> documents = host.onEditorThread(access -> access.openDocuments())
                .get(5, TimeUnit.SECONDS);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).id()).isEqualTo(created);
        assertThat(documents.get(0).name()).isEqualTo("Sumador de 2 bits");
        assertThat(documents.get(0).modified()).isFalse();
        assertThat(open.get(0).path()).isEmpty();
    }

    @Test
    void aSettingChangesOnlyTheWindowOfThatCircuit() throws Exception {
        DocumentId first = onEditor(() -> host.newCircuit("Uno"));
        onEditor(() -> host.newCircuit("Dos"));

        onEditor(() -> {
            host.setEditorSetting(first, EditorSetting.SHOW_GUIDES, "false");
            return null;
        });

        assertThat(windowPreferences.get(open.get(0)).showGuides()).isFalse();
        assertThat(windowPreferences.get(open.get(1)).showGuides()).isTrue();
        assertThat(saved.showGuides()).isTrue();
    }

    @Test
    void anUnknownCircuitOrAWrongValueIsRefused() throws Exception {
        DocumentId created = onEditor(() -> host.newCircuit("Uno"));

        assertThatThrownBy(() -> onEditor(() -> {
            host.setEditorSetting(created, EditorSetting.GRID_SPACING, "7");
            return null;
        })).hasCauseInstanceOf(AutomationException.class);
        assertThatThrownBy(() -> onEditor(() -> {
            host.setEditorSetting(new DocumentId("circuit-99"), EditorSetting.SHOW_GUIDES, "false");
            return null;
        })).cause().isInstanceOfSatisfying(AutomationException.class,
                exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void aProposalWaitsForTheUsersAnswer() throws Exception {
        String proposalId = onEditor(() -> host.proposePreference(ProposedPreference.THEME, "DARK", "Para el video"));
        CompletableFuture<ProposalAnswer> answer = onEditor(() -> host.proposalAnswer(proposalId));

        assertThat(proposals.pending()).hasSize(1);
        assertThat(answer).isNotDone();
        javax.swing.SwingUtilities.invokeAndWait(() -> proposals.answer(proposalId, true));

        assertThat(answer.get(5, TimeUnit.SECONDS)).isEqualTo(ProposalAnswer.ACCEPTED);
        assertThat(saved.theme()).isEqualTo(AppPreferences.Theme.DARK);
    }

    @Test
    void everyRequestTellsThatClaudeCodeIsConnected() throws Exception {
        host.onEditorThread(access -> access.openDocuments()).get(5, TimeUnit.SECONDS);
        host.onEditorThread(access -> access.openDocuments()).get(5, TimeUnit.SECONDS);

        assertThat(requests).hasValue(2);
    }

    @Test
    void withoutWindowsANewCircuitIsNotAllowed() {
        EditorAutomationHost bare = new EditorAutomationHost(List::of);

        assertThatThrownBy(() -> bare.onEditorThread(access -> bare.newCircuit("x")).get(5, TimeUnit.SECONDS))
                .cause().isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.NOT_ALLOWED));
    }

    private <T> T onEditor(java.util.function.Supplier<T> work) throws Exception {
        return host.onEditorThread(access -> work.get()).get(5, TimeUnit.SECONDS);
    }
}
