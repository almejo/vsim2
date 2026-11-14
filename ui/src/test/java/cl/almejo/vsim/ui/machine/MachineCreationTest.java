package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** Palette ▸ Memoria ▸ «Máquina de estados»: one undo step makes the definition and its piece. */
class MachineCreationTest {

    private final TestToolContext context = TestToolContext.withNewDocument();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void theToolMakesTheDefinitionAndThePieceInOneUndoStep() {
        PlaceMachineTool tool = new PlaceMachineTool();
        context.tool = tool;

        tool.mousePressed(context, TestToolContext.at(3, 4));

        ComponentDefinition definition = context.openDocument().document().definition("maquina-1").orElseThrow();
        assertThat(definition.name()).isEqualTo("Máquina 1");
        assertThat(definition.isMachine()).isTrue();
        Component piece = context.openDocument().document().components().get(0);
        assertThat(piece.typeId()).isEqualTo(SubcircuitTypes.typeIdOf("maquina-1"));
        assertThat(piece.position()).isEqualTo(new GridPoint(3, 4));
        assertThat(context.selection.ids()).containsExactly(piece.id());
        assertThat(context.tool).isSameAs(context.defaultTool);
        assertThat(context.openDocument().document().definitions()).hasSize(1);

        context.openDocument().history().undo();

        assertThat(context.openDocument().document().components()).isEmpty();
        assertThat(context.openDocument().document().definitions()).isEmpty();
    }

    @Test
    void aSecondMachineGetsTheNextIdAndName() {
        MachineCreation.create(context.openDocument(), new GridPoint(0, 0));
        MachineCreation.create(context.openDocument(), new GridPoint(20, 0));

        assertThat(context.openDocument().document().definitions()).extracting(ComponentDefinition::id)
                .containsExactly("maquina-1", "maquina-2");
        assertThat(context.openDocument().document().definitions()).extracting(ComponentDefinition::name)
                .containsExactly("Máquina 1", "Máquina 2");
    }

    @Test
    void aNameTakenByAnotherDefinitionIsSkipped() {
        MachineCreation.create(context.openDocument(), new GridPoint(0, 0));

        ComponentDefinition next = MachineCreation.nextDefinition(context.openDocument().document());

        assertThat(next.id()).isEqualTo("maquina-2");
        assertThat(next.name()).isEqualTo("Máquina 2");
    }

    @Test
    void createReturnsTheExecutedCommandWithItsPiece() {
        CreateMachineCommand command = MachineCreation.create(context.openDocument(), new GridPoint(1, 2));

        assertThat(command.instanceId()).contains(context.openDocument().document().components().get(0).id());
    }

    @Test
    void doubleClickingTheCreatedMachineEnters() {
        MachineCreation.create(context.openDocument(), new GridPoint(0, 0));
        Component piece = context.openDocument().document().components().get(0);
        SelectTool select = new SelectTool();
        context.tool = select;

        select.mousePressed(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 1, false));
        select.mouseReleased(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 1, false));
        select.mousePressed(context, TestToolContext.event(2, 1, CanvasMouseEvent.PRIMARY, 2, false));

        assertThat(context.entered).isEqualTo(piece);
    }

    @Test
    void thePaletteListsItInMemoryAndChoosingItArmsTheCreationTool() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorView view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        try {
            PaletteEntry entry = view.palette().entries().stream()
                    .filter(candidate -> candidate.id().equals(MachineCreation.PALETTE_ID)).findFirst()
                    .orElseThrow();
            assertThat(entry.name()).isEqualTo("Máquina de estados");
            assertThat(entry.category()).isEqualTo("Memoria");
            assertThat(view.palette().visibleSections()).doesNotContain("Máquinas");

            view.palette().chooseEntry(entry);

            assertThat(view.canvas().tool()).isInstanceOf(PlaceMachineTool.class);
        } finally {
            view.dispose();
            document.close();
        }
    }
}
