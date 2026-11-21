package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.closed.ClosedGroup;
import cl.almejo.vsim.ui.window.closed.ClosedTab;
import cl.almejo.vsim.ui.window.closed.ClosedTabState;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the tabs tell the window when the user closes something (card «ctrl-t»): a tab, a whole group or the window,
 * with what «Reabrir pestaña cerrada» needs to bring it back; and nothing for the closes the user did not make.
 */
class WindowTabsClosedTest {

    private static final String INVERTER = ExampleDefinitions.INVERTER;
    private static final ViewState MAIN_VIEW = new ViewState(1.5, 40, -20);
    private static final ViewState INSIDE_VIEW = new ViewState(2.0, 10, 5);

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;
    private WindowTabs other;

    @TempDir
    Path folder;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        tabs = newTabs(host);
        other = newTabs(otherHost);
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        other.disposeAll();
        documents.forEach(OpenDocument::close);
    }

    private WindowTabs newTabs(RecordingWindowHost recording) {
        return new WindowTabs(recording,
                place -> new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW),
                new SplitLayoutStore(preferences), () -> false);
    }

    private OpenDocument newDocument() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        return document;
    }

    /** A saved circuit with the inverter placed twice. */
    private OpenDocument savedWithInverters(String name) throws Exception {
        OpenDocument document = newDocument();
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 0, 0, Map.of());
        place(document, SubcircuitTypes.typeIdOf(INVERTER), 20, 0, Map.of());
        document.save(folder.resolve(name));
        return document;
    }

    private static String instance(OpenDocument document, int index) {
        List<Component> uses = document.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(INVERTER)))
                .toList();
        return uses.get(index).id().value();
    }

    private EditorView componentTab(WindowTabs window, OpenDocument document, int instance) {
        return window.addComponentTab(document, INVERTER,
                document.openBody(INVERTER, Optional.of(instance(document, instance))), Optional.of(INSIDE_VIEW));
    }

    @Test
    void closingAComponentTabRecordsItsPlaceInTheGroupItsViewAndItsInstance() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        EditorView first = componentTab(tabs, counter, 0);
        componentTab(tabs, counter, 1);

        tabs.closeTab(first);

        assertThat(host.closedByUser).hasSize(1);
        ClosedTab closed = (ClosedTab) host.closedByUser.getFirst();
        assertThat(closed.document().openDocument()).containsSame(counter);
        assertThat(closed.indexInGroup()).isEqualTo(1);
        assertThat(closed.tab()).isEqualTo(ClosedTabState.component(INVERTER, "Inversor",
                Optional.of(instance(counter, 0)), INSIDE_VIEW));
        assertThat(closed.window().window()).containsSame(tabs);
    }

    @Test
    void closingOneOfTwoMainTabsRecordsThatTab() {
        OpenDocument document = newDocument();
        tabs.addTab(document, Optional.of(MAIN_VIEW));
        EditorView second = tabs.addTab(document, Optional.of(INSIDE_VIEW));

        tabs.closeTab(second);

        ClosedTab closed = (ClosedTab) host.closedByUser.getFirst();
        assertThat(closed.tab()).isEqualTo(ClosedTabState.main(INSIDE_VIEW));
        assertThat(closed.indexInGroup()).isEqualTo(1);
    }

    @Test
    void closingTheComponentsOfACircuitRecordsEachSoTheyComeBackInOrder() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        componentTab(tabs, counter, 0);
        componentTab(tabs, counter, 1);

        tabs.closeComponentTabs(counter);

        assertThat(host.closedByUser).hasSize(2);
        assertThat(host.closedByUser).allSatisfy(closed ->
                assertThat(((ClosedTab) closed).indexInGroup()).isEqualTo(1));
        assertThat(((ClosedTab) host.closedByUser.getLast()).tab().contextPath()).contains(instance(counter, 1));
    }

    @Test
    void closingAGroupRecordsEveryTabTheActiveOneTheFoldAndItsPlaceInTheStrip() throws Exception {
        OpenDocument first = newDocument();
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument last = newDocument();
        tabs.addTab(first, Optional.empty());
        EditorView main = tabs.addTab(counter, Optional.of(MAIN_VIEW));
        componentTab(tabs, counter, 0);
        EditorView second = componentTab(tabs, counter, 1);
        tabs.addTab(last, Optional.empty());
        tabs.activate(second);

        assertThat(tabs.closeGroup(counter)).isTrue();

        ClosedGroup closed = (ClosedGroup) host.closedByUser.getFirst();
        assertThat(closed.document().openDocument()).containsSame(counter);
        assertThat(closed.tabs()).containsExactly(ClosedTabState.main(MAIN_VIEW),
                ClosedTabState.component(INVERTER, "Inversor", Optional.of(instance(counter, 0)), INSIDE_VIEW),
                ClosedTabState.component(INVERTER, "Inversor", Optional.of(instance(counter, 1)), INSIDE_VIEW));
        assertThat(closed.activeTab()).isEqualTo(2);
        assertThat(closed.folded()).isFalse();
        assertThat(closed.groupIndex()).isEqualTo(1);
        assertThat(closed.window().window()).containsSame(tabs);
        assertThat(main.openDocument()).isSameAs(counter);
    }

    @Test
    void aFoldedGroupIsRecordedFolded() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(newDocument(), Optional.empty());
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        componentTab(tabs, counter, 0);
        tabs.foldGroup(counter, true);

        tabs.closeGroup(counter);

        ClosedGroup closed = (ClosedGroup) host.closedByUser.getFirst();
        assertThat(closed.folded()).isTrue();
        assertThat(closed.activeTab()).isZero();
    }

    @Test
    void cancellingTheSaveQuestionRecordsNothing() {
        OpenDocument document = newDocument();
        place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
        tabs.addTab(newDocument(), Optional.empty());
        tabs.addTab(document, Optional.empty());
        host.answerToSave = false;

        tabs.closeGroup(document);

        assertThat(host.closedByUser).isEmpty();
    }

    @Test
    void closingTheOnlyGroupClosesTheWindowAsTheUsersClose() {
        tabs.addTab(newDocument(), Optional.empty());

        tabs.closeTab(tabs.activeView());

        assertThat(host.windowsClosedByUser).isEqualTo(1);
        assertThat(host.emptied).isEqualTo(1);
        assertThat(host.closedByUser).isEmpty();
    }

    @Test
    void movingAGroupToAnotherWindowRecordsNothing() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(newDocument(), Optional.empty());
        tabs.addTab(counter, Optional.empty());
        componentTab(tabs, counter, 0);

        tabs.moveGroupTo(counter, other, 0);

        assertThat(host.closedByUser).isEmpty();
        assertThat(host.windowsClosedByUser).isZero();
        assertThat(otherHost.closedByUser).isEmpty();
    }

    @Test
    void aComponentThatLeavesTheCircuitClosesItsTabsWithoutRecordingThem() {
        OpenDocument document = newDocument();
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        tabs.addTab(document, Optional.empty());
        tabs.enter(document, INVERTER, Optional.empty(), Optional.empty());

        document.history().undo(); // the definition

        assertThat(tabs.viewsOf(document)).hasSize(1);
        assertThat(host.closedByUser).isEmpty();
    }

    @Test
    void theClosedGroupsOfAWindowIncludeItsUntitledCircuits() throws Exception {
        OpenDocument untitled = newDocument();
        OpenDocument counter = savedWithInverters("contador.vsim");
        tabs.addTab(untitled, Optional.of(MAIN_VIEW));
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        EditorView inside = componentTab(tabs, counter, 1);
        tabs.activate(inside);

        List<ClosedGroup> groups = tabs.closedGroups();

        assertThat(groups).extracting(group -> group.document().openDocument().orElseThrow())
                .containsExactly(untitled, counter);
        assertThat(groups).extracting(ClosedGroup::groupIndex).containsExactly(0, 1);
        assertThat(groups.get(1).tabs()).hasSize(2);
        assertThat(groups.get(1).activeTab()).isEqualTo(1);
        assertThat(tabs.activeGroupIndex()).isEqualTo(1);
    }
}
