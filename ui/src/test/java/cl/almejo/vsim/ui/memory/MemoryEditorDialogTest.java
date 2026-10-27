package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryEditorDialogTest {

    @TempDir
    Path folder;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final FakePrompts prompts = new FakePrompts();

    @AfterEach
    void close() {
        document.close();
    }

    /** Answers every question from fields a test sets and records what was shown. */
    private static final class FakePrompts implements MemoryEditorPrompts {
        Optional<Path> importFile = Optional.empty();
        Optional<SaveChoice> saveChoice = Optional.empty();
        Optional<FillRequest> fill = Optional.empty();
        Optional<String> address = Optional.empty();
        boolean confirmation = true;
        final List<String> informed = new ArrayList<>();
        final List<String> confirmed = new ArrayList<>();
        String clipboard;

        @Override
        public Optional<Path> chooseImport() {
            return importFile;
        }

        @Override
        public Optional<SaveChoice> chooseSave(String suggestedName) {
            return saveChoice;
        }

        @Override
        public Optional<FillRequest> askFill(String firstAddress, String lastAddress) {
            return fill;
        }

        @Override
        public Optional<String> askAddress() {
            return address;
        }

        @Override
        public boolean confirm(String message) {
            confirmed.add(message);
            return confirmation;
        }

        @Override
        public void inform(String message) {
            informed.add(message);
        }

        @Override
        public void copyToClipboard(String text) {
            clipboard = text;
        }

        @Override
        public Optional<String> clipboardText() {
            return Optional.ofNullable(clipboard);
        }
    }

    /** Link actions that record the calls; unlinking really clears the parameter, as the real ones will. */
    private final class RecordingLinks implements MemoryLinkActions {
        final List<String> calls = new ArrayList<>();

        @Override
        public void link(EditorView view, ElementId componentId) {
            calls.add("link");
        }

        @Override
        public void unlink(EditorView view, ElementId componentId) {
            calls.add("unlink");
            Component component = document.document().component(componentId).orElseThrow();
            Map<String, Object> parameters = new HashMap<>(component.parameters());
            parameters.put(MemoryTypes.LINK, "");
            document.execute(new ChangeParametersCommand(componentId,
                    document.registry().require(component.typeId()), parameters));
        }

        @Override
        public void openExternal(EditorView view, ElementId componentId) {
            calls.add("external");
        }
    }

    private Component rom(Map<String, Object> parameters) {
        return place(document, StandardComponentTypes.ROM, 0, 0, parameters);
    }

    private Component ram(Map<String, Object> parameters) {
        return place(document, StandardComponentTypes.RAM, 0, 0, parameters);
    }

    private MemoryEditorDialog dialogOn(Component component) {
        return dialogOn(component, new RecordingLinks());
    }

    private MemoryEditorDialog dialogOn(Component component, MemoryLinkActions links) {
        return new MemoryEditorDialog(document, component.id(), null, links, prompts);
    }

    private String contentsOf(Component component) {
        return (String) document.document().component(component.id()).orElseThrow().parameters()
                .get(MemoryTypes.CONTENTS);
    }

    private static AbstractButton button(Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof AbstractButton found && name.equals(found.getName())) {
                return found;
            }
            if (child instanceof Container container) {
                AbstractButton nested = button(container, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    @Test
    void editingThreeCellsAndAcceptingIsOneUndoStepThatRestoresTheOldContents() {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 1"));
        MemoryEditorDialog dialog = dialogOn(component);
        dialog.model().setCell(1, "2");
        dialog.model().setCell(2, "3");
        dialog.model().setCell(3, "4");
        assertThat(dialog.accept()).isTrue();
        assertThat(contentsOf(component)).isEqualTo("@0 1 2 3 4");
        assertThat(document.history().undoDescription()).contains("command.editMemory");
        document.history().undo();
        assertThat(contentsOf(component)).isEqualTo("@0 1");
        document.history().undo();
        assertThat(document.document().component(component.id())).isEmpty(); // the placement: nothing in between
    }

    @Test
    void cancelChangesNothingAndLeavesNoUndoStep() {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 1"));
        long changesBefore = document.changeCount();
        MemoryEditorDialog dialog = dialogOn(component);
        dialog.model().setCell(1, "2");
        dialog.cancel();
        assertThat(contentsOf(component)).isEqualTo("@0 1");
        assertThat(document.changeCount()).isEqualTo(changesBefore);
        assertThat(document.history().undoDescription()).contains("command.add");
    }

    @Test
    void acceptingWithoutEditsDoesNotTouchTheDocument() {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16"));
        long changesBefore = document.changeCount();
        assertThat(dialogOn(component).accept()).isTrue();
        assertThat(document.changeCount()).isEqualTo(changesBefore);
    }

    @Test
    void aLinkedMemoryIsReadOnlyWithItsBarAndAcceptingChangesNothing() {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 1",
                MemoryTypes.LINK, "programa.hex"));
        MemoryEditorDialog dialog = dialogOn(component);
        assertThat(dialog.model().isReadOnly()).isTrue();
        assertThat(dialog.table().isCellEditable(0, 1)).isFalse();
        assertThat(dialog.linkBar().isVisible()).isTrue();
        assertThat(dialog.model().setCell(0, "5")).contains(MemoryGridModel.READ_ONLY);
        long changesBefore = document.changeCount();
        dialog.accept();
        assertThat(document.changeCount()).isEqualTo(changesBefore);
    }

    @Test
    void anUnlinkedMemoryHasNoBarAndItsGridIsEditable() {
        MemoryEditorDialog dialog = dialogOn(rom(Map.of(MemoryTypes.WORDS, "16")));
        assertThat(dialog.linkBar().isVisible()).isFalse();
        assertThat(dialog.model().isReadOnly()).isFalse();
    }

    @Test
    void unlinkingAsksAndThenTheGridBecomesEditable() {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.LINK, "programa.hex"));
        RecordingLinks links = new RecordingLinks();
        MemoryEditorDialog dialog = dialogOn(component, links);
        prompts.confirmation = false;
        button(dialog.content(), "memory.unlink").doClick();
        assertThat(links.calls).isEmpty();
        assertThat(dialog.model().isReadOnly()).isTrue();
        prompts.confirmation = true;
        button(dialog.content(), "memory.unlink").doClick();
        assertThat(links.calls).containsExactly("unlink");
        assertThat(dialog.model().isReadOnly()).isFalse();
        assertThat(dialog.linkBar().isVisible()).isFalse();
    }

    @Test
    void goToJumpsToTheLastRowForTheLastAddress() {
        MemoryEditorDialog dialog = dialogOn(rom(Map.of(MemoryTypes.WORDS, "1024")));
        prompts.address = Optional.of("3FF");
        button(dialog.content(), "memory.goTo").doClick();
        assertThat(dialog.table().getSelectedRow()).isEqualTo(dialog.model().getRowCount() - 1);
        assertThat(dialog.table().getSelectedColumn()).isEqualTo(16);
        prompts.address = Optional.of("400");
        button(dialog.content(), "memory.goTo").doClick();
        assertThat(prompts.informed).hasSize(1);
        assertThat(dialog.table().getSelectedRow()).isEqualTo(dialog.model().getRowCount() - 1);
    }

    @Test
    void theLauncherAddressSelectsItsCell() {
        MemoryEditorDialog dialog = dialogOn(rom(Map.of(MemoryTypes.WORDS, "1024")));
        dialog.goTo(0x21);
        assertThat(dialog.table().getSelectedRow()).isEqualTo(2);
        assertThat(dialog.table().getSelectedColumn()).isEqualTo(2);
    }

    @Test
    void dumpingToTheLinkedFileIsRefusedAndNothingIsWritten() throws IOException {
        Path linked = folder.resolve("programa.hex");
        Files.writeString(linked, "@0\n01\n");
        Component component = ram(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.LINK, linked.toString()));
        MemoryEditorDialog dialog = dialogOn(component);
        prompts.saveChoice = Optional.of(new MemoryEditorPrompts.SaveChoice(linked,
                cl.almejo.vsim.core.memory.MemoryFormats.all().get(0)));
        button(dialog.content(), "memory.dump").doClick();
        assertThat(prompts.informed).hasSize(1);
        assertThat(Files.readString(linked)).isEqualTo("@0\n01\n");
    }

    @Test
    void dumpingWritesTheSimulationsWordsWithAHeader() throws Exception {
        Component component = ram(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@2 AB"));
        Path target = folder.resolve("copia.hex");
        MemoryEditorDialog dialog = dialogOn(component);
        dialog.dumpTo(new MemoryEditorPrompts.SaveChoice(target,
                cl.almejo.vsim.core.memory.MemoryFormats.all().get(0))).get();
        assertThat(Files.readString(target)).startsWith("# vsim: ram words=16 width=8").contains("AB");
    }

    @Test
    void aRomHasNoDumpAndNoRestoreButAnImportAndAnExport() {
        MemoryEditorDialog rom = dialogOn(rom(Map.of()));
        assertThat(button(rom.content(), "memory.dump")).isNull();
        assertThat(button(rom.content(), "memory.restore")).isNull();
        assertThat(button(rom.content(), "memory.import")).isNotNull();
        assertThat(button(rom.content(), "memory.export")).isNotNull();
        MemoryEditorDialog ram = dialogOn(ram(Map.of()));
        assertThat(button(ram.content(), "memory.dump")).isNotNull();
        assertThat(button(ram.content(), "memory.restore")).isNotNull();
    }

    @Test
    void withNoLinkActionsTheLinkButtonsAreDisabled() {
        MemoryEditorDialog dialog = dialogOn(rom(Map.of(MemoryTypes.LINK, "programa.hex")), MemoryLinkActions.NONE);
        assertThat(button(dialog.content(), "memory.link").isEnabled()).isFalse();
        assertThat(button(dialog.content(), "memory.external").isEnabled()).isFalse();
        assertThat(button(dialog.content(), "memory.unlink").isEnabled()).isFalse();
        MemoryEditorDialog withActions = dialogOn(rom(Map.of(MemoryTypes.LINK, "programa.hex")));
        assertThat(button(withActions.content(), "memory.link").isEnabled()).isTrue();
        assertThat(button(withActions.content(), "memory.external").isEnabled()).isTrue();
    }

    @Test
    void theExternalEditorButtonNeedsALink() {
        MemoryEditorDialog dialog = dialogOn(rom(Map.of()));
        assertThat(button(dialog.content(), "memory.external").isEnabled()).isFalse();
    }

    @Test
    void importingReplacesTheContentsAndIsNotLinked() throws IOException {
        Path file = folder.resolve("datos.hex");
        Files.writeString(file, "# vsim: rom words=16 width=8\n@1\n0A 0B\n");
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 FF"));
        MemoryEditorDialog dialog = dialogOn(component);
        prompts.importFile = Optional.of(file);
        button(dialog.content(), "memory.import").doClick();
        assertThat(dialog.model().edited()).isEqualTo(MemoryContents.decode("@1 A B"));
        dialog.accept();
        assertThat(document.document().component(component.id()).orElseThrow().parameters())
                .containsEntry(MemoryTypes.LINK, "");
    }

    @Test
    void importingAnUnreadableFileExplainsAndChangesNothing() throws IOException {
        Path file = folder.resolve("roto.hex");
        Files.writeString(file, "00\nC6G1\n");
        MemoryEditorDialog dialog = dialogOn(rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 1")));
        prompts.importFile = Optional.of(file);
        button(dialog.content(), "memory.import").doClick();
        assertThat(prompts.informed).hasSize(1);
        assertThat(prompts.informed.get(0)).contains("roto.hex").contains("C6G1");
        assertThat(dialog.model().edited()).isEqualTo(MemoryContents.decode("@0 1"));
    }

    @Test
    void importingIntoALinkedMemoryAsksToUnlinkFirst() throws IOException {
        Path file = folder.resolve("datos.hex");
        Files.writeString(file, "@0\n07\n");
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.LINK, "programa.hex"));
        RecordingLinks links = new RecordingLinks();
        MemoryEditorDialog dialog = dialogOn(component, links);
        prompts.importFile = Optional.of(file);
        prompts.confirmation = false;
        button(dialog.content(), "memory.import").doClick();
        assertThat(prompts.confirmed).hasSize(1);
        assertThat(links.calls).isEmpty();
        assertThat(dialog.model().edited().count()).isZero();
        prompts.confirmation = true;
        button(dialog.content(), "memory.import").doClick();
        assertThat(links.calls).containsExactly("unlink");
        assertThat(dialog.model().edited()).isEqualTo(MemoryContents.decode("@0 7"));
    }

    @Test
    void exportingWritesTheContentsAsTheyAreInTheDialog() throws Exception {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 1"));
        MemoryEditorDialog dialog = dialogOn(component);
        dialog.model().setCell(1, "2");
        Path target = folder.resolve("fuera.hex");
        prompts.saveChoice = Optional.of(new MemoryEditorPrompts.SaveChoice(target,
                cl.almejo.vsim.core.memory.MemoryFormats.all().get(0)));
        button(dialog.content(), "memory.export").doClick();
        assertThat(Files.readString(target)).contains("01 02");
    }

    @Test
    void fillingTheSelectedRangeIsOneEditOfTheDialog() {
        MemoryEditorDialog dialog = dialogOn(ram(Map.of(MemoryTypes.WORDS, "256")));
        prompts.fill = Optional.of(new MemoryEditorPrompts.FillRequest("10", "1F", "AA"));
        button(dialog.content(), "memory.fill").doClick();
        assertThat(dialog.model().edited().count()).isEqualTo(16);
        dialog.undo();
        assertThat(dialog.model().edited().count()).isZero();
        prompts.fill = Optional.of(new MemoryEditorPrompts.FillRequest("10", "ZZ", "AA"));
        button(dialog.content(), "memory.fill").doClick();
        assertThat(prompts.informed).hasSize(1);
    }

    @Test
    void pastingHexTextFromTheSelectedCellOn() {
        MemoryEditorDialog dialog = dialogOn(ram(Map.of(MemoryTypes.WORDS, "256")));
        dialog.table().changeSelection(0, 1, false, false);
        prompts.clipboard = "3A 10 @08 FF";
        dialog.pasteAtSelection();
        MemoryContents edited = dialog.model().edited();
        assertThat(edited.word(0)).isEqualTo(0x3A);
        assertThat(edited.word(1)).isEqualTo(0x10);
        assertThat(edited.word(8)).isEqualTo(0xFF);
    }

    @Test
    void copyingATwoByTwoSelectionPutsTwoLinesOfHexOnTheClipboard() {
        Component component = ram(Map.of(MemoryTypes.WORDS, "256", MemoryTypes.CONTENTS, "@0 0A 0B 0C @10 1A 1B 1C"));
        MemoryEditorDialog dialog = dialogOn(component);
        dialog.table().setRowSelectionInterval(0, 1);
        dialog.table().setColumnSelectionInterval(1, 2);
        dialog.copySelection();
        assertThat(prompts.clipboard).isEqualTo("0A 0B\n1A 1B");
    }

    @Test
    void controlZInsideTheDialogUndoesTheLastCellEdit() {
        MemoryEditorDialog dialog = dialogOn(ram(Map.of(MemoryTypes.WORDS, "256")));
        dialog.table().changeSelection(0, 1, false, false);
        dialog.table().setValueAt("11", 0, 1);
        dialog.table().setValueAt("22", 0, 2);
        assertThat(dialog.model().edited().count()).isEqualTo(2);
        dialog.table().getActionMap().get("memory.undo").actionPerformed(null);
        assertThat(dialog.model().edited().word(1)).isZero();
        assertThat(dialog.model().edited().word(0)).isEqualTo(0x11);
        dialog.table().getActionMap().get("memory.redo").actionPerformed(null);
        assertThat(dialog.model().edited().word(1)).isEqualTo(0x22);
    }

    @Test
    void aRefusedCellShowsItsMessageInTheStatusLineInsteadOfAPopup() {
        MemoryEditorDialog dialog = dialogOn(ram(Map.of(MemoryTypes.WORDS, "256")));
        dialog.table().setValueAt("zz", 0, 1);
        assertThat(dialog.status().getText()).isNotBlank().doesNotContain("!");
        assertThat(dialog.model().edited().count()).isZero();
    }

    @Test
    void shrinkingBelowTheContentsWarnsInTheStatusLine() {
        Component component = rom(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@20 1"));
        MemoryEditorDialog dialog = dialogOn(component);
        assertThat(dialog.status().getText()).isNotBlank().doesNotContain("!");
        assertThat(dialog.model().edited().word(0x20)).isEqualTo(1);
        dialog.accept();
        assertThat(contentsOf(component)).isEqualTo("@20 1");
    }

    @Test
    void restoreAsksTheSimulationToRestoreTheRamWithoutTouchingTheDocument() {
        Component component = ram(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 5"));
        long changesBefore = document.changeCount();
        button(dialogOn(component).content(), "memory.restore").doClick();
        assertThat(document.changeCount()).isEqualTo(changesBefore);
    }

    @Test
    void theLiveGridShowsTheSimulationsWordsAndStopsWhenItIsNotRunning() throws Exception {
        Component component = ram(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.CONTENTS, "@0 5"));
        MemoryEditorDialog dialog = dialogOn(component);
        document.pause();
        waitUntil(() -> !document.latestSnapshot().running());
        dialog.pollLive().get(10, TimeUnit.SECONDS);
        assertThat(dialog.model().isShowingLive()).isFalse();
        document.resume();
        waitUntil(() -> document.latestSnapshot().running()
                && document.latestSnapshot().componentState(component.id().value()) != null);
        dialog.pollLive().get(10, TimeUnit.SECONDS);
        assertThat(dialog.model().valueAt(0)).isEqualTo(5);
        document.pause();
        waitUntil(() -> !document.latestSnapshot().running());
        dialog.pollLive().get(10, TimeUnit.SECONDS);
        assertThat(dialog.model().isShowingLive()).isFalse();
    }

    @Test
    void everyFileFormatAndRadixHasAName() {
        for (cl.almejo.vsim.core.memory.MemoryFileFormat format : cl.almejo.vsim.core.memory.MemoryFormats.all()) {
            assertThat(cl.almejo.vsim.ui.i18n.Messages.text("memory.format." + format.id())).doesNotStartWith("!");
        }
        for (MemoryRadix radix : MemoryRadix.values()) {
            assertThat(cl.almejo.vsim.ui.i18n.Messages.text("memory.radix." + radix)).doesNotStartWith("!");
        }
    }

    @Test
    void everyFileProblemOfTheFormatsHasAMessage() {
        for (String key : List.of("notHex", "tooLong", "tooWide", "badWords", "badWidth", "badRun", "notLogisim")) {
            assertThat(cl.almejo.vsim.ui.i18n.Messages.text("memory.file." + key)).doesNotStartWith("!");
        }
    }
}
