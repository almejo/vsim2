package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.RenameDefinitionCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.PlaceDefinitionTool;
import cl.almejo.vsim.ui.library.TemplatesExtension;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Duplicar como otra máquina» (hito 3, decision 4): a new machine of its own next to the original, named with the
 * components' rule («Semáforo 2»), with a fresh id, the same diagram, tests, notes and face; editing one never changes
 * the other, and one undo takes the copy and its piece away.
 */
class MachineDuplicationTest {

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private LibraryService service;
    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, StandardComponentTypes.registry(), Runnable::run);
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        document.execute(new RenameDefinitionCommand(MachineFixtures.DEFINITION_ID, "Semáforo"));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, List.of(new TemplatesExtension(service, new QuietLibraryDialogs()),
                new MachineModeExtension((snapshot, instancePath) -> Optional.empty())));
        view = new EditorView(document, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
    }

    @AfterEach
    void close() {
        view.dispose();
        service.close();
        document.close();
    }

    private Component original() {
        return document.document().component(new ElementId(MachineFixtures.copies(document).get(0))).orElseThrow();
    }

    private ComponentDefinition originalDefinition() {
        return document.document().definition(MachineFixtures.DEFINITION_ID).orElseThrow();
    }

    /** The machine definitions of the circuit other than the original. */
    private List<ComponentDefinition> copies() {
        return document.document().definitions().stream()
                .filter(definition -> !definition.id().equals(MachineFixtures.DEFINITION_ID)).toList();
    }

    private Component pieceOf(ComponentDefinition definition) {
        return document.document().components().stream()
                .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf(definition.id())))
                .findFirst().orElseThrow();
    }

    private void duplicate() {
        document.execute(MachineDuplication.duplicate(document, original(), library));
    }

    @Test
    void theCopyIsAMachineOfItsOwnNamedWithTheComponentsRule() {
        duplicate();

        assertThat(copies()).hasSize(1);
        ComponentDefinition copy = copies().get(0);
        assertThat(copy.id()).startsWith("maquina-");
        assertThat(copy.name()).isEqualTo("Semáforo 2");
        assertThat(copy.machine()).isEqualTo(originalDefinition().machine());
        assertThat(copy.appearance()).isEqualTo(originalDefinition().appearance());
        assertThat(copy.pinout()).isEqualTo(originalDefinition().pinout());
        assertThat(copy.description()).isEqualTo(originalDefinition().description());
    }

    @Test
    void theCopyTakesNoNameOfMisComponentes() {
        library.put(DefinitionBundle.of(MachineDefinitions.regenerated(MachineDefinitions.create("otra",
                "Semáforo 2").withMachine(StateMachine.initial())), id -> Optional.empty()));

        duplicate();

        assertThat(copies()).extracting(ComponentDefinition::name).containsExactly("Semáforo 3");
        assertThat(copies().get(0).id()).isNotIn(library.ids());
    }

    @Test
    void theNewPieceStandsToTheRightOfTheOriginalOverlappingNothing() {
        Component blocker = UiTestSupport.place(document, StandardComponentTypes.SWITCH,
                boundsOf(original()).maximumX() + 3, original().position().y(), Map.of());

        duplicate();

        Component piece = pieceOf(copies().get(0));
        assertThat(piece.position().y()).isEqualTo(original().position().y());
        assertThat(piece.position().x()).isGreaterThan(boundsOf(original()).maximumX());
        assertThat(boundsOf(piece).intersects(boundsOf(original()))).isFalse();
        assertThat(boundsOf(piece).intersects(boundsOf(blocker))).isFalse();
    }

    @Test
    void editingTheCopyLeavesTheOriginalAndTheOriginalLeavesTheCopy() {
        duplicate();
        String copyId = copies().get(0).id();
        StateMachine before = originalDefinition().machine().orElseThrow();

        document.execute(new EditMachineCommand(copyId, before.withState("s0", state -> new MachineState(
                state.id(), "Rojo", state.position(), state.outputs()))));

        assertThat(originalDefinition().machine()).contains(before);
        StateMachine copyNow = copies().get(0).machine().orElseThrow();
        document.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID, before.withState("s1",
                state -> new MachineState(state.id(), "Verde", state.position(), state.outputs()))));
        assertThat(copies().get(0).machine()).contains(copyNow);
    }

    @Test
    void oneUndoTakesTheCopyAndItsPieceAway() {
        int pieces = document.document().components().size();
        duplicate();
        assertThat(document.document().components()).hasSize(pieces + 1);

        perform(view.actions().undo());

        assertThat(copies()).isEmpty();
        assertThat(document.document().components()).hasSize(pieces);
    }

    @Test
    void theRightClickMenuOfAMachinePieceDuplicatesItAndSelectsTheCopy() {
        view.selection().select(original().id());

        item(view.contextMenu(Optional.of(original()), new Point2D.Double()), Messages.text("machine.duplicate"))
                .orElseThrow().doClick();

        assertThat(copies()).hasSize(1);
        assertThat(view.selection().ids()).containsExactly(pieceOf(copies().get(0)).id());
    }

    @Test
    void anOrdinaryPartsMenuHasNoDuplicateAsAMachine() {
        Component gate = UiTestSupport.place(document, StandardComponentTypes.SWITCH, 60, 40, Map.of());
        view.selection().select(gate.id());

        assertThat(item(view.contextMenu(Optional.of(gate), new Point2D.Double()),
                Messages.text("machine.duplicate"))).isEmpty();
    }

    @Test
    void theEditMenuOffersItForOneMachinePieceAndSaysWhyOtherwise() {
        JMenuItem entry = editItem();
        assertThat(entry.isEnabled()).isFalse();
        assertThat(entry.getToolTipText()).isEqualTo(Messages.text("machine.duplicate.choosePiece"));
        assertThat(cl.almejo.vsim.ui.actions.Actions.disabledReason(entry.getAction()))
                .contains(Messages.text("machine.duplicate.choosePiece"));

        view.selection().select(original().id());
        assertThat(entry.isEnabled()).isTrue();
        entry.doClick();

        assertThat(copies()).hasSize(1);
        assertThat(view.selection().ids()).containsExactly(pieceOf(copies().get(0)).id());
    }

    @Test
    void thePalettesMenuOfAMachineArmsPlacingAnIndependentCopy() {
        DefinitionBundle bundle = DefinitionBundle.of(originalDefinition(), document.document()::definition);
        JPopupMenu menu = new JPopupMenu();

        MachineDuplicationActions.paletteContributor(view)
                .accept(Optional.of(PalettePanel.UserComponent.of(bundle, false)), menu);
        item(menu, Messages.text("machine.duplicate")).orElseThrow().doClick();

        assertThat(view.canvas().tool()).isInstanceOfSatisfying(PlaceDefinitionTool.class, tool -> {
            assertThat(tool.bundle().root().name()).isEqualTo("Semáforo 2");
            assertThat(tool.bundle().root().id()).isNotEqualTo(MachineFixtures.DEFINITION_ID);
            assertThat(tool.bundle().root().machine()).isEqualTo(originalDefinition().machine());
        });
    }

    @Test
    void thePalettesMenuOfAnOrdinaryComponentHasNoDuplicate() {
        ComponentDefinition plain = new ComponentDefinition("negador", "Negador", "",
                originalDefinition().size(), List.of(), originalDefinition().body(), Optional.empty(), 1);
        JPopupMenu menu = new JPopupMenu();

        MachineDuplicationActions.paletteContributor(view).accept(Optional.of(PalettePanel.UserComponent.of(
                DefinitionBundle.of(plain, id -> Optional.empty()), true)), menu);

        assertThat(menu.getComponentCount()).isZero();
    }

    private Bounds boundsOf(Component component) {
        return ElementBounds.of(component, document.registry());
    }

    private JMenuItem editItem() {
        JMenu edit = view.menu(MenuId.EDIT);
        Set<String> texts = new java.util.LinkedHashSet<>();
        for (int index = 0; index < edit.getItemCount(); index++) {
            JMenuItem entry = edit.getItem(index);
            if (entry != null && Messages.text("machine.duplicate").equals(entry.getText())) {
                return entry;
            }
            if (entry != null) {
                texts.add(entry.getText());
            }
        }
        throw new AssertionError("no item in " + texts.stream().collect(Collectors.joining(", ")));
    }

    private static Optional<JMenuItem> item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem entry && text.equals(entry.getText())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
