package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class AiExtensionTest {

    private final AppPreferences saved = AppPreferences.inMemory();
    private final EditorAutomationHost host = new EditorAutomationHost(List::of);
    private final AiServerService service = new AiServerService(saved, new AutomationApi(host), new FakeAiTasks(),
            Runnable::run, Runnable::run);
    private final PreferenceProposals proposals = new PreferenceProposals(saved);
    private OpenDocument document;
    private EditorFixture fixture;
    private EditorView view;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(saved.layered());
        document = fixture.openDocument();
        view = fixture.view();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void itAddsTheAiPanelToHerramientasAndTakesEverythingBack() {
        JMenu tools = view.menu(MenuId.TOOLS);
        List<String> before = texts(tools);
        int statusComponents = view.statusBar().getComponentCount();
        int overlays = view.canvas().overlayCount();
        int serviceListeners = service.listenerCount();
        int proposalListeners = proposals.listenerCount();

        Runnable dispose = new AiExtension(service, host::documentId, proposals).install(view);

        assertThat(texts(tools)).endsWith("---", "IA (MCP)…");
        assertThat(view.shortcutActions()).containsKey(AiExtension.SHORTCUT_ID);
        assertThat(view.statusBar().getComponentCount()).as("the strip and the indicator")
                .isEqualTo(statusComponents + 2);
        assertThat(view.canvas().overlayCount()).isEqualTo(overlays + 1);

        dispose.run();

        assertThat(texts(tools)).isEqualTo(before);
        assertThat(view.shortcutActions()).doesNotContainKey(AiExtension.SHORTCUT_ID);
        assertThat(view.statusBar().getComponentCount()).isEqualTo(statusComponents);
        assertThat(view.canvas().overlayCount()).isEqualTo(overlays);
        assertThat(service.listenerCount()).isEqualTo(serviceListeners);
        assertThat(proposals.listenerCount()).isEqualTo(proposalListeners);
    }

    @Test
    void everyWindowsStatusBarSaysTheAiStateUntilDisposed() {
        Runnable dispose = new AiExtension(service, host::documentId, proposals).install(view);

        List<AiStatusIndicator> indicators = indicators();
        assertThat(indicators).hasSize(1);
        assertThat(indicators.getFirst().getText()).isEqualTo("IA: apagada");
        assertThat(view.statusBar().text()).as("the bar's own texts stay as they were").doesNotContain("IA:");

        dispose.run();

        assertThat(indicators()).isEmpty();
    }

    private List<AiStatusIndicator> indicators() {
        List<AiStatusIndicator> indicators = new ArrayList<>();
        for (java.awt.Component child : view.statusBar().getComponents()) {
            if (child instanceof AiStatusIndicator indicator) {
                indicators.add(indicator);
            }
        }
        return indicators;
    }
}
