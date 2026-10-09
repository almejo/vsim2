package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenDocumentTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<OpenDocument> opened = new ArrayList<>();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void closeAll() {
        opened.forEach(OpenDocument::close);
    }

    private OpenDocument track(OpenDocument document) {
        opened.add(document);
        return document;
    }

    @Test
    void newDocumentIsPristineAndUntitled() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        assertThat(document.isPristine()).isTrue();
        assertThat(document.isModified()).isFalse();
        assertThat(document.displayName()).isEqualTo("Sin título");
        assertThat(document.needsSaveAs()).isTrue();
        assertThat(document.suggestedFileName()).isEqualTo("Sin título.vsim");
    }

    @Test
    void everyEditRecompilesTheNetlistWithTheDocumentRevisionAsToken() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        Component source = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        assertThat(document.netlistToken()).isEqualTo(document.document().revision());
        assertThat(document.netlist().netForPin(new PinRef(source.id(), 0))).isPresent();
        assertThat(document.isModified()).isTrue();
        assertThat(document.isPristine()).isFalse();
    }

    @Test
    void simulationCatchesUpWithTheDocument() {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        Component led = place(document, StandardComponentTypes.LED, 3, -2, Map.of());
        waitUntil(() -> document.latestSnapshot().token() == document.netlistToken()
                && document.latestSnapshot().componentState(led.id().value()).equals(new VisibleState.LedState(Value.ONE)));
    }

    @Test
    void historyChangesAndSavesNotifyChangeListeners(@TempDir Path directory) throws IOException {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        AtomicInteger notifications = new AtomicInteger();
        document.addChangeListener(notifications::incrementAndGet);
        place(document, StandardComponentTypes.NOT, 0, 0, Map.of());
        document.history().undo();
        document.save(directory.resolve("c.vsim"));
        assertThat(notifications).hasValue(3);
    }

    @Test
    void savedFileReopensWithTheSameContent(@TempDir Path directory) throws Exception {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.NOT, 0, 0, Map.of());
        Path file = directory.resolve("inversor.vsim");
        document.save(file);
        assertThat(document.isModified()).isFalse();
        assertThat(document.path()).contains(file.toAbsolutePath());
        assertThat(document.needsSaveAs()).isFalse();
        assertThat(document.displayName()).isEqualTo("inversor");

        OpenDocument reopened = track(OpenDocument.open(file, registry));
        assertThat(reopened.document().components()).hasSize(1);
        assertThat(reopened.isImportedFromVsim1()).isFalse();
        assertThat(reopened.isModified()).isFalse();
    }

    @Test
    void importedDocumentNeedsSaveAs() throws Exception {
        OpenDocument document = track(OpenDocument.open(Path.of("../examples/vsim1/one-gate.json"), registry));
        assertThat(document.isImportedFromVsim1()).isTrue();
        assertThat(document.path()).isEmpty();
        assertThat(document.needsSaveAs()).isTrue();
        assertThat(document.displayName()).isEqualTo("one-gate");
        assertThat(document.suggestedFileName()).isEqualTo("one-gate.vsim");
        assertThat(document.isPristine()).isFalse();
    }

    @Test
    void failedSaveKeepsModifiedStateAndPath(@TempDir Path directory) throws IOException {
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        place(document, StandardComponentTypes.NOT, 0, 0, Map.of());
        Path good = directory.resolve("ok.vsim");
        document.save(good);
        place(document, StandardComponentTypes.NOT, 5, 0, Map.of());
        String before = Files.readString(good);

        assertThatThrownBy(() -> document.save(directory.resolve("missing-folder/x.vsim"))).isInstanceOf(IOException.class);

        assertThat(document.isModified()).isTrue();
        assertThat(document.path()).contains(good.toAbsolutePath());
        assertThat(Files.readString(good)).isEqualTo(before);
        try (var leftovers = Files.list(directory)) {
            assertThat(leftovers.map(path -> path.getFileName().toString())).containsExactly("ok.vsim");
        }
    }

    @Test
    void closeIsIdempotent() {
        OpenDocument document = OpenDocument.createEmpty(registry);
        document.close();
        document.close();
    }
}
