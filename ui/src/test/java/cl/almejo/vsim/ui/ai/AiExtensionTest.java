package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiExtensionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final AppPreferences saved = AppPreferences.inMemory();
    private final EditorAutomationHost host = new EditorAutomationHost(List::of);
    private final AiServerService service = new AiServerService(saved, new AutomationApi(host), new FakeAiTasks(),
            Runnable::run, Runnable::run);
    private final PreferenceProposals proposals = new PreferenceProposals(saved);
    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), saved.layered(), new ComponentLibrary(), List.of()), NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        document.close();
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
        assertThat(view.statusBar().getComponentCount()).isEqualTo(statusComponents + 1);
        assertThat(view.canvas().overlayCount()).isEqualTo(overlays + 1);

        dispose.run();

        assertThat(texts(tools)).isEqualTo(before);
        assertThat(view.shortcutActions()).doesNotContainKey(AiExtension.SHORTCUT_ID);
        assertThat(view.statusBar().getComponentCount()).isEqualTo(statusComponents);
        assertThat(view.canvas().overlayCount()).isEqualTo(overlays);
        assertThat(service.listenerCount()).isEqualTo(serviceListeners);
        assertThat(proposals.listenerCount()).isEqualTo(proposalListeners);
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }
}
