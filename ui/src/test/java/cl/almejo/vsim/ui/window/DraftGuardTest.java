package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import cl.almejo.vsim.ui.window.tabs.TabStripModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window asks a tab's drafts ({@link DraftGuard}) once on every way out of the tab: closing it, its group or
 * every component tab, switching to another tab, and in the close sequence of a window or an exit; Cancelar keeps
 * everything as it was. The tab's title says the guard is on.
 */
class DraftGuardTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<OpenDocument> documents = new ArrayList<>();
    private WindowTabs tabs;
    private OpenDocument circuit;
    private EditorView main;
    private EditorView inside;
    private RecordingGuard guard;

    /** Answers what it is told, and records every reason it was asked for. */
    private static final class RecordingGuard implements DraftGuard {
        private final List<Reason> asked = new ArrayList<>();
        private Answer answer = Answer.GO_ON;

        @Override
        public Answer settle(Reason reason) {
            asked.add(reason);
            return answer;
        }

        @Override
        public Optional<String> titleNote() {
            return Optional.of(" · prueba");
        }

        @Override
        public Optional<String> discardNotice() {
            return Optional.of("Se descartó el borrador");
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        tabs = new WindowTabs(host,
                place -> new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
        circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(circuit);
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        main = tabs.addTab(circuit, Optional.empty());
        inside = tabs.addComponentTab(circuit, ExampleDefinitions.INVERTER,
                circuit.openBody(ExampleDefinitions.INVERTER, Optional.empty()), Optional.empty());
        tabs.activate(inside);
        guard = new RecordingGuard();
        inside.draftGuards().add(guard);
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    private Optional<TabStripModel.Tab> tabOf(EditorView view) {
        return tabs.model().tabs().stream().filter(tab -> tab.definitionId().equals(view.openDocument().definitionId())
                && !tab.isMain() == view.openDocument().isBody()).findFirst();
    }

    @Test
    void closingTheTabAsksOnceAndCancelarKeepsIt() {
        guard.answer = DraftGuard.Answer.CANCEL;
        tabs.closeTab(inside);

        assertThat(guard.asked).containsExactly(DraftGuard.Reason.CLOSE);
        assertThat(tabs.views()).contains(inside);

        guard.asked.clear();
        guard.answer = DraftGuard.Answer.GO_ON;
        tabs.closeTab(inside);

        assertThat(guard.asked).containsExactly(DraftGuard.Reason.CLOSE);
        assertThat(tabs.views()).doesNotContain(inside);
    }

    @Test
    void switchingAwayAsksAndCancelarStaysOnTheTab() {
        guard.answer = DraftGuard.Answer.CANCEL;
        tabs.activate(main);

        assertThat(guard.asked).containsExactly(DraftGuard.Reason.SWITCH);
        assertThat(tabs.activeView()).isSameAs(inside);

        guard.asked.clear();
        guard.answer = DraftGuard.Answer.GO_ON;
        tabs.activate(main);

        assertThat(guard.asked).containsExactly(DraftGuard.Reason.SWITCH);
        assertThat(tabs.activeView()).isSameAs(main);
    }

    @Test
    void comingBackToTheTabAsksNothing() {
        tabs.activate(main);
        guard.asked.clear();
        tabs.activate(inside);

        assertThat(guard.asked).isEmpty();
    }

    @Test
    void closingTheComponentTabsAsksAndCancelarKeepsThemAll() {
        guard.answer = DraftGuard.Answer.CANCEL;
        tabs.closeComponentTabs(circuit);

        assertThat(guard.asked).containsExactly(DraftGuard.Reason.CLOSE);
        assertThat(tabs.views()).contains(inside);
    }

    @Test
    void closingTheGroupAsksBeforeTheSaveQuestion() {
        host.lastHolder = true;
        guard.answer = DraftGuard.Answer.CANCEL;
        boolean closed = tabs.closeGroup(circuit);

        assertThat(closed).isFalse();
        assertThat(guard.asked).containsExactly(DraftGuard.Reason.CLOSE);
        assertThat(host.saveQuestions).isEmpty();

        guard.asked.clear();
        guard.answer = DraftGuard.Answer.GO_ON;
        tabs.closeGroup(circuit);

        assertThat(guard.asked).containsExactly(DraftGuard.Reason.CLOSE);
        assertThat(host.saveQuestions).containsExactly(circuit);
    }

    @Test
    void theCloseSequenceAsksTheDraftsAfterTheInlineEditsAndBeforeSaving() {
        List<String> order = new ArrayList<>();
        guard.answer = DraftGuard.Answer.CANCEL;
        boolean closes = CloseSequence.confirm(List.of(), List.of(inside.draftGuards()), () -> {
            order.add("save");
            return true;
        });

        assertThat(closes).isFalse();
        assertThat(order).isEmpty();

        guard.answer = DraftGuard.Answer.GO_ON;
        closes = CloseSequence.confirm(List.of(), List.of(inside.draftGuards()), () -> {
            order.add("save");
            return true;
        });

        assertThat(closes).isTrue();
        assertThat(order).containsExactly("save");
        assertThat(tabs.drafts()).containsExactly(main.draftGuards(), inside.draftGuards());
    }

    @Test
    void theTabsTitleSaysTheGuardIsOn() {
        assertThat(tabOf(inside).orElseThrow().title()).endsWith(" · prueba");
    }

    @Test
    void aTabWhoseDefinitionLeftSaysItsDraftWentWithIt() {
        circuit.history().undo(); // the definition

        assertThat(tabs.views()).doesNotContain(inside);
        assertThat(guard.asked).isEmpty();
        assertThat(main.statusBar().text()).contains("Se descartó el borrador");
    }
}
