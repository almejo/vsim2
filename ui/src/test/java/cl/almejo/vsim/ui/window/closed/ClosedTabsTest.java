package cl.almejo.vsim.ui.window.closed;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.session.WindowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The stack of closed tabs (card «ctrl-t»): newest first, twenty at most, no closed document kept in memory. */
class ClosedTabsTest {

    private static final ViewState VIEW = new ViewState(1.5, 40, -20);
    private static final WindowState BOUNDS = new WindowState(10, 20, 1280, 820, false, VIEW);

    private final List<OpenDocument> documents = new ArrayList<>();
    private final ClosedTabs closedTabs = new ClosedTabs();

    @TempDir
    Path folder;

    @AfterEach
    void close() {
        documents.forEach(OpenDocument::close);
    }

    private OpenDocument untitled() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        return document;
    }

    private OpenDocument saved(String name) throws Exception {
        OpenDocument document = untitled();
        document.save(folder.resolve(name));
        return document;
    }

    private static void modify(OpenDocument document) {
        place(document, StandardComponentTypes.BUFFER, 0, 0, Map.of(StandardComponentTypes.NEGATE, true));
    }

    private static ClosedTab componentTab(OpenDocument document, int index) {
        return new ClosedTab(DocumentReference.of(document),
                ClosedTabState.component("inverter", "Inversor", Optional.of("c" + index), VIEW),
                WindowReference.none(), index);
    }

    private static ClosedGroup group(OpenDocument document) {
        return new ClosedGroup(DocumentReference.of(document), List.of(ClosedTabState.main(VIEW)), 0, false,
                WindowReference.none(), 0);
    }

    @Test
    void theNewestComesFirstAndOnlyTheLastTwentyStay() throws Exception {
        OpenDocument document = saved("contador.vsim");
        for (int index = 0; index < 25; index++) {
            closedTabs.push(componentTab(document, index));
        }

        assertThat(closedTabs.entries()).hasSize(ClosedTabs.CAPACITY).hasSize(20);
        assertThat(((ClosedTab) closedTabs.entries().getFirst()).indexInGroup()).isEqualTo(24);
        assertThat(((ClosedTab) closedTabs.entries().getLast()).indexInGroup()).isEqualTo(5);
        assertThat(closedTabs.pop()).hasValueSatisfying(entry ->
                assertThat(((ClosedTab) entry).indexInGroup()).isEqualTo(24));
        assertThat(closedTabs.pop()).hasValueSatisfying(entry ->
                assertThat(((ClosedTab) entry).indexInGroup()).isEqualTo(23));
        assertThat(closedTabs.entries()).hasSize(18);
    }

    @Test
    void anEmptyStackPopsNothing() {
        assertThat(closedTabs.isEmpty()).isTrue();
        assertThat(closedTabs.pop()).isEmpty();
    }

    @Test
    void anUntouchedUntitledCircuitNeverEnters() {
        closedTabs.push(group(untitled()));
        closedTabs.push(new ClosedWindow(List.of(group(untitled())), 0, BOUNDS));

        assertThat(closedTabs.isEmpty()).isTrue();
    }

    @Test
    void aWindowKeepsOnlyItsGroupsThatCanComeBack() throws Exception {
        OpenDocument adder = saved("sumador.vsim");
        closedTabs.push(new ClosedWindow(List.of(group(untitled()), group(adder)), 1, BOUNDS));

        ClosedWindow window = (ClosedWindow) closedTabs.pop().orElseThrow();
        assertThat(window.groups()).hasSize(1);
        assertThat(window.groups().getFirst().document().openDocument()).containsSame(adder);
        assertThat(window.activeGroup()).isZero();
    }

    @Test
    void aModifiedUntitledCircuitEntersAndClosesAsNeverSaved() {
        OpenDocument document = untitled();
        modify(document);
        closedTabs.push(group(document));

        closedTabs.documentClosed(document);

        ClosedGroup group = (ClosedGroup) closedTabs.entries().getFirst();
        assertThat(group.closing()).isEqualTo(Closing.NEVER_SAVED);
        assertThat(group.document().openDocument()).isEmpty();
        assertThat(group.document().path()).isEmpty();
    }

    @Test
    void whenItsDocumentClosesAReferenceKeepsOnlyTheFileAndHowItClosed() throws Exception {
        OpenDocument kept = saved("contador.vsim");
        OpenDocument discarded = saved("sumador.vsim");
        closedTabs.push(group(kept));
        closedTabs.push(componentTab(discarded, 1));
        closedTabs.push(new ClosedWindow(List.of(group(discarded)), 0, BOUNDS));
        assertThat(((ClosedGroup) closedTabs.entries().getLast()).closing()).isEqualTo(Closing.OPEN);

        modify(discarded); // «No guardar»: it closes with changes
        closedTabs.documentClosed(kept);
        closedTabs.documentClosed(discarded);

        ClosedGroup keptGroup = (ClosedGroup) closedTabs.entries().get(2);
        assertThat(keptGroup.document().openDocument()).isEmpty();
        assertThat(keptGroup.document().path()).contains(folder.resolve("contador.vsim"));
        assertThat(keptGroup.closing()).isEqualTo(Closing.SAVED);
        ClosedTab tab = (ClosedTab) closedTabs.entries().get(1);
        assertThat(tab.document().openDocument()).isEmpty();
        assertThat(tab.document().closing()).isEqualTo(Closing.LOST_CHANGES);
        ClosedWindow window = (ClosedWindow) closedTabs.entries().getFirst();
        assertThat(window.groups().getFirst().closing()).isEqualTo(Closing.LOST_CHANGES);
        assertThat(window.groups().getFirst().document().displayName()).isEqualTo("sumador");
    }

    @Test
    void aReferenceFollowsASaveAsWhileItsDocumentIsOpen() throws Exception {
        OpenDocument document = untitled();
        modify(document);
        ClosedTab tab = componentTab(document, 1);
        closedTabs.push(tab);

        document.save(folder.resolve("nuevo.vsim"));
        closedTabs.documentClosed(document);

        assertThat(tab.document().path()).contains(folder.resolve("nuevo.vsim"));
        assertThat(tab.document().closing()).isEqualTo(Closing.SAVED);
    }

    @Test
    void pushesAndPopsAreHeard() throws Exception {
        OpenDocument document = saved("contador.vsim");
        int[] heard = new int[1];
        Runnable remover = closedTabs.addListener(() -> heard[0]++);

        closedTabs.push(componentTab(document, 1));
        closedTabs.pop();
        closedTabs.pop(); // nothing left: nothing changes
        remover.run();
        closedTabs.push(componentTab(document, 2));

        assertThat(heard[0]).isEqualTo(2);
    }

    @Test
    void aWindowReferenceGivesItsWindowWhileItIsThere() {
        Object window = new Object();
        WindowReference reference = WindowReference.of(window);

        assertThat(reference.window()).containsSame(window);
        assertThat(WindowReference.none().window()).isEmpty();
    }
}
