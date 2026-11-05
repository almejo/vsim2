package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuBar;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tabbed window (spec §4): groups of tabs per document, the active tab's menu bar and toolbar, the closing rules
 * and the moves of groups. {@link WindowTabs} is what a {@code CircuitWindow} holds; a frame cannot be built headless,
 * so the window's rules live there and are tested with a recording host.
 */
class WindowTabsTest {

    /** What a frame and the application would do, recorded. */
    private static final class RecordingHost implements WindowTabs.Host {
        JMenuBar menuBar;
        boolean lastHolder = true;
        boolean answerToSave = true;
        final List<OpenDocument> saveQuestions = new ArrayList<>();
        final List<OpenDocument> closedGroups = new ArrayList<>();
        final List<OpenDocument> closingGroups = new ArrayList<>();
        final List<OpenDocument> changed = new ArrayList<>();
        final List<OpenDocument> droppedOutside = new ArrayList<>();
        int emptied;
        int activeChanges;

        @Override public void showMenuBar(JMenuBar shown) { menuBar = shown; }
        @Override public boolean isMaximized() { return false; }
        @Override public boolean isLastHolder(OpenDocument document) { return lastHolder; }
        @Override public boolean confirmSave(OpenDocument document) {
            saveQuestions.add(document);
            return answerToSave;
        }
        @Override public void tabsChanged(OpenDocument document) { changed.add(document); }
        @Override public void groupClosing(OpenDocument document) { closingGroups.add(document); }
        @Override public void groupClosed(OpenDocument document) { closedGroups.add(document); }
        @Override public void emptied() { emptied++; }
        @Override public void activeTabChanged() { activeChanges++; }
        @Override public int viewNumber(OpenDocument document, int mainTabIndex) { return 0; }
        @Override public void droppedOutside(OpenDocument document, Point screenPoint) {
            droppedOutside.add(document);
        }
        @Override public List<WindowMenu.Entry> everyTab() { return List.of(); }
    }

    private final RecordingHost host = new RecordingHost();
    private final RecordingHost otherHost = new RecordingHost();
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

    private WindowTabs newTabs(RecordingHost recording) {
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
}
