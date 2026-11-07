package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.WindowMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Which windows hold a tab of which document (spec §10.2), with plain strings as windows. */
class DocumentWindowsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<OpenDocument> opened = new ArrayList<>();
    private final DocumentWindows<String> windows = new DocumentWindows<>();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void closeAll() {
        opened.forEach(OpenDocument::close);
    }

    private OpenDocument newDocument() {
        OpenDocument document = OpenDocument.createEmpty(registry);
        opened.add(document);
        return document;
    }

    private OpenDocument savedDocument(Path file) throws Exception {
        OpenDocument document = newDocument();
        document.save(file);
        return document;
    }

    @Test
    void groupsWindowsByDocumentInOpeningOrder() {
        OpenDocument first = newDocument();
        OpenDocument second = newDocument();
        windows.add(first, "one");
        windows.add(second, "one");
        windows.add(first, "two");
        assertThat(windows.documents()).containsExactly(first, second);
        assertThat(windows.windowsOf(first)).containsExactly("one", "two");
        assertThat(windows.documentsOf("one")).containsExactly(first, second);
        assertThat(windows.all()).containsExactly("one", "two");
    }

    @Test
    void addIsIdempotentForAWindowThatAlreadyHoldsTheDocument() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        windows.add(document, "one");
        assertThat(windows.windowsOf(document)).containsExactly("one");
    }

    @Test
    void isLastHolder() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        assertThat(windows.isLastHolder(document, "one")).isTrue();
        windows.add(document, "two");
        assertThat(windows.isLastHolder(document, "one")).isFalse();
        assertThat(windows.isLastHolder(document, "ghost")).isFalse();
        assertThat(windows.isLastHolder(newDocument(), "one")).isTrue();
    }

    @Test
    void removeReturnsTrueWhenNoWindowHoldsIt() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        windows.add(document, "two");
        assertThat(windows.remove(document, "one")).isFalse();
        assertThat(windows.documents()).containsExactly(document);
        assertThat(windows.remove(document, "two")).isTrue();
        assertThat(windows.isEmpty()).isTrue();
        assertThat(windows.windowsOf(document)).isEmpty();
        assertThat(windows.remove(document, "ghost")).isFalse();
    }

    @Test
    void removeWindowHandsBackTheDocumentsItWasTheLastHolderOf() {
        OpenDocument shared = newDocument();
        OpenDocument alone = newDocument();
        windows.add(shared, "one");
        windows.add(alone, "one");
        windows.add(shared, "two");
        assertThat(windows.removeWindow("one")).containsExactly(alone);
        assertThat(windows.all()).containsExactly("two");
        assertThat(windows.removeWindow("ghost")).isEmpty();
    }

    @Test
    void viewNumberCountsMainTabsAcrossWindows() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        windows.add(document, "two");
        Map<String, Integer> mainTabs = Map.of("one", 2, "two", 1);
        assertThat(windows.viewNumber(document, "one", 0, (window, held) -> mainTabs.get(window))).isEqualTo(1);
        assertThat(windows.viewNumber(document, "one", 1, (window, held) -> mainTabs.get(window))).isEqualTo(2);
        assertThat(windows.viewNumber(document, "two", 0, (window, held) -> mainTabs.get(window))).isEqualTo(3);
        assertThat(windows.viewNumber(document, "one", 0, (window, held) -> window.equals("one") ? 1 : 0))
                .as("a lone main tab carries no number").isZero();
    }

    @Test
    void menuEntriesGroupByWindow() {
        windows.add(newDocument(), "one");
        windows.add(newDocument(), "two");
        List<String> focused = new ArrayList<>();
        List<WindowMenu.Entry> entries = windows.menuEntries(window -> List.of(
                new WindowMenu.Entry(window + "-a", false, () -> focused.add(window)),
                new WindowMenu.Entry(window + "-b", true, () -> focused.add(window))));
        assertThat(entries).extracting(WindowMenu.Entry::title)
                .containsExactly("Ventana 1", "one-a", "one-b", "Ventana 2", "two-a", "two-b");
        assertThat(entries).extracting(WindowMenu.Entry::header)
                .containsExactly(true, false, false, true, false, false);
        entries.get(4).focus().run();
        assertThat(focused).containsExactly("two");
    }

    @Test
    void aSingleWindowListsItsTabsWithoutAHeader() {
        windows.add(newDocument(), "one");
        List<WindowMenu.Entry> entries = windows.menuEntries(window -> List.of(
                new WindowMenu.Entry("contador", true, () -> { })));
        assertThat(entries).extracting(WindowMenu.Entry::title).containsExactly("contador");
    }

    @Test
    void changeListenersHearEveryAddAndRemove() {
        OpenDocument document = newDocument();
        List<Integer> holders = new ArrayList<>();
        Runnable listener = () -> holders.add(windows.windowsOf(document).size());
        windows.addChangeListener(listener);
        assertThat(windows.changeListenerCount()).isEqualTo(1);
        windows.add(document, "one");
        windows.add(document, "one");
        windows.add(document, "two");
        windows.remove(document, "ghost");
        windows.remove(document, "one");
        assertThat(holders).containsExactly(1, 1, 2, 1);
        windows.removeChangeListener(listener);
        windows.removeWindow("two");
        assertThat(holders).hasSize(4);
        assertThat(windows.changeListenerCount()).isZero();
    }

    @Test
    void firstShowingFindsTheFileUnderAnotherSpelling(@TempDir Path directory) throws Exception {
        OpenDocument saved = savedDocument(directory.resolve("sumador.vsim"));
        windows.add(newDocument(), "untitled");
        windows.add(saved, "saved-1");
        windows.add(saved, "saved-2");
        Files.createDirectory(directory.resolve("sub"));
        assertThat(windows.firstShowing(directory.resolve("sub/../sumador.vsim"))).contains("saved-1");
        assertThat(windows.firstShowing(directory.resolve("otro.vsim"))).isEmpty();
    }

    @Test
    void openingAFileFromAWindowAddsAGroupThere(@TempDir Path directory) {
        windows.add(newDocument(), "one");
        assertThat(windows.targetFor(directory.resolve("nuevo.vsim"), Optional.of("one")))
                .isEqualTo(new OpeningTarget.NewGroup<>("one"));
    }

    @Test
    void openingAnAlreadyOpenFileFocusesItsMainTab(@TempDir Path directory) throws Exception {
        OpenDocument saved = savedDocument(directory.resolve("sumador.vsim"));
        windows.add(newDocument(), "one");
        windows.add(saved, "two");
        assertThat(windows.targetFor(directory.resolve("sumador.vsim"), Optional.of("one")))
                .isEqualTo(new OpeningTarget.FocusTab<>("two", saved));
        assertThat(windows.targetFor(directory.resolve("sumador.vsim"), Optional.empty()))
                .isEqualTo(new OpeningTarget.FocusTab<>("two", saved));
    }

    @Test
    void withNoWindowAFileOpenedWithoutARequesterGetsANewWindow(@TempDir Path directory) {
        assertThat(windows.targetFor(directory.resolve("a.vsim"), Optional.empty()))
                .isEqualTo(new OpeningTarget.NewWindow<String>());
    }

    @Test
    void aFileOpenedWithoutARequesterJoinsTheLastOpenedWindowAsANewGroup(@TempDir Path directory) {
        windows.add(newDocument(), "one");
        windows.add(newDocument(), "two");
        assertThat(windows.targetFor(directory.resolve("a.vsim"), Optional.empty()))
                .isEqualTo(new OpeningTarget.NewGroup<>("two"));
    }

    @Test
    void aFileOpenedWithoutARequesterJoinsTheLastActivatedWindowStillOpen(@TempDir Path directory) {
        windows.add(newDocument(), "one");
        windows.add(newDocument(), "two");
        windows.add(newDocument(), "three");
        windows.activated("one");
        windows.activated("three");
        assertThat(windows.active()).contains("three");

        windows.removeWindow("three");

        assertThat(windows.active()).contains("one");
        assertThat(windows.targetFor(directory.resolve("a.vsim"), Optional.empty()))
                .isEqualTo(new OpeningTarget.NewGroup<>("one"));
    }

    @Test
    void thereIsNoActiveWindowWhenNoWindowHoldsADocument() {
        windows.activated("ghost");
        assertThat(windows.active()).isEmpty();
    }

    @Test
    void somethingNewReplacesOnlyThePristineDocumentsOfTheRequester(@TempDir Path directory) throws Exception {
        OpenDocument pristine = newDocument();
        OpenDocument saved = savedDocument(directory.resolve("sumador.vsim"));
        windows.add(pristine, "one");
        windows.add(saved, "one");
        windows.add(saved, "two");

        assertThat(windows.replacedBy("one")).containsExactly(pristine);
        assertThat(windows.replacedBy("two")).isEmpty();
        assertThat(windows.replacedBy("ghost")).isEmpty();
        assertThat(windows.replacedBy(null)).isEmpty();
    }

    @Test
    void anOpenedExampleIsAnUntitledUnmodifiedDocumentThatIsNeverReplaced() {
        CircuitDocument example = new CircuitDocument();
        example.setName("Sumador completo");
        example.add(registry.require(StandardComponentTypes.SWITCH).newComponent(example.newComponentId(),
                new GridPoint(2, 4), Rotation.R0, Map.of(), "A"));
        OpenDocument document = new OpenDocument(example, registry, null, false);
        opened.add(document);
        windows.add(document, "example");

        assertThat(document.path()).isEmpty();
        assertThat(document.isModified()).isFalse();
        assertThat(document.needsSaveAs()).isTrue();
        assertThat(document.displayName()).isEqualTo("Sumador completo");
        assertThat(document.suggestedFileName()).isEqualTo("Sumador completo.vsim");
        assertThat(windows.replacedBy("example")).isEmpty();
    }

    @Test
    void aViewTitleHasTheNumberOnlyWhenThereIsOne() {
        assertThat(DocumentWindows.viewTitle("contador", 0)).isEqualTo("contador");
        assertThat(DocumentWindows.viewTitle("contador", 2)).isEqualTo("contador (2)");
    }
}
