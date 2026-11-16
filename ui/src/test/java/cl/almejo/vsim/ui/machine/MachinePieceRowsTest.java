package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.TemplatesExtension;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine's piece in Propiedades (hito 3): its machine's name first, then Etiqueta, then a read-only summary of what
 * is changed inside (pins, states, type, edge) with «Entrar ↘ para cambiarlas», and «Editar descripción…» last.
 */
class MachinePieceRowsTest {

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final List<String> entered = new ArrayList<>();
    private LibraryService service;
    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, StandardComponentTypes.registry(), Runnable::run);
        document = MachineFixtures.circuitWithGeneratedMachines(2);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, List.of(new TemplatesExtension(service, new QuietLibraryDialogs()),
                new MachineModeExtension((snapshot, instancePath) -> Optional.empty())));
        view = new EditorView(document, services, recordingEntries());
        view.setSize(1200, 800);
        view.doLayout();
    }

    @AfterEach
    void close() {
        view.dispose();
        service.close();
        document.close();
    }

    private WindowCommands recordingEntries() {
        return new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
            @Override public void enter(OpenDocument place, String definitionId, Optional<String> contextPath,
                                        Optional<ViewState> from) {
                entered.add(definitionId + " @ " + contextPath.orElse(""));
            }
        };
    }

    private void selectCopy(int index) {
        view.selection().select(new ElementId(MachineFixtures.copies(document).get(index)));
    }

    /** The texts of Propiedades from «Nombre de la máquina» on: captions, values, section headers and buttons. */
    private List<String> texts() {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : view.labelFeatures().properties().getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                texts.add(label.getText());
            } else if (child instanceof AbstractButton button) {
                texts.add(button.getText());
            } else if (child.getClass().getSimpleName().equals("SectionHeader")) {
                texts.add(child.getAccessibleContext().getAccessibleName());
            }
        }
        return texts.subList(texts.indexOf(Messages.text("machine.piece.name")), texts.size());
    }

    @Test
    void thePiecesRowsGoNameLabelSummaryEnterAndTheDescriptionLast() {
        selectCopy(0);

        assertThat(texts()).containsExactly(
                "Nombre de la máquina", "Etiqueta",
                "De la máquina (se cambia adentro)",
                "Entradas", "x",
                "Salidas", "Z",
                "Estados", "3 · inicial S0",
                "Tipo", "Moore",
                "Flanco", "Bajada (como los flip-flops)",
                "Entrar ↘ para cambiarlas",
                "Editar descripción…");
    }

    @Test
    void entrarOpensTheMachinesTabAtThatCopy() {
        selectCopy(1);

        button(Messages.text("machine.piece.enter")).doClick();

        assertThat(entered).containsExactly(MachineFixtures.DEFINITION_ID + " @ "
                + MachineFixtures.copies(document).get(1));
    }

    @Test
    void theSummaryFollowsAnEditMadeInsideTheMachine() {
        selectCopy(0);
        StateMachine machine = MachineFixtures.detector();

        document.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID, machine
                .withStates(append(machine.states(), new MachineState("s3", "S3", new DiagramPoint(0, 160),
                        List.of())))
                .withEdge(StateMachine.EDGE_RISING)));

        assertThat(texts()).contains("4 · inicial S0", "Subida");
    }

    @Test
    void inputsAsABusSayWhichNameIsEachBit() {
        StateMachine machine = MachineFixtures.detector().withPinNames(List.of("x", "y"), List.of("Z"));

        assertThat(MachinePieceRows.inputsText(machine.withBus(true, false))).isEqualTo("in[1:0] = x·0 y·1");
        assertThat(MachinePieceRows.inputsText(machine)).isEqualTo("x, y");
        assertThat(MachinePieceRows.outputsText(machine.withBus(false, true))).isEqualTo("out[0] = Z·0");
        assertThat(MachinePieceRows.inputsText(machine.withPinNames(List.of(), List.of("Z")).withBus(true, true)))
                .isEqualTo("—");
    }

    @Test
    void aMachineWithoutInitialStateSaysOnlyHowManyStates() {
        StateMachine machine = MachineFixtures.detector().withInitialStateId(Optional.empty());

        assertThat(MachinePieceRows.statesText(machine)).isEqualTo("3");
    }

    private AbstractButton button(String text) {
        for (java.awt.Component child : view.labelFeatures().properties().getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("no button " + text);
    }

    private static <T> List<T> append(List<T> list, T element) {
        List<T> longer = new ArrayList<>(list);
        longer.add(element);
        return longer;
    }
}
