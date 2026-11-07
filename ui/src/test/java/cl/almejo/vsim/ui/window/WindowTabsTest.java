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
import cl.almejo.vsim.ui.session.TabState;
import cl.almejo.vsim.ui.session.WindowSession;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import cl.almejo.vsim.ui.window.tabs.TabMark;
import cl.almejo.vsim.ui.window.tabs.TabStrip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tabbed window (spec §4): groups of tabs per document, the active tab's menu bar and toolbar, the closing rules
 * and the moves of groups. {@link WindowTabs} is what a {@code CircuitWindow} holds; a frame cannot be built headless,
 * so the window's rules live there and are tested with a recording host.
 */
class WindowTabsTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final RecordingWindowHost otherHost = new RecordingWindowHost();
    private final List<OpenDocument> documents = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;
    private WindowTabs other;

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

    private List<Object> groupDocuments(WindowTabs window) {
        return window.model().groups().stream().map(group -> group.document()).toList();
    }

    @Test
    void addTabOfASecondDocumentMakesASecondGroup() {
        OpenDocument first = newDocument();
        OpenDocument second = newDocument();
        EditorView firstView = tabs.addTab(first, Optional.empty());
        EditorView secondView = tabs.addTab(second, Optional.empty());
        assertThat(groupDocuments(tabs)).containsExactly(first, second);
        assertThat(tabs.views()).containsExactly(firstView, secondView);
        assertThat(tabs.activeView()).isSameAs(secondView);
        assertThat(tabs.documents()).containsExactly(first, second);
        assertThat(host.changed).contains(first, second);
    }

    @Test
    void thePlusAndADoubleClickOnTheEmptyStripOpenANewCircuitTab() {
        host.onNewTab = () -> tabs.addTab(newDocument(), Optional.empty());
        tabs.addTab(newDocument(), Optional.empty());
        TabStrip strip = tabs.strip();
        strip.setSize(800, TabStrip.HEIGHT);
        strip.doLayout();
        int tabsBefore = tabs.model().tabs().size();
        int documentsBefore = tabs.documents().size();

        Rectangle button = strip.newTabButtonBounds().orElseThrow();
        clickOn(strip, button.x + 4, 20, 1);
        assertThat(tabs.model().tabs()).hasSize(tabsBefore + 1);
        assertThat(tabs.documents()).hasSize(documentsBefore + 1);

        strip.doLayout();
        clickOn(strip, 790, 20, 2);
        assertThat(tabs.model().tabs()).hasSize(tabsBefore + 2);
        assertThat(tabs.documents()).hasSize(documentsBefore + 2);
        assertThat(host.newTabs).isEqualTo(2);
    }

    private static void clickOn(TabStrip strip, int x, int y, int clickCount) {
        strip.dispatchEvent(new MouseEvent(strip, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y,
                x, y, clickCount, false, MouseEvent.BUTTON1));
        strip.dispatchEvent(new MouseEvent(strip, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, x, y, clickCount, false,
                MouseEvent.BUTTON1));
        strip.dispatchEvent(new MouseEvent(strip, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, x, y, clickCount, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void activatingATabSwapsTheMenuBarAndTheToolbar() {
        EditorView first = tabs.addTab(newDocument(), Optional.empty());
        EditorView second = tabs.addTab(newDocument(), Optional.empty());
        assertThat(host.menuBar).isSameAs(second.menuBar());
        assertThat(second.toolBar().getParent()).isSameAs(tabs.toolBarSlot());
        tabs.activate(first);
        assertThat(host.menuBar).isSameAs(first.menuBar());
        assertThat(first.toolBar().getParent()).isSameAs(tabs.toolBarSlot());
        assertThat(second.toolBar().getParent()).isNull();
        assertThat(first.isVisible()).isTrue();
        assertThat(second.isVisible()).isFalse();
    }

    @Test
    void activatingATabTellsItsViewItIsShown() {
        EditorView first = tabs.addTab(newDocument(), Optional.empty());
        EditorView second = tabs.addTab(newDocument(), Optional.empty());
        List<String> shownTabs = new ArrayList<>();
        first.addTabShownListener(() -> shownTabs.add("first"));
        Runnable removal = second.addTabShownListener(() -> shownTabs.add("second"));

        tabs.activate(first);
        removal.run();
        tabs.activate(second);

        assertThat(shownTabs).containsExactly("first");
    }

    @Test
    void aThemeChangeReachesTheToolbarAndMenuBarOfATabNotShown() {
        EditorView hidden = tabs.addTab(newDocument(), Optional.empty());
        tabs.addTab(newDocument(), Optional.empty());
        Object toolBarUi = hidden.toolBar().getUI();
        Object menuBarUi = hidden.menuBar().getUI();
        tabs.updateUI();
        assertThat(hidden.toolBar().getUI()).isNotSameAs(toolBarUi);
        assertThat(hidden.menuBar().getUI()).isNotSameAs(menuBarUi);
    }

    @Test
    void theViewNoLongerHoldsItsOwnToolbar() {
        EditorView view = tabs.addTab(newDocument(), Optional.empty());
        assertThat(view.toolBar().getParent()).isNotSameAs(view);
    }

    @Test
    void closingTheLastMainTabClosesTheGroupAndItsComponentTabs() {
        OpenDocument document = newDocument();
        OpenDocument kept = newDocument();
        EditorView main = tabs.addTab(document, Optional.empty());
        tabs.addComponentTab(document, "ram", document, Optional.empty());
        tabs.addComponentTab(document, "alu", document, Optional.empty());
        tabs.addTab(kept, Optional.empty());
        assertThat(tabs.viewsOf(document)).hasSize(3);

        tabs.closeTab(main);

        assertThat(host.saveQuestions).containsExactly(document);
        assertThat(tabs.viewsOf(document)).isEmpty();
        assertThat(groupDocuments(tabs)).containsExactly(kept);
        assertThat(host.closingGroups).containsExactly(document);
        assertThat(host.closedGroups).containsExactly(document);
        assertThat(host.emptied).isZero();
    }

    @Test
    void cancellingTheSaveQuestionKeepsTheGroup() {
        OpenDocument document = newDocument();
        EditorView main = tabs.addTab(document, Optional.empty());
        host.answerToSave = false;
        tabs.closeTab(main);
        assertThat(tabs.viewsOf(document)).containsExactly(main);
        assertThat(host.closedGroups).isEmpty();
    }

    @Test
    void anotherHolderOfTheDocumentMeansNoSaveQuestion() {
        OpenDocument document = newDocument();
        EditorView main = tabs.addTab(document, Optional.empty());
        tabs.addTab(newDocument(), Optional.empty());
        host.lastHolder = false;
        tabs.closeTab(main);
        assertThat(host.saveQuestions).isEmpty();
        assertThat(host.closedGroups).containsExactly(document);
    }

    @Test
    void closingAComponentTabAsksNothingAndKeepsTheGroup() {
        OpenDocument document = newDocument();
        tabs.addTab(document, Optional.empty());
        EditorView ram = tabs.addComponentTab(document, "ram", document, Optional.empty());
        tabs.closeTab(ram);
        assertThat(host.saveQuestions).isEmpty();
        assertThat(tabs.viewsOf(document)).hasSize(1);
        assertThat(host.closedGroups).isEmpty();
    }

    @Test
    void closingOneOfTwoMainTabsKeepsTheGroup() {
        OpenDocument document = newDocument();
        EditorView first = tabs.addTab(document, Optional.empty());
        tabs.newViewOfActivePlace();
        tabs.closeTab(first);
        assertThat(host.saveQuestions).isEmpty();
        assertThat(tabs.viewsOf(document)).hasSize(1);
    }

    @Test
    void closeComponentTabsLeavesTheMainTabs() {
        OpenDocument document = newDocument();
        EditorView main = tabs.addTab(document, Optional.empty());
        tabs.addComponentTab(document, "ram", document, Optional.empty());
        tabs.addComponentTab(document, "alu", document, Optional.empty());
        tabs.closeComponentTabs(document);
        assertThat(tabs.viewsOf(document)).containsExactly(main);
    }

    @Test
    void closingTheOnlyGroupClosesTheWindow() {
        OpenDocument document = newDocument();
        EditorView main = tabs.addTab(document, Optional.empty());
        tabs.closeTab(main);
        assertThat(host.emptied).isEqualTo(1);
    }

    @Test
    void ctrlTabCyclesVisibleTabsAndSkipsFolded() {
        OpenDocument first = newDocument();
        OpenDocument second = newDocument();
        EditorView firstMain = tabs.addTab(first, Optional.empty());
        tabs.addComponentTab(first, "ram", first, Optional.empty());
        EditorView secondMain = tabs.addTab(second, Optional.empty());
        tabs.foldGroup(first, true);
        tabs.activate(firstMain);
        tabs.nextTab();
        assertThat(tabs.activeView()).isSameAs(secondMain);
        tabs.nextTab();
        assertThat(tabs.activeView()).isSameAs(firstMain);
        tabs.previousTab();
        assertThat(tabs.activeView()).isSameAs(secondMain);
        assertThat(tabs.isFolded(first)).isTrue();
    }

    @Test
    void newViewOfTheActivePlaceAddsASecondMainTabNumbered() {
        OpenDocument document = newDocument();
        tabs.addTab(document, Optional.empty());
        EditorView second = tabs.newViewOfActivePlace();
        assertThat(tabs.viewsOf(document)).hasSize(2);
        assertThat(tabs.activeView()).isSameAs(second);
        assertThat(tabs.mainTabCount(document)).isEqualTo(2);
    }

    @Test
    void movingAGroupToAWindowThatHasTheDocumentMergesIt() {
        OpenDocument document = newDocument();
        EditorView targetMain = other.addTab(document, Optional.empty());
        other.addComponentTab(document, "ram", document, Optional.empty());
        tabs.addTab(document, Optional.empty());
        tabs.addComponentTab(document, "alu", document, Optional.empty());
        tabs.addTab(newDocument(), Optional.empty());

        tabs.moveGroupTo(document, other, 0);

        assertThat(tabs.viewsOf(document)).isEmpty();
        assertThat(other.viewsOf(document)).hasSize(3).startsWith(targetMain);
        assertThat(other.mainTabCount(document)).isEqualTo(1);
        assertThat(other.model().group(document).orElseThrow().componentTabs())
                .extracting(tab -> tab.definitionId().orElseThrow()).containsExactly("ram", "alu");
        assertThat(host.closedGroups).containsExactly(document);
        assertThat(host.saveQuestions).isEmpty();
        assertThat(otherHost.changed).contains(document);
    }

    @Test
    void movingAGroupToAnotherWindowKeepsItsFoldAndPlace() {
        OpenDocument moving = newDocument();
        OpenDocument staying = newDocument();
        other.addTab(staying, Optional.empty());
        tabs.addTab(moving, Optional.empty());
        tabs.addComponentTab(moving, "ram", moving, Optional.empty());
        tabs.foldGroup(moving, true);
        tabs.addTab(newDocument(), Optional.empty());

        tabs.moveGroupTo(moving, other, 0);

        assertThat(groupDocuments(other)).containsExactly(moving, staying);
        assertThat(other.isFolded(moving)).isTrue();
        assertThat(other.activeView()).isSameAs(other.viewsOf(moving).get(0));
    }

    @Test
    void movingTheLastGroupOutLeavesAnEmptyWindowThatCloses() {
        OpenDocument document = newDocument();
        tabs.addTab(document, Optional.empty());
        tabs.moveGroupTo(document, other, 0);
        assertThat(host.emptied).isEqualTo(1);
        assertThat(other.viewsOf(document)).hasSize(1);
    }

    @Test
    void aDropInTheSameStripReordersTheGroups() {
        OpenDocument first = newDocument();
        OpenDocument second = newDocument();
        tabs.addTab(first, Optional.empty());
        tabs.addTab(second, Optional.empty());
        tabs.groupDropped(new cl.almejo.vsim.ui.window.tabs.TabStrip.GroupDrop(first,
                new cl.almejo.vsim.ui.window.tabs.TabStrip.InStrip(1)));
        assertThat(groupDocuments(tabs)).containsExactly(second, first);
    }

    @Test
    void aDropOutsideAsksTheHostForANewWindow() {
        OpenDocument document = newDocument();
        tabs.addTab(document, Optional.empty());
        tabs.addTab(newDocument(), Optional.empty());
        tabs.groupDropped(new cl.almejo.vsim.ui.window.tabs.TabStrip.GroupDrop(document,
                new cl.almejo.vsim.ui.window.tabs.TabStrip.Outside(new Point(10, 10))));
        assertThat(host.droppedOutside).containsExactly(document);
    }

    @Test
    void mainTabsAreTitledByTheDocumentAndComponentTabsByTheirDefinition() {
        OpenDocument document = newDocument();
        tabs.addTab(document, Optional.empty());
        tabs.addComponentTab(document, "missing", document, Optional.empty());
        assertThat(tabs.model().visibleTabs()).extracting(tab -> tab.title())
                .containsExactly("Sin título", "missing");
    }

    @Test
    void twoCircuitsWithTheSameNameAreToldApartOnTheirTabs() {
        OpenDocument first = newDocument();
        OpenDocument second = newDocument();
        tabs.addTab(first, Optional.empty());
        tabs.addTab(second, Optional.empty());

        assertThat(tabs.model().visibleTabs()).extracting(tab -> tab.title())
                .containsExactly("Sin título", "Sin título 2");
    }

    // --- the session (spec §4.7) ---

    private static final ViewState MAIN_VIEW = new ViewState(1.5, 10, -4);
    private static final ViewState INSIDE_VIEW = new ViewState(3, 20, 40);
    private static final ViewState OTHER_VIEW = new ViewState(0.5, -30, 0);
    private static final WindowState BOUNDS = new WindowState(10, 20, 1280, 820, false, MAIN_VIEW);

    @TempDir
    Path folder;

    /** A saved circuit using the inverter twice (instances {@code first} and {@code second}). */
    private OpenDocument savedWithInverters(String name) throws Exception {
        OpenDocument document = newDocument();
        document.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());
        place(document, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 20, 0, Map.of());
        document.save(folder.resolve(name));
        return document;
    }

    private OpenDocument saved(String name) throws Exception {
        OpenDocument document = newDocument();
        document.save(folder.resolve(name));
        return document;
    }

    private static String instance(OpenDocument document, int index) {
        List<Component> uses = document.document().components().stream()
                .filter(component -> component.typeId()
                        .equals(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER)))
                .toList();
        return uses.get(index).id().value();
    }

    private static Function<Path, Optional<OpenDocument>> byFile(OpenDocument... opened) {
        return path -> Arrays.stream(opened)
                .filter(document -> document.path().filter(path::equals).isPresent()).findFirst();
    }

    @Test
    void theSessionOfAWindowHasItsTabsGroupsFoldsAndContextsWithoutUntitledOnes() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument adder = saved("sumador.vsim");
        String second = instance(counter, 1);
        tabs.addTab(counter, Optional.of(MAIN_VIEW));
        tabs.addComponentTab(counter, ExampleDefinitions.INVERTER,
                counter.openBody(ExampleDefinitions.INVERTER, Optional.of(second)), Optional.of(INSIDE_VIEW));
        tabs.addTab(newDocument(), Optional.of(OTHER_VIEW));
        EditorView adderView = tabs.addTab(adder, Optional.of(OTHER_VIEW));
        tabs.foldGroup(counter, true);
        tabs.activate(adderView);

        Path counterFile = counter.path().orElseThrow();
        assertThat(tabs.session(BOUNDS)).contains(new WindowSession(BOUNDS, List.of(
                TabState.main(counterFile, MAIN_VIEW),
                TabState.component(counterFile, ExampleDefinitions.INVERTER, "Inversor", Optional.of(second),
                        INSIDE_VIEW),
                TabState.main(adder.path().orElseThrow(), OTHER_VIEW)), 2, List.of(counterFile), java.util.Set.of(),
                Optional.of(InspectorState.initial())));
    }

    @Test
    void aWindowOfUntitledCircuitsHasNoSession() {
        tabs.addTab(newDocument(), Optional.empty());
        assertThat(tabs.session(BOUNDS)).isEmpty();
    }

    @Test
    void restoringAWindowBringsBackItsGroupsTheComponentTabTheActiveTabAndTheFolds() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        OpenDocument adder = saved("sumador.vsim");
        String second = instance(counter, 1);
        Path counterFile = counter.path().orElseThrow();
        WindowSession session = new WindowSession(BOUNDS, List.of(
                TabState.main(counterFile, MAIN_VIEW),
                TabState.component(counterFile, ExampleDefinitions.INVERTER, "Inversor", Optional.of(second),
                        INSIDE_VIEW),
                TabState.main(adder.path().orElseThrow(), OTHER_VIEW)), 1, List.of(adder.path().orElseThrow()));

        List<TabState> skipped = tabs.restore(session, byFile(counter, adder));

        assertThat(skipped).isEmpty();
        assertThat(tabs.documents()).containsExactly(counter, adder);
        assertThat(tabs.viewsOf(counter)).hasSize(2);
        OpenDocument place = tabs.activeView().openDocument();
        assertThat(place.isBody()).isTrue();
        assertThat(place.parent()).containsSame(counter);
        assertThat(place.definitionId()).contains(ExampleDefinitions.INVERTER);
        assertThat(place.contextPath()).contains(second);
        assertThat(tabs.isFolded(adder)).isTrue();
        assertThat(tabs.isFolded(counter)).isFalse();
        assertThat(tabs.session(BOUNDS).orElseThrow().tabs()).isEqualTo(session.tabs());
        assertThat(tabs.session(BOUNDS).orElseThrow().foldedDocuments()).isEqualTo(session.foldedDocuments());
        assertThat(tabs.session(BOUNDS).orElseThrow().activeTab()).isEqualTo(session.activeTab());
        assertThat(tabs.session(BOUNDS).orElseThrow().inspector()).contains(InspectorState.initial());
    }

    @Test
    void skipsATabWhoseDefinitionIsGone() throws Exception {
        OpenDocument counter = saved("contador.vsim");
        Path counterFile = counter.path().orElseThrow();
        TabState gone = TabState.component(counterFile, "ram", "Ram", Optional.of("c5"), INSIDE_VIEW);
        WindowSession session = new WindowSession(BOUNDS, List.of(TabState.main(counterFile, MAIN_VIEW), gone),
                1, List.of());

        List<TabState> skipped = tabs.restore(session, byFile(counter));

        assertThat(skipped).containsExactly(gone);
        assertThat(tabs.views()).hasSize(1);
        assertThat(tabs.activeView().openDocument()).isSameAs(counter);
    }

    @Test
    void aContextThatIsGoneFallsBackToTheFirstUse() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        Path counterFile = counter.path().orElseThrow();
        WindowSession session = new WindowSession(BOUNDS, List.of(TabState.main(counterFile, MAIN_VIEW),
                TabState.component(counterFile, ExampleDefinitions.INVERTER, "Inversor", Optional.of("c999"),
                        INSIDE_VIEW)), 1, List.of());

        tabs.restore(session, byFile(counter));

        assertThat(tabs.activeView().openDocument().contextPath()).contains(instance(counter, 0));
    }

    @Test
    void aComponentTabWhoseMainTabWasNotSavedStillGetsItsGroup() throws Exception {
        OpenDocument counter = savedWithInverters("contador.vsim");
        Path counterFile = counter.path().orElseThrow();
        WindowSession session = new WindowSession(BOUNDS, List.of(TabState.component(counterFile,
                ExampleDefinitions.INVERTER, "Inversor", Optional.empty(), INSIDE_VIEW)), 0, List.of());

        tabs.restore(session, byFile(counter));

        assertThat(tabs.viewsOf(counter)).hasSize(2);
        assertThat(tabs.activeView().openDocument().isBody()).isTrue();
        assertThat(tabs.activeView().openDocument().contextPath()).isEmpty();
    }

    @Test
    void tabsOfAFileThatDidNotOpenAreLeftOut() throws Exception {
        OpenDocument adder = saved("sumador.vsim");
        WindowSession session = new WindowSession(BOUNDS, List.of(
                TabState.main(folder.resolve("roto.vsim"), MAIN_VIEW),
                TabState.main(adder.path().orElseThrow(), OTHER_VIEW)), 0, List.of(folder.resolve("roto.vsim")));

        assertThat(tabs.restore(session, byFile(adder))).isEmpty();

        assertThat(tabs.documents()).containsExactly(adder);
        assertThat(tabs.activeView().openDocument()).isSameAs(adder);
    }

    // --- the left column's accordion, per window (spec §5.3) ---

    @Test
    void foldedPanelsAreSharedByTheTabsOfAWindowAndSavedInItsSession() throws Exception {
        OpenDocument counter = saved("contador.vsim");
        EditorView first = tabs.addTab(counter, Optional.of(MAIN_VIEW));
        EditorView second = tabs.addTab(counter, Optional.of(MAIN_VIEW));

        first.leftColumn().setFolded(LeftColumn.Section.PALETTE, true);

        assertThat(second.leftColumn().isFolded(LeftColumn.Section.PALETTE)).isTrue();
        EditorView third = tabs.addTab(counter, Optional.of(MAIN_VIEW));
        assertThat(third.leftColumn().isFolded(LeftColumn.Section.PALETTE)).isTrue();
        assertThat(tabs.session(BOUNDS).orElseThrow().foldedPanels()).containsExactly("palette");
        assertThat(other.views()).isEmpty();
    }

    @Test
    void theRightPanelIsSharedByTheTabsOfAWindowAndTwoWindowsAreIndependent() {
        OpenDocument document = newDocument();
        EditorView first = tabs.addTab(document, Optional.empty());
        EditorView second = tabs.addTab(document, Optional.empty());
        EditorView elsewhere = other.addTab(document, Optional.empty());

        first.inspector().restore(new InspectorState(true, InspectorPanel.PROBLEMS, 350));
        first.inspector().close();

        InspectorState expected = new InspectorState(false, InspectorPanel.PROBLEMS, 350);
        assertThat(second.inspector().state()).isEqualTo(expected);
        assertThat(tabs.addTab(document, Optional.empty()).inspector().state()).as("a new tab of the window")
                .isEqualTo(expected);
        assertThat(elsewhere.inspector().state()).isEqualTo(InspectorState.initial());
    }

    @Test
    void theRightPanelIsSavedInTheSessionAndRestoredPerWindowAndAnOldSessionOpensAsInitial() throws Exception {
        OpenDocument counter = saved("contador.vsim");
        Path counterFile = counter.path().orElseThrow();
        EditorView view = tabs.addTab(counter, Optional.of(MAIN_VIEW));
        view.inspector().restore(new InspectorState(false, InspectorPanel.NOTES, 333));

        assertThat(tabs.session(BOUNDS).orElseThrow().inspector())
                .contains(new InspectorState(false, InspectorPanel.NOTES, 333));

        WindowSession saved = tabs.session(BOUNDS).orElseThrow();
        other.restore(saved, byFile(counter));
        assertThat(other.views()).allSatisfy(restored ->
                assertThat(restored.inspector().state()).isEqualTo(new InspectorState(false, InspectorPanel.NOTES, 333)));

        tabs.restore(new WindowSession(BOUNDS, List.of(TabState.main(counterFile, MAIN_VIEW)), 0, List.of()),
                byFile(counter));
        assertThat(tabs.activeView().inspector().state()).isEqualTo(InspectorState.initial());
    }

    @Test
    void restoringAWindowFoldsItsPanelsAndAnOldSessionKeepsBothOpen() throws Exception {
        OpenDocument counter = saved("contador.vsim");
        Path counterFile = counter.path().orElseThrow();

        tabs.restore(new WindowSession(BOUNDS, List.of(TabState.main(counterFile, MAIN_VIEW)), 0, List.of(),
                java.util.Set.of("circuit")), byFile(counter));
        other.restore(new WindowSession(BOUNDS, List.of(TabState.main(counterFile, MAIN_VIEW)), 0, List.of()),
                byFile(counter));

        assertThat(tabs.activeView().leftColumn().folded()).containsExactly(LeftColumn.Section.CIRCUIT);
        assertThat(other.activeView().leftColumn().folded()).isEmpty();
    }

    // --- the AI's mark (fix round D, item 5n) ---

    /** Marks by place («main», or a definition and its piece), with the visits and listeners recorded. */
    private static final class FakePlaceMarks implements PlaceMarks {
        final Map<String, TabMark> marks = new HashMap<>();
        final List<String> visits = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();

        static String key(Optional<String> definitionId, Optional<String> contextPath) {
            return definitionId.orElse("main") + "@" + contextPath.orElse("-");
        }

        @Override
        public Optional<TabMark> markOf(OpenDocument document, Optional<String> definitionId,
                                        Optional<String> contextPath) {
            return Optional.ofNullable(marks.get(key(definitionId, contextPath)));
        }

        @Override
        public void visited(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath) {
            visits.add(key(definitionId, contextPath));
        }

        @Override
        public Runnable addListener(Runnable listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }
    }

    private static final TabMark AI_WORKING = new TabMark(TabMark.Phase.WORKING, Optional.of("Armar"));

    @Test
    void theAiMarkOfAPlaceReachesItsTabInTheTabListAndGoesWithTheWindow() throws Exception {
        FakePlaceMarks marks = new FakePlaceMarks();
        RecordingWindowHost marked = new RecordingWindowHost();
        marked.placeMarks = marks;
        WindowTabs window = newTabs(marked);
        OpenDocument counter = savedWithInverters("contador.vsim");
        String second = instance(counter, 1);
        window.addTab(counter, Optional.empty());
        window.addComponentTab(counter, ExampleDefinitions.INVERTER,
                counter.openBody(ExampleDefinitions.INVERTER, Optional.of(second)), Optional.empty());
        marks.marks.put(FakePlaceMarks.key(Optional.of(ExampleDefinitions.INVERTER), Optional.of(second)),
                AI_WORKING);

        assertThat(window.menuEntries(true, () -> { })).extracting(WindowMenu.Entry::mark)
                .containsExactly(Optional.empty(), Optional.of(AI_WORKING));
        assertThat(marks.listeners).as("the strip hears the marks").hasSize(1);

        window.disposeAll();
        assertThat(marks.listeners).isEmpty();
    }

    @Test
    void bringingATabToTheFrontTellsTheMarksThatPlaceWasVisited() throws Exception {
        FakePlaceMarks marks = new FakePlaceMarks();
        RecordingWindowHost marked = new RecordingWindowHost();
        marked.placeMarks = marks;
        WindowTabs window = newTabs(marked);
        try {
            OpenDocument counter = savedWithInverters("contador.vsim");
            String first = instance(counter, 0);
            EditorView main = window.addTab(counter, Optional.empty());
            window.addComponentTab(counter, ExampleDefinitions.INVERTER,
                    counter.openBody(ExampleDefinitions.INVERTER, Optional.of(first)), Optional.empty());
            window.activate(main);

            assertThat(marks.visits).containsExactly("main@-", ExampleDefinitions.INVERTER + "@" + first, "main@-");
        } finally {
            window.disposeAll();
        }
    }
}
