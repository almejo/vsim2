package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryKind;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** A memory file dropped on the canvas or chosen in Archivo ▸ Importar becomes a new linked RAM or ROM. */
class MemoryFileImportTest {

    @TempDir
    Path folder;

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final FakeLinkPrompts prompts = new FakeLinkPrompts();
    private final MemoryLinks links = new MemoryLinks(prompts, (file, command) -> true,
            MemoryLinks.PollScheduler.MANUAL);
    private final List<Path> openedAsCircuits = new ArrayList<>();
    private EditorView view;

    @BeforeEach
    void createView() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new MemoryEditorExtension(
                new MemoryEditorLauncher(links), links)));
        view = new EditorView(document, services, NO_WINDOW);
        view.setCircuitOpener(openedAsCircuits::addAll);
    }

    @AfterEach
    void close() {
        view.dispose();
        document.close();
    }

    private Path file(String name, String text) throws IOException {
        Path file = folder.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    private Component onlyComponent() {
        assertThat(document.document().components()).hasSize(1);
        return document.document().components().get(0);
    }

    @Test
    void aFileWithAHeaderBecomesItsMemoryAtTheDropPointLinkedInOneStep() throws IOException {
        Path program = file("programa.hex", "# vsim: rom name=Sprites words=1024 width=16\n@000\n3A10 C601\n");

        view.openOrImportFiles(List.of(program), openedAsCircuits::addAll, Optional.of(new GridPoint(12, 8)));

        Component rom = onlyComponent();
        assertThat(rom.typeId()).isEqualTo(StandardComponentTypes.ROM);
        assertThat(rom.label()).isEqualTo("Sprites");
        assertThat(rom.position()).isEqualTo(new GridPoint(12, 8));
        assertThat(rom.parameters()).containsEntry(MemoryTypes.WORDS, "1024").containsEntry(MemoryTypes.WIDTH, 16);
        assertThat(MemoryContents.decode((String) rom.parameters().get(MemoryTypes.CONTENTS)))
                .isEqualTo(MemoryContents.empty().with(0, 0x3A10).with(1, 0xC601));
        assertThat(MemoryLinks.resolve((String) rom.parameters().get(MemoryTypes.LINK), document.path()))
                .isEqualTo(program);
        assertThat(prompts.askedKinds).isEmpty();
        assertThat(openedAsCircuits).isEmpty();
        assertThat(view.selection().ids()).containsExactly(rom.id());
        assertThat(document.history().undoDescription()).contains("command.importMemory");
        document.history().undo();
        assertThat(document.document().components()).isEmpty();
    }

    @Test
    void aLogisimFileAsksRamOrRomWithTheValuesItSuggests() throws IOException {
        Path image = file("datos.txt", "v2.0 raw\n3a 10 c6 1 0 0 0 0 0 ff\n");
        prompts.kindAnswer = proposal -> new MemoryFileImport.Proposal(MemoryKind.RAM, proposal.name(),
                proposal.words(), proposal.width());

        view.openOrImportFiles(List.of(image), openedAsCircuits::addAll, Optional.of(new GridPoint(0, 0)));

        assertThat(prompts.askedKinds).containsExactly(new MemoryFileImport.Proposal(MemoryKind.ROM, "datos", 16, 8));
        Component ram = onlyComponent();
        assertThat(ram.typeId()).isEqualTo(StandardComponentTypes.RAM);
        assertThat(ram.label()).isEqualTo("datos");
        assertThat(ram.parameters()).containsEntry(MemoryTypes.WORDS, "16").containsEntry(MemoryTypes.WIDTH, 8);
    }

    @Test
    void cancellingTheQuestionAddsNothing() throws IOException {
        prompts.cancelKind = true;
        view.openOrImportFiles(List.of(file("datos.hex", "01 02\n")), openedAsCircuits::addAll, Optional.empty());
        assertThat(document.document().components()).isEmpty();
    }

    @Test
    void anUnreadableFileIsExplainedAndAddsNothing() throws IOException {
        view.openOrImportFiles(List.of(file("roto.hex", "# vsim: rom\nC6G1\n")), openedAsCircuits::addAll,
                Optional.empty());
        assertThat(prompts.informed).singleElement().asString().contains("roto.hex, línea 2");
        assertThat(document.document().components()).isEmpty();
    }

    @Test
    void onlyMemoryFilesAreRecognizedAndCircuitsStillOpen() throws IOException {
        Path circuit = file("cpu.vsim", "{}\n");
        Path picture = file("foto.png", "\u0089PNG\n");
        Path headed = file("programa.dat", "# vsim: ram\n01\n");
        assertThat(MemoryFileImport.isMemoryFile(file("rom.hex", "01\n"))).isTrue();
        assertThat(MemoryFileImport.isMemoryFile(headed)).isTrue();
        assertThat(MemoryFileImport.isMemoryFile(file("raw.bin", "v2.0 raw\n01\n"))).isTrue();
        assertThat(MemoryFileImport.isMemoryFile(circuit)).isFalse();
        assertThat(MemoryFileImport.isMemoryFile(picture)).isFalse();
        assertThat(MemoryFileImport.isMemoryFile(folder.resolve("missing.hex"))).isFalse();

        view.openOrImportFiles(List.of(circuit, headed), openedAsCircuits::addAll, Optional.empty());

        assertThat(openedAsCircuits).containsExactly(circuit);
        assertThat(onlyComponent().typeId()).isEqualTo(StandardComponentTypes.RAM);
    }

    @Test
    void theImportMenuAsksForAFileAndPlacesItInTheMiddleOfTheCanvas() throws IOException {
        prompts.memoryFile = Optional.of(file("programa.hex", "# vsim: rom\n01\n"));
        JMenu importMenu = null;
        JMenu file = view.menu(MenuId.FILE);
        for (int index = 0; index < file.getItemCount(); index++) {
            if (file.getItem(index) instanceof JMenu submenu && submenu.getText().equals("Importar")) {
                importMenu = submenu;
            }
        }
        assertThat(importMenu).isNotNull();
        JMenuItem item = importMenu.getItem(importMenu.getItemCount() - 1);
        assertThat(item.getText()).isEqualTo("Memoria desde archivo…");

        item.doClick();

        assertThat(onlyComponent().typeId()).isEqualTo(StandardComponentTypes.ROM);
        assertThat(onlyComponent().label()).isEqualTo("programa");
    }

    @Test
    void theKindDialogStartsFromTheProposalAndGivesWhatTheUserChose() {
        MemoryKindDialog dialog = new MemoryKindDialog(new MemoryFileImport.Proposal(MemoryKind.ROM, "datos", 16, 8));
        assertThat(dialog.result()).isEqualTo(new MemoryFileImport.Proposal(MemoryKind.ROM, "datos", 16, 8));
        assertThat(dialog.words().getSelectedItem()).isEqualTo("16");

        dialog.ram().setSelected(true);
        dialog.words().setSelectedItem("1K");
        dialog.name().setText(" Pila ");

        assertThat(dialog.result()).isEqualTo(new MemoryFileImport.Proposal(MemoryKind.RAM, "Pila", 1024, 8));
    }

    @Test
    void theProposalOfAFileWithoutHeaderComesFromItsWords() throws Exception {
        Path file = file("tabla.mem", "@12B\nFFF\n");
        MemoryFileImport.Proposal proposal = MemoryFileImport.propose(file, MemoryFileIo.read(file));
        assertThat(proposal).isEqualTo(new MemoryFileImport.Proposal(MemoryKind.ROM, "tabla", 512, 12));
    }
}
