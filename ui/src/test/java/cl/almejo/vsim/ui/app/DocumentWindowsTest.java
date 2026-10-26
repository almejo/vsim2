package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.window.WindowMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentWindowsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<OpenDocument> opened = new ArrayList<>();
    private final DocumentWindows<String> windows = new DocumentWindows<>();

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
        windows.add(first, "first-1");
        windows.add(second, "second-1");
        windows.add(first, "first-2");
        assertThat(windows.documents()).containsExactly(first, second);
        assertThat(windows.windowsOf(first)).containsExactly("first-1", "first-2");
        assertThat(windows.all()).containsExactly("first-1", "first-2", "second-1");
    }

    @Test
    void theDocumentIsReleasedOnlyWithItsLastWindow() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        windows.add(document, "two");
        assertThat(windows.isLastWindow("one")).isFalse();
        assertThat(windows.remove("one")).isEmpty();
        assertThat(windows.documents()).containsExactly(document);
        assertThat(windows.isLastWindow("two")).isTrue();
        assertThat(windows.remove("two")).contains(document);
        assertThat(windows.isEmpty()).isTrue();
        assertThat(windows.documents()).isEmpty();
        assertThat(windows.windowsOf(document)).isEmpty();
    }

    @Test
    void removingAnUnknownWindowChangesNothing() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        assertThat(windows.remove("ghost")).isEmpty();
        assertThat(windows.all()).containsExactly("one");
    }

    @Test
    void aWindowCanBeRegisteredOnlyOnce() {
        OpenDocument document = newDocument();
        windows.add(document, "one");
        assertThatThrownBy(() -> windows.add(newDocument(), "one")).isInstanceOf(IllegalArgumentException.class);
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
    void menuEntriesNumberTheViewsOfADocumentWithSeveralAndMarkTheCurrentOne(@TempDir Path directory)
            throws Exception {
        windows.add(newDocument(), "untitled");
        OpenDocument saved = savedDocument(directory.resolve("sumador.vsim"));
        windows.add(saved, "saved-1");
        windows.add(saved, "saved-2");
        List<String> focused = new ArrayList<>();
        List<WindowMenu.Entry> entries = windows.menuEntries("saved-2", focused::add);
        assertThat(entries).extracting(WindowMenu.Entry::title)
                .containsExactly("Sin título", "sumador (1)", "sumador (2)");
        assertThat(entries).extracting(WindowMenu.Entry::current).containsExactly(false, false, true);
        entries.get(1).focus().run();
        assertThat(focused).containsExactly("saved-1");
    }

    @Test
    void viewNumbersCountOnlyWhenADocumentHasSeveralWindowsAndFollowClosing() {
        OpenDocument document = newDocument();
        OpenDocument other = newDocument();
        windows.add(document, "a-1");
        windows.add(other, "b-1");
        assertThat(windows.viewNumber("a-1")).isZero();
        windows.add(document, "a-2");
        windows.add(document, "a-3");
        assertThat(windows.viewNumber("a-1")).isEqualTo(1);
        assertThat(windows.viewNumber("a-2")).isEqualTo(2);
        assertThat(windows.viewNumber("a-3")).isEqualTo(3);
        assertThat(windows.viewNumber("b-1")).isZero();
        windows.remove("a-2");
        assertThat(windows.viewNumber("a-3")).isEqualTo(2);
        windows.remove("a-1");
        assertThat(windows.viewNumber("a-3")).isZero();
        assertThat(windows.viewNumber("ghost")).isZero();
    }

    @Test
    void changeListenersHearEveryAddAndRemoveAfterTheChange() {
        OpenDocument document = newDocument();
        List<Integer> secondViewNumbers = new ArrayList<>();
        Runnable listener = () -> secondViewNumbers.add(windows.viewNumber("two"));
        windows.addChangeListener(listener);
        assertThat(windows.changeListenerCount()).isEqualTo(1);
        windows.add(document, "one");
        windows.add(document, "two");
        windows.remove("ghost");
        windows.remove("one");
        assertThat(secondViewNumbers).containsExactly(0, 2, 0);
        windows.removeChangeListener(listener);
        windows.remove("two");
        assertThat(secondViewNumbers).hasSize(3);
        assertThat(windows.changeListenerCount()).isZero();
    }

    @Test
    void somethingNewReplacesOnlyTheWindowsOfAPristineRequester(@TempDir Path directory) throws Exception {
        OpenDocument pristine = newDocument();
        OpenDocument saved = savedDocument(directory.resolve("sumador.vsim"));
        windows.add(pristine, "pristine-1");
        windows.add(saved, "saved");
        windows.add(pristine, "pristine-2");

        assertThat(windows.replacedBy("pristine-2")).containsExactly("pristine-1", "pristine-2");
        assertThat(windows.replacedBy("saved")).isEmpty();
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
